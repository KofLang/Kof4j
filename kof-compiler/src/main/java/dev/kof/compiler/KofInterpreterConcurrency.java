package dev.kof.compiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lambdas e concorrência do interpretador: map/filter/reduce nativos,
 * spawn/await/poll/cancel, select_any e jobs de time/scheduler — mesma
 * semântica do runtime gerado ({@code KofRuntime.kof_spawn/kof_time_interval}).
 */
final class KofInterpreterConcurrency {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    private final KofInterpreter interp;
    private final List<Thread> tasks = new CopyOnWriteArrayList<>();
    private final Map<Object, Thread> taskThreads = new ConcurrentHashMap<>();
    private final Map<String, Thread> timeJobs = new ConcurrentHashMap<>();
    private static final AtomicInteger timeSeq = new AtomicInteger();
    private static final AtomicInteger activeTasks = new AtomicInteger();

    KofInterpreterConcurrency(KofInterpreter interp) {
        this.interp = interp;
    }

    Object lambdaAware(KofCall kc, Object recv, Object[] args) throws Throwable {
        String name = kc.methodName();
        switch (name) {
            case "kof_list_map": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : src) out.add(interp.invokeLambda(args[1], new Object[]{o}));
                return out;
            }
            case "kof_list_filter": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : src) {
                    Object keep = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(keep) || Integer.valueOf(1).equals(keep)) out.add(o);
                }
                return out;
            }
            case "kof_list_reduce": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                Object acc = args[1];
                for (Object o : src) acc = interp.invokeLambda(args[2], new Object[]{acc, o});
                return acc;
            }
            case "kof_spawn_result": {
                CompletableFuture<Object> future = new CompletableFuture<>();
                startTask(future, () -> future.complete(interp.invokeLambda(args[0], new Object[0])));
                return future;
            }
            case "kof_spawn": {
                startTask(null, () -> interp.invokeLambda(args[0], new Object[0]));
                return null;
            }
            case "kof_await": {
                if (args[0] instanceof Future<?> fu) {
                    try {
                        return KofInterpreterValues.normalizeReturn(fu.get());
                    } catch (java.util.concurrent.ExecutionException e) {
                        throw unwrap(e);
                    }
                }
                throw new IllegalStateException("await: handle inválido");
            }
            case "kof_await_timeout": {
                Future<?> fu = (Future<?>) args[0];
                try {
                    return KofInterpreterValues.normalizeReturn(
                            fu.get(KofInterpreter.unboxInt(args[1]), TimeUnit.MILLISECONDS));
                } catch (java.util.concurrent.TimeoutException te) {
                    throw new RuntimeException("awaitTimeout: estourou o tempo limite de "
                            + args[1] + "ms");
                } catch (java.util.concurrent.ExecutionException e) {
                    throw unwrap(e);
                }
            }
            case "kof_poll": {
                if (args[0] instanceof CompletableFuture<?> cf) {
                    return KofInterpreterValues.normalizeReturn(cf.getNow(null));
                }
                if (args[0] instanceof Future<?> f && f.isDone()) {
                    try {
                        return KofInterpreterValues.normalizeReturn(f.get());
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
            case "kof_done":
                return args[0] instanceof Future<?> f && f.isDone() ? 1 : 0;
            case "kof_cancel":
                return args[0] instanceof Future<?> f && f.cancel(true) ? 1 : 0;
            case "kof_cancelled":
                return 0;
            case "kof_select_any": {
                @SuppressWarnings("unchecked")
                List<Object> handles = (List<Object>) args[0];
                CompletableFuture<?>[] arr = handles.stream()
                        .map(h -> (CompletableFuture<?>) h)
                        .toArray(CompletableFuture[]::new);
                return KofInterpreterValues.normalizeReturn(CompletableFuture.anyOf(arr).get());
            }
            case "kof_time_interval":
            case "kof_scheduler_every": {
                // mesma semântica do runtime gerado: job id "job-N", thread
                // daemon, cancel remove o job (o loop vê e para), ms<=0 erro.
                int ms = KofInterpreter.unboxInt(args[0]);
                if (ms <= 0) throw new IllegalArgumentException("interval must be positive: " + ms);
                String id = "job-" + timeSeq.incrementAndGet();
                Thread t = new Thread(() -> {
                    try {
                        while (timeJobs.containsKey(id)) {
                            Thread.sleep(ms);
                            if (!timeJobs.containsKey(id)) break;
                            interp.invokeLambda(args[1], new Object[0]);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable e) {
                        // o runtime gerado propaga; no daemon thread vira stderr
                        interp.err().println("interval task failed: "
                                + KofInterpreter.kofErrorMessage(e));
                    }
                }, "kof-time-" + id);
                t.setDaemon(true);
                timeJobs.put(id, t);
                t.start();
                return id;
            }
            case "kof_time_cancel":
            case "kof_scheduler_cancel": {
                Thread t = timeJobs.remove(String.valueOf(args[0]));
                if (t != null) t.interrupt();
                return null;
            }
            default:
                return NOT_HANDLED;
        }
    }

    private static Throwable unwrap(java.util.concurrent.ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException re) return re;
        if (cause instanceof Error er) return er;
        return new RuntimeException(cause);
    }

    private void startTask(Object handle, ThrowingRunnable body) {
        Runnable wrapped = () -> {
            try {
                body.run();
            } catch (Throwable e) {
                if (handle instanceof CompletableFuture<?> cf) {
                    cf.completeExceptionally(e);
                } else {
                    interp.err().println("spawn task failed: " + KofInterpreter.kofErrorMessage(e));
                }
            } finally {
                activeTasks.decrementAndGet();
            }
        };
        activeTasks.incrementAndGet();
        Thread t = startVirtualOrPlatform(wrapped);
        tasks.add(t);
        if (handle != null) taskThreads.put(handle, t);
    }

    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static Thread startVirtualOrPlatform(Runnable body) {
        try {
            Method m = Thread.class.getMethod("startVirtualThread", Runnable.class);
            return (Thread) m.invoke(null, body);
        } catch (Throwable ignored) {
            Thread t = new Thread(body, "kof-task");
            t.start();
            return t;
        }
    }

    /** Espelha o shutdown hook do runtime gerado: espera tarefas de spawn. */
    void awaitAllTasks() {
        while (activeTasks.get() > 0) {
            Thread.onSpinWait();
        }
    }
}

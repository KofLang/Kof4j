package dev.kof.compiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lambdas e concorrência do interpretador: map/filter/reduce nativos,
 * spawn/await/poll/cancel, select_any e jobs de time/scheduler — mesma
 * semântica do runtime gerado ({@code KofRuntime.kof_spawn/kof_time_interval}).
 */
public final class KofInterpreterConcurrency {

    static final Object NOT_HANDLED = KofInterpreterValues.NOT_HANDLED;

    private final KofInterpreter interp;
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
            // D-MULTIPARADIGMA-PHASE1A — eager short-circuit quantifiers;
            // truthiness reuses the filter rule; vacuous: all=true,
            // any/none=false. Lambdas throw through (short-circuit proof).
            case "kof_list_any": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                for (Object o : src) {
                    Object r = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(r) || Integer.valueOf(1).equals(r)) return 1;
                }
                return 0;
            }
            case "kof_list_all": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                for (Object o : src) {
                    Object r = interp.invokeLambda(args[1], new Object[]{o});
                    if (!(Boolean.TRUE.equals(r) || Integer.valueOf(1).equals(r))) return 0;
                }
                return 1;
            }
            case "kof_list_none": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                for (Object o : src) {
                    Object r = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(r) || Integer.valueOf(1).equals(r)) return 0;
                }
                return 1;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1b — find returns the match or
            // null (Map.get-missing contract); count(pred) counts matches.
            case "kof_list_find": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                for (Object o : src) {
                    Object r = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(r) || Integer.valueOf(1).equals(r)) return o;
                }
                return null;
            }
            case "kof_list_count_pred": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                int n = 0;
                for (Object o : src) {
                    Object r = interp.invokeLambda(args[1], new Object[]{o});
                    if (Boolean.TRUE.equals(r) || Integer.valueOf(1).equals(r)) n++;
                }
                return n;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach runs for effect.
            case "kof_list_foreach": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                for (Object o : src) interp.invokeLambda(args[1], new Object[]{o});
                return null;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1g — sorted(cmp): cópia ordenada
            // pelo comparador (negativo/zero/positivo); insertion sort estável
            // para comparadores puros. Chamada INSTANCE: receiver é a lista,
            // args[0] é a lambda (forma diferente dos FUNCTION acima).
            case "kof_list_sorted_cmp": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) recv;
                ArrayList<Object> out = new ArrayList<>(src);
                Object cmp = args[0];
                for (int i = 1; i < out.size(); i++) {
                    Object key = out.get(i);
                    int j = i - 1;
                    while (j >= 0 && ((Number) interp.invokeLambda(cmp,
                            new Object[]{out.get(j), key})).intValue() > 0) {
                        out.set(j + 1, out.get(j));
                        j--;
                    }
                    out.set(j + 1, key);
                }
                return out;
            }
            // #685 — enum sort(): in-place insertion via the synthesized
            // comparator (a.compareTo(b)); mutates the receiver list.
            case "kof_list_sort_cmp": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) recv;
                Object cmp = args[0];
                for (int i = 1; i < src.size(); i++) {
                    Object key = src.get(i);
                    int j = i - 1;
                    while (j >= 0 && ((Number) interp.invokeLambda(cmp,
                            new Object[]{src.get(j), key})).intValue() > 0) {
                        src.set(j + 1, src.get(j));
                        j--;
                    }
                    src.set(j + 1, key);
                }
                return null;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap concatenates each
            // element's List in order (non-List lambda result fails loudly).
            case "kof_list_flatmap": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) args[0];
                ArrayList<Object> out = new ArrayList<>();
                for (Object o : src) {
                    Object tmp = interp.invokeLambda(args[1], new Object[]{o});
                    out.addAll((java.util.List<?>) tmp);
                }
                return out;
            }
            // D-MULTIPARADIGMA-PHASE1A slice 1h — groupBy buckets by the
            // lambda key (LinkedHashMap = insertion order). INSTANCE shape:
            // caller emits user args first: recv is the list, args[0] the
            // lambda, args[1] the Native-only tag (same as kof_list_find).
            case "kof_list_groupby": {
                @SuppressWarnings("unchecked")
                ArrayList<Object> src = (ArrayList<Object>) recv;
                var out = new java.util.LinkedHashMap<Object, Object>();
                for (Object o : src) {
                    Object key = interp.invokeLambda(args[0], new Object[]{o});
                    groupBucket(out, key).add(o);
                }
                return out;
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
                throw new IllegalStateException("await: invalid handle");
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

    // D-MULTIPARADIGMA-PHASE1A slice 1h — bucket list for a groupBy key
    // (fresh list on first encounter, insertion order preserved).
    @SuppressWarnings("unchecked")
    private static ArrayList<Object> groupBucket(Map<Object, Object> out, Object key) {
        Object bucket = out.get(key);
        if (!(bucket instanceof ArrayList)) {
            bucket = new ArrayList<Object>();
            out.put(key, bucket);
        }
        return (ArrayList<Object>) bucket;
    }

    private void startTask(Object handle, ThrowingRunnable body) {        Runnable wrapped = () -> {
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
        startVirtualOrPlatform(wrapped);
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

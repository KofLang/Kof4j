package dev.kof.compiler;

/**
 * Core do runtime JVM: kof.time, kof.io básico, kof.concurrent (spawn/await) e kof.process.
 * Extraído de JvmRuntime.sourceCore (REFACTOR-500 Fase 5) — fragmento do
 * source do KofRuntime gerado; a concatenação preserva conteúdo byte-a-byte.
 */
final class JvmRuntimeCore {

    private JvmRuntimeCore() {}

    static String source() {
        return """
                // ── kof.time ───────────────────────────────────────

                public static long kof_now() {
                    return System.currentTimeMillis();
                }

                // ── kof.io ─────────────────────────────────────────

                private static final java.io.BufferedReader KOF_STDIN =
                        new java.io.BufferedReader(new java.io.InputStreamReader(System.in));

                public static String kof_read_line() {
                    // BufferedReader compartilhado: criar um por chamada
                    // perdia o buffer entre leituras (OBS: readLine repetido
                    // retornava null após a primeira linha).
                    try {
                        return KOF_STDIN.readLine();
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static String kof_read_file(String path) {
                    try {
                        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }

                public static int kof_write_file(String path, String content) {
                    try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(path), content);
                        return 0;
                    } catch (java.io.IOException e) {
                        return -1;
                    }
                }

                // ── kof.concurrent ─────────────────────────────────

                private static final java.util.concurrent.atomic.AtomicInteger KOF_ACTIVE_TASKS =
                        new java.util.concurrent.atomic.AtomicInteger();

                static {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        while (KOF_ACTIVE_TASKS.get() > 0) {
                            Thread.onSpinWait();
                        }
                    }, "kof-wait-tasks"));
                }

                private static final ThreadLocal<Object> KOF_CURRENT_HANDLE = new ThreadLocal<>();
                private static final java.util.Set<Object> KOF_CANCELLED =
                        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

                /**
                 * Thread de tarefa: virtual quando disponível (JVM 21+), platform
                 * thread no ART (Android não tem Thread.startVirtualThread).
                 * Detecta uma vez; sem reflection, sem falha em runtime antigo.
                 */
                private static final boolean KOF_VIRTUAL_OK = kofProbeVirtual();
                private static boolean kofProbeVirtual() {
                    try {
                        Thread.class.getMethod("startVirtualThread", Runnable.class);
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                }
                static void kofStartTask(Runnable body) {
                    if (KOF_VIRTUAL_OK) {
                        try {
                            Thread.class.getMethod("startVirtualThread", Runnable.class)
                                    .invoke(null, body);
                            return;
                        } catch (Throwable ignored) {}
                    }
                    new Thread(body, "kof-task").start();
                }

                public static Object kof_spawn_result(Object task) {
                    java.util.concurrent.CompletableFuture<Object> future =
                            new java.util.concurrent.CompletableFuture<>();
                    KOF_ACTIVE_TASKS.incrementAndGet();
                    kofStartTask(() -> {
                        KOF_CURRENT_HANDLE.set(future);
                        try {
                            future.complete(task.getClass().getMethod("invoke").invoke(task));
                        } catch (Throwable e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            future.completeExceptionally(cause);
                        } finally {
                            KOF_CURRENT_HANDLE.remove();
                            KOF_ACTIVE_TASKS.decrementAndGet();
                        }
                    });
                    return future;
                }

                /** cancel(handle) -> true (marca; a tarefa vê via cancelled()). */
                public static boolean kof_cancel(Object handle) {
                    if (handle == null) return false;
                    KOF_CANCELLED.add(handle);
                    return true;
                }

                /** cancelled() -> a tarefa ATUAL foi marcada como cancelada? */
                public static boolean kof_cancelled() {
                    Object h = KOF_CURRENT_HANDLE.get();
                    return h != null && KOF_CANCELLED.contains(h);
                }

                /** selectAny(handles) -> valor do primeiro handle pronto. */
                public static Object kof_select_any(java.util.List<?> handles) throws Exception {
                    if (handles == null || handles.isEmpty()) {
                        throw new IllegalArgumentException("selectAny: nenhuma tarefa");
                    }
                    java.util.concurrent.CompletableFuture<?>[] arr =
                            handles.stream()
                                .map(h -> {
                                    if (!(h instanceof java.util.concurrent.CompletableFuture<?> cf)) {
                                        throw new IllegalStateException(
                                                "selectAny: argumento não é Handle");
                                    }
                                    return cf;
                                })
                               .toArray(java.util.concurrent.CompletableFuture[]::new);
                    return java.util.concurrent.CompletableFuture.anyOf(arr).get();
                }

                public static Object kof_await(Object handle) throws Exception {
                    if (handle instanceof java.util.concurrent.Future<?> f) {
                        try {
                            return f.get();
                        } catch (java.util.concurrent.ExecutionException e) {
                            // re-lança a causa original: try/catch do Kof vê a
                            // exceção da tarefa, não o wrapper
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            if (cause instanceof RuntimeException re) throw re;
                            if (cause instanceof Error err) throw err;
                            throw new RuntimeException(cause);
                        }
                    }
                    throw new IllegalStateException("await: handle inválido");
                }

                /** awaitTimeout(handle, timeoutMs) -> valor; lança exceção no estouro. */
                public static Object kof_await_timeout(Object handle, int timeoutMs) throws Exception {
                    if (!(handle instanceof java.util.concurrent.Future<?> f)) {
                        throw new IllegalStateException("awaitTimeout: handle inválido");
                    }
                    try {
                        return f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException te) {
                        throw new RuntimeException("awaitTimeout: estourou o tempo limite de " + timeoutMs + "ms");
                    } catch (java.util.concurrent.ExecutionException e) {
                        // re-lança a causa original (mesma semântica do kof_await)
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (cause instanceof RuntimeException re) throw re;
                        if (cause instanceof Error err) throw err;
                        throw new RuntimeException(cause);
                    }
                }

                /** poll(rdi=handle) -> valor pronto | 0 (null) — não bloqueia. */
                public static Object kof_poll(Object handle) {
                    if (handle instanceof java.util.concurrent.CompletableFuture<?> cf) {
                        return cf.getNow(null);
                    }
                    if (handle instanceof java.util.concurrent.Future<?> f && f.isDone()) {
                        try { return f.get(); } catch (Exception e) { return null; }
                    }
                    return null;
                }

                /** kof_done(handle) -> true se a tarefa terminou. */
                public static boolean kof_done(Object handle) {
                    return handle instanceof java.util.concurrent.Future<?> f && f.isDone();
                }

                public static void kof_spawn(Object task) {
                    KOF_ACTIVE_TASKS.incrementAndGet();
                    kofStartTask(() -> {
                        try {
                            task.getClass().getMethod("invoke").invoke(task);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            System.err.println("spawn task failed: " + cause.getMessage());
                        } finally {
                            KOF_ACTIVE_TASKS.decrementAndGet();
                        }
                    });
                }

                public static ArrayList<String> kof_args_list(String[] args) {
                    ArrayList<String> list = new ArrayList<>(args.length);
                    for (String a : args) list.add(a);
                    return list;
                }

                // ── kof.process — multiplatform process abstraction ──

                public static final class ProcessResult {
                    public final String stdout;
                    public final String stderr;
                    public final int exitCode;

                    public ProcessResult(String stdout, String stderr, int exitCode) {
                        this.stdout = stdout;
                        this.stderr = stderr;
                        this.exitCode = exitCode;
                    }
                }

                public static void kof_process_exit(int code) {
                    // process.exit(code): termina na hora, sem stack trace
                    System.exit(code);
                }

                public static ProcessResult kof_process_run(String program, List<String> args) {                    try {
                        List<String> cmd = new ArrayList<>();
                        cmd.add(program);
                        cmd.addAll(args);
                        Process p = new ProcessBuilder(cmd)
                                .redirectErrorStream(false)
                                .redirectInput(java.lang.ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                                .start();
                        java.util.concurrent.FutureTask<String> outTask = new java.util.concurrent.FutureTask<>(
                                () -> new String(p.getInputStream().readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        java.util.concurrent.FutureTask<String> errTask = new java.util.concurrent.FutureTask<>(
                                () -> new String(p.getErrorStream().readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        kofStartTask(outTask);
                        kofStartTask(errTask);
                        int code = p.waitFor();
                        String out = outTask.get();
                        String err = errTask.get();
                        return new ProcessResult(out, err, code);
                    } catch (Exception e) {
                        return new ProcessResult("", e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getMessage(), -1);
                    }
                }

                // ── process.spawn — stdin/stdout vivos (F10) ────────

                private static final java.util.concurrent.ConcurrentHashMap<Long, Process> SPAWNED =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.BufferedReader> SPAWN_READERS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.PrintWriter> SPAWN_WRITERS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static long SPAWN_SEQ = 0;                public static Long kof_process_spawn(String program, List<String> args) {
                    try {
                        List<String> cmd = new ArrayList<>();
                        cmd.add(program);
                        cmd.addAll(args);
                        Process p = new ProcessBuilder(cmd)
                                .redirectErrorStream(false)
                                .redirectInput(java.lang.ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                                .start();
                        long id;
                        synchronized (KofRuntime.class) { id = ++SPAWN_SEQ; }
                        SPAWNED.put(id, p);
                        SPAWN_READERS.put(id, new java.io.BufferedReader(
                                new java.io.InputStreamReader(p.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8)));
                        SPAWN_WRITERS.put(id, new java.io.PrintWriter(
                                new java.io.OutputStreamWriter(p.getOutputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8), true));
                        return Long.valueOf(id);
                    } catch (Exception e) {
                        return Long.valueOf(-1);
                    }
                }

                public static String kof_spawn_read_line(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var r = SPAWN_READERS.get(handle);
                    if (r == null) return "";
                    try {
                        String line = r.readLine();
                        return line == null ? "" : line;
                    } catch (Exception e) {
                        return "";
                    }
                }

                public static void kof_spawn_write(Long handleBoxed, String data) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var w = SPAWN_WRITERS.get(handle);
                    if (w == null) return;
                    w.println(data);
                    w.flush();
                }

                public static int kof_spawn_exit_code(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    if (p == null) return -1;
                    try {
                        if (p.isAlive()) return Integer.MIN_VALUE;
                        return p.exitValue();
                    } catch (Exception e) {
                        return -1;
                    }
                }

                public static void kof_spawn_kill(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    if (p != null) {
                        p.destroyForcibly();
                        SPAWNED.remove(handle);
                        SPAWN_WRITERS.remove(handle);
                        SPAWN_READERS.remove(handle);
                    }
                }

                public static boolean kof_spawn_alive(Long handleBoxed) {
                    long handle = handleBoxed == null ? -1 : handleBoxed;
                    var p = SPAWNED.get(handle);
                    return p != null && p.isAlive();
                }

""";
    }
}

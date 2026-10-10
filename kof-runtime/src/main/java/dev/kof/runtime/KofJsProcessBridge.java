package dev.kof.runtime;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * Ponte de processo do host JS: expoe {@code kof_platform.processRun} e
 * {@code kof_platform.processRunWith} para o JavaScript gerado (face F4 do
 * {@code kof.process} e 2.2.3 do {@code kof.shell}). A convencao e honesta
 * nos dois alvos: erro de spawn ou argv vazio devolve Map com {@code stderr}
 * preenchido e {@code exitCode == -1} — nunca hang, nunca sucesso silencioso
 * (R6). Ambiente do {@code runWith} e ADITIVO (chaves sobrescrevem herdadas,
 * nunca limpeza silenciosa); cwd {@code ""} = diretorio do processo hospedeiro.
 * Extraido de {@link KofJsRunner} (gate <=500 — 19/09, shell 2.2.3).
 */
public final class KofJsProcessBridge {

    private KofJsProcessBridge() {
    }

    /**
     * §367: forma de dados process/shell — Map de conteudo com impressao
     * estavel {@code ProcessResult[exitCode=0, stdout=x, stderr=]} (golden do
     * corpus: conteudo, como record — nunca a forma crua do host). Chaves
     * stdout/stderr/exitCode inalteradas: o acesso do JavaScript gerado e o
     * de antes (additive, freeze 2). toString com null-seguranca (R6).
     */
    static final class KofResult extends LinkedHashMap<String, Object> {
        KofResult(String stdout, String stderr, int exitCode) {
            put("stdout", stdout);
            put("stderr", stderr);
            put("exitCode", exitCode);
        }

        @Override public String toString() {
            Object code = getOrDefault("exitCode", -1);
            Object o = getOrDefault("stdout", "");
            Object e = getOrDefault("stderr", "");
            return "ProcessResult[exitCode=" + code
                    + ", stdout=" + trimTrailingNewline(String.valueOf(o))
                    + ", stderr=" + trimTrailingNewline(String.valueOf(e)) + "]";
        }

        private static String trimTrailingNewline(String v) {
            int end = v.length();
            while (end > 0 && (v.charAt(end - 1) == '\n' || v.charAt(end - 1) == '\r')) end--;
            return v.substring(0, end);
        }
    }

    private static String trimTrailingNewline(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
    }

    /** Registra as faces de processo no mapa {@code kof_platform}. */
    public static void install(Map<String, Object> platform) {
        platform.put("processRun", (ProxyExecutable) args -> run(args));
        platform.put("processRunWith", (ProxyExecutable) args -> runWith(args));
        platform.put("processSpawn", (ProxyExecutable) args -> spawn(args));
        platform.put("spawnWrite", (ProxyExecutable) args -> {
            write(args[0].asLong(), args[1].asString());
            return 0;
        });
        platform.put("spawnReadLine", (ProxyExecutable) args -> readLine(args[0].asLong()));
        platform.put("spawnExitCode", (ProxyExecutable) args -> exitCode(args[0].asLong()));
        platform.put("spawnKill", (ProxyExecutable) args -> {
            kill(args[0].asLong());
            return 0;
        });
        platform.put("spawnAlive", (ProxyExecutable) args -> alive(args[0].asLong()) ? 1 : 0);
        platform.put("processPipeline", (ProxyExecutable) args -> pipeline(args));
    }

    /**
     * shell.pipeline(stages) — cadeia stdout→stdin com threads de pump,
     * espelho do `kof_shell_pipeline` JVM (JvmRuntimeCore 396+): stdin da
     * primeira etapa = /dev/null, demais = PIPE; ultimo exit code; erros
     * honestos como Result(-1), nunca excecao do host.
     */
    static Map<String, Object> pipeline(Value[] args) {
        Map<String, Object> result = new KofResult("", "", -1);
        java.util.List<Process> procs = new ArrayList<>();
        try {
            List<List<String>> stages = new ArrayList<>();
            if (args.length > 0 && !args[0].isNull() && args[0].hasArrayElements()) {
                long n = args[0].getArraySize();
                for (long i = 0; i < n; i++) {
                    Value stage = args[0].getArrayElement(i);
                    stages.add(stage.hasArrayElements() ? argvOf(stage) : List.of());
                }
            }
            if (stages.isEmpty()) {
                return fail(result, "kof_shell_pipeline: no stages");
            }
            for (List<String> argv : stages) {
                if (argv.isEmpty()) {
                    return fail(result, "kof_shell_pipeline: empty stage");
                }
                ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(false);
                pb.redirectInput(procs.isEmpty()
                        ? ProcessBuilder.Redirect.from(new java.io.File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null"))
                        : ProcessBuilder.Redirect.PIPE);
                procs.add(pb.start());
            }
            List<Thread> pumps = new ArrayList<>();
            for (int i = 1; i < procs.size(); i++) {
                final InputStream in = procs.get(i - 1).getInputStream();
                final OutputStream out = procs.get(i).getOutputStream();
                Thread pump = new Thread(() -> {
                    try (InputStream i2 = in; OutputStream o2 = out) {
                        i2.transferTo(o2);
                    } catch (Exception ignored) {
                    }
                });
                pump.setDaemon(true);
                pumps.add(pump);
                pump.start();
            }
            final Process last = procs.get(procs.size() - 1);
            java.util.concurrent.FutureTask<String> outTask = new java.util.concurrent.FutureTask<>(
                    () -> new String(last.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
            java.util.concurrent.FutureTask<String> errTask = new java.util.concurrent.FutureTask<>(
                    () -> new String(last.getErrorStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
            Thread ot = new Thread(outTask);
            Thread et = new Thread(errTask);
            ot.setDaemon(true);
            et.setDaemon(true);
            ot.start();
            et.start();
            int code = last.waitFor();
            for (Thread pump : pumps) pump.join(5000);
            for (Process p : procs) if (p.isAlive()) p.destroy();
            return new KofResult(outTask.get(), errTask.get(), code);
        } catch (Exception e) {
            for (Process p : procs) p.destroyForcibly();
            return fail(result, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static Map<String, Object> fail(Map<String, Object> result, String message) {
        result.put("stdout", "");
        result.put("stderr", message);
        result.put("exitCode", -1);
        return result;
    }

    static Map<String, Object> run(Value[] args) {
        try {
            String program = args[0].asString();
            List<String> cmd = new ArrayList<>();
            cmd.add(program);
            if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                cmd.addAll(argvOf(args[1]));
            }
            return execute(cmd, "", Map.of());
        } catch (Exception e) {
            return honestFailure(e);
        }
    }

    static Map<String, Object> runWith(Value[] args) {
        try {
            List<String> cmd = args.length > 0 && args[0].hasArrayElements()
                    ? argvOf(args[0]) : List.of();
            if (cmd.isEmpty()) {
                Map<String, Object> result = new KofResult("", "", -1);
                result.put("stdout", "");
                result.put("stderr", "kof_shell_runwith: empty argv");
                result.put("exitCode", -1);
                return result;
            }
            String cwd = args.length > 1 && args[1].isString() ? args[1].asString() : "";
            Map<String, String> env = new LinkedHashMap<>();
            if (args.length > 2 && !args[2].isNull() && args[2].hasMembers()) {
                for (String k : args[2].getMemberKeys()) {
                    Value v = args[2].getMember(k);
                    env.put(k, v.isString() ? v.asString() : String.valueOf(v));
                }
            }
            return execute(cmd, cwd, env);
        } catch (Exception e) {
            return honestFailure(e);
        }
    }

    private static List<String> argvOf(Value arrayArg) {
        List<String> cmd = new ArrayList<>();
        long n = arrayArg.getArraySize();
        for (long i = 0; i < n; i++) {
            Value v = arrayArg.getArrayElement(i);
            cmd.add(v.isString() ? v.asString() : String.valueOf(v));
        }
        return cmd;
    }

    private static Map<String, Object> execute(List<String> cmd, String cwd,
                                               Map<String, String> extraEnv) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            if (cwd != null && !cwd.isEmpty()) {
                pb.directory(new java.io.File(cwd));
            }
            if (!extraEnv.isEmpty()) {
                pb.environment().putAll(extraEnv);
            }
            Process p = pb.start();
            String outText = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            String errText = new String(p.getErrorStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int code = p.waitFor();
            return new KofResult(outText, errText, code);
        } catch (Exception e) {
            return honestFailure(e);
        }
    }

    private static Map<String, Object> honestFailure(Exception e) {
        return new KofResult("", e.getMessage() == null
                ? e.getClass().getSimpleName() : e.getMessage(), -1);
    }

    // ── process.spawn (F10) — stdin/stdout vivos no host JS. Espelho EXATO
    // do binding JVM medido em JvmRuntimeCore (279-350): handle = seq Long,
    // spawn falho = -1, readLine EOF/morto = "", exitCode ainda vivo =
    // Integer.MIN_VALUE, kill = destroyForcibly + remove. Mesma JVM do
    // processo hospedeiro (graal), mesmo ProcessBuilder — paridade por
    // construção, não por imitação.

    private static final java.util.concurrent.ConcurrentHashMap<Long, Process> SPAWNED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.BufferedReader> SPAWN_READERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.io.PrintWriter> SPAWN_WRITERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static long spawnSeq = 0;

    static long spawn(Value[] args) {
        Process p = null;
        java.io.BufferedReader reader = null;
        java.io.PrintWriter writer = null;
        try {
            String program = args[0].asString();
            List<String> cmd = new ArrayList<>();
            cmd.add(program);
            if (args.length > 1 && !args[1].isNull() && args[1].hasArrayElements()) {
                cmd.addAll(argvOf(args[1]));
            }
            p = new ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null")))
                    .start();
            reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8));
            writer = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(p.getOutputStream(),
                            java.nio.charset.StandardCharsets.UTF_8), true);
            long id;
            synchronized (KofJsProcessBridge.class) {
                id = ++spawnSeq;
            }
            SPAWNED.put(id, p);
            SPAWN_READERS.put(id, reader);
            SPAWN_WRITERS.put(id, writer);
            return id;
        } catch (Exception e) {
            // #555 (java/input-resource-leak / java/output-resource-leak): nada
            // escapa fechado pela metade — o handle falho devolve -1 e libera o
            // processo + pipes ja criados.
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // pipe ja morto — so fd hygiene
                }
            }
            if (p != null) {
                p.destroyForcibly();
            }
            return -1;
        }
    }

    static String readLine(long handle) {
        var r = SPAWN_READERS.get(handle);
        if (r == null) {
            return "";
        }
        try {
            String line = r.readLine();
            return line == null ? "" : line;
        } catch (Exception e) {
            return "";
        }
    }

    static void write(long handle, String data) {
        var w = SPAWN_WRITERS.get(handle);
        if (w == null) {
            return;
        }
        w.println(data);
        w.flush();
    }

    static int exitCode(long handle) {
        var p = SPAWNED.get(handle);
        if (p == null) {
            return -1;
        }
        try {
            if (p.isAlive()) {
                return Integer.MIN_VALUE;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    static void kill(long handle) {
        var p = SPAWNED.get(handle);
        if (p != null) {
            p.destroyForcibly();
            SPAWNED.remove(handle);
            // #555: os pipes do handle morrem com ele — sem fechar, cada
            // spawn+kill deixava um par de fds orfao no processo hospedeiro.
            var w = SPAWN_WRITERS.remove(handle);
            var r = SPAWN_READERS.remove(handle);
            if (w != null) {
                w.close();
            }
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                    // pipe ja morto — so fd hygiene
                }
            }
        }
    }

    static boolean alive(long handle) {
        var p = SPAWNED.get(handle);
        return p != null && p.isAlive();
    }
}

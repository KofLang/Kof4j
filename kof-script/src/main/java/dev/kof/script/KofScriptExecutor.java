package dev.kof.script;

import dev.kof.compiler.Target;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes compiled Kof output on the requested target.
 * <p>
 * Holds the target dispatch of {@link KofScript#runFile}: running the emitted
 * JVM bytecode in-memory (JIT, no fork) or forked, the JS module via the
 * embedded GraalJS runner, and the native binary. KofScript keeps source
 * collection + compilation; this class owns running what was compiled.
 */
final class KofScriptExecutor {

    private KofScriptExecutor() {}

    /**
     * Runs the compiled output in {@code outDir} for {@code target},
     * returning the process result.
     */
    static KofScript.RunResult executeCompiled(Path outDir, Target target, String[] programArgs) throws IOException {
        KofScript.RunResult rr;
        if (target == Target.JS) {
            String entry = findJsEntry(outDir);
            if (entry == null) return new KofScript.RunResult(1, "", "no JS entry", false);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            ByteArrayOutputStream beos = new ByteArrayOutputStream();
            PrintStream pe = new PrintStream(beos);
            int ec = dev.kof.runtime.KofJsRunner.run(Path.of(entry), ps, System.in, pe, false, programArgs);
            rr = new KofScript.RunResult(ec, baos.toString(), beos.toString(), ec == 0);
        } else if (target == Target.NATIVE) {
            Path bin = outDir.resolve("Default/Main");
            if (!Files.exists(bin)) return new KofScript.RunResult(1, "", "no native binary", false);
            List<String> cmd = new ArrayList<>();
            cmd.add(bin.toString());
            for (String a : programArgs) cmd.add(a);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String stdout = new String(p.getInputStream().readAllBytes());
            String stderr = new String(p.getErrorStream().readAllBytes());
            boolean finished = false;
            try { finished = p.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); p.destroyForcibly(); }
            if (!finished) { p.destroyForcibly(); return new KofScript.RunResult(124, stdout, "timeout", false); }
            rr = new KofScript.RunResult(p.exitValue(), stdout, stderr, p.exitValue() == 0);
        } else {
            // Try JIT in-memory first (fast, no fork) — fallback to fork if it fails or uses System.exit
            KofScript.RunResult inMem = null;
            // Only use in-memory for simple cases without System.exit and with programArgs (our test harness doesn't use System.exit)
            try {
                // Heuristic: if code contains System.exit or kof.test, don't use in-memory
                inMem = runJvmInMemory(outDir, programArgs);
                if (inMem != null && inMem.success()) {
                    rr = inMem;
                } else if (inMem != null && !inMem.stderr().contains("ClassNotFoundException")) {
                    // in-memory produced a result (even if failure), use it if it's not a class loading failure
                    rr = inMem;
                } else {
                    throw new Exception("fallback");
                }
            } catch (Exception e) {
                // Fallback to fork
                String runtimeCp = runtimeClasspath();
                String cp = outDir.toString() + (runtimeCp != null ? File.pathSeparator + runtimeCp : "");
                String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                List<String> cmd = new ArrayList<>();
                cmd.add(javaBin); cmd.add("-cp"); cmd.add(cp); cmd.add("Default.Main");
                for (String a : programArgs) cmd.add(a);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                Process p = pb.start();
                String stdout = new String(p.getInputStream().readAllBytes());
                String stderr = new String(p.getErrorStream().readAllBytes());
                boolean finished = false;
                try {
                    finished = p.waitFor(10, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    p.destroyForcibly();
                }
                if (!finished) {
                    p.destroyForcibly();
                    return new KofScript.RunResult(124, stdout, "timeout", false);
                }
                int ec = p.exitValue();
                rr = new KofScript.RunResult(ec, stdout, stderr, ec == 0);
            }
        }
        return rr;
    }

    // JIT in-memory: try to run Default.Main via URLClassLoader without forking java (fast for repl/eval)
    private static KofScript.RunResult runJvmInMemory(Path outDir, String[] programArgs) {
        try {
            var cl = new java.net.URLClassLoader(new java.net.URL[]{outDir.toUri().toURL()}, KofScript.class.getClassLoader());
            Class<?> mainClass = cl.loadClass("Default.Main");
            var mainMethod = mainClass.getMethod("main", String[].class);
            // Capture stdout/stderr
            var baosOut = new ByteArrayOutputStream();
            var baosErr = new ByteArrayOutputStream();
            var psOut = new PrintStream(baosOut);
            var psErr = new PrintStream(baosErr);
            var oldOut = System.out;
            var oldErr = System.err;
            System.setOut(psOut);
            System.setErr(psErr);
            int ec = 0;
            try {
                mainMethod.invoke(null, (Object) programArgs);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                if (cause != null) {
                    // Kof's exit via System.exit is not used in in-memory; check for SecurityException or normal exception
                    psErr.println(cause.toString());
                    ec = 1;
                } else ec = 1;
            } catch (Exception e) {
                psErr.println(e.toString());
                ec = 1;
            } finally {
                System.setOut(oldOut);
                System.setErr(oldErr);
                try { cl.close(); } catch (IOException ignore) {}
            }
            return new KofScript.RunResult(ec, baosOut.toString(), baosErr.toString(), ec == 0);
        } catch (Exception e) {
            return null; // fallback to fork
        }
    }

    private static String runtimeClasspath() {
        try {
            // When running from mvn test, kof-runtime/target/classes exists; when installed, kof.jar contains runtime
            Path candidate = Path.of("kof-runtime/target/classes");
            if (Files.exists(candidate)) return candidate.toString();
            // Try to locate via protection domain of a runtime class (KofJsRunner is always present)
            var loc = dev.kof.runtime.KofJsRunner.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path p = Path.of(loc.toURI());
                if (Files.exists(p)) return p.toString();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String findJsEntry(Path dir) {
        Path e = dir.resolve("Default.mjs");
        if (Files.exists(e)) return e.toString();
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs")).findFirst().map(Path::toString).orElse(null);
        } catch (IOException ex) { return null; }
    }
}

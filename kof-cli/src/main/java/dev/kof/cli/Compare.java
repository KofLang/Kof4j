package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof compare` — differential testing of a migration (docs/future/
 * DIFFERENTIAL_TESTING.md, Fase G).
 *
 * Runs a legacy program (a {@code .class} with a {@code main}, or a
 * {@code .jar}) and a Kof program with the same stdin/args, then compares the
 * observable behavior (stdout, stderr, exit code). Divergence is reported as
 * structured data, never a silent pass/fail.
 */
public final class Compare {

    private Compare() {
    }

    record RunResult(int exitCode, String stdout, String stderr, Map<String, String> files) {
        boolean sameStdout(RunResult o) { return stdout.equals(o.stdout); }
        boolean sameStderr(RunResult o) { return stderr.equals(o.stderr); }
        boolean sameExit(RunResult o) { return exitCode == o.exitCode; }
        boolean sameFiles(RunResult o) { return files.equals(o.files); }
    }

    enum Channel { EQUIVALENT, DIVERGENT }

    record Verdict(Channel stdout, Channel stderr, Channel exit, Channel files) {
        boolean equivalent() {
            return stdout == Channel.EQUIVALENT && exit == Channel.EQUIVALENT && files == Channel.EQUIVALENT;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stdout", stdout == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("stderr", stderr == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("exit", exit == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("files", files == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("overall", equivalent() ? "equivalent" : "divergent");
            return m;
        }
    }

    public static int run(String[] args) {
        if (args.length > 0 && "compare".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.out.println("usage: kof compare <legacy.class|legacy.jar> <file.kf> [--stdin <text>] [--arg <v>...] [--json]");
            System.out.println("  runs both programs with identical inputs and compares stdout/stderr/exit code");
            return 0;
        }
        if (args.length < 2) {
            System.err.println("usage: kof compare <legacy.class|legacy.jar> <file.kf> [--stdin <text>] [--arg <v>...] [--json]");
            return 1;
        }
        Path legacy = Path.of(args[0]);
        Path kofFile = Path.of(args[1]);
        boolean json = false;
        String stdin = null;
        List<String> programArgs = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> json = true;
                case "--stdin" -> stdin = args[++i];
                case "--arg" -> programArgs.add(args[++i]);
                default -> System.err.println("unknown option: " + args[i]);
            }
        }
        try {
            RunResult legacyRes = runLegacy(legacy, programArgs, stdin);
            RunResult kofRes = runKof(kofFile, programArgs, stdin);
            Verdict verdict = compare(legacyRes, kofRes);

            if (json) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("legacy", runToMap(legacyRes));
                out.put("kof", runToMap(kofRes));
                out.put("verdict", verdict.toMap());
                System.out.println(Json.stringify(out));
            } else {
                System.out.println("legacy exit=" + legacyRes.exitCode + " stdout=" + abbrev(legacyRes.stdout));
                System.out.println("kof    exit=" + kofRes.exitCode + " stdout=" + abbrev(kofRes.stdout));
                System.out.println("verdict: " + (verdict.equivalent() ? "EQUIVALENT" : "DIVERGENT")
                        + "  (stdout=" + verdict.stdout + ", stderr=" + verdict.stderr
                        + ", exit=" + verdict.exit + ", files=" + verdict.files + ")");
            }
            return verdict.equivalent() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("kof compare: " + e.getMessage());
            return 2;
        }
    }

    static Verdict compare(RunResult legacy, RunResult kof) {
        return new Verdict(
                legacy.sameStdout(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameStderr(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameExit(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameFiles(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT);
    }

    static RunResult runLegacy(Path legacy, List<String> args, String stdin) throws IOException {
        String file = legacy.getFileName().toString();
        Path sandbox = Files.createTempDirectory("kof-compare-legacy-");
        try {
            if (file.endsWith(".jar")) {
                return exec(List.of(Paths.java(), "-jar", legacy.toString()), args, stdin, sandbox);
            }
            if (file.endsWith(".class")) {
                String className = file.substring(0, file.length() - ".class".length());
                List<String> cmd = List.of(Paths.java(), "-cp", legacy.getParent().toString(), className);
                return exec(cmd, args, stdin, sandbox);
            }
            throw new IOException("legacy must be a .class or .jar file");
        } finally {
            cleanup(sandbox);
        }
    }

    static RunResult runKof(Path kofFile, List<String> args, String stdin) throws IOException {
        Path outDir = Files.createTempDirectory("kof-compare-");
        Path sandbox = Files.createTempDirectory("kof-compare-kof-");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(kofFile, outDir, Target.JVM);
        if (!result.success()) {
            throw new IOException("Kof compile failed: " + result.diagnostics().getDiagnostics());
        }
        try {
            return exec(List.of(Paths.java(), "-cp", outDir.toString(), "Default.Main"), args, stdin, sandbox);
        } finally {
            cleanup(outDir);
            cleanup(sandbox);
        }
    }

    private static RunResult exec(List<String> cmd, List<String> args, String stdin, Path sandbox) throws IOException {
        List<String> full = new ArrayList<>(cmd);
        full.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(full);
        pb.directory(sandbox.toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        if (stdin != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, String> files = captureFiles(sandbox);
        return new RunResult(p.exitValue(), out.trim(), err.trim(), files);
    }

    /** Snapshot dos arquivos criados no sandbox: caminho relativo → hash SHA-256. */
    private static Map<String, String> captureFiles(Path dir) throws IOException {
        Map<String, String> files = new java.util.TreeMap<>();
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                files.put(rel, sha256(Files.readAllBytes(p)));
            }
        }
        return files;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private static Map<String, Object> runToMap(RunResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exit", r.exitCode);
        m.put("stdout", r.stdout);
        m.put("stderr", r.stderr);
        m.put("files", r.files);
        return m;
    }

    private static String abbrev(String s) {
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }

    private static final class Paths {
        static String java() {
            return Path.of(System.getProperty("java.home"), "bin", "java").toString();
        }
    }
}
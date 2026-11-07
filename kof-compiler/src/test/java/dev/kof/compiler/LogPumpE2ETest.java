package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 5.9 slice — the LOG-INGESTION PUMP
 * ({@code libs/sentinel/LogPump.kf}): the operator redirects the app's
 * stderr (the measured {@code kof_log} TEXT format,
 * {@code yyyy-MM-dd HH:mm:ss.SSS LEVEL message}) into a file; the pump
 * reads that file with the measured io primitives, classifies the level
 * (the THIRD token) into the sentinel kind and records one event per line,
 * resuming at a caller-supplied line count (a stateless pump). Faces: the
 * resume line, the per-level counts, the missing-file refusal (LOGP001)
 * and the short-line refusal (LOGP002).
 */
class LogPumpE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "sentinel"; }

    @Override public List<String> libraryMarkers() {
        return List.of("Sentinel.kf", "LogPump.kf");
    }

    private static final String PROBE_TEMPLATE = """
            import sentinel.Sentinel
            import sentinel.LogPump

            main() {
                var sent = Sentinel(60000, 100)
                var n1 = ingestLog(sent, "__LOG__", 0, 1000L)
                println(n1 == 4)
                println(sent.count("__LOG__", "log:error", 1000L) == 1)
                println(sent.count("__LOG__", "log:warn", 1000L) == 1)
                println(sent.count("__LOG__", "log:info", 1000L) == 1)
                println(sent.count("__LOG__", "log:debug", 1000L) == 1)
                var n2 = ingestLog(sent, "__LOG__", 3, 1000L)
                println(n2 == 1)
                println(sent.count("__LOG__", "log:debug", 1000L) == 2)
                try {
                    ingestLog(sent, "__LOG__/missing.log", 0, 1000L)
                    println("no-refusal")
                } catch (String e) {
                    println(e.contains("LOGP001"))
                }
                println("done")
            }
            """;

    // four text-format lines (the measured kof_log shape); the resume at
    // line 3 reads ONLY the last line (a stateless pump — the caller says
    // how much it consumed); an absent file refuses LOGP001.
    private static final String LOG_TEXT =
            "2026-11-05 07:00:01.000 INFO server started\n"
            + "2026-11-05 07:00:02.000 WARN queue slow\n"
            + "2026-11-05 07:00:03.000 ERROR db down\n"
            + "2026-11-05 07:00:04.000 DEBUG trace\n";

    private static final String EXPECTED =
            "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ndone";

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    private Path writeProbe(Path root) throws Exception {
        Path log = tmp.resolve("app.log");
        Files.writeString(log, LOG_TEXT);
        String work = log.toAbsolutePath().toString().replace('\\', '/');
        String source = PROBE_TEMPLATE.replace("__LOG__", work);
        Path probe = root.resolve("Main.kf");
        Files.writeString(probe, source);
        return probe;
    }

    @Test
    void logPumpOnJvm() throws Exception {
        Path root = tmp.resolve("logpump-jvm");
        Files.createDirectories(root);
        Path probe = writeProbe(root);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(probe, out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void logPumpOnScript() throws Exception {
        Path root = tmp.resolve("logpump-script");
        Files.createDirectories(root);
        Path probe = writeProbe(root);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(probe), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

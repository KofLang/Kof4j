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
 * Universal platform Stage 5.9 slice — the THREAT-DETECTION CORE
 * ({@code libs/sentinel/Sentinel.kf}): an event ring with window pruning,
 * per-(source, kind) counting, the burst rule, and a JSON alert emitter.
 * Pure Kof over existing primitives (class + record + List + Long) — no
 * engine, no FFI; the http-middleware/log-ingestion wiring faces stay
 * recorded as follow-ups in the plan.
 */
class SentinelE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "sentinel"; }

    @Override public List<String> libraryMarkers() { return List.of("Sentinel.kf"); }

    private static final String PROBE = """
            import sentinel.Sentinel

            main() {
                var w = Sentinel(10000, 3)
                w.addEvent("10.0.0.1", "auth-fail", 1000L)
                w.addEvent("10.0.0.1", "auth-fail", 1100L)
                println(w.bursting("10.0.0.1", "auth-fail", 1300L))
                w.addEvent("10.0.0.1", "auth-fail", 1200L)
                println(w.bursting("10.0.0.1", "auth-fail", 1300L))
                println(w.bursting("10.0.0.2", "auth-fail", 1300L))
                println(w.count("10.0.0.1", "auth-fail", 1300L))
                var a = w.alert("10.0.0.1", "auth-fail", 1300L)
                if (a != null) {
                    println(a.contains("10.0.0.1") && a.contains("auth-burst"))
                } else {
                    println(false)
                }
                println(w.alert("10.0.0.2", "auth-fail", 1300L) == null)
                println(w.count("10.0.0.1", "auth-fail", 25000L))
                w.addEvent("10.0.0.1", "login-ok", 26000L)
                println(w.count("10.0.0.1", "auth-fail", 26000L))
            }
            """;

    // 2 events < limit 3 -> not bursting ; 3 events -> bursting ; another
    // source never inherits the ring ; the window prune (now 25000 cuts
    // everything before 15000) empties the count ; a different kind never
    // counts toward the burst ; the alert JSON names the source and rule and
    // is null for a quiet source.
    private static final String EXPECTED = "false\ntrue\nfalse\n3\ntrue\ntrue\n0\n0";

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

    @Test
    void sentinelOnJvm() throws Exception {
        Path root = tmp.resolve("sentinel-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
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
    void sentinelOnScript() throws Exception {
        Path root = tmp.resolve("sentinel-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

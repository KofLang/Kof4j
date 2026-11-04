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
 * Universal platform Stage 4.6 — light experiment tracking
 * ({@code libs/experiments/Experiments.kf}): runs, metrics and parameters as
 * ROWS over {@code kof.db}, plus a JSON Lines export over {@code kof.io}.
 * Pure Kof over existing primitives (record + String + Long + Double + List) —
 * no new syntax, no core change, no FFI.
 *
 * <p>The reader decodes the raw JSON row with {@code json.decode<Row>} into a
 * record defined in the LIB file, because the TYPED {@code db.query<Rec>} loses
 * the class there ({@code known-bugs} §727).
 */
class ExperimentsE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "experiments"; }

    @Override public List<String> libraryMarkers() { return List.of("Experiments.kf"); }

    private static final String PROBE = """
            import experiments.Experiments

            main() {
                var s = initExperiments("sqlite:%s")
                startRun(s, "r-1", "baseline", 1000L)
                logParam(s, "r-1", "lr", "0.01")
                logMetric(s, "r-1", "loss", 0, 2.5)
                logMetric(s, "r-1", "loss", 1, 1.25)
                logMetric(s, "r-1", "loss", 2, 0.5)
                logMetric(s, "r-1", "acc", 0, 0.75)
                finishRun(s, "r-1", 2000L)
                var mid = metricAt(s, "r-1", "loss", 1)
                if (mid != null) {
                    println(mid)
                } else {
                    println("mid-none")
                }
                var last = lastMetric(s, "r-1", "loss")
                if (last != null) {
                    println(last)
                } else {
                    println("last-none")
                }
                println(metricCount(s, "r-1"))
                var missingStep = metricAt(s, "r-1", "loss", 9)
                println(missingStep == null)
                var missingKey = metricAt(s, "r-1", "nope", 0)
                println(missingKey == null)
                exportRun(s, "r-1", "%s")
                var text = File("%s").readText()
                if (text != null) {
                    println(text.contains("baseline"))
                } else {
                    println(false)
                }
            }
            """;

    // metricAt(loss,1) = 1.25 ; lastMetric(loss) = 0.5 (highest step) ;
    // metricCount = 4 ; a missing step and a missing key are BOTH null (never 0,
    // never a throw) ; the export line carries the run name.
    private static final String EXPECTED = "1.25\n0.5\n4\ntrue\ntrue\ntrue";

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
    void experimentsOnJvm() throws Exception {
        Path root = tmp.resolve("exp-jvm");
        Files.createDirectories(root);
        Path export = root.resolve("run.jsonl");
        Files.writeString(root.resolve("Main.kf"),
                PROBE.formatted(root.resolve("kof.db"), export, export));
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
    void experimentsOnScript() throws Exception {
        Path root = tmp.resolve("exp-script");
        Files.createDirectories(root);
        Path export = root.resolve("run.jsonl");
        Files.writeString(root.resolve("Main.kf"),
                PROBE.formatted(root.resolve("kof.db"), export, export));
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

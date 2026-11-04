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
 * Universal platform Stage 4.3 slice — descriptive statistics
 * ({@code libs/stats/Stats.kf}): mean/median/minimum/maximum/pvariance/
 * stddev/percentile over {@code List<Double>} — pure Kof over existing
 * primitives, no FFI, no new primitive. Empty samples refuse honestly
 * (STATS001); an out-of-range percentile refuses (STATS002). The numeric
 * expectations are EXACT binary fractions (x.25/x.5), so both targets print
 * the identical text; {@code stddev} is asserted by interval narrowing (the
 * IEEE sqrt is exact, the bounds are far from it).
 */
class StatsE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "stats"; }

    @Override public List<String> libraryMarkers() { return List.of("Stats.kf"); }

    private static final String PROBE = """
            import stats.Stats

            main() {
                var xs = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
                var ys = listOf(3.0, 1.0, 4.0, 1.0, 5.0, 9.0, 2.0, 6.0)
                println(mean(xs))
                println(median(ys))
                println(percentile(xs, 50.0))
                println(percentile(xs, 25.0))
                println(pvariance(xs))
                var sd = stddev(xs)
                if (sd > 2.87 && sd < 2.88) {
                    println("sd-ok")
                } else {
                    println("sd-bad")
                }
                var lo = minimum(xs)
                if (lo == 1.0) {
                    println("min-ok")
                } else {
                    println("min-bad")
                }
                var hi = maximum(xs)
                if (hi == 10.0) {
                    println("max-ok")
                } else {
                    println("max-bad")
                }
                try {
                    mean(listOf())
                } catch (String e) {
                    println(e.contains("STATS001"))
                }
                try {
                    percentile(xs, 150.0)
                } catch (String e) {
                    println(e.contains("STATS002"))
                }
            }
            """;

    // mean(1..10) = 5.5 ; median({3,1,4,1,5,9,2,6}) = (3+4)/2 = 3.5 ;
    // p50 of 1..10 = 5.5 ; p25 = 3.25 ; population variance = 82.5/10 = 8.25 ;
    // the empty sample and the out-of-range percentile BOTH refuse by name.
    private static final String EXPECTED = "5.5\n3.5\n5.5\n3.25\n8.25\nsd-ok\nmin-ok\nmax-ok\ntrue\ntrue";

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
    void statsOnJvm() throws Exception {
        Path root = tmp.resolve("stats-jvm");
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
    void statsOnScript() throws Exception {
        Path root = tmp.resolve("stats-script");
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

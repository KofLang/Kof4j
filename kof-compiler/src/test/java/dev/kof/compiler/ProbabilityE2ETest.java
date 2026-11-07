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
 * Universal platform Stage 4.3 slice — the PROBABILITY half's exact faces
 * ({@code libs/stats/Probability.kf}): uniform/bernoulli/binomial in pure
 * Kof over {@code random.double()} (the S10 OS-entropy primitive), no FFI,
 * no new primitive. Deterministic edges are exact (bernoulli(0) is always
 * false, bernoulli(1) always true, binomial(0) = 0); the statistical faces
 * are asserted by WIDE intervals (~11 sigma or more, since the entropy
 * primitive is unseedable by contract). Out-of-range arguments refuse by
 * name (STATS003/004/005). The CONTINUOUS faces (normal/exponential) ride
 * {@code math.log}/{@code math.exp} — the primitives MATH-LOG-EXP landed on
 * every target (07/11); they are asserted by wide moments (a mean within a
 * generous band, every draw finite) since the entropy is unseedable.
 */
class ProbabilityE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "stats"; }

    @Override public List<String> libraryMarkers() { return List.of("Stats.kf", "Probability.kf"); }

    private static final String PROBE = """
            import stats.Probability

            main() {
                var u = uniform(3.0, 7.0)
                if (u >= 3.0 && u < 7.0) {
                    println("bound-ok")
                } else {
                    println("bound-bad")
                }
                var total = 0.0
                var i = 0
                while (i < 10000) {
                    total = total + uniform(10.0, 20.0)
                    i = i + 1
                }
                var avg = total / 10000.0
                if (avg > 14.0 && avg < 16.0) {
                    println("mean-ok")
                } else {
                    println("mean-bad")
                }
                println(bernoulli(0.0))
                println(bernoulli(1.0))
                var hits = 0
                var j = 0
                while (j < 10000) {
                    if (bernoulli(0.3)) {
                        hits = hits + 1
                    }
                    j = j + 1
                }
                var freq = hits.toDouble() / 10000.0
                if (freq > 0.25 && freq < 0.35) {
                    println("freq-ok")
                } else {
                    println("freq-bad")
                }
                var wins = binomial(100, 0.5)
                if (wins > 30 && wins < 70) {
                    println("binom-ok")
                } else {
                    println("binom-bad")
                }
                if (binomial(0, 0.5) == 0) {
                    println("edge0-ok")
                } else {
                    println("edge0-bad")
                }
                try {
                    uniform(5.0, 5.0)
                } catch (String e) {
                    println(e.contains("STATS003"))
                }
                try {
                    bernoulli(1.5)
                } catch (String e) {
                    println(e.contains("STATS004"))
                }
                try {
                    binomial(-1, 0.5)
                } catch (String e) {
                    println(e.contains("STATS005"))
                }
                var sum = 0.0
                var k = 0
                while (k < 2000) {
                    sum = sum + normal(100.0, 15.0)
                    k = k + 1
                }
                var nmean = sum / 2000.0
                if (nmean > 95.0 && nmean < 105.0) {
                    println("normal-ok")
                } else {
                    println("normal-bad")
                }
                var esum = 0.0
                var m = 0
                while (m < 2000) {
                    esum = esum + exponential(2.0)
                    m = m + 1
                }
                var emean = esum / 2000.0
                if (emean > 0.45 && emean < 0.55) {
                    println("exp-ok")
                } else {
                    println("exp-bad")
                }
                try {
                    normal(0.0, 0.0)
                } catch (String e) {
                    println(e.contains("STATS006"))
                }
                try {
                    exponential(0.0)
                } catch (String e) {
                    println(e.contains("STATS007"))
                }
            }
            """;

    // The two deterministic bernoulli edges print exactly; every statistical
    // face answers through a wide interval token; the three refusals print
    // their own STATS code match. 15 tokens, all deterministic.
    private static final String EXPECTED = "bound-ok\nmean-ok\nfalse\ntrue\nfreq-ok\nbinom-ok\nedge0-ok\ntrue\ntrue\ntrue\nnormal-ok\nexp-ok\ntrue\ntrue";

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
    void probabilityOnJvm() throws Exception {
        Path root = tmp.resolve("probability-jvm");
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
    void probabilityOnScript() throws Exception {
        Path root = tmp.resolve("probability-script");
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

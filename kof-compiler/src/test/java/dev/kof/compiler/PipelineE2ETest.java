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
 * Universal platform Stage 7.3 slice — the GENOMIC QC PIPELINE
 * ({@code libs/bio/Pipeline.kf}): the stats → filter → report pipeline shape
 * composing the Stage 7.1 readers — `sequenceStats` (per-record
 * header/length/gc), `qcFilter` (the AND-threshold filter step) and
 * `qcReport` (count/meanLength/meanGc; empty refuses QC001). Pure Kof over
 * the landed primitives.
 */
class PipelineE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "bio"; }

    @Override public List<String> libraryMarkers() { return List.of("Bio.kf", "Pipeline.kf"); }

    private static final String PROBE = """
            import bio.Bio
            import bio.Pipeline

            main() {
                var recs = readFasta(">s1\\nATGCGATT\\n>s2\\nGGAT\\n")
                var stats = sequenceStats(recs)
                println(stats.size())
                println(stats.get(0).length)
                println(stats.get(0).gc)
                var filtered = qcFilter(stats, 4, 0.45)
                println(filtered.size())
                println(filtered.get(0).header)
                var rep = qcReport(filtered)
                println(rep.count)
                if (rep.meanGc == 0.5) {
                    println("gc-ok")
                } else {
                    println("gc-bad")
                }
                if (rep.meanLength == 4.0) {
                    println("len-ok")
                } else {
                    println("len-bad")
                }
                try {
                    qcReport(listOf<SequenceStat>())
                } catch (String e) {
                    println(e.contains("QC001"))
                }
            }
            """;

    // 2 stats (s1: len 8, gc 3/8 = 0.375) ; the filter keeps ONLY s2
    // (len 4 >= 4, gc 0.5 >= 0.45) ; the report over it: count 1, meanGc
    // 0.5, meanLength 4.0 (narrowed — integral Doubles print as "4.0" or
    // "4" per target, so the equality is the deterministic face) ; an
    // empty report refuses QC001.
    private static final String EXPECTED = "2\n8\n0.375\n1\ns2\n1\ngc-ok\nlen-ok\ntrue";

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
    void pipelineOnJvm() throws Exception {
        Path root = tmp.resolve("pipeline-jvm");
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
    void pipelineOnScript() throws Exception {
        Path root = tmp.resolve("pipeline-script");
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

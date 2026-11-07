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
 * Universal platform Stage 7.3 slice — the WORKFLOW ORCHESTRATION of the
 * genomic QC pipeline ({@code libs/bio/Workflow.kf}): the stats → filter →
 * report shape of {@code Pipeline.kf} expressed as a real {@code kof.workflow}
 * DAG whose stages carry their data through durable TSV artifacts in the
 * caller's work directory. Faces: the happy run (allOk + the three stage
 * names in the summary + the {@code count=1} artifact), the empty-filtered
 * outcome recorded as {@code count=0} (a legitimate result — the pipeline
 * layer keeps refusing to fabricate), and the fault face (a BIO001 throw
 * inside the stats job lands the job in {@code failed} with its dependents
 * {@code skipped}). Pure Kof over the landed primitives.
 */
class PipelineWorkflowE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "bio"; }

    @Override public List<String> libraryMarkers() {
        return List.of("Bio.kf", "Pipeline.kf", "Workflow.kf");
    }

    private static final String PROBE_TEMPLATE = """
            import bio.Bio
            import bio.Pipeline
            import bio.Workflow
            import kof.workflow

            main() {
                var recs = readFasta(">s1\\nATGCGATT\\n>s2\\nGGATCCAA\\n")
                var flow = qcWorkflow("__WORK__/qc-run", recs, 4, 0.45)
                var rep = flow.run()
                println(rep.allOk())
                var s = rep.summary()
                println(s.contains("stats"))
                println(s.contains("filter"))
                println(s.contains("report"))
                var t1 = File("__WORK__/qc-run/qc-report.txt").readText()
                if (t1 != null) {
                    println(t1.contains("count=1"))
                } else {
                    println("missing")
                }
                var flow2 = qcWorkflow("__WORK__/qc-run2", recs, 4, 0.99)
                var rep2 = flow2.run()
                println(rep2.allOk())
                var t2 = File("__WORK__/qc-run2/qc-report.txt").readText()
                if (t2 != null) {
                    println(t2.contains("count=0"))
                } else {
                    println("missing")
                }
                var bad = listOf<FastaRecord>()
                bad.add(FastaRecord("bad", ""))
                var flow3 = qcWorkflow("__WORK__/qc-run3", bad, 4, 0.0)
                var rep3 = flow3.run()
                println(rep3.allOk())
                println(rep3.failed.contains("stats"))
                println(rep3.skipped.contains("filter"))
            }
            """;

    // s1 = ATGCGATT (len 8, gc 3/8 = 0.375), s2 = GGATCCAA (len 8, gc
    // 4/8 = 0.5) — both binary-exact fractions, so the TSV carrier text
    // is byte-identical per target. minLen 4 / minGc 0.45 keeps only s2
    // (count=1); minGc 0.99 empties the filter (count=0, still allOk);
    // the empty-sequence record makes the stats job throw BIO001, which
    // the workflow records as failed+skipped instead of propagating.
    private static final String EXPECTED =
            "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue\ntrue";

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

    private Path writeProbe(String probeName) throws Exception {
        String work = tmp.toAbsolutePath().toString().replace('\\', '/');
        String source = PROBE_TEMPLATE.replace("__WORK__", work);
        Path probe = tmp.resolve(probeName);
        Files.writeString(probe, source);
        Files.createDirectories(tmp.resolve("qc-run"));
        Files.createDirectories(tmp.resolve("qc-run2"));
        Files.createDirectories(tmp.resolve("qc-run3"));
        return probe;
    }

    @Test
    void pipelineWorkflowOnJvm() throws Exception {
        Path root = tmp.resolve("pwf-jvm");
        Files.createDirectories(root);
        Path probe = writeProbe("Main.kf");
        // the probe lands in tmp; the compile target and the work dirs stay
        // under the per-test roots so the three runs never collide.
        Files.move(probe, root.resolve("Main.kf"));
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
    void pipelineWorkflowOnScript() throws Exception {
        Path root = tmp.resolve("pwf-script");
        Files.createDirectories(root);
        Path probe = writeProbe("Main.kf");
        Files.move(probe, root.resolve("Main.kf"));
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

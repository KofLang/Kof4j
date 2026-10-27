package dev.kof.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-WORKFLOW-RUN (Stage 2 rows 2.5/2.6): {@code kof workflow} roda um
 * arquivo de pipeline ({@code pipeline(): KofWfDag}) — a DAG é REAL, o
 * runner sintetiza o main, o exit code reflete {@code Report.allOk()} e as
 * recusas são honestas (R6/R7). O pipeline é código Kof; o runner é tooling.
 */
class CmdWorkflowTest {

    private static Process startCli(Path workDir, String... cliArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("--enable-native-access=ALL-UNNAMED");
        cmd.add("--sun-misc-unsafe-memory-access=allow");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private record CliResult(int exit, String out) {}

    private static CliResult run(Path workDir, String... cliArgs) throws Exception {
        Process p = startCli(workDir, cliArgs);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "workflow cli timeout:\n" + out);
        return new CliResult(p.exitValue(), out);
    }

    private static Path pipeline(Path dir, String name, String body) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, body, StandardCharsets.UTF_8);
        return f;
    }

    private static final String CHAIN = """
        import kof.workflow
        KofWfDag pipeline() {
            var build = job("build", () -> true)
            var image = job("image", () -> true).after(build)
            return dag(listOf(image, build))
        }
        """;

    @Test
    void listShowsJobsAndDeps(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult human = run(dir, "workflow", "list", f.toString());
        assertEquals(0, human.exit(), human.out());
        assertTrue(human.out().contains("jobs: 2"), human.out());
        assertTrue(human.out().contains("  build"), human.out());
        assertTrue(human.out().contains("  image (after: build)"), human.out());

        CliResult json = run(dir, "workflow", "list", f.toString(), "--json");
        assertEquals(0, json.exit(), json.out());
        assertTrue(json.out().contains("\"jobs\""), json.out());
        assertTrue(json.out().contains("\"build\""), json.out());
    }

    @Test
    void runAllOkExitsZero(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult r = run(dir, "workflow", "run", f.toString());
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("ok=build,image failed= skipped="), r.out());
    }

    @Test
    void runFailureExitsOne(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "bad.kf", """
            import kof.workflow
            KofWfDag pipeline() {
                var build = job("build", () -> false)
                var image = job("image", () -> true).after(build)
                return dag(listOf(build, image))
            }
            """);
        CliResult r = run(dir, "workflow", "run", f.toString());
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("failed=build"), r.out());
        assertTrue(r.out().contains("skipped=image"), r.out());

        CliResult json = run(dir, "workflow", "run", f.toString(), "--json");
        assertEquals(1, json.exit(), json.out());
        assertTrue(json.out().contains("\"allOk\":false"), json.out());
    }

    @Test
    void dryRunPrintsOrderWithoutRunning(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult r = run(dir, "workflow", "run", f.toString(), "--dry-run");
        assertEquals(0, r.exit(), r.out());
        int build = r.out().indexOf("1. build");
        int image = r.out().indexOf("2. image");
        assertTrue(build >= 0 && image > build, r.out());
    }

    @Test
    void runJsTargetFacesJvmBytes(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult jvm = run(dir, "workflow", "run", f.toString());
        CliResult js = run(dir, "workflow", "run", f.toString(), "--target", "js");
        assertEquals(0, js.exit(), js.out());
        assertEquals(jvm.out(), js.out());
    }

    @Test
    void runJsFailureExitsOneLikeJvm(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "bad.kf", """
            import kof.workflow
            KofWfDag pipeline() {
                var good = job("good", () -> true)
                var bad = job("bad", () -> false).after(good)
                return dag(listOf(good, bad))
            }
            """);
        CliResult jvm = run(dir, "workflow", "run", f.toString());
        assertEquals(1, jvm.exit(), jvm.out());
        CliResult js = run(dir, "workflow", "run", f.toString(), "--target", "js");
        assertEquals(1, js.exit(), js.out());
        assertEquals(jvm.out(), js.out());
    }

    @Test
    void runJsDryRunAndJobFacesJvm(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult jvm = run(dir, "workflow", "run", f.toString(), "--dry-run");
        CliResult js = run(dir, "workflow", "run", f.toString(), "--dry-run", "--target", "js");
        assertEquals(jvm.out(), js.out());
        CliResult jvmJob = run(dir, "workflow", "run", f.toString(), "--job", "image");
        CliResult jsJob = run(dir, "workflow", "run", f.toString(), "--job", "image", "--target", "js");
        assertEquals(jvmJob.out(), jsJob.out());
    }

    @Test
    void jobRestrictsToTransitiveSubgraph(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "chain.kf", """
            import kof.workflow
            KofWfDag pipeline() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                var c = job("c", () -> true).after(b)
                return dag(listOf(c, b, a))
            }
            """);
        CliResult r = run(dir, "workflow", "run", f.toString(), "--job", "b");
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("ok=a,b failed= skipped="), r.out());
    }

    @Test
    void nonPipelineFilesAreRefusedHonestly(@TempDir Path dir) throws Exception {
        Path noImport = pipeline(dir, "plain.kf", "main() { println(\"hi\") }\n");
        CliResult a = run(dir, "workflow", "run", noImport.toString());
        assertEquals(1, a.exit(), a.out());
        assertTrue(a.out().contains("does not import kof.workflow"), a.out());

        Path noPipeline = pipeline(dir, "nop.kf", """
            import kof.workflow
            main() { println("hi") }
            """);
        CliResult b = run(dir, "workflow", "run", noPipeline.toString());
        assertEquals(1, b.exit(), b.out());
        assertTrue(b.out().contains("does not define pipeline()"), b.out());
    }

    @Test
    void unknownFlagAndNonJvmTargetAreRefused(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "pipe.kf", CHAIN);
        CliResult flag = run(dir, "workflow", "run", f.toString(), "--bogus");
        assertEquals(1, flag.exit(), flag.out());
        assertTrue(flag.out().contains("unknown or incomplete flag"), flag.out());

        CliResult nativeT = run(dir, "workflow", "run", f.toString(), "--target", "native");
        assertEquals(1, nativeT.exit(), nativeT.out());
        assertTrue(nativeT.out().contains("is not supported"), nativeT.out());

        CliResult jsOk = run(dir, "workflow", "run", f.toString(), "--target", "js");
        assertEquals(0, jsOk.exit(), jsOk.out());

        CliResult listJob = run(dir, "workflow", "list", f.toString(), "--job", "build");
        assertEquals(1, listJob.exit(), listJob.out());
        assertTrue(listJob.out().contains("only apply to 'run'"), listJob.out());
    }

    @Test
    void cycleFailsLoudWithActionableMessage(@TempDir Path dir) throws Exception {
        Path f = pipeline(dir, "cyc.kf", """
            import kof.workflow
            KofWfDag pipeline() {
                var a = job("a", () -> true)
                var b = job("b", () -> true).after(a)
                a.after(b)
                return dag(listOf(a, b))
            }
            """);
        CliResult r = run(dir, "workflow", "run", f.toString());
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("ciclo detectado entre: a,b"), r.out());
    }

    /** 2.5 E2E: the REAL example `examples/ci/ci-pipeline.kf` runs end to end
     *  — checkout/build/test/package with real filesystem artifacts, the
     *  topological order, and `--job test` running only its subgraph. The
     *  example is copied to a temp dir so the run leaves no build/ in the repo
     *  (this test is the golden that keeps the example valid). */
    @Test
    void realCiPipelineExampleRunsEndToEnd(@TempDir Path dir) throws Exception {
        Path example = repoExample();
        Assumptions.assumeTrue(example != null, "examples/ci/ci-pipeline.kf not found");
        Path copy = dir.resolve("ci-pipeline.kf");
        Files.copy(example, copy);

        CliResult list = run(dir, "workflow", "list", copy.toString());
        assertEquals(0, list.exit(), list.out());
        assertTrue(list.out().contains("jobs: 4"), list.out());
        assertTrue(list.out().contains("  package (after: test)"), list.out());

        CliResult dry = run(dir, "workflow", "run", copy.toString(), "--dry-run");
        assertEquals(0, dry.exit(), dry.out());
        assertTrue(dry.out().contains("1. checkout"), dry.out());
        assertTrue(dry.out().contains("4. package"), dry.out());

        CliResult full = run(dir, "workflow", "run", copy.toString());
        assertEquals(0, full.exit(), full.out());
        assertTrue(full.out().contains("ok=checkout,build,test,package"), full.out());
        assertTrue(Files.isRegularFile(dir.resolve("build/ci-demo/app.jar")),
                "artifact not produced:\n" + full.out());

        // --job test: only checkout+build+test; checkout wipes the workspace,
        // so the package artifact must NOT be recreated.
        CliResult job = run(dir, "workflow", "run", copy.toString(), "--job", "test");
        assertEquals(0, job.exit(), job.out());
        assertTrue(job.out().contains("ok=checkout,build,test failed= skipped="), job.out());
        assertTrue(!Files.exists(dir.resolve("build/ci-demo/app.jar")),
                "package must not run for --job test:\n" + job.out());
    }

    private static Path repoExample() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null) {
            Path ex = p.resolve("examples/ci/ci-pipeline.kf");
            if (Files.exists(ex)) return ex;
            p = p.getParent();
        }
        return null;
    }
}

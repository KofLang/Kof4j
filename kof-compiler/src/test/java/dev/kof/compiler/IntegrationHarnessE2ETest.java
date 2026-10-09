package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5 — integration harness: the temp-directory lifecycle + readiness poll
 * helpers, written in Kof (no new syntax/primitive, D-KOF-FIRST item 12) and
 * authorized by D-MAINT-BATCH-0610B/B ("temp dir / server / db lifecycle with
 * try/finally cleanup; Kof library, injected flat on import kof.test").
 *
 * <p>{@code withTempDir(dir, body)} creates the directory, runs the body and
 * recursively removes the tree afterwards even when the body throws (the
 * {@code finally}); {@code removeTree} is pure Kof because {@code
 * Directory.delete()} is not portable for a non-empty tree (JVM/Native delete
 * recursively, JS deletes only an empty directory — measured cross divergence).
 * {@code waitUntil(probe, attempts, intervalMs)} is the bounded readiness poll
 * for a resource that comes up asynchronously.
 *
 * <p>Parity: JVM + Native x86-64 + JS + riscv64/aarch64 (qemu). The cross legs
 * use a plain program because the cross harness does not link
 * {@code kof_process_exit}.
 */
class IntegrationHarnessE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String program(String base) {
        return """
                import kof.test

                main() {
                    var base = "%s"
                    withTempDir(base, (d: String) -> {
                        Directory(Path(d).resolve("sub")).createDirectories()
                        File(Path(d).resolve("a.txt")).writeText("hi")
                        File(Path(Path(d).resolve("sub")).resolve("b.txt")).writeText("deep")
                        println("inside exists=" + Directory(d).exists())
                        println("inside a=" + File(Path(d).resolve("a.txt")).readText())
                    })
                    println("after exists=" + Directory(base).exists())

                    var threw = false
                    try {
                        withTempDir(base + "-throw", (d: String) -> {
                            File(Path(d).resolve("x.txt")).writeText("y")
                            throw "boom"
                        })
                    } catch (String e) {
                        threw = true
                    }
                    println("threw=" + threw)
                    println("throw exists=" + Directory(base + "-throw").exists())

                    var n = 0
                    var ok = waitUntil(() -> {
                        n = n + 1
                        return n >= 3
                    }, 10, 1)
                    println("wait ok=" + ok + " polls=" + n)
                    println("wait never=" + waitUntil(() -> false, 3, 1))
                    println("wait zero=" + waitUntil(() -> true, 0, 1))
                }
                """.formatted(base);
    }

    private static final String GOLDEN = """
            inside exists=true
            inside a=hi
            after exists=false
            threw=true
            throw exists=false
            wait ok=true polls=3
            wait never=false
            wait zero=true""";

    private record Run(int exitCode, String output) {}

    private Run runJvm(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compileForTests(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    private Run runNative(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compileForTests(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    private Run runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compileForTests(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        return new Run(ec, out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim());
    }

    private Run runJvmProgram(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    private Run runCrossProgram(Path source, Path outDir, Target target, String arch)
            throws IOException, InterruptedException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch), "cross " + arch + " + qemu absent — skip");
        CompilationResult r = driver.compile(source, outDir, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " harness probe").replace("\r\n", "\n").trim();
        return new Run(p.exitValue(), out);
    }

    private Path write(Path tempDir, Path base) throws IOException {
        Path src = tempDir.resolve("H.kf");
        Files.writeString(src, program(base.toString()));
        return src;
    }

    @Test
    void tempDirLifecycleOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, tempDir.resolve("t-jvm"));
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals(GOLDEN, r.output(), "JVM oracle for the integration harness");
    }

    @Test
    void harnessMatchesJvmOnJs(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, tempDir.resolve("t-js"));
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run js = runJs(src, tempDir.resolve("out-js"));
        assertEquals(jvm.exitCode(), js.exitCode(), () -> "js: " + js.output());
        assertEquals(jvm.output(), js.output(), "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void harnessMatchesJvmOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, tempDir.resolve("t-nat"));
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run nat = runNative(src, tempDir.resolve("out-nat"));
        assertEquals(jvm.exitCode(), nat.exitCode(), () -> "native: " + nat.output());
        assertEquals(jvm.output(), nat.output(), "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void harnessMatchesJvmOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, tempDir.resolve("t-rv"));
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        assertEquals(GOLDEN, jvm.output(), "JVM oracle for the cross program");
        Run rv = runCrossProgram(src, tempDir.resolve("out-rv"), Target.NATIVE_RISCV64, "riscv64");
        assertEquals(jvm.exitCode(), rv.exitCode(), () -> "riscv64: " + rv.output());
        assertEquals(jvm.output(), rv.output(), "riscv64 must match the JVM oracle (rule 5)");
    }

    @Test
    void harnessMatchesJvmOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        Path src = write(tempDir, tempDir.resolve("t-aa"));
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        Run aa = runCrossProgram(src, tempDir.resolve("out-aa"), Target.NATIVE_AARCH64, "aarch64");
        assertEquals(jvm.exitCode(), aa.exitCode(), () -> "aarch64: " + aa.output());
        assertEquals(jvm.output(), aa.output(), "aarch64 must match the JVM oracle (rule 5)");
    }

    @Test
    void cleanupLeavesNoTraceOnDisk(@TempDir Path tempDir) throws Exception {
        Path base = tempDir.resolve("t-clean");
        Path src = write(tempDir, base);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(!Files.exists(base), "withTempDir must remove the tree on success");
        assertTrue(!Files.exists(tempDir.resolve("t-clean-throw")),
                "withTempDir must remove the tree when the body throws");
    }

    @Test
    void waitUntilIsBoundedAndHonestOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("W.kf");
        Files.writeString(src, """
                import kof.test

                main() {
                    var n = 0
                    println(waitUntil(() -> {
                        n = n + 1
                        return n >= 4
                    }, 100, 1))
                    println(n)
                    println(waitUntil(() -> false, 2, 1))
                    println(waitUntil(() -> true, 0, 1))
                }
                """);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals("true\n4\nfalse\ntrue", r.output(), () -> "output: " + r.output());
    }
}

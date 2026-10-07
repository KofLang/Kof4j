package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.6 — test doubles: the clock/random SEAMS (D-MAINT-BATCH-0610/D poll:
 * "só seams de clock/random"). Time- or randomness-dependent logic is tested
 * against an injected source, so the outcome is deterministic.
 *
 * Written in Kof (no new syntax/primitive, D-KOF-FIRST item 12); parity JVM +
 * Native x86-64 + JS.
 */
class TestSeamsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROGRAM = """
            import kof.test

            main() {
                var fixed = fixedClock(1234L)
                println(fixed())
                println(fixed())

                var scripted = scriptedClock(listOf(10L, 20L, 30L))
                println(scripted())
                println(scripted())
                println(scripted())
                println(scripted())
                println(scripted())

                var empty = scriptedClock(listOf())
                println(empty())

                var a = seededRandom(7)
                var b = seededRandom(7)
                println(a.next(100000) == b.next(100000))
                println(a.next(100000) == b.next(100000))
            }
            """;

    private static final String GOLDEN = """
            1234
            1234
            10
            20
            30
            30
            30
            0
            true
            true""";

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

    @Test
    void seamsAreDeterministicOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("S.kf");
        Files.writeString(src, PROGRAM);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals(GOLDEN, r.output(), "JVM oracle for the seams");
    }

    @Test
    void seamsMatchJvmOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("S.kf");
        Files.writeString(src, PROGRAM);
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run nat = runNative(src, tempDir.resolve("out-nat"));
        assertEquals(jvm.exitCode(), nat.exitCode(), () -> "native: " + nat.output());
        assertEquals(jvm.output(), nat.output(), "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void seamsMatchJvmOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("S.kf");
        Files.writeString(src, PROGRAM);
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run js = runJs(src, tempDir.resolve("out-js"));
        assertEquals(jvm.exitCode(), js.exitCode(), () -> "js: " + js.output());
        assertEquals(jvm.output(), js.output(), "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void scriptedClockExhaustionRepeatsLastOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("X.kf");
        Files.writeString(src, """
                import kof.test

                main() {
                    var s = scriptedClock(listOf(5L))
                    println(s())
                    println(s())
                    println(s())
                }
                """);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals("5\n5\n5", r.output(), () -> "output: " + r.output());
    }

    @Test
    void seededRandomIsReproducibleAcrossRuns(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("R.kf");
        Files.writeString(src, """
                import kof.test

                main() {
                    var a = seededRandom(99)
                    var b = seededRandom(99)
                    println(a.next(1000) == b.next(1000))
                    println(a.next(1000) == b.next(1000))
                }
                """);
        Run r1 = runJvm(src, tempDir.resolve("out1"));
        Run r2 = runJvm(src, tempDir.resolve("out2"));
        assertEquals("true\ntrue", r1.output(), () -> "output: " + r1.output());
        assertEquals(r1.output(), r2.output(), "same seed => same sequence on every run");
    }

    /** Plain-program oracle for the cross legs (the harness has no cross target). */
    private static final String CROSS_PROGRAM = """
            import kof.test

            main() {
                var fixed = fixedClock(77L)
                println(fixed())
                var scripted = scriptedClock(listOf(10L, 20L))
                println(scripted())
                println(scripted())
                println(scripted())
                var a = seededRandom(7)
                var b = seededRandom(7)
                println(a.next(100000) == b.next(100000))
                println(a.next(100000) == b.next(100000))
            }
            """;

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
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " seams probe").replace("\r\n", "\n").trim();
        return new Run(p.exitValue(), out);
    }

    @Test
    void seamsMatchJvmOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("C.kf");
        Files.writeString(src, CROSS_PROGRAM);
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        assertEquals("77\n10\n20\n20\ntrue\ntrue", jvm.output(), "JVM oracle for the cross program");
        Run rv = runCrossProgram(src, tempDir.resolve("out-rv"), Target.NATIVE_RISCV64, "riscv64");
        assertEquals(jvm.exitCode(), rv.exitCode(), () -> "riscv64: " + rv.output());
        assertEquals(jvm.output(), rv.output(), "riscv64 must match the JVM oracle (rule 5)");
    }

    @Test
    void seamsMatchJvmOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("C.kf");
        Files.writeString(src, CROSS_PROGRAM);
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        Run aa = runCrossProgram(src, tempDir.resolve("out-aa"), Target.NATIVE_AARCH64, "aarch64");
        assertEquals(jvm.exitCode(), aa.exitCode(), () -> "aarch64: " + aa.output());
        assertEquals(jvm.output(), aa.output(), "aarch64 must match the JVM oracle (rule 5)");
    }
}
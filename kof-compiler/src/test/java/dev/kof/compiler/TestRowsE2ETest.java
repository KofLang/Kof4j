package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.4 — parameterized tests: the additive {@code kof.test.testRows} helper
 * (D-MAINT-BATCH-0610/D). A table of {@code input -> expected} rows is evaluated
 * without duplicating one test per input; each row runs in isolation and the
 * failures aggregate into ONE named message the harness reports.
 *
 * Written in Kof (no new syntax/primitive, D-KOF-FIRST item 12); parity JVM +
 * Native x86-64 + JS (+ riscv64/aarch64 when the cross toolchain is present).
 */
class TestRowsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SUITE = """
            import kof.test

            test "square table" {
                testRows(listOf(listOf("1", "1"), listOf("2", "4"), listOf("3", "9")), "square", (r: List<String>) -> {
                    var input = r.get(0).toInt()
                    var square = input * input
                    if (square != r.get(1).toInt()) {
                        throw "expected " + r.get(1) + ", got " + square
                    }
                })
            }

            test "upper table" {
                testRows(listOf(listOf("a", "a"), listOf("b", "X"), listOf("c", "c")), "upper", (r: List<String>) -> {
                    if (r.get(0) != r.get(1)) {
                        throw "mismatch: " + r.get(0)
                    }
                })
            }

            main() {}
            """;

    /** Harness output for a passing table: both tests PASS, exit 0. */
    private static final String PASSING = """
            import kof.test

            test "passing table" {
                testRows(listOf(listOf("1", "1"), listOf("2", "4")), "square", (r: List<String>) -> {
                    var input = r.get(0).toInt()
                    if (input * input != r.get(1).toInt()) {
                        throw "expected " + r.get(1)
                    }
                })
            }

            main() {}
            """;

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

    /**
     * The cross natives are not `kof test` targets (the generated test harness
     * links `kof_process_exit`, absent on the cross runtime — §615); the helper
     * itself is proven cross with a plain program via {@code compile}.
     */
    private Run runCrossProgram(Path source, Path outDir, Target target, String arch)
            throws IOException, InterruptedException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch), "cross " + arch + " + qemu absent — skip");
        CompilationResult r = driver.compile(source, outDir, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " testRows probe").replace("\r\n", "\n").trim();
        return new Run(p.exitValue(), out);
    }

    @Test
    void passingTablePassesOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("P.kf");
        Files.writeString(src, PASSING);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS passing table"), () -> "output: " + r.output());
        assertTrue(r.output().contains("0 failed of 1 tests"), () -> "output: " + r.output());
    }

    @Test
    void failingTableNamesEveryBadRowOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("F.kf");
        Files.writeString(src, SUITE);
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "a failing test must exit 1; output: " + r.output());
        assertTrue(r.output().contains("PASS square table"), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL upper table: upper: 1 of 3 rows failed"),
                () -> "output: " + r.output());
        assertTrue(r.output().contains("row 1 [b, X]: mismatch: b"),
                () -> "the failing row must be named by index+content; output: " + r.output());
        assertTrue(r.output().contains("1 failed of 2 tests"), () -> "output: " + r.output());
    }

    @Test
    void rowsAreIsolatedTheGoodOnesStillRun(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("F.kf");
        Files.writeString(src, SUITE);
        Run r = runJvm(src, tempDir.resolve("out"));
        // exactly 1 of 3 rows failed -> the other two ran despite the failure
        assertTrue(r.output().contains("1 of 3 rows failed"),
                () -> "isolation: only the bad row is counted; output: " + r.output());
    }

    @Test
    void failingTableMatchesJvmOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("F.kf");
        Files.writeString(src, SUITE);
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run nat = runNative(src, tempDir.resolve("out-nat"));
        assertEquals(jvm.exitCode(), nat.exitCode(), () -> "native: " + nat.output());
        assertEquals(jvm.output(), nat.output(), "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void passingTableMatchesJvmOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("P.kf");
        Files.writeString(src, PASSING);
        Run jvm = runJvm(src, tempDir.resolve("out-jvm"));
        Run js = runJs(src, tempDir.resolve("out-js"));
        assertEquals(jvm.exitCode(), js.exitCode(), () -> "js: " + js.output());
        assertEquals(jvm.output(), js.output(), "JS must match the JVM oracle (rule 5)");
    }

    /** Plain-program oracle for the cross legs (the harness has no cross target). */
    private static final String CROSS_PROGRAM = """
            import kof.test

            main() {
                testRows(listOf(listOf("1", "1"), listOf("2", "4"), listOf("3", "9")), "square", (r: List<String>) -> {
                    var input = r.get(0).toInt()
                    if (input * input != r.get(1).toInt()) {
                        throw "expected " + r.get(1)
                    }
                })
                println("passing table ok")
                try {
                    testRows(listOf(listOf("a", "a"), listOf("b", "X"), listOf("c", "c")), "upper", (r: List<String>) -> {
                        if (r.get(0) != r.get(1)) {
                            throw "mismatch: " + r.get(0)
                        }
                    })
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
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

    @Test
    void helperMatchesJvmOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("C.kf");
        Files.writeString(src, CROSS_PROGRAM);
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        assertEquals("passing table ok\nupper: 1 of 3 rows failed\n  row 1 [b, X]: mismatch: b",
                jvm.output(), "JVM oracle for the cross program");
        Run rv = runCrossProgram(src, tempDir.resolve("out-rv"), Target.NATIVE_RISCV64, "riscv64");
        assertEquals(jvm.exitCode(), rv.exitCode(), () -> "riscv64: " + rv.output());
        assertEquals(jvm.output(), rv.output(), "riscv64 must match the JVM oracle (rule 5)");
    }

    @Test
    void helperMatchesJvmOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("C.kf");
        Files.writeString(src, CROSS_PROGRAM);
        Run jvm = runJvmProgram(src, tempDir.resolve("out-jvm"));
        Run aa = runCrossProgram(src, tempDir.resolve("out-aa"), Target.NATIVE_AARCH64, "aarch64");
        assertEquals(jvm.exitCode(), aa.exitCode(), () -> "aarch64: " + aa.output());
        assertEquals(jvm.output(), aa.output(), "aarch64 must match the JVM oracle (rule 5)");
    }
}
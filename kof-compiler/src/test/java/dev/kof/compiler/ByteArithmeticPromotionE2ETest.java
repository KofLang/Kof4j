package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #720 / known-bugs §561 — `Byte`/`Short`/`Char` arithmetic must promote
 * to `Int` (docs/language-reference/type-system.md §3.2: `double > float >
 * long > int`). Before the fix `TypeMetrics.commonNumericType` returned the
 * LEFT operand's sub-int type, so `Byte + Byte` outside [-128,127] crashed the
 * JVM (`Byte.valueOf` cache AIOOBE) while JS/Native already returned the
 * un-truncated Int. Maintainer decision (rule 6, 02/10): promote to `Int`.
 *
 * Golden = the JVM oracle. Every target must match it (parity rule 5).
 */
class ByteArithmeticPromotionE2ETest {

    static final String PROGRAM = """
            main() {
                var b: Byte = 65
                var x = b * 256
                println("x=" + x)
                var b2: Byte = 120
                println("c=" + (b2 + b2))
                var s: Short = 1000
                println("z=" + (s + s))
                var ch: Char = 'a'
                println("r=" + (ch + ch))
                var i = 100
                println("m=" + (b + i))
                var bb: Byte = 65
                bb = (bb + 1) as Byte
                println("b=" + bb)
            }
            """;

    static final String GOLDEN = """
            x=16640
            c=240
            z=2000
            r=194
            m=165
            b=66""";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private String runJvm(Path tempDir, String name) throws Exception {
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, name, Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        return out;
    }

    @Test
    void subIntArithmeticPromotesToIntOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("BP.kf");
        Files.writeString(src, PROGRAM);
        CompilationResult r = driver.compile(src, outDirFor(tempDir, "BP", Target.JVM), Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runJvm(tempDir, "BP"),
                "#720: Byte/Short/Char arithmetic must be Int (no Byte.valueOf cache crash)");
    }

    @Test
    void subIntArithmeticPromotesToIntOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("BJ.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outBJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void subIntArithmeticPromotesToIntOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("BS.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void subIntArithmeticPromotesToIntOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("BN.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outBN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    private void byteArithCross(Path tempDir, String name, Target target, String arch) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " + qemu ausente — pulando");
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("out-" + name);
        CompilationResult r = driver.compile(src, outDir, target);
        assertTrue(r.success(), name + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " byte-arith probe")
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), name + " run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, name + " must match the JVM oracle (parity rule 5)");
    }

    @Test
    void subIntArithmeticPromotesToIntOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        byteArithCross(tempDir, "BRV", Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void subIntArithmeticPromotesToIntOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        byteArithCross(tempDir, "BAR", Target.NATIVE_AARCH64, "aarch64");
    }
}

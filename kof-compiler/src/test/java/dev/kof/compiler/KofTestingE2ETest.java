package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.test primeiro slice (D-TESTING-PLATFORM, design `D-FUTURE-BATCH-2809B`)
 * — helpers de asserção aditivos, pacote virtual {@code kof.test}
 * (library-first, D-KOF-FIRST item 12). Contrato: nada de sintaxe estrangeira
 * (a superfície {@code test}/{@code assert} continua sendo a da linguagem);
 * cada helper falha com diagnóstico útil (label + esperado/atual) via
 * {@code throw} de String — mesmo caminho dos 4 alvos. Golden medido no
 * oráculo JVM; JS/Script/Native têm de bater (regra 5).
 */
class KofTestingE2ETest {

    static final String PROGRAM = """
            import kof.test

            main() {
                assertTrue(1 + 1 == 2, "add")
                assertFalse(1 == 2, "false")
                assertEqualInt(3, 3, "eqInt")
                assertEqualString("ab", "ab", "eqStr")
                assertNotEqualInt(1, 2, "neqInt")
                try {
                    assertEqualInt(1, 2, "boom")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualString("a", "b", "boomstr")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertTrue(false, "boomtrue")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    fail("boomfail")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                assertNull<String>(null, "null")
                assertNotNull<String>("x", "notnull")
                try {
                    assertNull<String>("x", "boomnull")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotNull<String>(null, "boomnotnull")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualBool(true, false, "boomeqbool")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualString("a", "a", "boomneqstr")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                assertThrows(() -> { throw "expected" }, "throws")
                try {
                    assertThrows(() -> { println("NOOP") }, "boomthrows")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                assertEqualLong(1234567890123, 1234567890123, "eqLong")
                assertNotEqualLong(1, 2, "neqLong")
                assertEqualDouble(3.5, 3.5, "eqDbl")
                assertNotEqualDouble(1.5, 2.5, "neqDbl")
                assertEqualFloat(1.5, 1.5, "eqFlt")
                assertNotEqualFloat(1.5, 2.5, "neqFlt")
                try {
                    assertEqualLong(10, 20, "boomeqLong")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualLong(7, 7, "boomneqLong")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualDouble(1.5, 2.5, "boomeqDbl")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualDouble(2.5, 2.5, "boomneqDbl")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualFloat(1.5, 2.5, "boomeqFlt")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualFloat(2.5, 2.5, "boomneqFlt")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                assertEqualByte(10, 10, "eqByte")
                assertNotEqualByte(10, 20, "neqByte")
                assertEqualShort(1000, 1000, "eqShort")
                assertNotEqualShort(1000, 2000, "neqShort")
                assertEqualChar('a', 'a', "eqChar")
                assertNotEqualChar('a', 'b', "neqChar")
                assertNotEqualBool(true, false, "neqBool")
                try {
                    assertEqualByte(10, 20, "boomeqByte")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualByte(10, 10, "boomneqByte")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualShort(1000, 2000, "boomeqShort")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualShort(1000, 1000, "boomneqShort")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertEqualChar('a', 'b', "boomeqChar")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualChar('a', 'a', "boomneqChar")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                try {
                    assertNotEqualBool(true, true, "boomneqBool")
                    println("NO-THROW")
                } catch (String e) {
                    println(e)
                }
                println("ALL PASS")
            }
            """;

    static final String GOLDEN =
            "assertion failed: boom (expected 1, got 2)\n"
          + "assertion failed: boomstr (expected \"a\", got \"b\")\n"
          + "assertion failed: boomtrue\n"
          + "assertion failed: boomfail\n"
          + "assertion failed: boomnull (expected null)\n"
          + "assertion failed: boomnotnull (expected non-null)\n"
          + "assertion failed: boomeqbool (expected true, got false)\n"
          + "assertion failed: boomneqstr (did not expect \"a\")\n"
          + "NOOP\n"
          + "assertion failed: boomthrows (expected an exception)\n"
          + "assertion failed: boomeqLong (expected 10, got 20)\n"
          + "assertion failed: boomneqLong (did not expect 7)\n"
          + "assertion failed: boomeqDbl (expected 1.5, got 2.5)\n"
          + "assertion failed: boomneqDbl (did not expect 2.5)\n"
          + "assertion failed: boomeqFlt (expected 1.5, got 2.5)\n"
          + "assertion failed: boomneqFlt (did not expect 2.5)\n"
          + "assertion failed: boomeqByte (expected 10, got 20)\n"
          + "assertion failed: boomneqByte (did not expect 10)\n"
          + "assertion failed: boomeqShort (expected 1000, got 2000)\n"
          + "assertion failed: boomneqShort (did not expect 1000)\n"
          + "assertion failed: boomeqChar (expected 'a', got 'b')\n"
          + "assertion failed: boomneqChar (did not expect 'a')\n"
          + "assertion failed: boomneqBool (did not expect true)\n"
          + "ALL PASS";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, outDirFor(tempDir, name, t), t);
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
    void assertionsRunOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "T", PROGRAM, Target.JVM);
        assertTrue(r.success(), "kof.test must compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runJvm(tempDir, "T"));
    }

    @Test
    void assertionsRunOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("TJ.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outTJ");
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
    void assertionsRunOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("TS.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void assertionsRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("TN.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outTN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void userDefinedAssertTrueDisablesInjection(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "TCOL", """
                import kof.test

                Void assertTrue(Bool cond, String label) {
                    println("user-defined")
                }

                main() {
                    assertTrue(true, "x")
                }
                """, Target.JVM);
        assertTrue(r.success(), "collision must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("user-defined", runJvm(tempDir, "TCOL"));
    }

    /** §549: `assertThrows` depende do pop do handler no caminho normal — o
     *  mesmo alvo que antes vazava. Prova cross riscv64/aarch64 (o bug existia
     *  também no cross; ver NativeTryHandlerLeakE2ETest). */
    private void assertionsMatchOnCross(Path tempDir, String name, Target target, String arch) throws Exception {
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
        String out = NativeRiscv64E2ETest.runBounded(p, arch + " kof.test probe").replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), name + " run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, name + " must match the JVM oracle (parity rule 5)");
    }

    @Test
    void assertionsRunOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        assertionsMatchOnCross(tempDir, "TRV", Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void assertionsRunOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        assertionsMatchOnCross(tempDir, "TAA", Target.NATIVE_AARCH64, "aarch64");
    }
}

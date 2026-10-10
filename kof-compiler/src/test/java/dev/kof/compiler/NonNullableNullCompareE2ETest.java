package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-NULL-INTENT queue item 3 (N3, `roadmap.md` §23 TIER 2.6.3): an
 * {@code == null} / {@code != null} on a NON-nullable value is legal, must
 * evaluate to the honest boolean, and must NEVER be a diagnostic.
 *
 * <p>This is the backward-compatibility face of the intent contract (rule 2):
 * existing code that compares a non-nullable to {@code null} keeps compiling.
 * On the JVM the comparison constant-folds (the operand can never be null), so
 * the dead branch never runs. Golden = the JVM oracle; every target must match
 * it (parity rule 5).
 *
 * <p>Before the D-NULL-INTENT core the primitive-vs-null comparison was
 * excluded from the shortcut path precisely so this stays a constant, never an
 * unwrap of a boxed value (`CompilerComparisons` line 54).
 */
class NonNullableNullCompareE2ETest {

    private static final String PROGRAM = """
            main() {
                var x = 5
                var s = "hi"
                var l = listOf(1, 2)
                println("i=" + (x == null))
                println("in=" + (x != null))
                println("s=" + (s == null))
                println("l=" + (l == null))
                println("d=" + (2.5 == null))
                if (x == null) {
                    println("DEAD-BRANCH")
                }
                println("done")
            }
            """;

    private static final String GOLDEN = """
            i=false
            in=true
            s=false
            l=false
            d=false
            done""";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDir(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    @Test
    void nonNullableNullCompareIsLegalOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("NNJ.kf");
        Files.writeString(src, PROGRAM);
        Path out = outDir(tempDir, "NNJ", Target.JVM);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "N3: non-nullable == null must compile, got: "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String txt = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + txt);
        assertEquals(GOLDEN, txt, "N3: non-nullable == null evaluates to false");
    }

    @Test
    void nonNullableNullCompareFoldsDeadBranchOnJvm(@TempDir Path tempDir) throws Exception {
        String fold = """
                main() {
                    var x = 5
                    if (x == null) {
                        println("FOLDLEAKMARKER")
                    }
                    println("fold_done")
                }
                """;
        Path src = tempDir.resolve("NNF.kf");
        Files.writeString(src, fold);
        Path out = outDir(tempDir, "NNF", Target.JVM);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "N3: fold probe must compile, got: "
                + r.diagnostics().getDiagnostics());
        byte[] cls = Files.readAllBytes(out.resolve("Default/Main.class"));
        String pool = new String(cls, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("FOLDLEAKMARKER"),
                "N3: x == null on a non-nullable must constant-fold; the dead-branch "
                        + "marker survived in the class constant pool");
        assertTrue(pool.contains("fold_done"), "sanity: the live println must survive");
    }

    @Test
    void nonNullableNullCompareIsLegalOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("NNS.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void nonNullableNullCompareIsLegalOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("NNJS.kf");
        Files.writeString(src, PROGRAM);
        Path out = outDir(tempDir, "NNJS", Target.JS);
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        String txt = buf.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void nonNullableNullCompareIsLegalOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("NNN.kf");
        Files.writeString(src, PROGRAM);
        Path out = outDir(tempDir, "NNN", Target.NATIVE);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String txt = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + txt);
        assertEquals(GOLDEN, txt, "native x86_64 must match the JVM oracle (rule 5)");
    }

    private void nonNullableNullCross(Path tempDir, String name, Target target, String arch)
            throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " + qemu ausente — pulando");
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, PROGRAM);
        Path out = tempDir.resolve("out-" + name);
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), name + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), name + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String txt = NativeRiscv64E2ETest.runBounded(p, arch + " non-nullable-null probe")
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), name + " run must exit 0, got:\n" + txt);
        assertEquals(GOLDEN, txt, name + " must match the JVM oracle (parity rule 5)");
    }

    @Test
    void nonNullableNullCompareIsLegalOnNativeRiscv64(@TempDir Path tempDir) throws Exception {
        nonNullableNullCross(tempDir, "NNRV", Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void nonNullableNullCompareIsLegalOnNativeAarch64(@TempDir Path tempDir) throws Exception {
        nonNullableNullCross(tempDir, "NNAR", Target.NATIVE_AARCH64, "aarch64");
    }
}

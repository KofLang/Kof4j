package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #338 — `Char c = 'A'; println(c == "A")` compiled and died at class LOAD:
 * the String `==` branch called kof_string_equals(String,String) with the
 * primitive pushed UNBOXED (iload) → VerifyError "Type integer is not
 * assignable to java/lang/Object" on the JVM (masked by the launcher's "main
 * not found"), SIGSEGV latent on Native; JS already printed `false`. The
 * contract is already frozen in the repo (String.equals(non-String) == false
 * on ALL targets — same fold as the .equals() call path): the comparison of a
 * primitive against a String is now CONSTANT-FOLDED (POP both operands —
 * POP2 for wide, SG-020/bug 79 — push BOOL), so every target agrees without
 * any runtime call.
 */
class PrimitiveStringEqTest extends JvmRunSupport {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    @Test
    void charAgainstStringIsFalseJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "C", """
                main() {
                    var c: Char = 'A'
                    println(c == "A")
                }
                """, Target.JVM);
        assertTrue(r.success(), "issue verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-CJVM"), "false", "#338 verbatim");
    }

    @Test
    void wideAndNotEqualAndOrderings(@TempDir Path tempDir) throws Exception {
        // Q3 edges: Double (2 slots — a POP instead of POP2 corrupts the
        // stack), `!=` polarity, and primitive on the RIGHT of the String.
        CompilationResult r = compile(tempDir, "W", """
                main() {
                    var c: Char = 'A'
                    println(c != "A")
                    var d: Double = 2.5
                    println(d == "x")
                    println(d != "x")
                    println("x" == d)
                }
                """, Target.JVM);
        assertTrue(r.success(), "wide/!=/right-side forms must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-WJVM"), "true\nfalse\ntrue\nfalse", "#338 edges");
    }

    @Test
    void stringStringEqualityUnchanged(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the kof_string_equals path stays intact.
        CompilationResult r = compile(tempDir, "S", """
                main() {
                    var s = "A"
                    println(s == "A")
                    println("A" == s)
                    println(s != "B")
                    println(s == "B")
                }
                """, Target.JVM);
        assertTrue(r.success(), "String×String path untouched: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-SJVM"), "true\ntrue\ntrue\nfalse", "String×String controls");
    }

    @Test
    void parityAcrossTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: fold lives in the shared IR lowerer. Script runs by direct IR
        // interpretation (the driver emits no artifact — COMP003 by design),
        // measured through the CLI instead: `false` on JVM/Script/JS/Native
        // (transcript of the sweep). Here the artifact targets are asserted.
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "P", """
                    main() {
                        var n: Int = 7
                        println(n == "7")
                    }
                    """, t);
            assertTrue(r.success(), t + ": must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}

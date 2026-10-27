package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 MATH-02: {@code math.min}/{@code max}/{@code clamp} and the
 *  {@code isEven}/{@code isOdd}/{@code isPositive}/{@code isNegative}/{@code isZero}
 *  family bound ANY argument type (arity-only check), so {@code math.min(1, 2.5)}
 *  passed {@code check} and died as a JVM {@code VerifyError} / a silent {@code 0}
 *  on native. The guards mirror {@code abs}/{@code sign}: a non-Int argument is
 *  refused at the binding layer with SEM025 (R6 — no silent widening).
 *
 *  <p>MATH-D (27/10, #776): the DOUBLE overloads for abs/min/max now EXIST
 *  (JVM = the exact Math.*, WASI = the IEEE instructions, JS = the
 *  math-double block — byte-identical, the F1 oracle) and floor/ceil joined
 *  the catalog — so the audit's minDD/maxDD/absD refusal faces FLIPPED to
 *  clean binds in the same commit (the stale-refusal law). What STAYS
 *  refused: the MIXED shapes (Int does not widen into Double — R6), clamp
 *  (no double variant yet), the predicate family, and String args. */
class KofMathTypeGuardTest {

    @TempDir
    Path tmp;

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out-" + tag), Target.JVM);
        return String.join("\n", r.diagnostics().getDiagnostics().stream()
                .map(Object::toString).toList());
    }

    @Test
    void mixedAndOutOfSliceArgsAreRefusedWithSem025() throws Exception {
        // the audit's MIXED faces (Int does not widen into Double — R6)
        assertTrue(diagOf("minMix", "main() {\n    println(\"\" + math.min(1, 2.5))\n}").contains("SEM025"),
                "math.min(Int, Double) must be refused at the binding layer");
        assertTrue(diagOf("clampD", "main() {\n    println(\"\" + math.clamp(1.5, 0.0, 2.0))\n}").contains("SEM025"),
                "math.clamp(Double, ...) must be refused (no double variant yet — MATH-D lifted only abs/min/max)");
        assertTrue(diagOf("isEvenD", "main() {\n    println(\"\" + math.isEven(2.5))\n}").contains("SEM025"),
                "math.isEven(Double) must be refused");
        assertTrue(diagOf("minStr", "main() {\n    println(\"\" + math.min(\"a\", \"b\"))\n}").contains("SEM025"),
                "math.min(String, String) must be refused (the audit's garbage-pointer face)");
        assertTrue(diagOf("floorI", "main() {\n    println(\"\" + math.floor(2))\n}").contains("SEM025"),
                "math.floor(Int) must be refused (Int does not widen — R6)");
        assertTrue(diagOf("signD", "main() {\n    println(\"\" + math.sign(1.5))\n}").contains("SEM025"),
                "math.sign(Double) must be refused (no double variant yet)");
    }

    @Test
    void intAndDoubleFacesStillBindCleanly() throws Exception {
        // the Int faces keep binding (the audit's invariant)
        assertTrue(diagOf("minII", "main() {\n    println(\"\" + math.min(1, 2))\n}").isEmpty(),
                "math.min(Int, Int) keeps binding");
        assertTrue(diagOf("maxII", "main() {\n    println(\"\" + math.max(1, 2))\n}").isEmpty(),
                "math.max(Int, Int) keeps binding");
        assertTrue(diagOf("clampIII", "main() {\n    println(\"\" + math.clamp(5, 0, 10))\n}").isEmpty(),
                "math.clamp(Int, Int, Int) keeps binding");
        assertTrue(diagOf("isEvenI", "main() {\n    println(\"\" + math.isEven(2))\n}").isEmpty(),
                "math.isEven(Int) keeps binding");
        // MATH-D (27/10): the double overloads BIND now (implemented on
        // JVM/JS/WASI — the F1 oracle is byte-identical); the old refusal
        // faces flipped in the same commit
        assertTrue(diagOf("absD", "main() {\n    println(\"\" + math.abs(1.5))\n}").isEmpty(),
                "math.abs(Double) binds (MATH-D)");
        assertTrue(diagOf("minDD", "main() {\n    println(\"\" + math.min(3.5, 2.5))\n}").isEmpty(),
                "math.min(Double, Double) binds (MATH-D)");
        assertTrue(diagOf("maxDD", "main() {\n    println(\"\" + math.max(1.0, 2.0))\n}").isEmpty(),
                "math.max(Double, Double) binds (MATH-D)");
        assertTrue(diagOf("floorD", "main() {\n    println(\"\" + math.floor(2.7))\n}").isEmpty(),
                "math.floor(Double) binds (MATH-D)");
        assertTrue(diagOf("ceilD", "main() {\n    println(\"\" + math.ceil(2.1))\n}").isEmpty(),
                "math.ceil(Double) binds (MATH-D)");
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KOF-SBD-001 — Array Bounds Safety.
 *
 * For every array A of length n and index i, an indexed read/write is
 * allowed only when {@code 0 <= i < n}; otherwise it must be rejected by
 * defined, controlled behavior on every target (never a silent out-of-bounds
 * read, an out-of-bounds write, or a silently-growing array).
 *
 * JVM already gets this for free (aaload/aastore/... bounds-check per JVM
 * spec §6.5). KofJS used to lower straight to {@code array[index]} /
 * {@code array[index] = value}, inheriting raw JavaScript array semantics
 * instead. Every case here is compiled to JVM and KofJS and executed; the
 * observable behavior must match on both targets.
 *
 * This does not claim Kof is a memory-safe language as a whole — only that
 * this one property (array bounds safety) holds.
 */
class ArrayBoundsSafetyE2ETest extends JvmJsRunSupport {

    // SBD-001-T01 — read at the first valid index.
    @Test
    void t01ReadFirstValidIndex(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(a[0])
                }
                """, "10", tempDir, "t01");
    }

    // SBD-001-T02 — read at the last valid index (length - 1).
    @Test
    void t02ReadLastValidIndex(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(a[2])
                }
                """, "30", tempDir, "t02");
    }

    // SBD-001-T03 — read at index == length must be rejected, not `undefined`.
    @Test
    void t03ReadAtLengthIsRejected(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    try {
                        var v = a[3]
                        println("no-throw:" + v)
                    } catch (String e) {
                        println("blocked")
                    }
                }
                """, "blocked", tempDir, "t03");
    }

    // SBD-001-T04 — read with a negative index must be rejected.
    @Test
    void t04ReadNegativeIndexIsRejected(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    try {
                        var v = a[-1]
                        println("no-throw:" + v)
                    } catch (String e) {
                        println("blocked")
                    }
                }
                """, "blocked", tempDir, "t04");
    }

    // SBD-001-T05 — write at index == length must be rejected.
    @Test
    void t05WriteAtLengthIsRejected(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    try {
                        a[3] = 99
                        println("no-throw")
                    } catch (String e) {
                        println("blocked")
                    }
                }
                """, "blocked", tempDir, "t05");
    }

    // SBD-001-T06 — write with a negative index must be rejected.
    @Test
    void t06WriteNegativeIndexIsRejected(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    try {
                        a[-1] = 99
                        println("no-throw")
                    } catch (String e) {
                        println("blocked")
                    }
                }
                """, "blocked", tempDir, "t06");
    }

    // SBD-001-T07 — a rejected write must not grow the array (KofJS-specific
    // regression: raw `array[array.length] = v` silently extends a JS array).
    @Test
    void t07RejectedWriteDoesNotGrowArray(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    a[0] = 1
                    a[1] = 2
                    a[2] = 3
                    try {
                        a[3] = 99
                    } catch (String e) {
                        // expected: rejected, array unchanged
                    }
                    println(a.length)
                    println(a[0] + a[1] + a[2])
                }
                """, "3\n6", tempDir, "t07");
    }

    // SBD-001-T08 — multidimensional arrays: an out-of-bounds access on an
    // inner dimension is rejected the same way as a flat array.
    @Test
    void t08MultidimensionalInnerBoundsEnforced(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[2][3]
                    a[0][0] = 1
                    a[0][1] = 2
                    a[0][2] = 3
                    try {
                        var v = a[0][5]
                        println("no-throw:" + v)
                    } catch (String e) {
                        println("blocked")
                    }
                    println(a[0][2])
                }
                """, "blocked\n3", tempDir, "t08");
    }

    // SBD-001-T09 — valid writes/reads keep working after the bounds check is
    // introduced (no regression on the golden path).
    @Test
    void t09ValidReadWriteStillWorks(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[2]
                    a[0] = 42
                    println(a[0])
                    a[0] = 99
                    println(a[0])
                    println(a[1])
                }
                """, "42\n99\n0", tempDir, "t09");
    }

    // SBD-001-T10 — compound assignment on an array element (`a[i] += v`,
    // GitHub #64) keeps working now that the load/store go through
    // kofArrayGet/kofArraySet instead of raw JsIndex.
    //
    // JVM only: KofJS has a PRE-EXISTING, unrelated compile crash for `+=`
    // on a raw-array element ("KofJS: unexpected op in expression statement:
    // KofDup2[]") — reproduces identically before this change (KofDup2 is
    // simply missing from JsExpressionStatementParser.isExpressionOp's
    // statement-boundary check), so it is untouched by this fix and out of
    // scope for SBD-001 (see docs/development/known-bugs.md #100). The
    // existing JVM-only regression for this case lives in
    // CoreRegressionE2ETest.compoundAssignmentOnArrayElementAndQualifiedStatic.
    @Test
    void t10CompoundAssignmentStillWorks(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("t10.kf");
        Files.writeString(source, """
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[0] += 5
                    println(a[0])
                }
                """);
        Path outJvm = tempDir.resolve("t10-jvm");
        CompilationResult rjvm = driver.compile(source, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        assertEquals("15", runJvm(outJvm));
    }
}

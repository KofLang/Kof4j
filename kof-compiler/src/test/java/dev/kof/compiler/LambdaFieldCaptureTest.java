package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #381 — a lambda that uses an instance field of the enclosing class
 * (`(x: Int) -> x * factor` inside `Multiplier.getMultiplier()`). The
 * capture collector only matched OUTER LOCALS, so `factor` stayed a free
 * name in the synthetic class and the identifier emitter's silent fallback
 * pushed the Lambda object itself (aload_0 of its own `this`): VerifyError
 * "Bad type on operand stack" on the JVM and "not an int: Lambda0@…" on
 * the script interpreter. The collector now captures a non-static
 * enclosing field with a sentinel-indexed synthetic capture and every
 * creation site pushes `this.getfield` there, where `this` is the real
 * receiver. Field capture is by VALUE (read at lambda construction);
 * locals keep their pre-existing semantics (mutation after creation is
 * visible — measured golden 102, not 13).
 */
class LambdaFieldCaptureTest extends JvmRunSupport {

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return new CompilerDriver().compile(source, tempDir.resolve("out-" + name + t), t);
    }

    
    private static final String VERBATIM = """
            class Multiplier {
                Int factor
                constructor(f: Int) { factor = f }
                getMultiplier(): (Int) -> Int {
                    return (x: Int) -> { return x * factor }
                }
            }
            main() {
                val m: Multiplier = Multiplier(3)
                val multiply: (Int) -> Int = m.getMultiplier()
                println(multiply(5))
            }
            """;

    @Test
    void lambdaCapturesEnclosingFieldByValue(@TempDir Path tempDir) throws Exception {
        // #381 verbatim — pre-fix compiled clean and died at LOAD (VerifyError
        // "Bad type on operand stack"), so the proof EXECUTES the oracle `15`.
        CompilationResult r = compile(tempDir, "V", VERBATIM, Target.JVM);
        assertTrue(r.success(), "#381 verbatim must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-VJVM"), "15", "#381 verbatim");
    }

    @Test
    void fieldPlusMutatedLocalKeepsPreExistingLocalSemantics(@TempDir Path tempDir) throws Exception {
        // faces: mixed capture (field + mutated local). The GOLDEN IS THE
        // MEASURED ORACLE `102` (2 + base 1 + step 99): locals were already
        // visible by reference pre-fix — r1 backward compatibility says the
        // field fix must NOT change that. Plain-lambda control `g` prints 8.
        CompilationResult r = compile(tempDir, "M", """
                class Counter {
                    Int base
                    constructor(b: Int) { base = b }
                    make(): (Int) -> Int {
                        var step = 10
                        val f = (x: Int) -> { return x + base + step }
                        step = 99
                        return f
                    }
                }
                main() {
                    val c: Counter = Counter(1)
                    val f = c.make()
                    println(f(2))
                    val g = (x: Int) -> { return x + 7 }
                    println(g(1))
                }
                """, Target.JVM);
        assertTrue(r.success(), "mixed capture must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-MJVM"), "102\n8", "#381 mixed capture");
    }

    @Test
    void wideAndReferenceFieldsCaptureCorrectly(@TempDir Path tempDir) throws Exception {
        // numeric edge: wide (Long) field capture and a reference (String)
        // field — the double-width slot sizing runs through the sentinel
        // path. Golden measured on the CLI oracle.
        CompilationResult r = compile(tempDir, "W", """
                class Box {
                    Long wide
                    String tag
                    constructor(w: Long, t: String) { wide = w; tag = t }
                    mix(): (Int) -> String {
                        return (n: Int) -> { return tag + (wide + n).toString() }
                    }
                }
                main() {
                    val b: Box = Box(10000000000, "v=")
                    println(b.mix()(5))
                }
                """, Target.JVM);
        assertTrue(r.success(), "wide/ref capture must compile: " + r.diagnostics().getDiagnostics());
        assertRuns(tempDir.resolve("out-WJVM"), "v=10000000005", "#381 wide+ref fields");
    }

    @Test
    void fieldCaptureCompilesOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // rule 5 parity: same IR feeds every backend — CLI measured `15` on
        // script/js/native with the fixed build; gate the compiler here.
        for (Target t : new Target[]{Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "P", VERBATIM, t);
            assertTrue(r.success(), t + " must compile: " + r.diagnostics().getDiagnostics());
        }
    }
}

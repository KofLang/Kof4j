package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1122: an extra argument to a callee that takes no parameters ({@code one(7)}
 * for {@code Int one()}, {@code f(7)} for {@code var f = () -> 1}) compiled
 * with no diagnostic; the argument stayed on the stack (JVM VerifyError,
 * Script/JS run-time errors). The shared argument check skipped every callee
 * with an empty parameter list; it now reports SEM013 like any other arity.
 */
class ZeroParamCallArityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String program, Target t) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + t), t);
    }

    private static void assertSem013(CompilationResult r, String callee) {
        assertFalse(r.success(), "an extra argument must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM013".equals(d.code())
                        && d.message().contains("'" + callee + "': expected 0 but got 1")),
                () -> "expected SEM013 for " + callee + ": " + r.diagnostics().getDiagnostics());
    }

    @Test
    void extraArgumentToZeroParamFunctionIsSem013OnEveryTarget(@TempDir Path tempDir) throws Exception {
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            assertSem013(compile(tempDir, """
                    Int one() { return 1 }
                    main() {
                        println(one(7))
                    }
                    """, t), "one");
        }
    }

    @Test
    void extraArgumentToZeroParamFunctionValueIsSem013(@TempDir Path tempDir) throws Exception {
        assertSem013(compile(tempDir, """
                main() {
                    var f = () -> 1
                    println(f(7))
                }
                """, Target.JVM), "f");
    }

    @Test
    void printlnInsideAUserZeroParamPrintlnIsSem013NotRecursion(@TempDir Path tempDir) throws Exception {
        // The user `println()` shadows the builtin (§931), so the inner call was
        // the user function with an extra argument: it recursed to StackOverflowError.
        assertSem013(compile(tempDir, """
                void println() {
                    println("inside")
                }
                main() {
                    println()
                }
                """, Target.JVM), "println");
    }

    @Test
    void zeroArgumentCallsStillCompile(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, """
                Int one() { return 1 }
                main() {
                    var f = () -> 2
                    println(one() + f())
                }
                """, Target.JVM);
        assertTrue(r.success(), () -> "zero-argument calls are valid: " + r.diagnostics().getDiagnostics());
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §629 — a bare TYPE NAME is not a value: `Int[3]` is not array allocation
 * (the surface is `new Int[n]`) and `var x = Int` has no value. Both used to
 * slip through the typer as {@code Unknown}, so the JVM backend emitted a
 * frame with a phantom operand and died in ASM {@code COMPUTE_FRAMES}
 * ({@code ArrayIndexOutOfBoundsException}/ {@code NegativeArraySizeException})
 * instead of a diagnostic (R6). Found while triaging issue #779.
 *
 * Fix: {@code SemExpressionTyper} reports {@code SEM103} for a builtin type
 * name in value position; `as`/`instanceof` type-refs are resolved before that
 * guard, so they keep compiling.
 */
class TypeNameAsValueE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String src) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        return driver.compile(source, tempDir.resolve("out"), Target.JVM);
    }

    private Diagnostic sem103(CompilationResult result) {
        return result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM103".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM103: " + result.diagnostics().getDiagnostics()));
    }

    @Test
    void typeNameIndexedIsNotArrayAllocation(@TempDir Path tempDir) throws Exception {
        // Pre-fix: RuntimeException "frame crash … ASM COMPUTE_FRAMES".
        CompilationResult result = compile(tempDir, """
                main() {
                    var x = Int[3]
                    x[0] = 7
                    println(x[0])
                }
                """);
        assertFalse(result.success(), "Int[3] must not compile");
        assertTrue(sem103(result).message().contains("new Int[n]"),
                () -> "SEM103 must point at `new Int[n]`: " + sem103(result).format());
    }

    @Test
    void bareTypeNameIsNotAValue(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                main() {
                    var x = Int
                    println(x)
                }
                """);
        assertFalse(result.success(), "`var x = Int` must not compile");
        assertTrue(sem103(result).message().contains("type, not a value"),
                () -> "SEM103 message: " + sem103(result).format());
    }

    @Test
    void allBuiltinTypeNamesAreGuarded(@TempDir Path tempDir) throws Exception {
        for (String t : new String[]{"String", "Double", "Bool", "Long", "Float", "Byte", "Short", "Char"}) {
            CompilationResult result = compile(tempDir, """
                    main() {
                        var x = %s[3]
                        x[0] = x[0]
                    }
                    """.formatted(t));
            assertFalse(result.success(), t + "[3] must not compile");
            assertTrue(sem103(result).message().contains(t),
                    () -> t + " SEM103 must name the type: " + sem103(result).format());
        }
    }

    @Test
    void declaredClassNameIndexedIsGuarded(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class Foo {
                    Int n
                }
                main() {
                    var x = Foo[3]
                    x[0] = x[0]
                }
                """);
        assertFalse(result.success(), "Foo[3] on a class name must not compile");
        assertTrue(sem103(result).message().contains("new Foo[n]"),
                () -> "SEM103 must point at `new Foo[n]`: " + sem103(result).format());
    }

    @Test
    void newArrayAllocationStillCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                main() {
                    var x = new Int[3]
                    x[0] = 7
                    println(x[0])
                }
                """);
        assertTrue(result.success(), () -> "new Int[3] must stay valid: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void asAndInstanceofTypeRefsStillCompile(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                main() {
                    var x = 3
                    var y = x as Int
                    var b = y instanceof Int
                    println(b)
                }
                """);
        assertTrue(result.success(), () -> "`as`/`instanceof` must keep compiling: "
                + result.diagnostics().getDiagnostics());
    }
}

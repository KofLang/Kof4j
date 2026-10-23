package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VoidReturnValuePositionTest {

    @TempDir
    Path tmp;

    private Diagnostic sem093(String src) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(r.success(), "a value returned from a void function must not compile");
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM093".equals(d.code()))
                .findFirst().orElseThrow(() -> new AssertionError("SEM093 expected: " + r.diagnostics().getDiagnostics()));
    }

    @Test
    void untypedFunctionReturnPointsAtTheReturn() throws Exception {
        Diagnostic d = sem093("""
                save(String s) {
                    println(s)
                    return s.length()
                }

                main() {
                    save("a")
                }
                """);
        assertEquals(3, d.line(), d.format());
        assertEquals(5, d.column(), d.format());
        assertTrue(d.format().contains("Main.kf:3:5"), d.format());
    }

    @Test
    void nestedReturnInVoidPointsAtTheReturn() throws Exception {
        Diagnostic d = sem093("""
                void firstPositive(Int x) {
                    if (x > 0) {
                        return x
                    }
                }

                main() {
                    firstPositive(1)
                }
                """);
        assertEquals(3, d.line(), d.format());
        assertEquals(9, d.column(), d.format());
    }

    @Test
    void constructorReturnPointsAtTheReturn() throws Exception {
        Diagnostic d = sem093("""
                class B {
                    Int v
                    constructor(Int a) {
                        v = a
                        return a
                    }
                }

                main() {
                    println(B(1).v)
                }
                """);
        assertEquals(5, d.line(), d.format());
        assertEquals(9, d.column(), d.format());
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §817 — {@code this.y = y} in a constructor (or {@code a.z = v}) on a class
 * that declares no such field compiled clean: the JVM died with
 * {@code NoSuchFieldError} at the first construction and Script/JS stored a
 * phantom field. The write now gets the SEM025 the read already had.
 */
class UndeclaredFieldWriteTest {

    @TempDir Path tmp;

    private CompilationResult compile(String src) throws Exception {
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, src);
        return new CompilerDriver().compile(f, tmp.resolve("out"), Target.JVM);
    }

    @Test
    void writingAnUndeclaredFieldIsSem025() throws Exception {
        CompilationResult r = compile("""
            class A {
                String x
                constructor(String x, String y) {
                    this.x = x
                    this.y = y
                }
            }
            main() {
                val a = A("p", "q")
                a.z = "w"
                println(a.x)
            }
            """);
        List<String> sem025 = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM025".equals(d.code())).map(d -> d.line() + ":" + d.message()).toList();
        assertEquals(List.of("5:Cannot resolve field 'y' on type 'A'", "10:Cannot resolve field 'z' on type 'A'"),
                sem025, "all diagnostics: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void inheritedKofAndJavaFieldsStillCompile() throws Exception {
        CompilationResult r = compile("""
            import java.util.ArrayList
            class Base {
                Int n = 0
            }
            class Child extends Base {
                constructor() { this.n = 2 }
            }
            class Counted extends ArrayList<String> {
                constructor() { this.modCount = 3 }
            }
            main() {
                val c = Child()
                c.n = 4
                println(c.n)
            }
            """);
        assertTrue(r.success(), "inherited fields are declared: " + r.diagnostics().getDiagnostics());
    }
}

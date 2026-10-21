package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeclaredTypePositionTest {

    private static final String SRC = """
            class Dog extends Animal {
                List<Missing> kids
                Thing bark(Other x) {
                    return null
                }
                constructor(Bogus b) {
                }
            }

            create(): Thing {
                return null
            }

            sum(Strng a, Int b): Int {
                return b
            }

            record Point(Xyz x, Int y)

            interface Shape extends Unknown {
            }

            class Crate<T: Foobar> {
            }

            entity User {
                name: Ghostly
            }

            main() {
                println("x")
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void everyDeclaredTypeErrorPointsAtItsDeclaration() throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(r.success());
        List<Diagnostic> ds = r.diagnostics().getDiagnostics();
        assertLine(ds, "superclass of 'Dog'", 1);
        assertLine(ds, "field 'kids'", 2);
        assertLine(ds, "return type of method 'bark'", 3);
        assertLine(ds, "parameter 'x' of method 'bark'", 3);
        assertLine(ds, "parameter 'b' of constructor 'Dog'", 6);
        assertLine(ds, "return type of function 'create'", 10);
        assertLine(ds, "parameter 'a' of function 'sum'", 14);
        assertLine(ds, "component 'x' of record 'Point'", 18);
        assertLine(ds, "interface of 'Shape'", 20);
        assertLine(ds, "type-parameter 'T' in class 'Crate'", 23);
        assertLine(ds, "field 'name' of entity 'User'", 27);
    }

    private static void assertLine(List<Diagnostic> ds, String where, int line) {
        Diagnostic d = ds.stream().filter(x -> x.message().contains(where)).findFirst()
                .orElseThrow(() -> new AssertionError("no diagnostic for " + where + ": " + ds));
        assertEquals(line, d.line(), d.format());
        assertTrue(d.column() > 0, d.format());
        assertTrue(d.format().startsWith(d.file()) && d.file().endsWith("Main.kf"), d.format());
    }
}

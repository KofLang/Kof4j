package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiUnknownInstanceMethodTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target target) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve(name + "-" + target.name().toLowerCase()), target);
    }

    private static void assertSem025(CompilationResult result, String fragment) {
        assertFalse(result.success(), "compilation must fail instead of dropping the call");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM025".equals(d.code()) && d.message().contains(fragment)),
                "expected SEM025 containing \"" + fragment + "\", was: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void canvasOnIsRejectedOnJsAndJvm(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var c = Canvas(10, 10)
                var l = Label("oi")
                c.on("pointerdown", (e: Event) -> { println("x") })
                var w = Window("t")
                w.bind(Column(listOf(c, l)))
                w.show()
            }
            """;
        assertSem025(compile(tempDir, "canvason", program, Target.JS), "'Canvas' does not have method 'on'");
        assertSem025(compile(tempDir, "canvason", program, Target.JVM), "'Canvas' does not have method 'on'");
    }

    @Test
    void misspelledWidgetMethodIsRejected(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile(tempDir, "typo", """
            main() {
                var b = Button("x", () -> {})
                b.setIdd("meu")
                println("ok")
            }
            """, Target.JS);
        assertSem025(result, "'Button' does not have method 'setIdd'");
    }

    @Test
    void knownUiMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var c = Canvas(10, 10)
                c.setFill(Color(255, 0, 0))
                c.beginPath()
                c.moveTo(0, 0)
                c.lineTo(5, 5)
                c.fill()
                var b = Button("x", () -> {})
                b.on("click", (e: Event) -> { println(e.type()) })
                b.setId("meu")
                var w = Window("t")
                w.bind(Column(listOf(c, b)))
                w.show()
                println("ok")
            }
            """;
        for (Target t : new Target[] {Target.JS, Target.JVM}) {
            CompilationResult result = compile(tempDir, "known", program, t);
            assertTrue(result.success(), t + ": " + result.diagnostics().getDiagnostics());
        }
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class-body field/method disambiguation: a field whose initializer is omitted
 * (`String title`) must not swallow a following member that begins with `(` — a
 * function-typed field (`() -> Long src = null`) or a function-typed return.
 *
 * <p>Before the fix the `(` on the NEXT line was read as this field's parameter
 * list (`String title()`), so the `->` of the function type died
 * {@code PARSE016} in the class body. The same-line guard mirrors the
 * trailing-lambda guard of {@code known-bugs} §692: the token that belongs to
 * the next member must not be consumed. A real method always writes `name(` on
 * one line, so the guard is additive.
 *
 * <p>The graphics/gaming slice 3.1 window host (`libs/game/Window.kf`) needs
 * exactly this shape: an uninitialized `String windowTitle` field followed by
 * the function-typed `() -> Long clockSource = null`.
 */
class ClassMemberParseE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void uninitializedFieldThenFunctionTypedFieldParsesOnJvm() throws Exception {
        String code = """
                class Holder {
                    String title
                    () -> Long src = null

                    Holder setValue(Long v) {
                        this.src = () -> v
                        return this
                    }

                    Long read() {
                        var f = this.src
                        if (f != null) { return f() }
                        return 0
                    }
                }

                main() {
                    var h = Holder()
                    h.setValue(42)
                    println("read=" + h.read())
                }
                """;
        assertEquals("read=42", runJvm(code));
    }

    @Test
    void uninitializedFieldThenFunctionTypedFieldParsesOnScript() throws Exception {
        String code = """
                class Holder {
                    String title
                    () -> Long src = null

                    Long read() {
                        var f = this.src
                        if (f != null) { return f() }
                        return 0
                    }
                }

                main() {
                    println("ok=" + Holder().read())
                }
                """;
        assertEquals("ok=0", runScript(code));
    }

    @Test
    void initializedFieldThenFunctionTypedFieldStillParses() throws Exception {
        String code = """
                class Holder {
                    Int a = 0
                    () -> Long src = null
                }

                main() {
                    println("ok")
                }
                """;
        assertEquals("ok", runJvm(code));
    }

    @Test
    void methodWithParenthesizedReturnTypeStillParses() throws Exception {
        // Control: a method whose parameters are on the same line as its name is
        // unaffected, and a function-typed return still works.
        String code = """
                class Holder {
                    (Int) -> Int mapper = null

                    Int apply(Int x) {
                        var f = this.mapper
                        if (f != null) { return f(x) }
                        return x
                    }
                }

                main() {
                    println("ok=" + Holder().apply(7))
                }
                """;
        assertEquals("ok=7", runJvm(code));
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(source, out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runScript(String code) throws Exception {
        Path root = tmp.resolve("script-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }
}

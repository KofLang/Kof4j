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
 * Trailing lambdas with explicit parameters must work on every call shape, not
 * only the {@code receiver.method { … }} form. The parser's bare-identifier and
 * parenthesized-argument branches only accepted a 0-parameter block, so
 * {@code f { dt: Int -> … }} and {@code f(1) { dt: Int -> … }} failed with
 * {@code PARSE041} even though {@code receiver.method { dt: Int -> … }} parsed.
 *
 * <p>The graphics/gaming slice 3.1 window form chosen by the maintainer
 * ({@code D-GRAPHICS-WINDOW-FORM}) is written exactly as
 * {@code Window("Pong") { frame { dt: Int -> … } }} — a parenthesized-call
 * trailing lambda whose body contains a bare trailing lambda with a typed
 * parameter — so this gap blocked the plan's literal syntax. The nested shape is
 * pinned here beside the flat forms and the 0-parameter regression.
 */
class TrailingLambdaParamsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void bareTrailingLambdaWithTypedParamOnJvm() throws Exception {
        assertEquals("bare=7", runJvm(bareProbe(), "bare"));
    }

    @Test
    void bareTrailingLambdaWithTypedParamOnScript() throws Exception {
        assertEquals("bare=7", runScript(bareProbe(), "bare"));
    }

    @Test
    void callArgsTrailingLambdaWithTypedParamOnJvm() throws Exception {
        assertEquals("args=30", runJvm(argsProbe(), "args"));
    }

    @Test
    void nestedWindowShapeTrailingLambdaOnJvm() throws Exception {
        assertEquals("dt=0", runJvm(windowProbe(), "window"));
    }

    @Test
    void nestedWindowShapeTrailingLambdaOnScript() throws Exception {
        assertEquals("dt=0", runScript(windowProbe(), "window"));
    }

    @Test
    void zeroParamTrailingLambdaStillWorksOnJvm() throws Exception {
        assertEquals("zero", runJvm(zeroProbe(), "zero"));
    }

    private static String bareProbe() {
        return """
                void bare((Int) -> Void f) {
                    f(7)
                }

                main() {
                    bare { dt: Int -> println("bare=" + dt) }
                }
                """;
    }

    private static String argsProbe() {
        return """
                void withArgs(Int a, (Int) -> Void f) {
                    f(a * 10)
                }

                main() {
                    withArgs(3) { dt: Int -> println("args=" + dt) }
                }
                """;
    }

    private static String windowProbe() {
        return """
                class Window {
                    String title
                    Int ticks

                    constructor(title: String, body: (Window) -> Void) {
                        this.title = title
                        this.ticks = 0
                        body(this)
                    }

                    void frame((Int) -> Void f) {
                        f(this.ticks)
                        this.ticks = this.ticks + 1
                    }
                }

                main() {
                    Window("Pong") { w: Window -> w.frame { dt: Int -> println("dt=" + dt) } }
                }
                """;
    }

    private static String zeroProbe() {
        return """
                void zero(() -> Void f) {
                    f()
                }

                main() {
                    zero { println("zero") }
                }
                """;
    }

    private String runJvm(String code, String label) throws Exception {
        Path root = tmp.resolve("jvm-" + label);
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

    private String runScript(String code, String label) throws Exception {
        Path root = tmp.resolve("script-" + label);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        return result.stdout().strip();
    }
}

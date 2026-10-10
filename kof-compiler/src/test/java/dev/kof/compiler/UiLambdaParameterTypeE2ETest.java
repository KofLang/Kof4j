package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * issue #710 — a lambda PARAMETER declared with a {@code kof.ui} type
 * ({@code (w: Label) -> …}) is mis-typed. This is the lambda-parameter face of
 * the §179 family (a declared {@code kof.ui}/{@code kof.media} type breaks the
 * JVM backend); §179 fixed signature/var/param/field but lambda parameters are
 * lowered on a separate path ({@link CompilerLambdaClass}, {@code le.parameters()})
 * that never runs the same qualification, so the synthetic SAM interface and
 * the call site disagree on the descriptor.
 */
class UiLambdaParameterTypeE2ETest extends ComponentCoreSupport {

    private static final String PROGRAM = """
        main() {
            var l = Label("oi")
            var h = (w: Label) -> { w.setText("y") }
            h(l)
            println(l.text())
        }
        """;

    @Test
    void lambdaParameterTypedWithUiTypeRunsOnJvm(@TempDir Path tempDir) throws IOException {
        // JVM/Native `kof.ui` is a no-op handle (CSS-first, real only in KofJS):
        // the fix is proven by the JVM no longer aborting with
        // `NoSuchMethodError: 'void Lambda0.invoke(int)'` and exiting 0.
        Path source = tempDir.resolve("lambdaui.kf");
        Files.writeString(source, PROGRAM);
        runJvm(source, tempDir.resolve("jvm"), "");
    }

    @Test
    void lambdaParameterTypedWithUiTypeStillRunsOnJs(@TempDir Path tempDir) throws IOException {
        assertEquals("y", runJs(tempDir, "lambdaui", PROGRAM),
                "a kof.ui lambda parameter must run correctly on JS too");
    }

    @Test
    void lambdaParameterTypedWithUiTypeStillRunsOnNative(@TempDir Path tempDir) throws IOException {
        // pre-fix the native link failed (`invoke` with the wrong descriptor);
        // post-fix it compiles and exits 0 (no-op handle).
        Path source = tempDir.resolve("lambdaui-native.kf");
        Files.writeString(source, PROGRAM);
        runNative(source, tempDir.resolve("native"), "");
    }

    @Test
    void lambdaParameterTypedWithUserRecordStaysCorrect(@TempDir Path tempDir) throws IOException {
        // Control from the report: a plain user record lambda parameter is
        // correct and must not regress.
        String program = """
            record Point(Int x, Int y)
            main() {
                var h = (p: Point) -> { println(p.x()) }
                h(Point(3, 4))
            }
            """;
        Path source = tempDir.resolve("recordparam.kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm2"), "3");
    }

    @Test
    void userClassShadowsUiLambdaParameterStaysGreen(@TempDir Path tempDir) throws IOException {
        // §179 shadowing guard: a user class named `Label` wins over the builtin.
        String program = """
            class Label {
                String v() { return "meu" }
            }
            main() {
                var h = (w: Label) -> { println(w.v()) }
                h(Label())
            }
            """;
        Path source = tempDir.resolve("shadowparam.kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm3"), "meu");
    }
}

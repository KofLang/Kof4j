package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #711 — a method that does not exist on a {@code kof.ui} instance
 * ({@code canvas.on(...)}, a misspelled {@code setIdd(...)}, ...) compiled
 * cleanly but was SILENTLY DROPPED: {@code emitUiInstance} returned without
 * emitting anything and left the receiver on the operand stack (JS emitted
 * {@code (c, kofUiWindowBind(w, root))}; JVM dangled the value). R6 violation.
 *
 * <p>The fix reports {@code SEM025} (the unknown-method family already used for
 * arrays/§168) and pops the receiver — diagnostics only, no method is added to
 * the language surface. While testing, two existing tests used calls the
 * compiler never mapped although the runtime implements them on JVM/Native/JS
 * ({@code Fieldset.remove()}, {@code Component.mount()}); those are mapped so
 * the tests keep passing for the right reason.</p>
 */
class UiUnknownMethodDiagnosticE2ETest {

    private CompilationResult compile(String source, Path tempDir, Target target) throws IOException {
        Files.createDirectories(tempDir);
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return new CompilerDriver().compile(src, tempDir.resolve("out"), target);
    }

    private void assertSem025(CompilationResult result, String method) {
        assertFalse(result.success(), "unknown kof.ui method must fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM025"), "Expected SEM025, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
        assertFalse(diags.contains("VerifyError"),
                "Must be a compile diagnostic, not a crash: " + diags);
    }

    // ---- diagnosis: the silent drop becomes a compile-time SEM025 ----

    @Test
    void canvasOnIsDiagnosedNotSilentlyDropped(@TempDir Path tempDir) throws IOException {
        assertSem025(compile("""
            main() {
                var c = Canvas(10, 10)
                c.on("pointerdown", (e: Event) -> { println("x") })
            }
            """, tempDir, Target.JVM), "on");
    }

    @Test
    void canvasOnFailsOnJsToo(@TempDir Path tempDir) throws IOException {
        // pre-fix the JS output silently reordered the receiver; the diagnostic
        // is emitted during lowering, so it must fire on every target.
        assertSem025(compile("""
            main() {
                var c = Canvas(10, 10)
                c.on("pointerdown", (e: Event) -> { println("x") })
            }
            """, tempDir, Target.JS), "on");
    }

    @Test
    void misspelledWidgetMethodIsDiagnosed(@TempDir Path tempDir) throws IOException {
        assertSem025(compile("""
            main() {
                var b = Button("x")
                b.setIdd("meu")
            }
            """, tempDir, Target.JVM), "setIdd");
    }

    @Test
    void unknownWindowMethodIsDiagnosed(@TempDir Path tempDir) throws IOException {
        assertSem025(compile("""
            main() {
                var w = Window("t")
                w.bogus()
            }
            """, tempDir, Target.JVM), "bogus");
    }

    // ---- mapping: the two calls the runtime already implements ----

    @Test
    void fieldsetRemoveAndComponentMountAreMapped() {
        assertNotNull(KofUi.instanceMethod(KofUi.FIELDSET, "remove", 0),
                "Fieldset.remove() must map to the runtime helper (#711)");
        assertNotNull(KofUi.instanceMethod(KofUi.COMPONENT, "mount", 0),
                "Component.mount() must map to the runtime helper (#711)");
    }

    // ---- counter-proof: valid UI calls keep compiling ----

    @Test
    void validUiCallsStillCompile(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var c = Canvas(10, 10)
                c.setFill(Color(255, 0, 0))
                c.fill()
                var b = Button("x")
                b.setId("meu")
                b.on("click", () -> { println("hi") })
                var f = Fieldset("fs")
                f.remove()
                println("ok")
            }
            """;
        assertTrue(compile(program, tempDir.resolve("jvm"), Target.JVM).success(),
                "valid UI calls must still compile on JVM");
        assertTrue(compile(program, tempDir.resolve("js"), Target.JS).success(),
                "valid UI calls must still compile on JS");
    }

    @Test
    void validUiProgramStillRunsOnJvm(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var c = Canvas(10, 10)
                c.setFill(Color(255, 0, 0))
                c.fill()
                var b = Button("x")
                b.setId("meu")
                b.on("click", () -> { println("hi") })
                println("ok")
            }
            """;
        Path out = tempDir.resolve("out");
        CompilationResult result = compile(program, tempDir, Target.JVM);
        assertTrue(result.success(), "should compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit code, output: '" + output + "'");
            assertEquals("ok", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA-gate probe (15/10, eixo strings): um método que não existe em String
 * ({@code "abc".upper()} — o contrato é {@code toUpperCase},
 * type-system.md §string-methods) compilava SEM diagnóstico e o emissor
 * JVM despachava o descritor fallback {@code ()Object} —
 * {@code NoSuchMethodError} em runtime (Native link-fail, JS TypeError).
 * É a família do precedente §145/#101 (isEmpty fora do registro) e da
 * #711 (método desconhecido em kof.ui). R6: method resolution em receptor
 * tipado não pode ser silenciosa.
 *
 * <p>O fix reporta {@code SEM025} (a mesma família usada para List/Map/
 * kof.ui) em vez de emitir a chamada quebrada — diagnóstico honesto, nenhum
 * método novo entra na superfície.</p>
 */
class StringUnknownMethodDiagnosticE2ETest {

    private CompilationResult compile(String source, Path tempDir, Target target) throws IOException {
        Files.createDirectories(tempDir);
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return new CompilerDriver().compile(src, tempDir.resolve("out"), target);
    }

    private void assertSem025(CompilationResult result, String method) {
        assertFalse(result.success(), "unknown String method must fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM025"), "Expected SEM025, was: " + diags);
        assertTrue(diags.contains(method), "Diagnostic must name the method, was: " + diags);
    }

    @Test
    void upperIsDiagnosedNotSilentlyBroken(@TempDir Path tempDir) throws IOException {
        assertSem025(compile("""
            main() {
                var t = "abc"
                println(t.upper())
            }
            """, tempDir, Target.JVM), "upper");
    }

    @Test
    void unknownMethodFailsOnJsToo(@TempDir Path tempDir) throws IOException {
        // o diagnóstico é emitido no lowering — deve disparar em todo alvo
        assertSem025(compile("""
            main() {
                var t = "abc"
                println(t.frobnicate())
            }
            """, tempDir, Target.JS), "frobnicate");
    }

    // ---- counter-proof: valid String calls keep compiling ----

    @Test
    void validStringCallsStillCompile(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var t = "Hello, Kof"
                println(t.toUpperCase())
                println(t.toLowerCase())
                println(t.substring(7))
                println(t.contains("Kof"))
                println(t.replace("Kof", "World"))
                println(t.length)
                println(t.isEmpty())
                println(t.split(",")[1])
                println(t.trim())
                println(t.indexOf("Kof"))
                println("ok")
            }
            """;
        assertTrue(compile(program, tempDir.resolve("jvm"), Target.JVM).success(),
                "valid String calls must still compile on JVM");
        assertTrue(compile(program, tempDir.resolve("js"), Target.JS).success(),
                "valid String calls must still compile on JS");
    }
}

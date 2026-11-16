package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #1167 — a USER class named {@code Handle} must win over the builtin
 * {@code kof.concurrent.Handle}, and the false SEM102 ("use {@code await h}")
 * must never fire on it.
 *
 * <p>The regression the maintainer measured: the out-param landing
 * ({@code 8f8bfe8f8}) added a SHADOWING-BLIND global map in {@code Type.of}
 * ({@code case "Handle" -> kof.concurrent.Handle}). {@code Type.of} has no
 * compilation-unit context, so every caller got the builtin — including a
 * program that DECLARES {@code class Handle} (the natural ownership idiom).
 * A function returning the user class was typed as the CONCURRENT handle, and
 * {@code x.id} on it died with SEM102 "use {@code await h}".
 *
 * <p>This test pins BOTH directions: the user class wins (and {@code x.id}
 * compiles clean, on every target that compiles it), AND the legitimate pin
 * survives for a program that does NOT declare {@code Handle} (the pcap
 * {@code new Handle[1]} face — covered by {@code PcapNextExDeadE2ETest}, whose
 * non-regression this file also asserts by name).
 */
class Issue1167UserHandleShadowingE2ETest {

    /** The issue's exact shape: a user class, a factory returning it, a field read. */
    private static final String USER_HANDLE = """
            class Handle {
                Int id
                public constructor(Int id) { this.id = id }
                void close() { }
                Int get() { return id }
            }
            Handle make() {
                var h = Handle(21)
                return h
            }
            main() {
                var x = make()
                println(x.id)
            }
            """;

    private static CompilationResult compile(Path dir, String tag, String source, CompilerDriver d, Target t)
            throws IOException {
        Files.createDirectories(dir);
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, source);
        return d.compile(src, dir.resolve("out-" + tag), t);
    }

    @Test
    void aUserClassNamedHandleCompilesCleanOnJvm() throws IOException {
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1167-jvm");
        CompilationResult r = compile(dir, "Main", USER_HANDLE, d, Target.JVM);
        var errors = r.diagnostics().getDiagnostics().stream()
                .filter(diag -> "ERROR".equals(String.valueOf(diag.severity()))).toList();
        assertTrue(errors.isEmpty(),
                "a `class Handle` do usuário deve compilar limpo — o falso SEM102"
                        + " \"use `await h`\" voltou: " + errors);
        assertFalse(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(diag -> diag.message() != null
                                && diag.message().contains("await h")),
                "nenhum diagnóstico pode sugerir `await h` para uma classe do usuário");
    }

    @Test
    void theUserHandleFieldReadProducesTheValue() throws IOException, InterruptedException {
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1167-run");
        CompilationResult r = compile(dir, "Main", USER_HANDLE, d, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Path out = dir.resolve("out-Main");
        var p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes()).strip();
        assertEquals(0, p.waitFor(), "run: " + text);
        assertEquals("21", text, "o campo da classe do usuário lê o valor, não um handle");
    }

    @Test
    void theLegitimatePinSurvivesWithoutAUserHandle() throws IOException {
        // A program that does NOT declare Handle keeps the builtin pin: the
        // pcap `new Handle[1]` face must still compile (no NoClassDefFoundError
        // shape). This is the non-regression the fix must NOT break.
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1167-pin");
        String src = """
                extern "libc.so.6" malloc(Long n): Handle
                main() {
                    var slots = new Handle[1]
                    println(slots.length)
                }
                """;
        CompilationResult r = compile(dir, "Main", src, d, Target.JVM);
        assertTrue(r.success(), "o `new Handle[1]` sem classe do usuário deve pinar o"
                + " builtin e compilar: " + r.diagnostics().getDiagnostics());
    }
}

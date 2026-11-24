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
 * #1198 — a user class named {@code Video}/{@code Image}/{@code Audio}/
 * {@code Mic} must win over the homonymous builtin media namespace when it is
 * the receiver of a static field, INCLUDING inside its own body.
 *
 * <p>{@code SemExpressionTyper}'s {@code IdentifierExpr} case never consulted
 * the module's class table: a bare type name used as a receiver typed as
 * {@code UnknownType}, and the SEM102 builtin-namespace guard
 * ({@code KofMedia.isStaticNamespace}) then fired on the user class. Outside
 * the class body a different path resolved it, so the same expression
 * compiled at top level and failed inside.
 *
 * <p>Pins BOTH directions: the user class wins (all four names, static field
 * read from inside and outside the body), AND the legitimate namespace
 * diagnostic survives for a program that does NOT declare the class.
 */
class Issue1198MediaNamespaceShadowingE2ETest {

    private static final String CLASS_BODY_TEMPLATE = """
            class %s {
                static Int LIMITE_BYTES = 25000000
                static Int limite() { return %s.LIMITE_BYTES }
            }
            main() {
                println(%s.limite())
            }
            """;

    private static CompilationResult compile(Path dir, String tag, String source, CompilerDriver d, Target t)
            throws IOException {
        Files.createDirectories(dir);
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, source);
        return d.compile(src, dir.resolve("out-" + tag), t);
    }

    private static List<String> errors(CompilationResult r) {
        return r.diagnostics().getDiagnostics().stream()
                .filter(diag -> "ERROR".equals(String.valueOf(diag.severity())))
                .map(diag -> String.valueOf(diag.message()))
                .toList();
    }

    @Test
    void aUserClassNamedLikeAMediaNamespaceCompilesInsideItsOwnBody() throws IOException {
        for (String name : List.of("Video", "Image", "Audio", "Mic")) {
            var d = new CompilerDriver();
            Path dir = Files.createTempDirectory("1198-" + name);
            CompilationResult r = compile(dir, "Main",
                    CLASS_BODY_TEMPLATE.formatted(name, name, name), d, Target.JVM);
            var errs = errors(r);
            assertTrue(errs.isEmpty(), "`" + name + ".LIMITE_BYTES` dentro do corpo de"
                    + " `class " + name + "` deve compilar — o SEM102 falso voltou: " + errs);
            assertFalse(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(diag -> "SEM102".equals(diag.code())),
                    "a classe do usuário tem precedência sobre o namespace de mídia homônimo");
        }
    }

    @Test
    void theUserClassFieldReadsTheValueFromInsideAndOutside() throws IOException, InterruptedException {
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1198-run");
        String src = """
                class Video {
                    static Int LIMITE_BYTES = 25000000
                    static Int limite() { return Video.LIMITE_BYTES }
                }
                main() {
                    println(Video.LIMITE_BYTES)
                    println(Video.limite())
                }
                """;
        CompilationResult r = compile(dir, "Main", src, d, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Path out = dir.resolve("out-Main");
        var p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String text = new String(p.getInputStream().readAllBytes()).strip();
        assertEquals(0, p.waitFor(), "run: " + text);
        assertEquals("25000000\n25000000", text,
                "o campo estático da classe do usuário lê o valor nos dois pontos");
    }

    @Test
    void anUnknownFieldOnTheUserClassIsASem025NotTheNamespaceGuard() throws IOException {
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1198-sem025");
        String src = """
                class Video {
                    static Int A = 1
                    static Int f() { return Video.NAO_EXISTE }
                }
                main() { println(Video.A) }
                """;
        CompilationResult r = compile(dir, "Main", src, d, Target.JVM);
        assertFalse(r.success(), "campo inexistente tem de falhar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(diag -> "SEM025".equals(diag.code())),
                "o diagnóstico real é SEM025 (campo inexistente), não SEM102: "
                        + errors(r));
    }

    @Test
    void theLegitimateMediaNamespaceDiagnosticSurvivesWithoutAUserClass() throws IOException {
        var d = new CompilerDriver();
        Path dir = Files.createTempDirectory("1198-pin");
        String src = """
                main() {
                    println(Image.bloquear(1))
                }
                """;
        CompilationResult r = compile(dir, "Main", src, d, Target.JVM);
        assertFalse(r.success(), "`Image.bloquear` sem classe do usuário tem de falhar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(diag -> diag.message() != null
                                && diag.message().contains("Image")),
                "o namespace de mídia real segue diagnosticado: " + errors(r));
    }
}

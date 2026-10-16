package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #849 — the grammar (`statements.md` §9) takes only a string LITERAL as
 * the `assert` message. A non-literal message used to compile with no
 * diagnostic and the message was dropped silently (`no-silent-fallback`); it
 * is now a parse error pointing at the offending expression.
 */
class AssertMessageLiteralTest {

    private static CompilationResult compile(String src) throws Exception {
        Path root = Files.createTempDirectory("assert-msg-");
        Path f = root.resolve("Main.kf");
        Files.writeString(f, src);
        return new CompilerDriver().compileSources(List.of(f),
                Files.createTempDirectory("assert-msg-out-"), Target.JVM, root);
    }

    private static Diagnostic parse087(CompilationResult r) {
        return r.diagnostics().getDiagnostics().stream()
                .filter(d -> "PARSE087".equals(d.code())).findFirst().orElse(null);
    }

    @Test
    void concatenatedMessageIsRejectedAtTheExpression() throws Exception {
        CompilationResult r = compile("""
                main() {
                    val n = 3
                    assert(n == 4, "valor " + n)
                }
                """);
        assertFalse(r.success(), "a non-literal assert message must not compile");
        Diagnostic d = parse087(r);
        assertTrue(d != null, "expected PARSE087, got " + r.diagnostics().getDiagnostics());
        assertEquals(3, d.line());
        assertEquals(20, d.column(), "points at the message expression");
    }

    @Test
    void variableMessageIsRejected() throws Exception {
        CompilationResult r = compile("""
                main() {
                    val m = "boom"
                    assert(false, m)
                }
                """);
        assertFalse(r.success());
        assertTrue(parse087(r) != null, "got " + r.diagnostics().getDiagnostics());
    }

    @Test
    void literalMessageStillCompiles() throws Exception {
        CompilationResult r = compile("""
                main() {
                    assert(1 + 1 == 2, "soma certa")
                }
                """);
        assertTrue(r.success(), "got " + r.diagnostics().getDiagnostics());
        assertTrue(parse087(r) == null);
    }
}

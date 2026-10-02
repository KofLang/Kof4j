package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SYN001 — switch como expressão. Pattern matching via expressão
 * ({@code case X -> body}), usável como valor. Forma aditiva: o switch
 * statement ({@code case X:}) continua válido (KofPatternMatchingTest é o
 * gate de retrocompatibilidade).
 */
class KofSwitchExprE2ETest extends KofSwitchExprSupport {

    // ── valor: Int ─────────────────────────────────────────────────

    @Test
    void intJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_INT_JVM, "dois");
    }

    @Test
    void intNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_INT_NATIVE, "dois");
    }

    @Test
    void intJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_INT_JS, "dois");
    }

    @Test
    void intDefaultJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_INT_DEFAULT_JVM, "outro");
    }

    // ── valor: String (igualdade por conteúdo — bug 4) ──────────────

    @Test
    void stringJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_STRING_JVM, "buscar");
    }

    @Test
    void stringNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_STRING_NATIVE, "buscar");
    }

    @Test
    void stringJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_STRING_JS, "buscar");
    }

    // ── pattern: case String s -> ───────────────────────────────────

    @Test
    void patternSimpleJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_PATTERN_SIMPLE_JVM, "str:hello");
    }

    @Test
    void patternSimpleNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_PATTERN_SIMPLE_NATIVE, "str:hello");
    }

    @Test
    void patternSimpleJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_PATTERN_SIMPLE_JS, "str:hello");
    }

    // ── pattern: destructuring case Point(var x, var y) -> ─────────

    @Test
    void destructureJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_DESTRUCTURE_JVM, "pt:3,4");
    }

    @Test
    void destructureNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_DESTRUCTURE_NATIVE, "pt:3,4");
    }

    @Test
    void destructureJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_DESTRUCTURE_JS, "pt:3,4");
    }

    // ── como return + aninhado ─────────────────────────────────────

    @Test
    void asReturnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_AS_RETURN_JVM, "um\nmuitos");
    }

    @Test
    void nestedJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_NESTED_JVM, "a1b2");
    }

    @Test
    void nestedJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_NESTED_JS, "a1b2");
    }

    // ── retrocompatibilidade: statement segue funcionando ───────────

    @Test
    void statementStillWorksJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_STATEMENT_STILL_WORKS_JVM, "buscar");
    }

    @Test
    void mixedStatementAndExprJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_MIXED_STATEMENT_AND_EXPR_JVM, "stmt-dois\ndois");
    }

    // ── sem default nem exaustão → erro SEM032 ─────────────────────

    @Test
    void missingDefaultFailsToCompile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC_MISSING_DEFAULT_FAILS_TO_COMPILE);
        Path outDir = tmp.resolve("out");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertFalse(result.success(), "deveria falhar sem default");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM032"),
                "deveria reportar SEM032: " + result.diagnostics().getDiagnostics());
        // D-DIAG-EN: a mensagem visivel e em ingles (tooling 100% EN).
        var msg = result.diagnostics().getDiagnostics().toString();
        assertTrue(msg.contains("switch expression requires 'default'"),
                "mensagem SEM032 deve ser EN, veio: " + msg);
        assertFalse(msg.matches("(?s).*[ãõáàâéêíóôúç].*"),
                "SEM032 ainda tem texto PT: " + msg);
    }

    // ── corpo de case em BLOCO → diagnóstico PARSE094 (R6) ──────────

    @Test
    void blockCaseBodyFailsWithDiagnostic(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC_BLOCK_CASE_BODY_FAILS_WITH_DIAGNOSTIC);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "case -> { } deve ser rejeitado (sem escopo de bloco)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("PARSE094"),
                "deveria reportar PARSE094: " + result.diagnostics().getDiagnostics());
    }

    // ── enum: exaustivo sem default (SEM031/SEM032) ─────────────────
    @Test
    void enumExhaustiveJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_JVM, "vermelho\nverde\nazul");
    }

    // bug 145: `Color.Red` como EXPRESSÃO (enum constante) tipava UNKNOWN → o
    // switch-expr exaustivo caía no SEM032 genérico quando o scrutinee vinha de
    // `var` ou era o literal direto. Com o tipo certo, a exaustividade volta a valer.

    @Test
    void enumExhaustiveVarSubjectJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_VAR_SUBJECT_JVM, "azul");
    }

    @Test
    void enumExhaustiveLiteralSubjectJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_LITERAL_SUBJECT_JVM, "verde");
    }

    @Test
    void enumExhaustiveVarSubjectNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_ENUM_EXHAUSTIVE_VAR_SUBJECT_NATIVE, "vermelho");
    }

    // §149: switch-expr EXAUSTIVO sobre enum (sem default) com corpo PRIMITIVO.
    // O fallback sintético usava o tipo do SUBJECT (enum = referência) como tipo
    // do resultado, então `branchTypesDiffer` boxeava os braços primitivos →
    // VerifyError no JVM (Integer vs int) e crash COMP002 em Double/Long. O
    // fallback agora usa o tipo do RESULTADO (inferExprType do switch).

    @Test
    void enumExhaustiveIntBodyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_INT_BODY_JVM, "3");
    }

    @Test
    void enumExhaustiveDoubleBodyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_DOUBLE_BODY_JVM, "3.5");
    }

    @Test
    void enumExhaustiveLongBodyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_LONG_BODY_JVM, "3");
    }

    @Test
    void enumExhaustiveBoolBodyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SRC_ENUM_EXHAUSTIVE_BOOL_BODY_JVM, "false");
    }

    @Test
    void enumExhaustiveIntBodyNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_ENUM_EXHAUSTIVE_INT_BODY_NATIVE, "2");
    }

    @Test
    void enumExhaustiveNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SRC_ENUM_EXHAUSTIVE_NATIVE, "verde");
    }

    @Test
    void enumExhaustiveJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SRC_ENUM_EXHAUSTIVE_JS, "azul");
    }

    @Test
    void enumNonExhaustiveFailsToCompile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, SRC_ENUM_NON_EXHAUSTIVE_FAILS_TO_COMPILE);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "deveria falhar sem cobrir Green/Blue");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("SEM032"),
                "deveria reportar SEM032: " + result.diagnostics().getDiagnostics());
    }

    // ── harness ────────────────────────────────────────────────────
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — a validação SEMÂNTICA: tudo o que o parser
 * aceita de forma mas não descreve uma intenção válida é recusado com um código
 * que ensina a correção (§13 do plano; nunca erro em runtime, nunca silêncio).
 *
 * <p>O spike-0 não baixa IR — então ESTA é a fronteira real de recusa por
 * enquanto: o diagnóstico tem de sair no compile, com arquivo:linha.
 */
class ExprDeclarationSemanticsE2ETest {

    @TempDir
    Path tmp;

    private String compile(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out-" + tag), Target.JVM);
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void referenceToAnUnknownPatternIsRefused() throws Exception {
        String d = compile("unknown", """
            expr url { protocol address }
            """);
        assertTrue(d.contains("SEM108"), () -> "referência desconhecida: " + d);
    }

    @Test
    void aReferenceResolvesRegardlessOfDeclarationOrder() throws Exception {
        String d = compile("order", """
            expr ipv4 { octet "." octet }
            expr octet { between(1, 3) digit }
            """);
        assertFalse(d.contains("SEM108"), () -> "visibilidade não é ordem de arquivo: " + d);
    }

    @Test
    void recursivePatternIsRefused() throws Exception {
        String d = compile("cycle", """
            expr a { b }
            expr b { a }
            """);
        assertTrue(d.contains("SEM109"), () -> "ciclo infinito deve ser recusado no compile: " + d);
    }

    @Test
    void selfReferenceIsRefused() throws Exception {
        String d = compile("self", """
            expr list { item optional { list } }
            """);
        assertTrue(d.contains("SEM109"), () -> d);
    }

    @Test
    void duplicateCaptureNameIsRefused() throws Exception {
        String d = compile("dup", """
            expr range { capture("v") { digit } "-" capture("v") { digit } }
            """);
        assertTrue(d.contains("SEM110"), () -> d);
    }

    @Test
    void exactlyZeroIsRefusedWithTheOptionalTeaching() throws Exception {
        String d = compile("zero", """
            expr t { exactly(0) digit }
            """);
        assertTrue(d.contains("SEM111") && d.contains("optional"),
                () -> "o erro tem de ensinar 'optional': " + d);
    }

    @Test
    void invertedBetweenIsRefused() throws Exception {
        String d = compile("inverted", """
            expr t { between(5, 2) digit }
            """);
        assertTrue(d.contains("SEM112"), () -> d);
    }

    @Test
    void misplacesAnchorIsRefused() throws Exception {
        String d = compile("anchor", """
            expr t { digit end digit }
            """);
        assertTrue(d.contains("SEM113"), () -> "'end' no meio não descreve intenção: " + d);
    }

    @Test
    void patternNameCollidingWithATypeIsRefused() throws Exception {
        String d = compile("collide", """
            class Point { }
            expr Point { digit }
            """);
        assertTrue(d.contains("SEM114"), () -> d);
    }

    @Test
    void patternNameCollidingWithAClassAtomIsRefused() throws Exception {
        // Fatia §5 (D-KOF-EXPR): `word`/`letter`/`digit`/… são ÁTOMOS de
        // classe na gramática — um `expr` com esse nome é ambíguo (o parser
        // leria o bare `word` como a classe, nunca como a referência:
        // medido — kof_expr_is_word no lugar do corpo do usuário).
        String d = compile("clash", """
            expr word { one_or_more { letter } }
            main() { println(word.matches("abc")) }
            """);
        assertTrue(d.contains("SEM114") && d.contains("character class"), () -> d);
    }
}

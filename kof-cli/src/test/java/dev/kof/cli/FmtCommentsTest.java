package dev.kof.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §509 / issue #625 — `kof fmt` deve preservar comentários SEMPRE, e o
 * resultado não pode depender da proporção de comentários no fonte (o mesmo
 * código com 1 comentário perdia-o; com 3 preservava — heurística de 50%
 * do KofFormatter decidindo por acidente qual caminho roda).
 *
 * Paths covered here (Fmt.format = the AST formatter; a source that does not
 * parse has no formatted text, §818): short line comment, block comment,
 * "//" inside a string and determinism regardless of the comment ratio.
 */
class FmtCommentsTest {

    private static final String REPRO = """
            main() {
                val a = 1
                val b = 2
                // soma os valores
                val c = a + b
                println(c)
            }
            """;

    @Test
    void fmtPreservesShortLineComment() {
        String out = Fmt.format(REPRO);
        assertTrue(out.contains("// soma os valores"),
                "comentario apagado em silencio:\n" + out);
    }

    @Test
    void fmtIdempotentWithComments() {
        // formatar 2x nao pode mudar nada (Q3 idempotencia) — o caminho dos
        // comentarios e o token-based; estabilidade e obrigatoria
        String once = Fmt.format("main(){\nval a=1\n// soma os valores\nprintln( a )\n}\n");
        String twice = Fmt.format(once);
        assertEquals(once, twice, "fmt nao-idempotente com comentarios");
    }

    @Test
    void fmtPreservesBlockCommentVerbatim() {
        String src = "main() {\n    /* nota */\n    println(1)\n}\n";
        String out = Fmt.format(src);
        assertTrue(out.contains("/* nota */"),
                "comentario de bloco destruido (tokenizado como operadores):\n" + out);
    }

    @Test
    void unparseableSourceHasNoFormattedText() {
        // §818: the token-based fallback is gone; a source that does not parse
        // has no canonical text, and the caller leaves the file untouched
        assertNull(Fmt.format("main( {\n    /* nota */\n    println(1)\n}\n"));
    }

    @Test
    void fmtPreservesTrailingCommentAfterCode() {
        String src = "main() {\n    val a = 1 // um\n    println(a)\n}\n";
        String out = Fmt.format(src);
        assertTrue(out.contains("// um"), "comentario no fim da linha perdido:\n" + out);
    }

    @Test
    void fmtDeterministicRegardlessOfCommentRatio() {
        // o caso do issue: mesmo programa, proporcões diferentes — ou todos
        // os comentarios sobrevivem, ou nenhum caminho e aceito
        String dense = "main() {\n    val a = 1\n    val b = 2\n"
                + "    // soma os valores das duas variaveis de entrada do calculo\n"
                + "    // este bloco inteiro faz a soma e imprime o resultado\n"
                + "    // mantenha a ordem: a antes de b\n"
                + "    val c = a + b\n    println(c)\n}\n";
        assertTrue(Fmt.format(REPRO).contains("// soma os valores"));
        assertTrue(Fmt.format(dense).contains("// soma os valores das duas variaveis"),
                "dense comments lost");
    }

    @Test
    void doubleSlashInsideStringStillTakesAstPath() {
        String src = "main() {\n    println(\"http://x\")\n}\n";
        String out = Fmt.format(src);
        assertTrue(out.contains("\"http://x\""), "string destruída pelo fallback: " + out);
        // AST preserva a chamada como expression canonica (sem espaços extras)
        assertTrue(out.contains("println(\"http://x\")"), "AST path esperado: " + out);
    }
}

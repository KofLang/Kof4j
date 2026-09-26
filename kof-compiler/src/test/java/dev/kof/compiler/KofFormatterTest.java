package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #52 — `kof fmt` deve PRESERVAR precedência e associatividade. O formatter
 * antigo reconstruía BinaryExpr/UnaryExpr sem re-inserir parênteses, mudando o
 * resultado do programa em silêncio (ex.: `(1+2)*3` virava `1+2*3`, 9→7).
 *
 * A prova é por STRING, não por execução: comparamos a saída formatada com o
 * agrupamento canônico esperado. Executar antes/depois é o E2E; aqui travamos
 * a regra de impressão (espelha ExpressionParser.precedence + left-assoc).
 */
class KofFormatterTest {

    private static String fmt(String body) {
        String src = "main() {\n    " + body + "\n}\n";
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out, "format() não deve cair no fallback p/ código válido:\n" + src);
        return out;
    }

    private static String inner(String body) {
        return fmt("println(" + body + ")").lines()
                .filter(l -> l.contains("println"))
                .findFirst().orElseThrow().trim();
    }

    @Test
    void preservesParenthesizedGroupingThatChangesValue() {
        // os três casos do issue: parênteses NECESSÁRIOS sobrevivem
        assertEquals("println((1 + 2) * 3)", inner("(1 + 2) * 3"));
        assertEquals("println(10 - (3 - 1))", inner("10 - (3 - 1)"));
        assertEquals("println(-(1 + 2))", inner("-(1 + 2)"));
    }

    @Test
    void removesRedundantParensKeepingAssociativity() {
        // agrupamento redundante some, mas o valor não muda (left-assoc):
        // 10 - 3 - 1 = (10-3)-1, e 8/4/2 = (8/4)/2 → sem parênteses
        assertEquals("println(10 - 3 - 1)", inner("(10 - 3) - 1"));
        assertEquals("println(8 / 4 / 2)", inner("(8 / 4) / 2"));
        // right-assoc exigiria parênteses: 10 - (3 - 1) PRESERVA
        assertEquals("println(10 - (3 - 1))", inner("10 - (3 - 1)"));
    }

    @Test
    void higherPrecedenceNeedsNoParens() {
        // filho com precedência maior que o pai nunca leva parênteses
        assertEquals("println(2 * 3 % 4)", inner("2 * 3 % 4"));
        assertEquals("println(2 + 3 * 4 - 1)", inner("2 + 3 * 4 - 1"));
        assertEquals("println(1 + 2 * 3)", inner("1 + 2 * 3"));
    }

    @Test
    void lowerPrecedenceChildIsWrapped() {
        // filho binário com precedência MENOR que o pai → parênteses
        assertEquals("println((2 + 3) * 4)", inner("(2 + 3) * 4"));
        assertEquals("println(1 < 2 && 3 > 2)", inner("1 < 2 && 3 > 2"));
        assertEquals("println((1 + 2) * (3 + 4))", inner("(1 + 2) * (3 + 4)"));
    }

    @Test
    void prefixUnaryOperandWrapsBinary() {
        // parser: operand do unary é parseUnary → binário DENTRO do unary
        // só existe via parênteses; a impressão os re-insere (sem () seria
        // (!a || b) ≠ !(a || b))
        assertEquals("println(!(a || b))", inner("!(a || b)"));
        // unary atômico seguido de binário: parse é (!a) || b — imprime igual
        assertEquals("println(!a || b)", inner("!a || b"));
        assertEquals("println(-a * b)", inner("-a * b"));
    }

    @Test
    void idempotentOnAlreadyFormatted() {
        String once = fmt("println((1 + 2) * 3)\n    println(10 - (3 - 1))");
        String twice = KofFormatter.format(once, "Main.kf");
        assertNotNull(twice);
        assertEquals(once, twice, "fmt deve ser idempotente (2ª passada não muda)");
    }

// ===== #447 — round-trip de literais com escape (CHAR e STRING) =====

    @Test
    void charEscapesSurviveRoundTrip() {
        // regressão #447: '\\' virava '\' (unterminated) e '\t' virava TAB cru
        for (String lit : new String[]{"'\\\\'", "'\\t'", "'\\n'", "'\\r'", "'\\''", "'\\0'"}) {
            String out = fmt("var c = " + lit + "\n    println(c)");
            assertTrue(out.contains(lit), lit + " deve sobreviver ao fmt, saída:\n" + out);
        }
    }

    @Test
    void stringEscapesSurviveRoundTrip() {
        String out = fmt("var s = \"a\\tb\"\n    println(s)");
        assertTrue(out.contains("\"a\\tb\""), "\"a\\tb\" nao deve virar TAB cru:\n" + out);
        out = fmt("var s = \"c\\\"d\"\n    println(s)");
        assertTrue(out.contains("\"c\\\"d\""), "\"c\\\"d\" nao deve quebrar a string:\n" + out);
        out = fmt("var s = \"p\\\\q\"\n    println(s)");
        assertTrue(out.contains("\"p\\\\q\""), "backslash literal nao deve sumir:\n" + out);
    }

    @Test
    void escapedLiteralsAreIdempotentAndReparseable() {
        String once = fmt("var c = '\\\\'\n    var s = \"a\\tb\"\n    println(c + s)");
        String twice = KofFormatter.format(once, "Main.kf");
        assertNotNull(twice, "saida do 1o fmt deve reparsar (nao pode ser codigo invalido):\n" + once);
        assertEquals(once, twice, "2a passada nao deve mudar nada");
        // saida compila de verdade (check passa onde antes dava LEX004):
        assertNotNull(KofFormatter.format(once, "Main.kf"));
    }

    @Test
    void plainLiteralsUnchangedByEscaper() {
        // nao-ASCII cru e texto comum NAO sao convertidos (o lexer aceita cru)
        String out = fmt("var s = \"café olá\"\n    println(s)");
        assertTrue(out.contains("\"café olá\""), "acentos devem permanecer crus:\n" + out);
        out = fmt("var c = 'ç'\n    println(c)");
        assertTrue(out.contains("'ç'"), "char nao-ASCII cru deve permanecer:\n" + out);
    }

    @Test
    void formatReturnsNullNotCorruptOutput() {
        // código inválido → null (fallback token-based no CLI), nunca saída errada
        assertNull(KofFormatter.format("main() { ((( ", "Bad.kf"));
    }

    @Test
    void doWhileUsesBlockBodyBranch() {
        // CodeQL contradictory-type-checks: havia DOIS `instanceof DoWhileStmt`
        // no chain do formatStmt (o 2º, com formatStmt no corpo, inalcançável
        // porque o 1º sempre casa antes). Removido o morto; resta o 1º, que
        // formata o corpo via formatBody (bloco com chaves). Trava a saída.
        String out = fmt("do { println(i) } while (i < 3)");
        String expected = "main() {\n    do {\n        println(i)\n    }\n    while (i < 3)\n}\n";
        assertEquals(expected, out, "do-while sai pelo ramo formatBody:\n" + out);
    }

    // §625 — `kof fmt` deletava comentarios em silencio: o caminho AST
    // reimprime sem comentarios e a heuristica de 50% decidia POR ACIDENTE
    // qual formatter rodava (pouco comentario = perda; muito = fallback
    // preserva). Aceitacao do bug: comentarios preservados SEMPRE e o mesmo
    // programa produce o mesmo resultado independentemente de quantos
    // comentarios tem.
    private static final String BUG_625_SRC = """
            main() {
                val a = 1
                val b = 2
                // soma os valores
                val c = a + b
                println(c)
            }
            """;

    @Test
    void bug625LineCommentSurvivesAstFormat() {
        String out = KofFormatter.format(BUG_625_SRC, "Main.kf");
        assertNotNull(out, "format() deve fechar o parse do reproducer da issue");
        assertTrue(out.contains("// soma os valores"),
                "§625: comentario de linha nao pode ser deletado — saida foi:\n" + out);
    }

    @Test
    void bug625BlockCommentSurvivesAstFormat() {
        String src = "/* cabecalho do modulo */\nmain() {\n    println(1)\n}\n";
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("cabecalho do modulo"),
                "§625: comentario de bloco nao pode ser deletado — saida foi:\n" + out);
    }

    @Test
    void bug625FewVsManyCommentsSameCodeOut() {
        String many = """
                main() {
                    val a = 1
                    // comentario um, este aqui e bem maior do que o anterior de proposito
                    // comentario dois, tambem longo, para empurrar o ratio de tamanho
                    // comentario tres, mais um bloco de texto pra passar de qualquer metade
                    val b = 2
                    val c = a + b
                    println(c)
                }
                """;
        String outFew = KofFormatter.format(BUG_625_SRC, "Main.kf");
        String outMany = KofFormatter.format(many, "Main.kf");
        assertNotNull(outFew, "pouco comentario: sem null (heuristica 50% extinta)");
        assertNotNull(outMany);
        String codeFew = outFew.replaceAll("//[^\\n]*", "").replaceAll("\\s+", " ").trim();
        String codeMany = outMany.replaceAll("//[^\\n]*", "").replaceAll("\\s+", " ").trim();
        assertEquals(codeFew, codeMany,
                "§625: o MESMO codigo deve sair igual com 1 ou 4 comentarios (determinismo)");
        assertTrue(outFew.contains("// soma os valores"));
        assertTrue(outMany.contains("// comentario tres, mais um bloco de texto pra passar de qualquer metade"));
    }

    @Test
    void bug625TrailingCommentAtFileEndKept() {
        String src = "main() {\n    println(1)\n}\n// fim do arquivo\n";
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("// fim do arquivo"),
                "§625: comentario final nao pode ser deletado — saida foi:\n" + out);
    }

    @Test
    void bug625CommentInsideStringIsNotTreatedAsComment() {
        String src = "main() {\n    println(\"// nao sou comentario\")\n    // sou comentario\n}\n";
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("\"// nao sou comentario\""),
                "§625: o conteudo da string deve sobreviver intacto — saida foi:\n" + out);
        assertTrue(out.contains("// sou comentario"),
                "§625: comentario real deve sobreviver — saida foi:\n" + out);
    }
}

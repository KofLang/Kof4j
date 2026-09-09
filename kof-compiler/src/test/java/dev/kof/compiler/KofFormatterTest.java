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

    @Test
    void formatReturnsNullNotCorruptOutput() {
        // código inválido → null (fallback token-based no CLI), nunca saída errada
        assertNull(KofFormatter.format("main() { ((( ", "Bad.kf"));
    }
}

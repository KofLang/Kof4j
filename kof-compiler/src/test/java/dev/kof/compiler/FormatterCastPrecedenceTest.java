package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * §815 — the formatter kept its own copy of the precedence table with
 * {@code as} at 5, so after {@code as} moved above arithmetic it would print
 * {@code (a - b) as Long} as {@code a - b as Long}, which now means
 * {@code a - (b as Long)}. The formatter reads the parser's table, keeps the
 * parentheses of a cast that is the operand of an arithmetic, shift or
 * comparison operator (never a PARSE100 shape), and treats a unary operand as
 * tighter than every binary operator.
 */
class FormatterCastPrecedenceTest {

    private static String fmt(String body) {
        String out = KofFormatter.format("main() {\n" + body + "\n}\n", "Main.kf");
        assertNotNull(out, "must parse: " + body);
        return out.lines().filter(l -> l.startsWith("    ")).map(String::strip).findFirst().orElseThrow();
    }

    @Test
    void parenthesesThatChangeTheMeaningOfACastAreKept() {
        assertEquals("println((now - h) as Long * 3600000L)", fmt("println((now - h) as Long * 3600000L)"));
        assertEquals("println(now - (h as Long) * 3600000L)", fmt("println(now - (h as Long) * 3600000L)"));
        assertEquals("println(-(x as Long))", fmt("println(-(x as Long))"));
        assertEquals("println(x as Long * 2L)", fmt("println(x as Long * 2L)"));
        assertEquals("println(a as Long as Object)", fmt("println(a as Long as Object)"));
    }

    @Test
    void formattedOutputIsStableAndHasNoMigrationWarning() {
        String src = "main() {\n    val now = 10L\n    val h = 2\n    println((now - h) as Long * 3L + now - (h as Long))\n}\n";
        String once = KofFormatter.format(src, "Main.kf");
        assertEquals(once, KofFormatter.format(once, "Main.kf"), "idempotent");
        DiagnosticCollector d = new DiagnosticCollector();
        new dev.kof.compiler.parser.Parser(new dev.kof.compiler.parser.Lexer(once, "Main.kf", d).tokenize(),
                d, "Main.kf").parse();
        assertFalse(d.getDiagnostics().stream().anyMatch(x -> "PARSE100".equals(x.code())),
                "fmt output must not warn: " + d.getDiagnostics());
    }
}

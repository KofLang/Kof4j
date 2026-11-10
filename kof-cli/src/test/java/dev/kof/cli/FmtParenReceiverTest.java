package dev.kof.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1013 — `kof fmt` must not drop the parentheses of a call/field/index
 * receiver: `("" + city).trim()` became `"" + city.trim()` — a DIFFERENT
 * program (the trim stopped applying to the concatenation). The formatter's
 * AST printer now prints member/index receivers at POSTFIX precedence (9):
 * a binary/`as` receiver gets its parens back.
 */
class FmtParenReceiverTest {

    @Test
    void parenthesizedCallReceiverKeepsItsParens() {
        String out = Fmt.format("main() {\n    var t = (\"\" + city).trim()\n}\n");
        assertTrue(out.contains("(\"\" + city).trim()"),
                "the parenthesized receiver survives: " + out);
        assertFalse(out.contains("+ city.trim()"), out);
    }

    @Test
    void parenthesizedReceiverInsideForInKeepsItsParens() {
        String out = Fmt.format(
                "main() {\n    for (var x in (a + \",\" + b).split(\",\")) { println(x) }\n}\n");
        assertTrue(out.contains("(a + \",\" + b).split(\",\")"), out);
    }

    @Test
    void parenthesizedAsCastReceiverKeepsItsParens() {
        String out = Fmt.format("main() {\n    println((o as Box).v)\n}\n");
        assertTrue(out.contains("(o as Box).v"), out);
        assertFalse(out.contains("o as Box.v"), out);
    }

    @Test
    void formattingIsIdempotentOnTheFixedFaces() {
        // fmt(fmt(x)) == fmt(x) — the reprinted parens must not grow on re-run.
        String once = Fmt.format("main() {\n    var t = (\"\" + city).trim()\n    println((o as Box).v)\n}\n");
        String twice = Fmt.format(once);
        assertEquals(once, twice, "idempotent");
    }

    @Test
    void plainChainsStayParenFree() {
        // The parity guard: simple receivers never gain parens.
        String out = Fmt.format("main() {\n    println(a.b.c)\n    println(list.get(0))\n}\n");
        assertEquals("main() {\n    println(a.b.c)\n    println(list.get(0))\n}", out.strip(),
                "plain chains unchanged: " + out);
    }
}

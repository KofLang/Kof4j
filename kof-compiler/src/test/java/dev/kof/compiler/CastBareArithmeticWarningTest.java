package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §815 — {@code as} now binds tighter than arithmetic, shifts and comparisons
 * (grammar §5.1), so {@code now - h as Long * 3600000L} is
 * {@code now - ((h as Long) * 3600000L)}. PARSE100 is the migration warning:
 * it fires where the old table (as = 5) parsed the same text differently,
 * i.e. on the unparenthesized operand of an arithmetic, shift or comparison
 * operator, and stays silent on parenthesized casts and on shapes whose parse
 * did not change.
 */
class CastBareArithmeticWarningTest {

    @TempDir Path tmp;

    private List<Diagnostic> parse100(String body) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, "main() {\n" + body + "\n}\n");
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "a warning must not fail the build: " + r.diagnostics().getDiagnostics());
        return r.diagnostics().getDiagnostics().stream().filter(d -> "PARSE100".equals(d.code())).toList();
    }

    @Test
    void aCastWhoseParseChangedWarns() throws Exception {
        List<Diagnostic> w = parse100("""
            val now = 10000000L
            val h = 2
            println(now - h as Long * 3600000L)
            """);
        assertEquals(1, w.size(), "the verbatim gap shape: " + w);
        assertEquals(4, w.get(0).line(), "the line of the cast");
        for (String op : List.of("+", "-", "*", "/", "%", "<<", ">>", ">>>", "==", "<")) {
            assertEquals(1, parse100("val a = 8\nval b = 2\nprintln(a " + op + " b as Long)").size(), op);
        }
    }

    @Test
    void parenthesizedOrUnchangedCastsStaySilent() throws Exception {
        assertEquals(List.of(), parse100("""
            val now = 10000000L
            val h = 2
            println((now - h) as Long * 3600000L)
            println(now - (h as Long) * 3600000L)
            println(h as Long * 3600000L)
            println(now - h)
            println(h as Long == 2L)
            println(9 as Long as Object)
            println(h > 1 && h as Long > 1L)
            """));
    }
}

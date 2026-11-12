package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kof.expr slice 2 (`D-KOF-EXPR`) — the quantifiers: `optional`, `zero_or_more`,
 * `one_or_more`, `exactly(n)`, `between(min, max)`. The matcher is greedy with
 * backtracking (regex-greedy: take the most, unwind when the tail refuses) and
 * TERMINATION is by construction: an unbounded iteration only takes another
 * instance when the body advanced the position (an empty-matching body cannot
 * loop). The plan §12 ReDoS design-out: an unbounded quantifier inside another
 * unbounded quantifier refuses at compile with EXPR002 (the plan's own
 * compile-time-rejection option; the documented step budget stays slice 8).
 * Bounded quantifiers compose freely (optional/exactly/between inside loops).
 */
class KofExprQuantifierE2ETest {

    @TempDir
    Path tmp;

    @Test
    void optionalIsGreedyWithAFallback() throws Exception {
        String src = """
                expr opt1 {
                    optional("+")
                    "55"
                }

                main() {
                    println(opt1.matches("+55"))
                    println(opt1.matches("55"))
                    println(opt1.matches("++55"))
                    println(opt1.matches(""))
                }
                """;
        assertEquals("true\ntrue\nfalse\nfalse\n", runJvm("opt", src));
    }

    @Test
    void zeroOrMoreBacktracksIntoTheTail() throws Exception {
        // greedy: a* takes every "a", then unwinds until "aa" fits
        String src = """
                expr many {
                    zero_or_more { "a" }
                    "aa"
                }

                main() {
                    println(many.matches("aaa"))
                    println(many.matches("aa"))
                    println(many.matches("aaaa"))
                    println(many.matches(""))
                }
                """;
        // "aaaa" matches: a* yields two a's so "aa" fits (the greedy unwind).
        assertEquals("true\ntrue\ntrue\nfalse\n", runJvm("many", src));
    }

    @Test
    void oneOrMoreRequiresOneInstance() throws Exception {
        String src = """
                expr ab {
                    one_or_more { "ab" }
                }

                main() {
                    println(ab.matches("abab"))
                    println(ab.matches("ab"))
                    println(ab.matches("aba"))
                    println(ab.matches(""))
                }
                """;
        assertEquals("true\ntrue\nfalse\nfalse\n", runJvm("one", src));
    }

    @Test
    void exactlyIsAllOrNothing() throws Exception {
        String src = """
                expr xx {
                    exactly(2) { "x" }
                    "y"
                }

                main() {
                    println(xx.matches("xxy"))
                    println(xx.matches("xxxy"))
                    println(xx.matches("xy"))
                }
                """;
        assertEquals("true\nfalse\nfalse\n", runJvm("exact", src));
    }

    @Test
    void betweenTakesMinToMaxGreedily() throws Exception {
        String src = """
                expr zz {
                    between(1, 3) { "z" }
                    "!"
                }

                main() {
                    println(zz.matches("zzz!"))
                    println(zz.matches("zz!"))
                    println(zz.matches("z!"))
                    println(zz.matches("!"))
                    println(zz.matches("zzzz!"))
                }
                """;
        assertEquals("true\ntrue\ntrue\nfalse\nfalse\n", runJvm("between", src));
    }

    @Test
    void anUnboundedLoopOverANullableBodyRefusesAtCompile() throws Exception {
        // `(a?)*` is the classic infinite-loop shape: the iteration can match
        // empty text, so it never advances. Termination is guaranteed by
        // CONSTRUCTION — the compiler refuses the shape and teaches the fix.
        String d = diagnostics("""
                expr maybe { zero_or_more { optional { "a" } } "b" }
                main() { println(maybe.matches("b")) }
                """);
        assertTrue(d.contains("EXPR003") && d.contains("empty"), () -> d);
        assertTrue(d.contains("at least one character"), () -> d);
        String seq = diagnostics("""
                expr maybe2 { one_or_more { optional { "a" } optional { "b" } } }
                main() { println(maybe2.matches("a")) }
                """);
        assertTrue(seq.contains("EXPR003"), () -> seq);
        // a non-nullable body in the same shape is accepted and matches
        String ok = """
                expr fine { zero_or_more { "a" optional { "b" } } "!" }

                main() {
                    println(fine.matches("ab!"))
                    println(fine.matches("!"))
                }
                """;
        assertEquals("true\ntrue\n", runJvm("nullableOk", ok));
    }

    @Test
    void thePhoneShapeOfThePlanOverLiterals() throws Exception {
        // §3.4 of the plan, restricted to slice-2 atoms (literals only)
        String src = """
                expr ph {
                    optional("+")
                    "55"
                    optional(" ")
                    "11"
                    optional(" ")
                    "9"
                }

                main() {
                    println(ph.matches("+55 11 9"))
                    println(ph.matches("55 11 9"))
                    println(ph.matches("55119"))
                    println(ph.matches("5511 x"))
                }
                """;
        assertEquals("true\ntrue\ntrue\nfalse\n", runJvm("phone", src));
    }

    @Test
    void unboundedInsideBoundedComposes() throws Exception {
        // exactly(2) bounds the count: two a's per instance, 1..3 instances.
        String src = """
                expr cap {
                    between(1, 3) { exactly(2) { "a" } }
                }

                main() {
                    println(cap.matches("aa"))
                    println(cap.matches("aaaa"))
                    println(cap.matches("aaaaaa"))
                    println(cap.matches("a"))
                    println(cap.matches("aaaaa"))
                    println(cap.matches("aaaaaaaa"))
                }
                """;
        assertEquals("true\ntrue\ntrue\nfalse\nfalse\nfalse\n", runJvm("cap", src));
    }

    @Test
    void quantifierScriptParityIsByteIdentical() throws Exception {
        String src = """
                expr opt1 { optional("+") "55" }
                expr many { zero_or_more { "a" } "aa" }
                expr ab { one_or_more { "ab" } }
                expr zz { between(1, 3) { "z" } "!" }

                main() {
                    println(opt1.matches("+55"))
                    println(opt1.matches("55"))
                    println(many.matches("aaa"))
                    println(many.matches("aa"))
                    println(ab.matches("abab"))
                    println(ab.matches("aba"))
                    println(zz.matches("zzz!"))
                    println(zz.matches("z!"))
                }
                """;
        String expected = "true\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue\ntrue\n";
        assertEquals(expected, runJvm("parity", src));
        assertEquals(expected, runScript(src),
                "ONE compiled form: the interpreter and the JVM agree byte for byte");
    }

    @Test
    void nestedUnboundedRefusesWithExpr002() throws Exception {
        String direct = diagnostics("""
                expr bad { zero_or_more { zero_or_more { "a" } } }
                main() { println(bad.matches("a")) }
                """);
        assertTrue(direct.contains("EXPR002") && direct.contains("ReDoS"), () -> direct);
        String viaSequence = diagnostics("""
                expr bad2 { one_or_more { "x" zero_or_more { "y" } } }
                main() { println(bad2.matches("xy")) }
                """);
        assertTrue(viaSequence.contains("EXPR002"), () -> viaSequence);
        String viaBounded = diagnostics("""
                expr bad3 { zero_or_more { exactly(2) { zero_or_more { "a" } } } }
                main() { println(bad3.matches("aa")) }
                """);
        assertTrue(viaBounded.contains("EXPR002"), () -> viaBounded);
    }

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), tag + ": " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runScript(String src) throws Exception {
        Path file = tmp.resolve("script").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout());
        return r.stdout();
    }

    private String diagnostics(String src) throws Exception {
        Path file = tmp.resolve("diag").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("diag-out"), Target.JVM);
        assertFalse(r.success(), "the pattern must refuse: " + src);
        return r.diagnostics().getDiagnostics().toString();
    }
}

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
 * Kof.expr slice 4 (`D-KOF-EXPR`) — groups + `either` (alternation), plan §3.3.
 *
 * <p>`either { a or b }` is an ORDERED choice: a branch is tried, and a branch
 * that matches but whose TAIL refuses is undone and the next branch tried —
 * the same backtracking a greedy alternation means. A bare `{ … }` group is a
 * unit, so a quantifier applies to the WHOLE group.
 *
 * <p>The §12 refusal analysis descends into alternation: a nullable branch
 * makes the alternation nullable (EXPR003 under an unbounded loop) and an
 * unbounded quantifier inside any branch is still EXPR002.
 */
class KofExprEitherE2ETest {

    @TempDir
    Path tmp;

    @Test
    void orderedChoicePicksTheFirstBranchWhoseTailFits() throws Exception {
        String src = """
                expr pick {
                    either { "cat" or "catalog" }
                }

                main() {
                    println(pick.matches("cat"))
                    println(pick.matches("catalog"))
                    println(pick.matches("ca"))
                }
                """;
        assertEquals("true\ntrue\nfalse\n", runJvm("pick", src));
    }

    @Test
    void aBranchThatMatchesButBricksTheTailFallsBack() throws Exception {
        // "ab" matches the first branch, but the tail "!" then refuses "abc!"?
        // no — the FIRST branch is undone and "a" leaves room for "bc".
        String src = """
                expr alt {
                    either { "ab" or "a" }
                    "bc"
                }

                main() {
                    println(alt.matches("abc"))
                    println(alt.matches("abcbc"))
                    println(alt.matches("abbc"))
                }
                """;
        assertEquals("true\nfalse\ntrue\n", runJvm("fallback", src));
    }

    @Test
    void eitherOverSequencesAndClasses() throws Exception {
        String src = """
                expr kind {
                    either {
                        "true"
                        or "false"
                        or "null"
                    }
                }

                expr wordish {
                    one_or_more { either { letter or digit } }
                }

                main() {
                    println(kind.matches("true"))
                    println(kind.matches("false"))
                    println(kind.matches("nul"))
                    println(wordish.matches("a1b2"))
                    println(wordish.matches("a-1"))
                }
                """;
        assertEquals("true\ntrue\nfalse\ntrue\nfalse\n", runJvm("seq", src));
    }

    @Test
    void aBonusBranchMatchesEmpty() throws Exception {
        String src = """
                expr maybe {
                    either { "x" or optional { "y" } }
                    "z"
                }

                main() {
                    println(maybe.matches("z"))
                    println(maybe.matches("yz"))
                    println(maybe.matches("xz"))
                }
                """;
        assertEquals("true\ntrue\ntrue\n", runJvm("empty", src));
    }

    @Test
    void eitherInsideAQuantifierBacktracks() throws Exception {
        String src = """
                expr bits {
                    zero_or_more { either { "a" or "b" } }
                    "!"
                }

                main() {
                    println(bits.matches("abab!"))
                    println(bits.matches("!"))
                    println(bits.matches("abx!"))
                }
                """;
        assertEquals("true\ntrue\nfalse\n", runJvm("quant", src));
    }

    @Test
    void aBareGroupIsQuantifiedAsAWhole() throws Exception {
        // Exactly(2) over the GROUP "ab": "abab" matches, "aba" does not —
        // the quantifier attaches to the sequence, not to "b" alone.
        String src = """
                expr pairs {
                    exactly(2) { "a" "b" }
                }

                expr twoAny {
                    exactly(2) { any }
                }

                main() {
                    println(pairs.matches("abab"))
                    println(pairs.matches("aba"))
                    println(twoAny.matches("\\uD83D\\uDE00k"))
                    println(twoAny.matches("k"))
                }
                """;
        assertEquals("true\nfalse\ntrue\nfalse\n", runJvm("group", src));
    }

    @Test
    void theLogLevelShapeOfThePlan() throws Exception {
        // §3.4 logLine, restricted to this slice's forms
        String src = """
                expr level {
                    either { "INFO" or "WARN" or "ERROR" }
                }

                main() {
                    println(level.matches("INFO"))
                    println(level.matches("WARN"))
                    println(level.matches("TRACE"))
                }
                """;
        assertEquals("true\ntrue\nfalse\n", runJvm("log", src));
    }

    @Test
    void theFormatterRoundTripsEitherAndGroups() throws Exception {
        String src = """
                expr shape {
                    either { "a" "b" or "c" }
                    exactly(2) { "z" }
                }
                """;
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out, "the formatter parses the pattern");
        assertTrue(out.contains("either"), out);
        assertTrue(out.contains("or"), out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"), "fmt is idempotent on either/groups");
        String normalized = out.replace(" ", "");
        assertTrue(normalized.contains("either"), out);
    }

    @Test
    void thePtSurfaceProducesTheSameMatch() throws Exception {
        String src = """
                molde tipo {
                    qualquer { "a" ou "b" }
                }
                principal() {
                    diga(tipo.matches("a"))
                    diga(tipo.matches("c"))
                }
                """;
        Path file = tmp.resolve("pt").resolve("Main.ptkf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("pt-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
        assertEquals("true\nfalse\n", runJar(out));
    }

    @Test
    void scriptParityIsByteIdenticalThroughBranches() throws Exception {
        String src = """
                expr pick { either { "cat" or "catalog" } }
                expr alt { either { "ab" or "a" } "bc" }
                expr wordish { one_or_more { either { letter or digit } } }

                main() {
                    println(pick.matches("catalog"))
                    println(alt.matches("abc"))
                    println(wordish.matches("a1b2"))
                    println(wordish.matches("a-1"))
                }
                """;
        String expected = "true\ntrue\ntrue\nfalse\n";
        assertEquals(expected, runJvm("parity", src));
        assertEquals(expected, runScript(src), "one compiled form, two executors");
    }

    @Test
    void anUnboundedLoopOverANullableAlternationRefuses() throws Exception {
        String d = diagnostics("""
                expr bad { zero_or_more { either { "a" or optional { "b" } } } "!" }
                main() { println(bad.matches("!")) }
                """);
        assertTrue(d.contains("EXPR003"), () -> d);
    }

    @Test
    void anUnboundedQuantifierInsideABranchIsStillReDoS() throws Exception {
        String d = diagnostics("""
                expr bad { one_or_more { either { "x" or one_or_more { "y" } } } }
                main() { println(bad.matches("x")) }
                """);
        assertTrue(d.contains("EXPR002") && d.contains("ReDoS"), () -> d);
    }

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), tag + ": " + r.diagnostics().getDiagnostics());
        return runJar(out);
    }

    private String runJar(Path out) throws Exception {
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
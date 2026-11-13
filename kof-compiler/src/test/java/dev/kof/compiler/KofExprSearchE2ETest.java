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
 * Kof.expr slice 8, part 1 (`D-KOF-EXPR`) — the search operations over the
 * SAME state chain with a FREE terminal (return-pos-anywhere; `matches` keeps
 * its whole-subject accept): `find(text): Int` (the first start index, -1 when
 * absent), `findAll(text): List<String>?` (non-capturing) / `List<nameMatch>?`
 * (capturing), `replace(text, replacement): String` (LITERAL replacement in
 * v1, §7). The §12 budget: a subject above 1,000,000 characters refuses at
 * runtime with EXPR-E2 (the engine has no catastrophic backtracking by
 * construction — EXPR002/003 refuse the shapes — so the budget is a documented
 * bound). PLUS the honest gate from slice 7's finding: the gate moves to the
 * DECLARATION (EXPR004 for JS and Native on ANY expr — a literal-only pattern
 * died with the internal COMP002 before).
 */
class KofExprSearchE2ETest {

    @TempDir
    Path tmp;

    private static final String WORD = """
            expr w { one_or_more { letter } }
            """;

    @Test
    void findReturnsTheFirstStartIndexOfAnOccurrence() throws Exception {
        String src = WORD + """
                main() {
                    println(w.find("kof é kof"))
                    println(w.find("42 kof"))
                    println(w.find("42!"))
                }
                """;
        assertEquals("0\n3\n-1\n", runJvm("find", src));
    }

    @Test
    void findAllReturnsEveryOccurrenceSubstringInOrder() throws Exception {
        String src = WORD + """
                main() {
                    var xs = w.findAll("um dois três")
                    if (xs != null) {
                        var a = xs[0]
                        if (a != null) { println(a) }
                        var b = xs[1]
                        if (b != null) { println(b) }
                        var c = xs[2]
                        if (c != null) { println(c) }
                        println(xs.length())
                    } else {
                        println("none")
                    }
                    var none = w.findAll("123")
                    if (none != null) { println(none.length()) } else { println("none") }
                }
                """;
        assertEquals("um\ndois\ntrês\n3\n0\n", runJvm("all", src));
    }

    @Test
    void replaceIsLiteralAndLeavesANonMatchUntouched() throws Exception {
        String src = WORD + """
                main() {
                    println(w.replace("oi kof oi", "KOF"))
                    println(w.replace("123", "KOF"))
                }
                """;
        assertEquals("KOF KOF KOF\n123\n", runJvm("replace", src));
    }

    @Test
    void searchFacesOnACapturingPatternReturnTheTypedRecord() throws Exception {
        String src = """
                expr date {
                    capture("day") { exactly(2) digit }
                    "/"
                    capture("month") { exactly(2) digit }
                }

                main() {
                    println(date.find("x 25/12 y"))
                    var ms = date.findAll("25/12 e 03/07")
                    if (ms != null) {
                        var first = ms[0]
                        if (first != null) {
                            var d = first.day
                            if (d != null) { println(d) }
                        }
                    } else {
                        println("none")
                    }
                    println(date.replace("x 25/12", "data"))
                }
                """;
        assertEquals("2\n25\nx data\n", runJvm("capturing", src));
    }

    @Test
    void emptyMatchesAdvanceWithoutLooping() throws Exception {
        // optional matches empty at every position: findAll must terminate
        String src = """
                expr maybe { optional { "a" } }
                expr z { zero_or_more { "b" } }

                main() {
                    var xs = maybe.findAll("bb")
                    if (xs != null) { println(xs.length()) }
                    var zs = z.findAll("bb")
                    if (zs != null) { println(zs.length()) }
                }
                """;
        // optional casa vazio em 0, 1 e 2; zero_or_more é greedy e casa "bb" de uma vez
        assertEquals("3\n1\n", runJvm("empty", src));
    }

    @Test
    void aSubjectAboveTheDocumentedBudgetRefusesAtRuntime() throws Exception {
        // §12: a documented maximum subject length, a distinct runtime family.
        // The v1 matcher is recursive CPS (depth O(subject), ~3 frames/char);
        // the budget pins the MEASURED stack-safe bound (2,000 pass, 4,000
        // overflow on a default JVM stack). The subject is built past it.
        String src = WORD + """
                main() {
                    var big = ""
                    var i = 0
                    while (i < 402) {
                        big = big + "aaaaa"
                        i = i + 1
                    }
                    println(w.find(big))
                }
                """;
        Path file = tmp.resolve("budget").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("budget-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String[] caught = new String[1];
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (java.lang.reflect.InvocationTargetException e) {
            caught[0] = String.valueOf(e.getCause());
        } finally {
            System.setOut(old);
        }
        // o orçamento é um throw de runtime com a família EXPR-E2: a mensagem
        // carrega o código e a borda documentada
        assertTrue(caught[0] != null && caught[0].contains("EXPR-E2"),
                () -> "msg=" + caught[0] + " out=" + buf);
        assertTrue(caught[0].contains("2,000"), () -> caught[0]);
    }

    @Test
    void theScriptBudgetIsTighterAndIsProvenOnTheInterpreter() throws Exception {
        // MEDIDO: o interpretador Script estoura o frame dele em ~400 chars —
        // o orçamento por executor pina 200 no Script. Um sujeito de 300
        // passa na JVM e recusa EXPR-E2 no interpretador.
        String src = WORD + """
                main() {
                    var big = ""
                    var i = 0
                    while (i < 60) {
                        big = big + "aaaaa"
                        i = i + 1
                    }
                    println(w.find(big))
                }
                """;
        Path file = tmp.resolve("script-budget").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertTrue(r.exitCode() != 0, "the Script run must refuse: exit=" + r.exitCode());
        assertTrue((r.stdout() + r.stderr()).contains("EXPR-E2"), () -> r.stdout() + r.stderr());
        assertTrue((r.stdout() + r.stderr()).contains("200"), () -> r.stdout() + r.stderr());
    }

    @Test
    void aLiteralOnlyPatternStillRefusesOnJsWithExpr004() throws Exception {
        // slice 7's finding: the MATCHER (not just the classes) does not lower
        // on JS — the gate is at the DECLARATION, so a literal-only pattern gets
        // the honest EXPR004 instead of the internal COMP002.
        //
        // slice 6 (13/11): on the native (x86-64 AND the cross riscv64/
        // aarch64) the SAME compiled chain runs — the backend is a 1:1
        // op→instruction emitter; the proof is the native execution in
        // KofExprNativeE2ETest and KofExprCrossE2ETest.
        String src = """
                expr pick { either { "cat" or "dog" } }
                main() { println(pick.matches("cat")) }
                """;
        String js = diagnostics(src, Target.JS);
        assertTrue(js.contains("EXPR004") && js.contains("JS"), () -> js);
        assertTrue(js.contains("structured-flow"), () -> js);
    }

    @Test
    void scriptParityIsByteIdenticalThroughSearch() throws Exception {
        String src = WORD + """
                main() {
                    println(w.find("42 kof"))
                    var xs = w.findAll("um dois")
                    if (xs != null) {
                        var a = xs[0]
                        if (a != null) { println(a) }
                        var b = xs[1]
                        if (b != null) { println(b) }
                    }
                    println(w.replace("oi kof", "KOF"))
                }
                """;
        String expected = "3\num\ndois\nKOF KOF\n";
        assertEquals(expected, runJvm("parity", src));
        assertEquals(expected, runScript(src), "one compiled form, two executors");
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

    private String diagnostics(String src, Target target) throws Exception {
        Path file = tmp.resolve("diag-" + target).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("diag-out-" + target), target);
        assertFalse(r.success(), "the pattern must refuse on " + target + ": " + src);
        return r.diagnostics().getDiagnostics().toString();
    }
}
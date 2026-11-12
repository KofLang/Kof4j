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
 * Kof.expr slice 5 (`D-KOF-EXPR`) — captures + the typed match result, the
 * plan §6 Option T: an expr WITH captures induces a small value type (a
 * generated record `nameMatch` whose fields are the captures — `date.day`,
 * `date.month`, `date.year`). The contract, honest about optionality:
 * `match(text)` returns `nameMatch?`; the record's fields are `String?` — a
 * capture outside the matched path (an untaken `either` branch, an optional
 * group) is null; a capture inside a quantifier keeps its LAST instance (the
 * successful path's final write); `matches(text)` stays Bool; `captures(text):
 * List<String>?` returns the captured values in declaration order. Duplicate
 * capture names are already SEM110.
 */
class KofExprCaptureE2ETest {

    @TempDir
    Path tmp;

    private static final String DATE = """
            expr date {
                capture("day") { exactly(2) digit }
                "/"
                capture("month") { exactly(2) digit }
                "/"
                capture("year") { exactly(4) digit }
            }
            """;

    @Test
    void matchReturnsTheGeneratedRecordWithTypedFields() throws Exception {
        String src = DATE + """
                main() {
                    var m = date.match("25/12/2025")
                    if (m != null) {
                        var d = m.day
                        if (d != null) { println(d) }
                        var mo = m.month
                        if (mo != null) { println(mo) }
                        var y = m.year
                        if (y != null) { println(y) }
                    } else {
                        println("none")
                    }
                }
                """;
        assertEquals("25\n12\n2025\n", runJvm("date", src));
    }

    @Test
    void matchReturnsNullWhenNoMatch() throws Exception {
        String src = DATE + """
                main() {
                    var m = date.match("2/12/2025")
                    if (m != null) { println("hit") } else { println("none") }
                }
                """;
        assertEquals("none\n", runJvm("nomatch", src));
    }

    @Test
    void matchesStaysBoolOnCapturingPatterns() throws Exception {
        String src = DATE + """
                main() {
                    println(date.matches("25/12/2025"))
                    println(date.matches("2/12/2025"))
                }
                """;
        assertEquals("true\nfalse\n", runJvm("bool", src));
    }

    @Test
    void capturesReturnsTheValuesInDeclarationOrder() throws Exception {
        String src = DATE + """
                main() {
                    var cs = date.captures("25/12/2025")
                    if (cs != null) {
                        var c0 = cs[0]
                        if (c0 != null) { println(c0) }
                        var c1 = cs[1]
                        if (c1 != null) { println(c1) }
                        var c2 = cs[2]
                        if (c2 != null) { println(c2) }
                    } else {
                        println("none")
                    }
                }
                """;
        assertEquals("25\n12\n2025\n", runJvm("caps", src));
    }

    @Test
    void aCaptureOutsideTheMatchedPathIsNull() throws Exception {
        String src = """
                expr pick {
                    either { capture("tag") { "cat" } or "dog" }
                }

                main() {
                    var m = pick.match("cat")
                    if (m != null) {
                        var t = m.tag
                        if (t != null) { println(t) } else { println("none") }
                    }
                    var n = pick.match("dog")
                    if (n != null) {
                        var u = n.tag
                        if (u != null) { println(u) } else { println("none") }
                    }
                }
                """;
        assertEquals("cat\nnone\n", runJvm("optional", src));
    }

    @Test
    void aCaptureInAQuantifierKeepsTheLastInstance() throws Exception {
        String src = """
                expr words {
                    zero_or_more {
                        capture("w") { one_or_more { letter } }
                        " "
                    }
                }

                main() {
                    var m = words.match("ab cd ")
                    if (m != null) {
                        var w = m.w
                        if (w != null) { println(w) } else { println("none") }
                    }
                }
                """;
        assertEquals("cd\n", runJvm("last", src));
    }

    @Test
    void duplicateCaptureNamesAreStillSem110() throws Exception {
        String d = diagnostics("""
                expr bad { capture("v") { "a" } "-" capture("v") { "b" } }
                main() { println(bad.matches("a-b")) }
                """);
        assertTrue(d.contains("SEM110"), () -> d);
    }

    @Test
    void aCaptureNameThatIsNotAnIdentifierIsRefused() throws Exception {
        String d = diagnostics("""
                expr bad { capture("my day") { "a" } }
                main() { println(bad.matches("a")) }
                """);
        assertTrue(d.contains("EXPR005") && d.contains("identifier"), () -> d);
    }

    @Test
    void scriptParityIsByteIdenticalThroughCaptures() throws Exception {
        String src = DATE + """
                main() {
                    var m = date.match("03/07/2024")
                    if (m != null) {
                        var d = m.day
                        if (d != null) { println(d) }
                        var y = m.year
                        if (y != null) { println(y) }
                    } else {
                        println("none")
                    }
                    println(date.matches("03/07/2024"))
                }
                """;
        String expected = "03\n2024\ntrue\n";
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

    private String diagnostics(String src) throws Exception {
        Path file = tmp.resolve("diag").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("diag-out"), Target.JVM);
        assertFalse(r.success(), "the pattern must refuse: " + src);
        return r.diagnostics().getDiagnostics().toString();
    }
}
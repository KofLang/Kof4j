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
 * Kof.expr slice 9 part 1 (`D-KOF-EXPR`) — the §3.4 MANDATORY EXAMPLES as an
 * E2E corpus: the plan's own acceptance faces. Six of the seven examples are
 * expressible with the landed slices (identifier/email/uuid/phone/date/
 * logLine — slices 2-5) and must run byte-identically on the landed targets
 * (JVM + Script). `ipv4` demonstrates composition by name (`octet`), which is
 * still EXPR001 (its slice) — the corpus asserts that refusal honestly.
 *
 * <p>§3.4 notation reconciliation: the plan's example bodies like
 * `one_or_more { letter digit "_" }` read as a SEQUENCE per the locked
 * grammar (the quantifier covers the whole block as one unit); the
 * character-class intent is written `one_or_more { either { … } }` (slice 4).
 * Both forms are carried here.
 */
class KofExprCorpusE2ETest {

    @TempDir
    Path tmp;

    @Test
    void theIdentifierExampleMatchesLetterDigitUnderscoreRuns() throws Exception {
        String src = """
                expr identifier {
                    one_or_more { either { letter or digit or "_" } }
                }

                main() {
                    println(identifier.matches("user_name_2"))
                    println(identifier.matches("user-2"))
                    println(identifier.matches(""))
                }
                """;
        assertEquals("true\nfalse\nfalse\n", runJvm("identifier", src));
    }

    @Test
    void theEmailExampleMatchesAndRequiresTheTld() throws Exception {
        String src = """
                expr email {
                    start
                    one_or_more { either { letter or digit or "." or "_" or "%" or "+" or "-" } }
                    "@"
                    one_or_more { either { letter or digit or "." or "-" } }
                    "."
                    between(2, 63) letter
                    end
                }

                main() {
                    println(email.matches("a_b%c+d@example.com"))
                    println(email.matches("a@x-y.co"))
                    println(email.matches("a@x.c"))
                    println(email.matches("a@x.12"))
                    println(email.matches("no-at-sign.com"))
                }
                """;
        assertEquals("true\ntrue\nfalse\nfalse\nfalse\n", runJvm("email", src));
    }

    @Test
    void theUuidExampleMatchesTheHexGroups() throws Exception {
        String src = """
                expr uuid {
                    exactly(8) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(12) hex
                }

                main() {
                    println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789ab"))
                    println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789a"))
                    println(uuid.matches("geadBEEF-1234-abcd-AAAA-0123456789ab"))
                }
                """;
        assertEquals("true\nfalse\nfalse\n", runJvm("uuid", src));
    }

    @Test
    void thePhoneExampleMatchesThePlanShape() throws Exception {
        String src = """
                expr phone {
                    optional("+55")
                    optional(" ")
                    optional("(")
                    exactly(2) digit
                    optional(")")
                    optional(" ")
                    exactly(4) digit
                    "-"
                    exactly(4) digit
                }

                main() {
                    println(phone.matches("+55 (11) 9876-5432"))
                    println(phone.matches("11 9876-5432"))
                    println(phone.matches("1198765432"))
                    println(phone.matches("+55 11 9876-543"))
                }
                """;
        // "1198765432" NÃO casa: o traço é obrigatório na forma do plano
        assertEquals("true\ntrue\nfalse\nfalse\n", runJvm("phone", src));
    }

    @Test
    void theDateExampleCapturesThroughTheGeneratedRecord() throws Exception {
        String src = """
                expr date {
                    capture("day")   { exactly(2) digit }
                    "/"
                    capture("month") { exactly(2) digit }
                    "/"
                    capture("year")  { exactly(4) digit }
                }

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
                    println(date.matches("2/12/2025"))
                }
                """;
        assertEquals("25\n12\n2025\nfalse\n", runJvm("date", src));
    }

    @Test
    void theLogLineExampleCapturesTimeLevelAndMessage() throws Exception {
        String src = """
                expr logLine {
                    capture("time")  { exactly(2) digit ":" exactly(2) digit }
                    " "
                    capture("level") { one_of("INFO", "WARN", "ERROR") }
                    " "
                    capture("msg")   { zero_or_more any }
                }

                main() {
                    var m = logLine.match("12:30 INFO servidor subiu")
                    if (m != null) {
                        var t = m.time
                        if (t != null) { println(t) }
                        var l = m.level
                        if (l != null) { println(l) }
                        var msg = m.msg
                        if (msg != null) { println(msg) }
                    } else {
                        println("none")
                    }
                    var e = logLine.match("12:30 TRACE subiu")
                    if (e != null) { println("matched") } else { println("none") }
                }
                """;
        assertEquals("12:30\nINFO\nservidor subiu\nnone\n", runJvm("log", src));
    }

    @Test
    void ipv4CompositionByReferenceMatchesAndRefusesTheWrongText() throws Exception {
        // §3.4 (o exemplo ipv4): a composição por NOME — `octet` declarado
        // uma vez e referenciado quatro vezes. O lowering inlínea o corpo do
        // referido no ponto da referência (o analisador já garante SEM108
        // desconhecido / SEM109 ciclo); a paridade é o oráculo JVM × Script.
        String src = """
                expr ipv4 {
                    octet
                    "."
                    octet
                    "."
                    octet
                    "."
                    octet
                }

                expr octet {
                    between(1, 3) digit
                }

                main() {
                    println(ipv4.matches("1.2.3.4"))
                    println(ipv4.matches("192.168.0.255"))
                    println(ipv4.matches("1.2.3"))
                    println(ipv4.matches("1.2.3.4.5"))
                    println(ipv4.matches("a.b.c.d"))
                }
                """;
        String expected = "true\ntrue\nfalse\nfalse\nfalse\n";
        assertEquals(expected, runJvm("ipv4", src));
        assertEquals(expected, runScript(src), "one corpus, two executors");
    }

    @Test
    void scriptParityIsByteIdenticalOnTheCorpus() throws Exception {
        String src = """
                expr identifier { one_or_more { either { letter or digit or "_" } } }
                expr uuid {
                    exactly(8) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(12) hex
                }
                expr date {
                    capture("day") { exactly(2) digit }
                    "/"
                    capture("month") { exactly(2) digit }
                }

                main() {
                    println(identifier.matches("user_name_2"))
                    println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789ab"))
                    var m = date.match("25/12")
                    if (m != null) {
                        var d = m.day
                        if (d != null) { println(d) }
                    }
                }
                """;
        String expected = "true\ntrue\n25\n";
        assertEquals(expected, runJvm("parity", src));
        assertEquals(expected, runScript(src), "one corpus, two executors");
    }

    @Test
    void jsParityIsByteIdenticalOnTheFullCorpus() throws Exception {
        // fatia 7 FECHADA (14/11): as sete faces obrigatorias do paragrafo 3.4
        // no artefato node vs o oraculo JVM - o estado de laco reestruturado
        // (tres estados de if unico), o predicado de classe reparado
        // (kofExprInRange lia a tabela como array-de-chars) e o roteamento
        // proprio dos predicados fizeram o JS responder byte-identico.
        String src = """
                expr identifier { one_or_more { either { letter or digit or "_" } } }
                expr email {
                    start
                    one_or_more { either { letter or digit or "." or "_" or "%" or "+" or "-" } }
                    "@"
                    one_or_more { either { letter or digit or "." or "-" } }
                    "."
                    between(2, 63) letter
                    end
                }
                expr uuid {
                    exactly(8) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(4) hex
                    "-"
                    exactly(12) hex
                }
                expr phone {
                    optional("+55")
                    optional(" ")
                    optional("(")
                    exactly(2) digit
                    optional(")")
                    optional(" ")
                    exactly(4) digit
                    "-"
                    exactly(4) digit
                }
                expr date {
                    capture("day")   { exactly(2) digit }
                    "/"
                    capture("month") { exactly(2) digit }
                    "/"
                    capture("year")  { exactly(4) digit }
                }
                expr logLine {
                    capture("time")  { exactly(2) digit ":" exactly(2) digit }
                    " "
                    capture("level") { one_of("INFO", "WARN", "ERROR") }
                    " "
                    capture("msg")   { zero_or_more any }
                }

                main() {
                    println(identifier.matches("user_name_2"))
                    println(identifier.matches("user-2"))
                    println(email.matches("a_b%c+d@example.com"))
                    println(email.matches("no-at-sign.com"))
                    println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789ab"))
                    println(uuid.matches("geadBEEF-1234-abcd-AAAA-0123456789ab"))
                    println(phone.matches("+55 (11) 9876-5432"))
                    println(phone.matches("1198765432"))
                    var m = date.match("25/12/2025")
                    if (m != null) {
                        var d = m.day
                        if (d != null) { println(d) }
                        var y = m.year
                        if (y != null) { println(y) }
                    } else { println("none") }
                    var e = logLine.match("12:30 INFO servidor subiu")
                    if (e != null) {
                        var t = e.time
                        if (t != null) { println(t) }
                        var l = e.level
                        if (l != null) { println(l) }
                        var msg = e.msg
                        if (msg != null) { println(msg) }
                    } else { println("none") }
                    var f = logLine.match("12:30 TRACE subiu")
                    if (f != null) { println("matched") } else { println("none") }
                }
                """;
        String expected = runJvm("js-corpus", src);
        assertEquals(expected, runNode(src), "one corpus, three executors");
    }

    private String runNode(String src) throws Exception {
        Path file = tmp.resolve("js-node").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("js-node-out"), Target.JS);
        assertTrue(r.success(), "js: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder("node", tmp.resolve("js-node-out/Default.mjs").toString());
        pb.redirectErrorStream(true);
        var p = pb.start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").stripTrailing();
        p.waitFor();
        assertEquals(0, p.exitValue(), "the JS run must exit clean: " + outText);
        return outText + "\n";
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
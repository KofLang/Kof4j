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
 * Kof.expr slice 3 (`D-KOF-EXPR`) — the character classes over UNICODE SCALAR
 * VALUES (plan §10: the subject model is the Kof string model, so a character
 * is a code point and a non-BMP character is ONE `any`), with the PINNED,
 * versioned table the plan requires: **Unicode 16.0.0** (measured on this JDK —
 * U+1C89 is a letter only since 16.0). `letter`/`digit`/`whitespace`/`word`/
 * `hex`/`any` + the list atoms `one_of`/`none_of`/`literal`; `hex` is ASCII by
 * definition (§10). The classes are runtime predicates taking the code point
 * (one definition for JVM and Script, no table folded into the artifact), and
 * JS/Native refuse at compile with EXPR004 naming their slices (7 and 6) — the
 * no-silent-fallback law.
 */
class KofExprClassesE2ETest {

    @TempDir
    Path tmp;

    @Test
    void letterOverAsciiAndNonAscii() throws Exception {
        String src = """
                expr name {
                    one_or_more { letter }
                }

                main() {
                    println(name.matches("Kof"))
                    println(name.matches("ação"))
                    println(name.matches("Ωμέγα"))
                    println(name.matches("Kof4j"))
                    println(name.matches(""))
                }
                """;
        assertEquals("true\ntrue\ntrue\nfalse\nfalse\n", runJvm("letter", src));
    }

    @Test
    void digitAndHexKeepTheirDocumentedRanges() throws Exception {
        String src = """
                expr num { one_or_more { digit } }
                expr hx { exactly(2) hex }

                main() {
                    println(num.matches("42"))
                    println(num.matches("٤٢"))
                    println(num.matches("4a"))
                    println(hx.matches("0f"))
                    println(hx.matches("AF"))
                    println(hx.matches("gg"))
                    println(hx.matches("é9"))
                }
                """;
        assertEquals("true\ntrue\nfalse\ntrue\ntrue\nfalse\nfalse\n", runJvm("digit", src));
    }

    @Test
    void whitespaceAndWordFollowThePinnedTable() throws Exception {
        String src = """
                expr gap { one_or_more { whitespace } }
                expr ident { one_or_more { word } }

                main() {
                    println(gap.matches(" \\t\\n"))
                    println(gap.matches("x"))
                    println(ident.matches("a_9"))
                    println(ident.matches("ação_2"))
                    println(ident.matches("a-b"))
                }
                """;
        assertEquals("true\nfalse\ntrue\ntrue\nfalse\n", runJvm("ws", src));
    }

    @Test
    void aNonBmpCharacterIsOneAnyNotTwoUnits() throws Exception {
        // U+1F600 is a surrogate PAIR in UTF-16: one `any` must consume both,
        // and exactly(2) any must not accept it (the pair is ONE character).
        String src = """
                expr one { exactly(1) any }
                expr two { exactly(2) any }

                main() {
                    println(one.matches("\\uD83D\\uDE00"))
                    println(two.matches("\\uD83D\\uDE00"))
                    println(two.matches("\\uD83D\\uDE00a"))
                }
                """;
        assertEquals("true\nfalse\ntrue\n", runJvm("bmp", src));
    }

    @Test
    void thePinnedUnicodeVersionIsProven() throws Exception {
        // U+1C89 (CYRILLIC CAPITAL LETTER TJE) became a letter in Unicode 16.0.
        String src = """
                expr tje { exactly(1) letter }

                main() {
                    println(tje.matches("\\u1C89"))
                }
                """;
        assertEquals("true\n", runJvm("pinned", src));
    }

    @Test
    void oneOfAndNoneOfUseCodePointMembership() throws Exception {
        String src = """
                expr vowel { one_of("a", "e", "i", "o", "u") }
                expr anyButVowel { none_of("a", "e", "i", "o", "u") }

                main() {
                    println(vowel.matches("e"))
                    println(vowel.matches("b"))
                    println(anyButVowel.matches("b"))
                    println(anyButVowel.matches("a"))
                    println(anyButVowel.matches("é"))
                }
                """;
        assertEquals("true\nfalse\ntrue\nfalse\ntrue\n", runJvm("oneof", src));
    }

    @Test
    void classesComposeWithQuantifiersAndLiterals() throws Exception {
        // the plan §3.4 phone shape, now with the real classes
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
                    println(phone.matches("+55 (1) 9876-5432"))
                }
                """;
        assertEquals("true\ntrue\nfalse\n", runJvm("phone", src));
    }

    @Test
    void classScriptParityIsByteIdentical() throws Exception {
        String src = """
                expr name { one_or_more { letter } }
                expr num { one_or_more { digit } }
                expr one { exactly(1) any }
                expr vowel { one_of("a", "e") }

                main() {
                    println(name.matches("ação"))
                    println(num.matches("٤٢"))
                    println(one.matches("\\uD83D\\uDE00"))
                    println(vowel.matches("e"))
                    println(vowel.matches("b"))
                }
                """;
        String expected = "true\ntrue\ntrue\ntrue\nfalse\n";
        assertEquals(expected, runJvm("parity", src));
        assertEquals(expected, runScript(src),
                "one definition, two executors: the interpreter and the JVM agree byte for byte");
    }

    @Test
    void oneOfTakesMultiCharacterAlternatives() throws Exception {
        // §3.4 (o logLine obrigatório): as alternativas do one_of são literais
        // de qualquer tamanho — a primeira que casa vence (determinístico)
        String src = """
                expr tag { one_of("ab", "c") }

                main() {
                    println(tag.matches("ab"))
                    println(tag.matches("c"))
                    println(tag.matches("a"))
                }
                """;
        assertEquals("true\ntrue\nfalse\n", runJvm("multi", src));
    }

    @Test
    void jsParityOnTheClassesThroughNode() throws Exception {
        // Fatia 7 FECHADA (14/11): o JS AGORA BAIXA o matcher e responde
        // byte-idêntico ao oráculo JVM — o estado de laço reestruturado em
        // três estados de if único + o predicado de classe reparado
        // (kofExprInRange lia a tabela como array-de-chars: parseInt sobre
        // '-'/'/' dava NaN e o search "aleatório" retornava true para
        // qualquer cp). O oráculo é executado DOS OS lados.
        String src = """
                expr name { one_or_more { letter } }
                main() {
                    println(name.matches("ação"))
                    println(name.matches("abc"))
                    println(name.matches("a1"))
                }
                """;
        String expected = runJvm(src);
        assertEquals(expected, runNode(src),
                "one corpus, the class face on the JS artifact vs the JVM oracle");
    }

    private String runJvm(String src) throws Exception {
        Path file = tmp.resolve("cls-jvm").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("cls-jvm-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), () -> "jvm: " + r.diagnostics().getDiagnostics());
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

    private String runNode(String src) throws Exception {
        Path file = tmp.resolve("cls-js").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("cls-js-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), () -> "js: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder("node", out.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        var p = pb.start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").stripTrailing();
        p.waitFor();
        assertEquals(0, p.exitValue(), "the JS run must exit clean: " + outText);
        return outText + "\n";
    }

    @Test
    void thePinnedTableDataCarriesTheUnicode16Letter() throws Exception {
        // A TABELA como dado é a face JS da pin (GraalJS não tem isLetter): os
        // intervalos commitados têm de carregar U+1C89 (letra só desde o
        // Unicode 16.0) como LETRA e como NÃO-hex — o mesmo valor que a face
        // JVM assegura pelo Character medido.
        assertTrue(KofExprUnicode.containsRange(KofExprUnicode.RANGES_LETTER, 0x1C89),
                "U+1C89 (letra desde o Unicode 16.0) está nos intervalos de letra");
        assertFalse(KofExprUnicode.containsRange(KofExprUnicode.RANGES_HEX, 0x1C89),
                "U+1C89 não é hex");
        assertFalse(KofExprUnicode.containsRange(KofExprUnicode.RANGES_LETTER, 0x1FAE8),
                "U+1FAE8 (símbolo do 15.0) não é letra");
        assertTrue(KofExprUnicode.containsRange(KofExprUnicode.RANGES_DIGIT, 0x10D40),
                "U+10D40 (dígito desde o Unicode 16.0) está nos intervalos de dígito");
        assertTrue(KofExprUnicode.containsRange(KofExprUnicode.RANGES_SPACE, 0x2028),
                "U+2028 (separador de linha) está nos intervalos de espaço");
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
        return diagnostics(src, Target.JVM);
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
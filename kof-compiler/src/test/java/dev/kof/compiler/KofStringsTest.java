package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S2a — kof.strings predicados (isAlpha/isNumeric) nos 3 targets
 * compiláveis (JVM/Native/JS); o interpretador (SCRIPT) herda por reflexão
 * no KofRuntime gerado — matriz stdstrings cobre os 4.
 * Paridade de vazio/null: "" e null => false (decisão registrada no plano).
 */
class KofStringsTest extends KofStringsSupport {

    @Test
    void stringsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, ALL_JVM, "true\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\ntrue\nfalse\nfalse|\n2\n1\n0\n0\nHello\nHello\n1abc\n|\ncba\nracecar\n|\nababab\n|\n|\nhello\nabc\n|\n007\nab---\nabc\nabc");
    }

    @Test
    void stringsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, ALL_NATIVE, "ok");
    }

    @Test
    void wordConvertersMatchBriefing(@TempDir Path tmp) throws Exception {
        // §5 do briefing stdlib: word-split de HTTPServer/XMLParser não é split(" ").
        runJvm(tmp, """
            main() {
                println(strings.toSnakeCase("hello world"))
                println(strings.toSnakeCase("helloWorld"))
                println(strings.toSnakeCase("HTTPServer"))
                println(strings.toSnakeCase("XMLParser"))
                println(strings.toCamelCase("hello_world"))
                println(strings.toCamelCase("hello-world"))
                println(strings.toCamelCase("HTTPServer"))
                println(strings.toPascalCase("hello world"))
                println(strings.toKebabCase("XMLParser"))
                println(strings.slugify("Hello, World!! 42"))
            }
            """, "hello_world\nhello_world\nhttp_server\nxml_parser\nhelloWorld\nhelloWorld\nhttpServer\nHelloWorld\nxml-parser\nhello-world-42");
    }

    @Test
    void wordConvertersNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(strings.toSnakeCase("hello world") == "hello_world")
                assert(strings.toSnakeCase("helloWorld") == "hello_world")
                assert(strings.toSnakeCase("HTTPServer") == "http_server")
                assert(strings.toSnakeCase("XMLParser") == "xml_parser")
                assert(strings.toCamelCase("hello_world") == "helloWorld")
                assert(strings.toCamelCase("HTTPServer") == "httpServer")
                assert(strings.toPascalCase("hello world") == "HelloWorld")
                assert(strings.toKebabCase("XMLParser") == "xml-parser")
                assert(strings.slugify("Hello, World!! 42") == "hello-world-42")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void wordConvertersClosedOnCrossArch(@TempDir Path tmp) throws Exception {
        // STRN001 FECHADO 09/09: joinWords portado p/ riscv (B15) + aarch
        // (mesmo asm traduzido). Antes reportava STRN001; agora compila nos
        // dois e executa byte-idêntico ao x86 (prova por diff do golden no
        // KofStringsTest + matrix stdstrings2b4 já em 4 targets).
        String src = """
            main() {
                assert(strings.toSnakeCase("HTTPServer") == "http_server")
                assert(strings.toSnakeCase("XMLParser") == "xml_parser")
                assert(strings.toCamelCase("hello_world") == "helloWorld")
                assert(strings.toPascalCase("hello world") == "HelloWorld")
                assert(strings.toKebabCase("helloWorld") == "hello-world")
                assert(strings.slugify("Hello, World!! 42") == "hello-world-42")
            }
            """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            runQemu(tmp, t, qemu, src);
        }
    }

    @Test
    void uncapitalizeAllTargets(@TempDir Path tmp) throws Exception {
        // S11: uncapitalize = espelho do capitalize (1º byte A-Z->a-z; null/""/
        // fora-de-A-Z => original). ASCII, paridade byte-a-byte nos 5 alvos.
        String golden = """
            main() {
                println(strings.uncapitalize("Hello World"))
                println(strings.uncapitalize("HELLO"))
                println(strings.uncapitalize("1abc"))
                println(strings.uncapitalize("hello"))
                println(strings.uncapitalize("") + "|")
            }
            """;
        String expected = "hello World\nhELLO\n1abc\nhello\n|";
        runJvm(tmp, golden, expected);
        runJs(tmp, golden, expected);
        runNative(tmp, golden, expected);
        String assertSrc = """
            main() {
                assert(strings.uncapitalize("Hello World") == "hello World")
                assert(strings.uncapitalize("HELLO") == "hELLO")
                assert(strings.uncapitalize("1abc") == "1abc")
                assert(strings.uncapitalize("hello") == "hello")
                assert(strings.uncapitalize("") == "")
            }
            """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            runQemu(tmp, t, qemu, assertSrc);
        }
    }

    // bug 97 (face JS): String.compareTo/hashCode caíam no default do
    // JsCallEmitter → `a.compareTo()` = TypeError (não existem em
    // String.prototype). Agora: hashCode → kofHashCode (bug 42, 31*h+unit
    // UTF-16), compareTo → kofStringCompareTo (walk de code units — sem
    // localeCompare, que diverge de locale/astral). Golden = MESMO do
    // NativeE2ETest.nativeStringCompareToAndHashCodeUtf16 (medido no oracle
    // JVM: 31*h+charCodeAt = Java; prefixo → diff de units; astral em pair).
    @Test
    void compareToAndHashCodeJvmJsNative(@TempDir Path tmp) throws Exception {
        String golden = """
            main() {
                println("ab".compareTo("aX"))
                println("a\\u00e9".compareTo("a"))
                println("abc".compareTo("abd"))
                println("ab".compareTo("abc"))
                println("\\uD83D\\uDE00".compareTo("a"))
                println("a\\uD83D\\uDE00".compareTo("a\\uFFFD"))
                println("a\\uFFFD".compareTo("a\\uD83D\\uDE00"))
                println("abc".hashCode())
                println("a\\u00e9".hashCode())
                println("\\uD83D\\uDE00".hashCode())
                println("".hashCode())
            }
            """;
        String expected = "10\n1\n-1\n-1\n55260\n-10176\n10176\n96354\n3240\n1772899\n0";
        runJvm(tmp, golden, expected);
        runJs(tmp, golden, expected);
        runNative(tmp, golden, expected);
    }

    // D-FULL-PARITY-050 row 11: String.toCharArray() — array de CODE UNITS
    // UTF-16 (não bytes UTF-8, não code points). Astral = 2 elementos
    // (high/low surrogate); `c[i] as Int` isola a unit (um surrogate solto não
    // é um caractere exibível). Golden = oracle JVM medido no MESMO programa.
    @Test
    void toCharArrayJvmJsNative(@TempDir Path tmp) throws Exception {
        String golden = """
            main() {
                var a = "café".toCharArray()
                println(a.length)
                for (var i = 0; i < a.length; i++) {
                    println(a[i] as Int)
                }
                var e = "a😀b".toCharArray()
                println(e.length)
                for (var i = 0; i < e.length; i++) {
                    println(e[i] as Int)
                }
                println("".toCharArray().length)
            }
            """;
        String expected = "4\n99\n97\n102\n233\n4\n97\n55357\n56832\n98\n0";
        runJvm(tmp, golden, expected);
        runJs(tmp, golden, expected);
        runNative(tmp, golden, expected);
    }

    // D-STR-UNICODE (row 11): String.toUpperCase/toLowerCase fold Unicode per
    // CODE UNIT (BMP simple case mapping), not ASCII-only. The units are read
    // back via toCharArray (the already-proven face) so the golden does not
    // depend on stdout encoding. Inputs avoid locale/full-mapping exceptions
    // (ß→SS, ﬁ, İ, Deseret) that are outside the ratified per-code-unit scope:
    // café/CAFÉ (Latin-1), Greek, Cyrillic, an astral emoji (pass-through),
    // ß (unchanged by SIMPLE lowercase), ſ/ı (2-byte → 1-byte shrink) and the
    // titlecase digraphs. Oracle = the JVM measured in the same program.
    @Test
    void toUpperCaseToLowerCaseUnicodeJvmJsNative(@TempDir Path tmp) throws Exception {
        String golden = """
            main() {
                var a = "café".toUpperCase().toCharArray()
                println(a.length)
                for (var i = 0; i < a.length; i++) { println(a[i] as Int) }
                var b = "CAFÉ".toLowerCase().toCharArray()
                println(b.length)
                for (var i = 0; i < b.length; i++) { println(b[i] as Int) }
                var g = "άλφα".toUpperCase().toCharArray()
                println(g.length)
                for (var i = 0; i < g.length; i++) { println(g[i] as Int) }
                var c = "привет".toUpperCase().toCharArray()
                println(c.length)
                for (var i = 0; i < c.length; i++) { println(c[i] as Int) }
                var e = "a😀b".toUpperCase().toCharArray()
                println(e.length)
                for (var i = 0; i < e.length; i++) { println(e[i] as Int) }
                var s = "straße".toLowerCase().toCharArray()
                println(s.length)
                for (var i = 0; i < s.length; i++) { println(s[i] as Int) }
                var k = "Kſı".toUpperCase().toCharArray()
                println(k.length)
                for (var i = 0; i < k.length; i++) { println(k[i] as Int) }
                var d = "Ǆǅǆ".toLowerCase().toCharArray()
                println(d.length)
                for (var i = 0; i < d.length; i++) { println(d[i] as Int) }
                println("".toUpperCase().toCharArray().length)
                println("".toLowerCase().toCharArray().length)
            }
            """;
        String expected = "4\n67\n65\n70\n201\n4\n99\n97\n102\n233\n"
                + "4\n902\n923\n934\n913\n6\n1055\n1056\n1048\n1042\n1045\n1058\n"
                + "4\n65\n55357\n56832\n66\n6\n115\n116\n114\n97\n223\n101\n"
                + "3\n75\n83\n73\n3\n454\n454\n454\n0\n0";
        runJvm(tmp, golden, expected);
        runJs(tmp, golden, expected);
        runNative(tmp, golden, expected);
    }



    @Test
    void stringsJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(strings.isAlpha("Hello"))
                println(strings.isAlpha("Hello World"))
                println(strings.isAlpha("abc123"))
                println(strings.isAlpha("") + "|")
                println(strings.isNumeric("12345"))
                println(strings.isNumeric("12.34"))
                println(strings.isNumeric("abc"))
                println(strings.isAlphaNumeric("abc123"))
                println(strings.isAlphaNumeric("abc-123"))
                println(strings.isAscii("ola"))
                println(strings.isAscii("olá"))
                println(strings.isUpperCase("HELLO"))
                println(strings.isUpperCase("Hello"))
                println(strings.isUpperCase("123"))
                println(strings.isLowerCase("hello"))
                println(strings.isLowerCase("abc-123"))
                println(strings.isLowerCase("Hello"))
                println(strings.count("aabaabaa", "ab"))
                println(strings.count("aaa", "aa"))
                println(strings.count("abc", ""))
                println(strings.capitalize("hello"))
                println(strings.capitalize("Hello"))
                println(strings.capitalize("1abc"))
                println(strings.capitalize("") + "|")
                println(strings.reverse("abc"))
                println(strings.reverse("racecar"))
                println(strings.reverse("") + "|")
                println(strings.repeat("ab", 3))
                println(strings.repeat("x", 0) + "|")
                println(strings.repeat("", 5) + "|")
                println(strings.truncate("hello world", 5))
                println(strings.truncate("abc", 10))
                println(strings.truncate("abc", 0) + "|")
                println(strings.padLeft("7", 3, "0"))
                println(strings.padRight("ab", 5, "-"))
                println(strings.padLeft("abc", 2, "0"))
                println(strings.padRight("abc", 5, ""))
            }
            """, "true\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\nfalse\ntrue\ntrue\nfalse\n2\n1\n0\nHello\nHello\n1abc\n|\ncba\nracecar\n|\nababab\n|\n|\nhello\nabc\n|\n007\nab---\nabc\nabc");
    }




    @Test
    void escapeHtmlJvmJsNative(@TempDir Path tmp) throws Exception {
        // STDLIB S3.1: 5 chars -> entidade (amp/lt/gt/quot + apos numérica);
        // >=128 cópia; null/"" => original. Oracle Python; golden == JVM == JS
        // == x86 == riscv/aarch (diff dos 8 vetores).
        String src = """
            main() {
                println(strings.escapeHtml("a<b>&\\"'c"))
                println(strings.escapeHtml("<script>alert('x')</script>"))
                println(strings.escapeHtml("Café & ç"))
                println(strings.escapeHtml("&amp;lt;"))
                println(strings.escapeHtml(""))
                println(strings.escapeHtml("&"))
                println(strings.escapeHtml("\\"hello\\" world"))
                println(strings.escapeHtml("<a href=\\"u\\">y</a>"))
            }
            """;
        String expected = "a&lt;b&gt;&amp;&quot;&#39;c\n"
            + "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;\n"
            + "Café &amp; ç\n&amp;amp;lt;\n\n&amp;\n"
            + "&quot;hello&quot; world\n"
            + "&lt;a href=&quot;u&quot;&gt;y&lt;/a&gt;";
        runJvm(tmp, src, expected);
        runJs(tmp, src, expected);
        runNative(tmp, src, expected);
    }

    @Test
    void escapeHtmlCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(strings.escapeHtml("a<b>&\\"'c") == "a&lt;b&gt;&amp;&quot;&#39;c")
                assert(strings.escapeHtml("Café & ç") == "Café &amp; ç")
                assert(strings.escapeHtml("&amp;lt;") == "&amp;amp;lt;")
                assert(strings.escapeHtml("") == "")
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    @Test
    void escapeJsonJvmJsNative(@TempDir Path tmp) throws Exception {
        // STDLIB S3.1c: corpo de string literal JSON (RFC 8259) — backslash,
        // aspas, b/f/n/r/t de 2 chars, ctrl -> backslash-u00xx hex minusculo,
        // demais bytes copiados. Oracle Python; golden JVM == JS == x86.
        String src = """
            main() {
                println(strings.escapeJson(\"plain\"))
                println(strings.escapeJson(\"quote \\\" inside\"))
                println(strings.escapeJson(\"back\\\\slash\"))
                println(strings.escapeJson(\"tab\\there\"))
                println(strings.escapeJson(\"nl\\nline\\r\\rend\"))
                println(strings.escapeJson(\"\"))
                println(strings.escapeJson(\"both \\\" \\\\ and \\t\"))
                println(strings.escapeJson(\"a\\u0001b\\u001fc\"))
                println(strings.escapeJson(\"bell\\u0007form\\u000c\"))
            }
            """;
        String expected = "plain\nquote \\\" inside\nback\\\\slash\ntab\\there\nnl\\nline\\r\\rend\n\nboth \\\" \\\\ and \\t\na\\u0001b\\u001fc\nbell\\u0007form\\f";
        runJvm(tmp, src, expected);
        runJs(tmp, src, expected);
        runNative(tmp, src, expected);
    }

    @Test
    void escapeJsonCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(strings.escapeJson(\"plain\") == \"plain\")
                assert(strings.escapeJson(\"quote \\\" inside\") == \"quote \\\\\\\" inside\")
                assert(strings.escapeJson(\"back\\\\slash\") == \"back\\\\\\\\slash\")
                assert(strings.escapeJson(\"tab\\there\") == \"tab\\\\there\")
                assert(strings.escapeJson(\"nl\\nline\\r\\rend\") == \"nl\\\\nline\\\\r\\\\rend\")
                assert(strings.escapeJson(\"\") == \"\")
                assert(strings.escapeJson(\"both \\\" \\\\ and \\t\") == \"both \\\\\\\" \\\\\\\\ and \\\\t\")
                assert(strings.escapeJson(\"a\\u0001b\\u001fc\") == \"a\\\\u0001b\\\\u001fc\")
                assert(strings.escapeJson(\"bell\\u0007form\\u000c\") == \"bell\\\\u0007form\\\\f\")
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    @Test
    void whitespaceJvmJsNative(@TempDir Path tmp) throws Exception {
        // STDLIB S3.2: WS = {9..13,32}; >=128 NÃO é WS; ""/null => ""/null;
        // normalize: trim + colapso p/ UM espaço. Oracle Python.
        String src = """
            main() {
                println(strings.removeWhitespace("  a\\tb\\nc  "))
                println(strings.removeWhitespace("Café é"))
                println(strings.removeWhitespace(""))
                println(strings.normalizeWhitespace("  a   b  "))
                println(strings.normalizeWhitespace("x"))
                println(strings.normalizeWhitespace("   "))
                println(strings.normalizeWhitespace("a\\t\\n b"))
            }
            """;
        String expected = "abc\nCaféé\n\na b\nx\n\na b";
        runJvm(tmp, src, expected);
        runJs(tmp, src, expected);
        runNative(tmp, src, expected);
    }

    @Test
    void whitespaceCrossArch(@TempDir Path tmp) throws Exception {
        String src = """
            main() {
                assert(strings.removeWhitespace("  a\\tb\\nc  ") == "abc")
                assert(strings.removeWhitespace("Café é") == "Caféé")
                assert(strings.removeWhitespace("") == "")
                assert(strings.normalizeWhitespace("  a   b  ") == "a b")
                assert(strings.normalizeWhitespace("x") == "x")
                assert(strings.normalizeWhitespace("   ") == "")
                assert(strings.normalizeWhitespace("a\\t\\n b") == "a b")
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    @Test
    void unescapeHtmlJvmJsNative(@TempDir Path tmp) throws Exception {
        // STDLIB S3.1b: 5 nomeadas + numéricos (UTF-8 1/2/3 bytes); outro
        // "&" LITERAL; 0/surrogate/>=0x10000/overflow => LITERAL. Oracle Python.
        String src = """
            main() {
                println(strings.unescapeHtml("a&amp;b"))
                println(strings.unescapeHtml("&lt;script&gt;"))
                println(strings.unescapeHtml("&amp;amp;"))
                println(strings.unescapeHtml("&#65;&#x42;"))
                println(strings.unescapeHtml("&notreal;"))
                println(strings.unescapeHtml("&&amp;"))
                println(strings.unescapeHtml("&#0;"))
            }
            """;
        String expected = "a&b\n<script>\n&amp;\nAB\n&notreal;\n&&\n&#0;";
        runJvm(tmp, src, expected);
        runJs(tmp, src, expected);
        runNative(tmp, src, expected);
    }

    @Test
    void unescapeHtmlUtf8(@TempDir Path tmp) throws Exception {        // numéricos multi-byte: é (2B), ☀ (3B), €. JVM/JS/x86/riscv/aarch idênticos.
        String src = """
            main() {
                assert(strings.unescapeHtml("caf&#233;") == "caf\u00e9")
                assert(strings.unescapeHtml("&#9731;") == "\u2603")
                assert(strings.unescapeHtml("&#8364;") == "\u20ac")
                assert(strings.unescapeHtml("&#xD800;") == "&#xD800;")
                assert(strings.unescapeHtml("&#x110000;") == "&#x110000;")
                assert(strings.unescapeHtml("&amp;lt;") == "&lt;")
                assert(strings.unescapeHtml("&amp;quot;x") == "&quot;x")
                assert(strings.unescapeHtml("plain") == "plain")
                assert(strings.unescapeHtml("&") == "&")
            }
            """;
        runJvm(tmp, src, "");
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

}

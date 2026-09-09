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
class KofStringsTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void stringsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(strings.isAlpha("Hello"))
                println(strings.isAlpha("Hello World"))
                println(strings.isAlpha("abc123"))
                println(strings.isAlpha("") + "|")
                println(strings.isNumeric("12345"))
                println(strings.isNumeric("12.34"))
                println(strings.isNumeric("abc"))
                println(strings.isNumeric("") + "|")
                println(strings.isAlphaNumeric("abc123"))
                println(strings.isAlphaNumeric("abc-123"))
                println(strings.isAlphaNumeric("") + "|")
                println(strings.isAscii("ola"))
                println(strings.isAscii("olá"))
                println(strings.isAscii("") + "|")
                println(strings.isUpperCase("HELLO"))
                println(strings.isUpperCase("Hello"))
                println(strings.isUpperCase("123"))
                println(strings.isUpperCase("") + "|")
                println(strings.isLowerCase("hello"))
                println(strings.isLowerCase("abc-123"))
                println(strings.isLowerCase("Hello"))
                println(strings.isLowerCase("") + "|")
                println(strings.count("aabaabaa", "ab"))
                println(strings.count("aaa", "aa"))
                println(strings.count("abc", ""))
                println(strings.count("", "x"))
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
            """, "true\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\ntrue\nfalse\nfalse|\n2\n1\n0\n0\nHello\nHello\n1abc\n|\ncba\nracecar\n|\nababab\n|\n|\nhello\nabc\n|\n007\nab---\nabc\nabc");
    }

    @Test
    void stringsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(strings.isAlpha("Hello"))
                assert(!strings.isAlpha("Hello World"))
                assert(!strings.isAlpha("abc123"))
                assert(!strings.isAlpha(""))
                assert(strings.isNumeric("12345"))
                assert(!strings.isNumeric("12.34"))
                assert(!strings.isNumeric("abc"))
                assert(!strings.isNumeric(""))
                assert(strings.isAlphaNumeric("abc123"))
                assert(!strings.isAlphaNumeric("abc-123"))
                assert(!strings.isAlphaNumeric(""))
                assert(strings.isAscii("ola"))
                assert(!strings.isAscii("olá"))
                assert(!strings.isAscii(""))
                assert(strings.isUpperCase("HELLO"))
                assert(!strings.isUpperCase("Hello"))
                assert(!strings.isUpperCase("123"))
                assert(!strings.isUpperCase(""))
                assert(strings.isLowerCase("hello"))
                assert(strings.isLowerCase("abc-123"))
                assert(!strings.isLowerCase("Hello"))
                assert(!strings.isLowerCase(""))
                assert(strings.count("aabaabaa", "ab") == 2)
                assert(strings.count("aaa", "aa") == 1)
                assert(strings.count("abc", "") == 0)
                assert(strings.count("", "x") == 0)
                assert(strings.capitalize("hello") == "Hello")
                assert(strings.capitalize("Hello") == "Hello")
                assert(strings.capitalize("1abc") == "1abc")
                assert(strings.capitalize("") == "")
                assert(strings.reverse("abc") == "cba")
                assert(strings.reverse("racecar") == "racecar")
                assert(strings.reverse("") == "")
                assert(strings.repeat("ab", 3) == "ababab")
                assert(strings.repeat("x", 0) == "")
                assert(strings.repeat("", 5) == "")
                assert(strings.truncate("hello world", 5) == "hello")
                assert(strings.truncate("abc", 10) == "abc")
                assert(strings.truncate("abc", 0) == "")
                assert(strings.padLeft("7", 3, "0") == "007")
                assert(strings.padRight("ab", 5, "-") == "ab---")
                assert(strings.padLeft("abc", 2, "0") == "abc")
                assert(strings.padRight("abc", 5, "") == "abc")
                println("ok")
            }
            """, "ok");
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

    private void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) {
                    Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
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

    private String runJvm(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    private String runNative(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: " + output);
        assertEquals(expected, output, "Native output");
        return output;
    }

    private String runJs(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), err);
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output + " err: " + err.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
            assertEquals(expected, output, "JS output");
            return output;
        }
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

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir).flatMap(p -> java.util.stream.Stream.of(p))
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
}

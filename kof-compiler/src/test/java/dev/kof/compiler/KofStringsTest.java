package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
            }
            """, "true\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse|\ntrue\nfalse\nfalse\nfalse|\ntrue\ntrue\nfalse\nfalse|\n2\n1\n0\n0\nHello\nHello\n1abc\n|\ncba\nracecar\n|");
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
                println("ok")
            }
            """, "ok");
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
            }
            """, "true\nfalse\nfalse\nfalse|\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\nfalse\ntrue\ntrue\nfalse\n2\n1\n0\nHello\nHello\n1abc\n|\ncba\nracecar\n|");
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

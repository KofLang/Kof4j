package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suporte do {@code kof.strings} ({@code KofStringsTest}): os runners
 * JVM/Native/JS/qemu, o guard de toolchain e os dois programas golden maiores.
 * Vive fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code KofStringsTest} — zero drift de
 * citação.
 */
abstract class KofStringsSupport {

    protected final CompilerDriver driver = new CompilerDriver();

    protected void assumeToolchain(String... tools) {
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
    protected void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
    }
    protected String runJvm(Path tempDir, String source, String expected) throws Exception {
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
    protected String runNative(Path tempDir, String source, String expected) throws Exception {
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
    protected String runJs(Path tempDir, String source, String expected) throws Exception {
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
    protected static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir).flatMap(p -> java.util.stream.Stream.of(p))
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
    static final String ALL_JVM = """
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
            """;

    static final String ALL_NATIVE = """
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
            """;

}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofEncodingTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void encodingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(encoding.hexEncode("Hi"))
                println(encoding.hexEncode("") + "|")
                println(encoding.hexDecode("4869"))
                println(encoding.hexEncode("café"))
                println(encoding.hexDecode("636166c3a9"))
            }
            """, "4869\n|\nHi\n636166c3a9\ncafé");
    }

    @Test
    void encodingNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(encoding.hexEncode("Hi") == "4869")
                assert(encoding.hexEncode("") == "")
                assert(encoding.hexDecode("4869") == "Hi")
                assert(encoding.hexEncode("café") == "636166c3a9")
                assert(encoding.hexDecode("636166c3a9") == "café")
                // odd length: último char é nibble alto (baixo=0) => 0x36='6'
                assert(encoding.hexDecode("36") == "6")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void encodingJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(encoding.hexEncode("Hi"))
                println(encoding.hexEncode("") + "|")
                println(encoding.hexDecode("4869"))
                println(encoding.hexEncode("café"))
                println(encoding.hexDecode("636166c3a9"))
            }
            """, "4869\n|\nHi\n636166c3a9\ncafé");
    }

    @Test
    void encodingOddLengthAndInvalid(@TempDir Path tmp) throws Exception {
        // bordas travadas só no JVM (re-empacotamos via hexEncode pra não
        // imprimir NUL): ímpar => último char é nibble ALTO (baixo=0);
        // char inválido => aquele nibble é 0.
        runJvm(tmp, """
            main() {
                println(encoding.hexDecode("4869") == "Hi")
                println(encoding.hexEncode(encoding.hexDecode("486")) == "4860")
                println(encoding.hexEncode(encoding.hexDecode("4g")) == "40")
            }
            """, "true\ntrue\ntrue");
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
            assertEquals(0, ec, "JS exit code, output: " + output
                    + " err: " + err.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
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

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S1 — kof.math (Int-only) nos 3 targets compiláveis (JVM/Native/JS).
 * O interpretador (Target.SCRIPT) herda via reflexão no KofRuntime gerado
 * (mesmo source de JVMStringMathRuntime) — a matriz cobre a paridade 4-target.
 */
class KofMathTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void mathJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(math.clamp(15, 0, 10))
                println(math.clamp(-3, 0, 10))
                println(math.abs(-7))
                println(math.sign(-4))
                println(math.min(3, 8))
                println(math.max(3, 8))
                println(math.isEven(4))
                println(math.isOdd(4))
                println(math.isPositive(1))
                println(math.isNegative(0))
                println(math.isZero(0))
            }
            """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\nfalse\ntrue");
    }

    @Test
    void mathNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(math.clamp(15, 0, 10) == 10)
                assert(math.clamp(-3, 0, 10) == 0)
                assert(math.abs(-7) == 7)
                assert(math.sign(-4) == -1)
                assert(math.min(3, 8) == 3)
                assert(math.max(3, 8) == 8)
                assert(math.isEven(4))
                assert(!math.isEven(3))
                assert(math.isOdd(3))
                assert(math.isPositive(1))
                assert(!math.isNegative(0))
                assert(math.isZero(0))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void mathJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(math.clamp(15, 0, 10))
                println(math.clamp(-3, 0, 10))
                println(math.abs(-7))
                println(math.sign(-4))
                println(math.min(3, 8))
                println(math.max(3, 8))
                println(math.isEven(4))
                println(math.isOdd(4))
                println(math.isPositive(1))
                println(math.isNegative(0))
                println(math.isZero(0))
            }
            """, "10\n0\n7\n-1\n3\n8\ntrue\nfalse\ntrue\nfalse\ntrue");
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

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
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

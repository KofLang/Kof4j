package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S10 — kof.random (plan-stdlib-expansion). Shape em 4 targets:
 * double ∈ [0,1); boolean é 0/1; int(bound) ∈ [0,bound), bound<=0 => 0;
 * hex(n) tem 2n hex minúsculo, n<=0 => null. Entropia não-determinística:
 * assert-only (mesma disciplina do KofUuidTest — nunca golden de valor).
 */
class KofRandomTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void randomShapeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var d = random.double()
                assert(d >= 0.0)
                assert(d < 1.0)
                var d2 = random.double()
                assert(d2 >= 0.0 && d2 < 1.0)
                var b = random.boolean()
                assert(b == true || b == false)
                var i = random.int(10)
                assert(i >= 0 && i < 10)
                assert(random.int(0) == 0)
                assert(random.int(-5) == 0)
                var h = random.hex(8)
                assert(h.length() == 16)
                var k = 0
                while (k < h.length()) {
                    var c = h.charAt(k)
                    assert((c >= 48 && c <= 57) || (c >= 97 && c <= 102))
                    k = k + 1
                }
                assert(random.hex(0) == null)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void randomShapeNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var d = random.double()
                assert(d >= 0.0)
                assert(d < 1.0)
                var b = random.boolean()
                assert(b == true || b == false)
                var i = random.int(10)
                assert(i >= 0 && i < 10)
                assert(random.int(0) == 0)
                assert(random.int(-5) == 0)
                var h = random.hex(8)
                assert(h.length() == 16)
                var k = 0
                while (k < h.length()) {
                    var c = h.charAt(k)
                    assert((c >= 48 && c <= 57) || (c >= 97 && c <= 102))
                    k = k + 1
                }
                println("ok")
            }
            """, "ok");
    }

    @Test
    void randomShapeJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var d = random.double()
                assert(d >= 0.0)
                assert(d < 1.0)
                var i = random.int(10)
                assert(i >= 0 && i < 10)
                assert(random.int(0) == 0)
                assert(random.int(-5) == 0)
                var h = random.hex(8)
                assert(h.length() == 16)
                assert(random.hex(0) == null)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void randomShapeCrossArch(@TempDir Path tmp) throws Exception {
        // RAND001: getrandom(2) ecall 278 (primitiva SECN000/B25 confirmada
        // no qemu). Shape idêntico ao x86 — assert-only, sem golden.
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
            main() {
                var d = random.double()
                assert(d >= 0.0)
                assert(d < 1.0)
                var b = random.boolean()
                assert(b == true || b == false)
                var i = random.int(10)
                assert(i >= 0 && i < 10)
                assert(random.int(0) == 0)
                var h = random.hex(8)
                assert(h.length() == 16)
                var k = 0
                while (k < h.length()) {
                    var c = h.charAt(k)
                    assert((c >= 48 && c <= 57) || (c >= 97 && c <= 102))
                    k = k + 1
                }
            }
            """);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                var d = random.double()
                assert(d >= 0.0)
                assert(d < 1.0)
                var i = random.int(10)
                assert(i >= 0 && i < 10)
                var h = random.hex(8)
                assert(h.length() == 16)
            }
            """);
    }

    private static void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                String o = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || o.isEmpty()) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path out = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult r = driver.compile(file, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(qemu, out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " qemu exit " + ec + ", out: " + o);
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

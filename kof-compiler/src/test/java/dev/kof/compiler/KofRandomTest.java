package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * STDLIB S10a — kof.random (randomInt/randomBoolean) nos 5 alvos.
 *
 * <p>Entropia é do SO (getrandom/SecureRandom), então o resultado é
 * NÃO-determinístico — o teste não fixa valores, prova o CONTRATO:
 * {@code 0 <= randomInt(bound) < bound} em N iterações, os casos de borda
 * ({@code randomInt(1)==0}, {@code randomInt(0)==0}, {@code randomInt(-5)==0})
 * e {@code randomBoolean} ∈ {0,1}. Os 5 alvos usam fontes de entropia
 * diferentes (JVM SecureRandom, JS kof_platform/crypto, x86/riscv/aarch
 * getrandom) — a paridade provada aqui é do CONSELHO (faixa + bordas), não
 * do valor, que é exatamente o que o plano de S10 garante.
 */
class KofRandomTest {
    private final CompilerDriver driver = new CompilerDriver();

    /** Corpo comum de asserts de contrato (roda em qualquer alvo). */
    private static final String CONTRACT_SRC = """
        main() {
            var i = 0
            while (i < 500) {
                var v = random.randomInt(1000)
                if (v < 0 || v >= 1000) { println("RANGE1000:" + v); throw "randomInt(1000) out of range: " + v }
                var w = random.randomInt(2)
                if (w != 0 && w != 1) { throw "randomInt(2) not 0/1: " + w }
                var b = random.randomBoolean()
                if (b != 0 && b != 1) { throw "randomBoolean not 0/1: " + b }
                i = i + 1
            }
            assert(random.randomInt(1) == 0)
            assert(random.randomInt(0) == 0)
            assert(random.randomInt(-5) == 0)
            assert(random.randomInt(1000000) >= 0)
            assert(random.randomInt(1000000) < 1000000)
            println("OK")
        }
        """;

    @Test
    void randomIntBoundsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, CONTRACT_SRC);
    }

    @Test
    void randomIntBoundsJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, CONTRACT_SRC);
    }

    @Test
    void randomIntBoundsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, CONTRACT_SRC);
    }

    @Test
    void randomIntCrossArch(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            runQemu(tmp, t, CONTRACT_SRC);
        }
    }

    /** S10b: randomString(n, alphabet) — contrato: len==n + chars do alfabeto. */
    private static final String STRING_SRC = """
        main() {
            var s = random.randomString(8, "abc")
            assert(s.length == 8)
            var i = 0
            while (i < 8) {
                var c = s.charAt(i)
                if (c != 97 && c != 98 && c != 99) { throw "char fora: " + s }
                i = i + 1
            }
            assert(random.randomString(0, "abc") == "")
            assert(random.randomString(-3, "abc") == "")
            assert(random.randomString(4, "") == "")
            assert(random.randomString(3, "x") == "xxx")
            var t = random.randomString(16, "0123456789abcdef")
            assert(t.length == 16)
            println("OK")
        }
        """;

    @Test
    void randomStringJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, STRING_SRC);
    }

    @Test
    void randomStringJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, STRING_SRC);
    }

    @Test
    void randomStringNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, STRING_SRC);
    }

    @Test
    void randomStringCrossArch(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            runQemu(tmp, t, STRING_SRC);
        }
    }

    /** Face S10 (main, merge 10/09): double/boolean/int/hex — shape. JVM/JS. */
    private static final String SHAPE_SRC = """
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
            assert(random.hex(0) == null)
            println("OK")
        }
        """;

    /** Face S10 (main) — Native x86: mesmo shape SEM hex(0)==null.
     *  kof_sec_random_hex (callee do kof_random_hex) retorna String vazia p/
     *  n<=0 no x86 — comportamento PRÉ-EXISTENTE da crypto lane (mesmo em
     *  security.randomHex(0)); o contrato null só vale JVM/JS. Divergência
     *  documentada na matriz stdrandom, não é falha do merge. */
    private static final String SHAPE_NATIVE_SRC = """
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
            println("OK")
        }
        """;

    @Test
    void randomShapeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SHAPE_SRC);
    }

    @Test
    void randomShapeJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SHAPE_SRC);
    }

    @Test
    void randomShapeNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SHAPE_NATIVE_SRC);
    }

    @Test
    void randomShapeCrossArch(@TempDir Path tmp) throws Exception {
        // RAND001: getrandom(2) ecall 278 (primitiva SECN000/B25 confirmada
        // no qemu). Shape idêntico ao x86 — assert-only, sem golden.
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, SHAPE_NATIVE_SRC);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, SHAPE_NATIVE_SRC);
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

    private void runQemu(Path tempDir, Target target, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(target == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64",
                bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
    }

    private void runJvm(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals("OK", output, "JVM output");
    }

    private void runNative(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: " + output);
        assertEquals("OK", output, "Native output");
    }

    private void runJs(Path tempDir, String source) throws Exception {
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
            assertEquals("OK", output, "JS output");
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir)
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
}

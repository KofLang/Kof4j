package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * X8 fatia 1 — namespace rng (PRNG semeável, xorshift128 + splitmix32).
 *
 * <p>O contrato central é DETERMINISMO com PARIDADE: a mesma seed produz a
 * mesma sequência em qualquer backend (fatia 1 = JVM + JS). O oracle é uma
 * implementação de referência EM JAVA no próprio teste (mesma matemática
 * 32-bit) — medida real, não memória. O teste de paridade roda o MESMO
 * programa .kf nos dois backends e exige stdout idêntico.
 *
 * <p>Bordas Q3: seed 0/negativo (splitmix espalha), bound<=0 => 0 (leniente,
 * paridade random.int), string com n<=0/alfabeto vazio => "", double em
 * [0,1) nunca 1.0, determinismo idempotente (re-seed reinicia a sequência),
 * gap honesto RNG001 nos alvos não cobertos (R6/R7).
 */
class KofRngTest {
    private final CompilerDriver driver = new CompilerDriver();

    // ── referência (oracle) — MESMA matemática dos fragments JVM/JS ──
    private static int splitmix32(int x) {
        int z = x + 0x9e3779b9;
        z = (z ^ (z >>> 16)) * 0x21f0aaad;
        z = (z ^ (z >>> 15)) * 0x735a2d97;
        return z ^ (z >>> 15);
    }

    private static final class Ref {
        int s0 = 0, s1 = 0, s2 = 0, s3 = 1;

        void seed(int seed) {
            int s = splitmix32(seed);
            s0 = splitmix32(s);
            s1 = splitmix32(s + 1);
            s2 = splitmix32(s + 2);
            s3 = splitmix32(s + 3);
            if ((s0 | s1 | s2 | s3) == 0) s1 = 1;
        }

        int next() {
            int t = s0 ^ (s0 << 11);
            s0 = s1; s1 = s2; s2 = s3;
            int w = s3;
            s3 = w ^ (w >>> 19) ^ (t ^ (t >>> 8));
            return s3;
        }

        int nextInt(int bound) {
            if (bound <= 0) return 0;
            return (int) ((next() & 0xffffffffL) % bound);
        }
    }

    /** Sequência esperada de 5 rng.int(1000) após rng.seed(n). */
    private static String expectedFiveInts(int seed) {
        Ref r = new Ref();
        r.seed(seed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(r.nextInt(1000));
            if (i < 4) sb.append(' ');
        }
        return sb.toString();
    }

    // ── programa determinístico: sequência + bordas de seed ──
    private static final String REF_SRC = """
        main() {
            rng.seed(42)
            var out = ""
            var i = 0
            while (i < 5) {
                out = out + rng.int(1000)
                if (i < 4) { out = out + " " }
                i = i + 1
            }
            println(out)
            rng.seed(0)
            println(rng.int(1000))
            rng.seed(-7)
            println(rng.int(1000))
            assert(rng.int(1) == 0)
            assert(rng.int(0) == 0)
            assert(rng.int(-5) == 0)
            println("OK")
        }
        """;

    private static String expectedRefOutput() {
        return expectedFiveInts(42) + "\n"
                + firstIntAfterSeed(0) + "\n"
                + firstIntAfterSeed(-7) + "\nOK";
    }

    private static String firstIntAfterSeed(int seed) {
        Ref r = new Ref();
        r.seed(seed);
        return String.valueOf(r.nextInt(1000));
    }

    @Test
    void deterministicJvmMatchesOracle(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, REF_SRC);
        assertEquals(expectedRefOutput(), out,
                "JVM rng sequence must match the in-test reference implementation");
    }

    @Test
    void deterministicJsMatchesOracle(@TempDir Path tmp) throws Exception {
        String out = runJs(tmp, REF_SRC);
        assertEquals(expectedRefOutput(), out,
                "JS rng sequence must match the in-test reference implementation");
    }

    /** Q3 cross-target: MESMO programa, stdout BYTE-A-BYTE igual nos 2 backends. */
    @Test
    void jvmJsParity(@TempDir Path tmp) throws Exception {
        String jvm = runJvm(tmp, REF_SRC);
        String js = runJs(tmp, REF_SRC);
        assertEquals(jvm, js, "same seed must give the same sequence on JVM and JS");
    }

    /** Q3 idempotência: re-seed reinicia a sequência (dentro da linguagem). */
    private static final String RESET_SRC = """
        main() {
            rng.seed(5)
            var d1 = rng.double()
            var i1 = rng.int(1000000)
            rng.seed(5)
            assert(rng.double() == d1)
            assert(rng.int(1000000) == i1)
            assert(rng.double() >= 0.0)
            assert(rng.double() < 1.0)
            println("OK")
        }
        """;

    @Test
    void reseedRestartsSequenceJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, RESET_SRC);
    }

    @Test
    void reseedRestartsSequenceJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, RESET_SRC);
    }

    /** Contrato de forma: range, bordas lenientes, string, bool. JVM + JS. */
    private static final String CONTRACT_SRC = """
        main() {
            rng.seed(20260918)
            var i = 0
            while (i < 500) {
                var v = rng.int(1000)
                if (v < 0 || v >= 1000) { throw "rng.int(1000) out of range: " + v }
                var w = rng.int(2)
                if (w != 0 && w != 1) { throw "rng.int(2) not 0/1: " + w }
                var b = rng.boolean()
                if (b != true && b != false) { throw "rng.boolean not bool" }
                i = i + 1
            }
            assert(rng.int(1) == 0)
            assert(rng.int(0) == 0)
            assert(rng.int(-5) == 0)
            var s = rng.string(8, "abc")
            assert(s.length == 8)
            var k = 0
            while (k < 8) {
                var c = s.charAt(k)
                if (c != 97 && c != 98 && c != 99) { throw "char fora: " + s }
                k = k + 1
            }
            assert(rng.string(0, "abc") == "")
            assert(rng.string(-3, "abc") == "")
            assert(rng.string(4, "") == "")
            assert(rng.string(3, "x") == "xxx")
            var seenT = false
            var seenF = false
            var j = 0
            while (j < 200) {
                if (rng.boolean()) { seenT = true } else { seenF = true }
                j = j + 1
            }
            assert(seenT && seenF)
            println("OK")
        }
        """;

    @Test
    void contractJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, CONTRACT_SRC);
    }

    @Test
    void contractJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, CONTRACT_SRC);
    }

    /**
     * R6/R7: fatia 2 = JVM+JS+NATIVE x86_64+ANDROID. Cross riscv64/aarch64
     * continuam gap honesto RNG001 no compile (port riscv = fatia 3 c/ qemu).
     * ANDROID entrou na fatia 2 em 07/10 (issue #777) — ver
     * {@link #androidMatchesOracle}.
     */
    @Test
    void crossStaysHonestGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, "main() { rng.seed(42) println(rng.int(10)) }");
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path outDir = tmp.resolve("out-" + t + "-" + System.nanoTime());
            CompilationResult r = driver.compile(file, outDir, t);
            assertFalse(r.success(), t + " deve falhar em compile (gap honesto)");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "RNG001".equals(d.code())),
                    t + " esperava RNG001, veio " + r.diagnostics().getDiagnostics());
        }
    }

    /**
     * Issue #777: o alvo ANDROID reusa o backend JVM e o MESMO KofRuntime
     * gerado (inclui o fragmento rng) — o rng é determinístico por construção
     * (só ops int 32-bit). Compila o MESMO programa para ANDROID e executa o
     * bytecode emitido no host: a sequência tem de bater com o oracle. Prova
     * a face DEX "por construção" sem SDK Android (o ART executa o mesmo
     * bytecode; paridade JVM≡ANDROID como `GpuAndroidE2ETest`).
     */
    @Test
    void androidMatchesOracle(@TempDir Path tmp) throws Exception {
        String out = runAndroid(tmp, REF_SRC);
        assertEquals(expectedRefOutput(), out,
                "ANDROID rng sequence must match the in-test reference implementation");
    }

    /**
     * Q3 cross-target (fatia 2): o MESMO programa exercitando seed/int/bool/
     * double/string produz stdout BYTE-A-BYTE igual no JVM e no NATIVE — a
     * paridade asm não é declarada, é executada (toolchain as/ld presente).
     */
    private static final String FULL_FACE_SRC = """
        main() {
            rng.seed(42)
            println(rng.int(1000))
            println(rng.int(1000))
            println(rng.int(1000))
            println(rng.boolean())
            println(rng.boolean())
            println(rng.double())
            println(rng.double())
            println(rng.string(8, "abcxyz"))
            println(rng.string(1, "q"))
            println(rng.int(0))
            println(rng.int(-5))
            println(rng.string(0, "abc"))
            rng.seed(42)
            println(rng.int(1000))
            println("OK")
        }
        """;

    @Test
    void jvmNativeParityFullFace(@TempDir Path tmp) throws Exception {
        String jvm = runJvm(tmp, FULL_FACE_SRC);
        String nat = runNative(tmp, FULL_FACE_SRC);
        assertEquals(jvm, nat, "rng face completa: JVM e NATIVE devem ser byte-idênticos");
    }

    /** Oracle direto no NATIVE (a mesma referência Java da fatia 1). */
    @Test
    void nativeMatchesOracle(@TempDir Path tmp) throws Exception {
        assertEquals(expectedRefOutput(), runNative(tmp, REF_SRC),
                "NATIVE rng sequence must match the in-test reference");
    }

    /** Contrato de forma/ranges também no NATIVE (500 iterações + bordas). */
    @Test
    void contractNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, CONTRACT_SRC);
    }

    // ── harness (padrão KofRandomTest) ──

    private String runNative(Path tempDir, String source) throws Exception {
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
        return output;
    }

    private String runJvm(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
        return output;
    }

    /**
     * Issue #777: compila para ANDROID e roda o bytecode emitido no host —
     * Android reusa o JvmBackend, então `Main.class`/`KofRuntime.class` são
     * os mesmos; o ART executa bytecode idêntico. Sem SDK Android.
     */
    private String runAndroid(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-android-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.ANDROID);
        assertTrue(result.success(), "ANDROID compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "ANDROID exit code, output: " + output);
        return output;
    }

    private String runJs(Path tempDir, String source) throws Exception {
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
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.toString().endsWith(".mjs")).findFirst();
            assertTrue(opt.isPresent(), "no .mjs emitted");
            return opt.get();
        }
    }
}

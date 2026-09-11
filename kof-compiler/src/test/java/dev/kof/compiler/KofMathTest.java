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

    // S1b: sqrt = PRIMEIRO Double em kof.math. Comparações Bool (nunca
    // println de double cru — bug 44 no Native). NaN em <0 = paridade
    // Math.sqrt (IEEE, medido nos 4 targets: NaN==NaN é false).
    private static final String SQRT_SRC = """
        main() {
            println(math.sqrt(9.0) == 3.0)
            println(math.sqrt(2.0) == 1.4142135623730951)
            println(math.sqrt(0.25) == 0.5)
            println(math.sqrt(1e30) == 1000000000000000.0)
            println(math.sqrt(1e300) == 1e150)
            println(math.sqrt(0.0) == 0.0)
            println(math.sqrt(-1.0) == -1.0)
            println(math.sqrt(-1.0) != math.sqrt(-1.0))
        }
        """;

    private static final String SQRT_OUT =
            "true\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\ntrue";

    @Test
    void sqrtJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, SQRT_SRC, SQRT_OUT);
    }

    @Test
    void sqrtGatedOnCrossArch(@TempDir Path tmp) throws Exception {
        assertGated(tmp, SQRT_SRC, "sqrt");
    }

    // S1b.1: escalares Double puros (lerp/percentage/isInteger/isDecimal) —
    // paridade byte-idêntica JVM/Script/JS/x86 (harness SSE2 isolado 18/18
    // antes desta classe). Golden travado no oracle JVM medido (P.java/O.java
    // da sessão), nunca de memória: 2.675-style não entra (roundTo fica no
    // degrau seguinte). NaN via percentage(0,0) — IEEE, != != em todos.
    private static final String DBL_SRC = """
        main() {
            println(math.lerp(0.0, 10.0, 0.5) == 5.0)
            println(math.lerp(0.0, 10.0, 0.25) == 2.5)
            println(math.lerp(-4.0, 4.0, 0.75) == 2.0)
            println(math.lerp(2.0, 8.0, 1.5) == 11.0)
            println(math.percentage(3.0, 4.0) == 75.0)
            println(math.percentage(1.0, 3.0) == 33.33333333333333)
            println(math.percentage(-2.0, 8.0) == -25.0)
            println(math.percentage(0.0, 5.0) == 0.0)
            println(math.percentage(0.0, 0.0) != math.percentage(0.0, 0.0))
            println(math.isInteger(4.0))
            println(math.isInteger(4.5) == false)
            println(math.isInteger(-3.0))
            println(math.isInteger(0.0))
            println(math.isInteger(1e20))
            println(math.isDecimal(4.5))
            println(math.isDecimal(4.0) == false)
            println(math.isInteger(1.0 / 0.0) == false)
            println(math.isDecimal(1.0 / 0.0))
        }
        """;

    private static final String DBL_OUT = String.join("\n",
            "true", "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true", "true", "true", "true", "true");

    @Test
    void doubleOpsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, DBL_SRC, DBL_OUT);
    }

    @Test
    void doubleOpsGatedOnCrossArch(@TempDir Path tmp) throws Exception {
        assertGated(tmp, DBL_SRC, "lerp");
    }

    private void assertGated(@TempDir Path tmp, String src, String label) throws Exception {
        // MATH001 FECHADO 11/09 (S1b/S1b.1 cross): os Double ops compilam nos
        // cross — a PARIDADE byte-idêntica riscv64/aarch64 é provada executando
        // sob qemu nos E2E cross (NativeRiscv64/Aarch64E2ETest); aqui só
        // garantimos que o gate R6 antigo não rejeita mais (regressão de wiring).
        Path gateSrc = tmp.resolve("Gate-" + label + "-" + System.nanoTime() + ".kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(
                    gateSrc, tmp.resolve("gate-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " deve compilar " + label + " (MATH001 fechado): "
                    + r.diagnostics().getDiagnostics());
        }
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

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MATH-POW-1 (01/11, round 84): as faces POW do WASI — math.pow fdlibm
 * (o port de {@code FdLibm.Pow} do JDK: hugey + main + tail + scalb +
 * hi/lo/withhi/lo0). O oráculo de igualdade é o JVM ({@code Math.pow});
 * as faces onde Math ≠ Strict (MEDIDAS: 1024^0.25, 2^0.75, 0.5^0.25,
 * 0.9999999999^1e10) ficam no teste de bits pinados — o módulo imprime
 * os bits fdlibm (Strict) EXATOS, o JVM imprime os de Math.
 *
 * Defeitos pagos no slice (a classe inverted-sub, o sub emite BAIXO−TOPO
 * e o minuend tem que entrar primeiro): a máscara do withhi, o n-flip da
 * cauda (0−n, era n−0 = no-op → toda face n<0 voltava 2^|n|), o shift do
 * scalb (1−t, era t−1 → o shift negativo contava mod 64 → 2^−1034 no
 * lugar de 2^−1074) e o w do hugey (1/3−t/4 e 0.5−P invertidos → w
 * negado → a soma da série errava 2w·INV_LN2). Lição paga: um return de
 * debug deixa o resto do corpo inalcançável e o validador TOLERA
 * desequilíbrio de pilha no código morto — nunca confiar no "valida"
 * com o return de debug presente (o extra local.get t do w passou
 * assim).
 */
class WasmWasiPowE2ETest {

    @Test
    void powSpecialsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Os especiais do fdlibm: ±0 (expoente par/ímpar/positivo/
        // negativo), ±1 com y enorme, base negativa com expoente
        // inteiro par/ímpar — MEDIDOS Math==Strict (Faces.java, 01/11).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(0.0, 0.0))
                    println(math.pow(0.0, 2.0))
                    println(math.pow(0.0, -2.0))
                    println(math.pow(-0.0, 2.0))
                    println(math.pow(-0.0, 3.0))
                    println(math.pow(-0.0, -3.0))
                    println(math.pow(1.0, 10000000000.0))
                    println(math.pow(1.0, -10000000000.0))
                    println(math.pow(-2.0, 3.0))
                    println(math.pow(-2.0, 2.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void powOverflowUnderflowWindowsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // As janelas do over/underflow: o dobro, o gatilho huge-y
        // (|y| > ~2^31 com x longe de 1 → os gates do over/underflow) e o
        // limite do expoente de 32 bits — faces do probe2, TODAS
        // Math==Strict (01/11, 89 faces medidas).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(1e300, 2.0))
                    println(math.pow(1e-300, 2.0))
                    println(math.pow(0.001, -200.0))
                    println(math.pow(0.02, -300.0))
                    println(math.pow(2.0, 2147483647.0))
                    println(math.pow(2.0, -2147483648.0))
                    println(math.pow(2.0, 3000000000.0))
                    println(math.pow(2.0, -3000000000.0))
                    println(math.pow(1.5, 3000000000.0))
                    println(math.pow(0.5, 3000000000.0))
                    println(math.pow(1e-320, 0.5))
                    println(math.pow(1e300, 0.5))
                    println(math.pow(1.7976931348623157e308, 0.5))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void powNegativeBaseMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Base negativa: inteiro ímpar/par, expoente não inteiro (NaN) e
        // as faces grandes do probe2 (o y_is_int do fdlibm) — Math==Strict
        // (Faces.java, 01/11).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(-8.0, 0.3333333333333333))
                    println(math.pow(-1e300, 3.0))
                    println(math.pow(-0.9999999, 77.0))
                    println(math.pow(-1.5, 65.0))
                    println(math.pow(-0.9, 81.0))
                    println(math.pow(-0.9, 2.0))
                    println(math.pow(-1.5, 2.0))
                    println(math.pow(-0.9999999, 2.0))
                    println(math.pow(-8.0, 2.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void powFractionalMainMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // O caminho principal fracionário (a redução BP/DP + a série L +
        // o split do CP): raízes exatas, potências inteiras e as faces
        // do probe2 perto do 1 — Math==Strict (01/11).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(2.0, 0.5))
                    println(math.pow(9.0, 0.5))
                    println(math.pow(27.0, 0.3333333333333333))
                    println(math.pow(2.0, 10.0))
                    println(math.pow(0.1, 3.0))
                    println(math.pow(5.0, -2.0))
                    println(math.pow(1e-300, 0.5))
                    println(math.pow(10.0, 0.5))
                    println(math.pow(10.0, -0.5))
                    println(math.pow(1024.0, 0.125))
                    println(math.pow(1.25, 3.5))
                    println(math.pow(123.456, 7.89))
                    println(math.pow(0.0001, 0.5))
                    println(math.pow(0.9999999999999999, 1024.0))
                    println(math.pow(1.0000000000000002, 2.0))
                    println(math.pow(0.9999999999999998, 3.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void powHugeYNearOneMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // O hugey perto de 1 (|y| > ~2^31 com |1-x| ≤ 2^-20): a série do
        // log (t/w/u/v/t1/t2 → a cauda). A face 1.0000000001^1e10 == Math
        // EXATO (o w negado era o defeito — medido após o fix) e os
        // espelhos y<0 (1e-10 → 0.3678794107513561; 0.9999999999^-1e10 →
        // 2.7182820535066154) — Math==Strict (Faces.java, 01/11).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(1.0000000001, 10000000000.0))
                    println(math.pow(1.0000000001, -10000000000.0))
                    println(math.pow(0.9999999999, -10000000000.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void powStrictDifferentialPinnedBits(@TempDir Path dir) throws Exception {
        // As 4 faces MEDIDAS Math≠Strict (Oracle2/Faces, 01/11): o módulo
        // imprime os bits fdlibm (Strict) EXATOS e o JVM imprime os de
        // Math — os dois lados pinados, nenhum dos dois é "o esperado"
        // genérico. 1024^0.25: M=5.656854249492381 S=5.65685424949238;
        // 2^0.75: M=1.681792830507429 S=1.6817928305074292;
        // 0.5^0.25: M=0.8408964152537145 S=0.8408964152537146;
        // 0.9999999999^1e10: M=0.36787941071456814 S=0.3678794107145682
        // (o n=[z+0.5] do fdlibm = −1 cai do OUTRO lado do meio-ulp que o
        // pow interno do Math — provado pela réplica Full.java).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.pow(1024.0, 0.25))
                    println(math.pow(2.0, 0.75))
                    println(math.pow(0.5, 0.25))
                    println(math.pow(0.9999999999, 10000000000.0))
                }
                """);
        String jvmExpected = """
                5.656854249492381
                1.681792830507429
                0.8408964152537145
                0.36787941071456814
                """;
        String wasiExpected = """
                5.65685424949238
                1.6817928305074292
                0.8408964152537146
                0.3678794107145682
                """;
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(WasmWasiFlowE2ETest.jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle: " + expected);
        assertEquals(jvmExpected.replaceAll("\n+$", ""), expected.replaceAll("\n+$", ""),
                "the JVM oracle must print the Math bits");
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "module must pass the official validator: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit: " + out);
        assertEquals(wasiExpected.replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "the frontend must print the exact fdlibm (Strict) bits");
    }
}

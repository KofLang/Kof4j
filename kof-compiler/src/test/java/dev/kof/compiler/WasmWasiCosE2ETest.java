package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COS-1 (15/11 — lane issues/bugs/gaps): as faces do math.cos WASI — o
 * FdLibm.Cos verbatim (o driver WasmCosDriver + os gêmeos médio/grande
 * WasmRemPio2.mediumCosFunc/largeCosFunc sobre o cos.kernel de MATH-LIBM-1).
 * A LEI MATH-POW-1: o port é fdlibm-EXATO; o oráculo Math.faceia o
 * subconjunto MEDIDO Math==Strict (43 faces, CosProbe 15/11) e as 9 faces
 * de drift (0.1, 2.3, ±1000, ±1193046, 2000000, ±1e15) entram no
 * DIFERENCIAL Strict pinado bit a bit (o padrão do WasmWasiPowE2ETest).
 * Extraída do WasmWasiTrigE2ETest (o ratchet do check_test_hygiene — a
 * responsabilidade única do cos).
 */
class WasmWasiCosE2ETest {

    @Test
    void mathCosFullRangeMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // As 43 faces MEDIDAS Math==Strict (CosProbe, 15/11): a faixa
        // pequena (≤ π/4 — o kernel direto), as bordas 0.3/0.78125 (o qx
        // bit-hack), os médios (o Cody-Waite + o octante do cos), os
        // grandes (o scratch 928B — quads 0/1/2/3 TODOS exercitados:
        // 823549/−823549/823548.999/823550/1e6/1.1e6/1.3e6/7e6/87654321/
        // 1e9/5e9/1e10/1e300) + os especiais (±0.0/π/±π/±π/2/Inf/NaN).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.cos(0.001))
                    println(math.cos(0.5))
                    println(math.cos(-0.5))
                    println(math.cos(0.7))
                    println(math.cos(-0.7))
                    println(math.cos(0.7853981633974483))
                    println(math.cos(-0.7853981633974483))
                    println(math.cos(0.3))
                    println(math.cos(0.78125))
                    println(math.cos(0.7812500000000001))
                    println(math.cos(0.0))
                    println(math.cos(-0.0))
                    println(math.cos(1.0))
                    println(math.cos(-1.0))
                    println(math.cos(1.5))
                    println(math.cos(2.0))
                    println(math.cos(4.0))
                    println(math.cos(6.5))
                    println(math.cos(10.0))
                    println(math.cos(100.0))
                    println(math.cos(1.5707963267948966))
                    println(math.cos(-1.5707963267948966))
                    println(math.cos(3.141592653589793))
                    println(math.cos(-3.141592653589793))
                    println(math.cos(123456.789))
                    println(math.cos(500000.0))
                    println(math.cos(823549.0))
                    println(math.cos(-823549.0))
                    println(math.cos(823548.999))
                    println(math.cos(823550.0))
                    println(math.cos(1000000.0))
                    println(math.cos(1100000.0))
                    println(math.cos(1300000.0))
                    println(math.cos(7000000.0))
                    println(math.cos(87654321.0))
                    println(math.cos(1000000000.0))
                    println(math.cos(5000000000.0))
                    println(math.cos(10000000000.0))
                    println(math.cos(1e300))
                    println(math.cos(1193049.0))
                    println(math.cos(-1193049.0))
                    println(math.cos(1.0 / 0.0))
                    println(math.cos(0.0 / 0.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void cosStrictDifferentialPinnedBits(@TempDir Path dir) throws Exception {
        // As faces MEDIDAS Math≠Strict (CosProbe, 15/11 — 9 por 1 ulp): o
        // módulo imprime os bits fdlibm (Strict) EXATOS e o JVM imprime os
        // de Math — os DOIS lados pinados, nenhum esperado genérico (o
        // padrão do pow). ±1193046 e 2000000 exercitam os quads 1/0 do
        // caminho grande — o cos lógica do quad 1 (−sin iy=1) e do quad 0.
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.cos(0.1))
                    println(math.cos(2.3))
                    println(math.cos(1000.0))
                    println(math.cos(-1000.0))
                    println(math.cos(1193046.0))
                    println(math.cos(-1193046.0))
                    println(math.cos(2000000.0))
                    println(math.cos(1e15))
                    println(math.cos(-1e15))
                }
                """);
        String jvmExpected = """
                0.9950041652780258
                -0.6662760212798241
                0.5623790762907029
                0.5623790762907029
                0.491436391112278
                0.491436391112278
                0.7550090968757464
                -0.5131937377869703
                -0.5131937377869703
                """;
        String wasiExpected = """
                0.9950041652780257
                -0.666276021279824
                0.562379076290703
                0.562379076290703
                0.49143639111227805
                0.49143639111227805
                0.7550090968757462
                -0.5131937377869702
                -0.5131937377869702
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

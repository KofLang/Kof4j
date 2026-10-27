package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fase E (27/10, #776 — split test-hygiene): as faces TRIG do WASI
 * (MATH-LIBM), extraídas do {@code WasmWasiFlowE2ETest} (o ratchet do
 * check_test_hygiene). Responsabilidade única: math.sin fdlibm (o kernel
 * pequeno + o caso especial n=±1 + o kernel_cos).
 */
class WasmWasiTrigE2ETest {

    @Test
    void mathSinSmallRangeMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // MATH-LIBM-1 (27/10, #776): o __kernel_sin fdlibm na faixa PEQUENA
        // (|x| ≤ π/4) — a FUNDAÇÃO MEDIDA: Math.sin == StrictMath.sin em
        // 29/29 entradas (o port fixa o oráculo byte a byte) MAS Math.cos
        // deriva 5/29 por 1 ulp (0.1, ±1000, 1e15) — a face cos paridade
        // está MORTA (o cos segue recusado no WASI; o veredito registrado).
        // As constantes são as do FdLibm.Sin DO PRÓPRIO JDK (os literais
        // hex — a tabela decimal da memória tinha os expoentes TODOS
        // deslocados e o F5 mediu o erro). O guard trapa além de π/4 (a
        // redução MÉDIA é a fatia seguinte — o F6 mediu o trap rc=134).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.sin(0.001))
                    println(math.sin(0.5))
                    println(math.sin(-0.5))
                    println(math.sin(0.7))
                    println(math.sin(0.7853981633974483))
                    println(math.sin(-0.7853981633974483))
                    println(math.sin(0.0))
                    println(math.sin(1.0))
                    println(math.sin(1.5))
                    println(math.sin(2.0))
                    println(math.sin(2.3))
                    println(math.sin(-1.0))
                    println(math.sin(-2.0))
                    println(math.sin(1.5707963267948966))
                    println(math.sin(4.0))
                    println(math.sin(6.5))
                    println(math.sin(10.0))
                    println(math.sin(100.0))
                    println(math.sin(1000.0))
                    println(math.sin(123456.789))
                    println(math.sin(500000.0))
                    println(math.sin(-4.0))
                    println(math.sin(-1000.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

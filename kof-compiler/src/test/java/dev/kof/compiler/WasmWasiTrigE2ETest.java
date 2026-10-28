package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase E (27-28/10, #776 — split test-hygiene): as faces TRIG do WASI
 * (MATH-LIBM), extraídas do {@code WasmWasiFlowE2ETest} (o ratchet do
 * check_test_hygiene). Responsabilidade única: math.sin + math.tan fdlibm
 * (o kernel pequeno + o caso especial n=±1 + o kernel_cos + o kernel_tan).
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
                    println(math.sin(823549.0))
                    println(math.sin(-823549.0))
                    println(math.sin(823548.999))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void sinBeyondMediumRangeTrapsHonestly(@TempDir Path dir) throws Exception {
        // MATH-LIBM-4 scoping (27/10, #776): além de 2^19·π/2 ≈ 823549 o
        // guard TRAPA honestamente (o caminho GRANDE ipio2 = a fatia
        // seguinte — nunca valor errado silencioso; a F10 mediu rc=134).
        // A face pina o trap via o EXIT CODE do wasmtime (o corpo nem roda).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        var driver = new CompilerDriver();
        Files.writeString(dir.resolve("Main.kf"),
                "main(String[] args) {\n    println(math.sin(2000000.0))\n}\n");
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compile (o guard da faixa valida): " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(proc.exitValue() != 0,
                "sin além de 2^19·π/2 TRAPA honestamente (rc=" + proc.exitValue() + ")");
    }

    @Test
    void mathTanFullRangeMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // MATH-TAN-1 (28/10, #776): o __kernel_tan fdlibm (o JDK FdLibm.Tan
        // — os T[0..12] + π/4/π/4lo verbatim do src.zip) com a reflexão
        // (1−tan y)/(1+tan y) na forma iy=1 do fdlibm. Os DEFEITOS que a
        // rodada 28 pagou: o Horner da v-chain emitido inside-out (o T12
        // pendurado = "values remaining on stack"), o sub da reflexão com
        // os operandos INVERTIDOS (xw−π/4 = o sinal trocado — o tan é ímpar),
        // e o if do valor testando o xw REFLETIDO (o fdlibm testa o ix
        // ORIGINAL — o flag sobrevive à reflexão). A borda EXATA
        // 0x1.59428p-1 faceia o >= (o fdlibm reflete na borda). O trap
        // além de π/4 = a face seguinte (o caminho grande via rem_pio2).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.tan(0.001))
                    println(math.tan(0.5))
                    println(math.tan(-0.5))
                    println(math.tan(0.0))
                    println(math.tan(1e-8))
                    println(math.tan(0.1))
                    println(math.tan(-0.1))
                    println(math.tan(0.7))
                    println(math.tan(-0.7))
                    println(math.tan(0.6743))
                    println(math.tan(0.6743354797363281))
                    println(math.tan(0.6744))
                    println(math.tan(-0.6744))
                    println(math.tan(0.75))
                    println(math.tan(-0.75))
                    println(math.tan(0.7853981633974483))
                    println(math.tan(-0.7853981633974483))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void tanBeyondQuarterPiTrapsHonestly(@TempDir Path dir) throws Exception {
        // MATH-TAN-1 scoping (28/10, #776): além de π/4 o guard TRAPA
        // honestamente (a redução rem_pio2 grande = a fatia seguinte —
        // nunca valor errado silencioso; o T5 mediu rc=134, o unreachable
        // no func do tan). A face pina o trap via o EXIT CODE do wasmtime.
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        var driver = new CompilerDriver();
        Files.writeString(dir.resolve("Main.kf"),
                "main(String[] args) {\n    println(math.tan(1.0))\n}\n");
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compile (o guard da faixa valida): " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        proc.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(proc.exitValue() != 0,
                "tan além de π/4 TRAPA honestamente (rc=" + proc.exitValue() + ")");
    }
}

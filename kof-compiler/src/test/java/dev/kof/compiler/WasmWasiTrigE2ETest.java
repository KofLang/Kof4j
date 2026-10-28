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
 * check_test_hygiene). Responsabilidade única: math.sin + math.tan +
 * math.atan + math.asin fdlibm (o kernel pequeno + o caso especial n=±1 +
 * o kernel_cos + o kernel_tan + o kernel_atan + o kernel_asin).
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

    @Test
    void mathAtanFullRangeMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // MATH-ATAN-1 (28/10, #776): o __kernel_atan fdlibm (o FdLibm.Atan
        // do JDK — atanhi/atanlo/aT verbatim do src.zip) AUTOCONTIDO: a
        // redução = a árvore de 4 ramos (0.4375/0.6875/1.1875/2.4375 — as
        // bordas de hi-word de mantissa 0, o compare double ⟺ o hi-word)
        // e o domínio = TODO double (SEM trap: o pequeno devolve x cedo —
        // o ±0.0 bit-exato — e o enorme id=3 converge pro π/2). A
        // fundação MEDIDA: Math.atan == StrictMath.atan 34/34. Os defeitos
        // que a rodada pagou no bissepto: o eq do id era f64.eq (0x61) e
        // não i32.eq (0x46), o if do small consumia a cond SEM o get (o
        // set só grava o local), o THEN do pequeno empilhava x UMA vez
        // (a forma precisa x DUAS: minuendo E fator) e sobrou um END do
        // block externo do tan (o atan não tem block).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.atan(0.0))
                    println(math.atan(-0.0))
                    println(math.atan(1e-300))
                    println(math.atan(1e-8))
                    println(math.atan(0.001))
                    println(math.atan(0.1))
                    println(math.atan(-0.1))
                    println(math.atan(0.4373))
                    println(math.atan(0.4375))
                    println(math.atan(-0.4375))
                    println(math.atan(0.5))
                    println(math.atan(0.6874))
                    println(math.atan(0.6875))
                    println(math.atan(1.0))
                    println(math.atan(-1.0))
                    println(math.atan(1.1874))
                    println(math.atan(1.1875))
                    println(math.atan(1.5))
                    println(math.atan(2.0))
                    println(math.atan(-2.0))
                    println(math.atan(2.4374))
                    println(math.atan(2.4375))
                    println(math.atan(10.0))
                    println(math.atan(-10.0))
                    println(math.atan(1e15))
                    println(math.atan(1e300))
                    println(math.atan(-1e300))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void mathAsinFullRangeMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // MATH-ASIN-1 (28/10, #776): o __ieee754_asin fdlibm (o FdLibm.Asin
        // do JDK — pio2/pio4 + pS0..pS5/qS1..qS4 verbatim do src.zip). Os
        // MESMOS polinômios nos DOIS trilhos (o pequeno |x| < 0.5 com o x
        // assinado; o grande com o t = (1−|x|)/2 e o __LO(s,0) — o zero do
        // word baixo via reinterpret/AND/reinterpret i64). O wrapper
        // acos = π/2 − asin está MORTO (8/18 por 1 ulp, medido) — o acos
        // terá a própria fatia. Os defeitos que a rodada pagou: a decl
        // dos locals tinha UM f64 a mais (o neg caía num slot f64 —
        // "expected f64, found i32" no set); o threshold 0x3FEF3333 = o
        // double 0x1.F3333p-1 = 0.9749999046325684 (o MENOR double do
        // hi-word). O NaN = bit-idêntico (0/0 do fdlibm = o canônico).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.asin(0.0))
                    println(math.asin(-0.0))
                    println(math.asin(1e-300))
                    println(math.asin(1e-8))
                    println(math.asin(0.001))
                    println(math.asin(-0.001))
                    println(math.asin(0.1))
                    println(math.asin(0.25))
                    println(math.asin(-0.25))
                    println(math.asin(0.4999))
                    println(math.asin(0.5))
                    println(math.asin(-0.5))
                    println(math.asin(0.6))
                    println(math.asin(0.7))
                    println(math.asin(-0.7))
                    println(math.asin(0.9))
                    println(math.asin(0.9749))
                    println(math.asin(0.9749999046325684))
                    println(math.asin(0.975))
                    println(math.asin(-0.975))
                    println(math.asin(0.999))
                    println(math.asin(1.0))
                    println(math.asin(-1.0))
                    println(math.asin(0.0/0.0))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

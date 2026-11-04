package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 15.2-STRSIG (02/11, #776 — round 87): a assinatura de STRING para funções
 * top-level no frontend WASI. Antes, `String greet()` ou `shout("hey")`
 * recusava com WASM002 ("fora do subset escalar da unidade 15.2") — a
 * maquinaria do String (o handle i32 do bump heap + kof.strLit/kof.strConcat/
 * kof.writeStr) pousou nas fatias 15.3b/15.3c/15.3d e o gate da assinatura
 * nunca a acompanhou. A lei é a MESMA dos params/retornos de CLASSE
 * (CLASS-2b, 29/10): o valor anda no MESMO handle i32 e o corpo baixa com a
 * maquinaria que JÁ existe — o gate é o único lugar que recusa.
 *
 * <p>O contrato é o PRINCÍPIO dos outros E2E WASI: cada face compara o
 * stdout do wasmtime com o ORÁCULO JVM da MESMA fonte e valida o módulo no
 * wasm-tools oficial.</p>
 */
class WasmWasiStrSigE2ETest {

    @Test
    void stringReturnMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String greet() {
                    return "ok"
                }
                main(String[] args) {
                    println(greet())
                }
                """);
    }

    @Test
    void stringParamMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String shout(String s) {
                    return s + "!"
                }
                main(String[] args) {
                    println(shout("hey"))
                }
                """);
    }

    @Test
    void chainedStringHelpersMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String tag(String name) {
                    return "[" + name + "]"
                }
                String shout(String s) {
                    return s + "!"
                }
                main(String[] args) {
                    println(shout(tag("kof")))
                    var a = tag("a")
                    var b = tag("b")
                    println(a == b)
                    println(a != b)
                }
                """);
    }

    @Test
    void stringHelperCallsScalarHelperMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String pair(Int n) {
                    return "n=" + n
                }
                Int twice(Int x) {
                    return x * 2
                }
                main(String[] args) {
                    println(pair(twice(21)))
                }
                """);
    }

    @Test
    void stringReturnThroughConditionalsMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String pick(Bool hot) {
                    if (hot) {
                        return "hot"
                    }
                    return "cold"
                }
                main(String[] args) {
                    println(pick(true))
                    println(pick(false))
                }
                """);
    }

    @Test
    void stringParamFeedsStringOpsMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        assertStrSigFace(dir, """
                String norm(String s) {
                    if (s.isEmpty()) {
                        return "-"
                    }
                    return s.toUpperCase()
                }
                main(String[] args) {
                    println(norm("mIxEd"))
                    println(norm(""))
                }
                """);
    }

    static void assertStrSigFace(Path dir, String source) throws Exception {
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), source);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

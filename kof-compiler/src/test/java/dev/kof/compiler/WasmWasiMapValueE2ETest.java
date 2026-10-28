package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FATIA SNULL (28/10, #776): as faces dos VALORES NULLABLE do map.get no
 * WASI, extraídas do {@code WasmWasiListE2ETest} (o ratchet do
 * check_test_hygiene). Responsabilidade única: o concat do valor nullable
 * (o mapval → boxToStr; o null = `null`).
 */
class WasmWasiMapValueE2ETest {

    @Test
    void mapNullableValueConcatMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA SNULL (28/10, #776): o CONCAT do valor nullable do map.get —
        // a cadeia valueOf do desugar recebe o HANDLE DA CAIXA (o mapval) e
        // agora roteia pelo kof.boxToStr (o null renderiza `null`, o MESMO
        // do print medido; o re-arme do mapval = a cadeia tem dois passes).
        // O `println(m.get(...))` JÁ era byte-idêntico (a rota mapval do
        // print) — o que faltava era o valueOf do concat. O round MEDIU
        // primeiro: as larguras de VALOR do map (Map<int,string>,
        // Map<string,string>, Map<string,double>) = a fatia SEPARANTE
        // (o gate `fora da fatia Map<String, Int>` — a próxima fatia).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 1, "b", 2)
                    println(m.get("a"))
                    println(m.get("z"))
                    println("v=" + m.get("z"))
                    println("w=" + m.get("a"))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

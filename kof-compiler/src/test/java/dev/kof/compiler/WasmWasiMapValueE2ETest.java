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

    @Test
    void mapStringStringMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MV (28/10, #776): Map<String, String> — os valores String no
        // MESMO slot i64 (o handle ESTENDIDO no put; o get/remove devolvem o
        // CRU com o rastro nullablestring — o passthrough do boxToStr
        // renderiza o String cru e o `null` no 0). Os rotros: o print com o
        // if VOID (o write DENTRO dos bracos — a LEI do if void: os bracos
        // terminam vazios; o if RESULT-typed na cadeia valueOf), o concat
        // pelo valueOf nullablestring (o 0 = a literal `null`), o remove
        // VOID consumido pelo KofPop do statement (o hasReturnValue do
        // Compiler Comparisons — a camada SEM drop proprio). Os defeitos
        // pagos: os clones NAMED kof.mapPutStr (o dedupe por nome tinha o
        // putIfAbsent com o nome VELHO — o modulo perdia os Str), o rastro
        // do remove (o object = o unbox do Int) e o if do print VOID vs o
        // if do concat RESULT-typed.
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", "x", "b", "y")
                    println(m.get("a"))
                    println(m.get("z"))
                    println("v=" + m.get("z"))
                    println("w=" + m.get("a"))
                    println(m.size())
                    println(m.contains("b"))
                    println(m.contains("z"))
                    m.put("c", "z")
                    println(m.get("c"))
                    println(m.size())
                    var old = m.remove("c")
                    println(old)
                    println(m.size())
                    println("d=" + m.remove("z"))
                    println(m.get("a"))
                    println(m.get("z"))
                    m.put("a", "w")
                    println(m.get("a"))
                    println(m.size())
                    m.remove("c")
                    println(m.size())
                    println(m.contains("c"))
                    println(m.contains("a"))
                    var l = m.keys()
                    println(l.size())
                    var v = m.values()
                    println(v.size())
                    println("k=" + m.get("a"))
                    println(m.get("b") == "y")
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void mapStringDoubleMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MAP-D (28/10, #776): Map<String, Double> — os valores Double
        // ANDAM como a CAIXA §284 (o boxDouble dos bits; o handle i32, o 0 =
        // null). Os rotros pagos: o put leva o reinterpret NO CALL SITE (a
        // f64 da pilha → o i64 dos bits do mapPut); o get/remove devolvem a
        // CAIXA (o mapGetDbl/mapRemoveDbl — os clones NAMED corrigidos da lei
        // do dedupe byName); o rastro "nullabledouble" (o typeName +
        // printableScalar + mapType — o trio) + o RE-ARME na cadeia valueOf
        // do println (o Double.valueOf do desugar chega com o param
        // PRIMITIVE apagado e limpava o rastro — o MESMO padrão do re-arme
        // do mapval); o print/concat renderizam via boxToStr (o null =
        // `null`); a ARITMÉTICA (o b + a) desboxa ANTES do op f64 — o
        // f64.load 16 dos bits da caixa (a dança do scratch vf64; o null
        // trapa = a paridade do NPE do unbox do JVM; a forma MISTA caixa +
        // escalar cru recusa no validate — o rastro de op1 não sobrevive).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 0.5, "b", 1.5, "c", 2.5)
                    println(m.get("a"))
                    println(m.get("z"))
                    println("v=" + m.get("z"))
                    println("w=" + m.get("a"))
                    println(m.size())
                    println(m.contains("b"))
                    println(m.contains("z"))
                    m.put("a", 9.75)
                    println(m.get("a"))
                    println(m.size())
                    var old = m.remove("c")
                    println(old)
                    println(m.size())
                    println("d=" + m.remove("z"))
                    println(m.get("b") + m.get("a"))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

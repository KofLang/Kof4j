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
    void mapIntStringMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MAP-KI (28/10, #776): Map<Int, String> — a chave anda RAW no
        // slot i64 (o Int = o i64; a busca = o i64.eq, nunca o strEq) e o
        // valor String estende no MESMO slot do putStr (o mapPutKi/
        // mapGetKi/mapRemoveKi/mapContainsKi — os clones nomeados, a lei do
        // dedupe byName). Os rotros: o get/remove devolvem o valor CRU com o
        // rastro nullablestring (o passthrough do boxToStr renderiza o
        // String e o `null` no 0); a EXTRAÇÃO keys()/values() sobre a chave
        // Int recusa HONESTA (o slot raw não é o handle — a fatia própria).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf(1, "x", 2, "y", 3, "w")
                    println(m.get(1))
                    println(m.get(9))
                    println("v=" + m.get(9))
                    println("k=" + m.get(1))
                    println(m.size())
                    println(m.contains(2))
                    println(m.contains(9))
                    m.put(3, "z")
                    println(m.get(3))
                    println(m.size())
                    var old = m.remove(2)
                    println(old)
                    println(m.size())
                    m.remove(77)
                    println(m.size())
                    println("d=" + m.remove(9))
                    println(m.contains(2))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void mapIntStringKeysValuesMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MAP-KX (28/10, #776): a EXTRAÇÃO keys()/values() sobre a
        // chave Int — o extract é AGNÓSTICO da chave (os slots 16B copiam
        // RAW: as chaves Int = os i64 cru, a MESMA fatia da lista Int; as
        // chaves String = os handles estendidos; o values() = os handles do
        // valor Str). As faces são LIVRES DE ORDEM (o HashMap não expõe a
        // ordem): o size/contains da lista + o get de par ÚNICO.
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf(1, "x", 2, "y")
                    var k = m.keys()
                    println(k.size())
                    println(k.contains(1))
                    println(k.contains(2))
                    println(k.contains(9))
                    var s = mapOf(7, "only")
                    var v = s.values()
                    println(v.size())
                    println(v.get(0))
                    var w = mapOf(3, "solo")
                    var kw = w.keys()
                    println(kw.get(0))
                    println(v.contains("x"))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void mapDoubleMixedArithmeticMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MAP-MX (28/10, #776): a ARITMÉTICA MISTAS das caixas — as
        // TRÊS danças pelas formas dos operandos (o rastro do topo diz op2; a
        // simulação FORWARD WasmOperandShape diz op1): caixa+caixa (o get+
        // get — a lei MAP-D: o load+scratch+load), escalar+caixa (o load 16
        // do op2 basta) e caixa+escalar (o escalar no scratch vf64, o load do
        // op1, o scratch de volta). O null trapa no load = a paridade do NPE
        // do unbox do JVM; a forma NÃO-coberta recusa no validate (o erro da
        // simulação = módulo inválido, nunca saída errada silenciosa).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("a", 0.5, "b", 1.5)
                    println(m.get("a") + 1.0)
                    println(2.0 + m.get("a"))
                    println(m.get("a") + m.get("b"))
                    var d = 2.25
                    println(m.get("b") + d)
                    println(d + m.get("a"))
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }

    @Test
    void mapStringBoolMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA MAP-B (28/10, #776): Map<String, Bool> — os valores Bool
        // ANDAM como a CAIXA §284 (o boxBool do qword zero-estendido; o
        // handle i32, o 0 = null — o 0 = false NUNCA pode ser a sentinela).
        // Os rotros: o put leva o extend_i32_u NO CALL SITE (o i32 do bool →
        // o qword do slot; o mapPut CRU); o get/remove devolvem a CAIXA
        // (kof.mapGetBool/kof.mapRemoveBool — os clones nomeados com o WRAP
        // antes do boxBool: o qword do slot volta a i32); o rastro
        // "nullablebool" (o trio + o mapa de rastro de LOCAL) + o RE-ARME no
        // Boolean.valueOf do desugar do println (o printlnFollows vence o
        // ramo Boolean do valueOf — o else apagava o rastro pelo param
        // PRIMITIVO e o writeBool despachava sobre o handle da caixa, medido)
        // e no valueOf do concat (o boxToStr renderiza pela tag 3 = strBool;
        // o null = `null`).
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var m = mapOf("t", true, "f", false)
                    println(m.get("t"))
                    println(m.get("f"))
                    println(m.get("z"))
                    println("v=" + m.get("z"))
                    println("w=" + m.get("t"))
                    println(m.size())
                    println(m.contains("f"))
                    m.put("a", false)
                    println(m.get("a"))
                    var f = m.get("a")
                    println(f)
                    println("s=" + f)
                    var old = m.remove("f")
                    println(old)
                    println(m.size())
                    println("d=" + m.remove("z"))
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

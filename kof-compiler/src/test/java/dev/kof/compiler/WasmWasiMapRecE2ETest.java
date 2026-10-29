package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MAP-REC (29/10, #776): as faces MAP de RECORD do WASI — os VALORES de
 * record (a disciplina CRU do handle — o mapPutStr/mapGetStr, ZERO runtime
 * novo, o local nullable-RECORD) e as CHAVES de record (a busca por
 * CONTEÚDO = o fold `kof.recEq.<Rec>` compartilhado — o mapPutRec/
 * mapGetRec/mapRemoveRec/mapContainsRec). Classe própria: a irmã
 * WasmWasiMapValueE2ETest está a 497 linhas (o ratchet de higiene ≤500
 * forcaria o split); os helpers sao os da classe irma, CHAMADA
 * QUALIFICADA.
 */
class WasmWasiMapRecE2ETest {

    @Test
    void mapRecordValuesAndKeysMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // MAP-REC (29/10, #776): as faces — (1) os VALORES de record
        // (Map<String, Point>: o put/get na disciplina CRU do handle, o
        // local `Point?`, o containsKey de chave String); (2) as CHAVES de
        // record (Map<Point, String>: o containsKey/get por CONTEÚDO — o
        // fold); (3) o Map<Rec, Rec> COMBINADO com o remove (o hit devolve
        // o valor, o miss devolve null); (4) o fold de campo
        // String/Double na chave. O NaN/±0.0 do campo Double fica FORA (a
        // divergencia pre-existente do backend JVM com o fold de record
        // Double — o == cru — known-bugs §764; o WASI anda nos bits = o
        // javac).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                record Book(String title, Double price)
                main(String[] args) {
                    var m = mapOf("a", Point(1, 2), "b", Point(3, 4))
                    println(m.size())
                    var v: Point? = m.get("a")
                    println(v != null)
                    var w: Point? = m.get("b")
                    println(w != null)
                    println(m.containsKey("z"))
                    var k = mapOf(Point(1, 2), "a", Point(3, 4), "b")
                    println(k.size())
                    var s: String? = k.get(Point(1, 2))
                    println(s != null)
                    println(k.containsKey(Point(9, 9)))
                    var p = mapOf(Point(1, 2), Point(3, 4), Point(9, 9), Point(1, 2))
                    println(p.size())
                    var q: Point? = p.get(Point(9, 9))
                    println(q != null)
                    println(p.containsKey(Point(3, 4)))
                    var r: Point? = p.remove(Point(3, 4))
                    println(r != null)
                    println(p.size())
                    var n: Point? = p.remove(Point(7, 7))
                    println(n != null)
                    println(p.size())
                    var b = mapOf(Book("Dune", 9.9), "old")
                    println(b.size())
                    println(b.containsKey(Book("Dune", 9.9)))
                    println(b.containsKey(Book("Dune", 9.8)))
                    var t: String? = b.get(Book("Dune", 9.9))
                    println(t != null)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}

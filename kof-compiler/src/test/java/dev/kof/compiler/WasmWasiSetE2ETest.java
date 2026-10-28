package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (28/10, #776 — split test-hygiene): as faces SET do WASI — as
 * fatias 1/2/3 (Int/String/escalares) + a SET-STR da rodada 53 (o remove
 * por conteúdo + o fix da célula 24B). O ratchet de higiene (≤500) forcou
 * a saida das faces de set do WasmWasiListE2ETest (o mesmo precedente dos
 * splits MapValue/Trig); os helpers sao os da classe irma, CHAMADA
 * QUALIFICADA (o ratchet bane nomes duplicados entre classes).
 */
class WasmWasiSetE2ETest {

    @Test
    void setBasicsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): SET fatia 1 — Set<Int> no WASI: o add
        // DEDUPLICA (o scan de conteudo antes do anexa — o contrato
        // HashSet) e DEVOLVE o booleano (anexou ou dedup — a forma void da
        // cadeia setOf dropa no despacho, o add-declaracao deixa o KofPop
        // consumir); o remove por valor devolve "removeu" (o KofPop idem).
        // Pego rodando: a condicao do has INVERTIDA (lt_s em vez de ge_s —
        // o shift rodava na NAO-remocao e corrompia o vizinho, S12 mediu).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = setOf(5, 6, 7)
                    s.add(6)
                    println(s.size())
                    s.remove(5)
                    println(s.size())
                    println(s.contains(7))
                    println(s.contains(5))
                    s.remove(99)
                    println(s.size())
                    var x = 42
                    s.add(x)
                    println(s.contains(42))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void setStringMembersMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): SET fatia 2 — os membros de STRING: o add
        // deduplica por CONTEUDO via o kof.strEq existente (o mesmo do fold
        // de `==`). A METADE MAP deste teste original ficou no
        // WasmWasiListE2ETest (o split do ratchet da rodada 53).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = setOf("a", "b")
                    s.add("c")
                    s.add("a")
                    println(s.size())
                    println(s.contains("b"))
                    println(s.contains("z"))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void setScalarElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): SET fatia 3 — os ESCALARES restantes: o
        // long entra DIRETO no slot i64 (3e9 > Int.MAX prova a largura), o
        // double via BITS (i64.reinterpret_f64 — a igualdade binaria do
        // HashSet; NaN == NaN aqui e' falso como no JVM), o bool estende o
        // i32 (i64.extend_i32_u), o char e' o codel i64. O despacho converte
        // ANTES do call; o runtime i64 e o mesmo do Set<Int>.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var ls = setOf(1L, 2L)
                    ls.add(3000000000L)
                    println(ls.size())
                    println(ls.contains(3000000000L))
                    var ds = setOf(1.5, 2.5)
                    ds.add(3.5)
                    println(ds.size())
                    println(ds.contains(2.5))
                    ds.remove(1.5)
                    println(ds.contains(1.5))
                    var bs = setOf(true, false)
                    println(bs.size())
                    println(bs.contains(true))
                    var cs = setOf('a', 'b')
                    println(cs.contains(97))
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void setStringRemoveAndGrowMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // SET-STR (28/10, rodada 53, #776): o Set<String>.remove por
        // CONTEUDO (o kof.setRemoveStr novo — o mesmo contrato strEq do
        // add/contains; o despacho roteia o elemento string ao runtime
        // novo, o handle cru como arg). O DEFEITO RAIZ QUE A SONDA PEGOU
        // (Q0): o setNew alocava a celula com 16 bytes e a cap vivia em
        // cell+16 — o store da cap CAIA NO SLOT 0 do bloco de dados, a
        // cap lida virava o valor do elemento 0, o grow NUNCA disparava e
        // os appends > 8 estouravam no bloco vizinho do heap (os literais
        // de String corrompidos, medido nas sondas S5/S7/S8; a via Int
        // escapava por sorte — as escritas caian no espaco livre do bump).
        // O fix = a celula de 24 bytes (a lei da lista/mapa). As faces: o
        // dedup do setOf, o add true/false, o remove presente/ausente,
        // o void-remove, o GROW de 11 elementos (a regiao que a cap
        // quebrada corrompia) e o conjunto vazio tipado.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = setOf("a", "b", "a")
                    println(s.size())
                    println(s.contains("b"))
                    println(s.contains("z"))
                    s.add("c")
                    println(s.size())
                    println(s.remove("a"))
                    println(s.size())
                    println(s.contains("a"))
                    println(s.remove("a"))
                    println(s.remove("zzz"))
                    println(s.size())
                    s.remove("b")
                    println(s.size())
                    println(s.contains("b"))
                    println(s.contains("c"))
                    var g = setOf("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11")
                    println(g.size())
                    println(g.contains("s4"))
                    println(g.contains("s5"))
                    println(g.remove("s5"))
                    println(g.size())
                    println(g.contains("s6"))
                    println(g.contains("s5"))
                    var e = setOf<String>()
                    println(e.size())
                    println(e.remove("x"))
                    e.add("m")
                    println(e.size())
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}
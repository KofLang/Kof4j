package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GC-1 (29/10, #776): a face do COLETOR mark-sweep sobre a memória linear
 * WASM — o design nativo (G-0..G-4, `native-multiarch.md`) re-expressado
 * por D-WASM-GO/D-WASM-04/05. O contrato observável = o do nativo G-6(a):
 * o allocator, na EXAUSTÃO da memória linear, COLETA e recicla — o
 * programa que exauria os 64KB e TRAPAVA (rc=134) completa com o GC,
 * byte-idêntico ao oráculo JVM. As faces: o loop de concat de 5000
 * iterações (o RED medido: pre-GC = rc=134) + o vizinho de 2000 (o
 * fronteira que cabia sem GC — a não-regressão).
 */
class WasmWasiGcE2ETest {

    @Test
    void gcCollectLetsExhaustingConcatLoopComplete(@TempDir Path dir) throws Exception {
        // GC-1: o loop de 5000 concats exaura a memória de 1 página no
        // código pré-GC (o RED medido por stash: rc=134 — o OOM trap do
        // bump sem coleta). Com o coletor: o mark (as raízes do spill +
        // a caminhada conservadora da gray-list intrusiva — a lição
        // §637/§639) + o sweep (a gc-list → a free-list) reciclam e o
        // loop COMPLETA byte-idêntico ao oráculo JVM.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var acc = 0
                    var i = 0
                    while (i < 5000) {
                        var s = "s" + i
                        acc = acc + s.length()
                        i = i + 1
                    }
                    println(acc)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void concatLoopThatFitsWithoutGcIsUnchanged(@TempDir Path dir) throws Exception {
        // A não-regressão: o loop de 2000 (o que CABIA sem o coletor) continua
        // byte-idêntico — o layout do cabeçalho (8 bytes antes do dado, o
        // handle = o data pointer) não mudou nenhum resultado observável.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var acc = 0
                    var i = 0
                    while (i < 2000) {
                        var s = "s" + i
                        acc = acc + s.length()
                        i = i + 1
                    }
                    println(acc)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void gcSurvivesRecordsAndCollectionsUnderPressure(@TempDir Path dir) throws Exception {
        // O coletor sobre os BLOCOS de record/list (o mark conservador
        // alcança os handles dentro dos blocos: o record na lista
        // sobrevive à coleta disparada por um loop vizinho de concat).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                main(String[] args) {
                    var l = listOf(Point(3, 4), Point(5, 6))
                    var j = 0
                    while (j < 5000) {
                        var s = "s" + j
                        j = j + 1
                    }
                    println(l.size())
                    var p: Point? = l.get(0)
                    println(p != null)
                    var q: Point? = l.get(0)
                    println(q != null)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void gcSafePointRoutingSurvivesMixedSizesWithRetainedLiveness(@TempDir Path dir) throws Exception {
        // GC-2 (29/10, #776): O ROTEAMENTO DE SAFE POINTS — a face que
        // PEGOU o Q0 do GC-1: os tamanhos MISTOS (as strings crescentes +
        // os records) com o liveness RETIDO (a lista `keep` cruza todo o
        // churn). O RED medido (pre-GC-2): em 1000 o `acc` divergia em 10
        // (um bloco VIVO liberado — o pendente da pilha durante o alloc
        // aninhado); em 20000 a memória falhava (a gray-list intrusiva
        // SOBRESCREVIA o campo gcnext — o sweep andava por links
        // selvagens). O FIX: os allocs de runtime = heapAllocNG (nunca
        // coletam — o pendente do caller é invisível ao spill), a coleta
        // = só nos sites de nível de programa com a pilha limpa; o
        // cabeçalho = 16 bytes [size|mark][gcnext][graynext][pad] (o
        // gray com campo PRÓPRIO — a lição §637/§639: a gray intrusiva
        // corrompia o link da gc-list).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                main(String[] args) {
                    var keep = listOf(Point(1, 2), Point(3, 4))
                    var acc = 0
                    var i = 0
                    while (i < 20000) {
                        var s = "tag" + i + "x"
                        var p = Point(i, i + 1)
                        acc = acc + s.length() + p.x
                        i = i + 1
                    }
                    println(keep.size())
                    var a: Point? = keep.get(0)
                    println(a != null)
                    var b: Point? = keep.get(1)
                    println(b != null)
                    println(acc)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void gcValidatesConservativeCandidatesUnderTwoRecordPressure(@TempDir Path dir) throws Exception {
        // GC-3 (29/10, #776): A SANIDADE DO CANDIDATO CONSERVADOR — a face
        // que PEGOU os DOIS Q0s do GC-2: (a) o SWEEP's next = o set do CUR
        // (o spin infinito — o nxt devia ser LOAD(cur+4); o G1..G4
        // passavam porque o rewrite NG impedia o coletor de disparar nos
        // churns de runtime — o G5 foi o primeiro a rodá-lo); (b) o
        // FALSO-POSITIVO CONSERVADOR — o escalar i64 grande (i*6 ≈ 120000
        // = em [heapFirst, top)) virava "ponteiro" → o "cabeçalho" lixo →
        // o tamanho enorme → a caminhada OOB. O FIX: o tryMark valida
        // tW + tSz ≤ heapTop antes de marcar; os falsos-positivos
        // restantes = o over-mark seguro.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                main(String[] args) {
                    var acc = 0
                    var i = 0
                    while (i < 20000) {
                        var s = "tag" + i + "x"
                        var p = Point(i, i + 1)
                        var q = Point(i * 2, i * 3)
                        acc = acc + s.length() + p.x + q.x
                        i = i + 1
                    }
                    println(acc)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}

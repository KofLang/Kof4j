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
}

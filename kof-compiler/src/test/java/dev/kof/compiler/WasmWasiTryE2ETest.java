package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TRY-1 (01/11, #776 — round 85): o try/catch/throw de {@code String} no
 * frontend WASI — o ÚLTIMO construto de linguagem fora do subset escalar
 * 15.2. A proposta EH do wasm (tag + try_table/throw, medida no wasmtime
 * 49.0.2) dá o desenrolar ENTRE frames; a pilha de exceções nativa (§551)
 * dá o despacho DENTRO do frame — o protocolo é re-expresso sobre a máquina
 * de estados {@code $pc} (a escrivania na memória linear: contagem@0, msg
 * estacionada@4, registros de handler de 8 bytes@8+8n; a região de 4KB
 * reservada LAZILY — módulos sem try ficam byte-estáveis).
 *
 * <p>O contrato é o PRINCÍPIO dos outros E2E WASI: um frontend nunca tem
 * comportamento diferente entre backends — cada face compara o stdout do
 * wasmtime com o ORÁCULO JVM da MESMA fonte e valida o módulo no
 * wasm-tools oficial. O wrapper do frame (openFrame/closeFrame) só existe
 * em funções COM try; frames sem try são transparentes ao EH do wasm.</p>
 */
class WasmWasiTryE2ETest {

    @Test
    void throwCatchMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // A face T1: o throw no corpo, o catch do String com concat da msg,
        // a execução segue após o try (o caminho normal NÃO captura o throw
        // seguinte — §549 medido no nativo, o mesmo protocolo aqui).
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        println("before")
                        throw "boom"
                    } catch (String e) {
                        println("caught: " + e)
                    }
                    println("done")
                }
                """);
    }

    @Test
    void crossFrameThrowIsCaughtByTheCaller(@TempDir Path dir) throws Exception {
        // A face T2: o throw numa FUNÇÃO chamada dentro do try — o desenrolar
        // ENTRE frames do EH do wasm leva a msg ao try_table do chamador.
        // (o helper Int fica no subset escalar 15.2 — o helper String-returning
        // é 15.3 e recusa antes, medido)
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        println(n() + 1)
                    } catch (String e) {
                        println("c:" + e)
                    }
                    println("end")
                }
                n(): Int {
                    throw "deep"
                }
                """);
    }

    @Test
    void nestedTryInnerCatchesFirst(@TempDir Path dir) throws Exception {
        // A face T3: o try dentro do try — o INTERNO despacha o próprio
        // throw (a contagem empilha os dois registros; o despacho lê o topo),
        // a execução continua no externo e ele termina sem disparar.
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        try {
                            throw "inner"
                        } catch (String e) {
                            println("c1:" + e)
                        }
                        println("after-inner")
                    } catch (String e2) {
                        println("c2:" + e2)
                    }
                    println("end")
                }
                """);
    }

    @Test
    void rethrowFromCatchReachesTheOuterHandler(@TempDir Path dir) throws Exception {
        // A face T4: o rethrow DENTRO do catch (o corpo de catch roda JÁ
        // desvinculado, §551) — o throw sai do try interno e o EXTERNO
        // despacha.
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        try {
                            throw "deep"
                        } catch (String e) {
                            println("c1:" + e)
                            throw e
                        }
                    } catch (String e2) {
                        println("c2:" + e2)
                    }
                    println("end")
                }
                """);
    }

    @Test
    void noThrowTakesTheFastPath(@TempDir Path dir) throws Exception {
        // A face T5: o try que NÃO dispara — o caminho normal (o KofExcUnlink
        // desempilha; a execução segue sem tocar o handler).
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        println("in")
                    } catch (String e) {
                        println("c:" + e)
                    }
                    println("end")
                }
                """);
    }

    @Test
    void lateThrowAfterInnerTryHitsTheOuterHandler(@TempDir Path dir) throws Exception {
        // A face T6: o §549 ANALOG — o throw DEPOIS do try interno fechar
        // (o caminho normal desempilhou o registro interno) ainda é capturado
        // pelo try EXTERNO, nunca pelo interno já fechado. O defeito original
        // do nativo (o handler continuava vinculado e capturava o throw
        // SEGUINTE) não se reproduz aqui.
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        try {
                            throw "inner"
                        } catch (String e) {
                            println("c1:" + e)
                        }
                        throw "late"
                    } catch (String e2) {
                        println("c2:" + e2)
                    }
                    println("end")
                }
                """);
    }

    @Test
    void returnFromTryUnlinksAndReturns(@TempDir Path dir) throws Exception {
        // A face T7: o `return` dentro do try — o unlink do caminho de saída
        // restaura a contagem e o frame retorna (sem o unlink o handler
        // vazaria para o código seguinte do chamador).
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        println("in")
                        return
                    } catch (String e) {
                        println("c:" + e)
                    }
                    println("after")
                }
                """);
    }

    @Test
    void breakAcrossTryInsideLoopRestoresTheCount(@TempDir Path dir) throws Exception {
        // A face T8: o break ATRAVESSANDO o try dentro do laço — o unlink do
        // break desempilha o registro; o laço continua do jeito certo.
        assertTryFace(dir, """
                main(String[] args) {
                    for (var i = 0.0; i < 3.0; i = i + 1.0) {
                        try {
                            if (i == 1.0) {
                                break
                            }
                            println("i=" + i)
                        } catch (String e) {
                            println("c:" + e)
                        }
                    }
                    println("out")
                }
                """);
    }

    @Test
    void sequentialTrysResetTheCount(@TempDir Path dir) throws Exception {
        // A face T9: dois try SEQUENCIAIS — o segundo dispara e o PRÓPRIO
        // despacha (a contagem restaurada entre eles; o handler do primeiro,
        // já fechado, não captura — §549 de novo, em trys irmãos).
        assertTryFace(dir, """
                main(String[] args) {
                    try {
                        throw "one"
                    } catch (String e) {
                        println("c1:" + e)
                    }
                    try {
                        throw "two"
                    } catch (String e2) {
                        println("c2:" + e2)
                    }
                    println("end")
                }
                """);
    }

    /** O contrato comum: o oráculo JVM da MESMA fonte, o validador oficial
     *  e o stdout do wasmtime idêntico (o padrão WasmWasiFlowE2ETest). */
    static void assertTryFace(Path dir, String source) throws Exception {
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), source);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}

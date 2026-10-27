package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase E (27/10, #776 — split test-hygiene): as faces de ARRAY do WASI
 * (ARRAY-1) — extraídas do {@code WasmWasiListE2ETest} (562 > o ratchet do
 * check_test_hygiene). Responsabilidade única: `new Int[n]` + store/load/
 * length no bump heap e o gate §368 do elemento.
 */
class WasmWasiArrayE2ETest {

    @Test
    void intArraysMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): ARRAY-1 — `new Int[n]` + store/load/length
        // no bump heap com o layout JÁ contratado da 15.3c-sliceB ([count
        // i32][slots i32×N] — o array args). O valor do array e' o PONTEIRO
        // CRU i32 (o local decl ganhou ArrayType → I32); o store tem o
        // bounds-trap (D-WASM-03) e o Int entra com wrap; o load sai com
        // SIGN-EXTEND (0xac — o elem Int e' i64-wide na pilha, o caminho
        // String do args fica byte-intocado); elems negativos e o wrap do
        // Int (2000000000) pinam a largura. O OOB (a[4] em Int[4]) e' o
        // TRAP documentado (wasmtime rc=134 — medido; nunca lixo), nao e'
        // face de paridade. O narrowing Long→Int na store RECUSA SEM021 nos
        // DOIS alvos (o gate §368 do elemento — a face a seguir).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var a = new Int[4]
                    a[0] = -5
                    a[1] = 2000000000
                    a[2] = 7
                    a[3] = -1
                    println(a[0])
                    println(a[1])
                    println(a[3])
                    var n = 3
                    var b = new Int[n]
                    b[0] = 100
                    println(b.length)
                    println(b[0])
                    var s = 0
                    for (var i = 0; i < a.length; i = i + 1) {
                        s = s + a[i]
                    }
                    println(s)
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void longIntoIntArrayIsRefusedSem021(@TempDir Path dir) throws Exception {
        // ARRAY-1 (27/10, #776): o gate §368 do ELEMENTO — o narrowing
        // Long→Int na store de array RECUSA pelo nome nos DOIS alvos
        // (SEM021, o MESMO codigo da store de variavel — o N2 mediu o
        // contrato; antes o programa compilava "clean" e morria em
        // VerifyError na carga, com o launcher JVM disfarcando de "JavaFX
        // runtime missing"). R6/§126: nunca truncar silenciosamente.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        Assumptions.assumeTrue(wasmtime != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var a = new Int[2]
                    var v = 3000000000L
                    a[0] = v
                    println(a[0])
                }
                """);
        var wasi = new CompilerDriver().compile(dir.resolve("Main.kf"),
                dir.resolve("out-wasi"), dev.kof.compiler.Target.WASI);
        assertFalse(wasi.success(), "o narrowing Long→Int deve recusar");
        assertTrue(wasi.diagnostics().getDiagnostics().toString().contains("SEM021"),
                "a recusa e' SEM021: " + wasi.diagnostics().getDiagnostics());
    }
}

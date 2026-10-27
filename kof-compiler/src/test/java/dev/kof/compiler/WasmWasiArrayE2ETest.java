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

    @Test
    void stringArraysMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // ARRAY-2 (27/10, #776): `new String[n]` — o MESMO alloc da fatia
        // Int (os slots zerados do bump SAO os handles null); o store SEM
        // wrap (o handle ja e' i32 — o wrap do i32 leria o vizinho) e o
        // scratch pela LARGURA (o dupWideIdx e' I64 — o handle i32 no slot
        // errado o validator pegou); o load e a maquinaria da face args.
        // A LEITURA do slot NAO inicializado renderiza "null" como o JVM —
        // o kof.writeStr ganhou o branch 0-handle (um handle REAL nunca e'
        // 0; os modulos existentes nao mudam). A leitura do slot Int nao
        // inicializado mediu GRATIS (o bump entrega zeros — 0/0 nos dois).
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var a = new String[3]
                    a[0] = "x"
                    a[2] = "z"
                    println(a.length)
                    println(a[0])
                    println(a[1])
                    println(a[2])
                    var b = new Int[3]
                    b[0] = 5
                    println(b[1])
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }

    @Test
    void boolCharArraysMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // ARRAY-3 (27/10, #776): os elems Bool/Char no slot de 4 bytes — o
        // store da wrap para o char (i64) e vai CRU para o bool (i32); o
        // load ZERO-estende o char (os code points sao nao-negativos) e NAO
        // estende o bool (o rastro "bool" e' estreito — o wide stale
        // zerado). A leitura do slot NAO inicializado anda no bump zerado:
        // false / o char NUL — o oraculo mediu os DOIS.
        var wasmtime = WasmWasiListE2ETest.host("wasmtime");
        var wasmTools = WasmWasiListE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var a = new Bool[3]
                    a[0] = true
                    a[2] = false
                    println(a.length)
                    println(a[0])
                    println(a[1])
                    println(a[2])
                    var c = new Char[3]
                    c[0] = 'a'
                    c[2] = 'z'
                    println(c.length)
                    println(c[0])
                    println(c[2])
                }
                """);
        WasmWasiListE2ETest.assertListFaceMatchesJvm(dir);
    }
}

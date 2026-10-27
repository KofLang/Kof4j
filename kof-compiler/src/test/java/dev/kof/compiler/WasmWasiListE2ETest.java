package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase E (27/10, #776): as faces LIST do WASI (as colecoes da unidade 15.3).
 * O contrato e o PRINCIPIO: um frontend nunca tem comportamento diferente
 * entre backends — cada face compara o stdout do wasmtime com o ORACULO JVM
 * da MESMA fonte e valida o modulo no wasm-tools oficial. O ratchet de
 * higiene (≤500) forcou a saida das faces de lista do WasmWasiFlowE2ETest
 * (mesmo precedente do split do WasmWasiE2ETest).
 */
class WasmWasiListE2ETest {

    static String jvmBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    private static void assertListFaceMatchesJvm(Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "module must pass the official validator: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "the frontend must behave identically on the JVM and on WASI");
    }


    @Test
    void listOfBasicsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 1 — List<Int> no WASI: o desugar
        // listOf (kof_list_new + N× add) + size/get no bump heap. O handle e'
        // uma CELULA INDIRETA ([dataPtr][len][cap]) para o add que cresce
        // (dobra, copia, reescreve a celula) NUNCA invalidar o handle do
        // chamador — o contrato observavel do ArrayList da JVM. A face
        // forc a DOIS crecimentos (11 elems: cap 4->8->16 — o primeiro
        // corte desta fatia pegou o heapAlloc gravando o DELTA no global e
        // a copia sobreposta borrava o bloco: 1,1,1,1 medido), a iteracao
        // com o indice em variavel e o add de valor computado.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
                    println(xs.size())
                    var i = 0
                    while (i < xs.size()) {
                        println(xs.get(i))
                        i = i + 1
                    }
                    var ys = listOf(100)
                    ys.add(200)
                    ys.add(300)
                    println(ys.get(0) + ys.get(1) + ys.get(2))
                    var z = 40
                    ys.add(z * 2)
                    println(ys.get(3))
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listMutatorsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 3 — os MUTADORES: set (store com
        // trap de fronteira — o Kof set e' void, o velho NAO volta), remove
        // (por indice, desloca para a esquerda, DEVOLVE o elemento — o
        // KofPop do desugar o consome) e clear (len=0). set/remove tem SEM
        // fantasma na pilha (diferente das consultas da fatia 2 — medido no
        // WAT: o drop aqui consumia o proprio valor). Os elems String viajam
        // como na fatia 2 (o handle zero-extendido no slot i64).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(10, 20, 30)
                    xs.set(1, 99)
                    println(xs.get(1))
                    xs.remove(0)
                    println(xs.size())
                    println(xs.get(0))
                    xs.clear()
                    println(xs.size())
                    println(xs.isEmpty())
                    var ss = listOf("a", "b", "c")
                    ss.set(1, "z")
                    println(ss.get(1))
                    ss.remove(0)
                    println(ss.get(0))
                    println(ss.size())
                }
                """);
        assertListFaceMatchesJvm(dir);
    }

    @Test
    void listOfNonIntElementIsHonestRefusal(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): a fatia so' admite elems Int — um add de
        // String recusa pelo nome (WASM002, sem artefato — Q7: nunca lista
        // silenciosa errada).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1)
                    var s = "x"
                    xs.add(s)
                }
                """);
        var wasi = new CompilerDriver().compile(dir.resolve("Main.kf"),
                dir.resolve("out-wasi"), dev.kof.compiler.Target.WASI);
        assertFalse(wasi.success(), "a fatia deve recusar elems nao-Int");
        assertTrue(wasi.diagnostics().getDiagnostics().toString().contains("List.add"),
                "a recusa nomeia o metodo: " + wasi.diagnostics().getDiagnostics());
    }

    @Test
    void listQueriesAndStringElemsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): LIST fatia 2 — contains/indexOf/lastIndexOf/
        // isEmpty (as consultas Int) + List<String> elems (o handle i32 viaja
        // zero-extendido no slot i64 e volta wrap no get, pelo type argument
        // do OWNER). Os desugares das consultas trazem um 0 FANTASMA no topo
        // (o param 0 = o receiver tipado, medido no IR) — o despacho o dropa.
        // O scan de busca e' DOIS blocos: as saidas de fronteira caem no -1
        // (dentro do bloco externo) e o br do MATCH mira o bloco externo
        // (preserva o indice — um bloco so' fazia o match cair no -1).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var xs = listOf(1, 2, 3)
                    println(xs.contains(2))
                    println(xs.contains(9))
                    println(xs.indexOf(2))
                    println(xs.indexOf(9))
                    println(xs.lastIndexOf(3))
                    println(xs.lastIndexOf(9))
                    println(xs.isEmpty())
                    var ys = listOf(1)
                    println(ys.isEmpty())
                    var ss = listOf("a", "b", "c")
                    println(ss.size())
                    println(ss.get(1))
                    ss.add("d")
                    println(ss.get(3))
                    var i = 0
                    while (i < ss.size()) {
                        println(ss.get(i))
                        i = i + 1
                    }
                }
                """);
        assertListFaceMatchesJvm(dir);
    }
}

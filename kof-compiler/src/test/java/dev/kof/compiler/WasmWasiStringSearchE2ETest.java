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
 * TIER 15 unit 15.3 (#776): as faces de BUSCA/ORDEM de String do runtime WASI
 * — `lastIndexOf` e `compareTo` — contra o oraculo JVM da mesma fonte.
 * Separado de {@code WasmWasiStringE2ETest} pelo gate {@code check_test_hygiene}
 * (classe de teste acima de 500 linhas).
 *
 * <p>Ambas compartilham a maquinaria de `WasmStrSearch`: laco de bloco-de-saida
 * unico (`strMatchAt`, `strUnitsBefore`) e `strCharAt`/`strLen` provados. O
 * ponto que estes testes trancam e' a semantica de CODE UNITS UTF-16 — um
 * `compareTo` por bytes daria outro numero em `"café"` vs `"cafz"`.
 */
class WasmWasiStringSearchE2ETest {

    static String jvmBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void compareToMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2j: `compareTo`. Lexicografico sobre CODE UNITS UTF-16:
        // devolve a diferenca do primeiro unit que difere, ou la-lb quando um e'
        // prefixo do outro. Reusa `strCharAt` (ja' provado para metades de
        // surrogate) + `strLen`, em vez de reimplementar varredura de bytes.
        // O caso "café" vs "cafz" = 111 e' o que prova a semantica de UNITS
        // (é = U+00E9, z = U+007A) em vez de bytes (onde o resultado seria outro).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("abc".compareTo("abd"))
                    println("abc".compareTo("abc"))
                    println("abc".compareTo("ab"))
                    println("ab".compareTo("abc"))
                    println("café".compareTo("café"))
                    println("b".compareTo("a"))
                    println("café".compareTo("cafz"))
                    println("".compareTo("a"))
                    println("a".compareTo(""))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm compareTo compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle compareTo: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi compareTo compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit compareTo: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.compareTo must equal the JVM oracle");
    }

    @Test
    void lastIndexOfMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2i: `lastIndexOf`. Reusa a MESMA maquinaria do `indexOf`
        // (busca em bytes + indice em code units), mas continua a varredura e
        // guarda o ULTIMO acerto em vez de sair no primeiro.
        // A procura foi reescrita em dois lacos de responsabilidade unica
        // (`strMatchAt`, `strUnitsBefore`) porque as tentativas anteriores
        // falhavam por aritmetica de profundidade de `br`, nao por semantica.
        // Casos: padrao repetido (ultimo acerto), ausente (-1), agulha vazia
        // (= comprimento), nao-ASCII, agulha maior que o palheiro, e o controle
        // de acerto unico que separa lastIndexOf de indexOf.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("abcabc".lastIndexOf("bc"))
                    println("abcabc".lastIndexOf("zz"))
                    println("abcabc".lastIndexOf(""))
                    println("caféx".lastIndexOf("x"))
                    println("abcabc".lastIndexOf("abc"))
                    println("abcabc".lastIndexOf("abcabcx"))
                    println("€€x".lastIndexOf("€"))
                    println("€€x".indexOf("x"))
                    println("abcabc".indexOf("bc"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm lastIndexOf compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle lastIndexOf: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi lastIndexOf compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit lastIndexOf: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.lastIndexOf must equal the JVM oracle");
    }
}

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

    @Test
    void replaceMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2k: `replace(String, String)`. Alvo nao-vazio e' EXATO em
        // BYTES (UTF-8 e' prefix-free), e nao-sobreposto da esquerda p/ direita.
        // Alvo VAZIO e' o caso que exige atencao: o JVM insere a substituicao
        // antes de CADA code unit e no fim — um "replace de bytes" daria outra
        // coisa, e por isso ele tem caminho proprio com a contagem de units.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("abcabc".replace("b", "X"))
                    println("café".replace("é", "e"))
                    println("abc".replace("zz", "Y"))
                    println("aaaa".replace("aa", "b"))
                    println("ab".replace("", "-"))
                    println("abc".replace("abc", "Z"))
                    println("aéa".replace("é", "€"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm replace compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle replace: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi replace compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit replace: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.replace must equal the JVM oracle");
    }

    @Test
    void indexOfAndLastIndexOfWithFromIndexMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // fromIndex (aridades de 2). Os contratos MEDIDOS do JVM sao DIFERENTES
        // nas duas: `indexOf(str,from)` acha o MENOR acerto com indice >= from
        // (from acima do fim da' -1); `lastIndexOf(str,from)` acha o MAIOR acerto
        // com indice <= from, com from CLAMPADO (99 da' o ultimo acerto, nao -1).
        // A pilha do frontend e' [receiver,str,from] — a MESMA ordem da assinatura
        // do helper, entao nada e' reordenado.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "abcabc"
                    println(s.indexOf("b", 1))
                    println(s.indexOf("b", 2))
                    println(s.indexOf("b", 5))
                    println(s.lastIndexOf("b", 3))
                    println(s.lastIndexOf("b", 1))
                    println(s.lastIndexOf("b", 0))
                    println(s.lastIndexOf("b", 99))
                    println(s.indexOf("b"))
                    println(s.lastIndexOf("b"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm fromIndex compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle fromIndex: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi fromIndex compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit fromIndex: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "fromIndex overloads must equal the JVM oracle");
    }

    @Test
    void containsMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // `contains(t)` e' literalmente `indexOf(t) >= 0`: reusa a busca ja'
        // provada em vez de ter implementacao propria. Os casos fixam os
        // limites do contrato: agulha vazia = true, agulha maior = false,
        // ausente = false, e conteudo nao-ASCII.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("abcabc".contains("bc"))
                    println("abc".contains("zz"))
                    println("abc".contains(""))
                    println("café".contains("é"))
                    println("abc".contains("abcd"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm contains compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle contains: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi contains compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit contains: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.contains must equal the JVM oracle");
    }

    @Test
    void unimplementedOverloadsRefuseInsteadOfEmittingAnInvalidModule(@TempDir Path dir) throws Exception {
        // REGRESSAO MEDIDA 22/10 (classe, nao instancia): sobrecargas que o
        // lowering NAO consome empilhavam um operando extra e o modulo saia
        // INVALIDO — `compile(...).success()` era true, e so' o HOST recusava.
        // Achadas sondando TODAS as faces ja' pousadas: instances
        // `startsWith(str, fromIndex)` e `replace(Char, Char)`.
        // O contrato (Q7/R6): cada uma tem de RECUSAR na compilacao, nomeada.
        record Shape(String label, String call, String needle) {}
        var shapes = List.of(
                new Shape("startsWith(str,from)", "\"abcdef\".startsWith(\"cd\", 2)", "fromIndex"),
                new Shape("replace(Char,Char)", "\"abc\".replace('b', 'X')", "Char"));
        for (var shape : shapes) {
            Files.writeString(dir.resolve("Main.kf"), """
                    main(String[] args) {
                        println(%s)
                    }
                    """.formatted(shape.call()));
            var wasi = new CompilerDriver().compile(dir.resolve("Main.kf"),
                    dir.resolve("out-" + shape.label().replaceAll("[^a-z]", "")), Target.WASI);
            assertFalse(wasi.success(),
                    shape.label() + " nao pode 'passar' e emitir modulo invalido");
            assertTrue(wasi.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.message().contains(shape.needle())),
                    shape.label() + " deve nomear a face em falta (" + shape.needle() + "): "
                            + wasi.diagnostics().getDiagnostics());
        }
    }

    @Test
    void charInAVariablePrintsAndConvertsLikeTheJvm(@TempDir Path dir) throws Exception {
        // REGRESSAO MEDIDA 22/10 (defeito PRE-EXISTENTE, reproduzia sem nenhuma
        // mudanca minha): `char` era declarado como slot i32 enquanto o Char do
        // Kof viaja como i64 na pilha e kof.writeChar/kof.strChar recebem i64.
        // Resultado: println(c) com c em VARIAVEL emitia module invalido
        // (expected i32, found i64). Literal direto funcionava porque nao passava
        // por slot. A correcao e' no mapType: char => TYPE_I64.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var c = 'k'
                    println(c)
                    println(c.toString())
                    println(String.valueOf(c))
                    var d = "abc".charAt(0)
                    println(d)
                    println(d.toString())
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm char-var compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle char-var: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi char-var compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit char-var: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "Char in a variable must print/convert like the JVM oracle");
    }

    @Test
    void integralDoublePrintsLikeTheJvmAndFractionalTraps(@TempDir Path dir) throws Exception {
        // Unidade 15.3, face Double — PRIMEIRO incremento, escopo medido.
        // O Double.toString completo exige o Schubfach shortest-round-trip
        // (500+ linhas de asm) para bater o JDK. Este incremento cobre so' o
        // subconjunto INTEGRAL, onde o rendering decimal simples JA' e' a saida
        // do JDK (`2.0`, `-3.0`, `100.0`). `-0.0` esta' aqui de proposito: e' a
        // REGRESSAO que este teste passou a pegar — trunc(-0.0) = 0, entao o
        // sinal se perdia e saia `0.0` onde o JVM imprime `-0.0`.
        // Valor FRACIONARIO trapa em vez de
        // imprimir uma string silenciosamente diferente (regra 5 / Q7) — o
        // teste fixa as DUAS metades do contrato.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(2.0)
                    println(100.0)
                    println(-3.0)
                    println(0.0)
                    println(-0.0)
                    println(1.0)
                    println(-1.0)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm integral-double compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle integral-double: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi integral-double compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit integral-double: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "integral Double printing must equal the JVM oracle");

        // Metade 2: fracionario NAO imprime string errada — falha.
        Files.writeString(dir.resolve("Frac.kf"), """
                main(String[] args) {
                    println(1.5)
                }
                """);
        var fracWasi = driver.compile(dir.resolve("Frac.kf"), dir.resolve("out-frac"), Target.WASI);
        assertTrue(fracWasi.success(), "fracional ainda COMPILA (o trap e' em execucao)");
        var fracProc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-frac", "Default", "Frac.wasm").toString()))
                .redirectErrorStream(true).start();
        String fracOut = new String(fracProc.getInputStream().readAllBytes());
        assertTrue(fracProc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertNotEquals(0, fracProc.exitValue(),
                "valor fracionario tem de TRAPAR, nunca imprimir string divergente: " + fracOut);
    }

    @Test
    void concatenatingNumbersWithStringsMatchesTheJvm(@TempDir Path dir) throws Exception {
        // DEFEITO MEDIDO 22/10: `"n=" + n` recusava WASM002. O concat chama
        // `String.valueOf` sobre o operando, e para WASI o lowering BOXAVA o
        // primitivo antes (caminho generico), entao o `valueOf(Object)` nao
        // casava nenhum ramo de stringificacao. O WASI nao tem caixa generica:
        // como o Native (#773), ele stringifica pelo TIPO REAL — primitivo cru,
        // sem box. O teste cobre Int, Long, literal e variavel, alem do caso
        // que ja' funcionava (string + string) para nao regredir.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var n = 7
                    println("n=" + n)
                    println("m=" + 42)
                    var x: Long = 1000000
                    println("x=" + x)
                    var s = "a" + "b"
                    println(s)
                    println("t=" + true)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm concat-number compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle concat-number: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi concat-number compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit concat-number: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "string + number concat must equal the JVM oracle");
    }
}

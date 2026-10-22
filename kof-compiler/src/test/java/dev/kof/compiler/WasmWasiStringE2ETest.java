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
 * TIER 15 unit 15.3 batch F (#776, D-WEB-WASI-DEFAULT-0710): o runtime de
 * STRING do alvo WASI sob wasmtime, sempre contra o oraculo JVM da MESMA
 * fonte. Separado de {@code WasmWasiE2ETest} pelo gate
 * {@code check_test_hygiene} (classe de teste acima de 500 linhas).
 *
 * <p>A armadilha que estes testes existem para trancar: o handle WASI guarda
 * BYTES UTF-8, mas a semantica do Kof e' de CODE UNITS UTF-16 — `length` conta
 * leaders, os afixos comparam bytes (UTF-8 e' prefix-free), e os casos
 * nao-ASCII abaixo sao os que denunciariam uma implementacao "por offset".
 */
class WasmWasiStringE2ETest {

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
    void stringEqualityMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // 15.3d fatia F face 1: `==`/`!=` de String. O frontend baixa o operador
        // para `kof_string_equals(a,b)`; antes o lowering WASI nao tinha destino e
        // recusava WASM002 na comparacao mais basica do runtime de strings.
        // As formas cobrem conteudo igual, prefixo, vazio, literal-vs-literal e o
        // resultado de um concat — todas paridade byte-a-byte com o oraculo JVM.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var a = "ab"
                    var b = "ab"
                    var c = "cd"
                    var d = "abcd"
                    var e = ""
                    println(a == b)
                    println(a == c)
                    println(a != c)
                    println(a != b)
                    println(a == d)
                    println("lit" == "lit")
                    println(a == e)
                    println(e == e)
                    println("ab" + "cd" == d)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm string-eq compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle string-eq: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi string-eq compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit string-eq: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String ==/!= must equal the JVM oracle");
    }

    @Test
    void stringLengthCountsUtf16CodeUnitsLikeTheJvm(@TempDir Path dir) throws Exception {
        // 15.3d fatia F face 2b: `s.length()`. O handle WASI guarda BYTES UTF-8,
        // mas `length()` do Kof conta code units UTF-16 — ler o cabecalho daria
        // certo so em ASCII e divergiria EM SILENCIO em qualquer string nao-ASCII.
        // Este teste e o que tranca essa distincao: 'é' sao 2 bytes / 1 unit,
        // '€' sao 3 bytes / 1 unit, um emoji sao 4 bytes / 2 units (par surrogate).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("abc".length())
                    var s = "café"
                    println(s.length())
                    println("".length())
                    var e = "a€b"
                    println(e.length())
                    var f = "x\uD83D\uDE00y"
                    println(f.length())
                    println(("ab" + "c").length())
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm string-length compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle string-length: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi string-length compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit string-length: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.length() must count UTF-16 code units, like the JVM oracle");
    }

    @Test
    void stringIsEmptyEqualsAndConcatMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2c: as tres faces cuja semantica e EXATA com os helpers ja
        // provados. `isEmpty` decide pelo comprimento em BYTES (zero bytes UTF-8
        // <=> zero code units), `equals` e o mesmo `kof.strEq` do `==`, e
        // `concat` e o `kof.strConcat` do `+`. O ultimo caso cruza concat com
        // `length()` para pegar uma eventual regressao nao-ASCII das duas juntas.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var e = ""
                    var s = "ab"
                    println(e.isEmpty())
                    println(s.isEmpty())
                    println(s.equals("ab"))
                    println(s.equals("cd"))
                    println(s.equals(s))
                    println(s.concat("cd"))
                    println(s.concat("").concat("z"))
                    println(s.concat("café").length())
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm string-methods compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle string-methods: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi string-methods compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit string-methods: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String isEmpty/equals/concat must equal the JVM oracle");
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
    void trimMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2h: `trim()`. Exato como operacao de BYTES porque o JVM
        // corta code units <= U+0020 e em UTF-8 todo code unit assim e' UM byte
        // <= 0x20, enquanto toda sequencia multibyte comeca com byte >= 0xC2 —
        // os conjuntos de pontas coincidem, sem contar code units.
        // Casos: sem padding, ambos os lados, so' espacos (vira vazia), TAB e
        // newline (tambem <= U+0020), nao-ASCII com e sem padding, e um espaco
        // INTERNO que NAO pode ser cortado.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println("  ab  ".trim() + "|")
                    println("ab".trim() + "|")
                    println("   ".trim() + "|")
                    println("\\tab\\t".trim() + "|")
                    println("\\nab\\n".trim() + "|")
                    println("café  ".trim() + "|")
                    println("  café  ".trim() + "|")
                    println(("  €  ".trim()).length())
                    println("  a b  ".trim() + "|")
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm trim compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle trim: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi trim compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit trim: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.trim must equal the JVM oracle");
    }

    @Test
    void charAtReturnsBothSurrogateHalvesAndNonAsciiCharsPrintAsUtf8(@TempDir Path dir) throws Exception {
        // fatia F face 2g — a que exigiu quatro desenhos. Duas coisas quebram
        // juntas aqui:
        //  (1) `charAt` de um par surrogate: o indice do unit ALTO devolve
        //      0xD800+.. e o do BAIXO 0xDC00+.. — imprimir o valor NUMERICO e'
        //      o que prova a metade (imprimir o Char mostraria '?' nos dois,
        //      porque um Char isolado de par e' replacement no JVM tambem).
        //  (2) `println(Char)` nao-ASCII: o JVM escreve UTF-8 (e' = 2 bytes,
        //      'euro' = 3), nao o byte baixo.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var f = "x\uD83D\uDE00y"
                    println(f.charAt(0) as Int)
                    println(f.charAt(1) as Int)
                    println(f.charAt(2) as Int)
                    println(f.charAt(3) as Int)
                    println("café".charAt(3))
                    println("a€b".charAt(1))
                    println(f.charAt(1))
                    println(f.charAt(2))
                    println('A')
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm surrogate compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle surrogate: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi surrogate compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit surrogate: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "surrogate halves + non-ASCII Char printing must equal the JVM oracle");
    }

    @Test
    void stringCharAtAndSubstringMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2f: `charAt`/`substring`, que exigem o MAPEAMENTO de
        // indice de code unit UTF-16 para offset de BYTE UTF-8 (`kof.strByteOff`).
        // Cobre o sentinela de 1 arg de `substring(i)` ("ate' o fim"), o recorte
        // vazio e os casos nao-ASCII nos dois sentidos — um avanco byte-a-byte
        // pararia no MEIO de um caractere multibyte.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "abcdef"
                    println(s.charAt(0))
                    println(s.charAt(5))
                    println(s.substring(1, 3))
                    println(s.substring(2))
                    println(s.substring(0, 0).length())
                    println("café".charAt(3) == 'é')
                    println("café".substring(3))
                    println("a€b".substring(1, 2))
                    println("a€b".length())
                    println("a€b".substring(2))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm charAt/substring compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle charAt/substring: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi charAt/substring compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit charAt/substring: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String charAt/substring must equal the JVM oracle");
    }

    @Test
    void stringIndexOfReturnsUtf16CodeUnitIndex(@TempDir Path dir) throws Exception {
        // fatia F face 2e: `s.indexOf(t)`. A busca casa em BYTES (pela mesma
        // propriedade prefix-free do startsWith), mas o INDICE devolvido e' de
        // CODE UNITS UTF-16 — o helper conta os units do prefixo [0,p).
        // O ultimo caso separa uma implementacao correta de uma que devolve o
        // offset de byte: em 'x😀y' o 'y' esta no byte 5 e no unit 3.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "abcabc"
                    println(s.indexOf("bc"))
                    println(s.indexOf("zz"))
                    println(s.indexOf(""))
                    println(s.indexOf("abc"))
                    println(s.indexOf("c"))
                    println("caféx".indexOf("x"))
                    println("caféx".indexOf("é"))
                    println("x\\uD83D\\uDE00y".indexOf("y"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm indexOf compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle indexOf: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi indexOf compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit indexOf: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.indexOf must return the UTF-16 code-unit index");
    }

    @Test
    void stringStartsWithAndEndsWithMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia F face 2d: prefixo/sufixo comparados em BYTES. E' exato porque
        // UTF-8 e' prefix-free (nenhuma sequencia valida e' prefixo de outra),
        // entao prefixo de bytes == prefixo de code units UTF-16, e o mesmo para
        // sufixo — dispensa a varredura de code units que charAt exigiria.
        // Casos: prefixo parcial, vazio, MAIOR que a string, a propria string,
        // e nao-ASCII nos dois sentidos (o where um deslocamento cru de +4
        // apareceria).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "abc"
                    println(s.startsWith("ab"))
                    println(s.startsWith(""))
                    println(s.startsWith("abcd"))
                    println(s.startsWith(s))
                    println("caféx".startsWith("café"))
                    println("café".startsWith("caf"))
                    println(s.endsWith("bc"))
                    println(s.endsWith(""))
                    println(s.endsWith("zabc"))
                    println("xcafé".endsWith("café"))
                    println("\\uD83D\\uDE00z".endsWith("z"))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm affix compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle affix: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi affix compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit affix: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String startsWith/endsWith must equal the JVM oracle");
    }

}

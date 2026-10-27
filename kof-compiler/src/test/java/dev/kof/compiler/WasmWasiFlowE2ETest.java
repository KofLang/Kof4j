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
 * Fase E (26/10, #776, D-WEB-WASI-DEFAULT-0710): o CONTROL FLOW do frontend
 * no WASI — condicoes sobre Bool-variavel, cadeias logicas (`&&`/`||`) e o
 * join do if-expression. O contrato e o PRINCIPIO: um frontend nunca tem
 * comportamento diferente entre backends — cada face compara o stdout do
 * wasmtime com o ORACULO JVM da MESMA fonte, e valida o modulo no wasm-tools
 * oficial. O caso heterogeneo do if-expression (`if (c) 1 else "x"`, join
 * boxed) ainda recusa honestamente no WASI e tem a fatia propria registrada
 * em `docs/wasm-wasi-plan.md`.
 */
class WasmWasiFlowE2ETest {

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
    void boolVariableConditionsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (24/10, #776): as condicoes sobre BOOL-VARIAVEL — o defeito
        // medido no plano (`var b = 1 > 2` + `if (b)` gerava modulo INVALIDO:
        // o slot bool e i32 no WASI e o literal INT (i64) rompia a largura).
        // Cobre if/else, if, while(flag), do-while(comparacao com false),
        // a negacao !d e a chamada Bool como condicao; o oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                Bool isBig(Int n) {
                    return n > 100
                }
                main(String[] args) {
                    var b = 1 > 2
                    if (b) { println(10) } else { println(20) }
                    var t = 1 < 2
                    if (t) { println(30) }
                    var flag = true
                    var i = 0
                    while (flag) {
                        i = i + 1
                        if (i >= 3) { flag = false }
                    }
                    println(i)
                    var d = false
                    var n = 0
                    do {
                        n = n + 1
                        if (n >= 2) { d = true }
                    } while (d == false)
                    println(n)
                    if (!d) { println(40) } else { println(50) }
                    if (isBig(200)) { println(70) }
                }
                """);
        assertFaceMatchesJvm(dir);
    }

    @Test
    void logicalChainsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (26/10, #776): as cadeias logicas — `&&`/`||` sobre bools e
        // sobre comparacoes — agora avaliam no WASI: o join vai para um
        // temporario (a maquina de estados nao transporta a pilha entre
        // estados; antes o modulo saia invalido). O curto-circuito e
        // PRESERVADO: o RHS com armadilha (1/z com z=0) nao roda quando o
        // lado esquerdo decide. O oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var b1 = true
                    var b2 = false
                    if (b1 && b2) { println(1) } else { println(2) }
                    if (b1 || b2) { println(3) } else { println(4) }
                    var x = 5
                    var y = 3
                    if (x > 0 && y > 0) { println(5) } else { println(6) }
                    if (x == 1 || x == 2) { println(7) } else { println(8) }
                    var z = 0
                    if (false && 1/z == 1) { println(9) } else { println(10) }
                    if (true || 1/z == 1) { println(11) } else { println(12) }
                    if (false && true || true) { println(13) } else { println(14) }
                    var c = false || true
                    println(c)
                }
                """);
        assertFaceMatchesJvm(dir);
    }

    @Test
    void ifExpressionJoinMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (26/10, #776): o JOIN do if-expression — `var m = if (c) 1
        // else 2` — agora avalia no WASI: o valor de cada ramo vai para um
        // TEMPORARIO e o join o carrega (a maquina de estados nao transporta
        // a pilha entre estados; antes o modulo saia invalido). Cobre Int,
        // String, Bool, aninhamento e o ramo que nao roda. O oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var flag = true
                    var m = if (flag) 1 else 2
                    println(m)
                    var s = if (flag) "yes" else "no"
                    println(s)
                    var b = if (flag) true else false
                    println(b)
                    var x = 5
                    var y = if (x > 3) x * 2 else x + 1
                    println(y)
                    var n = 3
                    var z = if (n > 0) (if (n > 5) 100 else 50) else 0
                    println(z)
                    var k = if (false) 1 else 2
                    println(k)
                }
                """);
        assertFaceMatchesJvm(dir);
    }

    /** Compila a mesma fonte para JVM e WASI, valida o modulo e compara o stdout (o contrato). */
    private static void assertFaceMatchesJvm(Path dir) throws Exception {
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
    void stringCaseFoldMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (26/10, #776): o case fold Unicode — toUpper/toLower com a
        // MESMA semantica D-STR-UNICODE do nativo (fold SIMPLES por code
        // unit; tabela BMP gerada do JDK; ASCII inline; astral passa
        // intocado). O oracle e o JVM; as excecoes de full-case-mapping
        // (ß→SS, İ→i̇, sigma final) ficam FORA do escopo ratificado — o
        // nativo tem o mesmo comportamento (a frente nao diverge entre
        // WASI e nativo).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "hello World"
                    println(s.toUpperCase())
                    println(s.toLowerCase())
                    var a = "caf\\c3\\a9 na\\c3\\afve"
                    println(a.toUpperCase())
                    println(a.toLowerCase())
                    var g = "ΣΟΣ"
                    println(g.toUpperCase())
                    println("σος".toUpperCase())
                    println("MiXeD123 !?".toUpperCase())
                    println("MiXeD123 !?".toLowerCase())
                }
                """);
        assertFaceMatchesJvm(dir);
    }


    @Test
    void mathIntFamilyMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (26/10, #776): a familia INT do math — abs/sign/min/max/
        // clamp + os predicados isEven/isOdd/isPositive/isNegative/isZero —
        // aritmetica i64 pura no runtime WASI (sem host imports). O oracle
        // e o JVM; inclui os edges (negativos, zero, clamp fora da faixa
        // dos dois lados e dentro).
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.abs(-7))
                    println(math.abs(7))
                    println(math.max(3, 9))
                    println(math.min(3, 9))
                    println(math.clamp(15, 0, 10))
                    println(math.clamp(-5, 0, 10))
                    println(math.clamp(5, 0, 10))
                    println(math.sign(-9))
                    println(math.sign(0))
                    println(math.sign(9))
                    println(math.isEven(4))
                    println(math.isEven(7))
                    println(math.isOdd(7))
                    println(math.isPositive(5))
                    println(math.isNegative(-5))
                    println(math.isZero(0))
                }
                """);
        assertFaceMatchesJvm(dir);
    }


    @Test
    void mathDoubleArithmeticFamilyMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): a familia DOUBLE de aritmetica pura do math —
        // sqrt (a INSTRUCAO f64.sqrt do WASM), toRadians/toDegrees (a MESMA
        // ordem do Math.toRadians/ToDegrees da JVM: v/180*PI e v*180/PI —
        // bit a bit), lerp (a+(b-a)*t) e percentage (part/total*100). Os
        // casos usam resultados INTEGRAIS porque o print fracionario segue
        // atras do port Schubfach (o teto honesto registrado). sin/cos/tan/
        // atan2/pow seguem fora (libm — fatia propria). O oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.sqrt(9.0))
                    println(math.sqrt(16.0))
                    println(math.sqrt(25.0))
                    println(math.toDegrees(1.5707963267948966))
                    println(math.toDegrees(3.141592653589793))
                    println(math.lerp(0.0, 10.0, 0.4))
                    println(math.lerp(10.0, 20.0, 0.5))
                    println(math.percentage(200.0, 400.0))
                    var w = 3.0
                    println(math.sqrt(w * w * 4.0))
                }
                """);
        assertFaceMatchesJvm(dir);
    }

    @Test
    void roundToMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): math.roundTo(value, decimals) — a MESMA
        // aritmetica S1b.3 do JVM/nativo: m=|d| saturado em 308, p=10^m por
        // multiplicacao repetida, half-away-from-zero (f >= 0.5 → +1 — GE,
        // nao GT: o meio-exato arredonda PARA LONGE do zero), overflow de
        // scaled devolve v. Casos INTEGRAIS (o print fracionario segue no
        // port Schubfach): d=0 nos dois sinais, o meio-exato 1.5/-1.5/0.5/
        // -0.5, o ANTI-DOUBLE-ROUNDING 0.49999999999999994 → 0, a escala
        // d<0 (1234.5678,-2 → 1200; 267.5,-1 → 270) e d>0 identidade
        // (12345.0,5). O oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(math.roundTo(2.675, 0))
                    println(math.roundTo(-2.5, 0))
                    println(math.roundTo(1234.5678, -2))
                    println(math.roundTo(1.5, 0))
                    println(math.roundTo(-1.5, 0))
                    println(math.roundTo(7.0, 3))
                    println(math.roundTo(267.5, -1))
                    println(math.roundTo(-267.5, -1))
                    println(math.roundTo(0.5, 0))
                    println(math.roundTo(-0.5, 0))
                    println(math.roundTo(0.49999999999999994, 0))
                    println(math.roundTo(12345.0, 5))
                }
                """);
        assertFaceMatchesJvm(dir);
    }


    @Test
    void combinedStringSearchAndSliceMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): os metodos de busca e de fatia NA MESMA
        // fonte — o strByteOff era dependencia comum de indexOf/contains E
        // de charAt/substring e era registrado DUAS VEZES (export
        // duplicado, modulo invalido). O dedupe por nome no backend fecha
        // a classe inteira de pares; os metodos combinados sao o oracle.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = "hello"
                    println(s.length())
                    println(s.contains("ell"))
                    println(s.indexOf("l"))
                    println(s.substring(1))
                    println(s.charAt(0))
                    println(s.startsWith("he"))
                    println(s.endsWith("lo"))
                }
                """);
        assertFaceMatchesJvm(dir);
    }


    @Test
    void charToStringAndConcatMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // Fase E (27/10, #776): o Char vira string — `"s" + c` e
        // `String.valueOf(c)` usam o MESMO kof.strChar que os campos Char de
        // record ja usavam (o runtime existia; o gancho do valueOf e o
        // registro condicional e' que faltavam). O loop constroi string a
        // partir de charAt — o padrao comum de filtragem por caractere.
        // O oracle e o JVM.
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var c = 'x'
                    println(c)
                    println("val: " + c)
                    println(String.valueOf('q'))
                    var word = "kof"
                    var initials = ""
                    var i = 0
                    while (i < word.length()) {
                        var ch = word.charAt(i)
                        if (i == 0 || ch != 'f') {
                            initials = initials + ch
                        }
                        i = i + 1
                    }
                    println(initials)
                }
                """);
        assertFaceMatchesJvm(dir);
    }

}

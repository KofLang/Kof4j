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
 * TIER 15 unit 15.3 slice 1 (#776, D-WEB-WASI-DEFAULT-0710, D-WASM-06): the
 * WASI-preview1 HOST. `Target.WASI` lowers `main` to `_start`, emits the
 * `wasi_snapshot_preview1` imports (fd_write/proc_exit) and prints scalar
 * values — stdout must equal the JVM oracle of the SAME source (measured:
 * 3, -42, 0, 55, true, false, A, 14). String variables/concat + `args` landed
 * in 15.3c. Record allocation + Int-field access landed in 15.3d increment 1
 * (bump-heap blocks, `ClassLayout` 8-byte slots, `KofNewObject`/`<init>` inline,
 * `KofLoadField` load). `println(record)`/`==` (toString/equals = INSTANCE-method
 * lowering) still refuse WASM002 naming the plan + slice + #776, with NO
 * artifacts (Q7) — that is 15.3d increment 2. The host flip stays unit 15.4.
 */
class WasmWasiE2ETest {

    private static final String SRC = """
            Int fib(Int n) {
                if (n < 2) { return n }
                return fib(n - 1) + fib(n - 2)
            }

            main(String[] args) {
                println(3)
                println(-42)
                println(0)
                println(fib(10))
                println(true)
                println(false)
                println(2 + 3 * 4)
                println("oi")
                println("hello kof")
                var s = "a" + "b"
                println(s)
                println("x" + "y" + "z")
            }
            """;

    private static final String SRC_RECORD_CORE = """
            record Point(Int x, Int y)

            main(String[] args) {
                var p = Point(1, 2)
                println(p.x)
                println(p.y)
            }
            """;

    private static final String SRC_ARGS = """
            main(String[] args) {
                println(args.length)
                println(args[0])
                println(args[1])
                var s = args[0] + "-" + args[1]
                println(s)
            }
            """;

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
    void wasiEmitsModuleWithWasiImportsAndStart(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var result = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "wasi compile must succeed for the host slice: "
                + result.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out", "Default", "Main.wasm");
        assertTrue(Files.exists(wasm), "Main.wasm must be emitted");
        byte[] bin = Files.readAllBytes(wasm);
        String all = new String(bin, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(all.startsWith("\0asm") && (bin[4] & 0xff) == 1, "wasm magic+version");
        assertTrue(all.contains("wasi_snapshot_preview1"), "WASI import module name");
        assertTrue(all.contains("fd_write") && all.contains("proc_exit"), "preview1 imports");
        assertTrue(all.contains("_start"), "command export");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent — run scripts/provision-wasmtime.sh");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "generated module must pass the official validator: " + vout);
    }

    @Test
    void mainPrintsTheSameStdoutAsTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var result = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "wasi compile: " + result.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out", "Default", "Main.wasm");
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host must answer within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit: " + out);
        assertEquals("3\n-42\n0\n55\ntrue\nfalse\n14\noi\nhello kof\nab\nxyz", out.replaceAll("(?m)^warning:.*$", "")
                .replaceAll("\\n+$", ""), "stdout must equal the JVM oracle");
    }

    @Test
    void argsLengthIndexAndConcatMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC_ARGS);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi args compile: " + wasi.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main", "alpha", "beta"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle must pass args: " + expected);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString(),
                "alpha", "beta"))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host must answer within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit with args: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "args stdout must equal the JVM oracle");
    }

    @Test
    void emptyArgsLengthMatchesJvmAndOobIndexTraps(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println(args.length)
                }
                """);
        var driver = new CompilerDriver();
        assertTrue(driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI)
                .success(), "empty-args compile");
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean exit with zero args: " + out);
        assertEquals("0", out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "args.length == 0 sem argv (JVM oracle)");

        Files.writeString(dir.resolve("Oob.kf"), """
                main(String[] args) {
                    println(args[7])
                }
                """);
        assertTrue(driver.compile(dir.resolve("Oob.kf"), dir.resolve("out-oob"), Target.WASI)
                .success(), "oob compile deve emitir (trap e runtime)");
        var oob = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-oob", "Default", "Oob.wasm").toString()))
                .redirectErrorStream(true).start();
        String oobOut = new String(oob.getInputStream().readAllBytes());
        assertTrue(oob.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertNotEquals(0, oob.exitValue(),
                "indice fora de limites trap deterministico (D-WASM-03 pendente), nunca lixo: " + oobOut);
    }

    @Test
    void recordAllocationAndIntFieldAccessMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC_RECORD_CORE);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle record-core compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle record-core: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi record-core compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit with record-core: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "record alloc + int field access must equal the JVM oracle");
    }

    @Test
    void stringValuesOfVariablesAndConcatMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // 15.3d inc2: String.valueOf(Int|Long) como VALOR (kof.intToStr no bump heap)
        // + concat de handles; paridade byte-a-byte com o oraculo JVM sob wasmtime.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    var s = String.valueOf(3)
                    println(s)
                    println(-42)
                    println(String.valueOf(7) + "!")
                    var n = String.valueOf(100)
                    println("x" + n)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle strings-valueof compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle strings-valueof: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi strings-valueof compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit with strings-valueof: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "String.valueOf/concat must equal the JVM oracle");
    }

    @Test
    void charAndBoolPrintMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // regressao medida na 15.3d inc2: `println(char)` empilhava i32/i64 divergente
        // do helper; o oraculo JVM pinha os dois formatos byte-a-byte sob wasmtime.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                main(String[] args) {
                    println('k')
                    println(true)
                    println(false)
                    println(3)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm char/bool compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM char/bool oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi char/bool compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit char/bool: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "println char/bool must equal the JVM oracle");
    }

    @Test
    void recordFieldWidthsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // 15.3d inc2 fatia B: campos Bool/Char (i32) e String-handle (i32) no slot de
        // 8 bytes do bump heap; leitura + println com paridade byte-a-byte do oraculo JVM.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Pair(String a, Int n)
                record Flag(Char c, Bool ok)
                main(String[] args) {
                    var q = Pair("ab", 7)
                    println(q.a)
                    println(q.n)
                    var f = Flag('k', true)
                    println(f.c)
                    println(f.ok)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm field-widths compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM field-widths oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi field-widths compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit field-widths: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "record Bool/Char/String fields must equal the JVM oracle");
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
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm bool-cond compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM bool-cond oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi bool-cond compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "bool-cond module must pass the official validator: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit bool-cond: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "Bool-variable conditions must equal the JVM oracle");
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
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm chain compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM chain oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi chain compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "chain module must pass the official validator: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit chains: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "logical chains must equal the JVM oracle");
    }

}

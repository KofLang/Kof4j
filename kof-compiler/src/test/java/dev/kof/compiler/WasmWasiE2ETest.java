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

    private static String jvmBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Path host(String tool) {
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
    void recordToStringAndEqualityStillRefuseHonestly(@TempDir Path dir) throws Exception {
        // increment 1 = alloc + int field access (green above). toString/equals/
        // record-in-concat need INSTANCE-method lowering (not built yet) -> honest
        // WASM002 naming plan + slice + #776, NO artifacts (Q7). increment 2 spec.
        var driver = new CompilerDriver();
        record Case(String name, String src) {}
        for (Case c : List.of(
                new Case("record-tostring", "record Point(Int x, Int y)\n"
                        + "main(String[] args) {\n    println(Point(1, 2))\n}\n"),
                new Case("record-equality", "record Point(Int x, Int y)\n"
                        + "main(String[] args) {\n    var p = Point(1, 2)\n    println(p == Point(1, 2))\n}\n"),
                new Case("record-string-field", "record Pair(String a, Int n)\n"
                        + "main(String[] args) {\n    var q = Pair(\"ab\", 7)\n    println(q.n)\n}\n"))) {
            Path src = dir.resolve("Refuse-" + c.name() + ".kf");
            Files.writeString(src, c.src());
            Path out = dir.resolve("out-" + c.name());
            var r = driver.compile(src, out, Target.WASI);
            assertFalse(r.success(), c.name() + " must refuse WASM002 until instance-method lowering");
            String msg = r.diagnostics().getDiagnostics().toString();
            assertTrue(msg.contains("WASM002") && msg.contains("#776")
                    && msg.contains("wasm-wasi-plan"), c.name() + " honest: " + msg);
            assertFalse(Files.exists(out.resolve("Default")), c.name() + ": refusal emits NO artifacts");
        }
    }

    @Test
    void argsAndOutOfSlicePrintRefuseWithNoArtifacts(@TempDir Path dir) throws Exception {
        var driver = new CompilerDriver();
        record Case(String name, String src) {}
        for (Case c : List.of(
                new Case("array-print", "main(String[] args) { println(args) }\n"),
                new Case("iter", "main(String[] args) {\n    for (var a in args) { println(a) }\n}\n"))) {
            Path src = dir.resolve("Refuse-" + c.name() + ".kf");
            Files.writeString(src, c.src().replace("\\n", "\n"));
            Path out = dir.resolve("out-" + c.name());
            var r = driver.compile(src, out, Target.WASI);
            assertFalse(r.success(), c.name() + " must refuse WASM002");
            String msg = r.diagnostics().getDiagnostics().toString();
            assertTrue(msg.contains("WASM002") && msg.contains("#776")
                    && msg.contains("wasm-wasi-plan"), c.name() + " honest: " + msg);
            assertFalse(Files.exists(out.resolve("Default")),
                    c.name() + ": refusal emits NO artifacts");
        }
    }
}

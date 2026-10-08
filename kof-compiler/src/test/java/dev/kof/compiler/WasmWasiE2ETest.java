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
 * 3, -42, 0, 55, true, false, A, 14). Strings/records/collections and args
 * refuse WASM002 naming the plan + slice + #776, with NO artifacts (Q7).
 * The host flip of the frontend default stays unit 15.4.
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
            }
            """;

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
        assertEquals("3\n-42\n0\n55\ntrue\nfalse\n14\noi\nhello kof", out.replaceAll("(?m)^warning:.*$", "")
                .replaceAll("\\n+$", ""), "stdout must equal the JVM oracle");
    }

    @Test
    void stringArgsAndOutOfSlicePrintRefuseWithNoArtifacts(@TempDir Path dir) throws Exception {
        var driver = new CompilerDriver();
        record Case(String name, String src) {}
        for (Case c : List.of(
                new Case("concat", "main(String[] args) { var s = \"a\" + \"b\"\\n    println(s) }\\n"),
                new Case("args", "main(String[] args) { println(args) }\\n"))) {
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

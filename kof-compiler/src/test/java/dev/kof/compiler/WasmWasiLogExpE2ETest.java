package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MATH-LOG-EXP fatia WASM (07/11, #776): the fdlibm {@code __ieee754_log} /
 * {@code __ieee754_exp} kernels close {@code math.log}/{@code math.exp} on
 * the LAST target. Before this slice the WASI emitter refused the call with
 * WASM002 (measured). Now the module emits, wasm-tools validates it, and
 * wasmtime's stdout matches the JVM oracle on the same source.
 *
 * <p>The oracle IS the JVM: the fdlibm port must agree with
 * {@code Math.log}/{@code Math.exp} bit-for-bit on ordinary values; the
 * boundary faces (log(0), log(-1), exp(-Inf)) are the IEEE contract.
 */
class WasmWasiLogExpE2ETest {

    private static final String SRC = """
            main() {
                var l1 = math.log(1.0)
                if (l1 == 0.0) {
                    println("log1-ok")
                } else {
                    println("log1-bad")
                }
                var l10 = math.log(10.0)
                if (l10 > 2.302 && l10 < 2.303) {
                    println("log10-ok")
                } else {
                    println("log10-bad")
                }
                var e0 = math.exp(0.0)
                if (e0 == 1.0) {
                    println("exp0-ok")
                } else {
                    println("exp0-bad")
                }
                var e1 = math.exp(1.0)
                if (e1 > 2.718 && e1 < 2.719) {
                    println("exp1-ok")
                } else {
                    println("exp1-bad")
                }
                var round = math.exp(math.log(42.0))
                if (round > 41.99 && round < 42.01) {
                    println("roundtrip-ok")
                } else {
                    println("roundtrip-bad")
                }
                var neg = math.log(-1.0)
                if (neg != neg) {
                    println("nan-ok")
                } else {
                    println("nan-bad")
                }
            }
            """;

    private static final String EXPECTED =
            "log1-ok\nlog10-ok\nexp0-ok\nexp1-ok\nroundtrip-ok\nnan-ok";

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    /** The RED this slice closes: before the kernels, `math.log` was WASM002. */
    @Test
    void logExpCompilesForWasi(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var result = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "WASI must compile math.log/math.exp (the kernels landed): "
                + result.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out/Default/Main.wasm");
        assertTrue(Files.exists(wasm), "Main.wasm must be emitted");
        String all = new String(Files.readAllBytes(wasm), StandardCharsets.ISO_8859_1);
        assertTrue(all.contains("kof_math_log"), "the log kernel must be in the module");
        assertTrue(all.contains("kof_math_exp"), "the exp kernel must be in the module");
    }

    @Test
    void moduleValidatesWithWasmTools(@TempDir Path dir) throws Exception {
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var result = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "WASI compile: " + result.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out/Default/Main.wasm");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "official validator must accept the log/exp module: " + out);
    }

    @Test
    void stdoutMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var result = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "WASI compile: " + result.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out/Default/Main.wasm");
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host must answer within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit: " + out);
        assertEquals(EXPECTED, out.replaceAll("(?m)^warning:.*$", "").replaceAll("\\n+$", ""),
                "the fdlibm port must agree with the JVM contract");
    }
}

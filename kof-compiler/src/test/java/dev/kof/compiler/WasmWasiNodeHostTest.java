package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Executes the emitted WASI module under a REAL WASI preview1 host, so the
 * backend's run-time behaviour is re-checked on every build instead of resting
 * on a one-off measurement.
 *
 * <p>{@code WasmWasiE2ETest} guards conformance against {@code wasmtime} and
 * {@code wasm-tools}, and SKIPS honestly when those are absent (they are in this
 * environment). Node 18+ ships {@code node:wasi}, a preview1 host, so this test
 * can run where the official tools cannot — it is a SEPARATE, weaker oracle, not
 * a replacement for that test.
 *
 * <p>It asserts three things: the module instantiates under the WASI host, it
 * prints what the Kof source says, and — the part that makes the claim real —
 * the output travels through the {@code fd_write} HOST CALL, counted by wrapping
 * it. Without that last assertion "it printed 42" could come from anywhere.
 */
class WasmWasiNodeHostTest {

    private static final String SRC = """
            Int f() { return 42 }
            main() { println(f()) }
            """;

    private static final String SRC_STRINGS = """
            Int f() { return 1 }
            main() { println("hi"); println(f()) }
            """;

    private static Path node() {
        String home = System.getenv().getOrDefault("KOF_NODE_HOME", "/usr/bin");
        Path p = Path.of(home, "node");
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void theWasiModuleRunsUnderARealWasiHostAndPrints(@TempDir Path dir) throws Exception {
        Path n = node();
        assumeTrue(n != null, "node absent — cannot run the WASI host oracle (environment)");

        Path wasm = compileWasi(dir, SRC);
        String out = runUnderWasiHost(n, dir, wasm);
        assertTrue(out.contains("42"), "the WASI host must print what the Kof source computes:\n" + out);
        assertTrue(out.contains("FD_WRITE_CALLS=1"),
                "output must travel through the fd_write HOST CALL exactly once "
                        + "(this is what makes 'it printed' real):\n" + out);
    }

    @Test
    void aStringProgramAlsoPrintsThroughTheHost(@TempDir Path dir) throws Exception {
        Path n = node();
        assumeTrue(n != null, "node absent — cannot run the WASI host oracle (environment)");

        Path wasm = compileWasi(dir, SRC_STRINGS);
        String out = runUnderWasiHost(n, dir, wasm);
        assertTrue(out.contains("hi"), "string println through WASI:\n" + out);
        assertTrue(out.contains("1"), "the int println after it:\n" + out);
    }

    private static Path compileWasi(Path dir, String source) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), source);
        var result = new CompilerDriver().compile(
                dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "wasi compile: " + result.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out").resolve("Default").resolve("Main.wasm");
        assertTrue(Files.isRegularFile(wasm), "Main.wasm must be emitted");
        return wasm;
    }

    /**
     * Runs the module under {@code node:wasi}, instrumenting {@code fd_write} so
     * the host-call count is part of the output the assertions read.
     */
    private static String runUnderWasiHost(Path node, Path dir, Path wasm) throws Exception {
        Path script = dir.resolve("run.mjs");
        Files.writeString(script, """
                import fs from 'node:fs';
                import { WASI } from 'node:wasi';
                const wasi = new WASI({ version: 'preview1', args: [], env: {} });
                let calls = 0;
                const real = wasi.wasiImport.fd_write;
                wasi.wasiImport.fd_write = (fd, iovs, n, written) => { calls++; return real(fd, iovs, n, written); };
                const mod = await WebAssembly.compile(fs.readFileSync(process.argv[2]));
                const inst = await WebAssembly.instantiate(mod, { wasi_snapshot_preview1: wasi.wasiImport });
                wasi.start(inst);
                console.error('FD_WRITE_CALLS=' + calls);
                """);
        ProcessBuilder pb = new ProcessBuilder(node.toString(), script.toString(), wasm.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "wasm run must not hang:\n" + out);
        return out;
    }
}

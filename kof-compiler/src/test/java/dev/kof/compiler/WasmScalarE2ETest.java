package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TIER 15 unit 15.2 (#776, D-WEB-WASI-DEFAULT-0710, D-WASM-01/02) — first
 * emitting slice: SCALAR top-level functions lower to a real WebAssembly
 * binary module (`Main.wasm`, direct backend, Int=i64; WAT stays a unit-15.3+ tool — plan §25). The runtime
 * face is executed under the vendored wasmtime host
 * (scripts/provision-wasmtime.sh → ~/.local/share/kof-wasm/bin) and checked
 * against the JVM oracle of the SAME source. Anything outside the scalar
 * subset refuses honestly with WASM002 — never a silent fallback (R6/Q7).
 */
class WasmScalarE2ETest {

    private static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    private record WasmFace(Path wasm) {}

    private static WasmFace compileWasm(String source, Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), source);
        var driver = new CompilerDriver();
        var result = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASM);
        assertTrue(result.success(), "wasm compile must succeed for the scalar subset: "
                + result.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out", "Default", "Main.wasm");
        assertTrue(Files.exists(wasm), "Main.wasm must be emitted");
        return new WasmFace(wasm);
    }

    private static String invoke(Path wasmtime, Path wasm, String fn, String... args)
            throws Exception {
        var cmd = new java.util.ArrayList<String>();
        cmd.add(wasmtime.toString());
        cmd.add("run");
        cmd.add("--invoke");
        cmd.add(fn);
        cmd.add(wasm.toString());
        cmd.addAll(List.of(args));
        var proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host must answer within 60s");
        assertEquals(0, proc.exitValue(), "wasmtime exit must be 0: " + out);
        return out.replaceAll("(?m)^warning:.*$", "").trim();
    }

    private static void runJvm(String source, Path dir, String expected) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), source);
        var driver = new CompilerDriver();
        var r = driver.compile(dir.resolve("Main.kf"), dir.resolve("jvm"), Target.JVM);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        var cp = System.getProperty("java.class.path");
        var proc = new ProcessBuilder(javaBin(), "-cp",
                dir.resolve("jvm") + ":" + cp, "Default.Main").redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, proc.exitValue(), out);
        assertEquals(expected, out.trim());
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Test
    void scalarAddEmitsValidatableModule(@TempDir Path dir) throws Exception {
        var face = compileWasm("""
                Int add(Int a, Int b) { return a + b }
                """, dir);
        // binary must start with the wasm magic + version (0x00 0x61 0x73 0x6d 0x01)
        byte[] bin = Files.readAllBytes(face.wasm());
        assertEquals("\0asm", new String(bin, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
        assertEquals(1, bin[4] & 0xff);
        String all = new String(bin, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(all.contains("add"), "export name must appear in the module bytes");
        var wasmTools = host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmTools != null,
                "wasm-tools absent — run scripts/provision-wasmtime.sh (recorded host gap)");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate", face.wasm().toString())
                .redirectErrorStream(true).start();
        assertEquals(0, proc.waitFor(60, TimeUnit.SECONDS) ? proc.exitValue() : -1,
                "generated module must pass the official validator");
    }

    @Test
    void scalarFunctionsExecuteUnderWasmtimeMatchingJvmOracle(@TempDir Path dir) throws Exception {
        String src = """
                Int add(Int a, Int b) { return a + b }
                Long sq(Long x) { return x * x }
                Double ratio(Double a, Double b) { return a / b }
                Bool even(Int n) { return n % 2 == 0 }
                Int collatz(Int n) {
                    var s = 0
                    while (n > 1) {
                        if (n % 2 == 0) { n = n / 2 } else { n = 3 * n + 1 }
                        s = s + 1
                    }
                    return s
                }
                Int fib(Int n) {
                    if (n < 2) { return n }
                    return fib(n - 1) + fib(n - 2)
                }
                Long pick(Int flag, Long a, Long b) {
                    var out = b
                    if (flag > 0) { out = a }
                    return out
                }
                """;
        var face = compileWasm(src, dir);
        var wasmtime = host("wasmtime");
        var wasmTools = host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null,
                "wasm host absent — run scripts/provision-wasmtime.sh (recorded host gap)");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", face.wasm().toString())
                .redirectErrorStream(true).start();
        assertEquals(0, v.waitFor(60, TimeUnit.SECONDS) ? v.exitValue() : -1);
        // behavior under the host
        assertEquals("42", invoke(wasmtime, face.wasm(), "add", "40", "2"));
        assertEquals("-7", invoke(wasmtime, face.wasm(), "add", "-3", "-4"));
        assertEquals("49", invoke(wasmtime, face.wasm(), "sq", "7"));
        assertEquals("111", invoke(wasmtime, face.wasm(), "collatz", "27"));
        assertEquals("55", invoke(wasmtime, face.wasm(), "fib", "10"));
        assertEquals("1", invoke(wasmtime, face.wasm(), "even", "8"));
        assertEquals("0", invoke(wasmtime, face.wasm(), "even", "7"));
        assertEquals("3", invoke(wasmtime, face.wasm(), "pick", "1", "3", "4"));
        assertEquals("4", invoke(wasmtime, face.wasm(), "pick", "0", "3", "4"));
        // ratio: f64 print is the host's — compare against a fixed digit form
        assertEquals("2.5", invoke(wasmtime, face.wasm(), "ratio", "5", "2"));
        // JVM oracle on the SAME sources (parity by construction, §27)
        Path jdir = dir.resolve("jvmface");
        Files.createDirectories(jdir);
        runJvm(src + "\nmain() { println(add(40, 2)); println(sq(7)); println(collatz(27));"
                + " println(fib(10)); println(even(8)); println(even(7)); println(pick(1, 3, 4));"
                + " println(ratio(5.0, 2.0)) }",
                jdir, "42\n49\n111\n55\ntrue\nfalse\n3\n2.5");
    }

    @Test
    void outsideTheScalarSubsetRefusesHonestlyWASM002(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), """
                Int echo(Int x) { println(x); return x }
                """);
        var driver = new CompilerDriver();
        var r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASM);
        assertFalse(r.success(), "WASM002: println is not in unit 15.2's scalar subset");
        String msg = r.diagnostics().getDiagnostics().toString();
        assertTrue(msg.contains("WASM002"), msg);
        assertTrue(msg.contains("wasm-wasi-plan"), msg);
        assertTrue(msg.contains("#776"), msg);
        assertFalse(Files.exists(Path.of(dir.toString(), "out", "Default", "Main.wasm")),
                "an honest refusal must leave no artifact (never a partial module)");
    }
}

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
 * TIER 15 fase 4 slice CLOCK+RANDOM (#776, D-WASM-GO): as capabilities de
 * host WASI-preview1 `clock_time_get`/`random_get` com runtimes de módulo.
 * `time.now()` devolve epoch-millis REALTIME (a face do JVM
 * `System.currentTimeMillis`), `random.double/boolean/int` seguem a matriz
 * stdrandom (53-bit mantissa em [0,1); bit do 1º byte; rejection sampling
 * uniforme em [0,bound), bound<=0 => 0). O clock não tem oráculo
 * byte-a-byte (o momento varia) — a PARIDADE é a FAIXA (>= a época do
 * build, nunca o zero da falha) + a forma (Long); randomNUmberNELA o
 * determinismo é a PROPRIEDADE (o domínio [0,bound), nunca valores fora).
 */
class WasmWasiCapsE2ETest {

    private static final String SRC_TIME = """
            main(String[] args) {
                var t1 = time.now()
                println(t1 > 0)
                var t2 = time.now()
                println(t2 >= t1)
            }
            """;

    private static final String SRC_RANDOM = """
            Int hits(Int bound, Int tries) {
                var ok = 0
                var i = 0
                while (i < tries) {
                    var v = random.int(bound)
                    if (v >= 0 && v < bound) { ok = ok + 1 }
                    i = i + 1
                }
                return ok
            }

            main(String[] args) {
                var inRange = true
                var i = 0
                while (i < 500) {
                    var d = random.double()
                    if (d < 0.0 || d >= 1.0) { inRange = false }
                    i = i + 1
                }
                println(inRange)
                var boolSum = 0
                i = 0
                while (i < 50) {
                    var b = random.boolean()
                    if (b) { boolSum = boolSum + 1 } else { boolSum = boolSum - 1 }
                    i = i + 1
                }
                if (boolSum <= 50 && boolSum >= -50) { println(true) } else { println(false) }
                println(hits(7, 500) == 500)
                println(random.int(0))
                println(random.int(-3))
            }
            """;

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void wasiModuleDeclaresOnlyTheCapsItUses(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), SRC_TIME);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "time.now() compile: " + r.diagnostics().getDiagnostics());
        String raw = new String(Files.readAllBytes(
                Path.of(dir.toString(), "out", "Default", "Main.wasm")),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("clock_time_get"), "the clock import must be declared");
        assertFalse(raw.contains("random_get"), "no unused random import (honest import set)");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent — run scripts/provision-wasmtime.sh");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate",
                Path.of(dir.toString(), "out", "Default", "Main.wasm").toString())
                .redirectErrorStream(true).start();
        String vout = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "the capability module must validate: " + vout);
    }

    @Test
    void timeNowReturnsMonotonicEpochMillisUnderWasmtime(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC_TIME);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "time.now() compile: " + r.diagnostics().getDiagnostics());
        var proc = runWasmtime(wasmtime, Path.of(dir.toString(), "out", "Default", "Main.wasm"));
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);
        assertEquals("true\ntrue", out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "epoch-millis > 0 e nao-decrescente (REALTIME; a paridade com a JVM e a FAIXA)");
    }

    @Test
    void randomFacesObeyTheStdrandomDomainLaw(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC_RANDOM);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "random compile: " + wasi.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(WasmWasiE2ETest.jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle must run: " + expected);
        var proc = runWasmtime(wasmtime, Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm"));
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);
        // o DOMINIO e a lei cross-target (determinista); os valores variam
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "500 doubles em [0,1), bools, 500 ints em [0,7), bound<=0 => 0 — igual a JVM");
    }

    @Test
    void unusedRandomFacesDeclareNoExtraImports(@TempDir Path dir) throws Exception {
        // a fase-4 RANDOM-STR (a fatia que aloca String) landed depois desta
        // fatia: o `random.hex` nao recusa mais — mas o programa que usa SO o
        // `random.int` nao pode declarar os runtimes das faces de String (o
        // conjunto de imports honesto — o host recusa o import nao concedido)
        Files.writeString(dir.resolve("Hex.kf"),
                "main(String[] args) { println(random.int(4)) }\n",
                java.nio.charset.StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Hex.kf"),
                dir.resolve("out-hex"), Target.WASI);
        assertTrue(r.success(), "random.int compila: " + r.diagnostics().getDiagnostics());
        String raw = new String(Files.readAllBytes(
                Path.of(dir.toString(), "out-hex", "Default", "Main.wasm")),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("kof_random_hex"), "a face de hex nao entra sem uso");
        assertFalse(raw.contains("kof_random_string"), "a face de randomString nao entra sem uso");
    }

    private static Process runWasmtime(Path wasmtime, Path wasm) throws Exception {
        return new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
    }
}

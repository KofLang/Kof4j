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
 * O descarte de valor de uma chamada de INSTÂNCIA dentro de um `if` produzia
 * um módulo WASI INVÁLIDO: o `KofPop` do desugar dava `drop` no resultado mas
 * NÃO limpava o rastro `ctx.lastPush` — então o `crossLiveValue` da fronteira
 * de bloco acreditava que o valor ainda estava NA PILHA e emitia o
 * `local.set` do join SEM nada abaixo (o validador: "expected i32 but nothing
 * on stack"). O `KofStoreLocal` já limpava o rastro pelo mesmo motivo ("o
 * store CONSUME o valor"); o `KofPop` não.
 *
 * <p>A chamada ESTATICA (`random.int(3)`) nunca acusava porque o rastro dela
 * era WIDE (o literal Int sai i64 e o `isWide` só via long/double) — o
 * `!ctx.lastPushWide` excluía o caso comum. Medido em `/tmp/iso7` (a
 * classe-sonda m1).
 *
 * <p>O caso também prova o app desktop ponta a ponta: o método é chamado
 * dentro do `if` e o programa segue rodando (o oracle do stdout).
 */
class WasmInstanceCallInIfE2ETest {

    private static final String SRC = """
            class Ticker {
                Int v() { return 7 }
            }

            main(String[] args) {
                val t = Ticker()
                if (true) { t.v() }
                println("ok")
                if (args.length > 0) { t.v() }
                println("fim")
            }
            """;

    static Path hostTool(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void anInstanceCallInsideAnIfEmitsAValidModule(@TempDir Path dir) throws Exception {
        var wasmtime = hostTool("wasmtime");
        var wasmTools = hostTool("wasm-tools");
        assumeTrue(wasmtime != null && wasmTools != null,
                "wasmtime/wasm-tools host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC, StandardCharsets.UTF_8);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "compile: " + wasi.diagnostics().getDiagnostics());
        Path wasm = Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm");
        var v = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "o modulo com chamada de instancia dentro de um if deve validar: " + vout);
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run", wasm.toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);
        assertEquals("ok\nfim", out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "o programa segue rodando DEPOIS do descarte da chamada no if");
    }
}

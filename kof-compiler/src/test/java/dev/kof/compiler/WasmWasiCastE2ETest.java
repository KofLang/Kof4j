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
 * As CONVERSOES escalares no WASI (medido 22/11 — TODAS recusavam).
 *
 * <p>O {@code emitUnary} do backend WASI so' tratava {@code NEG} e {@code NOT};
 * todo {@code as} de primitivo caia na recusa {@code WASM002}. A lei das
 * larguras do backend: Int/Long/Char viajam em i64 na pilha, Bool em i32,
 * Double em f64 — entao so' emite instrucao a conversao cuja largura DIFERE
 * (as de mesma largura sao identidade).
 *
 * <p>Dois bugs que a prova pegou (ambos meus, no mesmo commit):
 * <ol>
 *   <li>o {@code operandType()} de um {@code KofUnary} e' o tipo do OPERANDO,
 *       nao o do RESULTADO — usar ele no rastro {@code lastPush} fazia o
 *       {@code println} despachar o helper i64 sobre um f64 (modulo INVALIDO);
 *       o rastro agora sai de {@code resultTypeName};</li>
 *   <li>o opcode {@code f64.convert_i64_s} e' <b>0xB9</b>, nao 0xB7 (que e'
 *       {@code convert_i32_s}) — o validador do wasmtime recusou com "expected
 *       i32, found i64" e a medicao corrigiu.</li>
 * </ol>
 *
 * <p>A PROVA e' a paridade com o oraculo JVM: o mesmo fonte, o mesmo stdout.
 */
class WasmWasiCastE2ETest {

    static Path hostTool(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    private static final String SRC = """
            main() {
                val c = 65 as Char
                println(c)
                val l = 5 as Long
                println(l)
                val d = 1.5
                val i = d as Int
                println(i)
                val n = 7
                val dd = n as Double
                println(dd)
                val ch = 'A'
                println(ch as Int)
                val neg = -3 as Double
                println(neg)
                val big = 1000000 as Double
                println(big)
            }
            """;

    @Test
    void everyScalarConversionMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = hostTool("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC, StandardCharsets.UTF_8);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("w"), Target.WASI);
        assertTrue(wasi.success(), "WASI compile: " + wasi.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("j"), Target.JVM);
        assertTrue(jvm.success(), "JVM oracle compile: " + jvm.diagnostics().getDiagnostics());

        var oracle = new ProcessBuilder(List.of(WasmWasiE2ETest.jvmBin(), "-cp",
                dir.resolve("j").toString(), "Default.Main")).redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle must run: " + expected);

        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                dir.resolve("w/Default/Main.wasm").toString())).redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);

        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "toda conversao escalar deve bater com o oraculo JVM")
        ;
    }

    @Test
    void aConversionInsideImpliesAValidModule(@TempDir Path dir) throws Exception {
        // o rastro do RESULTADO (nao do operando) e' o que o println le
        Files.writeString(dir.resolve("Main.kf"),
                "main() { val i = 7; println(i as Double) }\n", StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"),
                dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var wasmTools = hostTool("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent");
        var v = new ProcessBuilder(wasmTools.toString(), "validate",
                dir.resolve("out/Default/Main.wasm").toString())
                .redirectErrorStream(true).start();
        String vout = new String(v.getInputStream().readAllBytes());
        assertTrue(v.waitFor(60, TimeUnit.SECONDS) && v.exitValue() == 0,
                "o modulo com conversao deve validar: " + vout);
    }
}

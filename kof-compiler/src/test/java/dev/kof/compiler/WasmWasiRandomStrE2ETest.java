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
 * TIER 15 fase 4 slice RANDOM-STR (#776): as faces de {@code kof.random} que
 * ALOCAM String — {@code random.hex(n)} (2n dígitos hex minúsculo; n&lt;=0 =>
 * null) e {@code random.randomString(n, alphabet)} (n chars uniformes do
 * alfabeto; n&lt;=0 OU alfabeto vazio => "").
 *
 * <p>Os contratos são os do oráculo JVM ({@code JvmStringRandomRuntime}:
 * {@code kof_random_hex} delega a {@code kof_sec_random_hex}; o
 * {@code kof_random_string} monta n chars com {@code nextInt(len)}), medidos
 * no binário: {@code random.hex(0) == null} é true e
 * {@code random.randomString(0, "abc").length()} é 0 (a String VAZIA, não o
 * null). A ENTROPIA não é comparável entre alvos (o {@code random_get} do
 * WASI e o {@code SecureRandom} da JVM são fontes diferentes), então a prova
 * é a LEI observável, byte-a-byte no FORMATO: o comprimento, o alfabeto de
 * dígitos, a nulidade — nunca o valor sorteado.
 */
class WasmWasiRandomStrE2ETest {

    private static final String SRC = """
            main(String[] args) {
                val h = random.hex(4)
                println(h.length())
                var ok = true
                var i = 0
                while (i < h.length()) {
                    val c = h.charAt(i)
                    if (c != '0' && c != '1' && c != '2' && c != '3' && c != '4'
                        && c != '5' && c != '6' && c != '7' && c != '8' && c != '9'
                        && c != 'a' && c != 'b' && c != 'c' && c != 'd' && c != 'e'
                        && c != 'f') { ok = false }
                    i = i + 1
                }
                println(ok)
                println(random.hex(0) == null)
                println(random.hex(-1) == null)
                val s = random.randomString(10, "abc")
                println(s.length())
                var inAlpha = true
                i = 0
                while (i < s.length()) {
                    val c = s.charAt(i)
                    if (c != 'a' && c != 'b' && c != 'c') { inAlpha = false }
                    i = i + 1
                }
                println(inAlpha)
                println(random.randomString(0, "abc").length())
                println(random.randomString(5, "").length())
                println(random.randomBytesHex(2).length())
            }
            """;

    static Path hostTool(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void theRandomStringFacesObeyTheJvmContract(@TempDir Path dir) throws Exception {
        var wasmtime = hostTool("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC, StandardCharsets.UTF_8);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "random.hex/string compile: "
                + wasi.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle compile: " + jvm.diagnostics().getDiagnostics());

        var oracle = new ProcessBuilder(List.of(WasmWasiE2ETest.jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle must run: " + expected);

        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);

        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "o FORMATO das faces (comprimento / alfabeto / nulidade) e' o do oráculo JVM");
    }

    @Test
    void aProgramWithoutTheRandomStringFacesDeclaresNoHeap(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main(String[] args) { println(random.int(7)) }\n", StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"),
                dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "random.int compile: " + r.diagnostics().getDiagnostics());
        String raw = new String(Files.readAllBytes(
                Path.of(dir.toString(), "out", "Default", "Main.wasm")),
                StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("kof_random_hex"), "a face de hex nao entra sem uso");
        assertFalse(raw.contains("kof_random_string"), "a face de randomString nao entra sem uso");
        var wasmTools = hostTool("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate",
                Path.of(dir.toString(), "out", "Default", "Main.wasm").toString())
                .redirectErrorStream(true).start();
        String vout = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "the module must validate: " + vout);
    }
}

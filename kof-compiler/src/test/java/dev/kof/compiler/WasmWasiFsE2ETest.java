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
 * TIER 15 fase 4 slice FILESYSTEM (#776, D-WASM-GO): as faces de ARQUIVO do
 * {@code kof.io} no host WASI-preview1 — {@code exists}/{@code isFile}/
 * {@code isDirectory}/{@code readText}/{@code writeText} sobre o
 * {@code path_filestat_get}/{@code path_open}/{@code fd_read}/{@code
 * fd_write} do preview1.
 *
 * <p>A SEMANTICA do host (medida, não inventada): o preview1 resolve o path
 * RELATIVO ao diretorio pre-aberto (o fd 3) e recusa o path absoluto (errno
 * 63 = EPERM) — o runtime tira o "/" inicial, e o path resolve dentro da
 * sandbox que o host concedeu.
 *
 * <p>A PARIDADE com o JVM e BYTE-A-BYTE: o mesmo fonte roda na JVM (o
 * oráculo, com o {@code kof_io_*} do `KofRuntime`) e no WASI (o runtime do
 * host) — a saida tem de ser identica, inclusive o {@code null} do
 * {@code readText} de um arquivo ausente.
 */
class WasmWasiFsE2ETest {

    private static final String SRC = """
            main(String[] args) {
                var f = File("nota.txt")
                println(f.exists())
                println(f.writeText("ola kof"))
                println(f.exists())
                println(f.readText())
                println(File("ausente.txt").exists())
                println(File("ausente.txt").readText())
            }
            """;

    static Path host(String tool) {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void kofIoFacesMatchTheJvmOracleByteForByte(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var driver = new CompilerDriver();
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi io compile: " + wasi.diagnostics().getDiagnostics());
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm oracle compile: " + jvm.diagnostics().getDiagnostics());

        // o oráculo JVM roda no seu diretorio (o path e' RELATIVO)
        Path jvmDir = Files.createDirectories(dir.resolve("jvm-run"));
        var oracle = new ProcessBuilder(List.of(WasmWasiE2ETest.jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .directory(jvmDir.toFile()).redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM oracle must run: " + expected);

        // o modulo WASI roda com o preopen = o seu diretorio (o --dir vem
        // ANTES do arquivo: e' uma opcao do host, nao um arg do modulo)
        Path wasiDir = Files.createDirectories(dir.resolve("wasi-run"));
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                "--dir", wasiDir.toString(),
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(120, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "clean WASI exit: " + out);

        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "the kof.io faces must be byte-identical to the JVM oracle");

        // o arquivo foi REALMENTE gravado no preopen, com o conteudo certo
        Path written = wasiDir.resolve("nota.txt");
        assertTrue(Files.exists(written), "writeText must create the file inside the preopen");
        assertEquals("ola kof", Files.readString(written, StandardCharsets.UTF_8),
                "the file content must be exactly what the program wrote");
    }

    @Test
    void theModuleValidatesAndDeclaresOnlyTheIoImports(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), SRC);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "wasi io compile: " + r.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out").resolve("Default").resolve("Main.wasm");
        String raw = new String(Files.readAllBytes(wasm), StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("path_open") && raw.contains("path_filestat_get")
                        && raw.contains("fd_read") && raw.contains("fd_close"),
                "the io imports must be declared");
        assertFalse(raw.contains("clock_time_get") && raw.contains("random_get"),
                "a module that uses no clock/random must not declare those imports");
        var wasmTools = host("wasm-tools");
        assumeTrue(wasmTools != null, "wasm-tools host absent — run scripts/provision-wasmtime.sh");
        var proc = new ProcessBuilder(wasmTools.toString(), "validate", wasm.toString())
                .redirectErrorStream(true).start();
        String vout = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0,
                "the io module must validate: " + vout);
    }

    @Test
    void aProgramWithoutIoDeclaresNoIoImports(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Plain.kf"), """
                main(String[] args) {
                    println(42)
                }
                """);
        var r = new CompilerDriver().compile(dir.resolve("Plain.kf"),
                dir.resolve("out"), Target.WASI);
        assertTrue(r.success(), "plain compile: " + r.diagnostics().getDiagnostics());
        String raw = new String(Files.readAllBytes(
                dir.resolve("out").resolve("Default").resolve("Main.wasm")),
                StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("path_open"), "no unused io import (honest import set)");
        assertFalse(raw.contains("path_filestat_get"), "no unused io import (honest import set)");
    }
}

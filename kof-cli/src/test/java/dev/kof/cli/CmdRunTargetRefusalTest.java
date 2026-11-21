package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Q7: `kof run` refuses the targets it cannot EXECUTE, honestly and early.
 *
 * <p>MEASURED before the guard: {@code --target wasm} and {@code --target wasi}
 * both fell through to the JVM class lookup and printed the bare
 * "no main class found" — a message that names no cause at all. The refusal
 * mirrors the {@code CmdTest}/{@code CmdScript} precedent and points at the real
 * route ({@code kof build --target wasi} plus a WASI runtime).
 *
 * <p>THE WASI HALF CHANGED (the WASI runner, TIER 15): a WASI module is a
 * COMMAND module (it exports {@code _start}), so a WASI host runs it directly —
 * the same shape the native target has. {@code kof run --target wasi} now
 * EXECUTES when a host is present (the parity with the JVM oracle is the
 * {@code CmdRunWasiE2ETest}'s job) and refuses HONESTLY — naming the missing
 * host — when there is none. The {@code wasm} target still refuses: it emits a
 * reactactor module (no {@code _start}), which needs an embedder, not a runner.
 */
class CmdRunTargetRefusalTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "the CLI itself must not hang\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static void assertRunRefusesHonestly(Path dir, String target) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "Int sq(Int n) { return n * n }\nmain() { println(sq(7)) }\n");
        Cli r = cli(dir, "run", src.toString(), "--target", target);
        assertEquals(1, r.exit(), target + " must fail explicitly:\n" + r.out());
        assertTrue(r.out().contains("cannot be executed by 'kof run'"),
                target + " names the refusal:\n" + r.out());
        assertTrue(r.out().contains("kof build --target " + target),
                target + " points at the build route:\n" + r.out());
        assertFalse(r.out().contains("no main class found"),
                target + " must not print the opaque JVM message:\n" + r.out());
    }

    @Test
    void runWasmIsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertRunRefusesHonestly(dir, "wasm");
    }

    @Test
    void runWasiExecutesWhenAHostIsPresent(@TempDir Path dir) throws Exception {
        // o alvo WASI agora EXECUTA (a fatia do runner): um modulo WASI e' um
        // modulo de comando (_start) e um host WASI o roda direto. A recusa
        // honesta ficou para o caso SEM host (o `WasiRunner.hostAvailable`).
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "Int sq(Int n) { return n * n }\nmain() { println(sq(7)) }\n");
        Cli r = cli(dir, "run", src.toString(), "--target", "wasi");
        if (r.exit() == 0) {
            assertTrue(r.out().contains("49"),
                    "wasi must print the result when a host is present:\n" + r.out());
        } else {
            // sem host neste machine: a recusa tem que NOMEAR o host que falta
            assertTrue(r.out().contains("needs a WASI host"),
                    "the refusal names the missing host:\n" + r.out());
        }
    }

    @Test
    void runJvmStillExecutes(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "Int sq(Int n) { return n * n }\nmain() { println(sq(7)) }\n");
        Cli r = cli(dir, "run", src.toString(), "--target", "jvm");
        assertEquals(0, r.exit(), "jvm must still run:\n" + r.out());
        assertTrue(r.out().contains("49"), "jvm must still print the result:\n" + r.out());
    }

    @Test
    void runScriptTargetStillExecutes(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "Int sq(Int n) { return n * n }\nmain() { println(sq(7)) }\n");
        Cli r = cli(dir, "run", src.toString(), "--target", "script");
        assertEquals(0, r.exit(), "the script target must still run:\n" + r.out());
        assertTrue(r.out().contains("49"), "script must still print the result:\n" + r.out());
    }
}

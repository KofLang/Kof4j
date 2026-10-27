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
    void runWasiIsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertRunRefusesHonestly(dir, "wasi");
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

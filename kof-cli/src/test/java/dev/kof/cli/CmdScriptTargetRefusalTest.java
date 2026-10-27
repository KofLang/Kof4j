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
 * Q7: `kof script` refuses targets it cannot EXECUTE, honestly and early.
 *
 * <p>MEASURED before the guard: {@code --target wasi} printed the raw
 * "Não foi possível localizar nem carregar a classe principal Default.Main" and
 * {@code --target wasm} a compiler diagnostic ({@code WASM002}) — neither named
 * the real problem, which is that this command compiles and then looks for a
 * runnable artifact that these targets do not produce. The refusal mirrors the
 * {@code CmdTest} precedent and points at the working targets (and at
 * {@code kof build}, which DOES emit the module).
 */
class CmdScriptTargetRefusalTest {

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
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "the CLI itself must not hang\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static void assertScriptRefusesHonestly(Path dir, String target) throws Exception {
        Path src = dir.resolve("s.ks");
        Files.writeString(src, "println(\"hi\")\n");
        Cli r = cli(dir, "script", src.toString(), "--target", target);
        assertEquals(1, r.exit(), target + " must fail explicitly:\n" + r.out());
        assertTrue(r.out().contains("cannot be executed by 'kof script'"),
                target + " names the refusal:\n" + r.out());
        assertTrue(r.out().contains("kof build --target " + target),
                target + " points at the build route:\n" + r.out());
        assertFalse(r.out().contains("N\u00e3o foi poss\u00edvel localizar"),
                target + " must not leak a raw class-loader error:\n" + r.out());
        assertFalse(r.out().contains("WASM002"),
                target + " must not surface an internal compile code:\n" + r.out());
    }

    @Test
    void scriptWasmIsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertScriptRefusesHonestly(dir, "wasm");
    }

    @Test
    void scriptWasiIsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertScriptRefusesHonestly(dir, "wasi");
    }

    @Test
    void scriptJvmStillRuns(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("s.ks");
        Files.writeString(src, "println(\"hi\")\n");
        Cli r = cli(dir, "script", src.toString(), "--target", "jvm");
        assertEquals(0, r.exit(), "jvm must still run:\n" + r.out());
        assertTrue(r.out().contains("hi"), "jvm must still print:\n" + r.out());
    }

    @Test
    void scriptOwnTargetStillRuns(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("s.ks");
        Files.writeString(src, "println(\"hi\")\n");
        Cli r = cli(dir, "script", src.toString(), "--target", "script");
        assertEquals(0, r.exit(), "the script target must still run:\n" + r.out());
        assertTrue(r.out().contains("hi"), "script must still print:\n" + r.out());
    }
}

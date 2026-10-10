package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `kof test` refuses the cross native targets honestly. The runner executes the
 * HOST binary and the cross test harness does not link {@code kof_process_exit},
 * so `--target native.risc`/`native.arm` used to be accepted, compiled, and then
 * died with a raw `riscv64-ld: undefined reference ... [COMP001]` — false
 * support (R6/Q7). The refusal mirrors the `--target android` precedent and
 * points at the real route (the compiler E2E suite under qemu, or
 * {@code kof build} + qemu). {@link CmdTestSuiteTest} pins the runnable targets.
 */
class CmdTestCrossTargetRefusalTest {

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
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o proprio CLI nao pode hangar\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static void assertRefusedHonestly(Path dir, String target) throws Exception {
        Files.writeString(dir.resolve("Calc.kf"),
                "test \"soma\", \"smoke\" {\n    assert(2 + 2 == 4)\n}\n");
        Cli r = cli(dir, "test", dir.resolve("Calc.kf").toString(), "--target", target);
        assertEquals(1, r.exit(), target + " must fail explicitly (not a test target):\n" + r.out());
        assertTrue(r.out().contains("is not a test target"), target + " names the refusal:\n" + r.out());
        assertTrue(r.out().contains("jvm|native|js"), target + " points at the runnable targets:\n" + r.out());
        assertFalse(r.out().contains("undefined reference"),
                target + " must refuse BEFORE compiling (no raw linker error):\n" + r.out());
        assertFalse(r.out().contains("COMP001"),
                target + " must not leak the internal COMP001 code:\n" + r.out());
    }

    @Test
    void nativeRiscv64IsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertRefusedHonestly(dir, "native.risc");
    }

    @Test
    void nativeAarch64IsRefusedHonestly(@TempDir Path dir) throws Exception {
        assertRefusedHonestly(dir, "native.arm");
    }
}

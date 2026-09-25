package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-FULL-PARITY-050 row 2 — {@code shell.run}/{@code cmd}/{@code ok} no CROSS
 * riscv64/aarch64. {@code run} reusa {@code kof_process_run} (row 1 slice C);
 * {@code cmd} usa {@code kof_shell_argv} ({@link dev.kof.compiler.nat.NativeRiscvAsmShell});
 * {@code ok} é IR puro sobre o accessor {@code exitCode}. Golden byte-a-byte
 * contra o oráculo JVM.
 */
class ShellCrossE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String capture(Process p) throws IOException, InterruptedException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, p.waitFor(), "exit code, output: " + out);
        return out;
    }

    private String runJvm(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runCross(Path src, Path out, String arch, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private void assertAllTargets(Path tmp, String tag, String program, String expected)
            throws IOException {
        Path src = tmp.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        assertEquals(expected, runJvm(src, tmp.resolve(tag + "-jvm")), "JVM oracle " + tag);
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(expected, runCross(src, tmp.resolve(tag + "-rv"),
                "riscv64", Target.NATIVE_RISCV64), "riscv64 " + tag);
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(expected, runCross(src, tmp.resolve(tag + "-aa"),
                "aarch64", Target.NATIVE_AARCH64), "aarch64 " + tag);
    }

    @Test
    void shellRunMatchesJvmGolden(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "run", """
            main() {
                var r = shell.run("echo", listOf("hi"))
                println(r.stdout)
                println(r.exitCode)
            }
            """, "hi\n\n0\n");
    }

    @Test
    void shellCmdBuildsArgvPrepend(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "cmd", """
            main() {
                var a = shell.cmd("echo", listOf("hi"))
                println(a.size())
                println(a.get(0))
                println(a.get(1))
            }
            """, "2\necho\nhi\n");
    }

    @Test
    void shellOkReadsExitCode(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "ok", """
            main() {
                var r0 = shell.run("sh", listOf("-c", "exit 0"))
                var r3 = shell.run("sh", listOf("-c", "exit 3"))
                println(shell.ok(r0))
                println(shell.ok(r3))
            }
            """, "true\nfalse\n");
    }

    /** Slice B1: runWith with the inherited cwd ("") + empty env is the argv-first
     *  spawn — byte-parity with the JVM oracle. */
    @Test
    void runWithInheritedCwdEnvMatchesJvm(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "rw", """
            main() {
                var r = shell.runWith(shell.cmd("echo", listOf("vivo")), "", mapOf())
                println(r.stdout)
                println(r.exitCode)
            }
            """, "vivo\n\n0\n");
    }

    /** Slice B1: a NON-EMPTY cwd (or env) is never silently ignored (R6) — the
     *  cross returns an honest Result failure (exitCode -1, stderr message).
     *  The JVM honors cwd/env, so this is a documented cross-only gap. */
    @Test
    void runWithNonEmptyCwdIsHonestResultOnCross(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main-rw-gap.kf");
        Files.writeString(src, """
            main() {
                var r = shell.runWith(shell.cmd("echo", listOf("hi")), "/tmp", mapOf())
                println(r.exitCode)
                println(r.stderr != "")
            }
            """);
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals("-1\ntrue\n", runCross(src, tmp.resolve("rw-gap-rv"),
                "riscv64", Target.NATIVE_RISCV64), "riscv64 honest cwd gap");
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals("-1\ntrue\n", runCross(src, tmp.resolve("rw-gap-aa"),
                "aarch64", Target.NATIVE_AARCH64), "aarch64 honest cwd gap");
    }
}

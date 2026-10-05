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
 * issue #762 — a Native {@code process.run}/{@code shell.run}/{@code shell.pipeline}
 * child must not inherit the parent's file descriptors. The JVM oracle
 * ({@code ProcessBuilder}) closes inherited descriptors; the Native child
 * ({@code fork}+{@code execvp}) left the runtime's own pipe ends and any parent
 * fd open, so a capability-restricted runner leaked authority to a less-trusted
 * subprocess.
 *
 * <p>Golden: the SAME Kof source runs on JVM and Native; the child lists
 * {@code /proc/self/fd} and the outputs must be byte-identical. Pre-fix the
 * Native child listed the leaked runtime pipe ends (4,6,…) the JVM does not.
 */
class NativeChildFdIsolationE2ETest {

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

    private static Path write(Path dir, String tag, String program) throws IOException {
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
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

    private String runNative(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
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

    /** Prints the child's own open descriptors, sorted, comma-separated. */
    private static final String PROBE = """
        main() {
            var r = shell.run("sh", listOf("-c", "ls /proc/self/fd | sort -n | tr '\\n' ','"))
            print("FDS:" + r.stdout)
            println("|" + r.exitCode)
        }
        """;

    @Test
    void nativeChildDoesNotInheritParentFds(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "fds", PROBE);
        String jvm = runJvm(src, tmp.resolve("fds-jvm"));
        String nat = runNative(src, tmp.resolve("fds-nat"));
        assertEquals(jvm, nat, "Native child fd set must match the JVM oracle");
    }

    @Test
    void crossChildDoesNotInheritParentFds(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "fdsx", PROBE);
        String jvm = runJvm(src, tmp.resolve("fdsx-jvm"));
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(jvm, runCross(src, tmp.resolve("fdsx-rv"), "riscv64", Target.NATIVE_RISCV64),
                "riscv64 child fd set must match the JVM oracle");
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(jvm, runCross(src, tmp.resolve("fdsx-aa"), "aarch64", Target.NATIVE_AARCH64),
                "aarch64 child fd set must match the JVM oracle");
    }

    @Test
    void pipelineChildDoesNotInheritParentFds(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "pl", """
            main() {
                var p = shell.pipeline(listOf(listOf("sh", "-c", "ls /proc/self/fd | sort -n | tr '\\n' ','"), listOf("cat")))
                print("FDS:" + p.stdout)
                println("|" + p.exitCode)
            }
            """);
        String jvm = runJvm(src, tmp.resolve("pl-jvm"));
        String nat = runNative(src, tmp.resolve("pl-nat"));
        assertEquals(jvm, nat, "Native pipeline child fd set must match the JVM oracle");
    }
}

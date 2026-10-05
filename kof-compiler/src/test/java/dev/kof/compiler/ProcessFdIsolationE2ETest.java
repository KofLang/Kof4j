package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #762: subprocesses must not inherit ambient parent file descriptors. */
class ProcessFdIsolationE2ETest {

    private static final String EXPECTED = "run=CLEAN\nspawn=CLEAN\npipeline=CLEAN\n";

    private static final String SOURCE = """
            extern "libc.so.6" open(String path, Int flags): Int
            extern "libc.so.6" close(Int fd): Int

            main() {
                val fd = open("/etc/hostname", 0)
                val probe = "if [ -e /proc/self/fd/" + fd
                    + " ]; then echo LEAK; else echo CLEAN; fi"

                val r = process.run("sh", "-c", probe)
                println("run=" + r.stdout.trim())

                val h = process.spawn("sh", "-c", probe)
                println("spawn=" + h.readLine())
                var spin = 0
                while (h.alive() && spin < 1000000) { spin = spin + 1 }

                val p = shell.pipeline(listOf(listOf("sh", "-c", probe)))
                println("pipeline=" + p.stdout.trim())
                close(fd)
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static boolean has(String... commands) {
        for (String command : commands) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + command)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String capture(Process p) throws IOException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        try {
            assertEquals(0, p.waitFor(), "program exit, output: " + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return out;
    }

    private Path source(String tag) throws IOException {
        Path src = tmp.resolve("fd-isolation-" + tag + ".kf");
        Files.writeString(src, SOURCE);
        return src;
    }

    private String runJvm(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        return capture(new ProcessBuilder("java", "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start());
    }

    private String runNative(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "x86 native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        return capture(new ProcessBuilder(bin.toString()).redirectErrorStream(true).start());
    }

    private String runCross(Path src, Path out, String arch, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        return capture(NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start());
    }

    @Test
    void x86NativeMatchesJvmDescriptorIsolation() throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux")
                        && Files.isDirectory(Path.of("/proc/self/fd")),
                "Linux /proc/self/fd required");
        Path src = source("x86");
        assertEquals(EXPECTED, runJvm(src, tmp.resolve("x86-jvm")), "JVM oracle");
        assertEquals(EXPECTED, runNative(src, tmp.resolve("x86-native")), "x86 native");
    }

    @Test
    void crossTargetsMatchJvmDescriptorIsolation() throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux")
                        && Files.isDirectory(Path.of("/proc/self/fd"))
                        && has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64",
                               "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "Linux + both cross toolchains/sysroots required");
        Path src = source("cross");
        assertEquals(EXPECTED, runJvm(src, tmp.resolve("cross-jvm")), "JVM oracle");
        assertEquals(EXPECTED, runCross(src, tmp.resolve("cross-rv"),
                "riscv64", Target.NATIVE_RISCV64), "riscv64");
        assertEquals(EXPECTED, runCross(src, tmp.resolve("cross-aa"),
                "aarch64", Target.NATIVE_AARCH64), "aarch64");
    }
}

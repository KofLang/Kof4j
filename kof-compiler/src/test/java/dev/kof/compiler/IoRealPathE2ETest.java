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
 * #751 / D-MAINT-BATCH-0510 (IO1) — {@code Path.realPath()} / {@code
 * File.realPath()}: the canonical path with symlinks and Windows directory
 * junctions resolved; null when the path does not exist (contrast {@code
 * toAbsolute()}, which is lexical). The JVM oracle is {@code
 * Path.toRealPath()}; Native x86-64 and the riscv64/aarch64 cross resolve via
 * libc {@code realpath}.
 *
 * <p>Golden: the SAME Kof source runs on JVM and Native and the outputs must be
 * byte-identical — a confinement check that compares {@code realPath()} of a
 * child against the real root must not be fooled by a symlink.
 */
class IoRealPathE2ETest {

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

    private static String q(String s) {
        return s.replace("\\", "\\\\");
    }

    private static Path link(Path target, Path at) throws IOException {
        Files.createSymbolicLink(at, target);
        return at;
    }

    @Test
    void realPathResolvesSymlinkJvmAndNative(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(
                System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "symlink oracle runs on Linux");
        Path real = Files.createDirectories(tmp.resolve("real"));
        Files.writeString(real.resolve("file.txt"), "x");
        Path sym = link(real, tmp.resolve("sym"));

        String realCanon = real.toRealPath().toString();
        String fileCanon = real.resolve("file.txt").toRealPath().toString();
        String expected = realCanon + "\n" + fileCanon + "\nnull\n" + q(sym.toString()) + "\n";

        String body = """
            main() {
                println(File("%s").realPath())
                println(File("%s").realPath())
                println(File("%s").realPath())
                println(Path("%s").toAbsolute())
            }
            """.formatted(q(sym.toString()), q(real.resolve("file.txt").toString()),
                q(tmp.resolve("missing").toString()), q(sym.toString()));
        Path src = tmp.resolve("Main-real.kf");
        Files.writeString(src, body);

        String jvm = runJvm(src, tmp.resolve("out-jvm"));
        assertEquals(expected, jvm, "JVM realPath must canonicalize the symlink");
        String nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(jvm, nat, "Native realPath must match the JVM oracle");
    }

    @Test
    void realPathCrossMatchesJvm(@TempDir Path tmp) throws IOException {
        Path real = Files.createDirectories(tmp.resolve("real"));
        Path sym = link(real, tmp.resolve("sym"));
        String body = """
            main() {
                println(Path("%s").realPath())
                println(Path("%s").realPath())
            }
            """.formatted(q(sym.toString()), q(tmp.resolve("missing").toString()));
        Path src = tmp.resolve("Main-realx.kf");
        Files.writeString(src, body);
        String jvm = runJvm(src, tmp.resolve("outx-jvm"));

        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(jvm, runCross(src, tmp.resolve("outx-rv"), "riscv64", Target.NATIVE_RISCV64),
                "riscv64 realPath must match the JVM oracle");

        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(jvm, runCross(src, tmp.resolve("outx-aa"), "aarch64", Target.NATIVE_AARCH64),
                "aarch64 realPath must match the JVM oracle");
    }

    @Test
    void realPathOnJsIsIojs001(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main-realjs.kf");
        Files.writeString(src, """
            main() { println(Path("/tmp/kof-real-probe").realPath()) }
            """);
        CompilationResult r = driver.compile(src, tmp.resolve("outjs"), Target.JS);
        assertFalse(r.success(), "JS has no realPath binding — must refuse");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                "expected IOJS001, got: " + r.diagnostics().getDiagnostics());
    }
}

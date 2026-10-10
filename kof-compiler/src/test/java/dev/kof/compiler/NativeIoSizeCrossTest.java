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
 * D-FULL-PARITY-050 (row 13) — {@code size()} do {@code kof.io} no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoSize}).
 * Sucesso: st_size byte-identico ao oraculo JVM medido no MESMO programa.
 * Miss: LANÇA `file not found: ` como o JVM (D-IO-SIZE-JVM-LAW, §494 FIXED
 * 27/09 — antes o cross espelhava o prefixo `size: ` do x86).
 */
class NativeIoSizeCrossTest extends NativeIoJvmOracleSupport {

    private static String sizeProgram(Path file) {
        return "main() {\n"
                + "    println(File(\"" + file + "\").size())\n"
                + "}\n";
    }

    private static String missingProgram(Path file) {
        return "main() {\n"
                + "    try {\n"
                + "        println(File(\"" + file + "\").size())\n"
                + "    } catch (String e) {\n"
                + "        println(e)\n"
                + "    }\n"
                + "}\n";
    }

    private static Path seed(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path f = seed(tempDir, "jvm.txt", "hello");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, sizeProgram(f));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return "5";
    }

    @Test
    void riscv64SizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path f = seed(tempDir, "riscv.txt", "hello");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, sizeProgram(f));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, sizeProgram(f));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 size != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64SizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path f = seed(tempDir, "aarch.txt", "hello");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, sizeProgram(f));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, sizeProgram(f));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 size != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void jvmMissingThrowsWithJvmMessage(@TempDir Path tempDir) throws IOException {
        Path missing = tempDir.resolve("nope-jvm.txt");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, missingProgram(missing));
        String out = runJvm(driver, src, tempDir.resolve("jvm-out"));
        assertTrue(out.startsWith("file not found: "),
                "JVM message should be 'file not found: ' — got: " + out);
        assertTrue(out.endsWith(missing.toString()), "should end with path — got: " + out);
    }

    @Test
    void riscv64MissingThrowsWithJvmMessage(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path missing = tempDir.resolve("nope-riscv.txt");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, missingProgram(missing));
        String out = runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64);
        assertTrue(out.startsWith("file not found: "),
                "§494/D-IO-SIZE-JVM-LAW: cross lanca como o JVM — veio: " + out);
        assertTrue(out.endsWith(missing.toString()), "should end with path — got: " + out);
    }

    @Test
    void aarch64MissingThrowsWithJvmMessage(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path missing = tempDir.resolve("nope-aarch.txt");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, missingProgram(missing));
        String out = runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64);
        assertTrue(out.startsWith("file not found: "),
                "§494/D-IO-SIZE-JVM-LAW: cross lanca como o JVM — veio: " + out);
        assertTrue(out.endsWith(missing.toString()), "should end with path — got: " + out);
    }

    @Test
    void x86MissingThrowsWithJvmMessage(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("as", "ld"), "x86 toolchain ausente — pulando");
        Path missing = tempDir.resolve("nope-x86.txt");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, missingProgram(missing));
        CompilationResult result = driver.compile(src, tempDir.resolve("out-x86"), Target.NATIVE);
        assertTrue(result.success(), "x86 compile: " + result.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("out-x86/Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "exit code, output: " + out);
            assertTrue(out.startsWith("file not found: "),
                    "§494/D-IO-SIZE-JVM-LAW: x86 lanca como o JVM — veio: " + out);
            assertTrue(out.endsWith(missing.toString()), "should end with path — got: " + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}

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
 * D-FULL-PARITY-050 (row 13) — {@code Path.toAbsolute} no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoToAbsolute}),
 * via getcwd ({@code __NR_getcwd}=17) + resolve. O cwd do processo qemu e o
 * mesmo do oraculo JVM (ProcessBuilder sem directory), entao o oraculo JVM e
 * medido no mesmo programa.
 * Q3: cobre path ja absoluto, relativo simples e relativo com ".." (nao
 * normalizado — so resolve contra o cwd, como o x86/JVM).
 */
class NativeIoToAbsoluteCrossTest extends NativeIoJvmOracleSupport {

    private static final String PROGRAM = """
            main() {
                println(Path("/abs/x").toAbsolute())
                println(Path("rel/x").toAbsolute())
                println(Path("a/../b").toAbsolute())
            }
            """;

    private static String expected() {
        String cwd = System.getProperty("user.dir");
        return "/abs/x\n" + cwd + "/rel/x\n" + cwd + "/a/../b";
    }

    private static Path writeProgram(Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        return src;
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        return writeProgram(tempDir);
    }

    @Override
    protected String jvmOracleExpected() {
        return expected();
    }

    @Test
    void riscv64ToAbsoluteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 toAbsolute != JVM oracle");
    }

    @Test
    void aarch64ToAbsoluteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 toAbsolute != JVM oracle");
    }
}

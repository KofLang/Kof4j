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
 * D-FULL-PARITY-050 (row 13) — {@code Path.normalize} no cross riscv64/aarch64
 * (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoNormalize}). Sem IO de disco;
 * oraculo JVM medido no mesmo programa.
 * Q3: cobre "." / ".." / separador duplo / barra final / raiz "/" / resultado
 * vazio relativo ("").
 */
class NativeIoNormalizeCrossTest extends NativeIoJvmOracleSupport {

    private static final String PROGRAM = """
            main() {
                println(Path("/a/b/../c").normalize())
                println(Path("a/./b").normalize())
                println(Path("a/b/../../c").normalize())
                println(Path("//a//b").normalize())
                println(Path("/..").normalize())
                println(Path("a/..").normalize())
                println(Path("x/").normalize())
            }
            """;

    private static final String EXPECTED = "/a/c\na/b\nc\n/a/b\n/\n.\nx";

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
        return EXPECTED;
    }

    @Test
    void riscv64NormalizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 normalize != JVM oracle");
    }

    @Test
    void aarch64NormalizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 normalize != JVM oracle");
    }
}

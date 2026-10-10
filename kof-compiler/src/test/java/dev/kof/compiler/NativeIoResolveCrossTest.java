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
 * D-FULL-PARITY-050 (row 13) — {@code Path.resolve} no cross riscv64/aarch64
 * (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoResolve}). Programa sem IO
 * de disco; oraculo JVM medido no mesmo programa.
 * Q3: cobre child relativo (base com/sem '/' final) e child absoluto.
 */
class NativeIoResolveCrossTest extends NativeIoJvmOracleSupport {

    private static final String PROGRAM = """
            main() {
                println(Path("/a/b").resolve("c/d"))
                println(Path("/a/b/").resolve("c"))
                println(Path("/a/b").resolve("/x"))
            }
            """;

    private static final String EXPECTED = "/a/b/c/d\n/a/b/c\n/x";

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
    void riscv64ResolveMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 resolve != JVM oracle");
    }

    @Test
    void aarch64ResolveMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 resolve != JVM oracle");
    }
}

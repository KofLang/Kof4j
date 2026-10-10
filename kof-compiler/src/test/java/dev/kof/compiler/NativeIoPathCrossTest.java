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
 * D-FULL-PARITY-050 (row 13) — faces PURAS de path do {@code kof.io}
 * (sem syscall) no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoPath}):
 * {@code File.name()}, {@code Path.fileName()}, {@code Path.extension()},
 * {@code Path.parent()}, {@code Path.isAbsolute()}. Programa sem IO de disco
 * (so strings) e oraculo JVM medido no mesmo programa.
 * Q3: cobre com/sem extensao nao (dotfile fica de fora: x86 e JVM divergem
 * pre-existente em ".hidden" — nao e escopo desta fatia).
 */
class NativeIoPathCrossTest extends NativeIoJvmOracleSupport {

    private static final String PROGRAM = """
            main() {
                println(File("/a/b/c.txt").name())
                println(Path("/a/b/c.txt").fileName())
                println(Path("/a/b/c.txt").extension())
                println(Path("/a/b").parent())
                println(Path("/a/b").isAbsolute())
                println(Path("a/b").isAbsolute())
            }
            """;

    private static final String EXPECTED = "c.txt\nc.txt\ntxt\n/a\ntrue\nfalse";

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
    void riscv64PathMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 path != JVM oracle");
    }

    @Test
    void aarch64PathMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 path != JVM oracle");
    }
}

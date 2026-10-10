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
 * D-FULL-PARITY-050 (row 13) — {@code readRange(offset, len)} do {@code kof.io}
 * no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoReadRange}). Escreve 5 bytes e le
 * o range (1,3), exigindo os 3 bytes do meio como Int[] (openat + pread64).
 * Q3: cobre offset != 0 e len parcial (leitura curta deterministica).
 */
class NativeIoReadRangeCrossTest extends NativeIoJvmOracleSupport {

    private static String program(Path file) {
        return "main() {\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    var b = new Int[5]\n"
                + "    b[0] = 10\n"
                + "    b[1] = 20\n"
                + "    b[2] = 30\n"
                + "    b[3] = 40\n"
                + "    b[4] = 50\n"
                + "    println(f.writeBytes(b))\n"
                + "    var r = f.readRange(1, 3)\n"
                + "    println(r.length)\n"
                + "    println(r[0])\n"
                + "    println(r[1])\n"
                + "    println(r[2])\n"
                + "}\n";
    }

    private static final String EXPECTED = "true\n3\n20\n30\n40";

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvm.bin")));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64ReadRangeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm.bin")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscv.bin")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 readRange != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64ReadRangeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm.bin")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarch.bin")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 readRange != JVM oracle (oracle=" + oracle + ")");
    }
}

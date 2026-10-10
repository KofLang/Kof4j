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
 * D-FULL-PARITY-050 (row 13) — {@code createDirectories()}/{@code mkdirs()}
 * (mkdir -p) do {@code kof.io} no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoMkdirs}). Cria um caminho
 * profundo cujos pais nao existem, ignorando EEXIST nos prefixos. O cross tem
 * de ser byte-identico ao oraculo JVM medido no MESMO programa (caminhos
 * distintos por alvo). size/bytes/listing seguem gated (NAT006, §427).
 */
class NativeIoMkdirsCrossTest extends NativeIoJvmOracleSupport {

    private static String program(Path deep) {
        return "main() {\n"
                + "    var d = Directory(\"" + deep + "\")\n"
                + "    println(d.createDirectories())\n"
                + "    println(File(\"" + deep + "\").exists())\n"
                + "    println(File(\"" + deep + "\").isDirectory())\n"
                + "    println(File(\"" + deep.getParent().getParent() + "\").isDirectory())\n"
                + "}\n";
    }

    private static final String EXPECTED = "true\ntrue\ntrue\ntrue";

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvm-x/y/z")));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64MkdirsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm-x/y/z")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscv-x/y/z")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64MkdirsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm-x/y/z")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarch-x/y/z")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 != JVM oracle (oracle=" + oracle + ")");
    }
}

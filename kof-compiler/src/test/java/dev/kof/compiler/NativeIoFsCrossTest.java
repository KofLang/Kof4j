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
 * D-FULL-PARITY-050 (row 13) — mutacoes de FS do {@code kof.io}
 * ({@code delete()}/{@code create()}) no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoFs}). O oraculo e medido no JVM
 * no MESMO programa (caminhos distintos por alvo) e o cross tem de ser
 * byte-identico. createDirectories/bytes/list seguem com gate NAT006 (§427).
 */
class NativeIoFsCrossTest extends NativeIoJvmOracleSupport {

    // create=1, exists=1, isDirectory=1, create again=0 (EEXIST),
    // writeText=1, delete=1, exists=0, delete missing=0.
    private static final String EXPECTED = "true\ntrue\ntrue\nfalse\ntrue\ntrue\nfalse\nfalse";

    private static String program(Path dir, Path file) {
        return "main() {\n"
                + "    var d = Directory(\"" + dir + "\")\n"
                + "    println(d.create())\n"
                + "    println(File(\"" + dir + "\").exists())\n"
                + "    println(File(\"" + dir + "\").isDirectory())\n"
                + "    println(d.create())\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    println(f.writeText(\"x\"))\n"
                + "    println(f.delete())\n"
                + "    println(f.exists())\n"
                + "    println(f.delete())\n"
                + "}\n";
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64IoFsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscvd"), tempDir.resolve("riscvf.txt")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64IoFsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarchd"), tempDir.resolve("aarchf.txt")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 != JVM oracle (oracle=" + oracle + ")");
    }
}

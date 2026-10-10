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
 * D-FULL-PARITY-050 (row 13) — faces de estat de {@code kof.io}
 * ({@code exists()}/{@code isFile()}/{@code isDirectory()}) no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoStat}).
 * Golden = oraculo JVM medido no MESMO programa, byte-identico no cross.
 * As demais faces de kof.io seguem com gate honesto NAT006 (§427).
 */
class NativeIoStatCrossTest extends NativeIoJvmOracleSupport {

    // file: exists=true, isFile=true, isDir=false
    // dir : exists=true, isFile=false, isDir=true
    // none: exists=false, isFile=false, isDir=false
    private static final String EXPECTED =
            "true\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\nfalse\nfalse";

    private static String program(Path file, Path dir, Path none) {
        return "main() {\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    println(f.exists())\n"
                + "    println(f.isFile())\n"
                + "    println(f.isDirectory())\n"
                + "    var d = File(\"" + dir + "\")\n"
                + "    println(d.exists())\n"
                + "    println(d.isFile())\n"
                + "    println(d.isDirectory())\n"
                + "    var n = File(\"" + none + "\")\n"
                + "    println(n.exists())\n"
                + "    println(n.isFile())\n"
                + "    println(n.isDirectory())\n"
                + "}\n";
    }

    private void prepare(Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("d"));
        Files.writeString(tempDir.resolve("d/f.txt"), "x");
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        prepare(tempDir);
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("d/f.txt"), tempDir.resolve("d"),
                tempDir.resolve("nope")));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64IoStat(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        prepare(tempDir);
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("d/f.txt"), tempDir.resolve("d"),
                tempDir.resolve("nope")));
        assertEquals(EXPECTED, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64IoStat(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        prepare(tempDir);
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("d/f.txt"), tempDir.resolve("d"),
                tempDir.resolve("nope")));
        assertEquals(EXPECTED, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64));
    }
}

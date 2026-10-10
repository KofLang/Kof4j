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
 * D-FULL-PARITY-050 (row 13) — faces de texto do {@code kof.io}
 * ({@code readText()}/{@code writeText()}/{@code appendText()}) no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoText}).
 * O oraculo e medido no JVM no MESMO programa e o cross tem de ser
 * byte-identico. As faces delete/bytes/dir seguem com gate honesto NAT006 (§427).
 */
class NativeIoTextCrossTest extends NativeCrossSupport {

    // JVM: writeText("hello")=true; readText="hello"; appendText("!")=true;
    //      readText="hello!"; exists=true; readText(missing)=null -> "null".
    private static String program(Path file, Path missing) {
        return "main() {\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    println(f.writeText(\"hello\"))\n"
                + "    println(f.readText())\n"
                + "    println(f.appendText(\"!\"))\n"
                + "    println(f.readText())\n"
                + "    println(f.exists())\n"
                + "    var g = File(\"" + missing + "\")\n"
                + "    println(g.readText())\n"
                + "}\n";
    }

    @Test
    void riscv64IoTextMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("io.txt"), tempDir.resolve("missing.txt")));
        String oracle = runJvm(driver, src, tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64IoTextMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("io.txt"), tempDir.resolve("missing.txt")));
        String oracle = runJvm(driver, src, tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void jvmOracleIsStable(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("io.txt"), tempDir.resolve("missing.txt")));
        assertEquals("true\nhello\ntrue\nhello!\ntrue\nnull",
                runJvm(driver, src, tempDir.resolve("jvm-out")));
    }
}

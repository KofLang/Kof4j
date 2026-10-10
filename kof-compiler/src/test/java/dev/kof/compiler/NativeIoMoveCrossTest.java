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
 * D-FULL-PARITY-050 (row 13) — {@code File.moveTo()} em todos os alvos:
 * x86-64 ({@link dev.kof.compiler.runtime.RuntimeIoMove}) e cross
 * ({@link dev.kof.compiler.nat.NativeRiscvAsmIoMove}). Contrato JVM G-ORG-002:
 * Files.move sem sobrescrever (destino existente -> false).
 * Q3: cobre move OK (src some, dst tem conteudo) e dest existente -> false com
 * src e dst intactos.
 */
class NativeIoMoveCrossTest extends NativeCrossSupport {

    private static Path writeProgram(Path dir, String tag, String base) throws IOException {
        String program = """
                main() {
                    File("%1$s/m1").writeText("m")
                    println(File("%1$s/m1").moveTo("%1$s/m2"))
                    println(File("%1$s/m2").exists())
                    println(File("%1$s/m1").exists())
                    println(File("%1$s/m2").readText())
                    File("%1$s/o1").writeText("old")
                    File("%1$s/o2").writeText("new")
                    println(File("%1$s/o2").moveTo("%1$s/o1"))
                    println(File("%1$s/o1").readText())
                    println(File("%1$s/o2").exists())
                }
                """.formatted(base);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private static final String EXPECTED = "true\ntrue\nfalse\nm\nfalse\nold\ntrue";

    @Test
    void jvmMoveToMatchesContract(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(writeProgram(tempDir, "jvm", base(tempDir, "jvm").toString()),
                tempDir.resolve("jvm-out")));
    }

    @Test
    void x86MoveToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "x86-64 native runs on Linux");
        assertEquals(EXPECTED, runNative(writeProgram(tempDir, "x86", base(tempDir, "x86").toString()),
                tempDir.resolve("x86-out")));
    }

    @Test
    void riscv64MoveToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "r", base(tempDir, "r").toString()),
                tempDir.resolve("riscv-out"), "qemu-riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64MoveToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "a", base(tempDir, "a").toString()),
                tempDir.resolve("aarch-out"), "qemu-aarch64", Target.NATIVE_AARCH64));
    }
}

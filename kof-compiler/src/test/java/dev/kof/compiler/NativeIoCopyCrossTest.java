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
 * D-FULL-PARITY-050 (row 13) — {@code File.copyTo()} em todos os alvos:
 * x86-64 ({@link dev.kof.compiler.runtime.RuntimeIoCopy}) e cross
 * ({@link dev.kof.compiler.nat.NativeRiscvAsmIoCopy}). Contrato JVM G-ORG-002:
 * Files.copy com COPY_ATTRIBUTES, sem sobrescrever (destino existente -> false).
 * Q3: cobre copia OK (src permanece, dst com conteudo) e dest existente -> false
 * com dst intacto.
 */
class NativeIoCopyCrossTest extends NativeCrossSupport {

    private static Path writeProgram(Path dir, String tag, String base) throws IOException {
        String program = """
                main() {
                    File("%1$s/c1").writeText("hello")
                    println(File("%1$s/c1").copyTo("%1$s/c2"))
                    println(File("%1$s/c2").exists())
                    println(File("%1$s/c2").readText())
                    println(File("%1$s/c1").exists())
                    File("%1$s/d1").writeText("old")
                    println(File("%1$s/c1").copyTo("%1$s/d1"))
                    println(File("%1$s/d1").readText())
                }
                """.formatted(base);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private static final String EXPECTED = "true\ntrue\nhello\ntrue\nfalse\nold";

    @Test
    void jvmCopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(writeProgram(tempDir, "jvm", base(tempDir, "jvm").toString()),
                tempDir.resolve("jvm-out")));
    }

    @Test
    void x86CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "x86-64 native runs on Linux");
        assertEquals(EXPECTED, runNative(writeProgram(tempDir, "x86", base(tempDir, "x86").toString()),
                tempDir.resolve("x86-out")));
    }

    @Test
    void riscv64CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "r", base(tempDir, "r").toString()),
                tempDir.resolve("riscv-out"), "qemu-riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "a", base(tempDir, "a").toString()),
                tempDir.resolve("aarch-out"), "qemu-aarch64", Target.NATIVE_AARCH64));
    }
}

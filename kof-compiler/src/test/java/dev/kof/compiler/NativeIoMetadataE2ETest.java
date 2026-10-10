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
 * D-FULL-PARITY-050 (row 13) — metadados de arquivo em TODOS os alvos:
 * {@code File.modifiedTime()} (millis) e {@code File.isSymlink()} no x86-64
 * ({@link dev.kof.compiler.runtime.RuntimeIoMeta}) e no cross riscv64/aarch64
 * ({@link dev.kof.compiler.nat.NativeRiscvAsmIoMeta}). O arquivo+link sao
 * criados UMA vez (path absoluto) e lidos por JVM e nativo, entao o oraculo e
 * exatamente o valor medido em Java (Files.getLastModifiedTime(...).toMillis),
 * nao um chute.
 * Q3: cobre mtime != 0 e symlink true/regular false.
 */
class NativeIoMetadataE2ETest extends NativeCrossSupport {

    private static Path writeProgram(Path dir, String tag, String file, String link) throws IOException {
        String program = """
                main() {
                    println(File("%1$s").modifiedTime())
                    println(File("%2$s").isSymlink())
                    println(File("%1$s").isSymlink())
                }
                """.formatted(file, link);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private static String expected(Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis() + "\ntrue\nfalse";
    }

    private Path file;
    private Path link;
    private String expected;

    private void setup(Path tempDir) throws IOException {
        file = tempDir.resolve("meta.txt");
        Files.writeString(file, "x");
        link = tempDir.resolve("meta-link.txt");
        try {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, file.getFileName());
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("symlinks unsupported: " + e);
        }
        expected = expected(file);
    }

    @Test
    void jvmMetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        setup(tempDir);
        assertEquals(expected, runJvm(writeProgram(tempDir, "jvm", file.toString(), link.toString()),
                tempDir.resolve("jvm-out")));
    }

    @Test
    void x86MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "x86-64 native runs on Linux");
        setup(tempDir);
        assertEquals(expected, runNative(writeProgram(tempDir, "x86", file.toString(), link.toString()),
                tempDir.resolve("x86-out")));
    }

    @Test
    void riscv64MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        setup(tempDir);
        assertEquals(expected, runCross(writeProgram(tempDir, "r", file.toString(), link.toString()),
                tempDir.resolve("riscv-out"), "qemu-riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        setup(tempDir);
        assertEquals(expected, runCross(writeProgram(tempDir, "a", file.toString(), link.toString()),
                tempDir.resolve("aarch-out"), "qemu-aarch64", Target.NATIVE_AARCH64));
    }
}

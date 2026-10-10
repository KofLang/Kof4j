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
 * D-FULL-PARITY-050 (row 13) — {@code Directory.delete()} RECURSIVO no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoDirDelete}).
 * Arvore nao-vazia (dir + arquivo + subdir/arquivo) deve ser removida por
 * inteiro, como o contrato JVM (oraculo medido no mesmo programa). Path
 * ABSOLUTO injetado (independe do cwd).
 * Q3: cobre dir nao-vazio recursivo (sucesso), pos-condicao exists=false e
 * dir inexistente -> false.
 */
class NativeIoDirDeleteCrossTest extends NativeIoJvmOracleSupport {

    private static final String EXPECTED = "true\nfalse\nfalse";

    private static Path writeProgram(Path dir, String tag) throws IOException {
        Path probe = dir.resolve("probe-" + tag).toAbsolutePath();
        String p = probe.toString();
        String program = """
                main() {
                    Directory("%1$s").create()
                    File("%1$s/f.txt").writeText("x")
                    Directory("%1$s/sub").create()
                    File("%1$s/sub/g.txt").writeText("y")
                    println(Directory("%1$s").delete())
                    println(File("%1$s").exists())
                    println(Directory("%1$s/none").delete())
                }
                """.formatted(p);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        return writeProgram(tempDir, "jvm");
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64DirDeleteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir, "o1"), tempDir.resolve("jvm-out1"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir, "r1"), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 dir_delete != JVM oracle");
        assertFalse(Files.exists(tempDir.resolve("probe-r1")), "tree must be gone (riscv64)");
    }

    @Test
    void aarch64DirDeleteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir, "o2"), tempDir.resolve("jvm-out2"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir, "a2"), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 dir_delete != JVM oracle");
        assertFalse(Files.exists(tempDir.resolve("probe-a2")), "tree must be gone (aarch64)");
    }
}

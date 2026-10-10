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
 * D-FULL-PARITY-050 (row 13) — {@code Directory.list()} do {@code kof.io} no
 * cross riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoDirList}).
 * Cria um diretorio com 2 arquivos em ordem nao-alfabetica e exige a listagem
 * ORDENADA (como o JVM: {@code Files.list().sorted()}), pulando "." e "..".
 * Q3: cobre ordenacao, filtro de "."/".." e o tamanho da lista.
 */
class NativeIoDirListCrossTest extends NativeIoJvmOracleSupport {

    private static String program(Path dir) {
        return "main() {\n"
                + "    var d = Directory(\"" + dir + "\")\n"
                + "    println(d.create())\n"
                + "    println(File(\"" + dir + "/b.txt\").writeText(\"y\"))\n"
                + "    println(File(\"" + dir + "/a.txt\").writeText(\"x\"))\n"
                + "    var names = d.list()\n"
                + "    println(names.size())\n"
                + "    for (var n in names) { println(n) }\n"
                + "}\n";
    }

    private static final String EXPECTED = "true\ntrue\ntrue\n2\na.txt\nb.txt";

    @Override
    protected Path jvmOracleSource(Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvmdir")));
        return src;
    }

    @Override
    protected String jvmOracleExpected() {
        return EXPECTED;
    }

    @Test
    void riscv64DirListMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmdir")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscvd")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 dir listing != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64DirListMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmdir")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarchd")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 dir listing != JVM oracle (oracle=" + oracle + ")");
    }
}

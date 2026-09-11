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
 * Faces UTF-16 de String nos cross-arch (bug 43 residual — B34):
 * length/charAt/substring contam code units UTF-16 (JVM/x86 idênticos),
 * não bytes UTF-8. Arquivo NOVO e isolado de propósito: as faces x86 já
 * vivem em NativeE2ETest (stringLengthUtf16 etc.) e os E2Es de cross
 * (NativeRiscv64E2ETest/NativeAarch64E2ETest) estão na lane do sweep
 * §104/§106/§107 — nada de dois agentes no mesmo arquivo (DOING.md).
 * Golden = oracle JVM medido no MESMO programa (12 valores).
 */
class NativeStringUtf16CrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String runCross(Path tempDir, String source, String qemu, String archFlag)
            throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir,
                dev.kof.compiler.Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = new ProcessBuilder(qemu, bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        assertEquals(0, ec, "exit code, output: " + output);
        return output;
    }

    private static final String PROGRAM = """
            main() {
                var s = "café"
                println(s.length)
                println(s.charAt(3))
                println(s.substring(1))
                println(s.substring(1).length)
                println(s.substring(3))
                var e = "a😀b"
                println(e.length)
                println(e.charAt(1))
                println(e.charAt(2))
                println(e.charAt(3))
                println(e.substring(0, 3).length)
                println(e.substring(1, 3))
                println(e.substring(3))
            }
            """;

    // Oracle JVM medido 11/09 (JVM == x86_64 == Script == riscv == aarch):
    private static final String GOLDEN =
            "4\n233\nafé\n3\né\n4\n55357\n56832\n98\n3\n😀\nb";

    @Test
    void riscv64StringUtf16Faces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64StringUtf16Faces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}

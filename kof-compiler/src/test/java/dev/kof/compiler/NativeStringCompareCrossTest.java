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
 * §97 cross (B36) + §111 cross: String.equals/compareTo/hashCode rodam nos
 * cross-archs (o x86 os tem desde 10/07 em RuntimeStringCompare.java; riscv
 * caía em undefined reference a String_equals/String_compareTo/String_hashCode
 * no link — o router cross só tinha o intrínseco do ==, não o MÉTODO). E o
 * sentinela do substring 1-arg virou -1 no cross também (o 0 colidia com o
 * 0 legítimo do 2-arg — §111 x86 do maintainer; riscv tinha o MESMO bug).
 * Arquivo NOVO e isolado (não toca os E2Es da lane §104 — DOING.md).
 * Golden = oracle JVM medido == x86_64 (18 valores compare + 7 substring).
 */
class NativeStringCompareCrossTest {

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
        Files.writeString(src, source, StandardCharsets.UTF_8);
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

    // compareTo/hashesCode/equals sobre units UTF-16: BMP, prefixo, astrais.
    private static final String COMPARE_PROGRAM = """
            main() {
                println("café".compareTo("cafe"))
                println("cafe".compareTo("café"))
                println("café".compareTo("café"))
                println("abc".compareTo("abd"))
                println("abc".compareTo("ab"))
                println("ab".compareTo("abc"))
                println("".compareTo(""))
                println("a😀b".compareTo("a😀b"))
                println("a😀b".compareTo("aéb"))
                println("café".hashCode())
                println("".hashCode())
                println("a😀b".hashCode())
                println("hello".hashCode())
                println("".equals(""))
                println("café".equals("café"))
                println("café".equals("cafe"))
                println("a😀b".equals("a😀b"))
                println("a😀b".equals("ab"))
            }
            """;

    // Oracle JVM medido == x86_64 (11/09): -1/0/astrais/prefixo.
    private static final String COMPARE_GOLDEN =
            "132\n-132\n0\n-1\n1\n-1\n0\n0\n55124\n3045921\n0\n57849694\n99162322\ntrue\ntrue\nfalse\ntrue\nfalse";

    // §111 cross: sentinela 0→-1 no substring 1-arg (0 legítimo do 2-arg).
    private static final String SUBSTR_PROGRAM = """
            main() {
                var h = "hello"
                println("[" + h.substring(0, 0) + "]")
                println("[" + h.substring(2) + "]")
                println("[" + h.substring(1, 1) + "]")
                println("[" + "café".substring(4) + "]")
                println("[" + "café".substring(1, 3) + "]")
                println("[" + "a😀b".substring(1) + "]")
                println("[" + "a😀b".substring(0, 1) + "]")
            }
            """;

    private static final String SUBSTR_GOLDEN =
            "[]\n[llo]\n[]\n[]\n[af]\n[😀b]\n[a]";

    @Test
    void riscv64StringCompareHashEquals(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(COMPARE_GOLDEN, runCross(tempDir, COMPARE_PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
        assertEquals(SUBSTR_GOLDEN, runCross(tempDir, SUBSTR_PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64StringCompareHashEquals(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(COMPARE_GOLDEN, runCross(tempDir, COMPARE_PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
        assertEquals(SUBSTR_GOLDEN, runCross(tempDir, SUBSTR_PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}

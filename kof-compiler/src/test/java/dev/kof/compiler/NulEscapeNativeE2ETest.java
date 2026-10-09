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
 * §623: um escape {@code "\0"} (NUL) — e qualquer byte de controle — dentro de
 * um string literal compilava no JVM mas quebrava o assembler nativo: o
 * emitter escrevia o byte cru dentro de {@code .asciz "…"} e o GAS morria com
 * {@code invalid character ... in mnemonic}, mal-rotulado {@code COMP001}.
 *
 * A correção ({@code NativeGasStrings.gasEscape}) escapa bytes de controle
 * como octal de 3 dígitos e deixa os bytes UTF-8 intactos. Este teste prova
 * paridade byte-a-byte contra o oracle JVM medido no MESMO programa, nos
 * quatro alvos: JVM, Native x86-64, riscv64(qemu) e aarch64(qemu).
 */
class NulEscapeNativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // Contém: NUL simples, NUL no fim, NUL seguido de dígito ('\0' + "1"
    // concat — o caso que exige o octal de 3 dígitos), tab, CR, aspas,
    // barra invertida e UTF-8 multibyte.
    private static final String PROGRAM = """
            main() {
                println("A\\0B")
                println("tab\\there")
                println("cr\\rhere")
                println("quote\\"here")
                println("back\\\\slash")
                println("nulend\\0")
                println("x\\0" + "1")
                println("caf\\u00e9 \\u2713")
            }
            """;

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

    private String run(Path tempDir, String source, Target target, String archTag, String qemu)
            throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archTag);
        CompilationResult result = driver.compile(src, outDir, target);
        assertTrue(result.success(), "compile " + archTag + ": " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb;
        if (target == Target.JVM) {
            pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        } else {
            Path bin = outDir.resolve("Default/Main");
            assertTrue(Files.exists(bin), archTag + ": binary should exist");
            pb = qemu == null
                    ? new ProcessBuilder(bin.toString())
                    : NativeRiscv64E2ETest.qemu(qemu, bin);
        }
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
        assertEquals(0, ec, archTag + " exit code, output: " + output);
        return output;
    }

    @Test
    void nulEscapeParityJvmAndX86(@TempDir Path tempDir) throws IOException {
        String oracle = run(tempDir, PROGRAM, Target.JVM, "jvm", null);
        // Oracle JVM: NUL real, tab, CR, aspas, barra, e "café ✓".
        assertEquals("A\u0000B\n"
                + "tab\there\n"
                + "cr\rhere\n"
                + "quote\"here\n"
                + "back\\slash\n"
                + "nulend\u0000\n"
                + "x\u00001\n"
                + "caf\u00e9 \u2713", oracle, "JVM oracle");
        assertEquals(oracle, run(tempDir, PROGRAM, Target.NATIVE, "x86_64", null), "x86-64 parity");
    }

    @Test
    void nulEscapeParityRiscv64(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = run(tempDir, PROGRAM, Target.JVM, "jvm-r", null);
        assertEquals(oracle, run(tempDir, PROGRAM, Target.NATIVE_RISCV64, "riscv64", "riscv64"),
                "riscv64 parity");
    }

    @Test
    void nulEscapeParityAarch64(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = run(tempDir, PROGRAM, Target.JVM, "jvm-a", null);
        assertEquals(oracle, run(tempDir, PROGRAM, Target.NATIVE_AARCH64, "aarch64", "aarch64"),
                "aarch64 parity");
    }
}

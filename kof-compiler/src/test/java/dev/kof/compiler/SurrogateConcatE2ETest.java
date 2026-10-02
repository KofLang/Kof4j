package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §537 (A1) — a supplementary character rebuilt from its UTF-16 halves
 * (`"" + (hi as Char) + (lo as Char)`) must equal the literal `😀` on every
 * target (rule 5). Before the fix the natives encoded each half as its own
 * 3-byte WTF-8 sequence, so `==` was false and the printed bytes were invalid
 * UTF-8; the fix merges a trailing high surrogate with a leading low surrogate
 * into one 4-byte sequence in the native `kof_string_concat`. JVM/JS use UTF-16
 * and are the oracle.
 */
class SurrogateConcatE2ETest extends KofStringsSupport {

    private static final String PROG = """
            main() {
                val hi = 55357
                val lo = 56832
                val s = "" + (hi as Char) + (lo as Char)
                assert(s == "😀")
                assert(s.length == 2)
                assert(("x" + (hi as Char) + (lo as Char) + "y") == "x😀y")
                assert(("" + (hi as Char)) != "😀")
                println("ok")
            }
            """;

    @Test
    void jvmParity(@TempDir Path t) throws Exception {
        runJvm(t, PROG, "ok");
    }

    @Test
    void nativeX86(@TempDir Path t) throws Exception {
        runNative(t, PROG, "ok");
    }

    @Test
    void crossArch(@TempDir Path t) throws Exception {
        assumeToolchain("qemu-riscv64", "qemu-aarch64");
        surrogateRunQemuExpect(t, Target.NATIVE_RISCV64, "qemu-riscv64", PROG, "ok");
        surrogateRunQemuExpect(t, Target.NATIVE_AARCH64, "qemu-aarch64", PROG, "ok");
    }

    /** runQemu do support só checa exit 0; aqui o stdout importa (bytes UTF-8). */
    private void surrogateRunQemuExpect(Path tempDir, Target target, String qemu, String source,
                               String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), target + " timed out");
        assertEquals(0, p.exitValue(), target + " exit, out: " + output);
        assertEquals(expected, output, target + " output");
    }
}

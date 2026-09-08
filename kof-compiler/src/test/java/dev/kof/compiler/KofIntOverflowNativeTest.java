package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão: aritmética Int no target Native deve truncar para 32 bits a cada
 * operação (imul/add/sub/cmp em 32 bits), igual à JVM e à especificação.
 * Antes do fix, o backend emitia imulq/addq/cmpq (64 bits) para Int também,
 * então um produto Int que excedia 2^32 guardava o valor de 64 bits enquanto
 * o literal era sign-extendido de 32 bits — a comparação dava falso e o
 * println (que truncava) mostrava um número diferente do usado na comparação.
 * Ver regressions/N21 no kof-agent.
 */
class KofIntOverflowNativeTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void intMulOverflowTruncatesTo32bitsAndComparesEqualNative(@TempDir Path tmp) throws Exception {
        // 2000000000 * 33 = 66000000000; truncado a 32 bits = 1575490560 (positivo)
        runNative(tmp, """
            calc(): Int {
                var h = 2000000000
                h = h * 33
                return h
            }

            main() {
                var v = calc()
                assert(v == 1575490560)
                assert(v - 1575490560 == 0)
                println(v)
            }
            """, "1575490560");
    }

    @Test
    void intMulOverflowNegativeWrapsNative(@TempDir Path tmp) throws Exception {
        // 2000000000 * 34 = 68000000000; mod 2^32 = 3575490560; como int32 = 3575490560-2^32 = -719476736
        runNative(tmp, """
            calc(): Int {
                var h = 2000000000
                h = h * 34
                return h
            }

            main() {
                var v = calc()
                assert(v == 0 - 719476736)
                assert(v < 0)
                println(v)
            }
            """, "-719476736");
    }

    @Test
    void intAddOverflowTruncatesNative(@TempDir Path tmp) throws Exception {
        // 2147483647 + 1 = 2147483648; truncado a int32 = -2147483648
        runNative(tmp, """
            main() {
                var a = 2147483647
                var b = a + 1
                assert(b == 0 - 2147483648)
                println(b)
            }
            """, "-2147483648");
    }

    @Test
    void longArithmeticStays64bitsNative(@TempDir Path tmp) throws Exception {
        // Long não pode truncar a 32 bits
        runNative(tmp, """
            main() {
                var big: Long = 1000000000000000000
                var sum: Long = big + 1
                assert(sum == 1000000000000000001)
                var d: Long = 9007199254740993 - 9007199254740992
                assert(d == 1)
                println(sum)
            }
            """, "1000000000000000001");
    }

    @Test
    void intOverflowMatchesJvmParity(@TempDir Path tmp) throws Exception {
        // o mesmo programa em JVM e Native deve imprimir o mesmo valor truncado
        String src = """
            main() {
                var h = 2000000000
                h = h * 33
                println(h)
            }
            """;
        String jvm = runJvm(tmp, src, null);
        String nat = runNative(tmp, src, null);
        assertEquals(jvm, nat, "JVM/Native devem truncar Int igual");
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}

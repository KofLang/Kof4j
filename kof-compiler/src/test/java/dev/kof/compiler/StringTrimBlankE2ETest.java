package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §1230 — `trim()` of a string made ONLY of spaces returned a string with a
 * NEGATIVE length ({@code 0xFFFFFFFD} in the header) instead of {@code ""}: the
 * leading scan consumed every byte, so the tail index was never set, and
 * {@code len = 0 - len} came out negative. On the native target the negative
 * length turned into a ~4 GiB allocation request and the program aborted with
 * {@code Runtime error: out of memory} (measured 15/20 runs on the tip, with
 * `println("b=[" + "   ".trim() + "]")` alone).
 *
 * <p>The fix sends the all-blank path to its own label that zeroes BOTH indices,
 * so {@code len = 0} like the JVM's {@code String.trim()}.
 *
 * <p>The test also pins the empty string and a normal trim, and runs the native
 * binary ten times (the failure was timing/allocation dependent on the tip).
 */
class StringTrimBlankE2ETest {

    private static final String PROG = """
            class Use {
                static String clean(String s) { return s.trim() }
            }

            main() {
                println("a=[" + "".trim() + "]")
                println("b=[" + "   ".trim() + "]")
                println("c=[" + Use.clean("   ") + "]")
                println("d=[" + Use.clean("  x  ") + "]")
                println("e=[" + "\\t ".trim() + "]")
            }
            """;

    private static String run(List<String> cmd, int seconds) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "<timeout>";
        }
        return out;
    }

    @Test
    void theBlankTrimIsEmptyOnEveryRun(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROG);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("j"), Target.JVM);
        assertTrue(jvm.success(), "jvm: " + jvm.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String oracle = run(List.of(java, "-cp", dir.resolve("j").toString(), "Default.Main"), 30);
        assertEquals("a=[]\nb=[]\nc=[]\nd=[x]\ne=[]", oracle, "oráculo JVM");

        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        var nat = driver.compile(dir.resolve("Main.kf"), dir.resolve("n"), Target.NATIVE);
        assertTrue(nat.success(), "native: " + nat.diagnostics().getDiagnostics());
        for (int i = 1; i <= 10; i++) {
            assertEquals(oracle, run(List.of(dir.resolve("n/Default/Main").toString()), 30),
                    "execução " + i + " do binário nativo (o bug era intermitente)");
        }
    }
}

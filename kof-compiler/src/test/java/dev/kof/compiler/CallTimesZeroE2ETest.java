package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1226 RED: a CALL multiplied by the literal 0 must still BE CALLED — the Kof
 * contract evaluates the call (it can have side effects), and the arithmetic
 * keeps its value. Measured 25/11: `println(hf(1, 3) * 0 + 237)` printed 237
 * on the native target, i.e. the call's result was dropped (a `hl(…) * 0` that
 * went through the constant-fold path) — and the wrong value reached the app's
 * screen geometry.
 */
class CallTimesZeroE2ETest {

    private static final String PROG = """
            Int hf(Int a, Int b) { return a * 100 + b * 10 }

            main() {
                println(hf(1, 3) * 0 + 237)
                println(237 + hf(1, 3) * 0)
                println(hf(1, 3))
                println(hf(1, 3) * 0)
            }
            """;

    @Test
    void theInterpreterKeepsTheCall(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROG);
        String out = new CompilerDriver().interpret(List.of(src), dir, new String[0]).stdout().trim();
        assertEquals("237\n237\n130\n0", out);
    }

    @Test
    void theJvmKeepsTheCall(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROG);
        Path out = dir.resolve("jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm: " + r.diagnostics().getDiagnostics());
        assertEquals("237\n237\n130\n0", runJvm(out));
    }

    @Test
    void theNativeKeepsTheCall(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROG);
        Path out = dir.resolve("native");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "saida: " + text);
        assertEquals("237\n237\n130\n0", text);
    }

    private static String runJvm(Path out) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        return text;
    }
}

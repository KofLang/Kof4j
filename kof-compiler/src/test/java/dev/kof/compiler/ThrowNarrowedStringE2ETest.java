package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1052 — `throw e` of a narrowed `String?` emitted a RAW `athrow` of the
 * String (VerifyError: Bad type on operand stack — String is not assignable
 * to Throwable): the JVM/Android lowering wraps a String into the Kof
 * exception (`RuntimeException(String)`) only when the lowering type is
 * EXACTLY `String`, and a narrowed `String?` local still types
 * `NullableType[String]` at lowering time. The contract (EXCEPTIONS_MODEL +
 * D-NULL-INTENT): a thrown String is the exception message — a narrowed
 * optional message rethrows like `throw "msg"` does.
 */
class ThrowNarrowedStringE2ETest {

    @TempDir
    Path tempDir;

    private String runOnJvm(String source) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        // O `java` do PATH é o stub javapackages do sistema — engole o
        // VerifyError de carga e imprime a mensagem enganosa de JavaFX. O
        // java.home da própria JVM do surefire é o JDK real (25).
        // O `java.home` da JVM do surefire é o JDK real; um VerifyError de
        // carga aparece como a mensagem de fallback do launcher (JavaFX).
        String javaBin = System.getProperty("java.home") + "/bin/java";
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + output);
        return output;
    }

    /** The issue's exact program: throw a narrowed `String?` after the null check. */
    private static final String NARROWED = """
            String? why(k: Int) {
                if (k == 1) { return "boom" }
                return null
            }
            main() {
                try {
                    val e = why(1)
                    if (e != null) { throw e }
                } catch (String s) {
                    println("caught " + s)
                }
            }
            """;

    /** A plain String local throws correctly (the pre-existing face — parity guard). */
    private static final String PLAIN = """
            main() {
                try {
                    var s = "plain"
                    throw s
                } catch (String t) {
                    println("caught " + t)
                }
            }
            """;

    @Test
    void narrowedStringThrowsItsMessageJvm() throws Exception {
        String out = runOnJvm(NARROWED);
        assertEquals("caught boom", out.strip(),
                "the narrowed String? rethrows its message like a literal: " + out);
    }

    @Test
    void plainStringParityJvm() throws Exception {
        String out = runOnJvm(PLAIN);
        assertEquals("caught plain", out.strip(), "a plain String local keeps its wrap: " + out);
    }
}

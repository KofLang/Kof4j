package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1032 — a statement-level call into a JDK class whose receiver is an
 * EXTERNAL `new` (never resolved by the semantic layer) emitted a
 * statement-level POP decided by the KofIo name-match on an Unknown receiver:
 * `ByteArrayOutputStream.writeBytes` ([B)V matched the io file face (Bool) →
 * POP over nothing → ASM COMPUTE_FRAMES NegativeArraySizeException (COMP002).
 *
 * <p>The fix reflects the JDK method from the receiver's IR SLOT type (a
 * java.* class) BEFORE the name-match — a void method gets no POP, a
 * value method gets exactly one. The exact issue repro prints "2".
 */
class JdkVoidStatementE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** The issue's exact reproduction. */
    private static final String VOID_FACE = """
            import java.io.ByteArrayOutputStream

            main() {
                val out = new ByteArrayOutputStream()
                out.writeBytes("hi".getBytes())
                println(out.size())
            }
            """;

    /** A VALUE-returning JDK call as a bare statement (one POP, int width). */
    private static final String VALUE_FACE = """
            import java.io.ByteArrayOutputStream

            main() {
                val out = new ByteArrayOutputStream()
                out.writeBytes("hi".getBytes())
                out.size()
                println(out.size())
            }
            """;

    @Test
    void voidJdkCallAsStatementCompilesAndRuns() throws Exception {
        Path root = tmp.resolve("jdk-void");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), VOID_FACE);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals("2", stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void valueJdkCallAsStatementCompiles() throws Exception {
        Path root = tmp.resolve("jdk-value");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), VALUE_FACE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals("2", result.stdout().strip());
    }
}

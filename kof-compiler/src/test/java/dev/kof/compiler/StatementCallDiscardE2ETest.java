package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §787 — an expression statement decided whether to discard a value from the
 * statement's INFERRED type, which could disagree with the call actually
 * emitted: {@code out.writeBytes(b)} ({@code ([B)V}) got a POP with nothing
 * on the stack and crashed the frame computation. A statement that ends in a
 * call now discards exactly what that call returns (nothing, POP or POP2).
 */
class StatementCallDiscardE2ETest {

    private static final String SRC = """
        import java.io.ByteArrayOutputStream
        import java.lang.StringBuilder
        Long big() { return 5L }
        main() {
            val out = new ByteArrayOutputStream()
            val b = "hi".getBytes()
            out.writeBytes(b)
            println(out.size())
            val sb = new StringBuilder()
            var i = 0
            while (i < 3) {
                sb.append("x")
                i = i + 1
            }
            println(sb.toString())
            big()
            println("done")
        }
        """;

    @TempDir Path tmp;

    @Test
    void jvmDiscardsWhatTheCallReturns() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("2\nxxx\ndone\n", buf.toString());
    }

    @Test
    void scriptMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("2\nxxx\ndone", r.stdout().strip());
    }
}

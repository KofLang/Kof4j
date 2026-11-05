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
 * §810 — {@code catch (String e)} caught only {@code RuntimeException} on the
 * JVM and in the Script interpreter, so a CHECKED exception raised by a Java
 * call ({@code URISyntaxException}, {@code ClassNotFoundException},
 * {@code UnknownHostException}) escaped a catch that Kof code cannot
 * otherwise write: Kof has no checked exceptions (EXCEPTIONS_MODEL §6). The
 * String catch now matches every {@code Exception}; a typed catch written
 * before it still wins, and a Kof {@code throw "msg"} keeps its message.
 */
class CatchStringCheckedExceptionE2ETest {

    private static final String SRC = """
            import java.net.URI
            import java.io.IOException
            import java.lang.Class
            main() {
                try {
                    URI("a b")
                    println("parsed")
                } catch (String e) {
                    println("uri: " + e.startsWith("Illegal character"))
                }
                try {
                    Class.forName("no.such.Type")
                } catch (String e) {
                    println("class: " + e)
                }
                try {
                    throw "kof"
                } catch (String e) {
                    println("thrown: " + e)
                }
            }
            """;

    private static final String GOLDEN = "uri: true\nclass: no.such.Type\nthrown: kof\n";

    @TempDir Path tmp;

    @Test
    void jvmStringCatchCatchesCheckedJavaExceptions() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
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
        assertEquals(GOLDEN, buf.toString());
    }

    @Test
    void scriptStringCatchMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout());
    }
}

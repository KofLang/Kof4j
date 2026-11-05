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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §788 — null narrowing follows De Morgan: the right side of
 * {@code t == null || t.length() == 0} sees {@code t} non-null, and so does the
 * code after {@code if (t == null || ...) { throw }} (both were SEM049). The
 * old table also narrowed the ELSE of {@code x == null && y}, which is unsound
 * ({@code x} may be null there); that now stays SEM049.
 */
class NullNarrowingOrE2ETest {

    private static final String SRC = """
        String f(t: String?) {
            if (t == null || t.length() == 0) { throw "empty" }
            return t.trim()
        }
        Bool blank(t: String?) {
            return t == null || t.trim().length() == 0
        }
        Int twice(n: Int?) {
            if (n == null || n < 0) { return 0 }
            return n * 2
        }
        main() {
            println(f(" a "))
            println(blank(null))
            println(blank(" "))
            println(blank("x"))
            println(twice(null))
            println(twice(-1))
            println(twice(21))
        }
        """;

    @TempDir Path tmp;

    @Test
    void jvmNarrowsBothFacesOfOr() throws Exception {
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
        assertEquals("a\ntrue\ntrue\nfalse\n0\n0\n42\n", buf.toString());
    }

    @Test
    void scriptMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("a\ntrue\ntrue\nfalse\n0\n0\n42", r.stdout().strip());
    }

    @Test
    void theElseOfAnAndDoesNotNarrow() throws Exception {
        Path source = tmp.resolve("Bad.kf");
        Files.writeString(source, """
            Int f(x: String?, y: Bool) {
                if (x == null && y) { return 0 } else { return x.length() }
            }
            main() { println(f(null, false)) }
            """);
        CompilationResult r = new CompilerDriver().compile(source, tmp.resolve("bad"), Target.JVM);
        assertFalse(r.success(), "x may be null in the else of (x == null && y)");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM049".equals(d.code())),
                "expected SEM049: " + r.diagnostics().getDiagnostics());
    }
}

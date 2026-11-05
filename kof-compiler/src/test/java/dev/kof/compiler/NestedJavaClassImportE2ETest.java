package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §791 — {@code import java.net.http.HttpRequest.BodyPublishers} was split as
 * package {@code java.net.http.HttpRequest} + class {@code BodyPublishers}, a
 * class that does not exist ({@code HttpRequest$BodyPublishers} does), so every
 * member was SEM025. An import whose top-level split does not exist now names
 * the nested class, in the analyzer and in the lowering (which had its own
 * copy of the import resolution).
 */
class NestedJavaClassImportE2ETest {

    @TempDir Path tmp;

    @Test
    void anImportedNestedJdkClassResolvesItsMembers() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
            import java.net.http.HttpRequest.BodyPublishers
            import java.lang.Thread.State
            main() {
                val p = BodyPublishers.ofString("abc")
                println(p.contentLength())
                println(State.NEW.name())
                println(State.valueOf("RUNNABLE").ordinal())
            }
            """);
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
        assertEquals("3\nNEW\n1\n", buf.toString());
    }
}

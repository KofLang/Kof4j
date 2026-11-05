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
 * §778 — inside a class, an unqualified call to the class's own method named
 * like a stdlib builtin ({@code query(sql)}, {@code transaction(t)}) was
 * taken by the builtin: {@code query} called the web-context function
 * (returned {@code null}), {@code transaction} the db one (SEM033 on the void
 * value, or a frame crash). The method of the enclosing class now wins, as an
 * implicit-this call does in Java — the class-member face of the #931/#932
 * shadowing law.
 */
class ClassMemberShadowsBuiltinE2ETest {

    private static final String SRC = """
        class Store {
            String query(String sql) { return "own-query:" + sql }
            String transaction(String t) { return "own-tx:" + t }
            String run() {
                var r = query("select 1")
                var t = transaction("a")
                return r + " " + t
            }
        }
        class Child extends Store {
            String again() { return query("child") }
        }
        main() {
            println(Store().run())
            println(Child().again())
        }
        """;

    private static final String GOLDEN = "own-query:select 1 own-tx:a\nown-query:child\n";

    @TempDir Path tmp;

    @Test
    void jvmCallsTheOwnMethod() throws Exception {
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
        assertEquals(GOLDEN, buf.toString());
    }

    @Test
    void scriptCallsTheOwnMethod() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN.strip(), r.stdout().strip());
    }
}

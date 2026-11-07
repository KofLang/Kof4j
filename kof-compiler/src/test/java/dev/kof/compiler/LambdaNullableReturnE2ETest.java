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
 * §785 — a lambda whose return type is nullable ({@code () -> pass()} with
 * {@code String? pass()}) implemented {@code kof.Function0_NCString} but
 * declared {@code Object invoke()}: the interface method
 * {@code String invoke()} had no implementation and the first call died with
 * {@code AbstractMethodError}. The invoke return now keeps the nullable's
 * inner type, like the interface.
 */
class LambdaNullableReturnE2ETest {

    @TempDir Path tmp;

    @Test
    void aLambdaWithANullableReturnImplementsItsInterface() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
            String? pass() { return null }
            String? some() { return "x" }
            Int? count() { return 3 }
            class F {
                () -> String? run
                constructor(r: () -> String?) { run = r }
            }
            main() {
                var f = F(() -> pass()).run
                var g = F(() -> some()).run
                println(f() == null)
                println(g())
                val n = () -> count()
                println(n())
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
        assertEquals("true\nx\n3\n", buf.toString());
    }
}

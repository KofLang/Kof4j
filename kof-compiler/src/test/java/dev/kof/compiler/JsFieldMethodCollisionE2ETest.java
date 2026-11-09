package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1107 — on JS, a class with a FIELD and a METHOD of the same name must not
 * collide: the field's instance property shadows the prototype method and
 * `w.label()` threw `TypeError: w.label is not a function` (the field emit
 * used the raw name). JVM and Script are the oracle: `w.label()` prints
 * "method", `w.label` prints "field".
 */
class JsFieldMethodCollisionE2ETest {

    private static final String SRC = """
            class W {
                String label
                W() { this.label = "field" }
                String label() { return "method" }
            }

            main() {
                val w = W()
                println(w.label())
                println(w.label)
            }
            """;

    private static final String OUT = "method\nfield";

    @TempDir
    Path tmp;

    @Test
    void fieldAndMethodOfTheSameNameCoexistOnJs() throws Exception {
        assertEquals(OUT, js("col-js", SRC));
    }

    @Test
    void fieldAndMethodOfTheSameNameOnJvmIsTheOracle() throws Exception {
        assertEquals(OUT, jvm("col-jvm", SRC));
    }

    private String jvm(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8).strip();
    }

    private String js(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        return buf.toString(StandardCharsets.UTF_8).strip();
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }
}

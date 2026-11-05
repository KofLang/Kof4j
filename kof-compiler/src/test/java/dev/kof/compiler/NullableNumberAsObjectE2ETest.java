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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §816 — {@code v as Object} on a {@code Int?}/{@code Long?} (narrowed by
 * {@code if (v == null) return} or not) boxed the value again: a {@code T?}
 * number is already its wrapper (D-NULL-INTENT), so the JVM asked for
 * {@code Integer.valueOf(Integer)} and died with {@code NoSuchMethodError},
 * only when the value was present. Script and JS already printed the golden.
 */
class NullableNumberAsObjectE2ETest {

    private static final String SRC = """
            class Params {
                List<Object> vals = listOf()
                void add(Object o) { vals.add(o) }
                void bindIntOrNull(Int? v) {
                    if (v == null) { return }
                    add(v as Object)
                }
                void bindLongOrNull(Long? v) {
                    if (v == null) { return }
                    add(v as Object)
                }
            }
            Int? score() { return 7 }
            main() {
                val p = Params()
                p.bindIntOrNull(score())
                p.bindLongOrNull(9L)
                p.bindIntOrNull(null)
                println(p.vals)
                Int? x = 5
                if (x != null) { println(x as Object) }
                Double? d = 1.5
                Object od = d as Object
                println(od)
            }
            """;

    private static final String GOLDEN = "[7, 9]\n5\n1.5\n";

    @TempDir Path tmp;

    private Path source() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);
        return src;
    }

    @Test
    void jvmPassesTheWrapperWithoutBoxingAgain() throws Exception {
        Path out = tmp.resolve("jvm");
        CompilationResult r = new CompilerDriver().compile(source(), out, Target.JVM);
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
    void scriptMatchesTheJvm() throws Exception {
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source()), tmp, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout());
    }

    @Test
    void jsMatchesTheJvm() throws Exception {
        Path out = tmp.resolve("js");
        CompilationResult r = new CompilerDriver().compile(source(), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js output: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8));
    }
}

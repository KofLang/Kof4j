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
 * {@code m.get(k) as Long} on a {@code Map<String, Long>} crashed frame
 * computation at compile time (COMP002): the source is a {@code Long?}, the
 * boxed wrapper on the stack, but the cast took the numeric-conversion branch
 * as if it were a raw {@code long}. The box is now opened first.
 */
class CastNullableNumberE2ETest {

    @TempDir Path tmp;

    @Test
    void castingANullableNumberOpensTheBox() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            class C {
                static Long sum(m: Map<String, Long>) {
                    var t = 0L
                    for (var k in m.keys()) {
                        t = t + (m.get(k) as Long)
                    }
                    return t
                }
            }
            Int plusOne(m: Map<String, Int>) = (m.get("a") as Int) + 1
            Double widened(m: Map<String, Long>) = m.get("a") as Double
            main() {
                println(C.sum(mapOf("a", 2L)))
                println(plusOne(mapOf("a", 4)))
                println(widened(mapOf("a", 3L)))
            }
            """);
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
        assertEquals("2\n5\n3.0\n", buf.toString());
    }
}

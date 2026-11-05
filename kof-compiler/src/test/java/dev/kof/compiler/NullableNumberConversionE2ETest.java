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
 * {@code n.toDouble()} on a {@code Long?} narrowed by a null check crashed
 * frame computation at compile time: the numeric conversion (the alias of
 * {@code as}) widened the boxed {@code Long} reference as if it were a raw
 * {@code long}. The receiver is now unboxed first, like every other
 * primitive consumer of a narrowed nullable.
 */
class NullableNumberConversionE2ETest {

    @TempDir Path tmp;

    @Test
    void aNarrowedNullableNumberConverts() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            Long? some(k: Int) {
                if (k > 0) { return 5L }
                return null
            }
            Int? count(k: Int) {
                if (k > 0) { return 7 }
                return null
            }
            main() {
                val n = some(1)
                if (n != null) { println(n.toDouble()) }
                val c = count(1)
                if (c != null) {
                    println(c.toLong() + 1L)
                    println(c.toDouble())
                }
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
        assertEquals("5.0\n8\n7.0\n", buf.toString());
    }
}

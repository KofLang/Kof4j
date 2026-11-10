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
 * {@code throw e} where {@code e} is a {@code String?} narrowed by
 * {@code if (e != null)} emitted a raw {@code athrow} of the String
 * ({@code VerifyError: String is not assignable to Throwable}): the JVM
 * wrapping into the Kof exception only looked for a plain {@code String}.
 */
class ThrowNarrowedStringE2ETest {

    @TempDir Path tmp;

    @Test
    void aNarrowedNullableStringIsThrownAsAKofException() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            String? why(k: Int) {
                if (k == 1) { return "boom" }
                return null
            }
            main() {
                try {
                    val e = why(1)
                    if (e != null) { throw e }
                } catch (String s) {
                    println("caught " + s)
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
        assertEquals("caught boom\n", buf.toString());
    }
}

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

class KofFormatterCallResultTest {

    private static final String SRC = """
            adder(Int a): (Int) -> Int {
                return (b: Int) -> a + b
            }

            main() {
                println(adder(2)(3))
                var fs = listOf((x: Int) -> x * 2)
                println(fs.get(0)(21))
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void callingTheResultOfACallKeepsItsShape() throws Exception {
        String out = KofFormatter.format(SRC, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("println(adder(2)(3))"), out);
        assertTrue(out.contains("println(fs.get(0)(21))"), out);
        assertFalse(out.contains(").("), out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"));
        assertEquals(run("original", SRC), run("formatted", out));
    }

    private String run(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), name + ": " + r.diagnostics().getDiagnostics() + "\n" + src);
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}

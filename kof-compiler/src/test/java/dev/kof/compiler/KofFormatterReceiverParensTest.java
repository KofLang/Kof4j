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
 * §786 — the formatter printed the receiver of a call, field access or index
 * without its parentheses: {@code ("" + city).trim()} became
 * {@code "" + city.trim()} and {@code (a + "," + b).split(",")} became
 * {@code a + "," + b.split(",")}, a different program that often still
 * compiled. {@code kof fmt -w} silently changed behavior.
 */
class KofFormatterReceiverParensTest {

    private static final String SRC = """
            class Box {
                Int v = 7
            }

            main() {
                var city = " bh "
                println(("" + city).trim())
                var a = "x"
                var b = "y"
                for (var part in (a + "," + b).split(",")) {
                    println(part)
                }
                Object o = Box()
                println((o as Box).v)
                var n = 5
                println((n * 2).toString().length())
                println((-n).toString())
                println(((a + b) as String).length())
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void aParenthesisedReceiverKeepsItsParentheses() throws Exception {
        String out = KofFormatter.format(SRC, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("(\"\" + city).trim()"), out);
        assertTrue(out.contains("(a + \",\" + b).split(\",\")"), out);
        assertTrue(out.contains("(o as Box).v"), out);
        assertTrue(out.contains("(-n).toString()"), out);
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

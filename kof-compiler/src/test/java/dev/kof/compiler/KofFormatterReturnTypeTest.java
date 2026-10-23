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

class KofFormatterReturnTypeTest {

    private static final String SRC = """
            wrap<X>(X x): List<X> {
                return listOf(x)
            }

            clock(Long millis): () -> Long {
                return () -> millis
            }

            counter(Long start): () -> Long {
                return () -> start + 1
            }

            class Box {
                Int n
                constructor(Int v) {
                    n = v
                }
                (Int) -> Int adder() {
                    return (m: Int) -> n + m
                }
                (Int) -> Int scaler() {
                    return (m: Int) -> n * m
                }
            }

            main() {
                println(wrap("a"))
                var c = clock(7)
                println(c())
                var k = counter(1)
                println(k())
                var b = Box(3)
                var add = b.adder()
                println(add(4))
                var scale = b.scaler()
                println(scale(5))
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void genericAndFunctionTypedReturnsComeAfterTheParameters() throws Exception {
        String out = KofFormatter.format(SRC, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("wrap<X>(x: X): List<X> = listOf(x)"), out);
        assertTrue(out.contains("clock(millis: Long): () -> Long = () -> millis"), out);
        assertTrue(out.contains("    adder(): (Int) -> Int = (m: Int) -> n + m"), out);
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

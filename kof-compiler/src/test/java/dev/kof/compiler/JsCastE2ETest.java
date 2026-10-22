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

class JsCastE2ETest {

    private static final String SRC = """
            class Stack<T> {
                List<T> items = listOf()
                push(T x) {
                    items.add(x)
                }
                pop(): T = items.remove(items.size - 1)
                size(): Int = items.size
            }

            record Item(Int n)

            class Counter {
                Int calls = 0
                public constructor() {}
                next(): Object {
                    calls++
                    return Item(calls)
                }
                text(): Object {
                    calls++
                    return "t" + calls
                }
            }

            main() {
                var s = Stack<String>()
                s.push("a")
                s.push("b")
                println(s.pop())
                println(s.size())
                var r = Stack<Item>()
                r.push(Item(1))
                r.push(Item(2))
                println(r.pop())
                println(r.size())
                var c = Counter()
                var i = c.next() as Item
                println(i)
                var t = c.text() as String
                println(t)
                println(c.calls)
                Object n = 42
                try {
                    String bad = n as String
                    println("sem erro " + bad)
                } catch (Exception e) {
                    println("cast falhou")
                }
                var names = Stack<String>()
                names.push("z")
                var all = listOf(names.pop(), "y")
                println(all)
            }
            """;

    private static final String OUT = "b\n1\nItem[n=2]\n1\nItem[n=1]\nt2\n2\ncast falhou\n[z, y]";

    @TempDir
    Path tmp;

    @Test
    void castsRunOnceAndFailingStringCastThrowsOnJs() throws Exception {
        Path file = write("js");
        Path out = tmp.resolve("js-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void castsRunOnceAndFailingStringCastThrowsOnJvm() throws Exception {
        Path file = write("jvm");
        Path out = tmp.resolve("jvm-out");
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
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8).strip());
    }

    private Path write(String name) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        return file;
    }
}

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

class JsDoWhileCallTailE2ETest {

    private static final String SRC = """
            class Log {
                List<String> lines = listOf()
                public constructor() {}
                add(String s) {
                    lines.add(s)
                }
            }

            next(Int n): Int = n + 3

            main() {
                var i = 0
                do {
                    i += 2
                    println(i)
                } while (i < 6)
                var xs = listOf<Int>()
                do {
                    xs.add(i)
                    i--
                } while (i > 3)
                println(xs)
                var k = 0
                do {
                    k += 1
                    if (k == 2) {
                        continue
                    }
                    print(k + ";")
                } while (k < 4)
                println("")
                var log = Log()
                var j = 0
                do {
                    j = next(j)
                    log.add("j" + j)
                } while (next(j) < 12)
                println(log.lines)
                var m = mapOf("a", 1)
                do {
                    m.put("b", 2)
                } while (m.size < 2)
                println(m.size)
            }
            """;

    private static final String OUT = "2\n4\n6\n[6, 5, 4]\n1;3;4;\n[j3, j6, j9]\n2";

    private static final String NESTED = """
            main() {
                var a = 0
                do {
                    var b = 0
                    do {
                        b++
                        print(a * 10 + b)
                        print(",")
                    } while (b < 2)
                    var w = 0
                    while (w < 2) {
                        w++
                    }
                    for (var f = 0; f < 2; f++) {
                        print("f")
                    }
                    a++
                } while (a < 2)
                println("")
                println(a)
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void doWhileEndingInCallCompilesOnJs() throws Exception {
        assertEquals(OUT, js("dw-js", SRC));
        assertEquals("1,2,ff11,12,ff\n2", js("nested-js", NESTED));
    }

    @Test
    void doWhileEndingInCallRunsOnJvm() throws Exception {
        assertEquals(OUT, jvm("dw-jvm", SRC));
        assertEquals("1,2,ff11,12,ff\n2", jvm("nested-jvm", NESTED));
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

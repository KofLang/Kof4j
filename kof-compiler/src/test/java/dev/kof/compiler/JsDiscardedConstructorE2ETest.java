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

class JsDiscardedConstructorE2ETest {

    private static final String SRC = """
            class Reg {
                static Int made = 0
                public constructor(String tag) {
                    made++
                    println("new " + tag)
                }
            }

            class Strict {
                public constructor(Int n) {
                    if (n < 0) {
                        throw "negativo"
                    }
                }
            }

            main() {
                Reg("a")
                Reg("b")
                for (var i = 0; i < 2; i++) {
                    Reg("loop")
                }
                var make = () -> {
                    Reg("lambda")
                }
                make()
                println(Reg.made)
                try {
                    Strict(-1)
                    println("sem erro")
                } catch (String e) {
                    println("pego " + e)
                }
            }
            """;

    private static final String OUT = "new a\nnew b\nnew loop\nnew loop\nnew lambda\n5\npego negativo";

    @TempDir
    Path tmp;

    @Test
    void discardedConstructorCallRunsOnJs() throws Exception {
        assertEquals(OUT, js("ctor-js", SRC));
    }

    @Test
    void discardedConstructorCallRunsOnJvm() throws Exception {
        assertEquals(OUT, jvm("ctor-jvm", SRC));
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

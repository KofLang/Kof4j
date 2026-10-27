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

class JsMultiCatchE2ETest {

    private static final String SRC = """
            boom(Int k) {
                if (k == 1) {
                    throw "texto"
                }
                if (k == 2) {
                    var xs = listOf(1)
                    println(xs.get(9) > 0)
                }
            }

            check(Int k): String {
                try {
                    boom(k)
                    return "ok"
                } catch (String e) {
                    return "pego"
                } catch (Exception e) {
                    return "outro"
                } finally {
                    print("[f" + k + "]")
                }
            }

            main() {
                for (var k = 0; k < 3; k++) {
                    try {
                        boom(k)
                        println("ok " + k)
                    } catch (String e) {
                        println("str " + k)
                    } catch (Exception e) {
                        println("exc " + k)
                    }
                }
                println(check(0))
                println(check(1))
                try {
                    throw "z"
                } catch (Exception e) {
                    println("primeiro")
                } catch (String e) {
                    println("segundo")
                }
            }
            """;

    private static final String OUT = "ok 0\nstr 1\nstr 2\n[f0]ok\n[f1]pego\nprimeiro";

    @TempDir
    Path tmp;

    @Test
    void tryWithSeveralCatchesCompilesOnJs() throws Exception {
        assertEquals(OUT, js("mc-js", SRC));
    }

    @Test
    void tryWithSeveralCatchesRunsOnJvm() throws Exception {
        assertEquals(OUT, jvm("mc-jvm", SRC));
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

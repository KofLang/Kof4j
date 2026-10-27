package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LambdaImplicitFieldE2ETest {

    private static final String SRC = """
            class Counter {
                Int n = 0
                Long total = 0L
                String log = ""
                List<Int> seen = listOf()
                public constructor() {}

                run() {
                    var inc = () -> {
                        n++
                        ++n
                        n += 10
                        total = total + n
                        log = log + "x"
                        seen.add(n)
                    }
                    inc()
                    inc()
                    println(n)
                    println(total)
                    println(log)
                    println(seen)
                    var read = () -> n * 2
                    n = 100
                    println(read())
                    var post = () -> n--
                    println(post())
                    println(n)
                    var outer = () -> {
                        var inner = () -> {
                            n = n + 1000
                        }
                        inner()
                    }
                    outer()
                    println(n)
                    var n = 5
                    var shadow = () -> {
                        n = n + 1
                    }
                    shadow()
                    println(n)
                    println(this.n)
                }
            }

            class Multiplier {
                Int factor
                public constructor(Int f) {
                    factor = f
                }
                get(): (Int) -> Int {
                    return (x: Int) -> x * factor
                }
            }

            main() {
                Counter().run()
                var m = Multiplier(3)
                var f = m.get()
                println(f(5))
            }
            """;

    private static final String OUT = "24\n36\nxx\n[12, 24]\n200\n100\n99\n1099\n6\n1099\n15";

    private static final String INHERITED = """
            class Base {
                Int hits = 0
                public constructor() {}
                bump() {
                    hits++
                }
                label(): String = "b" + hits
            }

            class Child extends Base {
                public constructor() {
                    super()
                }
                run() {
                    var f = () -> {
                        hits++
                        bump()
                        hits += 10
                    }
                    f()
                    f()
                    println(hits)
                    var g = () -> label()
                    println(g())
                }
            }

            main() {
                Child().run()
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void implicitFieldInLambdaJvm() throws Exception {
        assertEquals(OUT, jvm("field-jvm", SRC));
        assertEquals("24\nb24", jvm("inherited-jvm", INHERITED));
    }

    @Test
    void implicitFieldInLambdaScript() throws Exception {
        assertEquals(OUT, script("field-script", SRC));
        assertEquals("24\nb24", script("inherited-script", INHERITED));
    }

    @Test
    void implicitFieldInLambdaJs() throws Exception {
        assertEquals(OUT, js("field-js", SRC));
        assertEquals("24\nb24", js("inherited-js", INHERITED));
    }

    @Test
    void implicitFieldInLambdaNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(OUT, nativeRun("field-native", SRC));
        assertEquals("24\nb24", nativeRun("inherited-native", INHERITED));
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

    private String script(String name, String src) throws Exception {
        Path file = write(name, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        return r.stdout().replace("\r\n", "\n").strip();
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

    private String nativeRun(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.NATIVE);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), o);
        return o.strip();
    }

    private static boolean hasToolchain() {
        try {
            return new ProcessBuilder("sh", "-c", "command -v as && command -v ld").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }
}

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

class CapturedIncrementE2ETest {

    private static final String CAPTURED = """
            counter(): () -> Int {
                var n = 0
                return () -> {
                    n++
                    return n
                }
            }

            main() {
                var a = counter()
                a()
                a()
                println(a())
                var k = 0
                var inc = () -> {
                    k++
                    ++k
                }
                inc()
                println(k)
                var d = 10
                var dec = () -> d--
                println(dec())
                println(d)
                var pre = () -> --d
                println(pre())
                Long big = 5L
                var lb = () -> {
                    big++
                }
                lb()
                println(big)
                Double x = 0.5
                var dx = () -> x++
                println(dx())
                println(x)
                var hits = 0
                listOf(1, 2, 3).forEach((v: Int) -> {
                    hits++
                })
                println(hits)
            }
            """;

    private static final String CAPTURED_OUT = "3\n2\n10\n9\n8\n6\n0.5\n1.5\n3";

    private static final String FOR_UPDATE = """
            class Walker {
                Int pos = 0
                public constructor() {}
                walk(): Int {
                    var steps = 0
                    for (pos = 0; pos < 5; pos++) {
                        steps += 1
                    }
                    return steps
                }
            }

            main() {
                println(Walker().walk())
                var total = 0
                var bump = () -> {
                    total += 1
                }
                for (var i = 0; i < 3; i++) {
                    bump()
                }
                println(total)
                var j = 0
                var reset = () -> {
                    j = j
                }
                for (j = 0; j < 4; j++) {
                    reset()
                }
                println(j)
            }
            """;

    private static final String FOR_UPDATE_OUT = "5\n3\n4";

    private static final String NARROW = """
            class Reg {
                Byte b = 127 as Byte
                Short s = 32767 as Short
                Char c = 65535 as Char
                public constructor() {}
            }

            main() {
                Byte b = 127 as Byte
                b++
                println(b)
                Short s = -32768 as Short
                s--
                Int si = s
                println(si)
                Char c = 65535 as Char
                ++c
                println(c as Int)
                Byte m = -128 as Byte
                println(m--)
                println(m)
                var r = Reg()
                r.b++
                r.s++
                r.c++
                println(r.b)
                println(r.s)
                println(r.c as Int)
                Byte cap = 127 as Byte
                var bump = () -> {
                    cap++
                }
                bump()
                println(cap)
                Byte i = 126 as Byte
                var n = 0
                for (; i > 0; i++) {
                    n += 1
                }
                println(n)
                println(i)
            }
            """;

    private static final String NARROW_OUT = "-128\n32767\n0\n-128\n127\n-128\n-32768\n0\n-128\n2\n-128";

    @TempDir
    Path tmp;

    @Test
    void incrementOfCapturedVariableJvm() throws Exception {
        assertEquals(CAPTURED_OUT, jvm("cap-jvm", CAPTURED));
        assertEquals(FOR_UPDATE_OUT, jvm("loop-jvm", FOR_UPDATE));
        assertEquals(NARROW_OUT, jvm("narrow-jvm", NARROW));
    }

    @Test
    void incrementOfCapturedVariableScript() throws Exception {
        assertEquals(CAPTURED_OUT, script("cap-script", CAPTURED));
        assertEquals(FOR_UPDATE_OUT, script("loop-script", FOR_UPDATE));
        assertEquals(NARROW_OUT, script("narrow-script", NARROW));
    }

    @Test
    void incrementOfCapturedVariableJs() throws Exception {
        assertEquals(CAPTURED_OUT, js("cap-js", CAPTURED));
        assertEquals(FOR_UPDATE_OUT, js("loop-js", FOR_UPDATE));
        assertEquals(NARROW_OUT, js("narrow-js", NARROW));
    }

    @Test
    void incrementOfCapturedVariableNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(CAPTURED_OUT, nativeRun("cap-native", CAPTURED));
        assertEquals(FOR_UPDATE_OUT, nativeRun("loop-native", FOR_UPDATE));
        assertEquals(NARROW_OUT, nativeRun("narrow-native", NARROW));
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

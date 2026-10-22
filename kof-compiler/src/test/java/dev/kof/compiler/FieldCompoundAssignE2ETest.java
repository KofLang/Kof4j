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

class FieldCompoundAssignE2ETest {

    private static final String SRC = """
            record P(Int x)

            class Doc {
                String text = "a"
                Int edits = 0
                public constructor() {}
                add(String more) {
                    this.text += more
                    edits++
                }
                later(): () -> Void {
                    return () -> {
                        this.text += "!"
                    }
                }
            }

            class Shelf {
                Doc doc = Doc()
                Int calls = 0
                public constructor() {}
                pick(): Doc {
                    calls++
                    return doc
                }
            }

            main() {
                var d = Doc()
                d.text += "b"
                d.text += 1
                d.text += 'c'
                d.text += 2.5
                d.text += true
                d.text += P(3)
                println(d.text)
                d.add("z")
                d.later()()
                println(d.text)
                var sh = Shelf()
                sh.pick().text += "q"
                sh.doc.text += "r"
                println(sh.doc.text)
                println(sh.calls)
            }
            """;

    private static final String OUT = "ab1c2.5trueP[x=3]\nab1c2.5trueP[x=3]z!\naqr\n1";

    private static final String COMPOUND = """
            class R {
                Byte b = 127 as Byte
                Short s = 32767 as Short
                Long l = 1L
                String text = "t"
                static Byte sb = 127 as Byte
                static Long sl = 1L
                static String st = "x"
                public constructor() {}
                inside() {
                    b += 1
                    s -= 1
                    l <<= 2
                    text += 'k'
                    sb += 1
                    sl <<= 4
                    st += 'm'
                }
            }

            main() {
                var r = R()
                r.b += 1
                r.s += 1
                r.l <<= 3
                r.l >>= 1
                r.text += 'c'
                R.sb += 1
                R.sl <<= 2
                R.st += 'c'
                R.st += 1
                println(r.b)
                println(r.s)
                println(r.l)
                println(r.text)
                println(R.sb)
                println(R.sl)
                println(R.st)
                r.inside()
                println(r.b)
                println(r.s)
                println(r.l)
                println(r.text)
                println(R.sb)
                println(R.sl)
                println(R.st)
            }
            """;

    private static final String COMPOUND_OUT = "-128\n-32768\n4\ntc\n-128\n4\nxc1\n-127\n32767\n16\ntck\n-127\n64\nxc1m";

    @TempDir
    Path tmp;

    @Test
    void stringFieldPlusAssignThroughReceiverJvm() throws Exception {
        assertEquals(OUT, jvm("strfield-jvm", SRC));
        assertEquals(COMPOUND_OUT, jvm("compound-jvm", COMPOUND));
    }

    @Test
    void stringFieldPlusAssignThroughReceiverScript() throws Exception {
        assertEquals(OUT, script("strfield-script", SRC));
        assertEquals(COMPOUND_OUT, script("compound-script", COMPOUND));
    }

    @Test
    void stringFieldPlusAssignThroughReceiverJs() throws Exception {
        assertEquals(OUT, js("strfield-js", SRC));
        assertEquals(COMPOUND_OUT, js("compound-js", COMPOUND));
    }

    @Test
    void stringFieldPlusAssignThroughReceiverNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(OUT, nativeRun("strfield-native", SRC));
        assertEquals(COMPOUND_OUT, nativeRun("compound-native", COMPOUND));
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

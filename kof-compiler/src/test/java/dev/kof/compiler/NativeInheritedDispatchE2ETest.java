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

import static org.junit.jupiter.api.Assertions.*;

class NativeInheritedDispatchE2ETest {

    private static final String SRC = """
            class Box {
                Int v
                public constructor(Int v) {
                    this.v = v
                }
                toString(): String = "Box(" + v + ")"
                tag(Int n): String = "i" + n
                tag(String s): String = "s" + s
            }

            class Mid extends Box {
                public constructor(Int v) {
                    super(v)
                }
                tag(String s): String = "mid-" + s
            }

            class Both extends Box {
                public constructor(Int v) {
                    super(v)
                }
                tag(Int n): String = "bi" + n
                tag(String s): String = "bs" + s
            }

            class Sub extends Mid {
                public constructor(Int v) {
                    super(v)
                }
            }

            main() {
                var m = Mid(1)
                println(m.tag("a"))
                Box b = m
                println(b.tag("c"))
                println(b.tag(2))
                Box o = Both(2)
                println(o.tag(3))
                println(o.tag("d"))
                Box s = Sub(6)
                println(s.tag("e"))
                println(s.tag(4))
                var sub = Sub(7)
                println(sub)
                println("x=" + sub)
                println(listOf(sub))
            }
            """;

    private static final String THREE_LEVELS = """
            class A {
                public constructor() {}
                show(): String = "A"
                other(): String = "oA"
            }

            class B extends A {
                public constructor() {
                    super()
                }
                show(): String = "B"
            }

            class C extends B {
                public constructor() {
                    super()
                }
            }

            main() {
                A x = C()
                println(x.show())
                println(x.other())
                println(C().show())
            }
            """;

    private static final String OUT = "mid-a\nmid-c\ni2\nbi3\nbsd\nmid-e\ni4\nBox(7)\nx=Box(7)\n[Box(7)]";

    @TempDir
    Path tmp;

    @Test
    void inheritedDispatchMatchesJvm() throws Exception {
        assertEquals(OUT, jvm("jvm", SRC));
        assertEquals("B\noA\nB", jvm("jvm3", THREE_LEVELS));
    }

    @Test
    void inheritedDispatchOnNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(OUT, nativeRun("native", SRC));
        assertEquals("B\noA\nB", nativeRun("native3", THREE_LEVELS));
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

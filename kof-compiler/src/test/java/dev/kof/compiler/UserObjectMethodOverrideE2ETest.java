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

class UserObjectMethodOverrideE2ETest {

    private static final String SRC = """
            record Money(Long cents) {
                toString(): String = "R$" + cents
            }

            record Tag(String name, Int n) {
                hashCode(): Int = 7
                equals(Object o): Bool = true
            }

            record Plain(Int a)

            class Box {
                Int v
                public constructor(Int v) {
                    this.v = v
                }
                toString(): String = "Box(" + v + ")"
            }

            class Point {
                Int x
                Int y
                public constructor(Int x, Int y) {
                    this.x = x
                    this.y = y
                }
                equals(Object o): Bool {
                    if (o instanceof Point) {
                        Point p = o as Point
                        return p.x == x && p.y == y
                    }
                    return false
                }
                hashCode(): Int = x * 31 + y
            }

            main() {
                var m = Money(150L)
                println(m)
                println("total: " + m)
                println(listOf(m, Money(5L)))
                println(Tag("a", 1).hashCode())
                println(Tag("a", 1) == Tag("b", 2))
                println(Plain(3))
                println(Plain(3) == Plain(3))
                println(Box(4))
                println("b=" + Box(5))
                println(listOf(Box(1)))
                println(setOf(Point(1, 2), Point(1, 2), Point(2, 1)).size)
                println(listOf(Point(1, 2)).contains(Point(1, 2)))
                println(listOf(Point(5, 5), Point(1, 2)).indexOf(Point(1, 2)))
                println(setOf(Tag("a", 1), Tag("b", 2)).size)
                println(listOf(Tag("a", 1)).contains(Tag("z", 9)))
            }
            """;

    private static final String OUT = """
            R$150
            total: R$150
            [R$150, R$5]
            7
            true
            Plain[a=3]
            true
            Box(4)
            b=Box(5)
            [Box(1)]
            2
            true
            1
            1
            true
            """;

    @TempDir
    Path tmp;

    @Test
    void userObjectMethodsWinJvm() throws Exception {
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
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void userObjectMethodsWinScript() throws Exception {
        Path file = write("script");
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        assertEquals(OUT, r.stdout().replace("\r\n", "\n"));
    }

    @Test
    void userObjectMethodsWinJs() throws Exception {
        Path file = write("js");
        Path out = tmp.resolve("js-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        assertEquals(OUT.strip(), buf.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void userObjectMethodsWinNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        Path file = write("native");
        Path out = tmp.resolve("native-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.NATIVE);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), o);
        assertEquals(OUT.strip(), o.strip());
    }

    private static boolean hasToolchain() {
        try {
            return new ProcessBuilder("sh", "-c", "command -v as && command -v ld").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path write(String name) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        return file;
    }
}

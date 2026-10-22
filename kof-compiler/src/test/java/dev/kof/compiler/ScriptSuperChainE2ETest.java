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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptSuperChainE2ETest {

    private static final String SRC = """
            class A {
                public constructor() {}
                show(): String = "A"
                tag(Int n): String = "a" + n
            }

            class B extends A {
                public constructor() {
                    super()
                }
                show(): String = "B" + super.show()
            }

            class C extends B {
                public constructor() {
                    super()
                }
                tag(Int n): String = "c" + super.tag(n)
                later(): () -> String = () -> "L" + super.show()
            }

            class D extends C {
                public constructor() {
                    super()
                }
                show(): String = "D" + super.show()
                tag(Int n): String = "d" + super.tag(n + 1)
            }

            main() {
                println(C().show())
                println(D().show())
                println(D().tag(1))
                println(D().later()())
                A a = D()
                println(a.show())
            }
            """;

    private static final String OUT = "BA\nDBA\ndca2\nLBA\nDBA\n";

    @TempDir
    Path tmp;

    @Test
    void superCallFromInheritedMethodScript() throws Exception {
        Path file = write("script");
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        assertEquals(OUT, r.stdout().replace("\r\n", "\n"));
    }

    @Test
    void superCallFromInheritedMethodJvm() throws Exception {
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

    private Path write(String name) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        return file;
    }
}

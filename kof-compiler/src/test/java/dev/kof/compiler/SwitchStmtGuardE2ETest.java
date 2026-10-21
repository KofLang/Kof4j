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

class SwitchStmtGuardE2ETest {

    private static final String OTHER_TYPE = """
            describe(Object o): String {
                switch (o) {
                    case String s if s.isEmpty():
                        return "vazia"
                    case String s:
                        return "texto " + s
                }
                return "outro"
            }

            main() {
                println(describe(""))
                println(describe("oi"))
                println(describe(42))
            }
            """;

    private static final String DESTRUCTURED = """
            record Point(Int x, Int y)
            record Pair(String a, Double b)

            describe(Object o): String {
                switch (o) {
                    case Point(x, y) if x > 0 && y > 0:
                        return "primeiro " + (x + y)
                    case Point(x, y) if x < 0:
                        return "esquerda " + y
                    case Pair(a, b) if b > 1.5:
                        return a + " grande"
                    case Point p:
                        return "outro ponto"
                }
                return "nada"
            }

            main() {
                println(describe(Point(1, 2)))
                println(describe(Point(-1, 7)))
                println(describe(Point(0, 0)))
                println(describe(Pair("x", 2.5)))
                println(describe(Pair("y", 0.5)))
                println(describe("s"))
            }
            """;

    private static final String DESTRUCTURED_OUT =
            "primeiro 3\nesquerda 7\noutro ponto\nx grande\nnada\nnada\n";

    @TempDir
    Path tmp;

    @Test
    void stmtGuardSkipsSubjectOfAnotherTypeJvm() throws Exception {
        assertEquals("vazia\ntexto oi\noutro\n", jvm("other", OTHER_TYPE));
    }

    @Test
    void stmtGuardSkipsSubjectOfAnotherTypeScript() throws Exception {
        assertEquals("vazia\ntexto oi\noutro\n", script("other-s", OTHER_TYPE));
    }

    @Test
    void stmtGuardSeesDestructuredFieldsJvm() throws Exception {
        assertEquals(DESTRUCTURED_OUT, jvm("destr", DESTRUCTURED));
    }

    @Test
    void stmtGuardSeesDestructuredFieldsScript() throws Exception {
        assertEquals(DESTRUCTURED_OUT, script("destr-s", DESTRUCTURED));
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }

    private String jvm(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), name + ": " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String script(String name, String src) throws Exception {
        Path file = write(name, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        return r.stdout().replace("\r\n", "\n");
    }
}

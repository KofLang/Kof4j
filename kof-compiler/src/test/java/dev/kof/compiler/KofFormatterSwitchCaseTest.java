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

class KofFormatterSwitchCaseTest {

    private static final String GUARDS = """
            record Point(Int x, Int y)

            describe(Object o): String {
                return switch (o) {
                    case String s if s.isEmpty() -> "vazia"
                    case String s -> "texto " + s
                    default -> "outro"
                }
            }

            quadrant(Object o): String {
                switch (o) {
                    case Point p if p.x() > 0 && p.y() > 0:
                        return "primeiro"
                    case Point(x, y):
                        return "outro"
                }
                return "nada"
            }

            main() {
                println(describe(""))
                println(describe("oi"))
                println(quadrant(Point(1, 2)))
                println(quadrant(Point(-1, 2)))
            }
            """;

    private static final String EMPTY_DEFAULT = """
            main() {
                val ok = true
                switch (ok) {
                    case true:
                        println("sim")
                    default:
                }
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void caseGuardsSurviveFormatting() throws Exception {
        String out = KofFormatter.format(GUARDS, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("case String s if s.isEmpty() -> \"vazia\""), out);
        assertTrue(out.contains("case Point p if p.x() > 0 && p.y() > 0:"), out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"));
        assertEquals("vazia\ntexto oi\nprimeiro\noutro\n", run("guards", out));
    }

    @Test
    void emptyDefaultSurvivesFormatting() throws Exception {
        String out = KofFormatter.format(EMPTY_DEFAULT, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("default:"), out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"));
        assertEquals("sim\n", run("default", out));
    }

    private String run(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), name + ": " + r.diagnostics().getDiagnostics() + "\n" + src);
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
}

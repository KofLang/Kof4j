package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1116 — {@code List.removeValue(v)}: remove the first element equal to
 * {@code v} and report whether one was found. Composed from the landed
 * {@code indexOf} + {@code remove}, so every target inherits it.
 *
 * <p>Faces: a present value (true, list shrinks, the FIRST occurrence goes),
 * an absent value (false, list unchanged), a String list (content equality,
 * not identity), and the empty list.
 */
class ListRemoveValueE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROBE = """
            main() {
                var xs = listOf(3, 1, 3, 2)
                println(xs.removeValue(3))
                println(xs)
                println(xs.removeValue(99))
                println(xs)
                var ss = listOf("a", "b", "c")
                println(ss.removeValue("b"))
                println(ss)
                var empty = listOf<Int>()
                println(empty.removeValue(1))
                println(empty.size())
            }
            """;

    private static final String EXPECTED =
            "true\n[1, 3, 2]\nfalse\n[1, 3, 2]\ntrue\n[a, c]\nfalse\n0";

    @Test
    void removeValueOnJvm() throws Exception {
        Path root = tmp.resolve("rv-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void removeValueOnScript() throws Exception {
        Path root = tmp.resolve("rv-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }
}

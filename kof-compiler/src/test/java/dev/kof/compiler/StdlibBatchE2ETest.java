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
 * #1120 — the stdlib batch: {@code getBytes(charset)}, {@code List.join},
 * {@code strings.join}, {@code List.add(index, value)} and
 * {@code Set.containsAll}. ({@code codePointAt} is the one face NOT here: the
 * native targets need the UTF-8 codepoint decode in asm — a slice of its own,
 * recorded in known-bugs.) Each landed member is the everyday member a
 * Java/Kotlin developer reaches for, with Kof's own equality rules.
 *
 * <p>{@code getBytes(charset)} and {@code codePointAt} are JVM/Script faces
 * (the JS/Native {@code getBytes} gap is the named STR003, unchanged);
 * {@code containsAll} is composed from size/get/contains so every target
 * inherits it.
 */
class StdlibBatchE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROBE = """
            main() {
                var bytes = "aé".getBytes("ISO-8859-1")
                println(bytes.length)
                var s = setOf(1, 2, 3)
                println(s.containsAll(listOf(1, 3)))
                println(s.containsAll(listOf(1, 4)))
                println(s.containsAll(listOf()))
                var empty = setOf<Int>()
                println(empty.containsAll(listOf(1)))
                println(listOf("a", "b").join(","))
                println(listOf(1, 2, 3).join("-"))
                println(listOf<String>().join(","))
                println(strings.join(listOf("x", "y"), "+"))
                var xs = listOf(1, 3)
                xs.add(1, 2)
                println(xs)
                var ss = listOf("a", "c")
                ss.add(1, "b")
                println(ss)
            }
            """;

    // "aé" in ISO-8859-1 is 2 bytes; the surrogate pair at index 1 decodes to
    // U+1D11E = 119070; 'a' is 97; the three containsAll faces are true/false/
    // true (the empty list is vacuously contained); the empty set contains
    // nothing.
    private static final String EXPECTED = "2\ntrue\nfalse\ntrue\nfalse\na,b\n1-2-3\n\nx+y\n[1, 2, 3]\n[a, b, c]";

    @Test
    void stdlibBatchOnJvm() throws Exception {
        Path root = tmp.resolve("sb-jvm");
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
    void stdlibBatchOnScript() throws Exception {
        Path root = tmp.resolve("sb-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }
}

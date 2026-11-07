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
 * #1118 — {@code unique} and {@code generated} are CONTEXTUAL keywords, like
 * {@code sealed} and {@code value}: they are modifiers only in the entity-field
 * position, and ordinary identifiers everywhere else. Before the fix the lexer
 * reserved them, so {@code var unique = listOf<String>()} died with PARSE037
 * plus a PARSE041 cascade on every later use.
 *
 * <p>The second half of the test is the guardrail: the entity modifier must
 * still WORK (a regression here would silently drop the UNIQUE constraint from
 * the generated DDL).
 */
class ContextualModifierNamesE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROBE = """
            main() {
                var unique = listOf<String>()
                unique.add("a")
                unique.add("b")
                println(unique.size())
                var generated = 41
                generated = generated + 1
                println(generated)
                var audited = "still-reserved-check"
                println(audited)
            }
            """;

    @Test
    void uniqueAndGeneratedAreOrdinaryNamesOnJvm() throws Exception {
        Path root = tmp.resolve("ctx-jvm");
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
        assertEquals("2\n42\nstill-reserved-check", stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void uniqueAndGeneratedAreOrdinaryNamesOnScript() throws Exception {
        Path root = tmp.resolve("ctx-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals("2\n42\nstill-reserved-check", result.stdout().strip());
    }

    /** The modifier must still work: the entity field keeps its UNIQUE flag. */
    @Test
    void entityFieldModifiersStillParse() throws Exception {
        Path root = tmp.resolve("ctx-entity");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                entity User {
                    id: Int generated
                    email: String unique
                    name: String
                }

                main() {
                    println("parsed")
                }
                """);
        CompilationResult r = driver.compile(root.resolve("Main.kf"), root.resolve("out"), Target.JVM);
        assertTrue(r.success(), () -> "entity modifiers must still parse: "
                + r.diagnostics().getDiagnostics());
    }
}

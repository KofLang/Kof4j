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
 * #1034 — null narrowing with {@code ||} and the unsound {@code &&}-else.
 *
 * <p>(a) ADDITIVE: the guard clause {@code if (t == null || t.length() == 0)
 * { throw "empty" } return t.trim()} must compile — the code after an
 * exiting then-branch runs with the condition FALSE, so {@code t != null};
 * and the RIGHT operand of {@code ||} only evaluates when the left failed,
 * so it also sees {@code t != null}.
 *
 * <p>(b) SOUNDNESS FIX: {@code if (x == null && y) {} else { x.length() }}
 * compiled before, although the else means ¬(x==null ∧ y) = x≠null ∨ ¬y —
 * {@code x} can be null there, so NO narrowing applies and the member access
 * is rejected (SEM049). The unsound acceptance was never a guarantee (the
 * issue documents the fix direction).
 */
class NullNarrowingOrAndE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** The issue's exact reproduction — the usual guard clause. */
    private static final String GUARD = """
            String f(t: String?) {
                if (t == null || t.length() == 0) { throw "empty" }
                return t.trim()
            }

            main() {
                try {
                    println(f(null))
                } catch (String e) {
                    println(e.contains("empty"))
                }
                println(f(" hi "))
            }
            """;

    private static final String GUARD_EXPECTED = "true\nhi";

    /** The right operand of || sees the left's negation. */
    private static final String RIGHT_OPERAND = """
            Int g(t: String?) {
                if (t == null || t.length() == 0) { return -1 }
                return t.length()
            }

            main() {
                println(g(null))
                println(g("abcd"))
            }
            """;

    private static final String RIGHT_EXPECTED = "-1\n4";

    /** The unsound &&-else must now be rejected. */
    private static final String UNSOUND = """
            Int h(x: String?) {
                if (x == null && x.length() > 0) {
                    return 1
                } else {
                    return x.length()
                }
            }

            main() {
                println(h("ok"))
            }
            """;

    @Test
    void guardClauseNarrowsAfterTheOrThrow() throws Exception {
        Path root = tmp.resolve("nn-guard");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), GUARD);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(GUARD_EXPECTED, result.stdout().strip());
    }

    @Test
    void orRightOperandSeesTheLeftNegation() throws Exception {
        Path root = tmp.resolve("nn-right");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), RIGHT_OPERAND);
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
        assertEquals(RIGHT_EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void unsoundAndElseIsRejected() throws Exception {
        Path root = tmp.resolve("nn-unsound");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), UNSOUND);
        CompilationResult r = driver.compile(root.resolve("Main.kf"), root.resolve("out"), Target.JVM);
        assertFalse(r.success(), "the unsound &&-else narrowing must be rejected (#1034)");
        boolean sem049 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM049".equals(d.code()));
        assertTrue(sem049, () -> "expected SEM049, got: " + r.diagnostics().getDiagnostics());
    }
}

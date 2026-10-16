package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code SEM025} "Cannot resolve method … on type 'List' (valid: …)" hint
 * must list EVERY method the typer actually accepts — an INCOMPLETE list
 * misleads a reader (and an agent) into avoiding real idioms.
 *
 * <p>Found by a proactive corpus/diagnostic audit: the list omitted
 * {@code push}/{@code append} (accepted at {@code MemberCallTyper} line ~284)
 * and {@code count} (accepted at ~183) — all three measured WORKING. This test
 * pins BOTH directions: the three methods COMPILE clean (so they ARE valid), and
 * the {@code SEM025} message names them.
 */
class ListValidMethodsDiagnosticE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
    }

    @Test
    void thePreviouslyOmittedListMethodsAreAccepted() throws Exception {
        // positive control: push/append/count ARE valid List methods
        CompilationResult r = compile("ok", """
                main() {
                    var xs = listOf(1, 2, 3)
                    xs.push(4)
                    xs.append(5)
                    var n = xs.count((x: Int) -> x > 2)
                    println(n)
                }
                """);
        assertTrue(r.success(), () -> "push/append/count must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void theSem025HintListsTheAcceptedMethods() throws Exception {
        CompilationResult r = compile("bad", """
                main() {
                    var xs = listOf(1, 2, 3)
                    xs.nope()
                }
                """);
        assertEquals(false, r.success(), "xs.nope() must not compile");
        String msg = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SEM025".equals(d.code()))
                .map(d -> d.message())
                .reduce("", (a, b) -> a + b);
        for (String m : new String[] {"push", "append", "count"}) {
            assertTrue(msg.contains(m), () -> "SEM025 'valid:' hint must list '" + m + "': " + msg);
        }
    }
}

package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #692 — the trailing-lambda rule ({@code f(args) { ... }} / {@code f { ... }},
 * closures.md §6) latched onto ANY {@code {} on the next line after a call,
 * swallowing an unrelated bare block statement into the call as a fabricated
 * extra lambda argument (a confusing SEM056/SEM023 downstream). The `(` sibling
 * case already had a same-line guard; this pins the same rule for `{`.
 */
class TrailingLambdaSameLineTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String name, String program) throws Exception {
        Path f = dir.resolve(name + ".kf");
        Files.writeString(f, program);
        return driver.compile(f, dir.resolve("out-" + name), Target.JVM);
    }

    private static String runJvm(Path out) throws Exception {
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run must exit 0, got:\n" + outText);
        return outText;
    }

    @Test
    void bareBlockAfterCallIsNotSwallowed(@TempDir Path t) throws Exception {
        // verbatim from the issue: `listOf(1)` followed by an unrelated block.
        String program = """
                main() {
                    var a = listOf(1)
                    {
                        println("x")
                    }
                    println(a.size())
                }
                """;
        CompilationResult r = compile(t, "Bare", program);
        assertTrue(r.success(), "two independent statements must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("x\n1", runJvm(t.resolve("out-Bare")));
    }

    @Test
    void constructorCallDoesNotSwallowFollowingBlock(@TempDir Path t) throws Exception {
        // second instance from the issue: the constructor call used to absorb
        // the block and emit a spurious SEM023 arity error.
        String program = """
                class Handle {
                    Int id
                    public constructor(Int id) { this.id = id }
                    void close() { }
                }

                main() {
                    var h = Handle(1)
                    {
                        h.close()
                    }
                    h.close()
                }
                """;
        CompilationResult r = compile(t, "Ctor", program);
        // The block must not merge into Handle(1): the real contract then fires
        // (double close -> MEM001), never the fabricated-arity SEM023.
        assertFalse(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM023".equals(d.code())),
                "no fabricated-arity SEM023: " + r.diagnostics().getDiagnostics());
        assertFalse(r.success(), "double close must be rejected: " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "MEM001".equals(d.code())),
                "expected MEM001 for double close: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void trailingLambdaOnTheSameLineStillAttaches(@TempDir Path t) throws Exception {
        String program = """
                main() {
                    listOf(1, 2, 3).forEach { x -> println(x * 10) }
                }
                """;
        CompilationResult r = compile(t, "SameLine", program);
        assertTrue(r.success(), "same-line trailing lambda must still attach: " + r.diagnostics().getDiagnostics());
        assertEquals("10\n20\n30", runJvm(t.resolve("out-SameLine")));
    }
}

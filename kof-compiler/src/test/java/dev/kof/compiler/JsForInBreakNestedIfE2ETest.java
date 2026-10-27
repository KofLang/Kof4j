package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #975 — a {@code break} inside a for-in that is itself nested in an
 * {@code if} was DROPPED on JS: the loop ran to the end and every element was
 * processed where the JVM stopped at the first (silent wrong value), or the
 * residual label of a constant-folded wrapper blew up COMP002.
 *
 * <p>Two roots, both new with the 0.6.7 assembly-opt Phase B passes:
 * (1) jump-threading re-taxates the break's {@code Jump(loopEnd)} to the end
 * label of the enclosing {@code if} when the two labels end up adjacent and
 * empty — the JS loop-body parse consumed that jump as a generic "region
 * exit" and discarded it; now a region-exit jump whose target lies beyond
 * the innermost live loop's end is classified as a break-out
 * ({@code JsControlFlowParser}). (2) constant-folding the CJump of a
 * STATEMENT if orphaned the {@code KofStatementIf} marker (§267's contract
 * is that the marked CJump exists); the fold now skips statement-if jumps
 * ({@code OptimizerConstantFold}).
 *
 * <p>The oracle is the JVM. RED-first: both JS faces were RED pre-fix
 * (face A = COMP002, face B = silent 3-instead-of-1).
 */
class JsForInBreakNestedIfE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Face A — the constant-folded wrapper (compiled to COMP002 pre-fix). */
    private static final String FOLDED_IF = """
            Int breakInIfInIf() {
                var xs = listOf(1,2,3,4,5)
                var hits = 0
                if (true) {
                    for (var x in xs) {
                        hits = hits + 1
                        if (x == 2) { break }
                    }
                }
                return hits
            }

            main() {
                println(breakInIfInIf())
            }
            """;

    /** Face B — the issue's shape: break under a computed if (silent drop pre-fix). */
    private static final String COMPUTED_IF = """
            class Slot {
                Bool active = false
                fire() { active = true }
            }
            class Rack {
                Int steps = 0
                List<Slot> slots = listOf(Slot(), Slot(), Slot())
                tick() {
                    steps = steps + 1
                    if (steps % 12 == 0) {
                        for (var slot in slots) {
                            if (!slot.active) { slot.fire(); break }
                        }
                    }
                }
            }

            main() {
                var rack = Rack()
                for (var i in listOf(1,2,3,4,5,6,7,8,9,10,11,12,13)) { rack.tick() }
                var lit = 0
                for (var s in rack.slots) { if (s.active) { lit = lit + 1 } }
                println(lit)
            }
            """;

    @Test
    void jvmIsTheContractForNestedForInBreak() throws Exception {
        assertEquals("2", runJvm("jvmA", FOLDED_IF), "o oráculo JVM mudou — revise o caso");
        assertEquals("1", runJvm("jvmB", COMPUTED_IF), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void jsMatchesTheJvmOnFoldedIfWrapper() throws Exception {
        assertEquals("2", runJs("jsA", FOLDED_IF),
                "#975 face A: o break dentro do if dobrado estourava COMP002 no JS");
    }

    @Test
    void jsMatchesTheJvmOnComputedIfWrapper() throws Exception {
        assertEquals("1", runJs("jsB", COMPUTED_IF),
                "#975 face B: o break era descartado e o laço JS ativava todos os slots");
    }

    @Test
    void scriptMatchesTheJvmOnNestedForInBreak() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), FOLDED_IF);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("2", r.stdout().strip(), "o Script já compilava");
    }

    @Test
    void nativeX86MatchesTheJvmOnNestedForInBreak() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("nat");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), COMPUTED_IF);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "" + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default").resolve("Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        p.waitFor();
        assertEquals("1", o);
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o.strip();
    }

    private String runJs(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> name + " js compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> name + " js saída: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private static boolean has(String... progs) {
        for (String prog : progs) {
            try {
                new ProcessBuilder(prog, "--version").start().waitFor();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}

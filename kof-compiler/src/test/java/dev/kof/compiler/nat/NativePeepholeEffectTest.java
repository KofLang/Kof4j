package dev.kof.compiler.nat;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §17 (benchmark-driven optimization) of
 * {@code docs/development/assembly-optimization-plan.md}: "no ISA-specific
 * optimization may be promoted just because it 'looks better in assembly'. Each
 * relevant optimization must have … a before/after comparison … correctness
 * validation."
 *
 * <p>The §7 x86-64 peephole had unit tests for each fold, but no measurement of
 * its effect on a REAL program. This test compiles the same source with the
 * peephole ON and OFF ({@code -Dkof.native.peephole.disable}) and asserts three
 * things §17 asks for: the instruction count strictly DROPS, the delta is
 * measured rather than asserted qualitatively, and BOTH binaries produce the
 * same output (correctness validation).
 *
 * <p>MEASURED on this program: instructions 3227 -> 3162 (-65), loads 597 -> 523
 * (-74), stores 479 -> 416 (-63). The assertion is only "strictly fewer", so the
 * test does not pin an exact count (the peephole keeps gaining folds); these
 * figures are the reference point recorded at the landing.
 */
class NativePeepholeEffectTest {

    private static final String SOURCE = """
            Int sumTo(Int n) {
                var s = 0
                for (var i = 0; i < n; i++) { s = s + i * 2 + 1 }
                return s
            }
            Int abs(Int v) { if (v < 0) { return 0 - v }; return v }
            main() {
                println(sumTo(10))
                println(abs(-7))
                println(sumTo(0) + abs(3))
            }
            """;
    private static final String EXPECTED = "100\n7\n3";

    @Test
    void thePeepholeReducesInstructionCountWithoutChangingBehaviour(@TempDir Path dir)
            throws Exception {
        List<String> withPeephole = compile(dir.resolve("on"), "on");
        List<String> withoutPeephole = compile(dir.resolve("off"), "off");

        AsmMetrics on = AsmMetrics.of(withPeephole);
        AsmMetrics off = AsmMetrics.of(withoutPeephole);

        assertTrue(on.instructions() < off.instructions(),
                "§17: the peephole must MEASURABLY reduce instructions; on="
                        + on.summary() + " off=" + off.summary());
        assertTrue(on.loads() <= off.loads(), "loads must not grow: " + on.summary());
        assertTrue(on.stores() <= off.stores(), "stores must not grow: " + on.summary());

        // Correctness validation (§17 item 5): the optimized binary must print
        // exactly the same thing.
        assertEquals(EXPECTED, runBinary(dir.resolve("on")), "peephole ON output");
        assertEquals(EXPECTED, runBinary(dir.resolve("off")), "peephole OFF output");
    }

    private List<String> compile(Path root, String tag) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, SOURCE);
        String prior = System.getProperty("KOF_KEEP_ASM");
        String disabled = System.getProperty("kof.native.peephole.disable");
        System.setProperty("KOF_KEEP_ASM", "1");
        if (tag.equals("off")) {
            System.setProperty("kof.native.peephole.disable", "1");
        } else {
            System.clearProperty("kof.native.peephole.disable");
        }
        try {
            CompilationResult r = new CompilerDriver().compile(src, root.resolve("out"), Target.NATIVE);
            assertTrue(r.success(), "compile (" + tag + "): " + r.diagnostics().getDiagnostics());
        } finally {
            if (prior == null) System.clearProperty("KOF_KEEP_ASM");
            else System.setProperty("KOF_KEEP_ASM", prior);
            if (disabled == null) System.clearProperty("kof.native.peephole.disable");
            else System.setProperty("kof.native.peephole.disable", disabled);
        }
        return Files.readAllLines(root.resolve("out").resolve("Default").resolve("Main.s"));
    }

    private String runBinary(Path root) throws Exception {
        Path bin = root.resolve("out").resolve("Default").resolve("Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "binary timed out");
        assertEquals(0, p.exitValue(), "binary exit code, output: " + out);
        return out.replace("\r\n", "\n").strip();
    }
}

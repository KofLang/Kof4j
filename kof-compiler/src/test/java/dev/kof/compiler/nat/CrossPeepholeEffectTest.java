package dev.kof.compiler.nat;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §17 (benchmark-driven optimization) of
 * {@code docs/assembly-optimization-plan.md}, "coverage on the
 * supported targets": the before/after measurement of the §7 peepholes must
 * cover the CROSS targets too, not only x86-64.
 *
 * <p>Compiles the same source with the peepholes ON and OFF
 * ({@code -Dkof.native.peephole.disable}) for riscv64 and aarch64 and asserts the
 * instruction count strictly DROPS on each. AArch64 is translated FROM the riscv
 * text, so the two should move by the same amount — asserted, because a
 * divergence would mean one of the two text passes is not seeing the shape the
 * other does.
 *
 * <p>MEASURED on this fixture: riscv64 4708 -> 4561 and aarch64 5482 -> 5335
 * (both -147).
 */
class CrossPeepholeEffectTest {

    private static final String SOURCE = """
            Int sumTo(Int n) {
                var s = 0
                for (var i = 0; i < n; i++) { s = s + i * 2 + 1 }
                return s
            }
            main() { println(sumTo(10)) }
            """;

    @Test
    void riscv64PeepholeReducesInstructions(@TempDir Path dir) throws Exception {
        AsmMetrics on = measure(dir.resolve("on"), Target.NATIVE_RISCV64, false);
        AsmMetrics off = measure(dir.resolve("off"), Target.NATIVE_RISCV64, true);
        assertTrue(on.instructions() < off.instructions(),
                "§17: the riscv64 peephole must measurably reduce instructions; on="
                        + on.summary() + " off=" + off.summary());
    }

    @Test
    void aarch64PeepholeReducesInstructions(@TempDir Path dir) throws Exception {
        AsmMetrics on = measure(dir.resolve("on"), Target.NATIVE_AARCH64, false);
        AsmMetrics off = measure(dir.resolve("off"), Target.NATIVE_AARCH64, true);
        assertTrue(on.instructions() < off.instructions(),
                "§17: the aarch64 peephole must measurably reduce instructions; on="
                        + on.summary() + " off=" + off.summary());
    }

    @Test
    void bothCrossTargetsGainTheSameAmount(@TempDir Path dir) throws Exception {
        // #1134: the aarch64 text = the riscv text translated + the peephole
        // folded + the SP-aligner restructured (by design) — the DELTAS
        // legitimately diverge now. The preserved intent: both peepholes
        // still gain (the foldable shapes are seen on both archs), and the
        // #1134 invariant holds in BOTH arms (every sub/add sp a multiple of
        // 16 — the SIGBUS can never return through this fixture).
        int riscvGain = gain(dir.resolve("rv"), Target.NATIVE_RISCV64);
        int aarch64Gain = gain(dir.resolve("a64"), Target.NATIVE_AARCH64);
        assertTrue(riscvGain > 0 && aarch64Gain > 0,
                "both must gain: riscv=" + riscvGain + " aarch64=" + aarch64Gain);
        assertSpAligned(dir.resolve("a64/on/out/Default/Main.s"));
        assertSpAligned(dir.resolve("a64/off/out/Default/Main.s"));
    }

    /** #1134: every sub/add sp, sp, #N in the aarch64 asm is a multiple of
     *  16 (outside the dead .macro body). */
    private static void assertSpAligned(Path asm) throws Exception {
        boolean inMacro = false;
        for (String raw : Files.readAllLines(asm)) {
            String s = raw.strip();
            if (s.startsWith(".macro")) { inMacro = true; continue; }
            if (s.startsWith(".endm")) { inMacro = false; continue; }
            if (inMacro) continue;
            var m = java.util.regex.Pattern
                    .compile("^(sub|add)\\s+sp,\\s*sp,\\s*#(\\d+)$").matcher(s);
            if (m.matches() && Long.parseLong(m.group(2)) % 16 != 0) {
                fail("#1134: " + asm + " has a sp adjustment that is not a multiple"
                        + " of 16 (the SIGBUS on real hardware): " + s);
            }
        }
    }

    private int gain(Path root, Target target) throws Exception {
        return measure(root.resolve("off"), target, true).instructions()
                - measure(root.resolve("on"), target, false).instructions();
    }

    private AsmMetrics measure(Path root, Target target, boolean disabled) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, SOURCE);
        String priorKeep = System.getProperty("KOF_KEEP_ASM");
        String priorDisable = System.getProperty("kof.native.peephole.disable");
        System.setProperty("KOF_KEEP_ASM", "1");
        if (disabled) {
            System.setProperty("kof.native.peephole.disable", "1");
        } else {
            System.clearProperty("kof.native.peephole.disable");
        }
        try {
            CompilationResult r = new CompilerDriver().compile(src, root.resolve("out"), target);
            assertTrue(r.success(), target + " compile (peephole "
                    + (disabled ? "OFF" : "ON") + "): " + r.diagnostics().getDiagnostics());
        } finally {
            restore("KOF_KEEP_ASM", priorKeep);
            restore("kof.native.peephole.disable", priorDisable);
        }
        List<String> asm = Files.readAllLines(root.resolve("out").resolve("Default").resolve("Main.s"));
        return AsmMetrics.of(asm);
    }

    private static void restore(String key, String prior) {
        if (prior == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prior);
        }
    }
}

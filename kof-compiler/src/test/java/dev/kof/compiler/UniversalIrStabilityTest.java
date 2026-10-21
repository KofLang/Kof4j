package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §22 (promotion criteria) of
 * {@code docs/development/assembly-optimization-plan.md}: an optimization must
 * "not introduce target-dependent behavior in cross-target code".
 *
 * <p>{@code OptimizationScopeTest} proves WHICH passes are universal; this proves
 * the BEHAVIOUR of those passes: optimizing the same source for different
 * targets must produce the SAME IR, because the universal passes run over the
 * target-independent IR before any lowering.
 *
 * <p>The comparison is deliberately scoped to a PURE helper method (no stdlib
 * calls). {@code main}'s IR legitimately differs between targets — the lowering
 * of {@code println} is target-specific by design (a JVM {@code Integer.valueOf}
 * chain vs a native {@code kof_println_int}) — so asserting equality over the
 * whole module would be asserting something the architecture never promised. The
 * pure method isolates the shared optimization path, which is exactly what §22 is
 * talking about.
 */
class UniversalIrStabilityTest {

    private static final String SOURCE = """
            Int pure(Int a) {
                var x = a * 4 + 1
                var y = x - 0
                return y + 0
            }
            main() { println(pure(3)) }
            """;

    private static final List<Target> TARGETS = List.of(
            Target.JVM, Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64, Target.JS);

    @Test
    void theUniversalPassesProduceIdenticalIrOnEveryTarget(@TempDir Path dir) throws Exception {
        Map<String, List<String>> byTarget = new LinkedHashMap<>();
        for (Target t : TARGETS) {
            byTarget.put(t.toString(), optimizedIrOf(dir.resolve(t.toString()), t));
        }
        List<String> reference = byTarget.get(Target.JVM.toString());
        assertFalse(reference.isEmpty(), "the helper method must have ops");
        for (Map.Entry<String, List<String>> e : byTarget.entrySet()) {
            assertEquals(reference, e.getValue(),
                    "§22: universal passes must produce the same IR regardless of target; "
                            + e.getKey() + " differs from JVM");
        }
    }

    @Test
    void theIrReallyWasOptimized(@TempDir Path dir) throws Exception {
        // Guard against a vacuous pass: if optimization were disabled, "identical
        // IR" would be trivially true and prove nothing.
        Path root = dir.resolve("check");
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, SOURCE);
        CompilerDriver d = new CompilerDriver();
        IRModule[] before = {null};
        IRModule[] after = {null};
        d.setIRObserver((un, opt) -> {
            before[0] = un;
            after[0] = opt;
        });
        CompilationResult r = d.compile(src, root.resolve("out"), Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        List<String> un = opsOf(before[0], "pure");
        List<String> opt = opsOf(after[0], "pure");
        assertNotEquals(un, opt,
                "§22 guard: the optimizer must actually CHANGE the helper's IR "
                        + "(strength reduction x*4 -> shift), else this test is vacuous");
        assertTrue(opt.stream().anyMatch(o -> o.contains("SHL")),
                "expected the strength reduction to reach `pure`: " + opt);
    }

    private List<String> optimizedIrOf(Path root, Target target) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, SOURCE);
        CompilerDriver d = new CompilerDriver();
        IRModule[] seen = {null};
        d.setIRObserver((un, opt) -> seen[0] = opt);
        CompilationResult r = d.compile(src, root.resolve("out"), target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        assertNotNull(seen[0], "the IR observer must have run for " + target);
        return opsOf(seen[0], "pure");
    }

    private static List<String> opsOf(IRModule module, String methodName) {
        List<String> ops = new ArrayList<>();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if (!m.name().equals(methodName)) continue;
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) ops.add(op.toString());
                }
            }
        }
        return ops;
    }
}

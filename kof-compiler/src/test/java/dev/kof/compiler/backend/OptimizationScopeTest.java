package dev.kof.compiler.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §22 (promotion criteria) of
 * {@code docs/assembly-optimization-plan.md}: "An optimization that
 * improves x86_64 but degrades AArch64 and RISC-V should not be treated as
 * universal. It must be explicitly classified as target-specific."
 *
 * <p>This guards the CLASSIFICATION, not the optimizations: every pass the
 * pipeline runs must be in {@link OptimizationScope}, and every universal entry
 * must name a class that really exists in the IR layer rather than a target text
 * pass. That way an optimization cannot be added without a scope, and a
 * target-specific one cannot quietly claim to be universal.
 */
class OptimizationScopeTest {

    private static final Path BACKEND =
            Path.of("src/main/java/dev/kof/compiler/backend");
    private static final Path NAT =
            Path.of("src/main/java/dev/kof/compiler/nat");

    @Test
    void everyAdjacentPassInTheStandardPipelineIsClassified() throws IOException {
        String pipeline = Files.readString(BACKEND.resolve("PassPipeline.java"));
        // The IR passes seeded into the standard pipeline — the ones that run for
        // EVERY target and therefore must be UNIVERSAL.
        Set<String> wired = new TreeSet<>();
        for (String cls : List.of("ConstantPropagation", "CopyPropagation",
                "DeadCodeElimination", "StrengthReduction", "AlgebraicSimplification",
                "JumpThreading")) {
            if (pipeline.contains("new " + cls + "()")) wired.add(cls);
        }
        assertFalse(wired.isEmpty(), "expected the standard pipeline to seed IR passes");

        Set<String> declared = OptimizationScope.of(OptimizationScope.Scope.UNIVERSAL)
                .stream().map(OptimizationScope.Optimization::where)
                .collect(Collectors.toCollection(TreeSet::new));
        for (String cls : wired) {
            assertTrue(declared.contains(cls),
                    "§22: " + cls + " runs in the standard pipeline but is not classified "
                            + "UNIVERSAL in OptimizationScope.ALL");
        }
    }

    @Test
    void everyClassifiedOptimizationExistsInTheSourceTree() {
        for (OptimizationScope.Optimization o : OptimizationScope.ALL) {
            boolean universal = o.scope() == OptimizationScope.Scope.UNIVERSAL;
            Path dir = universal ? BACKEND : NAT;
            Path file = dir.resolve(o.where() + ".java");
            assertTrue(Files.isRegularFile(file),
                    "§22: '" + o.name() + "' is classified as " + o.scope()
                            + " naming " + o.where() + ", but " + file + " does not exist");
        }
    }

    @Test
    void aTargetTextPassIsNeverClassifiedUniversal() {
        // The inverse mistake: a target text pass must not claim UNIVERSAL scope,
        // which would let a one-ISA win be promoted as a cross-target one.
        for (OptimizationScope.Optimization o : OptimizationScope.of(OptimizationScope.Scope.UNIVERSAL)) {
            assertFalse(Files.isRegularFile(NAT.resolve(o.where() + ".java")),
                    "§22: '" + o.name() + "' is UNIVERSAL but lives in the per-target "
                            + "nat/ layer (" + o.where() + ")");
        }
    }

    @Test
    void theThreeNativeTargetsAllHaveAtLeastOneClassifiedOptimization() {
        for (OptimizationScope.Scope s : List.of(OptimizationScope.Scope.X86_64,
                OptimizationScope.Scope.AARCH64, OptimizationScope.Scope.RISCV64)) {
            assertFalse(OptimizationScope.of(s).isEmpty(),
                    "§22: target " + s + " has no classified optimization");
        }
    }
}

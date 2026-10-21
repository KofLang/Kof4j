package dev.kof.compiler.backend;

import java.util.List;

/**
 * §22 (promotion criteria) of
 * {@code docs/development/assembly-optimization-plan.md}: an optimization that
 * helps one ISA but hurts another "must be explicitly classified as
 * target-specific" — it should not silently pass as universal.
 *
 * <p>This record makes that classification EXPLICIT DATA instead of prose in the
 * plan: every optimization the backend runs is listed once, with the scope that
 * describes where it is applied.
 *
 * <ul>
 *   <li>{@link Scope#UNIVERSAL} — operates on the Kof IR, runs ONCE before any
 *       target lowering, so every backend sees the same transformed program.
 *       These cannot be target-specific by construction.</li>
 *   <li>{@link Scope#X86_64}, {@link Scope#AARCH64}, {@link Scope#RISCV64} — a
 *       text pass over ONE target's assembly. It may change that target's output
 *       only; it can never make another target worse, and it is never claimed as
 *       a cross-target win.</li>
 * </ul>
 *
 * <p>The companion test asserts the inventory is complete against the passes the
 * pipeline actually runs, so adding an optimization without classifying it fails
 * rather than slipping in unclassified.
 */
public final class OptimizationScope {

    private OptimizationScope() {
    }

    /** Where an optimization is applied. */
    public enum Scope {
        /** Over the target-independent IR, once, before lowering. */
        UNIVERSAL,
        /** A text pass over x86-64 assembly only. */
        X86_64,
        /** A text pass over AArch64 assembly only. */
        AARCH64,
        /** A text pass over RISC-V 64 assembly only. */
        RISCV64
    }

    /** One optimization and its classification. */
    public record Optimization(String name, Scope scope, String where) {
    }

    /**
     * Every optimization the backend applies, classified.
     *
     * <p>{@code where} names the class that implements it, so the classification
     * can be checked against the code rather than trusted.
     */
    public static final List<Optimization> ALL = List.of(
            // ── universal: over the IR, before lowering ──────────────────
            new Optimization("constant-folding", Scope.UNIVERSAL, "OptimizerConstantFold"),
            new Optimization("constant-propagation", Scope.UNIVERSAL, "ConstantPropagation"),
            new Optimization("copy-propagation", Scope.UNIVERSAL, "CopyPropagation"),
            new Optimization("dead-code-elimination", Scope.UNIVERSAL, "DeadCodeElimination"),
            new Optimization("dead-store-elimination", Scope.UNIVERSAL, "LivenessDeadStores"),
            new Optimization("strength-reduction", Scope.UNIVERSAL, "StrengthReduction"),
            new Optimization("algebraic-simplification", Scope.UNIVERSAL, "AlgebraicSimplification"),
            new Optimization("jump-threading", Scope.UNIVERSAL, "JumpThreading"),

            // ── x86-64: text passes over that target's assembly ──────────
            new Optimization("x86-push-pop-fold", Scope.X86_64, "NativeX86Peephole"),
            new Optimization("x86-lea-fold", Scope.X86_64, "NativeX86LeaFold"),
            new Optimization("x86-branch-flag-fold", Scope.X86_64, "NativeX86BranchFold"),
            new Optimization("x86-stack-align", Scope.X86_64, "NativeX86StackAlign"),

            // ── aarch64 ──────────────────────────────────────────────────
            new Optimization("aarch64-push-pop-fold", Scope.AARCH64, "NativeAarch64Peephole"),

            // ── riscv64 ──────────────────────────────────────────────────
            new Optimization("riscv-push-pop-fold", Scope.RISCV64, "NativeRiscvPeephole"));

    /** The optimizations classified with {@code scope}. */
    public static List<Optimization> of(Scope scope) {
        return ALL.stream().filter(o -> o.scope() == scope).toList();
    }
}

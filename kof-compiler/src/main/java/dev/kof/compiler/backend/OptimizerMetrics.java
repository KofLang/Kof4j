package dev.kof.compiler.backend;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofDebugInfo;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Before/after metric comparison for the optimization pipeline (Phase F/§17 of
 * {@code docs/assembly-optimization-plan.md}).
 *
 * <p>§17 requires every relevant optimization to have a "before/after
 * comparison" — measured, not asserted ("the goal is to avoid cosmetic
 * optimizations"). This class runs the PRODUCTION {@link Optimizer} on a single
 * method's op stream and reports the {@link IrMetrics} before and after, so an
 * optimization's effect is quantified (instruction/load/store deltas), not
 * claimed.
 *
 * <p>It is a measurement helper: it builds a throwaway single-method module,
 * optimizes it, and reads the metrics. Nothing is emitted; it changes no
 * behavior.
 */
public final class OptimizerMetrics {

    /** The metrics before and after the production pipeline. */
    public record Comparison(IrMetrics before, IrMetrics after) {

        /** Instructions removed by the pipeline (positive = a reduction). */
        public int instructionDelta() {
            return before.instructions() - after.instructions();
        }

        /** Loads removed by the pipeline. */
        public int loadDelta() {
            return before.loads() - after.loads();
        }

        /** Stores removed by the pipeline. */
        public int storeDelta() {
            return before.stores() - after.stores();
        }

        /** True when the pipeline reduced the instruction count. */
        public boolean reduced() {
            return after.instructions() < before.instructions();
        }

        public String summary() {
            return "before[" + before.summary() + "] after[" + after.summary() + "]"
                    + " delta(instr=" + instructionDelta() + " loads=" + loadDelta()
                    + " stores=" + storeDelta() + ")";
        }
    }

    private OptimizerMetrics() {
    }

    /** Optimizes a method's op stream and compares its metrics before/after. */
    public static Comparison compare(List<KofOperation> ops) {
        IrMetrics before = IrMetrics.of(ops);
        IRModule optimized = Optimizer.optimize(moduleOf(ops));
        List<KofOperation> afterOps = new ArrayList<>();
        for (IRClass c : optimized.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    afterOps.addAll(b.operations());
                }
            }
        }
        return new Comparison(before, IrMetrics.of(afterOps));
    }

    private static IRModule moduleOf(List<KofOperation> ops) {
        IRMethod method = new IRMethod("m", Type.PrimitiveType.INT,
                List.<Type>of(), 0, List.<String>of(),
                List.of(new IRBasicBlock(0, ops)), List.of(), KofDebugInfo.EMPTY,
                List.of(), List.of());
        IRClass cls = new IRClass("Main", "java/lang/Object", List.<String>of(), 0,
                List.of(), List.of(method), List.<String>of(), null, 0, List.of(), false);
        return new IRModule("Main", List.of(cls), List.<String>of(), null, null);
    }
}

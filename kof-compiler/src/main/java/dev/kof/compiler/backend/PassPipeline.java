package dev.kof.compiler.backend;

import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A deterministic, ordered IR pass pipeline (Phase A of
 * {@code docs/development/assembly-optimization-plan.md} §21: "define the pass
 * pipeline").
 *
 * <p>The existing {@link Optimizer} hard-codes its pass order inline; this class
 * makes the order a first-class, composable list so later phases can register
 * new passes (copy propagation, DCE, …) and iterate them to a fixpoint. It is
 * additive: nothing in the compiler calls it yet, and the linear
 * {@link Optimizer} is unchanged — so behavior is identical until a caller
 * opts in.
 *
 * <p>Iteration stops when a full sweep produces no change (size and element
 * equality), capped by a bound to guarantee termination on a pass set that does
 * not converge.
 */
public final class PassPipeline {

    private final List<IrPass> passes;

    public PassPipeline(List<IrPass> passes) {
        this.passes = List.copyOf(passes);
    }

    /** The pipeline's passes, in application order. */
    public List<IrPass> passes() {
        return passes;
    }

    /** Runs each pass once, in order, threading the op list through. */
    public List<KofOperation> runOnce(List<KofOperation> ops,
                                      Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> cur = ops;
        for (IrPass p : passes) {
            cur = p.run(cur, positions);
        }
        return cur;
    }

    /**
     * Runs the pipeline repeatedly until a full sweep is a fixpoint, or
     * {@code maxSweeps} is reached. The result is deterministic: the same input
     * and pass list always yield the same output.
     */
    public List<KofOperation> runToFixpoint(List<KofOperation> ops,
                                            Map<KofOperation, SourcePosition> positions,
                                            int maxSweeps) {
        List<KofOperation> cur = ops;
        for (int i = 0; i < maxSweeps; i++) {
            List<KofOperation> next = runOnce(cur, positions);
            if (next.size() == cur.size() && next.equals(cur)) break;
            cur = next;
        }
        return cur;
    }

    /**
     * The pipeline seeded with the existing reusable pass (constant folding) plus
     * the Phase B passes as they land (currently: constant propagation). This is
     * the extension point Phase B grows. Nothing in the production
     * {@link Optimizer} calls this yet — behavior is unchanged until a caller
     * opts in.
     */
    public static PassPipeline standard() {
        List<IrPass> ps = new ArrayList<>();
        ps.add(new IrPass() {
            @Override
            public String name() {
                return "constant-fold";
            }

            @Override
            public List<KofOperation> run(List<KofOperation> ops,
                                          Map<KofOperation, SourcePosition> positions) {
                return OptimizerConstantFold.constantFold(ops, positions);
            }
        });
        ps.add(new ConstantPropagation());
        ps.add(new CopyPropagation());
        ps.add(new DeadCodeElimination());
        ps.add(new StrengthReduction());
        ps.add(new AlgebraicSimplification());
        ps.add(new JumpThreading());
        return new PassPipeline(ps);
    }
}

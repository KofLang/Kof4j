package dev.kof.compiler.backend;

import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;

import java.util.List;
import java.util.Map;

/**
 * An IR optimization pass (Phase A of
 * {@code docs/assembly-optimization-plan.md} §21: "define the pass
 * pipeline").
 *
 * <p>A pass is a semantics-preserving rewrite of the linear op stream. It gets
 * the ops and their debug positions, and returns a new list; positions of
 * removed ops are removed from the map, and a folded result keeps the position
 * of the first consumed op (the convention the existing
 * {@link OptimizerConstantFold} already follows). Passes are pure functions of
 * their inputs — no shared mutable state — so the pipeline can run them in any
 * order the plan specifies and iterate to a fixpoint deterministically.
 */
public interface IrPass {

    /** Stable name (for the pipeline order and diagnostics). */
    String name();

    /** Rewrites {@code ops} in place-in-map {@code positions}; returns the new ops. */
    List<KofOperation> run(List<KofOperation> ops, Map<KofOperation, SourcePosition> positions);
}

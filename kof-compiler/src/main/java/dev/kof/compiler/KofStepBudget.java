package dev.kof.compiler;

/**
 * Embedding contract (kofscript-embedding slice 2): the cooperative work
 * budget the host uses to BOUND interpreter progress.
 *
 * <p>{@link #tick()} is called once per executed IR operation. A host that
 * wants bounded execution installs a budget that blocks inside {@code tick()}
 * when its granted quota is exhausted — the interpreter then stops advancing
 * at an OP BOUNDARY (never mid-operation), so resuming is always safe.
 *
 * <p>This is deliberately NOT a script concurrency model: it is a host-side
 * control point. The default ({@link #UNBOUNDED}) ticks freely and preserves
 * the classic run-to-completion semantics.
 */
public interface KofStepBudget {

    /** No bound: run to completion (the classic behavior, byte-identical). */
    KofStepBudget UNBOUNDED = () -> { };

    /** One executed IR operation. May block (park) when the quota is spent. */
    void tick();
}

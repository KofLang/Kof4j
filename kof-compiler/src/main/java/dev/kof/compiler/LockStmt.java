package dev.kof.compiler;

/**
 * G240 — {@code lock (target) { body }}: mutual exclusion on {@code target}
 * for the duration of {@code body}, released on every exit path (normal end,
 * {@code return}, {@code break}/{@code continue}, a throw). Reentrant: the
 * thread that holds the lock may enter it again.
 *
 * <p>Lowering: the target is evaluated once into a hidden local,
 * {@code kof_lock_enter(target)} runs, and the body is lowered as the try body
 * of a {@code try/finally} whose finally is {@link LockExitStmt} (so the
 * existing finally machinery covers every exit). Each target supplies a real
 * lock: a per-object reentrant lock on the JVM and in Script, a reentrant
 * futex lock table on Native, a no-op on JS and Wasm (single-threaded, no
 * {@code spawn} preemption).</p>
 *
 * @param body always a {@link BlockStmt}
 */
public record LockStmt(SourcePosition position, ExpressionNode target,
                       StatementNode body) implements StatementNode {
}

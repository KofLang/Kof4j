package dev.kof.compiler;

/**
 * G240 — internal statement, created only by the lowering of
 * {@link LockStmt}: releases the lock held on the hidden local {@code slot}.
 * It is the finally body of the generated {@code try/finally}, so every exit
 * path re-emits it through the finally frames. Never produced by the parser.
 */
record LockExitStmt(SourcePosition position, Type targetType, int slot) implements StatementNode {
}

package dev.kof.compiler;

import java.util.List;

/**
 * D-SCOPED-RESOURCES-GO slice 1 — {@code using (name = init, closer) { body }}.
 *
 * <p>Parse-level only: {@link CompilerDesugar#desugarUsing} rewrites this into
 * {@code { var name = init; try { body } finally { closer } }} before semantic
 * analysis, so no typer/lowerer/codegen change is needed on any target. The
 * closer is an explicit expression (never a convention): {@code x.close()} is
 * false for {@code db} (the handle is a String closed via
 * {@code db.close(handle)}), while {@code conn.close()} / {@code sse.close()}
 * stay writable as the closer.</p>
 */
public record UsingStmt(SourcePosition position, String name, ExpressionNode init,
                 ExpressionNode closer, List<StatementNode> body) implements StatementNode {
}

package dev.kof.compiler;

import java.util.List;
public record ThrowStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

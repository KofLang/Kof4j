package dev.kof.compiler;

import java.util.List;
public record ExpressionStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

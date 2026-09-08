package dev.kof.compiler;

import java.util.List;
public record AssertStmt(SourcePosition position, ExpressionNode condition, String message) implements StatementNode {
}

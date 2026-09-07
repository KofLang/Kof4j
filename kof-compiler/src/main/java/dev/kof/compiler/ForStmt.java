package dev.kof.compiler;

import java.util.List;
public record ForStmt(SourcePosition position, StatementNode init, ExpressionNode condition,
               ExpressionNode update, StatementNode body) implements StatementNode {
}

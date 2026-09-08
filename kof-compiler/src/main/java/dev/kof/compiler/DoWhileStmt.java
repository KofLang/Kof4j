package dev.kof.compiler;

import java.util.List;
public record DoWhileStmt(SourcePosition position, ExpressionNode condition,
                   StatementNode body) implements StatementNode {
}

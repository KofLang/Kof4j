package dev.kof.compiler;

import java.util.List;
public record WhileStmt(SourcePosition position, ExpressionNode condition,
                 StatementNode body) implements StatementNode {
}

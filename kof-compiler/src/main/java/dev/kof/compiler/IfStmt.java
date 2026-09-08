package dev.kof.compiler;

import java.util.List;
public record IfStmt(SourcePosition position, ExpressionNode condition,
              StatementNode thenBranch, StatementNode elseBranch) implements StatementNode {
}

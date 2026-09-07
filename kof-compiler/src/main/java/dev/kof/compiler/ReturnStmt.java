package dev.kof.compiler;

import java.util.List;
public record ReturnStmt(SourcePosition position, ExpressionNode value) implements StatementNode {
}

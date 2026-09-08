package dev.kof.compiler;

import java.util.List;
public record SpawnStmt(SourcePosition position, ExpressionNode expression) implements StatementNode {
}

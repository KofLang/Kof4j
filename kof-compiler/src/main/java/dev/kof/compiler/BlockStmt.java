package dev.kof.compiler;

import java.util.List;
public record BlockStmt(SourcePosition position, List<StatementNode> statements) implements StatementNode {
}

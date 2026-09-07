package dev.kof.compiler;

import java.util.List;
public record ForInStmt(SourcePosition position, String varName, ExpressionNode collection,
                 StatementNode body) implements StatementNode {
}

package dev.kof.compiler;

import java.util.List;
public record VarDeclStmt(SourcePosition position, String type, String name,
                   ExpressionNode initializer) implements StatementNode {
}

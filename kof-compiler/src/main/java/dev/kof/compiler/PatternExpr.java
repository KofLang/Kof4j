package dev.kof.compiler;

import java.util.List;
public record PatternExpr(SourcePosition position, String typeName, String varName, java.util.List<String> fieldVars) implements ExpressionNode {
    public PatternExpr(SourcePosition position, String typeName, String varName) {
        this(position, typeName, varName, java.util.List.of());
    }
}

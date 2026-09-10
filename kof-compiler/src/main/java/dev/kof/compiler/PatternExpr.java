package dev.kof.compiler;

import java.util.List;

/**
 * SG-014: pattern de switch com GUARDA opcional — `case Point p if (p.x() > 0) :`.
 * O guard é avaliado com a var do pattern já bound; false → próximo case.
 */
public record PatternExpr(SourcePosition position, String typeName, String varName,
                   java.util.List<String> fieldVars,
                   ExpressionNode guard) implements ExpressionNode {
    public PatternExpr(SourcePosition position, String typeName, String varName) {
        this(position, typeName, varName, java.util.List.of(), null);
    }

    public PatternExpr(SourcePosition position, String typeName, String varName, java.util.List<String> fieldVars) {
        this(position, typeName, varName, fieldVars, null);
    }
}

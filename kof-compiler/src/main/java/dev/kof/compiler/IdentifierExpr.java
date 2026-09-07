package dev.kof.compiler;

import java.util.List;
public record IdentifierExpr(SourcePosition position, String name) implements ExpressionNode {
}

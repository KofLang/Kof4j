package dev.kof.compiler;

import java.util.List;
public record NewArrayExpr(SourcePosition position, String elementType, ExpressionNode size) implements ExpressionNode {
}

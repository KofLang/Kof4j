package dev.kof.compiler;

import java.util.List;
public record IfExpr(SourcePosition position, ExpressionNode condition,
              ExpressionNode thenExpr, ExpressionNode elseExpr) implements ExpressionNode {
}

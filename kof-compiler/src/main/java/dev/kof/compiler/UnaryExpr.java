package dev.kof.compiler;

import java.util.List;
public record UnaryExpr(SourcePosition position, String operator, ExpressionNode operand,
                 boolean prefix) implements ExpressionNode {
}

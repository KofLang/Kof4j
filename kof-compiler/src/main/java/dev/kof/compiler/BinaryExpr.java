package dev.kof.compiler;

import java.util.List;
public record BinaryExpr(SourcePosition position, String operator, ExpressionNode left,
                  ExpressionNode right) implements ExpressionNode {
}

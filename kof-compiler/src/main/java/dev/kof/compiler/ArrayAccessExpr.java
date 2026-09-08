package dev.kof.compiler;

import java.util.List;
public record ArrayAccessExpr(SourcePosition position, ExpressionNode receiver, ExpressionNode index) implements ExpressionNode {
}

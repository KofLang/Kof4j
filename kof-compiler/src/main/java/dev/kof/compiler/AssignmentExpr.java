package dev.kof.compiler;

import java.util.List;
public record AssignmentExpr(SourcePosition position, ExpressionNode target,
                      String operator, ExpressionNode value) implements ExpressionNode {
}

package dev.kof.compiler;

import java.util.List;
public record MethodCallExpr(SourcePosition position, ExpressionNode receiver,
                      String methodName, List<String> typeArguments,
                      List<ExpressionNode> arguments) implements ExpressionNode {
}

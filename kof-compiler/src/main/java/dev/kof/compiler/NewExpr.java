package dev.kof.compiler;

import java.util.List;
public record NewExpr(SourcePosition position, String typeName, List<String> typeArguments,
               List<ExpressionNode> arguments) implements ExpressionNode {
}

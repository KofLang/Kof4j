package dev.kof.compiler;

import java.util.List;
public record LambdaExpr(SourcePosition position, List<FormalParameterNode> parameters,
                  List<StatementNode> body) implements ExpressionNode {
}

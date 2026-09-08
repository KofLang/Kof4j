package dev.kof.compiler;

import java.util.List;
public record SwitchExpr(SourcePosition position, ExpressionNode expression,
                  List<SwitchExprCase> cases, ExpressionNode defaultValue) implements ExpressionNode {
}

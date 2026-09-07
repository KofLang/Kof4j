package dev.kof.compiler;

import java.util.List;
public record SwitchExprCase(SourcePosition position, ExpressionNode value,
                      ExpressionNode body) implements AstNode {
}

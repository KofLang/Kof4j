package dev.kof.compiler;

import java.util.List;
public record SwitchCase(SourcePosition position, ExpressionNode value, List<StatementNode> body) implements AstNode {
}

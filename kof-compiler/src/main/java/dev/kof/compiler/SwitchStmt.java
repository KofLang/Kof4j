package dev.kof.compiler;

import java.util.List;
public record SwitchStmt(SourcePosition position, ExpressionNode expression,
                  List<SwitchCase> cases, List<StatementNode> defaultBody) implements StatementNode {
}

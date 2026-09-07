package dev.kof.compiler;

import java.util.List;
public record TestDeclarationNode(SourcePosition position, String name,
                           List<StatementNode> body) implements AstNode {
}

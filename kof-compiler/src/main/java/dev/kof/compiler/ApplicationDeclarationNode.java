package dev.kof.compiler;

import java.util.List;
public record ApplicationDeclarationNode(SourcePosition position,
                                  List<StatementNode> onStart,
                                  List<StatementNode> onShutdown) implements AstNode {
}

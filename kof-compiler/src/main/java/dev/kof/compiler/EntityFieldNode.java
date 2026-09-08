package dev.kof.compiler;

import java.util.List;
public record EntityFieldNode(SourcePosition position, String type, String name,
                       boolean generated, boolean unique) implements AstNode {
}

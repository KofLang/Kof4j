package dev.kof.compiler;

import java.util.List;
public interface TypeDeclarationNode extends AstNode {
    String name();
    List<String> modifiers();
}

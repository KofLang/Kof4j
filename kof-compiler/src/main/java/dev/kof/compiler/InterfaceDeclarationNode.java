package dev.kof.compiler;

import java.util.List;
public record InterfaceDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                List<String> interfaces,
                                List<? extends AstNode> members,
                                List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public InterfaceDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                    List<String> interfaces, List<? extends AstNode> members) {
        this(position, name, modifiers, interfaces, members, List.of());
    }
}

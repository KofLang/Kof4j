package dev.kof.compiler;

import java.util.List;
public record EntityDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                             List<EntityFieldNode> fields,
                             List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public EntityDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 List<EntityFieldNode> fields) {
        this(position, name, modifiers, fields, List.of());
    }
}

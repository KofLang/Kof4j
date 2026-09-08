package dev.kof.compiler;

import java.util.List;
public record EnumDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                            List<String> constants,
                            List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public EnumDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                               List<String> constants) {
        this(position, name, modifiers, constants, List.of());
    }
}

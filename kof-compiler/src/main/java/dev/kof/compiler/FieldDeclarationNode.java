package dev.kof.compiler;

import java.util.List;
public record FieldDeclarationNode(SourcePosition position, List<String> modifiers, String type,
                            String name, ExpressionNode initializer,
                            List<AnnotationNode> annotations) implements MemberNode {

    public FieldDeclarationNode(SourcePosition position, List<String> modifiers, String type,
                                String name, ExpressionNode initializer) {
        this(position, modifiers, type, name, initializer, List.of());
    }
}

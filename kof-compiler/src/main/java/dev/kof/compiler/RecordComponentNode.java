package dev.kof.compiler;

import java.util.List;
public record RecordComponentNode(SourcePosition position, List<String> modifiers, String type, String name,
                            ExpressionNode initializer, List<AnnotationNode> annotations) implements AstNode {

    public RecordComponentNode(SourcePosition position, List<String> modifiers, String type, String name,
                               ExpressionNode initializer) {
        this(position, modifiers, type, name, initializer, List.of());
    }
}

package dev.kof.compiler;

import java.util.List;
public record FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                           String name, ExpressionNode defaultExpression,
                           List<AnnotationNode> annotations) implements AstNode {

    public FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                               String name, ExpressionNode defaultExpression) {
        this(position, modifiers, type, name, defaultExpression, List.of());
    }

    public FormalParameterNode(SourcePosition position, List<String> modifiers, String type,
                               String name) {
        this(position, modifiers, type, name, null, List.of());
    }
}

package dev.kof.compiler;

import java.util.List;
public record FunctionDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                               String name, List<FormalParameterNode> parameters,
                               List<String> thrownExceptions, List<String> typeParameters,
                               List<StatementNode> body, List<AnnotationNode> annotations) implements AstNode {

    public FunctionDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                                   String name, List<FormalParameterNode> parameters,
                                   List<String> thrownExceptions, List<String> typeParameters,
                                   List<StatementNode> body) {
        this(position, modifiers, returnType, name, parameters, thrownExceptions, typeParameters,
                body, List.of());
    }
}

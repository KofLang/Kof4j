package dev.kof.compiler;

import java.util.List;
public record MethodDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                             String name, List<FormalParameterNode> parameters,
                             List<String> thrownExceptions, List<StatementNode> body,
                             List<AnnotationNode> annotations) implements MemberNode {

    public MethodDeclarationNode(SourcePosition position, List<String> modifiers, String returnType,
                                 String name, List<FormalParameterNode> parameters,
                                 List<String> thrownExceptions, List<StatementNode> body) {
        this(position, modifiers, returnType, name, parameters, thrownExceptions, body, List.of());
    }
}

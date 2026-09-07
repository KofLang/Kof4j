package dev.kof.compiler;

import java.util.List;
public record ConstructorDeclarationNode(SourcePosition position, List<String> modifiers,
                                  String name, List<FormalParameterNode> parameters,
                                  List<String> thrownExceptions,
                                  List<StatementNode> body,
                                  List<AnnotationNode> annotations) implements MemberNode {

    public ConstructorDeclarationNode(SourcePosition position, List<String> modifiers,
                                      String name, List<FormalParameterNode> parameters,
                                      List<String> thrownExceptions,
                                      List<StatementNode> body) {
        this(position, modifiers, name, parameters, thrownExceptions, body, List.of());
    }
}

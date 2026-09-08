package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                             String superClass, List<String> interfaces,
                             List<String> typeParameters,
                             List<RecordComponentNode> components,
                             List<? extends AstNode> members,
                             List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 String superClass, List<String> interfaces,
                                 List<RecordComponentNode> components,
                                 List<? extends AstNode> members) {
        this(position, name, modifiers, superClass, interfaces, List.of(), components, members, List.of());
    }

    public RecordDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                 String superClass, List<String> interfaces,
                                 List<RecordComponentNode> components,
                                 List<? extends AstNode> members,
                                 List<AnnotationNode> annotations) {
        this(position, name, modifiers, superClass, interfaces, List.of(), components, members, annotations);
    }
}

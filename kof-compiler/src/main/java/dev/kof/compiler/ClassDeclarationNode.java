package dev.kof.compiler;

import java.util.List;
public record ClassDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                            String superClass, List<String> interfaces, List<String> typeParameters,
                            List<? extends AstNode> members, List<AnnotationNode> annotations) implements TypeDeclarationNode {

    public ClassDeclarationNode(SourcePosition position, String name, List<String> modifiers,
                                String superClass, List<String> interfaces, List<String> typeParameters,
                                List<? extends AstNode> members) {
        this(position, name, modifiers, superClass, interfaces, typeParameters, members, List.of());
    }
}

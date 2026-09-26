package dev.kof.compiler;

import java.util.List;

/**
 * {@code test "nome" { ... }} e {@code test "nome", "tag", ... { ... }}
 * (X8 fatia 3, {@code D-COMPLETE-FIRST}): o primeiro literal é o nome; cada
 * literal extra entre vírgulas é uma TAG da suíte. Zero sintaxe nova além da
 * vírgula — a forma mais curta que expressa a intenção (rule 11).
 */
public record TestDeclarationNode(SourcePosition position, String name,
                           List<String> tags, List<StatementNode> body) implements AstNode {

    public TestDeclarationNode {
        tags = List.copyOf(tags);
    }

    /** Forma sem tags (compatível com todo código 0.4/0.5 existente). */
    public TestDeclarationNode(SourcePosition position, String name, List<StatementNode> body) {
        this(position, name, List.of(), body);
    }
}

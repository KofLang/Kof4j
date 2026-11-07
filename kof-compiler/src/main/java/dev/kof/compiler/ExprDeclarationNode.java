package dev.kof.compiler;

import java.util.List;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`, mantenedora 03/11) — a DECLARAÇÃO de padrão:
 *
 * <pre>{@code
 * expr email {
 *     start
 *     one_or_more { letter digit "." "_" "%" "+" "-" }
 *     "@"
 *     ...
 *     end
 * }
 * }</pre>
 *
 * <p>É um kind de declaração de topo, modelado no {@link EnumDeclarationNode}:
 * nome + corpo, sem commits de lowering no spike-0 (§17 do plano: travar a
 * gramática e a AST, com prova de parser, antes de qualquer IR).
 */
public record ExprDeclarationNode(SourcePosition position, String name,
                                  List<String> modifiers, ExprNode body,
                                  List<AnnotationNode> annotations) implements AstNode {
}

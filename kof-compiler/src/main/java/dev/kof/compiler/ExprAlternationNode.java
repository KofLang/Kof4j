package dev.kof.compiler;

import java.util.List;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — a ALTERNATIVA explícita:
 * {@code either { a or b or c }}. Cada ramo é uma sequência (ou aninhada).
 *
 * <p>Não há {@code |}: a alternância é uma construção nomeada, com um
 * separador que se lê ({@code or}), para que o padrão continue legível para
 * quem nunca viu regex.
 */
public record ExprAlternationNode(SourcePosition position,
                                  List<ExprNode> alternatives) implements ExprNode {
}

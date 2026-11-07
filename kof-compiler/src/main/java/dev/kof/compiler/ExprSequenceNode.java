package dev.kof.compiler;

import java.util.List;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — a SEQUÊNCIA: as unidades em ordem. É a
 * forma do corpo de um `expr` e também a unidade que os quantificadores e as
 * capturas envolvem (`one_or_more { … }` quantifica a sequência do bloco).
 */
public record ExprSequenceNode(SourcePosition position,
                               List<ExprNode> units) implements ExprNode {
}

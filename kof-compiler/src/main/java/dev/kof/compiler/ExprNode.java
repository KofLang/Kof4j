package dev.kof.compiler;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`, mantenedora 03/11) — a árvore de um
 * padrão: o que o `expr NAME { … }` descreve. Os nós existem para que a
 * gramática seja TRAVADA com prova (parser) antes de qualquer lowering; o
 * spike-0 não emite IR nenhum.
 *
 * <p>Um padrão é uma SEQUÊNCIA de unidades ({@link ExprSequenceNode}). Cada
 * unidade é um átomo, um quantificador sobre uma unidade, um grupo, uma
 * alternativa, uma captura nomeada ou uma referência a outro `expr`.
 * Preparado para crescer por fatias: cada fatia nova acrescenta uma
 * variante aqui, nunca reescreve as anteriores.
 */
public sealed interface ExprNode extends AstNode permits
        ExprAtomNode, ExprSequenceNode, ExprQuantifierNode,
        ExprAlternationNode, ExprCaptureNode, ExprReferenceNode {
}

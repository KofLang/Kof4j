package dev.kof.compiler;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — uma CAPTURA nomeada:
 * {@code capture("name") { … }}. O que a unidade reconhece fica disponível no
 * resultado do casamento sob o nome dado.
 *
 * <p>A captura é sempre nomeada em v1 (captura anônima não tem intenção que
 * valha um nome no resultado). O corpo é uma unidade qualquer — hoje uma
 * sequência, um átomo, um quantificador ou uma alternativa.
 */
public record ExprCaptureNode(SourcePosition position, String name,
                              ExprNode body) implements ExprNode {
}

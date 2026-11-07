package dev.kof.compiler;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — a referência a OUTRO {@code expr} por
 * nome: {@code ipv4} usa três vezes o padrão {@code octet}. É a composição do
 * §5 do plano: um `expr` nomeado é referenciável onde um padrão é esperado,
 * exatamente como um tipo nomeado o é onde um tipo é esperado.
 *
 * <p>{@code alias} guarda a palavra como foi escrita; a resolução para o
 * {@code expr} declarado (ou a recusa, quando desconhecido ou cíclico) é uma
 * validação semântica — o parser não sabe quais `expr` existem.
 */
public record ExprReferenceNode(SourcePosition position, String alias) implements ExprNode {
}

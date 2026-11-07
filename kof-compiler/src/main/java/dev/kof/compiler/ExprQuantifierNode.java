package dev.kof.compiler;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — um QUANTIFICADOR sobre a unidade seguinte:
 * {@code optional { … }}, {@code zero_or_more { … }}, {@code one_or_more { … }},
 * {@code exactly(n) { … }}, {@code between(min, max) { … }}.
 *
 * <p>{@code kind} é a forma canônica; {@code min}/{@code max} só carregam valor
 * nos dois últimos ({@code exactly} usa só {@code min}, e {@code -1} em
 * {@code max} significa "sem teto" — a semântica valida o intervalo, nunca o
 * parser, para que a mensagem ensine a intenção).
 */
public record ExprQuantifierNode(SourcePosition position, String kind,
                                 int min, int max, ExprNode target) implements ExprNode {
}

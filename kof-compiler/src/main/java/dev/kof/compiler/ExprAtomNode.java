package dev.kof.compiler;

import java.util.List;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`) — um ÁTOMO do padrão: aquilo que reconhece
 * uma unidade de texto sem combinar nada.
 *
 * <p>{@code kind} é o átomo semântico canônico (`start`, `end`, `any`,
 * `letter`, `digit`, `whitespace`, `word`, `hex`, `literal`, `one_of`,
 * `none_of`); {@code values} carrega os literais de {@code literal}/
 * {@code one_of}/{@code none_of} e fica vazio nos demais atoms — a forma
 * escolhida mantém o nó único e a validação concentrada na semântica.
 */
public record ExprAtomNode(SourcePosition position, String kind,
                           List<String> values) implements ExprNode {
}

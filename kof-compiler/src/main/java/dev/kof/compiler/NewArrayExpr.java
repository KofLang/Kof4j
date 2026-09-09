package dev.kof.compiler;

import java.util.List;

/**
 * Criação de array: {@code new T[size]} (1 dim, caso canônico congelado) ou
 * multidimensional {@code new T[a][b][...]} — os tamanhos além do primeiro
 * ficam em {@code moreDims} (vazio = forma 1-dim, comportamento inalterado).
 * Dimensões vazias ({@code new T[2][]}) não são suportadas: PARSE046.
 */
public record NewArrayExpr(SourcePosition position, String elementType, ExpressionNode size,
                           List<ExpressionNode> moreDims) implements ExpressionNode {
    public NewArrayExpr(SourcePosition position, String elementType, ExpressionNode size) {
        this(position, elementType, size, List.of());
    }
}

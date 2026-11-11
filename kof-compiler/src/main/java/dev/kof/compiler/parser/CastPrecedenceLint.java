package dev.kof.compiler.parser;

import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.Token;

/**
 * #1095 — lint de precedência do {@code as}.
 *
 * <p>A precedência é CONGELADA (grammar §5.1): {@code as} fica no nível 5,
 * abaixo de {@code + -} (7), {@code * / %} (8) e dos shifts (6). Então
 * {@code now - h as Long * 3600000L} é {@code (now - h) as Long * 3600000L} —
 * o {@code as} pega a expressão inteira à esquerda. Quem vem de Java (cast
 * unário) ou Kotlin ({@code as} acima da multiplicação) lê o oposto, e nada
 * avisava: o programa calculava outro número em silêncio.
 *
 * <p>Sem tocar na precedência, o parser AVISA ({@code PARSE100}) quando o
 * operando esquerdo do {@code as} é uma expressão NÃO parentetizada com um
 * operador mais forte ({@code + - * / % << >> >>>}) construída pela mesma
 * escada de precedência. Um operando parentetizado volta de
 * {@code parsePrimary} e está marcado em {@link ParseContext#parenthesized},
 * então {@code (a - b) as T}, {@code a - (b as T)}, {@code h as Long * k},
 * {@code x as Long == 2L} e {@code a as B as C} ficam silenciosos.
 */
final class CastPrecedenceLint {

    private CastPrecedenceLint() {}

    private static final String MESSAGE =
            "'as' binds looser than arithmetic and shifts, so it casts the whole "
            + "expression on its left; parenthesize the operand you mean";

    static void lint(ParseContext ctx, ExpressionNode left, Token asToken) {
        if (!(left instanceof BinaryExpr be)) return;
        if (ctx.parenthesized.contains(left)) return;
        if (!isTighterOperator(be.operator())) return;
        ctx.diagnostics.warning(ctx.file, asToken.line(), asToken.column(), asToken.length(),
                MESSAGE, "PARSE100");
    }

    private static boolean isTighterOperator(String op) {
        return switch (op) {
            case "+", "-", "*", "/", "%", "<<", ">>", ">>>" -> true;
            default -> false;
        };
    }
}

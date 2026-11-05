package dev.kof.compiler.parser;

import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.Token;

/**
 * §815 — {@code as} sits at the relational level (grammar §5.1, a frozen
 * precedence), so in {@code now - h as Long * 3600000L} the cast takes
 * {@code now - h}, not {@code h}, and the product silently runs on the
 * difference: a wrong number, no diagnostic. The left operand of {@code as}
 * that the same precedence loop has just built from a tighter operator is
 * never parenthesized (a parenthesized operand comes back from
 * {@code parseUnary}), so {@code PARSE100} warns on exactly that shape and
 * asks for the parentheses that state the intent. The parse itself is
 * unchanged.
 */
final class CastPrecedenceLint {

    private CastPrecedenceLint() {}

    /** Warns on {@code a OP b as T} with OP tighter than {@code as};
     *  returns the precedence of {@code op} for the caller's climb. */
    static int check(ParseContext ctx, Token op, ExpressionNode left, boolean leftIsBareBinary) {
        int prec = ExpressionParser.precedence(ctx, op.value());
        if ("as".equals(op.value()) && leftIsBareBinary && left instanceof BinaryExpr bin
                && ExpressionParser.precedence(ctx, bin.operator()) > prec) {
            ctx.diagnostics.warning(ctx.file, op.line(), op.column(), op.length(),
                    "'as' binds looser than arithmetic and shifts, so it casts the whole expression"
                            + " on its left; parenthesize the operand you mean: (a - b) as T or a - (b as T)",
                    "PARSE100");
        }
        return prec;
    }
}

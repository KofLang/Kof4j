package dev.kof.compiler.parser;

import dev.kof.compiler.Token;

/**
 * §815 — {@code as} used to sit at the relational level (precedence 5), so
 * {@code now - h as Long * 3600000L} cast {@code now - h} and multiplied the
 * difference: a wrong number with no diagnostic. {@code as} now binds tighter
 * than arithmetic, shifts and comparisons (precedence 9, as in Kotlin), and
 * the same text means {@code now - ((h as Long) * 3600000L)}.
 *
 * <p>Migration: code written for the old table changes meaning silently
 * exactly when {@code as} is taken by an operand climb at a level the old
 * {@code as} could not reach ({@code minPrec} above 5), i.e. it is the
 * unparenthesized operand of an arithmetic, shift or comparison operator.
 * {@code PARSE100} warns on that shape; a parenthesized cast restarts the
 * climb at 0 and stays silent.
 */
final class CastPrecedenceLint {

    private CastPrecedenceLint() {}

    /** Warns on {@code a OP b as T} whose parse changed with §815;
     *  returns the precedence of {@code op} for the caller's climb. */
    static int check(ParseContext ctx, Token op, int minPrec) {
        if ("as".equals(op.value()) && minPrec > OperatorPrecedence.LEGACY_AS && minPrec != OperatorPrecedence.ELVIS_OPERAND) {
            ctx.diagnostics.warning(ctx.file, op.line(), op.column(), op.length(),
                    "'as' now binds tighter than arithmetic, shifts and comparisons, so it casts"
                            + " only the operand on its left (before 0.8 it cast the whole expression);"
                            + " parenthesize to state the intent: a - (b as T) or (a - b) as T",
                    "PARSE100");
        }
        return ExpressionParser.precedence(ctx, op.value());
    }
}

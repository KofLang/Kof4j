package dev.kof.compiler.parser;

/**
 * The binary operator precedence table (grammar §5.1), the one source for the
 * parser's precedence climb and the formatter's parenthesization. The
 * formatter kept a copy, so §815 (`as` moved above arithmetic) would have
 * made `kof fmt` drop the parentheses of `(a - b) as T` and change its
 * meaning.
 */
public final class OperatorPrecedence {

    /** Where {@code as} sat before §815: an {@code as} taken by an operand climb
     *  above this level reads differently than it did before 0.8 (PARSE100). */
    public static final int LEGACY_AS = 5;

    /** The climb level of the right operand of {@code ?:} (§821, new in 0.8):
     *  a cast there has no older reading, so it is not a PARSE100 position. */
    public static final int ELVIS_OPERAND = 7;

    private OperatorPrecedence() {}

    /** Higher binds tighter; -1 for a token that is not a binary operator. */
    public static int of(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|", "^" -> 3;
            case "&" -> 4;
            case "==", "!=", "<", "<=", ">", ">=", "instanceof" -> 5;
            case "?:" -> 6; // §821: above comparisons, below shifts, as in Kotlin
            case "<<", ">>", ">>>" -> 7;
            case "+", "-" -> 8;
            case "*", "/", "%" -> 9;
            case "as" -> 10; // §815: tighter than arithmetic, as in Kotlin
            default -> -1;
        };
    }
}

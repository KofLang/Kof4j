package dev.kof.compiler;

/**
 * #1195: the collection factories ({@code listOf}/{@code mapOf}/{@code setOf})
 * took a {@code null} literal as an element and the program only broke when the
 * value was read — the same {@code null} that SEM048 refuses in a variable, in
 * an assignment, in a non-nullable parameter and in {@code return} (SG-008: no
 * {@code null} literal is assignable, not even to {@code T?}).
 *
 * <p>Only an element type that this module actually KNOWS is judged: an
 * unresolved element cannot host a {@code T?} and rejecting it would be a guess.
 */
final class CollectionNullElements {

    private CollectionNullElements() {}

    static void reject(SemanticAnalyzer sa, MethodCallExpr mc, Type elementType, String role) {
        if (sa.diagnostics() == null || elementType == null) return;
        if (elementType instanceof Type.UnknownType) return;
        for (ExpressionNode arg : mc.arguments()) {
            if (!CompilerComparisons.isNullLiteral(arg)) continue;
            sa.diagnostics().error(arg,
                    "null cannot be a " + role + " element: null safety works by narrowing"
                            + " (if (x != null)), never by direct null literals",
                    "SEM048");
        }
    }
}

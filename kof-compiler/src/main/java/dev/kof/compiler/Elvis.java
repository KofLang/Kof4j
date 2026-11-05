package dev.kof.compiler;

import java.util.List;

/**
 * §821 — the elvis operator {@code a ?: b} (maintainer decision
 * {@code D-ELVIS}, 05/11): the value of {@code a} when it is not null,
 * otherwise {@code b}; {@code a} is evaluated once and {@code b} only when
 * {@code a} is null. Before, a default for an optional needed an
 * {@code if} statement into a local or a helper ({@code ?:} was PARSE041).
 *
 * <p>Typing: with {@code a: T?}, the result is {@code T} when {@code b} is a
 * {@code T}, and {@code T?} when {@code b} is itself nullable (or the
 * {@code null} literal). A non-nullable {@code a} is the result as it is
 * ({@code b} is never needed). A {@code b} that is not a {@code T} is SEM014.
 *
 * <p>Lowering uses only existing ops (a temporary local, the reference
 * comparison against {@code null} that {@code x != null} uses, and
 * {@code kof_unbox} for a primitive result), so the four targets share it.
 */
final class Elvis {

    static final String OP = "?:";

    private Elvis() {}

    static Type resultType(Type left, Type right, ExpressionNode rightNode) {
        if (!(left instanceof Type.NullableType nt)) return left;
        boolean rightNullable = right instanceof Type.NullableType || Type.isUnknown(right)
                || CompilerComparisons.isNullLiteral(rightNode);
        return rightNullable ? left : nt.inner();
    }

    /** The result type, with SEM014 when the default is not a {@code T}. */
    static Type check(SemanticAnalyzer sa, BinaryExpr bin, Type left, Type right) {
        if (left instanceof Type.NullableType nt && sa.diagnostics() != null && !Type.isUnknown(right)
                && !CompilerComparisons.isNullLiteral(bin.right()) && !Type.isUnknown(nt.inner())) {
            Type want = right instanceof Type.NullableType ? left : nt.inner();
            if (!TypeChecker.isAssignable(sa, right, want)) {
                sa.diagnostics().error(bin.right(), "The default of '?:' must be a '" + Type.display(want)
                        + "' (the left side is '" + Type.display(left) + "') but got '"
                        + Type.display(right) + "'", "SEM014");
            }
        }
        return resultType(left, right, bin.right());
    }

    static int lower(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops, String owner,
                     int localIdx, List<IRLocalVariable> locals) {
        Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type result = resultType(left, ExpressionTyper.inferExprType(driver, bin.right(), locals), bin.right());
        localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
        if (TypeMetrics.isPrimitiveType(left) && !(left instanceof Type.NullableType)) {
            return localIdx; // a primitive is never null: the default is dead code
        }
        int tmp = localIdx;
        ops.add(new KofStoreLocal(left, tmp));
        locals.add(new IRLocalVariable(tmp, "#elvis", left));
        localIdx += TypeMetrics.isDoubleWidth(left) ? 2 : 1;
        LabelId keep = LabelId.create();
        LabelId fallback = LabelId.create();
        LabelId end = LabelId.create();
        ops.add(new KofLoadLocal(left, tmp));
        ops.add(KofLoadLiteral.ofNull());
        ops.add(new KofConditionalJump(KofComparison.NE,
                new Type.ClassType("java.lang", "Object", List.of()), keep, fallback));
        ops.add(new KofLabel(keep));
        ops.add(new KofLoadLocal(left, tmp));
        if (TypeMetrics.isPrimitiveType(result) && !(result instanceof Type.NullableType)) {
            // T? holds the boxed value (D-NULL-INTENT); a T result is the raw primitive.
            // Char is boxed as Integer (§104b-ii), so its unbox returns an Int.
            Type raw = TypeMetrics.isCharType(result) ? Type.PrimitiveType.INT : result;
            ops.add(new KofCall(raw, "kof_unbox", List.of(TypeMetrics.boxedTypeFor(result)), raw,
                    KofCallKind.FUNCTION));
        }
        ops.add(new KofJump(end));
        ops.add(new KofLabel(fallback));
        localIdx = ExpressionLowerer.emitExpression(driver, bin.right(), ops, owner, localIdx, locals);
        ops.add(new KofLabel(end));
        return localIdx;
    }
}

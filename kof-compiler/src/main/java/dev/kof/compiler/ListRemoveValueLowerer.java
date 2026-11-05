package dev.kof.compiler;

import java.util.List;

/**
 * §824 — {@code xs.removeValue(v)}: removes the first element equal to
 * {@code v} and returns whether one was found ({@code remove(i)} stays
 * index-based; maintainer decision 05/11).
 *
 * <p>Library-first, like {@link CollectionZipLowerer}: the call is rebuilt
 * from two existing list ops, {@code indexOf} (same equality and string tag
 * as {@code contains}) and {@code remove(i)}, over temporaries, so every
 * target gets it with no new runtime entry:
 * {@code i = xs.indexOf(v); if (i >= 0) { xs.remove(i); true } else false}.
 *
 * <p>The caller already pushed the receiver, so the stack is {@code [recv]}.
 * The receiver and the argument are each evaluated once.
 */
final class ListRemoveValueLowerer {

    static final String METHOD = "removeValue";

    private ListRemoveValueLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        var pos = mc.position();
        if (mc.arguments().size() != 1) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "List.removeValue takes exactly one argument (the value to remove)", "SEM025");
            }
            return localIdx;
        }
        int recvIdx = localIdx++;
        locals.add(new IRLocalVariable(recvIdx, "$kwrvrecv" + recvIdx, recvType));
        ops.add(new KofStoreLocal(recvType, recvIdx));
        ExpressionNode arg = mc.arguments().get(0);
        Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        int argIdx = localIdx;
        localIdx += TypeMetrics.isDoubleWidth(argType) ? 2 : 1;
        locals.add(new IRLocalVariable(argIdx, "$kwrvarg" + argIdx, argType));
        ops.add(new KofStoreLocal(argType, argIdx));

        ExpressionNode list = new IdentifierExpr(pos, "$kwrvrecv" + recvIdx);
        localIdx = ExpressionLowerer.emitExpression(driver, new MethodCallExpr(pos, list, "indexOf",
                List.of(), List.of(new IdentifierExpr(pos, "$kwrvarg" + argIdx))), ops, owner, localIdx, locals);
        int posIdx = localIdx++;
        locals.add(new IRLocalVariable(posIdx, "$kwrvidx" + posIdx, Type.PrimitiveType.INT));
        ops.add(new KofStoreLocal(Type.PrimitiveType.INT, posIdx));

        LabelId found = LabelId.create();
        LabelId missing = LabelId.create();
        LabelId end = LabelId.create();
        ops.add(new KofLoadLocal(Type.PrimitiveType.INT, posIdx));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.GE, found, missing));
        ops.add(new KofLabel(found));
        MethodCallExpr removeAt = new MethodCallExpr(pos, list, "remove",
                List.of(), List.of(new IdentifierExpr(pos, "$kwrvidx" + posIdx)));
        Type removed = ExpressionTyper.inferExprType(driver, removeAt, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, removeAt, ops, owner, localIdx, locals);
        ops.add(TypeMetrics.isDoubleWidth(removed) ? new KofPop2() : new KofPop());
        ops.add(KofLoadLiteral.ofBool(true));
        ops.add(new KofJump(end));
        ops.add(new KofLabel(missing));
        ops.add(KofLoadLiteral.ofBool(false));
        ops.add(new KofLabel(end));
        return localIdx;
    }
}

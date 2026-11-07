package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * #1116 — {@code List.removeValue(v)}: remove the FIRST element equal to
 * {@code v} and report whether one was found.
 *
 * <p>Composed from the LANDED {@code indexOf} + {@code remove} rather than a
 * new runtime primitive, so all five targets inherit it with no backend work:
 *
 * <pre>
 *   idx = indexOf(v)          // -1 when absent (the java.util.List contract)
 *   if (idx &lt; 0) return false
 *   remove(idx)               // the index form, already implemented everywhere
 *   return true
 * </pre>
 *
 * <p>The element tag that {@code indexOf} needs (raw / String content / object
 * equals) is the same one {@code contains} and {@code indexOf} already compute
 * in {@link CollectionWrites#stringTag}.
 */
final class CollectionRemoveValueLowerer {

    private CollectionRemoveValueLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                     List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        if (mc.arguments().size() != 1) {
            if (driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "List.removeValue takes exactly one argument (the value to remove); got "
                                + mc.arguments().size(),
                        "SEM025");
            }
            return localIdx;
        }
        Type elemType = driver.listElementType(recvType);
        Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);

        // The receiver is ALREADY on the stack (ExpressionInstanceCallLowerer
        // emits it before dispatching here) — store it into a temp so indexOf
        // and remove see the same list without re-evaluating the receiver.
        int listTmp = localIdx++;
        ops.add(new KofStoreLocal(recvType, listTmp));
        locals.add(new IRLocalVariable(listTmp, "#removeValueList", recvType));

        // idx = indexOf(v) — the tag is the same one contains/indexOf use.
        int idxTmp = localIdx++;
        ops.add(new KofLoadLocal(recvType, listTmp));
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                CollectionWrites.stringTag(elemType, List.of(argType), 0)));
        ops.add(new KofCall(recvType, "kof_list_index_of",
                List.of(argType, Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.INSTANCE));
        ops.add(new KofStoreLocal(Type.PrimitiveType.INT, idxTmp));
        locals.add(new IRLocalVariable(idxTmp, "#removeValueIdx", Type.PrimitiveType.INT));

        // result = false; if (idx >= 0) { remove(idx); result = true }
        // A result TEMP keeps the frame balanced across the branch join (a
        // bare pop or a value left on the stack crashed the frame computation,
        // measured COMP002).
        int resultTmp = localIdx++;
        locals.add(new IRLocalVariable(resultTmp, "#removeValueResult", Type.PrimitiveType.BOOL));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, 0));
        ops.add(new KofStoreLocal(Type.PrimitiveType.BOOL, resultTmp));

        LabelId found = LabelId.create();
        LabelId skip = LabelId.create();
        ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        ops.add(new KofConditionalJump(KofComparison.GE, found, skip));

        ops.add(new KofLabel(found));
        // remove(idx) — the index form, already implemented on every target.
        // The removed element goes into a throwaway temp (the contract returns
        // Bool, not the value).
        int discarded = localIdx++;
        locals.add(new IRLocalVariable(discarded, "#removeValueDiscard", elemType));
        ops.add(new KofLoadLocal(recvType, listTmp));
        ops.add(new KofLoadLocal(Type.PrimitiveType.INT, idxTmp));
        ops.add(new KofCall(recvType, "kof_list_remove",
                List.of(Type.PrimitiveType.INT), elemType, KofCallKind.INSTANCE));
        ops.add(new KofStoreLocal(elemType, discarded));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, 1));
        ops.add(new KofStoreLocal(Type.PrimitiveType.BOOL, resultTmp));

        ops.add(new KofLabel(skip));
        ops.add(new KofLoadLocal(Type.PrimitiveType.BOOL, resultTmp));
        return localIdx;
    }
}

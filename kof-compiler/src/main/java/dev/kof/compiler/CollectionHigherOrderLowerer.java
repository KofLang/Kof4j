package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering dos higher-orders de {@code List} (map/filter/reduce +
 * any/all/none da D-MULTIPARADIGMA-PHASE1A), extraído do
 * {@link CollectionCallLowerer} (gate 500 — o bloco higher-order cruzou a
 * linha com os quantificadores). Retorna -1 quando não é higher-order de
 * List (o chamador cai no restante do lowering). Corpo verbatim do original.
 */
final class CollectionHigherOrderLowerer {

    private CollectionHigherOrderLowerer() {
    }

    static int lowerHo(CompilerDriver driver, Type recvType, MethodCallExpr mc,
                       List<KofOperation> ops, String owner, int localIdx,
                       List<IRLocalVariable> locals) {
        String mn = mc.methodName();
        // count with a lambda counts matches (same FUNCTION shape as the
        // other higher-orders); bare count keeps the kof_list_size path.
        boolean countPred = "count".equals(mn) && mc.arguments().stream()
                .anyMatch(a -> a instanceof LambdaExpr);
        if (!BuiltinTypes.isList(recvType)
                || !("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn)
                    || "any".equals(mn) || "all".equals(mn) || "none".equals(mn)
                    || "find".equals(mn) || "forEach".equals(mn) || "flatMap".equals(mn)
                    || countPred)) {
            return -1;
        }
        // kof_ names are snake_case: camelCase surface names map explicitly
        // (forEach→foreach, flatMap→flatmap — like indexOf→kof_list_index_of
        // in the switch below). A verbatim "kof_list_flatMap" misses every
        // registry (hasRuntimeFn/descriptors/pieces) and breaks per-target.
        String hook = mc.methodName();
        if ("forEach".equals(hook)) hook = "foreach";
        else if ("flatMap".equals(hook)) hook = "flatmap";
        String hoFn = countPred ? "kof_list_count_pred" : "kof_list_" + hook;
        // D-MULTIPARADIGMA-PHASE1A — quantifiers/find/count(pred)/forEach take
        // exactly one lambda; without it the runtime call would break
        // per-target (R6, same class as #382's indexOf gate). map/filter/reduce
        // keep theirs.
        if ("any".equals(mc.methodName()) || "all".equals(mc.methodName())
                || "none".equals(mc.methodName()) || "find".equals(mc.methodName())
                || "forEach".equals(mc.methodName()) || "flatMap".equals(mc.methodName())
                || countPred) {
            boolean oneLambda = mc.arguments().size() == 1
                    && mc.arguments().get(0) instanceof LambdaExpr;
            if (!oneLambda && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "List." + mc.methodName() + " takes exactly one lambda argument",
                        "SEM025");
                return localIdx;
            }
        }
        // receiver já empilhado acima (3396) — não duplicar
        Type lambdaT = Type.UnknownType.UNKNOWN;
        // reduce: init antes; lambda por último
        for (ExpressionNode arg : mc.arguments()) {
            if (!(arg instanceof LambdaExpr)) {
                Type argT = ExpressionTyper.inferExprType(driver, arg, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                // (#57: IfExpr/switch heterogêneo já boxeou in-branch → pular)
                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM
                        && !ExpressionTyper.boxesOwnBranches(driver, arg, locals)) {
                    Type boxed = TypeMetrics.boxedTypeFor(argT);
                    ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                }
            }
        }
        for (ExpressionNode arg : mc.arguments()) {
            if (arg instanceof LambdaExpr lam) {
                lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, lam, ops, owner, localIdx, locals);
            }
        }
        List<Type> callParams = new ArrayList<>();
        callParams.add(new Type.ClassType("java.util", "ArrayList", List.of()));
        if ("reduce".equals(mc.methodName())) callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        // D-MULTIPARADIGMA-PHASE1A slice 1b — find carries a static box tag
        // (NativeBoxTags numbering via Gates.findBoxTag): Native list slots
        // hold RAW primitives but T? consumers expect boxed values (Map slots
        // are boxed); the hit path boxes per tag, miss stays 0. JVM pops it
        // (static takes and ignores the int); JS/Script ignore the extra arg.
        if ("find".equals(mc.methodName())) {
            int tag = CollectionMethodGates.findBoxTag(driver.listElementType(recvType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
            callParams.add(Type.PrimitiveType.INT);
        }
        Type ret;
        if ("filter".equals(mc.methodName())) ret = recvType;
        // D-MULTIPARADIGMA-PHASE1A — quantifiers always return Bool; find
        // returns the element type as nullable (missing = null per target,
        // mirroring Map.get).
        else if ("any".equals(mc.methodName()) || "all".equals(mc.methodName())
                || "none".equals(mc.methodName()))
            ret = Type.PrimitiveType.BOOL;
        else if ("find".equals(mc.methodName()))
            ret = new Type.NullableType(driver.listElementType(recvType));
        else if (countPred)
            ret = Type.PrimitiveType.INT;
        // D-MULTIPARADIGMA-PHASE1A slice 1c — forEach always returns Void.
        else if ("forEach".equals(mc.methodName()))
            ret = Type.PrimitiveType.VOID;
        // D-MULTIPARADIGMA-PHASE1A slice 1d — flatMap returns the lambda's
        // List<R> itself (no re-wrap); non-List lambda result is UNKNOWN.
        else if ("flatMap".equals(mc.methodName())) {
            if (lambdaT instanceof Type.FunctionType ft
                    && !(ft.returnType() instanceof Type.UnknownType)
                    && ft.returnType() instanceof Type.ClassType ct
                    && "List".equals(ct.name())) ret = ft.returnType();
            else ret = Type.UnknownType.UNKNOWN;
        }
        else if (countPred)
            ret = Type.PrimitiveType.INT;
        else if ("map".equals(mc.methodName())) {
            Type elem = (lambdaT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType)) ? ft.returnType() : Type.UnknownType.UNKNOWN;
            ret = new Type.ClassType("kof", "List", List.of(elem));
        } else {
            ret = (lambdaT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
        }
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()), hoFn, callParams, ret,
                KofCallKind.FUNCTION));
        return localIdx;
    }
}

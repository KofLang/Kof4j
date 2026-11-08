package dev.kof.compiler;

import java.util.List;

/**
 * #1054 (split do gate 500, extraído de {@code ExpressionInstanceCallLowerer}):
 * as conversões numéricas em receiver PRIMITIVO —
 * {@code toInt()}/{@code toLong()}/{@code toFloat()}/{@code toDouble()} como
 * alias do {@code as} (§89, decisão 3a 13/09), com a ponte de representação
 * da #845 para receiver PRIMITIVO-ANULÁVEL ESTREITADO (o slot é a caixa do
 * D-NULL-INTENT; a caixa abre antes do alargamento cru).
 */
final class PrimitiveConversionLowerer {

    private PrimitiveConversionLowerer() {}

    /** O método é uma conversão numérica? (o chamador já garantiu argc==0). */
    static boolean isConversion(String methodName) {
        return "toInt".equals(methodName) || "toLong".equals(methodName)
                || "toFloat".equals(methodName) || "toDouble".equals(methodName);
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, Type recvType,
                     List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        Type target = switch (mc.methodName()) {
            case "toInt" -> Type.PrimitiveType.INT;
            case "toLong" -> Type.PrimitiveType.LONG;
            case "toFloat" -> Type.PrimitiveType.FLOAT;
            default -> Type.PrimitiveType.DOUBLE;
        };
        // #1054: the narrowed nullable-primitive receiver's SLOT is the boxed
        // reference (D-NULL-INTENT: the slot stays Nullable) while the semantic
        // type says primitive — the raw numeric widening (l2d/l2i) would read
        // the reference as a number and crash the frame computation. Open the
        // box first (the same bridge the primitive consumers use since #845).
        if (mc.receiver() instanceof IdentifierExpr rid) {
            IRLocalVariable lv = driver.findLocalVar(rid.name(), locals);
            if (lv != null) {
                recvType = CompilerEmissionHelpers.emitNarrowedNullableUnbox(
                        driver, ops, lv.type(), recvType);
            }
        }
        String fn0 = TypeMetrics.primitiveName(recvType);
        boolean mayTruncate = ("toInt".equals(mc.methodName()) || "toLong".equals(mc.methodName()))
                && (fn0.isEmpty() || "double".equals(fn0) || "Double".equals(fn0)
                    || "float".equals(fn0) || "Float".equals(fn0));
        if (mayTruncate && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.warning("", 0, 0, 0,
                    "'" + mc.methodName() + "()' may truncate (fractional part discarded; "
                        + "overflow throws) — explicit form: value as "
                        + TypeMetrics.primitiveName(target), "SEM090");
        }
        driver.emitWideningIfNeeded(ops, recvType, target);
        // #1087: the byte/short/char narrow ops (incl. the wide→Int step)
        // live in emitPrimNarrow now.
        driver.emitPrimNarrow(ops, recvType, target);
        return localIdx;
    }
}

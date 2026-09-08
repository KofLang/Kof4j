package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace http (http.*) no emitExpression.
 */
public final class ExpressionHttpCallLowerer {

    private ExpressionHttpCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
    if (httpCall != null) {
        if (!KofHttp.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (HTTP002)",
                        "HTTP002");
            }
            return localIdx;
        }
        // headers variádicos (GitHub #32): o runtime recebe headers como uma
        // única String `\n`-separada. args fixos = url (+body p/ post/put/patch);
        // os demais são headers, colapsados em um só slot.
        int fixed = ("post".equals(mc.methodName()) || "put".equals(mc.methodName())
                || "patch".equals(mc.methodName())) ? 2 : 1;
        int n = mc.arguments().size();
        for (int i = 0; i < Math.min(n, fixed); i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
        }
        if (n > fixed) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(fixed), ops, owner, localIdx, locals);
            for (int i = fixed + 1; i < n; i++) {
                // pilha: [..., acc]; queremos acc + "\n" + h_i
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "\n"));
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
        }
        ops.add(new KofCall(KofHttp.HTTP, httpCall.function(), httpCall.parameterTypes(),
                httpCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}
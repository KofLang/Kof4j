package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace http (http.*) no emitExpression.
 */
final class ExpressionHttpCallLowerer {

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
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofHttp.HTTP, httpCall.function(), httpCall.parameterTypes(),
                httpCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}
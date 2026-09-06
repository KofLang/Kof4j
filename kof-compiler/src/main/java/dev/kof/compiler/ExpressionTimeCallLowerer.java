package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace time (time.*) no emitExpression.
 */
final class ExpressionTimeCallLowerer {

    private ExpressionTimeCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
    if (timeCall != null) {
        if (!KofTime.supportedOn(mc.methodName(), driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (TIME001)",
                        "TIME001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofTime.TIME, timeCall.function(), timeCall.parameterTypes(),
                timeCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}
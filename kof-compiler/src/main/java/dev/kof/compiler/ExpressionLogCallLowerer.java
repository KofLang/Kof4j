package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace log (log.*) no emitExpression.
 */
final class ExpressionLogCallLowerer {

    private ExpressionLogCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
    if (logCall != null) {
        if (!KofLog.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofLog.gapCode() + ")",
                        KofLog.gapCode());
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.log", "Log", List.of()),
                logCall.function(), logCall.parameterTypes(), logCall.returnType(),
                KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}

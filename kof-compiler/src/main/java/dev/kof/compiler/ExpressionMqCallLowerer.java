package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace mq (mq.*) no emitExpression.
 */
final class ExpressionMqCallLowerer {

    private ExpressionMqCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
    if (mqCall != null) {
        if (!KofMq.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (MQ001)",
                        "MQ001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofMq.MQ, mqCall.function(), mqCall.parameterTypes(),
                mqCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}
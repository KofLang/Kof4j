package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace shell (`shell.*`) no emitExpression. JVM-first;
 * SHL001 nos demais targets (gap honesto, nunca stub silencioso).
 */
final class ExpressionShellCallLowerer {

    private ExpressionShellCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) {
            argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        }
        KofShell.ShellCall sh = KofShell.staticCall(mc.methodName(), argTypes);
        if (sh != null) {
            if (!KofShell.supportedOn(driver.target)) {
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(
                            mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0,
                            mc.methodName() + ": not available on the " + driver.target
                                    + " target yet (SHL001)",
                            "SHL001");
                }
                return localIdx;
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofShell.SHELL, sh.function(), sh.parameterTypes(),
                    sh.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
        return localIdx;
    }
}
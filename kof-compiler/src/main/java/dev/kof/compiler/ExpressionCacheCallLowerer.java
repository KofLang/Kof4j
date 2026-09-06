package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do namespace cache (cache.*) no emitExpression.
 */
final class ExpressionCacheCallLowerer {

    private ExpressionCacheCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
    if (cacheCall != null) {
        if (!KofCache.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (CACHE001)",
                        "CACHE001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofCache.CACHE, cacheCall.function(), cacheCall.parameterTypes(),
                cacheCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
    }
}
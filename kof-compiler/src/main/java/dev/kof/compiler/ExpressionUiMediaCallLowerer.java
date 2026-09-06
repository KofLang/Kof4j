package dev.kof.compiler;

import java.util.List;

/**
 * Lowering dos namespaces Ui/Media/Io (receiver estático) no emitExpression.
 * Retorna -1 se nenhum namespace casou.
 */
final class ExpressionUiMediaCallLowerer {

    private ExpressionUiMediaCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(((IdentifierExpr) mc.receiver()).name())) {
    KofIo.IoCall ioCall = KofIo.staticMethod(((IdentifierExpr) mc.receiver()).name(), mc.methodName(), mc.arguments().size());
    if (ioCall != null) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                ioCall.function(), ioCall.parameterTypes(), ioCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofMedia.isStaticNamespace(((IdentifierExpr) mc.receiver()).name())) {
    KofMedia.MediaCall mediaCall = KofMedia.staticCall(((IdentifierExpr) mc.receiver()).name(), mc.methodName(), mc.arguments().size());
    if (mediaCall != null) {
        if (driver.target != Target.JVM && driver.target != Target.ANDROID) {
            String code = KofMedia.gapCode(mediaCall.function());
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        ((IdentifierExpr) mc.receiver()).name() + "." + mc.methodName() + ": not available on the "
                                + driver.target + " driver.target yet (" + code + ")",
                        code);
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                mediaCall.function(), mediaCall.parameterTypes(),
                mediaCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid2 && KofUi.isPalette(((IdentifierExpr) mc.receiver()).name())) {
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(((IdentifierExpr) mc.receiver()).name())) {
    KofUi.UiCall uiCall = KofUi.staticMethod(((IdentifierExpr) mc.receiver()).name(), mc.methodName(), mc.arguments().size());
    if (uiCall != null && "kof_ui_color_rgba".equals(uiCall.function())) {
        localIdx = driver.emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
        return localIdx;
    }
    if (uiCall != null && "kof_ui_theme_light".equals(uiCall.function())) {
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        return localIdx;
    }
    if (uiCall != null && "kof_ui_theme_dark".equals(uiCall.function())) {
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        return localIdx;
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr ridRt && KofUi.isRouterNamespace(((IdentifierExpr) mc.receiver()).name())) {
    // Fase 7 (docs/ui/architecture.md §2.9): Router.*
    KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
    if (routerCall != null) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofUi.COMPONENT, routerCall.function(), routerCall.parameterTypes(),
                routerCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
    }
    return -1;
    }
}
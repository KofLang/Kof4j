package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de factories Ui estáticos (Icon/Font/Button/Component/Store).
 */
final class ExpressionUiStaticLowerer {

    private ExpressionUiStaticLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
if (mc.receiver() == null && "Icon".equals(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    if (mc.arguments().size() == 2) {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_icon_new_size", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    } else {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_icon_new", List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    }
    return localIdx;
}
if (mc.receiver() == null && "Font".equals(mc.methodName())
        && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    if (mc.arguments().size() == 3) {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_font_new_bold", List.of(BuiltinTypes.STRING,
                        Type.PrimitiveType.INT, Type.PrimitiveType.BOOL),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    } else {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_font_new", List.of(BuiltinTypes.STRING, Type.PrimitiveType.INT),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    }
    return localIdx;
}
if (mc.receiver() == null && "Button".equals(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    if (mc.arguments().size() == 2) {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_button_new_action",
                List.of(BuiltinTypes.STRING, Type.UnknownType.UNKNOWN),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    } else {
        ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
                "kof_ui_button_new", List.of(BuiltinTypes.STRING),
                Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    }
    return localIdx;
}
if (mc.receiver() == null && "Component".equals(mc.methodName())
        && mc.arguments().size() == 1) {
    // Component Core (docs/ui/architecture.md): nó da árvore de
    // UI com estado reativo + view builder + lifecycle + effects.
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_component_new", List.of(Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Store".equals(mc.methodName())
        && mc.arguments().size() == 1) {
    // Fase 8 (docs/ui/architecture.md §2.6): estado compartilhado
    // observável entre componentes.
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_store_new", List.of(Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
        return -1;
    }
}
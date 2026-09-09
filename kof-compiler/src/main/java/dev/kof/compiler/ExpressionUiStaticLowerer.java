package dev.kof.compiler;

import java.util.List;

/**
 * Lowering de factories Ui estáticos (Icon/Font/Button/Component/Store).
 */
public final class ExpressionUiStaticLowerer {

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
if (mc.receiver() == null && "Canvas".equals(mc.methodName()) && mc.arguments().size() == 2) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_canvas_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && ("Window".equals(mc.methodName()) || "Label".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = "Window".equals(mc.methodName()) ? "kof_ui_window_new" : "kof_ui_label_new";
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_input_new", List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Textarea".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_textarea_new", List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Select".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_select_new", List.of(new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && ("Ul".equals(mc.methodName()) || "Ol".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "Ol".equals(mc.methodName()) ? "kof_ui_ol_new" : "kof_ui_ul_new",
            List.of(new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Table".equals(mc.methodName()) && mc.arguments().size() == 2) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_table_new",
            List.of(new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING)),
                    new Type.ClassType("kof", "List", List.of(new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING))))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName())
        || "Form".equals(mc.methodName()) || "Fieldset".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = "Column".equals(mc.methodName()) ? "kof_ui_column_new"
            : "Form".equals(mc.methodName()) ? "kof_ui_form_new"
            : "Fieldset".equals(mc.methodName()) ? "kof_ui_fieldset_new" : "kof_ui_row_new";
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Iframe".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_iframe_new", List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && ("Video".equals(mc.methodName()) || "Audio".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = "Video".equals(mc.methodName()) ? "kof_ui_video_new" : "kof_ui_audio_new";
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Hr".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_hr_new", List.of(),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_view_new", List.of(Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
// ── UI003 (restante): Fieldset/Iframe/Video/Audio/Hr — widgets DOM simples
if (mc.receiver() == null && "Fieldset".equals(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            mc.arguments().size() == 2 ? "kof_ui_fieldset_new_legend" : "kof_ui_fieldset_new",
            mc.arguments().size() == 2
                    ? List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT)), BuiltinTypes.STRING)
                    : List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && ("Iframe".equals(mc.methodName()) || "Video".equals(mc.methodName())
        || "Audio".equals(mc.methodName())) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = "Iframe".equals(mc.methodName()) ? "kof_ui_iframe_new"
            : "Video".equals(mc.methodName()) ? "kof_ui_video_new" : "kof_ui_audio_new";
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Hr".equals(mc.methodName()) && mc.arguments().size() == 0) {
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_hr_new", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
// ── Fase 4: primitivas de layout (docs/ui/architecture.md §2.8)
if (mc.receiver() == null && ("Box".equals(mc.methodName())
        || "Stack".equals(mc.methodName()) || "Wrap".equals(mc.methodName())
        || "Center".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = switch (mc.methodName()) {
        case "Box" -> "kof_ui_box_new";
        case "Stack" -> "kof_ui_stack_new";
        case "Wrap" -> "kof_ui_wrap_new";
        default -> "kof_ui_center_new";
    };
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Grid".equals(mc.methodName()) && mc.arguments().size() == 2) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_grid_new", List.of(Type.PrimitiveType.INT,
            new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Spacer".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_spacer_new", List.of(Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Align".equals(mc.methodName()) && mc.arguments().size() == 3) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_align_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
            new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_style_new", List.of(Type.PrimitiveType.INT, Type.PrimitiveType.INT,
            Type.PrimitiveType.INT, Type.PrimitiveType.INT),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
    }
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_link_new", List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_image_new", List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
        return -1;
    }
}
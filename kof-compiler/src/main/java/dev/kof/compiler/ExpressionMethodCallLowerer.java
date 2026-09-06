package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de MethodCallExpr (case do emitExpression).
 */
final class ExpressionMethodCallLowerer {

    private ExpressionMethodCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
// User-defined classes take precedence over builtin helpers
// with the same name: ClassName(args) is implicit construction.
SymbolTable.ClassSymbol userCtor = driver.semanticAnalyzer != null
        ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
if (mc.receiver() == null && userCtor != null) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    SymbolTable.ConstructorSymbol ctor = null;
    SymbolTable.Symbol ctorSym = userCtor.members().resolve("<init>");
    if (ctorSym instanceof SymbolTable.ConstructorSymbol ctorSingle) ctor = ctorSingle;
    List<Type> ctorParamTypes = (ctor != null
            && ctor.parameterTypes().size() == mc.arguments().size())
            ? ctor.parameterTypes() : null;
    if (ctorParamTypes == null && ctorSym instanceof SymbolTable.ConstructorSet set) {
        // resolve por assignability: arg pode ser subtipo do
        // formal (ex.: FixedClock onde TimeSource esperado)
        for (SymbolTable.ConstructorSymbol c : set.constructors()) {
            if (c.parameterTypes().size() != argTypes.size()) continue;
            boolean compatible = true;
            for (int ai = 0; ai < argTypes.size(); ai++) {
                Type formalP = c.parameterTypes().get(ai);
                Type argP = argTypes.get(ai);
                if (!(formalP.equals(argP) || Type.isUnknown(argP)
                        || (formalP instanceof Type.ClassType
                            && argP instanceof Type.ClassType))) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) { ctorParamTypes = c.parameterTypes(); break; }
        }
        if (ctorParamTypes == null) {
            for (SymbolTable.ConstructorSymbol c2 : set.constructors()) {
                if (c2.parameterTypes().size() == argTypes.size()) {
                    ctorParamTypes = c2.parameterTypes();
                    break;
                }
            }
        }
    }
    if (ctorParamTypes == null) ctorParamTypes = argTypes;
    ops.add(new KofNewObject(userCtor.type(), argTypes));
    ops.add(new KofDup());
    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
    ops.add(new KofCall(userCtor.type(), "<init>", ctorParamTypes,
            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
    return localIdx;
}
if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    ops.add(new KofCall(new Type.ClassType("kof", "time", List.of()), "kof_now",
            List.of(), Type.PrimitiveType.LONG, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "uiNodesLive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    // kof.ui probe (testes de leak): nº de nós vivos na árvore.
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_nodes_live", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "storesLive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_stores_live", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "emit".equals(mc.methodName()) && mc.arguments().size() == 2) {
    // Fase 5: dispara um evento num componente (bubbling).
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_emit", List.of(Type.PrimitiveType.INT, BuiltinTypes.STRING),
            Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_line",
            List.of(), new Type.NullableType(BuiltinTypes.STRING), KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())) {
    KofWeb.WebCall webCtx = KofWeb.contextCall(mc.methodName(), mc.arguments().size());
    if (webCtx != null) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofWeb.APP, webCtx.function(), webCtx.parameterTypes(),
                webCtx.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
}
if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
    if (!KofDb.supportedOn(driver.target)) {
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0,
                    0,
                    "transaction: not available on the " + driver.target
                            + " driver.target yet (" + KofDb.gapCode() + ")",
                    KofDb.gapCode());
        }
        return localIdx;
    }
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
            "kof_db_transaction", List.of(Type.UnknownType.UNKNOWN),
            Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_read_file",
            List.of(BuiltinTypes.STRING), new Type.NullableType(BuiltinTypes.STRING), KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
    ops.add(new KofCall(new Type.ClassType("kof", "io", List.of()), "kof_write_file",
            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    return localIdx;
}
if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 3) {
    localIdx = driver.emitPackedColor(mc.arguments(), ops, owner, localIdx, locals);
    return localIdx;
}
if (mc.receiver() == null && "Color".equals(mc.methodName()) && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
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
if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    String fn = "Column".equals(mc.methodName()) ? "kof_ui_column_new" : "kof_ui_row_new";
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            fn, List.of(new Type.ClassType("kof", "List", List.of(Type.PrimitiveType.INT))),
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
if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
    Type elemType = driver.listOfElementType(mc, locals);
    Type listType = new Type.ClassType("kof", "List", List.of(elemType));
    ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
    for (ExpressionNode arg : mc.arguments()) {
        ops.add(new KofDup());
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        ops.add(new KofCall(listType, "kof_list_add",
                List.of(ExpressionTyper.inferExprType(driver, arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    }
    return localIdx;
}
if (mc.receiver() == null && ("cancel".equals(mc.methodName())
        || "cancelled".equals(mc.methodName()) || "selectAny".equals(mc.methodName()))
        && driver.findLocalVar(mc.methodName(), locals) == null) {
    boolean argsOk = "cancelled".equals(mc.methodName())
            ? mc.arguments().isEmpty() : !mc.arguments().isEmpty();
    if (!argsOk) return localIdx;
    // Native: cancel/cancelled/selectAny sobre o handle pthread
    // (flags de cancel por TID + polling anyOf) — CONC001 fechado.
    // Android: reusa o caminho JVM (CompletableFuture + platform
    // threads no ART) — AND001 fechado 31/08.
    if ("selectAny".equals(mc.methodName())) {
        Type firstH = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        Type elemT = new Type.ClassType("kof.concurrent", "Handle",
                firstH instanceof Type.ClassType fh
                        && !fh.typeArguments().isEmpty()
                        ? List.of(fh.typeArguments().get(0)) : List.of());
        Type listT = new Type.ClassType("kof", "List", List.of(elemT));
        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT,
                KofCallKind.FUNCTION));
        for (ExpressionNode arg : mc.arguments()) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            ops.add(new KofCall(listT, "kof_list_add", List.of(elemT),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        }
        Type resT = ExpressionTyper.inferExprType(driver, mc, locals);
        ops.add(new KofCall(
                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                "kof_select_any", List.of(listT), resT, KofCallKind.FUNCTION));
        return localIdx;
    }
    String fn = "kof_" + mc.methodName();
    Type ret = Type.PrimitiveType.BOOL;
    if ("cancelled".equals(mc.methodName())) {
        ops.add(new KofCall(
                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                fn, List.of(), ret, KofCallKind.FUNCTION));
    } else {
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type h = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        ops.add(new KofCall(
                new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                fn, List.of(h), ret, KofCallKind.FUNCTION));
    }
    return localIdx;
}
if (mc.receiver() == null && ("poll".equals(mc.methodName()) || "done".equals(mc.methodName()))
        && mc.arguments().size() == 1
        && driver.findLocalVar(mc.methodName(), locals) == null) {
    // Native: done/poll são leituras não-bloqueantes do flag do
    // handle (pthread já existe via spawn) — CONC001 fechado p/ estes.
    // Android: reusa o caminho JVM (Future.isDone/getNow).
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type hE = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    Type rE = Type.UnknownType.UNKNOWN;
    if (hE instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
            && "poll".equals(mc.methodName())) {
        rE = ct.typeArguments().get(0);
    }
    Type ret = "poll".equals(mc.methodName()) ? rE : Type.PrimitiveType.BOOL;
    ops.add(new KofCall(
            new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
            "kof_" + mc.methodName(),
            List.of(hE), ret, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
        && mc.arguments().size() == 2
        && driver.findLocalVar("awaitTimeout", locals) == null) {
    // awaitTimeout(r, timeoutMs): valor se a task terminar no prazo;
    // senão lança exceção (capturável via try/catch). G8/CONC residual.
    // Android: Future.get(timeout) existe no ART — AND001 fechado.
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type hT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    Type resT = Type.UnknownType.UNKNOWN;
    if (hT instanceof Type.ClassType ct && "kof.concurrent".equals(ct.packageName())
            && !ct.typeArguments().isEmpty()) {
        resT = ct.typeArguments().get(0);
    }
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(1), ops, owner, localIdx, locals);
    ops.add(new KofCall(
            new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
            "kof_await_timeout", List.of(hT, Type.PrimitiveType.INT),
            resT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "channel".equals(mc.methodName())
        && mc.arguments().isEmpty()
        && driver.findLocalVar("channel", locals) == null) {
    // Canais tipados (concorrência): channel<T>() -> Channel<T>
    // FIFO thread-safe; c.send(v) enfileira, c.receive() retira.
    Type elemT = mc.typeArguments().isEmpty()
            ? Type.UnknownType.UNKNOWN
            : CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
    Type chanT = new Type.ClassType("kof.concurrent", "Channel", List.of(elemT));
    ops.add(new KofCall(chanT, "kof_channel_new", List.of(),
            chanT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
    if (driver.target.isNative()) {
        // CONC001: spawn-expr com handle real (pthread)
        ExpressionNode bodyN = mc.arguments().get(0);
        Type resultTN = ExpressionTyper.inferExprType(driver, bodyN, locals);
        Type handleTN = new Type.ClassType("kof.concurrent", "Handle", List.of(resultTN));
        LambdaExpr leN2 = bodyN instanceof LambdaExpr l2 ? l2
                : new LambdaExpr(bodyN.position() != null ? bodyN.position() : mc.position(),
                        List.of(), List.of(new ExpressionStmt(
                                bodyN.position() != null ? bodyN.position() : mc.position(), bodyN)));
        Type.FunctionType ftN2 = new Type.FunctionType(List.of(), resultTN, null);
        String lambdaClassN2 = driver.lambdaClass(leN2, ftN2, List.of(), true);
        Type taskTypeN2 = new Type.ClassType("", lambdaClassN2, List.of());
        ops.add(new KofNewObject(taskTypeN2, List.of()));
        ops.add(new KofDup());
        ops.add(new KofCall(taskTypeN2, "<init>", List.of(),
                Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                "kof_spawn_result", List.of(taskTypeN2), handleTN, KofCallKind.FUNCTION));
        return localIdx;
    }
    ExpressionNode body = mc.arguments().get(0);
    Type resultT = ExpressionTyper.inferExprType(driver, body, locals);
    Type handleT = new Type.ClassType("kof.concurrent", "Handle", List.of(resultT));
    LambdaExpr le = body instanceof LambdaExpr l0 ? l0
            : new LambdaExpr(body.position() != null ? body.position() : mc.position(),
                    List.of(), List.of(new ExpressionStmt(
                            body.position() != null ? body.position() : mc.position(), body)));
    Type.FunctionType ft = new Type.FunctionType(List.of(), resultT, null);
    String lambdaClass = driver.lambdaClass(le, ft, List.of(), true);
    Type taskType = new Type.ClassType("", lambdaClass, List.of());
    ops.add(new KofNewObject(taskType, List.of()));
    ops.add(new KofDup());
    ops.add(new KofCall(taskType, "<init>", List.of(),
            Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
            "kof_spawn_result", List.of(taskType), handleT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type hT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    Type resT = Type.UnknownType.UNKNOWN;
    if (hT instanceof Type.ClassType ct
            && !ct.typeArguments().isEmpty()) resT = ct.typeArguments().get(0);
    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
            "kof_await", List.of(hT), resT, KofCallKind.FUNCTION));
    return localIdx;
}
if (mc.receiver() instanceof IdentifierExpr rid && CompilerTypes.isEnumName(rid.name(), driver.currentUnit)
        && !driver.isLocalVarName(rid.name(), locals)) {
    Type enumT = new Type.ClassType("", rid.name(), List.of());
    // lista interna com elemento STRING (runtime do enum é o nome);
    // a tipagem List<Color> fica na checagem de tipos
    Type stringListT = new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        ops.add(new KofCall(stringListT,
                "kof_list_new", List.of(), stringListT,
                KofCallKind.FUNCTION));
        for (String c : CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit)) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
            ops.add(new KofCall(stringListT,
                    "kof_list_add", List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        return localIdx;
    }
    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
        Type listT = stringListT;
        ops.add(new KofCall(listT, "kof_list_new", List.of(), listT,
                KofCallKind.FUNCTION));
        for (String c : CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit)) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, c));
            ops.add(new KofCall(listT, "kof_list_add", List.of(enumT),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        ops.add(new KofCall(enumT, "kof_enum_value_of",
                List.of(listT, BuiltinTypes.STRING), enumT, KofCallKind.FUNCTION));
        return localIdx;
    }
    return localIdx;
}
if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {

    Type keyType = Type.UnknownType.UNKNOWN;
    Type valueType = Type.UnknownType.UNKNOWN;
    if (!mc.arguments().isEmpty()) {
        // mapOf(k1, v1, k2, v2, ...): pinning do tipo no primeiro par
        keyType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        if (mc.arguments().size() > 1) {
            valueType = ExpressionTyper.inferExprType(driver, mc.arguments().get(1), locals);
        }
    }
    Type mapType = new Type.ClassType("kof", "Map", List.of(keyType, valueType));
    ops.add(new KofCall(mapType, "kof_map_new", List.of(), mapType, KofCallKind.FUNCTION));
    // pares: (k0,v0), (k1,v1), ...
    for (int ai = 0; ai + 1 < mc.arguments().size(); ai += 2) {
        ops.add(new KofDup());
        Type kType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
        Type vType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai + 1), locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai + 1), ops, owner, localIdx, locals);
        // VOID no put: o map duplicado continua na pilha para o próximo par
        ops.add(new KofCall(mapType, "kof_map_put", List.of(kType, vType),
                Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    }
    return localIdx;
}
if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
    Type elemType = Type.UnknownType.UNKNOWN;
    if (!mc.arguments().isEmpty()) elemType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    Type setType = new Type.ClassType("kof", "Set", List.of(elemType));
    ops.add(new KofCall(setType, "kof_set_new", List.of(), setType, KofCallKind.FUNCTION));
    for (ExpressionNode arg : mc.arguments()) {
        ops.add(new KofDup());
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        // VOID na construção: o backend descarta o bool e o set
        // duplicado continua na pilha para o próximo append
        ops.add(new KofCall(setType, "kof_set_add",
                List.of(ExpressionTyper.inferExprType(driver, arg, locals)), Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    }
    return localIdx;
}
if (("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
    Type printedType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (Type.isVoid(printedType)) {
        // void não é um valor: println(f()) com f void empilhava
        // nada e o backend dava pop de lixo (segfault Native /
        // VerifyError JVM). Diagnóstico limpo em vez disso.
        if (driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                    mc.position() != null ? mc.position().line() : 0,
                    mc.position() != null ? mc.position().column() : 0, 0,
                    mc.methodName() + "(...) recebeu um valor void — a chamada não"
                            + " retorna valor (adicione 'return' ou não a use como argumento)",
                    "SEM033");
        }
        return localIdx;
    }
    if (!driver.fpSupportedOnNative(printedType, mc.position())) {
        return localIdx;
    }
    ops.add(new KofGetStatic(
            new Type.ClassType("java.lang", "System", List.of()),
            "out", new Type.ClassType("java.io", "PrintStream", List.of())));
    localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
    Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (TypeMetrics.isPrimitiveType(argType)) {
        if (driver.target.isNative()) {
            // println(char) é NUMÉRICO (congelado: strings.md
            // "72 (H)" + execStringCharAt). valueOf(char) solto
            // é o caractere UTF-8 (common-mistakes.md "h").
            // O dispatch nativo do valueOf decide pelo tipo do
            // parâmetro — aqui mapeia char→Int para imprimir o
            // codepoint sem quebrar String.valueOf(char).
            Type nativeArg = (argType instanceof Type.PrimitiveType p
                    && "char".equals(p.name()))
                    ? Type.PrimitiveType.INT : argType;
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(nativeArg),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        } else {
            TypeEmitter.boxPrimitive(ops, argType);
            ops.add(new KofCall(
                    BuiltinTypes.STRING,
                    "valueOf", List.of(Type.UnknownType.UNKNOWN),
                    BuiltinTypes.STRING, KofCallKind.STATIC));
        }
    } else {
        // o tipo REAL do arg só vai para o valueOf NATIVO (para
        // despachar toString de records). JVM/JS usam Object
        // (String.valueOf(Object) chama toString; valueOf de um
        // ClassType específico não existe no JVM).
        ops.add(new KofCall(
                BuiltinTypes.STRING,
                "valueOf", List.of(driver.target.isNative()
                        && !Type.isString(argType) ? argType
                        : Type.UnknownType.UNKNOWN),
                BuiltinTypes.STRING, KofCallKind.STATIC));
    }
    ops.add(new KofCall(
            new Type.ClassType("java.io", "PrintStream", List.of()),
            mc.methodName(), List.of(BuiltinTypes.STRING),
            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && driver.semanticAnalyzer != null
        && driver.semanticAnalyzer.getClass(rid.name()) != null) {
    // Metodo ESTATICO de classe KOF de outro pacote:
    // Desconto.aplicar(c) -> invokestatic vendas/regras/Desconto.aplicar
    SymbolTable.MethodSymbol ksm = null;
    SymbolTable.Symbol ks = driver.semanticAnalyzer.resolveInHierarchy(rid.name(), mc.methodName());
    if (ks instanceof SymbolTable.MethodSymbol ms0
            && ms0.parameterTypes().size() == mc.arguments().size()) {
        ksm = ms0;
    }
    if (ksm != null) {
        SymbolTable.ClassSymbol kt = driver.semanticAnalyzer.getClass(rid.name());
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ksm.parameterTypes(),
                ops, owner, localIdx, locals);
        ops.add(new KofCall(kt.type(), mc.methodName(), ksm.parameterTypes(),
                ksm.returnType(), KofCallKind.STATIC));
        return localIdx;
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && CompilerTypes.qualifyViaImports(rid.name(), driver.currentUnit) instanceof Type.ClassType extQ
        && !extQ.packageName().isEmpty()
        && driver.externalClasspath != null
        && driver.externalClasspath.knows(extQ.internalName())
        && driver.externalClasspath.resolveMethod(extQ.internalName(), mc.methodName(),
                mc.arguments().size()) != null) {
    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
    // estático, interface externa ou instância — resolve pelo
    // classpath ANTES dos namespaces builtin (Button também é
    // widget do kof.ui; o import decide). Local sombreia.
    ExternalClasspath.MethodSignature extSig = driver.externalClasspath.resolveMethod(
            extQ.internalName(), mc.methodName(), mc.arguments().size());
    List<Type> extFormal = new ArrayList<>();
    for (String d : extSig.parameterDescriptors()) {
        extFormal.add(ExternalClasspath.typeFromDescriptor(d));
    }
    Type extRet = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), extFormal,
            ops, owner, localIdx, locals);
    KofCallKind extKind = extSig.isStatic() ? KofCallKind.STATIC
            : (extSig.ownerIsInterface() ? KofCallKind.INTERFACE
            : KofCallKind.INSTANCE);
    ops.add(new KofCall(extQ, mc.methodName(), extFormal, extRet, extKind));
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && "json".equals(rid.name())) {
    if ("encode".equals(mc.methodName()) && mc.arguments().size() == 1) {
        Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        if (!driver.jsonSupported(argType, false)) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        List<Type> paramTypes = List.of(argType);
        if (BuiltinTypes.isList(argType)) {
            int tag = JsonDispatch.listTag(driver.listElementType(argType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
            paramTypes = List.of(argType, Type.PrimitiveType.INT);
        } else if (driver.target.isNative()
                && argType instanceof Type.ClassType ect
                && !BuiltinTypes.isString(argType)
                // List/Map têm caminho builtin próprio
                && !BuiltinTypes.isList(argType) && !BuiltinTypes.isMap(argType)) {
            // JSN002: compoe o JSON em compile-time a partir
            // dos campos conhecidos (sem reflection, sem
            // walker generico) — so primitivas testadas.
            String cn2 = ect.packageName().isEmpty()
                    ? ect.name() : ect.packageName() + "." + ect.name();
            java.util.List<String[]> flds = driver.classFieldsOrdered(cn2);
            // guarda o objeto em local temporario
            ops.add(new KofStoreLocal(argType, localIdx));
            locals.add(new IRLocalVariable(localIdx, "#jsonobj", argType));
            int objTmp = localIdx;
            localIdx += TypeMetrics.isDoubleWidth(argType) ? 2 : 1;
            // acc = "{"
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "{"));
            for (int fi = 0; fi < flds.size(); fi++) {
                String fname = flds.get(fi)[0];
                Type ftype = CompilerTypes.toType(flds.get(fi)[1], driver.currentUnit);
                if (fi > 0) {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ","));
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                }
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                        "\"" + fname + "\":"));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                // valor do campo
                ops.add(new KofLoadLocal(argType, objTmp));
                ops.add(new KofLoadField(argType, fname, ftype));
                switch (ftype instanceof Type.PrimitiveType fp
                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                    case "long":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_long_to_string",
                                List.of(Type.PrimitiveType.LONG), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "bool":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_bool_to_string",
                                List.of(Type.PrimitiveType.BOOL), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "int":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_int_to_string",
                                List.of(Type.PrimitiveType.INT), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "double":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_double_to_string",
                                List.of(Type.PrimitiveType.DOUBLE), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "float":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_float_to_string",
                                List.of(Type.PrimitiveType.FLOAT), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    default: // string
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_quote",
                                List.of(BuiltinTypes.STRING), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                }
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "}"));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        ops.add(new KofCall(argType, JsonDispatch.encodeFunction(argType), paramTypes,
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
    } else if ("decode".equals(mc.methodName()) && mc.arguments().size() == 1
            && !mc.typeArguments().isEmpty()) {
        Type targetType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        if (!driver.jsonSupported(targetType, true)) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        String decodeFn = JsonDispatch.decodeFunction(targetType, driver.listElementType(targetType));
        List<Type> decodeParams = List.of(BuiltinTypes.STRING);
        if (BuiltinTypes.isList(targetType)
                && driver.listElementType(targetType) instanceof Type.ClassType ect
                && !BuiltinTypes.isString(ect)) {
            // decode<List<T>> where T is a user class: bind
            // each element to T (the element type survives the
            // generic erasure through the type system).
            decodeFn = "kof_json_decode_object_list";
            decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
            String className = ect.packageName().isEmpty()
                    ? ect.name() : ect.packageName() + "." + ect.name();
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, className));
        } else if (driver.target.isNative()
                && targetType instanceof Type.ClassType dct
                && !BuiltinTypes.isString(targetType)
                // List/Map têm caminho builtin próprio
                && !BuiltinTypes.isList(targetType) && !BuiltinTypes.isMap(targetType)) {
            // JSN002: decode composto — find_value por campo +
            // decoders escalares + construtor canonico
            String cn3 = dct.packageName().isEmpty()
                    ? dct.name() : dct.packageName() + "." + dct.name();
            java.util.List<String[]> flds = driver.classFieldsOrdered(cn3);
            // json em local temporario
            ops.add(new KofStoreLocal(BuiltinTypes.STRING, localIdx));
            locals.add(new IRLocalVariable(localIdx, "#jsonsrc", BuiltinTypes.STRING));
            int jTmp = localIdx;
            localIdx += 1;
            List<Type> ctorTypes = new ArrayList<>();
            ops.add(new KofNewObject(targetType,
                    flds.stream().map(f -> CompilerTypes.toType(f[1], driver.currentUnit)).toList()));
            ops.add(new KofDup());
            for (String[] f : flds) {
                Type ft = CompilerTypes.toType(f[1], driver.currentUnit);
                ctorTypes.add(ft);
                ops.add(new KofLoadLocal(BuiltinTypes.STRING, jTmp));
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f[0]));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_find_value",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                String dec = switch (ft instanceof Type.PrimitiveType fp
                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                    case "int", "char", "byte", "short" -> "kof_json_decode_int";
                    case "long" -> "kof_json_decode_long";
                    case "bool" -> "kof_json_decode_bool";
                    default -> "kof_json_decode_string";
                };
                ops.add(new KofCall(targetType, dec,
                        List.of(BuiltinTypes.STRING), ft, KofCallKind.FUNCTION));
            }
            ops.add(new KofCall(targetType, "<init>", ctorTypes,
                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            return localIdx;
        }
        ops.add(new KofCall(targetType, decodeFn, decodeParams,
                targetType, KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
    if (dbCall != null) {
        if (!KofDb.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofDb.gapCode() + ")",
                        KofDb.gapCode());
            }
            return localIdx;
        }
        for (int i = 0; i < mc.arguments().size() && i < 2; i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
        }
        for (int i = 2; i < mc.arguments().size(); i++) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            TypeEmitter.boxPrimitive(ops, argTypes.get(i));
        }
        if (KofDb.isQuery(mc.methodName())) {
            if (typed && !mc.typeArguments().isEmpty()) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.typeArguments().get(0)));
            } else {
                ops.add(new KofLoadLiteral(Type.UnknownType.UNKNOWN, null));
            }
        }
        List<Type> params = new ArrayList<>(dbCall.parameterTypes());
        Type retType = dbCall.returnType();
        if (KofDb.isQuery(mc.methodName())) {
            // o className (ou null) é sempre empurrado; o
            // param precisa estar na lista para o native
            // popar na ordem certa
            params.add(BuiltinTypes.STRING);
            if (typed) {
                retType = new Type.ClassType("kof", "List",
                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
            }
        }
        ops.add(new KofCall(new Type.ClassType("kof.db", "Db", List.of()),
                dbCall.function(), params, retType, KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofOrm.isOrmNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = !mc.typeArguments().isEmpty();
    String entityName = typed ? mc.typeArguments().get(0) : null;
    if (entityName == null && "save".equals(mc.methodName()) && !argTypes.isEmpty()) {
        Type objType = argTypes.get(argTypes.size() - 1);
        if (objType instanceof Type.ClassType ct) entityName = ct.name();
    }
    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
    if (ormCall != null) {
        if (!KofOrm.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofOrm.gapCode() + ")",
                        KofOrm.gapCode());
            }
            return localIdx;
        }
        List<EntityFieldNode> fields = entityName == null ? null : driver.entitySchemas.get(entityName);
        boolean needsEntity = !"migrate".equals(mc.methodName());
        if (needsEntity && fields == null) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "orm." + mc.methodName() + ": unknown entity '"
                                + (entityName == null ? "?" : entityName) + "' (ORM002)",
                        "ORM002");
            }
            return localIdx;
        }
        // P3-10: validação tipada do campo em where/count/where_op —
        // a coluna tem que ser um campo real da entidade (ORM003)
        driver.validateOrmField(mc, entityName, fields);
        // args do usuário: (db[, obj|id]) — primitivos são
        // boxed (o runtime espera Object para obj/id)
        for (int ai = 0; ai < mc.arguments().size(); ai++) {
            ExpressionNode arg = mc.arguments().get(ai);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (ai > 0 && TypeMetrics.isPrimitiveType(ExpressionTyper.inferExprType(driver, arg, locals))) {
                TypeEmitter.boxPrimitive(ops, ExpressionTyper.inferExprType(driver, arg, locals));
            }
        }
        // literais do schema (conhecidos em compile-time):
        // table, schema, [className]
        boolean isMigrate = "migrate".equals(mc.methodName());
        String table = entityName == null ? "" : KofOrm.tableName(entityName);
        String schema = entityName == null ? "" : KofOrm.schemaString(fields);
        boolean needsClassName = "find".equals(mc.methodName())
                || "all".equals(mc.methodName())
                || "where".equals(mc.methodName())
                || "page".equals(mc.methodName());
        List<Type> params = new ArrayList<>(ormCall.parameterTypes());
        if (!isMigrate) {
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
            params.add(BuiltinTypes.STRING); // table
            params.add(BuiltinTypes.STRING); // schema
        }
        if (needsClassName) {
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, CompilerTypes.classNameFor(entityName)));
            params.add(BuiltinTypes.STRING); // className
        }
        Type retType = ormCall.returnType();
        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
            retType = argTypes.get(argTypes.size() - 1);
        } else if (typed) {
            if ("all".equals(mc.methodName()) || "page".equals(mc.methodName())
                    || "where".equals(mc.methodName())) {
                retType = new Type.ClassType("kof", "List",
                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
            } else if ("find".equals(mc.methodName())) {
                retType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
            }
        }
        ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                ormCall.function(), params, retType, KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofLog.isLogNamespace(rid.name())) {
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
                        rid.name() + "." + mc.methodName()
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
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
    if (procCall != null && "kof_process_spawn".equals(procCall.function())) {
        if (driver.target.isNative()) {
            // F10: pipes vivos no native exigem fork/exec com
            // descriptors no runtime asm — gap explícito por ora
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "process.spawn: interactive stdin/stdout not supported on the Native driver.target yet (JVM/JS support it)",
                        "PROC001");
            }
            return localIdx;
        }
        // F10: process.spawn(program, args...) → monta List<String>
        // e chama kof_process_spawn (stdin/stdout vivos)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.HANDLE, "kof_process_spawn",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.HANDLE, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (procCall != null) {
        if (driver.target.isNative()) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "process.run: not supported on the Native driver.target yet (JVM supports it)",
                        "PROC001");
            }
            return localIdx;
        }
        // process.run(program, args...) →
        // kof_process_run(program, List<String>)
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listType = KofProcess.STRING_LIST;
        ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
        for (int i = 1; i < mc.arguments().size(); i++) {
            ops.add(new KofDup());
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(i), ops, owner, localIdx, locals);
            ops.add(new KofCall(listType, "kof_list_add",
                    List.of(BuiltinTypes.STRING), Type.PrimitiveType.VOID,
                    KofCallKind.INSTANCE));
        }
        ops.add(new KofCall(KofProcess.RESULT, "kof_process_run",
                List.of(BuiltinTypes.STRING, KofProcess.STRING_LIST),
                KofProcess.RESULT, KofCallKind.FUNCTION));
    } else {
        // process.exit(code) — todos os targets
        KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
        if (exitCall != null) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(new Type.ClassType("kof.process", "Process", List.of()),
                    exitCall.function(), exitCall.parameterTypes(), exitCall.returnType(),
                    KofCallKind.FUNCTION));
        }
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofHttp.isHttpNamespace(rid.name())) {
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
                        rid.name() + "." + mc.methodName()
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
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofTime.isTimeNamespace(rid.name())) {
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
                        rid.name() + "." + mc.methodName()
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
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofScheduler.isSchedulerNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (schedCall != null) {
        if (!KofScheduler.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (SCHED001)",
                        "SCHED001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                schedCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (schedCall != null) {
        if (!KofScheduler.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (SCHED001)",
                        "SCHED001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                schedCall.returnType(), KofCallKind.FUNCTION));
        return localIdx;
    }
    // fall through to normal handling if not matched
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofMq.isMqNamespace(rid.name())) {
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
                        rid.name() + "." + mc.methodName()
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
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofConfig.isConfigNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
    if (cfgCall != null) {
        if (!KofConfig.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (CONF001)",
                        "CONF001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        driver.recordConfigKey(mc);
        ops.add(new KofCall(KofConfig.CONFIG, cfgCall.function(), cfgCall.parameterTypes(),
                cfgCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofCache.isCacheNamespace(rid.name())) {
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
                        rid.name() + "." + mc.methodName()
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
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofGpu.isGpuNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    if (System.getProperty("kof.trace") != null) {
        System.err.println("GPU call " + mc.methodName() + " argTypes=" + argTypes);
    }
    // Unknown (var sem tipo inferido no lowering) casa com
    // qualquer array: o staticCall exige tipos concretos, mas
    // o `var a = new Long[4]` pode chegar como Unknown quando
    // o local foi registrado antes do NewArray. Substitui
    // Unknown por Long[]/Int[] conforme o nome do método.
    List<Type> candidate = new ArrayList<>();
    boolean hasUnknown = false;
    for (Type t : argTypes) {
        if (t instanceof Type.UnknownType) { hasUnknown = true; break; }
    }
    if (hasUnknown) {
        Type arrType = "dispatchMatmul64".equals(mc.methodName())
                ? new Type.ArrayType(Type.PrimitiveType.LONG)
                : new Type.ArrayType(Type.PrimitiveType.INT);
        for (Type t : argTypes) {
            candidate.add(t instanceof Type.UnknownType ? arrType : t);
        }
        argTypes = candidate;
    }
    KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
    if (gpuCall != null) {
        if (!KofGpu.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (GPU001)",
                        "GPU001");
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(KofGpu.GPU, gpuCall.function(), gpuCall.parameterTypes(),
                gpuCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofSecurity.isSecurityNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (secCall != null) {
        if (!KofSecurity.supportedOn(secCall.function(), driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofSecurity.gapCode(secCall.function()) + ")",
                        KofSecurity.gapCode(secCall.function()));
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.security", "Security", List.of()),
                secCall.function(), secCall.parameterTypes(), secCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofValidation.isValidationNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (vCall != null) {
        if (!KofValidation.supportedOn(vCall.function(), driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofValidation.gapCode(vCall.function()) + ")",
                        KofValidation.gapCode(vCall.function()));
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.validation", "Validation", List.of()),
                vCall.function(), vCall.parameterTypes(), vCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofObservability.isObservabilityNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (oCall != null) {
        if (!KofObservability.supportedOn(oCall.function(), driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofObservability.gapCode(oCall.function()) + ")",
                        KofObservability.gapCode(oCall.function()));
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.observability", "Observability", List.of()),
                oCall.function(), oCall.parameterTypes(), oCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofTetris.isTetrisNamespace(rid.name())) {
    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
            mc.arguments().size());
    if (tetrisCall != null) {
        if (!KofTetris.supportedOn(driver.target)) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName()
                                + ": not available on the " + driver.target
                                + " driver.target yet (" + KofTetris.gapCode() + ")",
                        KofTetris.gapCode());
            }
            return localIdx;
        }
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.tetris", "Tetris", List.of()),
                tetrisCall.function(), tetrisCall.parameterTypes(), tetrisCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofWeb.isWebNamespace(rid.name())) {
    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        if (driver.target != Target.JVM && driver.target != Target.ANDROID
                && driver.target != Target.NATIVE
                && driver.target != Target.NATIVE_RISCV64
                && driver.target != Target.NATIVE_AARCH64) {
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        "web: not available on the " + driver.target
                                + " driver.target yet (WEB001)",
                        "WEB001");
            }
            return localIdx;
        }
        KofWeb.WebCall appCall = KofWeb.appConstructor();
        ops.add(new KofCall(KofWeb.APP, appCall.function(), appCall.parameterTypes(),
                appCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())) {
    KofIo.IoCall ioCall = KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size());
    if (ioCall != null) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                ioCall.function(), ioCall.parameterTypes(), ioCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && KofMedia.isStaticNamespace(rid.name())) {
    KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(), mc.arguments().size());
    if (mediaCall != null) {
        if (driver.target != Target.JVM && driver.target != Target.ANDROID) {
            String code = KofMedia.gapCode(mediaCall.function());
            if (driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0,
                        0,
                        rid.name() + "." + mc.methodName() + ": not available on the "
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
} else if (mc.receiver() instanceof IdentifierExpr rid2 && KofUi.isPalette(rid2.name())) {
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
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
} else if (mc.receiver() instanceof IdentifierExpr ridRt && KofUi.isRouterNamespace(ridRt.name())) {
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
} else if (mc.receiver() != null) {
    if (mc.receiver() instanceof IdentifierExpr sid && "super".equals(sid.name())
            && !owner.isEmpty()) {
        // super.method(args): non-virtual call to the
        // superclass implementation — lowered to
        // INVOKESPECIAL on the direct superclass (JVM).
        if (driver.target.isNative() && driver.currentDiagnostics != null) {
            SourcePosition p = mc.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "super." + mc.methodName()
                            + "() is not supported on the native driver.target yet (SUP001)",
                    "SUP001");
            return localIdx;
        }
        // super.metodo() só faz sentido no corpo de um método
        // de classe; dentro de lambda sintética usa o driver
        // externo capturado ($outer) — sem ele, gap honesto
        String effectiveOwner = owner;
        String ownerSimple0 = owner.substring(owner.lastIndexOf('/') + 1);
        if (driver.semanticAnalyzer == null || driver.semanticAnalyzer.getClass(ownerSimple0) == null) {
            String enc = driver.lambdaEnclosingOwner.get(owner);
            if (enc == null) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "super." + mc.methodName()
                                    + "() is only valid inside class methods (SUP002)",
                            "SUP002");
                }
                return localIdx;
            }
            effectiveOwner = enc;
        }
        String superInternal = HierarchyResolver.findSuperClass(effectiveOwner, driver.semanticAnalyzer);
        if (superInternal == null) superInternal = "java/lang/Object";
        // nomes declarados com pontos (android.view.View)
        // viram nome interno JVM para resolução e emissão
        superInternal = superInternal.replace('.', '/');
        Type superType = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
        SymbolTable.MethodSymbol superMethod = null;
        if (driver.semanticAnalyzer != null) {
            String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
            SymbolTable.Symbol s = driver.semanticAnalyzer.resolveInHierarchy(superSimple, mc.methodName());
            if (s instanceof SymbolTable.MethodSymbol ms) superMethod = ms;
        }
        List<Type> paramTypes;
        Type returnType;
        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
        ExternalClasspath.MethodSignature extSig = null;
        if (superMethod == null && osig == null && driver.externalClasspath != null) {
            extSig = driver.externalClasspath.resolveMethod(superInternal, mc.methodName(),
                    mc.arguments().size());
        }
        if (superMethod == null && osig == null && extSig == null
                && HierarchyResolver.hierarchyFullyKnown(superInternal, driver.semanticAnalyzer) && driver.currentDiagnostics != null) {
            // hierarquia inteiramente conhecida e o método não
            // existe — erro em compile-time, não NoSuchMethodError
            SourcePosition p = mc.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "method '" + mc.methodName() + "' does not exist in superclass '"
                            + HierarchyResolver.superSimpleName(superInternal) + "'",
                    "SEM016");
            return localIdx;
        }
        if (superMethod != null
                && superMethod.parameterTypes().size() == mc.arguments().size()) {
            paramTypes = superMethod.parameterTypes();
            returnType = superMethod.returnType();
        } else if (osig != null) {
            paramTypes = osig.parameterTypes();
            returnType = osig.returnType();
        } else if (extSig != null) {
            // assinatura real lida do classpath externo — o
            // descritor emitido casa com a classe externa
            List<Type> formal = new ArrayList<>();
            for (String d : extSig.parameterDescriptors()) {
                formal.add(ExternalClasspath.typeFromDescriptor(d));
            }
            paramTypes = formal;
            returnType = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
        } else {
            paramTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) paramTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            returnType = ExpressionTyper.inferExprType(driver, mc, locals);
        }
        // receiver: dentro de lambda sintética é o $outer e a
        // chamada vira uma PONTE kof_super$metodo na classe dona
        IRLocalVariable outerVar = driver.findLocalVar("$outer", locals);
        if (outerVar != null) {
            ops.add(new KofLoadLocal(outerVar.type(), outerVar.index()));
            String bridgeName = driver.ensureSuperBridge(effectiveOwner, superInternal,
                    mc.methodName(), paramTypes, returnType);
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes,
                    ops, effectiveOwner, localIdx, locals);
            ops.add(new KofCall(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), bridgeName,
                    paramTypes, returnType, KofCallKind.INSTANCE));
            return localIdx;
        } else {
            ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), 0));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes, ops, owner, localIdx, locals);
            ops.add(new KofCall(superType, mc.methodName(), paramTypes, returnType, KofCallKind.SUPER));
            return localIdx;
        }
    }
    localIdx = ExpressionLowerer.emitExpression(driver, mc.receiver(), ops, owner, localIdx, locals);
    Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    // narrowing de null-safety (`if (x != null) { x.substring(...) }`):
    // dispatch pelo inner — antes emitia `"".substring` (owner "" inválido)
    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
    if (KofUi.isUiType(recvType)) {
        localIdx = driver.emitUiInstance(recvType, mc, ops, owner, localIdx, locals);
        return localIdx;
    }
    if (CompilerTypes.isEnumType(recvType, driver.currentUnit) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        // o valor do enum JÁ é o nome (String em runtime): identidade
        return localIdx;
    }
    if (KofWeb.isAppType(recvType)) {
        List<Type> webArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
        if (webCall != null) {
            boolean nativeWebT1 = (driver.target == Target.NATIVE
                    || driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64)
                    && (webCall.function().equals("kof_web_listen")
                        || webCall.function().equals("kof_web_route"));
            if (driver.target != Target.JVM && driver.target != Target.ANDROID && !nativeWebT1) {
                String webCode = KofWeb.gapCode(webCall.function());
                String webMsg = switch (webCode) {
                    case "WEB002" -> "web TLS: not available on the " + driver.target
                            + " driver.target yet (WEB002)";
                    case "WEB003" -> "web SSE: not available on the " + driver.target
                            + " driver.target yet (WEB003)";
                    case "WEB004" -> "web WebSocket: not available on the " + driver.target
                            + " driver.target yet (WEB004)";
                    default -> "web: not available on the " + driver.target
                            + " driver.target yet (WEB001)";
                };
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0, webMsg, webCode);
                }
                return localIdx;
            }
            List<Type> webParams = new ArrayList<>();
            webParams.add(BuiltinTypes.STRING);
            if (KofWeb.isRouteMethod(mc.methodName()) && !"ws".equals(mc.methodName())) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                webParams.add(BuiltinTypes.STRING);
            }
            for (ExpressionNode arg : mc.arguments()) {
                webParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                    webCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }
    if (KofMedia.isHandleType(recvType)) {
        KofMedia.MediaCall mediaCall =
                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
        if (mediaCall != null) {
            List<Type> mediaParams = new ArrayList<>();
            mediaParams.add(Type.PrimitiveType.INT);      // handle (receiver)
            for (ExpressionNode arg : mc.arguments()) {
                mediaParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    mediaCall.function(), mediaParams,
                    mediaCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }
    if (KofIo.isIoType(recvType)) {
        if (KofIo.isIdentityMethod(mc.methodName())) {
            return localIdx;
        }
        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (ioCall != null) {
            // receiver File/Path/Directory é apagado pra String
            // path em runtime (empilhado acima); os METHOD args
            // alinham com ioCall.parameterTypes() — a conversão
            // formal (int literal → long slot no readRange)
            // evita o frame bug I/J no visitMaxs
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ioCall.parameterTypes(),
                    ops, owner, localIdx, locals);
            List<Type> ioParams = new ArrayList<>();
            ioParams.add(BuiltinTypes.STRING);
            ioParams.addAll(ioCall.parameterTypes());
            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    if (recvType instanceof Type.FunctionType ft) {
        if (ft.className() == null) {
            // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
            // (s: (Int) -> Int), sem classe sintética). Todas as
            // lambdas da assinatura implementam a interface
            // sintética — invoca via INVOKEINTERFACE.
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            Type iface = driver.lambdaInterfaceType(ft);
            ops.add(new KofCall(iface, "invoke", argTypes, ft.returnType(), KofCallKind.INTERFACE));
            return localIdx;
        }
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        // f.invoke(): o owner precisa ser a classe sintética
        // da lambda — FunctionType não tem nome JVM
        Type invokeOwner = new Type.ClassType("", ft.className(), List.of());
        ops.add(new KofCall(invokeOwner, "invoke", argTypes, ft.returnType(), KofCallKind.INSTANCE));
        return localIdx;
    }
    if (BuiltinTypes.isList(recvType)
            && ("map".equals(mc.methodName()) || "filter".equals(mc.methodName())
                || "reduce".equals(mc.methodName()))) {
        String hoFn = "kof_list_" + mc.methodName();
        // receiver já empilhado acima (3396) — não duplicar
        Type lambdaT = Type.UnknownType.UNKNOWN;
        // reduce: init antes; lambda por último
        for (ExpressionNode arg : mc.arguments()) {
            if (!(arg instanceof LambdaExpr)) {
                Type argT = ExpressionTyper.inferExprType(driver, arg, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                if (TypeMetrics.isPrimitiveType(argT) && driver.target == Target.JVM) {
                    Type boxed = TypeMetrics.boxedTypeFor(argT);
                    ops.add(new KofCall(boxed, "kof_box", List.of(argT), boxed, KofCallKind.FUNCTION));
                }
            }
        }
        for (ExpressionNode arg : mc.arguments()) {
            if (arg instanceof LambdaExpr lam) {
                lambdaT = ExpressionTyper.inferExprType(driver, lam, locals);
                localIdx = ExpressionLowerer.emitExpression(driver, lam, ops, owner, localIdx, locals);
            }
        }
        List<Type> callParams = new ArrayList<>();
        callParams.add(new Type.ClassType("java.util", "ArrayList", List.of()));
        if ("reduce".equals(mc.methodName())) callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        callParams.add(new Type.ClassType("java.lang", "Object", List.of()));
        Type ret;
        if ("filter".equals(mc.methodName())) ret = recvType;
        else if ("map".equals(mc.methodName())) {
            Type elem = (lambdaT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType)) ? ft.returnType() : Type.UnknownType.UNKNOWN;
            ret = new Type.ClassType("kof", "List", List.of(elem));
        } else {
            ret = (lambdaT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
        }
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()), hoFn, callParams, ret,
                KofCallKind.FUNCTION));
        return localIdx;
    }
    if (KofProcess.isHandle(recvType)) {
        // F10: h.write/readLine/exitCode/kill/alive — o handle
        // empilhado entra como 1º parâmetro do call estático
        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(),
                mc.arguments().stream().map(a -> ExpressionTyper.inferExprType(driver, a, locals)).toList());
        if (hm != null) {
            List<Type> params = new ArrayList<>();
            params.add(KofProcess.HANDLE);
            for (int pi = 1; pi < hm.parameterTypes().size(); pi++) {
                params.add(hm.parameterTypes().get(pi));
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    hm.function(), params, hm.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    if (BuiltinTypes.isChannel(recvType)) {
        // Canais tipados: c.send(v) enfileira; c.receive() retira.
        // O receiver (Channel) está empilhado; o elemento vai
        // após — o backend faz a ordem (send: chan,elem; receive: chan).
        Type elemT = BuiltinTypes.channelElement(recvType);
        if ("send".equals(mc.methodName()) && mc.arguments().size() == 1) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, "kof_channel_send", List.of(elemT),
                    Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
            return localIdx;
        }
        if ("receive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            ops.add(new KofCall(recvType, "kof_channel_receive", List.of(),
                    elemT, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isList(recvType)) {
        String listFn = switch (mc.methodName()) {
            case "add", "push", "append" -> "kof_list_add";
            case "get" -> "kof_list_get";
            case "set" -> "kof_list_set";
            case "size", "length", "count" -> "kof_list_size";
            case "contains" -> "kof_list_contains";
            case "isEmpty" -> "kof_list_is_empty";
            case "remove" -> "kof_list_remove";
            case "clear" -> "kof_list_clear";
            default -> null;
        };
        if (listFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = driver.listElementType(recvType);
            // listOf() with no type argument produces
            // List<Unknown>; the first add() pins the element
            // type on the local so later get() calls are
            // typed (records, classes) instead of Object.
            if ("kof_list_add".equals(listFn)
                    && Type.UnknownType.UNKNOWN.equals(elemType)
                    && !argTypes.isEmpty()
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "List", List.of(argTypes.get(0)))));
                        break;
                    }
                }
            }
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type retType = switch (listFn) {
                case "kof_list_add", "kof_list_set", "kof_list_clear" -> Type.PrimitiveType.VOID;
                case "kof_list_contains", "kof_list_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_list_remove" -> elemType;
                default -> elemType;
            };
            if ("kof_list_contains".equals(listFn)) {

                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, listFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isMap(recvType)) {

        String mapFn = switch (mc.methodName()) {
            case "put" -> "kof_map_put";
            case "get" -> "kof_map_get";
            case "remove" -> "kof_map_remove";
            case "containsKey", "contains" -> "kof_map_contains";
            case "size", "length", "count" -> "kof_map_size";
            case "clear" -> "kof_map_clear";
            case "isEmpty" -> "kof_map_is_empty";
            case "keys" -> "kof_map_keys";
            case "values" -> "kof_map_values";
            default -> null;
        };
        if (mapFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type keyType = Type.UnknownType.UNKNOWN;
            Type valueType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                keyType = ct.typeArguments().get(0);
                valueType = ct.typeArguments().get(1);
            }
            // mapOf() nasce Map<Unknown,Unknown>: o primeiro put()
            // pina os tipos no local para que get()/remove() tenham
            // tipo concreto (comparações e unboxing corretos)
            if ("kof_map_put".equals(mapFn)
                    && keyType instanceof Type.UnknownType
                    && argTypes.size() == 2
                    && !(argTypes.get(0) instanceof Type.UnknownType)
                    && mc.receiver() instanceof IdentifierExpr rid) {
                for (int li = 0; li < locals.size(); li++) {
                    IRLocalVariable lv = locals.get(li);
                    if (lv.name().equals(rid.name())) {
                        locals.set(li, new IRLocalVariable(lv.index(), lv.name(),
                                new Type.ClassType("kof", "Map", List.of(argTypes.get(0), argTypes.get(1)))));
                        break;
                    }
                }
            }
            Type retType = switch (mapFn) {
                case "kof_map_put", "kof_map_remove" -> valueType;
                // get() devolve V? para valores de REFERÊNCIA (ausência = null,
                // narrowing via `if (x != null)`); para primitivos/UI a ausência
                // não é representável no modelo atual (storage é o primitivo) —
                // ficam como V e a ausência vira exceção/erro de runtime.
                case "kof_map_get" -> valueType instanceof Type.ClassType ct
                        && !KofUi.isUiType(ct) && !KofMedia.isHandleType(ct)
                        ? new Type.NullableType(valueType) : valueType;
                case "kof_map_contains", "kof_map_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_map_size" -> Type.PrimitiveType.INT;
                case "kof_map_clear" -> Type.PrimitiveType.VOID;
                case "kof_map_keys", "kof_map_values" -> new Type.ClassType("kof", "List", List.of(mapFn.equals("kof_map_keys") ? keyType : valueType));
                default -> Type.UnknownType.UNKNOWN;
            };
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            ops.add(new KofCall(recvType, mapFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    if (BuiltinTypes.isSet(recvType)) {

        String setFn = switch (mc.methodName()) {
            case "add" -> "kof_set_add";
            case "contains" -> "kof_set_contains";
            case "remove" -> "kof_set_remove";
            // add/contains/remove recebem tag de tipo (1=string)
            case "size", "length", "count" -> "kof_set_size";
            case "clear" -> "kof_set_clear";
            case "isEmpty" -> "kof_set_is_empty";
            default -> null;
        };
        if (setFn != null) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) elemType = ct.typeArguments().get(0);
            Type retType = switch (setFn) {
                case "kof_set_add", "kof_set_remove" -> Type.PrimitiveType.BOOL;
                case "kof_set_contains", "kof_set_is_empty" -> Type.PrimitiveType.BOOL;
                case "kof_set_size" -> Type.PrimitiveType.INT;
                case "kof_set_clear" -> Type.PrimitiveType.VOID;
                default -> Type.UnknownType.UNKNOWN;
            };
            for (ExpressionNode arg : mc.arguments()) localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (driver.target.isNative()
                    && ("kof_set_add".equals(setFn) || "kof_set_contains".equals(setFn)
                        || "kof_set_remove".equals(setFn))) {
                // tag de tipo só no Native (HashSet usa equals no JVM)
                int tag = BuiltinTypes.isString(elemType) ? 1 : 0;
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
                argTypes = new ArrayList<>(argTypes);
                argTypes.add(Type.PrimitiveType.INT);
            }
            ops.add(new KofCall(recvType, setFn, argTypes, retType, KofCallKind.INSTANCE));
            return localIdx;
        }
    }
    // bug 16: `toArray()` não é suportado (nem documentado) e
    // caía no caminho genérico → bytecode inválido (JVM) /
    // undefined reference (Native). Diagnóstico limpo em vez de
    // saída quebrada.
    if ("toArray".equals(mc.methodName())
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "método '" + mc.methodName() + "' não é suportado em coleções;"
                        + " use um loop com new T[n] para materializar um array",
                "SEM029");
    }
    // bug 16 (cauda): `sublist()`/`subSet()` retornam COLEÇÃO —
    // o backend não sabe materializar o retorno de coleção e
    // emitia bytecode inválido (JVM) / undefined reference
    // (Native). Mesmo tratamento do toArray: diagnóstico limpo.
    if (("sublist".equals(mc.methodName()) || "subSet".equals(mc.methodName()))
            && (BuiltinTypes.isList(recvType) || BuiltinTypes.isSet(recvType))
            && driver.currentDiagnostics != null) {
        driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0,
                "método '" + mc.methodName() + "' não é suportado em coleções"
                        + " (retorno de coleção não é materializável);"
                        + " copie os elementos com um loop",
                "SEM034");
    }
    Type methodReturnType = Type.UnknownType.UNKNOWN;
    List<Type> methodParamTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        methodParamTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
    SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer.getResolvedMethod(mc);
    if (resolvedMethod != null) {
        recvType = CompilerTypes.ownerTypeFromInternal(resolvedMethod.ownerClass(), driver.semanticAnalyzer);
        methodReturnType = resolvedMethod.returnType();
        methodParamTypes = new ArrayList<>(resolvedMethod.parameterTypes());
    } else if (BuiltinTypes.isString(recvType)) {
        StringMethodRegistry.Sig sig = StringMethodRegistry.stringMethodSignature(mc.methodName(), mc.arguments().size(),
                methodParamTypes);
        if (sig != null) {
            methodReturnType = sig.returnType();
            methodParamTypes = sig.parameterTypes();
        }
    } else if (TypeMetrics.isPrimitiveType(recvType) && "toString".equals(mc.methodName())
            && mc.arguments().isEmpty()) {
        // primitivo.toString(): o primitivo não tem classe —
        // boxar e converter (String.valueOf) em vez de gerar
        // um owner vazio no bytecode (ClassFormatError)
        TypeEmitter.boxPrimitive(ops, recvType);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        return localIdx;
    } else {
        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
        if (osig != null) {
            methodReturnType = osig.returnType();
            methodParamTypes = osig.parameterTypes();
        }
    }
    if (methodReturnType instanceof Type.UnknownType) {
        // fall back to the lowering's own inference (list-get
        // chains, user classes resolved through hierarchy)
        Type inferred = ExpressionTyper.inferExprType(driver, mc, locals);
        if (!(inferred instanceof Type.UnknownType)) {
            methodReturnType = inferred;
        }
    }
    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), methodParamTypes, ops, owner, localIdx, locals);
    KofCallKind callKind = KofCallKind.INSTANCE;
    if (recvType instanceof Type.ClassType rt && driver.semanticAnalyzer != null) {
        if (driver.semanticAnalyzer.isInterfaceType(rt.name())) {
            callKind = KofCallKind.INTERFACE;
        }
    }
    if (callKind == KofCallKind.INSTANCE && resolvedMethod != null && driver.semanticAnalyzer != null) {
        String ownerName = resolvedMethod.ownerClass();
        if (ownerName.contains("/")) ownerName = ownerName.substring(ownerName.lastIndexOf('/') + 1);
        if (driver.semanticAnalyzer.isInterfaceType(ownerName)) {
            callKind = KofCallKind.INTERFACE;
        }
    }
    String runtimeMethod = BuiltinTypes.isString(recvType)
            ? StringMethodRegistry.stringRuntimeMethod(mc.methodName()) : null;
    // receiver de classe EXTERNA sem símbolo resolvido: última
    // linha de defesa — assinatura vem do classpath, senão o
    // descritor sairia errado (owner vazio / retorno Object)
    if (resolvedMethod == null && runtimeMethod == null
            && mc.receiver() != null && driver.currentDiagnostics != null) {
        Type rt2 = driver.semanticAnalyzer != null
                ? driver.semanticAnalyzer.getExpressionType(mc.receiver())
                : Type.UnknownType.UNKNOWN;
        if (!(rt2 instanceof Type.ClassType)) {
            rt2 = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
        }
        if (rt2 instanceof Type.ClassType ct2 && !ct2.packageName().isEmpty()
                && driver.externalClasspath != null
                && driver.externalClasspath.knows(ct2.internalName())) {
            ExternalClasspath.MethodSignature sig = driver.externalClasspath.resolveMethod(
                    ct2.internalName(), mc.methodName(), mc.arguments().size());
            if (sig != null) {
                List<Type> formal = new ArrayList<>();
                for (String d : sig.parameterDescriptors()) {
                    formal.add(ExternalClasspath.typeFromDescriptor(d));
                }
                recvType = ct2;
                methodParamTypes = formal;
                methodReturnType = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
            }
        }
    }
    // String.valueOf/Integer.valueOf/…: receiver é o NOME de
    // um tipo builtin (não uma variável) — o identificador não
    // empilha valor; mapeia para o owner JDK estático (sem
    // isso o emit saía com owner "" → ClassFormatError)
    if (recvType instanceof Type.UnknownType
            && mc.receiver() instanceof IdentifierExpr brid
            && driver.findLocalVar(brid.name(), locals) == null
            && !brid.name().isEmpty()
            && Character.isUpperCase(brid.name().charAt(0))) {
        Type jdkOwner = switch (brid.name()) {
            case "String" -> BuiltinTypes.STRING;
            case "Int", "Integer" -> new Type.ClassType("java.lang", "Integer", List.of());
            case "Long" -> new Type.ClassType("java.lang", "Long", List.of());
            case "Float" -> new Type.ClassType("java.lang", "Float", List.of());
            case "Double" -> new Type.ClassType("java.lang", "Double", List.of());
            case "Bool", "Boolean" -> new Type.ClassType("java.lang", "Boolean", List.of());
            default -> Type.UnknownType.UNKNOWN;
        };
        if (!(jdkOwner instanceof Type.UnknownType)) {
            recvType = jdkOwner;
            callKind = KofCallKind.STATIC;
            if (methodParamTypes.size() == 1
                    && methodParamTypes.get(0) instanceof Type.PrimitiveType) {
                // valueOf(I) direto do JDK — sem boxing duplo
                methodReturnType = BuiltinTypes.STRING;
            }
        }
    }
    ops.add(new KofCall(recvType,
            runtimeMethod != null ? runtimeMethod : mc.methodName(),
            methodParamTypes, methodReturnType, callKind));
    if (methodReturnType instanceof Type.TypeVariable) {
        Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
        if (TypeMetrics.isPrimitiveType(effective)) {
            driver.emitErasureUnbox(ops, effective);
        }
    }
} else {
    if (("super".equals(mc.methodName()) || "driver".equals(mc.methodName()))
            && driver.semanticAnalyzer != null && !owner.isEmpty()) {
        // super(args): construtor da superclasse (Object quando
        // a classe não tem extends). driver(args): delegação para
        // outro construtor da própria classe — o alvo executa
        // super() e os inicializadores de campo.
        boolean delegation = "driver".equals(mc.methodName());
        String targetInternal;
        if (delegation) {
            targetInternal = owner;
        } else {
            targetInternal = HierarchyResolver.findSuperClass(owner, driver.semanticAnalyzer);
            if (targetInternal == null) targetInternal = "java/lang/Object";
            targetInternal = targetInternal.replace('.', '/');
        }
        Type targetType = CompilerTypes.ownerTypeFromInternal(targetInternal, driver.semanticAnalyzer);
        SymbolTable.ClassSymbol targetCs = driver.semanticAnalyzer.getClass(
                targetInternal.substring(targetInternal.lastIndexOf('/') + 1));
        SymbolTable.ConstructorSymbol ctor = null;
        if (targetCs != null) {
            SymbolTable.Symbol ctorSym = targetCs.members().resolve("<init>");
            if (ctorSym instanceof SymbolTable.ConstructorSymbol c
                    && c.parameterTypes().size() == mc.arguments().size()) {
                ctor = c;
            }
        }
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer), 0));
        List<Type> ctorParamTypes;
        if (ctor != null && ctor.parameterTypes().size() == mc.arguments().size()) {
            ctorParamTypes = ctor.parameterTypes();
        } else {
            if (targetCs != null && driver.currentDiagnostics != null) {
                // classe conhecida e nenhum construtor com essa
                // aridade — erro em compile-time
                SourcePosition p = mc.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        (delegation ? "no constructor of '" : "no super constructor of '")
                                + targetInternal.substring(targetInternal.lastIndexOf('/') + 1)
                                + "' with " + mc.arguments().size() + " argument(s)",
                        "SEM017");
                return localIdx;
            }
            ctorParamTypes = argTypes;
        }
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
        ops.add(new KofCall(targetType, "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        return localIdx;
    }
    SymbolTable.MethodSymbol selfMethod = driver.semanticAnalyzer != null
            ? driver.semanticAnalyzer.getResolvedMethod(mc) : null;
    if (selfMethod != null && !owner.isEmpty()
            && !"<init>".equals(selfMethod.name())
            && selfMethod.ownerClass() != null) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(selfMethod.ownerClass(), driver.semanticAnalyzer);
        ops.add(new KofLoadLocal(ownerType, 0));
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                ops, owner, localIdx, locals);
        ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                selfMethod.returnType(), KofCallKind.INSTANCE));
        return localIdx;
    }
    SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
    if (cs != null) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        SymbolTable.ConstructorSymbol ctor = null;
        SymbolTable.Symbol ctorSym = cs.members().resolve("<init>");
        if (ctorSym instanceof SymbolTable.ConstructorSymbol ctorSingle) ctor = ctorSingle;
        ops.add(new KofNewObject(cs.type(), argTypes));
        ops.add(new KofDup());
        List<Type> ctorParamTypes = (ctor != null
                && ctor.parameterTypes().size() == mc.arguments().size())
                ? ctor.parameterTypes() : argTypes;
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ctorParamTypes, ops, owner, localIdx, locals);
        ops.add(new KofCall(cs.type(), "<init>", ctorParamTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
    } else {
        IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
        if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
            if (lft.className() == null) {
                // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
                // (s: (Int) -> Int), sem classe sintética). Todas
                // as lambdas da assinatura implementam a interface
                // sintética — invoca via INVOKEINTERFACE.
                localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                        ops, owner, localIdx, locals);
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(),
                        ops, owner, localIdx, locals);
                Type iface = driver.lambdaInterfaceType(lft);
                ops.add(new KofCall(iface, "invoke", argTypes, lft.returnType(),
                        KofCallKind.INTERFACE));
            } else {
            localIdx = ExpressionLowerer.emitExpression(driver, new IdentifierExpr(mc.position(), mc.methodName()),
                    ops, owner, localIdx, locals);
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), lft.parameterTypes(), ops, owner, localIdx, locals);
            Type invokeOwner = new Type.ClassType("", lft.className(), List.of());
            ops.add(new KofCall(invokeOwner, "invoke", argTypes, lft.returnType(), KofCallKind.INSTANCE));
            }
        } else {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            Type returnType = Type.UnknownType.UNKNOWN;
            if (driver.currentUnit != null) {
                for (AstNode d : driver.currentUnit.declarations()) {
                    if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                        returnType = CompilerTypes.resolveWithTypeParams(fn.returnType(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
                        List<Type> fnTypes = fn.parameters().stream()
                                .map(p -> CompilerTypes.resolveWithTypeParams(p.type(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
                        boolean hasDefaults = fn.parameters().stream()
                                .anyMatch(p -> p.defaultExpression() != null);
                        if (hasDefaults && mc.arguments().size() < fnTypes.size()) {
                            argTypes = fnTypes.subList(0, mc.arguments().size());
                        } else {
                            argTypes = fnTypes;
                        }
                        break;
                    }
                }
            }
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), argTypes, ops, owner, localIdx, locals);
            ops.add(new KofCall(CompilerTypes.mainClassType(driver.currentModule), mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
            Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
            if (returnType instanceof Type.TypeVariable && TypeMetrics.isPrimitiveType(effective)) {
                driver.emitErasureUnbox(ops, effective);
            }
        }
    }
}
return localIdx;
    }
}
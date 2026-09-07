package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de chamadas estáticas/builtin (receiver null) no emitExpression.
 * Retorna -1 se nenhum branch estático foi reconhecido.
 */
final class ExpressionStaticCallLowerer {

    private ExpressionStaticCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
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
    if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())) {
        int handledUi = ExpressionUiStaticLowerer.lower(driver, mc, ops, owner, localIdx, locals);
        if (handledUi >= 0) return handledUi;
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
    ExpressionNode body = mc.arguments().get(0);
    Type resultT = ExpressionTyper.inferExprType(driver, body, locals);
    Type handleT = new Type.ClassType("kof.concurrent", "Handle", List.of(resultT));
    LambdaExpr le = body instanceof LambdaExpr l0 ? l0
            : new LambdaExpr(body.position() != null ? body.position() : mc.position(),
                    List.of(), List.of(new ExpressionStmt(
                            body.position() != null ? body.position() : mc.position(), body)));
    Type.FunctionType ft = new Type.FunctionType(List.of(), resultT, null);
    List<IRLocalVariable> caps = driver.collectCaptures(le, locals);
    List<IRLocalVariable> eff = driver.lambdaEffectiveCaptures.get(le);
    if (eff != null) caps = eff;
    String lc = driver.lambdaClass(le, ft, caps, true);
    Type tt = new Type.ClassType("", lc, List.of());
    List<Type> cts = new ArrayList<>();
    for (IRLocalVariable c : caps) cts.add(c.type());
    ops.add(new KofNewObject(tt, cts));
    ops.add(new KofDup());
    for (IRLocalVariable c : caps) ops.add(new KofLoadLocal(c.type(), c.index()));
    ops.add(new KofCall(tt, "<init>", cts, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
    ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
            "kof_spawn_result", List.of(tt), handleT, KofCallKind.FUNCTION));
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
        return ExpressionPrintLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    }
    return -1;
    }
}
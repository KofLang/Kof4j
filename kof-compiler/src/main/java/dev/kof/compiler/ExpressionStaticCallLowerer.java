package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de chamadas estáticas/builtin (receiver null) no emitExpression.
 * Retorna -1 se nenhum branch estático foi reconhecido.
 */
public final class ExpressionStaticCallLowerer {

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
if (mc.receiver() == null && "subscriptionsLive".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    // D-COMPLETE-FIRST item 4: leak probe de subscriptions vivas.
    ops.add(new KofCall(new Type.ClassType("kof.ui", "Ui", List.of()),
            "kof_ui_subscriptions_live", List.of(), Type.PrimitiveType.INT, KofCallKind.FUNCTION));
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
        // #102 item 3: o runtime nativo só tem o T1 (listen/route + body/
        // method/path); as demais funções de contexto não são emitidas e a
        // chamada vazava para o linker como `undefined reference to
        // 'kof_web_param'`. Agora é WEB001 em tempo de compilação (R6 —
        // gap diagnosticado, nunca ld-fail).
        if (!KofWeb.contextNativeSupported(webCtx.function()) && KofWeb.isNativeTarget(driver.target)) {
            if (driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0,
                        0, "web context '" + mc.methodName() + "()': not available on the "
                                + driver.target + " driver.target yet (WEB001)",
                        "WEB001");
            }
            return localIdx;
        }
        // 16/09: o mesmo vazamento existia no JS — context-fns sem runtime
        // baixavam para kofWebStub (return 0 SILENCIOSO, R6). Gap em tempo de
        // compilação, código por função. SSE (kof_web_sse_send) ganhou runtime
        // handler-scoped no host JS (16/09) e saiu da lista; restam wsSend/
        // wsMessage (WEB004) e stats (WEB001).
        if (driver.target == Target.JS
                && !KofWeb.contextJsSupported(webCtx.function())) {
            String code = KofWeb.gapCode(webCtx.function());
            if (driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0,
                        0, "web context '" + mc.methodName() + "()': not available on the "
                                + driver.target + " driver.target yet (" + code + ")",
                        code);
            }
            return localIdx;
        }
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
    if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())) {
        int handledUi = ExpressionUiStaticLowerer.lower(driver, mc, ops, owner, localIdx, locals);
        if (handledUi >= 0) return handledUi;
    }
// X6 (D-INTEROP-REFLECT, X6.2): TODO uso do namespace `interop` é dono do
// CompilerInterop — `schema(R)` dobra para as mesmas ops que
// `listOf(Field("n","t"), …)` emitiria (sem reflexão em runtime, mesma saída
// nos 4 alvos); membro desconhecido, aridade errada ou argumento que não é o
// nome de um record declarado são diagnóstico honesto (R6) — nunca silêncio
// (antes caíam no instance-lowerer genérico e a chamada sumia). Locais,
// campos da classe corrente e classes/records do usuário chamados `interop`
// sombream o namespace e seguem o caminho de instância/classe normal.
if (mc.receiver() instanceof IdentifierExpr rid
        && CompilerInterop.isInteropNamespace(rid.name())
        && !driver.isLocalVarName(rid.name(), locals)
        && !ExpressionMethodCallLowerer.shadowsFieldOfCurrentClass(driver, owner, rid.name())
        && (driver.semanticAnalyzer == null || driver.semanticAnalyzer.getClass(rid.name()) == null)) {
    return CompilerInterop.lowerNamespaceCall(driver, mc, ops, owner, localIdx, locals);
}
if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
    Type elemType = driver.listOfElementType(mc, locals);
    Type listType = new Type.ClassType("kof", "List", List.of(elemType));
    ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
    for (ExpressionNode arg : mc.arguments()) {
        Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
        // §126/§121/§144 (B1c): o caminho literal NÃO passava por
        // pollutesPinned nem pela coerção → `listOf(1, 2.5)` (Double em
        // Int-pinado) virava VerifyError no JVM, `listOf(1L, 2)` (widening)
        // quebrava igual. Mesma disciplina do add: rejeitar o que quebra,
        // converter o widening abençoado.
        if ((CollectionWrites.pollutesPinned(elemType, argType)
                || CollectionWrites.breaksPinnedList(elemType, argType))
                && driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "listOf: element " + CollectionWrites.typeNameFor(argType)
                            + " does not match the list element type ("
                            + CollectionWrites.typeNameFor(elemType)
                            + ") — Kof collections are homogeneous", "SEM056");
            return localIdx;
        }
        ops.add(new KofDup());
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        Type paramT = argType;
        if (CompilerEmissionHelpers.coerceStoreWiden(driver, ops, argType, elemType, true)) {
            paramT = elemType instanceof Type.NullableType nt ? nt.inner() : elemType;
        }
        ops.add(new KofCall(listType, "kof_list_add", List.of(paramT),
                Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    }
    return localIdx;
}
// CONC001 fechado 15/09: os 6 helpers existem no cross (fatia RtB48 +
// spawn trampoline com cancel slot) — o gate isCrossMissingConcurrencyBuiltin
// foi REMOVIDO; cancel/cancelled/selectAny/done/poll/awaitTimeout descem
// pelo caminho genérico abaixo (mesmas assinaturas do x86).
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
if (mc.receiver() == null && "ring1".equals(mc.methodName())
        && mc.arguments().size() == 1
        && driver.findLocalVar("ring1", locals) == null
        && !hasUserFunctionNamed(driver, "ring1")) {
    // B-6.2b: builtin marcador `ring1(fn)` (D-BAREMETAL-RING1-SURFACE) — so
    // baixa no perfil x86_64 UEFI_RING; nos demais alvos/perfis e NATIVE003
    // nomeado (R6/R7, nunca silencioso). O alvo roda em CPL1: so pode tocar
    // memoria e retornar (chamar firmware ring0, ex. println, geraria #GP).
    ExpressionNode arg0 = mc.arguments().get(0);
    String fnName = arg0 instanceof IdentifierExpr id ? id.name() : null;
    if (fnName != null
            && driver.target == Target.NATIVE
            && dev.kof.compiler.nat.NativeProfile.UEFI_RING.equals(driver.nativeProfile())) {
        ops.add(new KofFunctionAddress(
                CompilerTypes.mainClassType(driver.currentModule), fnName, List.of()));
        ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                "kof_ring1_run", List.of(Type.PrimitiveType.LONG),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION));
        return localIdx;
    }
    if (driver.currentDiagnostics != null) {
        var rpos = arg0.position() != null ? arg0.position() : mc.position();
        driver.currentDiagnostics.error(rpos != null ? rpos.file() : "",
                rpos != null ? rpos.line() : 0, rpos != null ? rpos.column() : 0, 0,
                "ring1(fn): only available on --target native --profile uefi-ring"
                        + " (x86_64) (NATIVE003)", "NATIVE003");
    }
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
    // bug 29: o corpo é uma LAMBDA — o tipo do Handle é o RETURN da lambda,
    // não o tipo dela em si. inferExprType(lambda) dá FunctionType([],
    // void); usar isso como returnType da task gerava FunctionType([],
    // FunctionType) → invoke():Object com corpo void → areturn em stack
    // vazia (VerifyError JVM) e segfault no nativo.
    Type resultT;
    if (body instanceof LambdaExpr le0) {
        resultT = ExpressionTyper.inferLambdaBodyType(driver, le0, locals);
    } else {
        resultT = ExpressionTyper.inferExprType(driver, body, locals);
    }
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
    for (IRLocalVariable c : caps) CompilerCaptures.pushCapture(driver, ops, c);
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
    Type enumT = CompilerTypes.enumTypeOf(rid.name(), driver.semanticAnalyzer); // #445
    Type enumListT = new Type.ClassType("kof", "List", List.of(enumT));
    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        ops.add(new KofCall(enumT, "values", List.of(), enumListT, KofCallKind.STATIC));
        return localIdx;
    }
    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        ops.add(new KofCall(enumT, "valueOf", List.of(BuiltinTypes.STRING), enumT, KofCallKind.STATIC));
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
        Type kType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
        Type vType = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai + 1), locals);
        // §126/§144 (B1b-c): literal do mapOf também é escrita — rejeitar o
        // que quebra (String↔não-String, narrowing numérico; M3 dava mapa
        // heterogêneo no JVM e truncado no Native).
        if (CollectionWrites.pollutesPinned(valueType, vType) && driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "mapOf: value " + CollectionWrites.typeNameFor(vType)
                            + " does not match the map type ("
                            + CollectionWrites.typeNameFor(valueType)
                            + ") — Kof collections are homogeneous", "SEM056");
            return localIdx;
        }
        ops.add(new KofDup());
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai), ops, owner, localIdx, locals);
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(ai + 1), ops, owner, localIdx, locals);
        // §121/§143 (B1): widening abençoado no VALOR (M1: put(2) em Map<_,Long>
        // dava CCE no get — Integer salvo sob pin Long).
        if (CompilerEmissionHelpers.coerceStoreWiden(driver, ops, vType, valueType, false)) {
            vType = valueType instanceof Type.NullableType nt2 ? nt2.inner() : valueType;
        }
        // §284-map (18/09): UMA convenção física para o valor do Map — o
        // literal boxea igual ao m.put() (CollectionCallLowerer), senão o
        // tag-7 do formatador leria cru × caixa mistos no mesmo mapa.
        if (driver.target.isNative() && driver.needsErasureBoxing()
                && CollectionCallLowerer.mapBoxablePrim(vType)
                && !ExpressionTyper.boxesOwnBranches(driver, mc.arguments().get(ai + 1), locals)) {
            CompilerEmissionHelpers.emitErasureBox(driver, ops, vType);
        }
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
        Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
        List<Type> addArgs = new ArrayList<>(List.of(argType));
        if (driver.target.isNative()) {
            // §104b-ii (24/09): MESMA convenção do set.add (CollectionCallLowerer)
            // — sem o tag o runtime lia um registrador SUJO (a2/edx) e o tag 2
            // (objeto Kof) disparava despacho indevido (SIGSEGV riscv em
            // `setOf(1, 2)` depois de um println de lista; dedup de String
            // também dependia da sujeira).
            addArgs.add(Type.PrimitiveType.INT);
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT,
                    CollectionWrites.stringTag(elemType, List.of(argType), 0)));
        }
        // VOID na construção: o backend descarta o bool e o set
        // duplicado continua na pilha para o próximo append
        ops.add(new KofCall(setType, "kof_set_add", addArgs, Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    }
    return localIdx;
}
    if (mc.receiver() == null && ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) && mc.arguments().size() == 1) {
        return ExpressionPrintLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    }
    return -1;
    }

    // #91: o gate CONC001 foi REMOVIDO (15/09) — os 6 helpers (poll/done/
    // cancel/cancelled/selectAny/awaitTimeout) existem no runtime cross
    // (fatia NativeRiscvAsmRtB48 + trampoline do spawn registrando o cancel
    // slot; TID real via gettid(178) gravado pelo kernel no clone ctid).
    // O caminho do frontend é o MESMO do x86 (kof_* FUNCTION, assinaturas
    // idênticas) — NativeX86Calls é a referência de semântica.

    /** B-6.2b: `ring1` so vale como builtin se nenhuma funcao do usuario usa
     *  o nome (nao sombreia — freeze regra 2). */
    private static boolean hasUserFunctionNamed(CompilerDriver driver, String name) {
        if (driver.currentUnit == null) return false;
        for (AstNode d : driver.currentUnit.declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)) return true;
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        return false;
    }
}
package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo de MethodCallExpr (parte do inferExprType).
 */
final class MethodCallTyper {

    private MethodCallTyper() {}

    static Type inferType(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
// super.metodo(): resolvido AQUI (o cache do analyzer é
// limpo a cada classe/passe — não dá para confiar nele)
if (mc.receiver() instanceof IdentifierExpr srid && "super".equals(srid.name())
        && driver.semanticAnalyzer != null && driver.currentLoweringOwner != null) {
    String simple = driver.currentLoweringOwner.substring(driver.currentLoweringOwner.lastIndexOf('/') + 1);
    SymbolTable.ClassSymbol self = driver.semanticAnalyzer.getClass(simple);
    String sup = self != null && self.superClass() != null ? self.superClass() : "Object";
    sup = sup.replace('.', '/');
    SymbolTable.Symbol m2 = driver.semanticAnalyzer.resolveInHierarchy(
            sup.substring(sup.lastIndexOf('/') + 1), mc.methodName());
    if (m2 instanceof SymbolTable.MethodSymbol ms2) return ms2.returnType();
    return Type.UnknownType.UNKNOWN;
}
// o analyzer já tipou esta expressão durante a análise:
// fonte secundária para os demais casos
if (driver.semanticAnalyzer != null) {
    Type semantic = driver.semanticAnalyzer.getExpressionType(mc);
    // tipos com FunctionType de className null vêm da análise
    // semântica, que roda ANTES da síntese das lambdas — são
    // obsoletos para o emit (o invoke de lambda precisaria do
    // className → owner "" → ClassFormatError, bug 20). Re-inferir.
    if (!(semantic instanceof Type.UnknownType)
            && !CompilerTypes.containsLambdaFunctionType(semantic)) {
        if (semantic instanceof Type.TypeVariable tv && mc.receiver() != null) {
            Type recvT = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
            Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
            if (subst != null) return subst;
        }
        return semantic;
    }
}
if (mc.receiver() == null && driver.semanticAnalyzer != null
        && driver.semanticAnalyzer.getClass(mc.methodName()) != null) {
    return driver.semanticAnalyzer.getClass(mc.methodName()).type();
}
if (mc.receiver() != null && "toString".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    Type rv = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    if (TypeMetrics.isPrimitiveType(rv) || rv instanceof Type.ArrayType) return BuiltinTypes.STRING;
}
// String.valueOf(x) / Integer.valueOf(x)…: receiver é o NOME
// do tipo builtin (estático). Sem tipo aqui o concat após um
// "s = s + String.valueOf(x)" aplicava box+valueOf duplicado
// no resultado (frame crash — 3 valueOf na pilha)
if (mc.receiver() instanceof IdentifierExpr srid && mc.arguments().size() == 1
        && driver.findLocalVar(srid.name(), locals) == null
        && switch (srid.name()) {
            case "String", "Int", "Integer", "Long", "Float",
                    "Double", "Bool", "Boolean" -> true;
            default -> false;
        }) {
    return BuiltinTypes.STRING;
}
if ("println".equals(mc.methodName()) || "print".equals(mc.methodName())) return Type.PrimitiveType.VOID;
if ("now".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
    return Type.PrimitiveType.LONG;
}
if ("uiNodesLive".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
    return Type.PrimitiveType.INT;
}
if ("emit".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().size() == 2) {
    return Type.PrimitiveType.VOID;
}
if ("storesLive".equals(mc.methodName()) && mc.receiver() == null && mc.arguments().isEmpty()) {
    return Type.PrimitiveType.INT;
}
if (mc.receiver() == null && "transaction".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return Type.PrimitiveType.VOID;
}
if ("readLine".equals(mc.methodName()) && mc.receiver() == null) {
    return new Type.NullableType(BuiltinTypes.STRING);
}
if ("readFile".equals(mc.methodName()) && mc.receiver() == null) {
    return new Type.NullableType(BuiltinTypes.STRING);
}
if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
        && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
    return BuiltinTypes.STRING;
}
if ("writeFile".equals(mc.methodName()) && mc.receiver() == null) {
    return Type.PrimitiveType.INT;
}
if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
    return KofIo.constructorType(mc.methodName());
}
if (mc.receiver() == null && "Color".equals(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
    return KofUi.COLOR;
}
if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.WINDOW;
}
if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.LABEL;
}
if (mc.receiver() == null && "Button".equals(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
    return KofUi.BUTTON;
}
if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.INPUT;
}
if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    return "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
}
if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.VIEW;
}
if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2
                || mc.arguments().size() == 3)) {
    Type ct = KofUi.constructorType(mc.methodName());
    // cobre layout/store E os widgets sem branch explícito acima
    // (Canvas/Image/Icon/Link/Font/Component) — sem isso o local é
    // inferido UNKNOWN e o emit produz owner "" (ClassFormatError).
    if (KofUi.isUiType(ct)) {
        return ct;
    }
}
if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
    return KofUi.STYLE;
}
if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
    KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
    if (uiCall != null) return uiCall.returnType();
}
if (mc.receiver() instanceof IdentifierExpr ridR && KofUi.isRouterNamespace(ridR.name())) {
    KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
    if (routerCall != null) {
        for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferExprType(driver, arg, locals);
        return routerCall.returnType();
    }
}
if ("listOf".equals(mc.methodName()) && mc.receiver() == null) {
    return new Type.ClassType("kof", "List", List.of(driver.listOfElementType(mc, locals)));
}
if ("mapOf".equals(mc.methodName()) && mc.receiver() == null) {
    // pinning do tipo no primeiro par — espelha o emit (mapOf(k1,v1,...))
    Type keyType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
            : ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    Type valueType = mc.arguments().size() < 2 ? Type.UnknownType.UNKNOWN
            : ExpressionTyper.inferExprType(driver, mc.arguments().get(1), locals);
    return new Type.ClassType("kof", "Map", List.of(keyType, valueType));
}
if ("setOf".equals(mc.methodName()) && mc.receiver() == null) {
    Type elemType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN : ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    return new Type.ClassType("kof", "Set", List.of(elemType));
}
if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
    Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    return new Type.ClassType("kof.concurrent", "Handle", List.of(t));
}
                if (mc.receiver() == null && "cancel".equals(mc.methodName())
        && mc.arguments().size() == 1
        && driver.findLocalVar("cancel", locals) == null) {
    return Type.PrimitiveType.BOOL;
}
if (mc.receiver() == null && "cancelled".equals(mc.methodName())
        && mc.arguments().isEmpty()
        && driver.findLocalVar("cancelled", locals) == null) {
    return Type.PrimitiveType.BOOL;
}
if (mc.receiver() == null && "selectAny".equals(mc.methodName())
        && !mc.arguments().isEmpty()
        && driver.findLocalVar("selectAny", locals) == null) {
    Type first = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (first instanceof Type.ClassType ct
            && "kof.concurrent".equals(ct.packageName())
            && !ct.typeArguments().isEmpty()) {
        return ct.typeArguments().get(0);
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && "poll".equals(mc.methodName())
        && mc.arguments().size() == 1 && driver.findLocalVar("poll", locals) == null) {
    Type h = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (h instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
        return ct.typeArguments().get(0);
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && "done".equals(mc.methodName())
        && mc.arguments().size() == 1 && driver.findLocalVar("done", locals) == null) {
    return Type.PrimitiveType.BOOL;
}
if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
    Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (t instanceof Type.ClassType ct
            && "kof.concurrent".equals(ct.packageName())
            && !ct.typeArguments().isEmpty()) {
        return ct.typeArguments().get(0);
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
        && mc.arguments().size() == 2
        && driver.findLocalVar("awaitTimeout", locals) == null) {
    Type t = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if (t instanceof Type.ClassType ct
            && "kof.concurrent".equals(ct.packageName())
            && !ct.typeArguments().isEmpty()) {
        return ct.typeArguments().get(0);
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && CompilerTypes.isEnumName(rid.name(), driver.currentUnit)
        && driver.findLocalVar(rid.name(), locals) == null) {
    java.util.List<String> consts = CompilerTypes.enumConstantsOf(rid.name(), driver.currentUnit);
    Type enumT = new Type.ClassType("", rid.name(), List.of());
    // MVP: elementos tipados como String (runtime do enum é o nome);
    // comparação com constantes funciona via string-equals
    if ("values".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        return new Type.ClassType("kof", "List", List.of(BuiltinTypes.STRING));
    }
    if ("valueOf".equals(mc.methodName()) && mc.arguments().size() == 1) {
        return enumT;
    }
    // constante via sintaxe de método? Color.Red() — não suportado
    if (consts.contains(mc.methodName())) return enumT;
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && "json".equals(rid.name())) {
    if ("encode".equals(mc.methodName())) return BuiltinTypes.STRING;
    if ("decode".equals(mc.methodName()) && !mc.typeArguments().isEmpty()) {
        return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofWeb.isWebNamespace(rid.name())) {
    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        return KofWeb.APP;
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
    KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
    if (dbCall != null) {
        if (typed && !mc.typeArguments().isEmpty()) {
            return new Type.ClassType("kof", "List",
                    List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
        }
        return dbCall.returnType();
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofHttp.isHttpNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
    if (httpCall != null) return httpCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofMq.isMqNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
    if (mqCall != null) return mqCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofTime.isTimeNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
    if (timeCall != null) return timeCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofScheduler.isSchedulerNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (sc != null) return sc.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall sc = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (sc != null) return sc.returnType();
    // fall through
}
if (mc.receiver() instanceof IdentifierExpr rid && KofWorkflow.isWorkflowNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofWorkflow.WorkflowCall wc = KofWorkflow.staticCall(mc.methodName(), argTypes);
    if (wc != null) return wc.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && KofWorkflow.isWorkflowMethod(mc.methodName())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofWorkflow.WorkflowCall wc = KofWorkflow.staticCall(mc.methodName(), argTypes);
    if (wc != null) return wc.returnType();
    // fall through
}
if (mc.receiver() instanceof IdentifierExpr rid && KofShell.isShellNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofShell.ShellCall sc = KofShell.staticCall(mc.methodName(), argTypes);
    if (sc != null) return sc.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() == null && KofShell.isShellMethod(mc.methodName())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofShell.ShellCall sc = KofShell.staticCall(mc.methodName(), argTypes);
    if (sc != null) return sc.returnType();
    // fall through
}
if (mc.receiver() instanceof IdentifierExpr rid && KofLog.isLogNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
    if (logCall != null) return logCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    boolean typed = !mc.typeArguments().isEmpty();
    String entityName = typed ? mc.typeArguments().get(0) : null;
    KofOrm.OrmCall ormCall = KofOrm.staticCall(mc.methodName(), argTypes, typed, entityName);
    if (ormCall != null) {
        if ("save".equals(mc.methodName()) && !argTypes.isEmpty()) {
            return argTypes.get(argTypes.size() - 1);
        }
        if (typed && !mc.typeArguments().isEmpty()) {
            if ("all".equals(mc.methodName()) || "where".equals(mc.methodName())
                    || "page".equals(mc.methodName())) {
                return new Type.ClassType("kof", "List",
                        List.of(CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit)));
            }
            if ("find".equals(mc.methodName())) {
                return CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
            }
        }
        return ormCall.returnType();
    }
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
    if (procCall != null) return procCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofConfig.isConfigNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
    if (cfgCall != null) return cfgCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid && KofTetris.isTetrisNamespace(rid.name())) {
    KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
            mc.arguments().size());
    if (tetrisCall != null) return tetrisCall.returnType();
    return Type.UnknownType.UNKNOWN;
}
if (mc.receiver() instanceof IdentifierExpr rid2 && KofIo.isConstructor(rid2.name())) {
    KofIo.IoCall ioCall = KofIo.staticMethod(rid2.name(), mc.methodName(), mc.arguments().size());
    if (ioCall != null) return ioCall.returnType();
}
if (mc.receiver() != null) {
    Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    // narrowing de null-safety: `if (x != null) { x.metodo() }`
    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
    if (KofProcess.isHandle(recvType)) {
        List<Type> hArgs = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) hArgs.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), hArgs);
        if (hm != null) return hm.returnType();
    }
    if (mc.receiver() instanceof IdentifierExpr rid && KofSecurity.isSecurityNamespace(rid.name())) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
        if (secCall != null) return secCall.returnType();
        return Type.UnknownType.UNKNOWN;
    }
    if (mc.receiver() instanceof IdentifierExpr rid && KofValidation.isValidationNamespace(rid.name())) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
        if (vCall != null) return vCall.returnType();
        return Type.UnknownType.UNKNOWN;
    }
    if (mc.receiver() instanceof IdentifierExpr rid && KofObservability.isObservabilityNamespace(rid.name())) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
        if (oCall != null) return oCall.returnType();
        return Type.UnknownType.UNKNOWN;
    }
    if (KofUi.isUiType(recvType)) {
        KofUi.UiCall uiCall = KofUi.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (uiCall != null) return uiCall.returnType();
    }
    if (KofWeb.isAppType(recvType)) {
        List<Type> webArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
        if (webCall != null) return webCall.returnType();
        return Type.UnknownType.UNKNOWN;
    }
    if (KofMedia.isHandleType(recvType)) {
        KofMedia.MediaCall mediaCall =
                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
        if (mediaCall != null) return mediaCall.returnType();
    }
    if (KofIo.isIoType(recvType)) {
        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (ioCall != null) return ioCall.returnType();
        if (KofIo.isIdentityMethod(mc.methodName())) return recvType;
    }
    if (recvType instanceof Type.FunctionType ft) {
        return ft.returnType();
    }
    if (CompilerTypes.isEnumType(recvType, driver.currentUnit) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        return BuiltinTypes.STRING;
    }
    if (BuiltinTypes.isList(recvType) || BuiltinTypes.isMap(recvType) || BuiltinTypes.isSet(recvType) || Type.isString(recvType)) {
        return CollectionMethodTyper.inferCollectionType(driver, recvType, mc, locals);
    }
} else if (driver.currentUnit != null) {
    IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
    if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
        return lft.returnType();
    }
    for (AstNode d : driver.currentUnit.declarations()) {
        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
            Type returnType = CompilerTypes.toType(fn.returnType(), driver.currentUnit);
            if (fn.typeParameters().contains(fn.returnType())) {
                returnType = new Type.TypeVariable(fn.returnType());
            }
            if (returnType instanceof Type.TypeVariable tv) {
                for (int pi = 0; pi < fn.parameters().size(); pi++) {
                    if (pi < mc.arguments().size() && tv.name().equals(fn.parameters().get(pi).type())) {
                        return ExpressionTyper.inferExprType(driver, mc.arguments().get(pi), locals);
                    }
                }
                return Type.UnknownType.UNKNOWN;
            }
            return returnType;
        }
    }
}
SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer.getResolvedMethod(mc);
if (resolvedMethod != null) {
    Type rt = resolvedMethod.returnType();
    if (rt instanceof Type.TypeVariable tv && mc.receiver() != null) {
        Type recvT = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
        Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
        if (subst != null) return subst;
    }
    return rt;
}
if (mc.receiver() != null) {
    Type recvT = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    // receiver nullable inferido (ex.: `var v = m.get(k)` → V?):
    // desempacota para a hierarquia — sem isso `instanceof ClassType`
    // falhava e o retorno do método saía `Object` (bug 33: o Map/Set era
    // só o caminho que produz o local nullable; W1 `var v = maybe()`
    // reproduz sem coleção). Espelha o unwrap da linha do handle acima.
    if (recvT instanceof Type.NullableType nt) recvT = nt.inner();
    if (recvT instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
        SymbolTable.Symbol m = driver.semanticAnalyzer.resolveInHierarchy(ct.name(), mc.methodName());
        if (m instanceof SymbolTable.MethodSymbol ms) {
            Type rt = ms.returnType();
            if (rt instanceof Type.TypeVariable tv) {
                Type subst = CompilerTypes.substituteTypeVariable(tv.name(), recvT, driver.currentUnit);
                if (subst != null) return subst;
            }
            return rt;
        }
    }
    if (recvT instanceof Type.ClassType) {
        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
        if (osig != null) return osig.returnType();
    }
}
SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
if (cs != null) return cs.type();
return Type.UnknownType.UNKNOWN;
    }
}
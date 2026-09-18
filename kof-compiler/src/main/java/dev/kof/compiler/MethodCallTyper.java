package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo de MethodCallExpr (parte do inferExprType).
 */
public final class MethodCallTyper {

    private MethodCallTyper() {}

    static Type inferType(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
// array: `.get(i)` → elemento, `.size/.length/.count` → int (o lowering
// emite arrayload/arraylength — sem isto o tipo Unknown vazava p/ o emit
// e o unbox de String.valueOf(Object) virava VerifyError, GitHub #30).
if (mc.receiver() != null
        && ExpressionTyper.inferExprType(driver, mc.receiver(), locals) instanceof Type.ArrayType at) {
    if ("get".equals(mc.methodName()) && mc.arguments().size() == 1) return at.componentType();
    if (("size".equals(mc.methodName()) || "length".equals(mc.methodName())
            || "count".equals(mc.methodName())) && mc.arguments().isEmpty()) return Type.PrimitiveType.INT;
    return Type.UnknownType.UNKNOWN;
}
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
// §89 (decisão 3a, 13/09): conversão numérica em receiver primitivo/unknown
// (String recebe dispatch próprio ANTES no lowering, sem colisão) = alias
// do `as` — o TIPO da expressão é o alvo da conversão, senão o `var d =
// n.toDouble()` fica Unknown e o EQ seguinte compara Object (dava false).
if (mc.receiver() != null && mc.arguments().isEmpty()
        && switch (mc.methodName()) {
            case "toInt", "toLong", "toFloat", "toDouble" -> true;
            default -> false;
        }) {
    Type rv89 = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    if (rv89 instanceof Type.NullableType nt89) rv89 = nt89.inner();
    if (TypeMetrics.isPrimitiveType(rv89) || rv89 instanceof Type.UnknownType) {
        return switch (mc.methodName()) {
            case "toInt" -> Type.PrimitiveType.INT;
            case "toLong" -> Type.PrimitiveType.LONG;
            case "toFloat" -> Type.PrimitiveType.FLOAT;
            default -> Type.PrimitiveType.DOUBLE;
        };
    }
}
if (mc.receiver() != null && "toString".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    Type rv = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    if (TypeMetrics.isPrimitiveType(rv) || rv instanceof Type.ArrayType) return BuiltinTypes.STRING;
}
// String.valueOf(x) / Integer.valueOf(x)…: receiver é o NOME
// do tipo builtin (estático). Sem tipo aqui o concat após um
// "s = s + String.valueOf(x)" aplicava box+valueOf duplicado
// no resultado (frame crash — 3 valueOf na pilha)
// #166: os `parse*` estáticos dos wrappers (`Int.parseInt`, `Long.parseLong`,
// `Double.parseDouble`, `Boolean.parseBoolean`) caíam no ramo de `valueOf`
// abaixo e tinham o retorno tipado como String → o JVM emitia
// `Integer.parseInt(...)Ljava/lang/String;` (NoSuchMethodError). O retorno é
// o primitivo correspondente; a sobrecarga com radix `(String, Int)` também.
// #233: mesma família — `Double.isNaN(d)`/`isInfinite`/`isFinite` (e Float)
// são estáticos JDK reais com retorno BOOL; sem isso o retorno caía em
// String e o JVM emitia `Double.isNaN(D)Ljava/lang/String;` (NoSuchMethodError).
if (mc.receiver() instanceof IdentifierExpr fpKid && driver.findLocalVar(fpKid.name(), locals) == null
        && switch (fpKid.name()) {
            case "Double", "Float" -> true;
            default -> false;
        }
        && switch (mc.methodName()) {
            case "isNaN", "isInfinite", "isFinite" -> true;
            default -> false;
        }
        && mc.arguments().size() == 1) {
    return Type.PrimitiveType.BOOL;
}
if (mc.receiver() instanceof IdentifierExpr srid && driver.findLocalVar(srid.name(), locals) == null
        && ("Double".equals(srid.name()) || "Float".equals(srid.name()))
        && ("isNaN".equals(mc.methodName()) || "isInfinite".equals(mc.methodName()) || "isFinite".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    return Type.PrimitiveType.BOOL;
}
if (mc.receiver() instanceof IdentifierExpr srid && driver.findLocalVar(srid.name(), locals) == null
        && switch (mc.methodName()) {
            case "parseInt", "parseLong", "parseDouble", "parseFloat", "parseBoolean" -> true;
            default -> false;
        }
        && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
    return switch (mc.methodName()) {
        case "parseInt" -> Type.PrimitiveType.INT;
        case "parseLong" -> Type.PrimitiveType.LONG;
        case "parseFloat" -> Type.PrimitiveType.FLOAT;
        case "parseBoolean" -> Type.PrimitiveType.BOOL;
        default -> Type.PrimitiveType.DOUBLE;
    };
}
if (mc.receiver() instanceof IdentifierExpr srid && mc.arguments().size() == 1
        && driver.findLocalVar(srid.name(), locals) == null
        && switch (srid.name()) {
            case "String", "Int", "Integer", "Long", "Float",
                    "Double", "Bool", "Boolean" -> true;
            default -> false;
        }) {
    return BuiltinTypes.STRING;
}
if (mc.receiver() == null && ("println".equals(mc.methodName()) || "print".equals(mc.methodName()))) return Type.PrimitiveType.VOID;
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
if (mc.receiver() == null && "Textarea".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.TEXTAREA;
}
if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName())
        || "Form".equals(mc.methodName()) || "Fieldset".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    if ("Form".equals(mc.methodName())) return KofUi.FORM;
    if ("Fieldset".equals(mc.methodName())) return KofUi.FIELDSET;
    return "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
}
if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.VIEW;
}
if (mc.receiver() == null && "RawView".equals(mc.methodName()) && mc.arguments().size() == 4) {
    return KofUi.RAW_VIEW;
}
if (mc.receiver() == null && "Iframe".equals(mc.methodName()) && mc.arguments().size() == 1) {
    return KofUi.IFRAME;
}
if (mc.receiver() == null && ("Video".equals(mc.methodName()) || "Audio".equals(mc.methodName()))
        && mc.arguments().size() == 1) {
    return "Video".equals(mc.methodName()) ? KofUi.VIDEO : KofUi.AUDIO;
}
if (mc.receiver() == null && "Hr".equals(mc.methodName()) && mc.arguments().isEmpty()) {
    return KofUi.HR;
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
if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 1) {
    // D-UI-STYLE (UI007): declarative form, parsed in the compiler (Q4).
    return KofUi.STYLE;
}
// D-UI-STYLE (UI007): Style("<declarations>") — parse/validate in the
// compiler (Q4/Q3); the lowering carries the normalized CSS.
if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 1) {
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
// #149: resultado de List.map/filter/reduce precisa de tipo REAL — cair em
// Unknown fazia `r[i]` sobre o resultado emitir AALOAD (o typer do
// ArrayAccessExpr não sabia que é List) e `r.get(i)`/`r.size` perdiam o
// dispatch de coleção. Espelha o emit do CollectionCallLowerer.
if (mc.receiver() != null
        && ("map".equals(mc.methodName()) || "filter".equals(mc.methodName())
            || "reduce".equals(mc.methodName()))
        && ExpressionTyper.inferExprType(driver, mc.receiver(), locals) instanceof Type.ClassType lt
        && "kof".equals(lt.packageName()) && "List".equals(lt.name())) {
    if ("filter".equals(mc.methodName())) return lt;
    if (mc.arguments().isEmpty()) return Type.UnknownType.UNKNOWN;
    Type lamT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
    if ("map".equals(mc.methodName())) {
        Type elem = (lamT instanceof Type.FunctionType ft && !(ft.returnType() instanceof Type.UnknownType))
                ? ft.returnType() : Type.UnknownType.UNKNOWN;
        return new Type.ClassType("kof", "List", List.of(elem));
    }
    return (lamT instanceof Type.FunctionType ft) ? ft.returnType() : Type.UnknownType.UNKNOWN;
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
    ExpressionNode body = mc.arguments().get(0);
    // bug 46: `spawn { return 42 }` — o type-arg do Handle é o RETURN da
    // lambda, não o FunctionType (inferExprType(lambda) dá FunctionType([],Int)).
    // Sem o unwrap, `await h` devolve FunctionType → println vira String →
    // deref inválido (SIGSEGV nativo). Espelha ExpressionStaticCallLowerer.
    Type t = body instanceof LambdaExpr le
            ? ExpressionTyper.inferLambdaBodyType(driver, le, locals)
            : ExpressionTyper.inferExprType(driver, body, locals);
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
Type nsType = MethodCallNamespaces.inferStatic(driver, mc, locals);
if (nsType != null) return nsType;
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
    if (mc.receiver() instanceof IdentifierExpr rid && KofStd.isStdNamespace(rid.name())) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofStd.StdCall sCall = KofStd.staticMethod(rid.name(), mc.methodName(), argTypes);
        if (sCall != null) return sCall.returnType();
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
    if (CompilerTypes.isEnumType(recvType, driver.currentUnit)) {
        if (("name".equals(mc.methodName()) || "toString".equals(mc.methodName())) && mc.arguments().isEmpty()) {
            return BuiltinTypes.STRING;
        }
        if ("ordinal".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            return Type.PrimitiveType.INT;
        }
        if ("compareTo".equals(mc.methodName()) && mc.arguments().size() == 1) {
            return Type.PrimitiveType.INT;
        }
    }
    if (BuiltinTypes.isList(recvType) || BuiltinTypes.isMap(recvType) || BuiltinTypes.isSet(recvType) || Type.isString(recvType)) {
        return CollectionMethodTyper.inferCollectionType(driver, recvType, mc, locals);
    }
} else if (driver.currentUnit != null) {
    IRLocalVariable lambdaVar = driver.findLocalVar(mc.methodName(), locals);
    if (lambdaVar != null && lambdaVar.type() instanceof Type.FunctionType lft) {
        return lft.returnType();
    }
    // SG-011B: coletar candidatos homônimos; único → caminho original
    // (genéricos incluídos); ≥2 → seleção por assinatura (mesmo veredicto do
    // BuiltinCallTyper/lowering — frontend único, mesma mensagem nos 5 targets).
    List<FunctionDeclarationNode> tloFns = new ArrayList<>();
    for (AstNode d : driver.currentUnit.declarations()) {
        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) tloFns.add(fn);
    }
    List<TopLevelOverload.Candidate> tloCands = new ArrayList<>();
    if (tloFns.size() > 1) {
        List<Type> tloArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) tloArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        for (FunctionDeclarationNode fn : tloFns) {
            if (!fn.typeParameters().isEmpty()) continue;
            List<Type> pt = new ArrayList<>();
            for (var p : fn.parameters()) pt.add(CompilerTypes.toType(p.type(), driver.currentUnit));
            // §231: requiredArity = índice do 1º default (espelho dos wrappers de
            // lowerFunctionDefaults) — antes era pt.size(), o que fazia o pick
            // NUNCA escolher o candidato com default numa chamada curta (a
            // chamada caía em SEM013/SEM014). Frontend único com o lowering e o
            // BuiltinCallTyper: mesma seleção nos 3 sítios.
            tloCands.add(new TopLevelOverload.Candidate(fn, pt, TopLevelOverload.requiredArityOf(fn)));
        }
        TopLevelOverload.Status[] st = new TopLevelOverload.Status[1];
        int sel = TopLevelOverload.pick(tloCands, tloArgTypes, st);
        if (sel >= 0) tloFns = List.of(tloCands.get(sel).fn());
        else tloFns = List.of(tloFns.get(0)); // erro já reportado no typer semântico (SEM013/14/56)
    }
    for (FunctionDeclarationNode fn : tloFns) {
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
SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer != null
        ? driver.semanticAnalyzer.getResolvedMethod(mc) : null;
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
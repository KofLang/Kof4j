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
int handledStatic = ExpressionStaticCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
if (handledStatic >= 0) return handledStatic;
if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
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
    return ExpressionJsonCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())) {
    return ExpressionDbCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && KofOrm.isOrmNamespace(rid.name())) {
    return ExpressionOrmCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofLog.isLogNamespace(rid.name())) {
    return ExpressionLogCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null) {
    return ExpressionProcessCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofHttp.isHttpNamespace(rid.name())) {
    return ExpressionHttpCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofTime.isTimeNamespace(rid.name())) {
    return ExpressionTimeCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
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
    return ExpressionSchedulerCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofMq.isMqNamespace(rid.name())) {
    return ExpressionMqCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofConfig.isConfigNamespace(rid.name())) {
    return ExpressionConfigCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofCache.isCacheNamespace(rid.name())) {
    return ExpressionCacheCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
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
} else if (mc.receiver() instanceof IdentifierExpr uimrid
        && (KofIo.isConstructor(uimrid.name())
            || KofMedia.isStaticNamespace(uimrid.name())
            || KofUi.isPalette(uimrid.name())
            || KofUi.isConstructor(uimrid.name())
            || KofUi.isRouterNamespace(uimrid.name()))) {
    return ExpressionUiMediaCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() != null) {
    return ExpressionInstanceCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
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
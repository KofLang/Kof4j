package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;


/**
 * Lowering de MethodCallExpr (case do emitExpression).
 */
public final class ExpressionMethodCallLowerer {

    private ExpressionMethodCallLowerer() {}

    /** Diagnóstico de gap (XXX00x) — posição da chamada, mensagem e código prontos. */
    private static void gapError(CompilerDriver driver, MethodCallExpr mc, String msg, String code) {
        if (driver.currentDiagnostics == null) return;
        SourcePosition p = mc.position();
        driver.currentDiagnostics.error(p != null ? p.file() : "",
                p != null ? p.line() : 0, p != null ? p.column() : 0, 0, msg, code);
    }

    /**
     * #403 — o nome do receiver sombreia um CAMPO da classe corrente? A
     * semântica já resolve `log` como campo (SemExpressionTyper:63, antes da
     * isenção de namespace); se o EMIT hijackear o nome cru p/ um lowerer de
     * namespace (log/json/db/…) o corpo da chamada sai VAZIO quando o método
     * não está mapeado (`log.add(...)` → ExpressionLogCallLowerer engole →
     * `return` só → VerifyError: Operand stack underflow no load, R6/silencioso).
     * Espelha a resolução por owner de ExpressionLowerer:72 (getfield do campo).
     */
    static boolean shadowsFieldOfCurrentClass(CompilerDriver driver, String owner, String name) {
        if (driver.semanticAnalyzer == null || owner == null || owner.isEmpty()) return false;
        String className = owner.substring(owner.lastIndexOf('/') + 1);
        if (className.isEmpty()) return false;
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(className);
        if (cs == null) return false;
        return HierarchyResolver.resolveFieldInHierarchy(cs.name(), name, driver.semanticAnalyzer)
                instanceof SymbolTable.FieldSymbol;
    }

    /**
     * #786: `db` is both the KofDb namespace and a name the documented idiom
     * `var db = db.connect(...)` / `db.execute(db, ...)` binds — the handle is a
     * String (`KofDb.staticCall` returns `STR`). A local/parameter named `db` of
     * ANY OTHER type is an ordinary receiver (`db.next()` on a user class), and
     * routing it to the db namespace made the call vanish: `KofDb.staticCall`
     * returns null and `ExpressionDbCallLowerer` emits nothing (R6/Q7 silent
     * drop → frame crash). Only a handle-typed local keeps the namespace, so the
     * idiom is untouched.
     */
    static boolean shadowsDbNamespaceWithForeignLocal(CompilerDriver driver, String name,
                                                      List<IRLocalVariable> locals) {
        IRLocalVariable lv = driver.findLocalVar(name, locals);
        return lv != null && !BuiltinTypes.STRING.equals(lv.type());
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
// User-defined classes take precedence over builtin helpers
int handledStatic = ExpressionStaticCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
if (handledStatic >= 0) return handledStatic;
if (mc.receiver() == null && driver.externSignatures.containsKey(mc.methodName())) {
    // Face extern/FFI extraída (ExpressionExternCallLowerer); -1 = binding
    // inativo — cai no resto da cadeia exatamente como o corpo original caía.
    int handledExtern = ExpressionExternCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    if (handledExtern >= 0) return handledExtern;
}
// entity-history fatia 2c-parte-2 (D-ENTITY-HISTORY): a consulta
    // `Entity.history(db, id[, offset, limit])` é RESERVADA de entity —
    // vem ANTES da resolução estática genérica de classes (o histórico não
    // é método da classe; HIST002 fala em nome dela).
    if (mc.receiver() instanceof IdentifierExpr ehRid
            && driver.entityNamesMirror.contains(ehRid.name())
            && (("history".equals(mc.methodName())
                    && (mc.arguments().size() == 2 || mc.arguments().size() == 3
                        || mc.arguments().size() == 4))
                || "at".equals(mc.methodName())
                || "atTime".equals(mc.methodName()) || "changes".equals(mc.methodName()))) {
        return ExpressionEntityHistoryLowerer.lower(driver, mc, ops, owner,
                localIdx, locals, ehRid.name());
    }
if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && driver.semanticAnalyzer != null
        && driver.semanticAnalyzer.getClass(rid.name()) != null) {
    // Metodo ESTATICO de classe KOF de outro pacote:
    // Desconto.aplicar(c) -> invokestatic vendas/regras/Desconto.aplicar
    SymbolTable.MethodSymbol ksm = null;
    SymbolTable.Symbol ks = driver.semanticAnalyzer.resolveInHierarchy(rid.name(), mc.methodName());
    if (ks instanceof SymbolTable.MethodSet set) {
        List<Type> argTypes0 = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes0.add(ExpressionTyper.inferExprType(driver, arg, locals));
        ksm = set.select(mc.arguments().size(), argTypes0);
    } else if (ks instanceof SymbolTable.MethodSymbol ms0
            && ms0.parameterTypes().size() == mc.arguments().size()) {
        ksm = ms0;
    }
    if (ksm != null) {
        SymbolTable.ClassSymbol kt = driver.semanticAnalyzer.getClass(rid.name());
        // #258: `Calc.instanceMethod(x)` NAO é estática — o dispatcher anterior
        // emitia KofCallKind.STATIC sempre → invokestatic sem `this` →
        // IncompatibleClassChangeError em runtime. Regra (espelha o Java oracle):
        // só vale como chamada de instância DENTRO de um método de instância da
        // MESMA classe (equivale a `this.method(x)`). Fora disso (contexto
        // estático, ou classe diferente) é erro honesto SEM060, nunca invokestatic
        // silencioso (R6). Chamada REALMENTE estática mantém o caminho de antes.
        boolean calleeStatic = (ksm.accessFlags() & AccessFlags.STATIC) != 0;
        IRLocalVariable thisVar = calleeStatic ? null : driver.findLocalVar("this", locals);
        boolean sameClass = thisVar != null && owner != null
                && owner.substring(owner.lastIndexOf('/') + 1).equals(rid.name());
        if (!calleeStatic && !sameClass) {
            if (driver.currentDiagnostics != null) {
                SourcePosition p = mc.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "cannot call instance method '" + rid.name() + "." + mc.methodName()
                                + "()' without a receiver — use 'this." + mc.methodName()
                                + "()' inside an instance method of '" + rid.name()
                                + "' or call it on an instance (method() is not static)",
                                "SEM060");
            }
            return localIdx;
        }
        if (!calleeStatic) {
            // this.method(x): empilha `this` (slot 0 do método de instância) e
            // baixa como INSTANCE — o back-end JVM faz invokevirtual, o
            // interpretador/JS/Native despacham pela classe do receiver (polimorfismo real).
            ops.add(new KofLoadLocal(thisVar.type(), thisVar.index()));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ksm.parameterTypes(),
                    ops, owner, localIdx, locals);
            ops.add(new KofCall(kt.type(), mc.methodName(), ksm.parameterTypes(),
                    ksm.returnType(), KofCallKind.INSTANCE));
            return localIdx;
        }
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ksm.parameterTypes(),
                ops, owner, localIdx, locals);
        ops.add(new KofCall(kt.type(), mc.methodName(), ksm.parameterTypes(),
                ksm.returnType(), KofCallKind.STATIC));
        return localIdx;
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && CompilerTypes.qualifyViaImports(rid.name(), driver.currentUnit,
                driver.externalClasspath) instanceof Type.ClassType extQ
        && !extQ.packageName().isEmpty()
        && driver.externalClasspath.knows(extQ.internalName())
        && driver.externalClasspath.resolveMethod(extQ.internalName(), mc.methodName(),
                mc.arguments().size()) != null) {
    // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
    // estático, interface externa ou instância — resolve pelo
    // classpath ANTES dos namespaces builtin (Button também é
    // widget do kof.ui; o import decide). Local sombreia.
    // §500: resolveMethod agora enxerga ACC_VARARGS e o packing é
    // único (VarargsArrayPacker) — `Arrays.asList(1,2)` empacota
    // Object[] e sai invokestatic com o descritor REAL, nunca mais
    // o dono vazio `"".asList:(II)`.
    // #760: an imported EXTERNAL class used as a static-method receiver
    // (Runtime.getRuntime(), Arrays.asList(...)) is a JVM-backed face; JS/Native
    // must refuse with INTEROP003 instead of leaking the java_* call. The
    // wrapper owners keep their shims.
    if (JvmInteropTargetGap.refuses(driver.target)
            && !JvmInteropTargetGap.isShimmedOwner(extQ.internalName())) {
        JvmInteropTargetGap.refuse(driver, mc.position(),
                "static method '" + mc.methodName() + "()' of external class '"
                        + extQ.internalName().replace('/', '.') + "'");
        return localIdx;
    }
    ExternalClasspath.MethodSignature extSig = driver.externalClasspath.resolveMethod(
            extQ.internalName(), mc.methodName(), mc.arguments().size());
    List<Type> extFormal = VarargsArrayPacker.formalTypes(extSig);
    Type extRet = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
    localIdx = VarargsArrayPacker.lower(driver, mc.arguments(), extSig,
            ops, owner, localIdx, locals);
    KofCallKind extKind = extSig.isStatic() ? KofCallKind.STATIC
            : (extSig.ownerIsInterface() ? KofCallKind.INTERFACE
            : KofCallKind.INSTANCE);
    ops.add(new KofCall(extQ, mc.methodName(), extFormal, extRet, extKind));
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && ("Double".equals(rid.name()) || "Float".equals(rid.name()) || "Long".equals(rid.name())
            || "Integer".equals(rid.name()) || "Int".equals(rid.name()) || "Boolean".equals(rid.name())
            || "Bool".equals(rid.name()) || "String".equals(rid.name())
            || "Char".equals(rid.name()) || "Character".equals(rid.name())
            || "Byte".equals(rid.name()) || "Short".equals(rid.name()))) {
    // #156/#216: String.format(String, Object...) é varargs do JDK — o
    // classpath externo só casa name+arity e o descritor fabricado
    // `(String,String,int)Object` dava NoSuchMethodError. Ver
    // StringFormatCallLowerer (packing em Object[] + descritor real).
    if ("String".equals(rid.name()) && "format".equals(mc.methodName())
            && StringFormatCallLowerer.matches(driver, mc, locals)) {
        return StringFormatCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    }
    if ("toString".equals(mc.methodName()) && mc.arguments().size() == 1
            && wrapperArgMatches(rid.name(), ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals))) {
        return lower(driver, new MethodCallExpr(mc.position(), new IdentifierExpr(rid.position(), "String"),
                "valueOf", List.of(), mc.arguments()), ops, owner, localIdx, locals);
    }
    String javaClass = switch (rid.name()) {
        case "Int", "Integer" -> "java/lang/Integer";
        case "Long" -> "java/lang/Long";
        case "Float" -> "java/lang/Float";
        case "Double" -> "java/lang/Double";
        case "Bool", "Boolean" -> "java/lang/Boolean";
        case "Char", "Character" -> "java/lang/Character";
        case "Byte" -> "java/lang/Byte";
        case "Short" -> "java/lang/Short";
        default -> "java/lang/String";
    };
    List<Type> actualArgTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        actualArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
    ExternalClasspath.MethodSignature extSig = driver.externalClasspath
            .resolveMethodWithArgs(javaClass, mc.methodName(), mc.arguments().size(), actualArgTypes);
    if (extSig != null) {
        // §686: `String.valueOf(contêiner)` no alvo nativo — a assinatura JDK
        // `valueOf(Object)` apaga o tipo e o emissor imprimia o PONTEIRO cru;
        // o tipo estático Kof volta aqui (NativeValueOfArgs). JVM/JS/Script
        // seguem a assinatura do JDK.
        List<Type> extFormal = NativeValueOfArgs.recover(driver, mc,
                VarargsArrayPacker.formalTypes(extSig), locals);
        Type extRet = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
        // §500: `String.join(CharSequence, CharSequence...)` etc. — os args
        // finais empacotam no array do tipo componente (descritor REAL),
        // não 3 strings cruas contra 2 formais (VerifyError).
        localIdx = VarargsArrayPacker.lower(driver, mc.arguments(), extSig,
                ops, owner, localIdx, locals);
        KofCallKind extKind = extSig.isStatic() ? KofCallKind.STATIC : KofCallKind.INSTANCE;
        ops.add(new KofCall(new Type.ClassType("java.lang", javaClass.substring(javaClass.lastIndexOf('/') + 1), List.of()),
                mc.methodName(), extFormal, extRet, extKind));
        return localIdx;
    } else if (mc.arguments().size() == 1
            && ("isNaN".equals(mc.methodName()) || "isInfinite".equals(mc.methodName()) || "isFinite".equals(mc.methodName()))
            && ("Double".equals(rid.name()) || "Float".equals(rid.name()))) {
        Type argType = "Double".equals(rid.name()) ? Type.PrimitiveType.DOUBLE : Type.PrimitiveType.FLOAT;
        List<Type> extFormal = List.of(argType);
        Type extRet = Type.PrimitiveType.BOOL;
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), extFormal, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("java.lang", javaClass.substring(javaClass.lastIndexOf('/') + 1), List.of()),
                mc.methodName(), extFormal, extRet, KofCallKind.STATIC));
        return localIdx;
    }
    // #233 regression (found by lane bugs-and-gaps): when the classpath is
    // present but the wrapper class is NOT in it (or the method does not
    // resolve), this branch used to fall out and emit NOTHING — the call was
    // silently dropped (R6) and the enclosing expression broke (JVM frame
    // crash on the outer valueOf with a missing argument). Fall back to the
    // instance lowerer, which owns the builtin wrapper/`valueOf` handling.
    return ExpressionInstanceCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && "json".equals(rid.name())
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionJsonCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && KofDb.isDbNamespace(rid.name())
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    if (shadowsDbNamespaceWithForeignLocal(driver, rid.name(), locals)) {
        return ExpressionInstanceCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    }
    return ExpressionDbCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && KofOrm.isOrmNamespace(rid.name())
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionOrmCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofLog.isLogNamespace(rid.name())
            && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionLogCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionProcessCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && "shell".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionShellCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && "ssh".equals(rid.name())
        && driver.findLocalVar(rid.name(), locals) == null
        && !shadowsFieldOfCurrentClass(driver, owner, rid.name())) {
    return ExpressionSshCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofHttp.isHttpNamespace(rid.name())) {
    return ExpressionHttpCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofTime.isTimeNamespace(rid.name())) {
    return ExpressionTimeCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofScheduler.isSchedulerNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerScheduler(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName())) {
    return ExpressionSchedulerCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    // #108 (opção 1, 13/09): `sleep(ms)` sem receiver = `time.sleep(ms)`.
    // Reusa o lowerer do namespace (emite kof_time_sleep) — espelha o
    // `now()` sem receiver do ExpressionStaticCallLowerer. Guarda anti-
    // sombreamento igual à do typer (MethodCallNamespaces).
} else if (mc.receiver() == null && "sleep".equals(mc.methodName())
        && driver.findLocalVar("sleep", locals) == null) {
    List<Type> sleepArgTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) sleepArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    if (KofTime.staticCall("sleep", sleepArgTypes) != null) {
        return ExpressionTimeCallLowerer.lowerSleep(driver, mc, ops, owner, localIdx, locals);
    }
    // sem match de aridade: cai no fluxo normal (SEM015 honesto)
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
    return ExpressionNamespaceCallLowerer.lowerGpu(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofSecurity.isSecurityNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerSecurity(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofValidation.isValidationNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerValidation(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && KofStd.isStdNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        // narrowing de null-safety: `if (q != null) { math.parseInt(q) }`
        // — o frontend estreita `q: String?` para `String` no escopo do
        // ramo, mas o lowerer lê os IR-locals (ainda Nullable); sem o
        // unwrap o `staticMethod` devolve null, o `if (sCall != null)`
        // abaixo NÃO emite nada e o IR fica sem o call → frame crash
        // COMP002 (Q7: chamada sumida). Espelha o unwrap de receiver no
        // ramo de instance call (`ExpressionInstanceCallLowerer`).
        Type at = ExpressionTyper.inferExprType(driver, arg, locals);
        if (at instanceof Type.NullableType nt) at = nt.inner();
        argTypes.add(at);
    }
    KofStd.StdCall sCall = KofStd.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (sCall != null) {
        if (!KofStd.supportedOn(sCall, driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName() + ": not available on the "
                    + driver.target + " target yet (" + KofStd.gapCode(sCall, driver.target) + ")",
                    KofStd.gapCode(sCall, driver.target));
            return localIdx;
        }
        // The math constants are compile-time doubles: fold to a literal rather
        // than emit kof_math_pi/e/tau, a symbol only the JVM runtime defines —
        // a call that type-checked everywhere but died at ld on every native
        // target (mislabeled COMP001). Byte-identical, reuses the literal path.
        Double mathConst = KofMath.constantValue(sCall.function());
        if (mathConst != null) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, mathConst));
            return localIdx;
        }
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), sCall.parameterTypes(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType(sCall.ownerPackage(), sCall.ownerClass(), List.of()),
                sCall.function(), sCall.parameterTypes(), sCall.returnType(),
                KofCallKind.FUNCTION));
    } else {
        // §607/#770 face R6 (Q7): nenhum match de namespace = divergencia
        // sem x lowering; o corpo VAZIO era o drop silencioso que deixava o
        // frame incompleto (COMP002). Diagnostico honesto na linha da decl.
        gapError(driver, mc, "no lowering for '" + rid.name() + "." + mc.methodName()
                + "()' with argument types " + argTypes, "SEM025");
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && KofObservability.isObservabilityNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerObservability(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofTetris.isTetrisNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerTetris(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
        && KofImage.isImageNamespace(rid.name())) {
    return ExpressionNamespaceCallLowerer.lowerImage(driver, mc, rid, ops, owner, localIdx, locals);
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofWeb.isWebNamespace(rid.name())) {
    if ("app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        // AND002 (docs/targets/KOFANDROID.md): app móvel não escuta porta —
        // o servidor embutido (web.app) não tem realização no Android; o
        // alvo diz na hora (R6), nunca silencia.
        if (driver.target == Target.ANDROID) {
            gapError(driver, mc, "web.app: embedded server not available on Android — "
                    + "a mobile app does not listen on a port; use interop (AND002)", "AND002");
            return localIdx;
        }
        // WEB001-T1 JS (13/09): web.app() liberado — o runtime JS tem server
        // real (JsRuntimeUiWeb: kofWebAppNew/Route/Listen via GraalJS
        // HttpServer); o gap real era o frontend bloquear o JS aqui.
        if (driver.target != Target.JVM
                && driver.target != Target.NATIVE
                && driver.target != Target.NATIVE_RISCV64
                && driver.target != Target.NATIVE_AARCH64
                && driver.target != Target.JS) {
            gapError(driver, mc, "web: not available on the " + driver.target
                    + " driver.target yet (WEB001)", "WEB001");
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
            || KofUiTokens.isTokenNamespace(uimrid.name())
            || KofUi.isConstructor(uimrid.name())
            || KofUi.isRouterNamespace(uimrid.name()))) {
    return ExpressionUiMediaCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else if (mc.receiver() != null) {
    return ExpressionInstanceCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
} else {
    return ExpressionBareCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
}
return localIdx;
    }

    private static int lowerEntityHistory(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx,
            List<IRLocalVariable> locals, String entityName) {
        return ExpressionEntityHistoryLowerer.lower(driver, mc, ops, owner,
                localIdx, locals, entityName);
    }


    private static boolean wrapperArgMatches(String wrapper, Type argType) {
        if (!(argType instanceof Type.PrimitiveType)) return false;
        String prim = TypeMetrics.primitiveName(argType);
        return switch (wrapper) {
            case "Int", "Integer" -> "int".equals(prim) || "Int".equals(prim);
            case "Long" -> "long".equals(prim) || "Long".equals(prim);
            case "Double" -> "double".equals(prim) || "Double".equals(prim);
            case "Float" -> "float".equals(prim) || "Float".equals(prim);
            case "Bool", "Boolean" -> "boolean".equals(prim) || "bool".equals(prim) || "Bool".equals(prim);
            default -> false;
        };
    }
}
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

    /** Emite todos os argumentos da chamada, devolvendo o localIdx atualizado. */
    private static int emitArgs(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        return localIdx;
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

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                        String owner, int localIdx, List<IRLocalVariable> locals) {
// User-defined classes take precedence over builtin helpers
int handledStatic = ExpressionStaticCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
if (handledStatic >= 0) return handledStatic;
if (mc.receiver() == null && driver.externSignatures.containsKey(mc.methodName())) {
    ExternalFunctionNode ext = driver.externSignatures.get(mc.methodName());
    if (CompilerFfiBinding.isExternBound(driver, ext)) {
        // #431/§61 (Native): ABI escalar DIRETA — os args ficam crus na pilha de
        // operandos (mesma convenção push dos calls internos) e o backend emite o
        // marshaling SysV + `call sym@PLT` (precedente: consumidor SQLite/DB001).
        // O KofCall carrega owner=kof.ffi + methodName "lib::simbolo" como
        // metadado — nenhum lowering de usuário gera "::" num nome de método.
        if (driver.target.isNative()) {
            // #431 fatia 2: o mesmo KofCall "lib::simbolo" serve as 3 archs —
            // o backend escolhe o shim (x86 SysV / riscv LP64 / aarch AAPCS64
            // via tradução do texto riscv).
            List<Type> ffiParams = new java.util.ArrayList<>();
            for (var p : ext.parameters()) {
                Type scalar = FfiSignature.paramType(p.type());
                if (scalar != null) {
                    ffiParams.add(scalar);
                } else {
                    // 3.7: `record` por valor — o Type carrega os campos p/ o
                    // backend montar os registradores sem precisar do driver.
                    String fc = FfiSignature.structFieldChars(p.type(), driver);
                    if (fc != null) {
                        ffiParams.add(FfiStructLayout.structTypeOfChars(fc));
                    } else {
                        // D6-2/3.7: array escalar `T[]`→`ptr` (marker kof.ffi/array).
                        Character ae = FfiSignature.arrayElemChar(p.type());
                        ffiParams.add(ae != null ? FfiStructLayout.arrayPtrType(ae) : null);
                    }
                }
            }
            for (int i = 0; i < mc.arguments().size(); i++) {
                ExpressionNode arg = mc.arguments().get(i);
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                // #549/§370: o marshaling SysV lê os bits pela classe do SLOT — o
                // argumento chega já convertido ao tipo declarado (regra comum).
                ExternArgumentCoercion.coerce(driver, arg, i < ffiParams.size() ? ffiParams.get(i) : null, ops, locals);
            }
            // 3.7 fatia 2: retorno `record` por valor — o KofCall carrega o
            // ClassType REAL do record (resolução de `.campo()` a jusante e o
            // backend materializa o objeto a partir do registrador/scratch).
            Type ffiRet = FfiSignature.returnType(ext.returnType());
            if (FfiSignature.returnChar(ext.returnType()) == null) {
                Type st = FfiSignature.structReturnType(ext.returnType(), driver);
                if (st != null) ffiRet = st;
            }
            ops.add(new KofCall(new Type.ClassType("kof", "ffi", List.of()),
                    ext.library() + "::" + ext.name(), ffiParams,
                    ffiRet, KofCallKind.FUNCTION));
            return localIdx;
        }
        // FFI (R3, generalizado): kof_ffi(lib, nome, sig, Object[] args).
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type objectArray = new Type.ArrayType(object);
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                ext.library() != null ? ext.library() : ""));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ext.name()));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                FfiSignature.signature(driver, ext)));
        int n = mc.arguments().size();
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, n));
        ops.add(new KofNewArray(object));
        for (int i = 0; i < n; i++) {
            ops.add(new KofDup());
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, i));
            ExpressionNode arg = mc.arguments().get(i);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type argType = ExpressionTyper.inferExprType(driver, arg, locals);
            // #549/§370: `Double→Float`/`Int→Float`… viram a conversão real ANTES do
            // box — o `kof_ffi` faz cast pelo wrapper do slot (Float), nunca reinterpreta.
            Type slot = i < ext.parameters().size() ? FfiSignature.paramType(ext.parameters().get(i).type()) : null;
            if (ExternArgumentCoercion.coerce(driver, arg, slot, ops, locals)) argType = slot;
            if (argType instanceof Type.PrimitiveType) {
                TypeEmitter.boxPrimitive(ops, argType);
            }
            ops.add(new KofArrayStore(object));
        }
        String ffiHelper = FfiSignature.isVoidFFI(ext.returnType())
                ? "kof_ffi_void" : "kof_ffi";
        ops.add(new KofCall(new Type.ClassType("kof", "ffi", List.of()), ffiHelper,
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING, BuiltinTypes.STRING, objectArray),
                FfiSignature.callReturnType(driver, ext), KofCallKind.FUNCTION));
        return localIdx;
    }
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
            || "Bool".equals(rid.name()) || "String".equals(rid.name()))) {
    // #156/#216: String.format(String, Object...) é varargs do JDK — o
    // classpath externo só casa name+arity e o descritor fabricado
    // `(String,String,int)Object` dava NoSuchMethodError. Ver
    // StringFormatCallLowerer (packing em Object[] + descritor real).
    if ("String".equals(rid.name()) && "format".equals(mc.methodName())
            && StringFormatCallLowerer.matches(driver, mc, locals)) {
        return StringFormatCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
    }
    String javaClass = switch (rid.name()) {
        case "Int", "Integer" -> "java/lang/Integer";
        case "Long" -> "java/lang/Long";
        case "Float" -> "java/lang/Float";
        case "Double" -> "java/lang/Double";
        case "Bool", "Boolean" -> "java/lang/Boolean";
        default -> "java/lang/String";
    };
    List<Type> actualArgTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        actualArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
    ExternalClasspath.MethodSignature extSig = driver.externalClasspath
            .resolveMethodWithArgs(javaClass, mc.methodName(), mc.arguments().size(), actualArgTypes);
    if (extSig != null) {
        List<Type> extFormal = VarargsArrayPacker.formalTypes(extSig);
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
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofScheduler.SchedulerCall schedCall = KofScheduler.staticCall(mc.methodName(), argTypes);
    if (schedCall != null) {
        if (!KofScheduler.supportedOn(schedCall.function(), driver.target)) {
            String code = KofScheduler.gapCode(schedCall.function());
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + code + ")", code);
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(KofScheduler.SCHEDULER, schedCall.function(), schedCall.parameterTypes(),
                schedCall.returnType(), KofCallKind.FUNCTION));
    }
    return localIdx;
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
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (GPU001)", "GPU001");
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
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
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofSecurity.gapCode(secCall.function()) + ")",
                    KofSecurity.gapCode(secCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
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
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofValidation.gapCode(vCall.function()) + ")",
                    KofValidation.gapCode(vCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.validation", "Validation", List.of()),
                vCall.function(), vCall.parameterTypes(), vCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
} else if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
            && KofStd.isStdNamespace(rid.name())) {
    List<Type> argTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    KofStd.StdCall sCall = KofStd.staticMethod(rid.name(), mc.methodName(), argTypes);
    if (sCall != null) {
        if (!KofStd.supportedOn(sCall, driver.target)) {
            gapError(driver, mc, rid.name() + "." + mc.methodName() + ": not available on the "
                    + driver.target + " target yet (" + KofStd.gapCode(sCall, driver.target) + ")",
                    KofStd.gapCode(sCall, driver.target));
            return localIdx;
        }
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), sCall.parameterTypes(), ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType(sCall.ownerPackage(), sCall.ownerClass(), List.of()),
                sCall.function(), sCall.parameterTypes(), sCall.returnType(),
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
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofObservability.gapCode(oCall.function()) + ")",
                    KofObservability.gapCode(oCall.function()));
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
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
            gapError(driver, mc, rid.name() + "." + mc.methodName()
                    + ": not available on the " + driver.target
                    + " driver.target yet (" + KofTetris.gapCode() + ")",
                    KofTetris.gapCode());
            return localIdx;
        }
        localIdx = emitArgs(driver, mc, ops, owner, localIdx, locals);
        ops.add(new KofCall(new Type.ClassType("kof.tetris", "Tetris", List.of()),
                tetrisCall.function(), tetrisCall.parameterTypes(), tetrisCall.returnType(),
                KofCallKind.FUNCTION));
    }
    return localIdx;
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
}
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
    return ExpressionDbCallLowerer.lower(driver, mc, ops, owner, localIdx, locals);
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
    if (BuiltinTypes.isList(recvType) || BuiltinTypes.isChannel(recvType)
            || BuiltinTypes.isMap(recvType) || BuiltinTypes.isSet(recvType)) {
        int handled = CollectionCallLowerer.lower(driver, recvType, mc, ops, owner, localIdx, locals);
        if (handled >= 0) return handled;
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
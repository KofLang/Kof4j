package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de chamadas de método COM receiver (membros de classe,
 * super, classes externas, namespaces kof.*), extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Retorna null quando nenhuma regra se aplica.
 */
final class MemberCallTyper {

    private MemberCallTyper() {}

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null) return null;
        if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
            KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
            if (uiCall != null) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                return uiCall.returnType();
            }
        }
        if (mc.receiver() instanceof IdentifierExpr ridR && KofUi.isRouterNamespace(ridR.name())) {
            KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
            if (routerCall != null) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                return routerCall.returnType();
            }
        }
        // Nome de CLASSE KOF (de qualquer pacote do modulo) como
        // receiver para metodo ESTATICO: Desconto.aplicar(c)
        if (mc.receiver() instanceof IdentifierExpr krid
                && !ExpressionTyper.isLocalName(scope, krid.name())
                && sa.allClasses().containsKey(krid.name())) {
            SymbolTable.Symbol km = MemberResolver.resolveInHierarchy(sa, krid.name(), mc.methodName());
            if (km instanceof SymbolTable.MethodSymbol kms
                    && kms.parameterTypes().size() == mc.arguments().size()) {
                SymbolTable.ClassSymbol kt = sa.allClasses().get(krid.name());
                sa.resolvedMethods().put(mc, new SymbolTable.MethodSymbol(
                        kms.name(), kt.internalName(), kms.returnType(),
                        kms.parameterTypes(), kms.accessFlags(),
                        SymbolTable.DispatchKind.STATIC));
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), inferArgTypes(sa, mc, scope), kms.parameterTypes());
                return kms.returnType();
            }
        }
        // Nome de CLASSE EXTERNA como receiver: Button.inflate(...)
        // — resolve pelo classpath antes dos namespaces builtin
        // (Button também é widget do kof.ui; o import decide)
        if (mc.receiver() instanceof IdentifierExpr rid) {
            Type q = MemberResolver.qualifyViaImports(sa.unit(), rid.name());
            if (q == null && rid.name().contains(".")) {
                q = MemberResolver.qualifiedType(Type.of(rid.name()));
            }
            if (q instanceof Type.ClassType qt && sa.isExternal(qt)) {
                ExternalClasspath.MethodSignature sig = sa.externalTypes().resolveMethod(
                        qt.internalName(), mc.methodName(), mc.arguments().size());
                if (sig != null) {
                    List<Type> params = new ArrayList<>();
                    for (String d : sig.parameterDescriptors()) {
                        params.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                    sa.resolvedMethods().put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                            qt.internalName(), ret, params, 1,
                            SymbolTable.DispatchKind.STATIC));
                    return ret;
                }
            }
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "super".equals(rid.name())) {
            // super.method(args): resolve against the superclass
            // hierarchy of the enclosing class. The resolved symbol
            // is intentionally NOT registered in resolvedMethods —
            // lowering emits a non-virtual SUPER call.
            String superName = "Object";
            if (sa.currentClassName() != null) {
                SymbolTable.ClassSymbol self = sa.allClasses().get(sa.currentClassName());
                if (self != null && self.superClass() != null && !"Object".equals(self.superClass())) {
                    superName = self.superClass();
                }
            }
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            SymbolTable.Symbol m = MemberResolver.resolveInHierarchy(sa, superName, mc.methodName());
            if (m instanceof SymbolTable.MethodSymbol ms) {
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes());
                return ms.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        Type recvType = ExpressionTyper.inferType(sa, mc.receiver(), scope);
        // coleções: infere o retorno dos métodos (get → elemento,
        // size → Int, ...). Sem isso `var f = l.get(0)` de uma
        // List<FunctionType> inferia Unknown → `f(4)` dava SEM015
        // (bug 20). Espelha o inferExprType do CompilerDriver.
        if (BuiltinTypes.isList(recvType)) {
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                elemType = ct.typeArguments().get(0);
            String mn = mc.methodName();
            if ("get".equals(mn)) return elemType;
            if ("remove".equals(mn)) return elemType;
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                    || "set".equals(mn) || "clear".equals(mn))
                return Type.PrimitiveType.VOID;
        }
        if (BuiltinTypes.isMap(recvType)) {
            Type valueType = Type.UnknownType.UNKNOWN;
            Type keyType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                valueType = ct.typeArguments().get(1);
                keyType = ct.typeArguments().get(0);
            }
            String mn = mc.methodName();
            if ("get".equals(mn)) return valueType;
            if ("remove".equals(mn)) return valueType;
            if ("put".equals(mn)) return valueType;
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
            if ("keys".equals(mn)) return new Type.ClassType("kof", "List", List.of(keyType));
            if ("values".equals(mn)) return new Type.ClassType("kof", "List", List.of(valueType));
        }
        if (BuiltinTypes.isSet(recvType)) {
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                elemType = ct.typeArguments().get(0);
            String mn = mc.methodName();
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("add".equals(mn) || "remove".equals(mn)) return Type.PrimitiveType.BOOL;
            if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofDb.isDbNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
            KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
            if (dbCall != null) {
                if (typed && !mc.typeArguments().isEmpty()) {
                    return new Type.ClassType("kof", "List",
                            List.of(MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope)));
                }
                return dbCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofLog.isLogNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
            if (logCall != null) return logCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofOrm.isOrmNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
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
                                List.of(MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope)));
                    }
                    if ("find".equals(mc.methodName())) {
                        return MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
                    }
                }
                return ormCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                && !ExpressionTyper.isLocalName(scope, rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
            if (procCall != null) return procCall.returnType();
            KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
            if (exitCall != null) return exitCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofConfig.isConfigNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
            if (cfgCall != null) return cfgCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofCache.isCacheNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
            if (cacheCall != null) return cacheCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofGpu.isGpuNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
            if (gpuCall != null) return gpuCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofHttp.isHttpNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
            if (httpCall != null) return httpCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofMq.isMqNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
            if (mqCall != null) return mqCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofTime.isTimeNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
            if (timeCall != null) return timeCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofSecurity.isSecurityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (secCall != null) return secCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofValidation.isValidationNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (vCall != null) return vCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofObservability.isObservabilityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (oCall != null) return oCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofTetris.isTetrisNamespace(rid.name())) {
            KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (tetrisCall != null) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                return tetrisCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofMedia.isStaticNamespace(rid.name())) {
            KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (mediaCall != null) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                return mediaCall.returnType();
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !ExpressionTyper.isLocalName(scope, rid.name()) && KofWeb.isWebNamespace(rid.name())
                && "app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            return KofWeb.APP;
        }
        if (KofWeb.isAppType(recvType)) {
            if ("sse".equals(mc.methodName()) && mc.arguments().size() == 2
                    && mc.arguments().get(1) instanceof LambdaExpr le
                    && le.parameters().isEmpty()) {
                mc.arguments().set(1, new LambdaExpr(le.position(),
                        List.of(new FormalParameterNode(le.position(), List.of(),
                                MethodCallTyper.SSE_CONNECTION_TYPE, "sse")), le.body()));
            }
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), argTypes);
            if (webCall != null) return webCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (KofWeb.isSseConnectionType(recvType)) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall sseCall = KofWeb.sseConnectionMethod(mc.methodName(), argTypes);
            if (sseCall != null) return sseCall.returnType();
            return Type.UnknownType.UNKNOWN;
        }
        if (KofMedia.isImageData(recvType) || KofMedia.isAudio(recvType)) {
            KofMedia.MediaCall mediaCall = KofMedia.isImageData(recvType)
                    ? KofMedia.imageDataMethod(mc.methodName(), mc.arguments().size())
                    : KofMedia.audioMethod(mc.methodName(), mc.arguments().size());
            if (mediaCall != null) {
                for (ExpressionNode arg : mc.arguments()) ExpressionTyper.inferType(sa, arg, scope);
                return mediaCall.returnType();
            }
        }
        if (recvType instanceof Type.FunctionType ft) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
            TypeChecker.checkArgTypes(sa.diagnostics(), "function call", argTypes, ft.parameterTypes());
            return ft.returnType();
        }
        if (recvType instanceof Type.ClassType ct) {
            SymbolTable.Symbol m = MemberResolver.resolveInHierarchy(sa, ct.name(), mc.methodName());
            if (m instanceof SymbolTable.MethodSymbol ms) {
                sa.resolvedMethods().put(mc, ms);
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes());
                return ms.returnType();
            }
            // receiver de classe EXTERNA (android.* etc.): assinatura
            // vem do classpath — sem isso o lowering emitiria
            // invokevirtual com owner vazio
            if (sa.isExternal(ct)) {
                ExternalClasspath.MethodSignature sig = sa.externalTypes().resolveMethod(
                        ct.internalName(), mc.methodName(), mc.arguments().size());
                if (sig != null) {
                    List<Type> params = new ArrayList<>();
                    for (String d : sig.parameterDescriptors()) {
                        params.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                    sa.resolvedMethods().put(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                            ct.internalName(), ret, params, 1,
                            SymbolTable.DispatchKind.INSTANCE));
                    return ret;
                }
            }
            // Nenhum símbolo encontrado — método inexistente (SC3)
            // Nota: String/Int/Long/Bool são tipos JDK — métodos como
            // contains/split são resolvidos via lowering direto (JVM) ou
            // via runtime (Native/JS), não via externalTypes. Evita SEM025
            // falso-positivo para esses tipos. Object methods (hashCode etc.)
            // são válidos para qualquer classe.
            if (sa.diagnostics() != null && !BuiltinTypes.isList(ct)
                    && !MemberResolver.isObjectMethod(mc.methodName(), mc.arguments().size())) {
                boolean isKnownReceiver = sa.allClasses().containsKey(ct.name())
                        || sa.isExternal(ct);
                if (isKnownReceiver) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "Cannot resolve method '" + mc.methodName()
                                    + "' on type '" + ct.name() + "'",
                            "SEM025");
                }
            }
        }
        return null;
    }

    static List<Type> inferArgTypes(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferType(sa, arg, scope));
        return argTypes;
    }
}

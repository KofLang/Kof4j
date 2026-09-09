package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de chamadas de método COM receiver (membros de classe,
 * super, classes externas, namespaces kof.*), extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Retorna null quando nenhuma regra se aplica.
 */
public final class MemberCallTyper {

    private MemberCallTyper() {}

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null) return null;
        if (mc.receiver() instanceof IdentifierExpr rid3 && KofUi.isConstructor(rid3.name())) {
            KofUi.UiCall uiCall = KofUi.staticMethod(rid3.name(), mc.methodName(), mc.arguments().size());
            if (uiCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return uiCall.returnType();
            }
        }
        if (mc.receiver() instanceof IdentifierExpr ridR && KofUi.isRouterNamespace(ridR.name())) {
            KofUi.UiCall routerCall = KofUi.staticMethod("Router", mc.methodName(), mc.arguments().size());
            if (routerCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return routerCall.returnType();
            }
        }
        // Nome de CLASSE KOF (de qualquer pacote do modulo) como
        // receiver para metodo ESTATICO: Desconto.aplicar(c)
        if (mc.receiver() instanceof IdentifierExpr krid
                && !SemExpressionTyper.isLocalName(scope, krid.name())
                && sa.allClasses().containsKey(krid.name())) {
            SymbolTable.Symbol km = MemberResolver.resolveInHierarchy(sa, krid.name(), mc.methodName());
            if (km instanceof SymbolTable.MethodSymbol kms
                    && kms.parameterTypes().size() == mc.arguments().size()) {
                SymbolTable.ClassSymbol kt = sa.allClasses().get(krid.name());
                sa.resolvedMethods().put(mc, new SymbolTable.MethodSymbol(
                        kms.name(), kt.internalName(), kms.returnType(),
                        kms.parameterTypes(), kms.accessFlags(),
                        SymbolTable.DispatchKind.STATIC));
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
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
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            SymbolTable.Symbol m = MemberResolver.resolveInHierarchy(sa, superName, mc.methodName());
            if (m instanceof SymbolTable.MethodSymbol ms) {
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes());
                return ms.returnType();
            }
            // P0 #6: super.metodoInexistente() — mesma regra da classe:
            // SEM025 e depois UNKNOWN (error recovery). Excetos: Object
            // methods e superclasse EXTERNA (android.*), cuja hierarquia
            // completa não está no allClasses (super.onCreate é legítimo).
            if (sa.diagnostics() != null
                    && sa.allClasses().containsKey(superName)
                    && !MemberResolver.isObjectMethod(mc.methodName(), mc.arguments().size())) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Cannot resolve method '" + mc.methodName()
                                + "' in superclass '" + superName + "'",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        Type recvType = SemExpressionTyper.inferType(sa, mc.receiver(), scope);
        // coleções: infere o retorno dos métodos (get → elemento,
        // size → Int, ...). Sem isso `var f = l.get(0)` de uma
        // List<FunctionType> inferia Unknown → `f(4)` dava SEM015
        // (bug 20). Espelha o inferExprType do CompilerDriver.
        if (BuiltinTypes.isList(recvType)) {
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                elemType = ct.typeArguments().get(0);
            String mn = mc.methodName();
            // inferir args para detectar identificadores não declarados (ghost) nos argumentos/lambdas
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            if ("get".equals(mn)) return elemType;
            if ("remove".equals(mn)) return elemType;
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                    || "set".equals(mn) || "clear".equals(mn))
                return Type.PrimitiveType.VOID;
            if ("map".equals(mn) || "filter".equals(mn)) return recvType;
            if ("reduce".equals(mn)) return elemType;
            if (!"toArray".equals(mn) && !"sublist".equals(mn) && !"subSet".equals(mn)) {
                if (sa.diagnostics() != null) {
                    sa.diagnostics().error("", 0, 0, 0,
                            "Cannot resolve method '" + mn + "' on type 'List' (valid: add/get/set/remove/contains/size/isEmpty/clear/map/filter/reduce)",
                            "SEM025");
                }
            }
        }
        if (BuiltinTypes.isMap(recvType)) {
            Type valueType = Type.UnknownType.UNKNOWN;
            Type keyType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && ct.typeArguments().size() == 2) {
                valueType = ct.typeArguments().get(1);
                keyType = ct.typeArguments().get(0);
            }
            String mn = mc.methodName();
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
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
            if (sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Cannot resolve method '" + mn + "' on type 'Map' (valid: put/get/remove/containsKey/contains/size/clear/isEmpty/keys/values)",
                        "SEM025");
            }
        }
        if (BuiltinTypes.isSet(recvType)) {
            Type elemType = Type.UnknownType.UNKNOWN;
            if (recvType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty())
                elemType = ct.typeArguments().get(0);
            String mn = mc.methodName();
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("add".equals(mn) || "remove".equals(mn)) return Type.PrimitiveType.BOOL;
            if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
            if (sa.diagnostics() != null && !"toArray".equals(mn) && !"subSet".equals(mn) && !"sublist".equals(mn)) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Cannot resolve method '" + mn + "' on type 'Set' (valid: add/contains/remove/size/clear/isEmpty)",
                        "SEM025");
            }
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofDb.isDbNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            boolean typed = KofDb.isQuery(mc.methodName()) && !mc.typeArguments().isEmpty();
            KofDb.DbCall dbCall = KofDb.staticCall(mc.methodName(), argTypes, typed);
            if (dbCall != null) {
                if (typed && !mc.typeArguments().isEmpty()) {
                    return new Type.ClassType("kof", "List",
                            List.of(MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope)));
                }
                return dbCall.returnType();
            }
            return unknownNamespaceMethod(sa, "db", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofLog.isLogNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofLog.LogCall logCall = KofLog.staticCall(mc.methodName(), argTypes);
            if (logCall != null) return logCall.returnType();
            return unknownNamespaceMethod(sa, "log", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofOrm.isOrmNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
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
            return unknownNamespaceMethod(sa, "orm", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && "process".equals(rid.name())
                && !SemExpressionTyper.isLocalName(scope, rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofProcess.ProcessCall procCall = KofProcess.entryCall(mc.methodName(), argTypes);
            if (procCall != null) return procCall.returnType();
            KofProcess.ProcessCall exitCall = KofProcess.exitCall(argTypes);
            if (exitCall != null) return exitCall.returnType();
            if (sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Cannot resolve method '" + mc.methodName() + "' on 'process' (valid: run, spawn, exit)",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofConfig.isConfigNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofConfig.ConfigCall cfgCall = KofConfig.staticCall(mc.methodName(), argTypes);
            if (cfgCall != null) return cfgCall.returnType();
            return unknownNamespaceMethod(sa, "config", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofCache.isCacheNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofCache.CacheCall cacheCall = KofCache.staticCall(mc.methodName(), argTypes);
            if (cacheCall != null) return cacheCall.returnType();
            return unknownNamespaceMethod(sa, "cache", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofGpu.isGpuNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofGpu.GpuCall gpuCall = KofGpu.staticCall(mc.methodName(), argTypes);
            if (gpuCall != null) return gpuCall.returnType();
            return unknownNamespaceMethod(sa, "gpu", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofHttp.isHttpNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofHttp.HttpCall httpCall = KofHttp.staticCall(mc.methodName(), argTypes);
            if (httpCall != null) return httpCall.returnType();
            return unknownNamespaceMethod(sa, "http", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofMq.isMqNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofMq.MqCall mqCall = KofMq.staticCall(mc.methodName(), argTypes);
            if (mqCall != null) return mqCall.returnType();
            return unknownNamespaceMethod(sa, "mq", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofTime.isTimeNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofTime.TimeCall timeCall = KofTime.staticCall(mc.methodName(), argTypes);
            if (timeCall != null) return timeCall.returnType();
            return unknownNamespaceMethod(sa, "time", mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofSecurity.isSecurityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofSecurity.SecCall secCall = KofSecurity.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (secCall != null) return secCall.returnType();
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofValidation.isValidationNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofValidation.ValidationCall vCall = KofValidation.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (vCall != null) return vCall.returnType();
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofStd.isStdNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofStd.StdCall sCall = KofStd.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (sCall != null) return sCall.returnType();
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofObservability.isObservabilityNamespace(rid.name())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofObservability.ObservabilityCall oCall = KofObservability.staticMethod(rid.name(), mc.methodName(), argTypes);
            if (oCall != null) return oCall.returnType();
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofTetris.isTetrisNamespace(rid.name())) {
            KofTetris.TetrisCall tetrisCall = KofTetris.staticMethod(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (tetrisCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return tetrisCall.returnType();
            }
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofMedia.isStaticNamespace(rid.name())) {
            KofMedia.MediaCall mediaCall = KofMedia.staticCall(rid.name(), mc.methodName(),
                    mc.arguments().size());
            if (mediaCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return mediaCall.returnType();
            }
            return unknownNamespaceMethod(sa, rid.name(), mc.methodName());
        }
        if (mc.receiver() instanceof IdentifierExpr rid && !SemExpressionTyper.isLocalName(scope, rid.name()) && KofWeb.isWebNamespace(rid.name())
                && "app".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            return KofWeb.APP;
        }
        if (KofWeb.isAppType(recvType)) {
            if ("sse".equals(mc.methodName()) && mc.arguments().size() == 2
                    && mc.arguments().get(1) instanceof LambdaExpr le
                    && le.parameters().isEmpty()) {
                mc.arguments().set(1, new LambdaExpr(le.position(),
                        List.of(new FormalParameterNode(le.position(), List.of(),
                                SemMethodCallTyper.SSE_CONNECTION_TYPE, "sse")), le.body()));
            }
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), argTypes);
            if (webCall != null) return webCall.returnType();
            return unknownNamespaceMethod(sa, "web.app", mc.methodName());
        }
        if (KofWeb.isSseConnectionType(recvType)) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall sseCall = KofWeb.sseConnectionMethod(mc.methodName(), argTypes);
            if (sseCall != null) return sseCall.returnType();
            return unknownNamespaceMethod(sa, "sse", mc.methodName());
        }
        if (KofMedia.isImageData(recvType) || KofMedia.isAudio(recvType)) {
            KofMedia.MediaCall mediaCall = KofMedia.isImageData(recvType)
                    ? KofMedia.imageDataMethod(mc.methodName(), mc.arguments().size())
                    : KofMedia.audioMethod(mc.methodName(), mc.arguments().size());
            if (mediaCall != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return mediaCall.returnType();
            }
        }
        if (recvType instanceof Type.FunctionType ft) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            TypeChecker.checkArgTypes(sa.diagnostics(), "function call", argTypes, ft.parameterTypes());
            return ft.returnType();
        }
        if (recvType instanceof Type.ClassType ct) {
            SymbolTable.Symbol m = MemberResolver.resolveInHierarchy(sa, ct.name(), mc.methodName());
            if (m instanceof SymbolTable.MethodSymbol ms) {
                // SG-013 (SEM046): private/protected checados em compile-time
                // (antes viravam flags JVM e acesso indevido só explodia em
                // runtime com IllegalAccessError).
                checkMemberAccess(sa, ms.accessFlags(), ms.ownerClass(), ct.name(),
                        "'" + ct.name() + "." + mc.methodName() + "'");
                sa.resolvedMethods().put(mc, ms);
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
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
        for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
        return argTypes;
    }

    /**
     * SG-013 (SEM046): visibilidade em compile-time. `private` só é acessível
     * dentro da própria classe declarante; `protected` dentro da declarante ou
     * subclasses. Chamada fora → erro SEM046 (antes: IllegalAccessError runtime).
     */
    private static void checkMemberAccess(SemanticAnalyzer sa, int accessFlags,
                                          String ownerClass, String receiverClass,
                                          String memberDesc) {
        if (sa.diagnostics() == null) return;
        boolean isPriv = (accessFlags & AccessFlags.PRIVATE) != 0;
        boolean isProt = (accessFlags & AccessFlags.PROTECTED) != 0;
        if (!isPriv && !isProt) return;
        String caller = sa.currentClassName();
        if (caller == null) {
            // contexto top-level (main/função livre): não é dono de nada —
            // private E protected são inacessíveis
            sa.diagnostics().error("", 0, 0, 0,
                    memberDesc + " is " + (isPriv ? "private" : "protected")
                            + " (declared in '" + ownerClass
                            + "') and cannot be accessed from top-level code",
                    "SEM046");
            return;
        }
        if (isPriv) {
            // private: só a própria classe declarante
            if (!ownerClass.equals(caller)) {
                sa.diagnostics().error("", 0, 0, 0,
                        memberDesc + " is private (declared in '" + ownerClass
                                + "') and cannot be accessed from '" + caller + "'",
                        "SEM046");
            }
        } else {
            // protected: declarante ou subclasse (hierarquia transitiva)
            if (!isInHierarchy(sa, caller, ownerClass)) {
                sa.diagnostics().error("", 0, 0, 0,
                        memberDesc + " is protected (declared in '" + ownerClass
                                + "') and cannot be accessed from '" + caller + "'",
                        "SEM046");
            }
        }
    }

    /** caller está na hierarquia de `base` (caller == base ou estende transitivamente)? */
    private static boolean isInHierarchy(SemanticAnalyzer sa, String caller, String base) {
        String current = caller;
        int depth = 0;
        while (current != null && depth++ < 32) {
            if (current.equals(base)) return true;
            SymbolTable.ClassSymbol cs = sa.allClasses().get(current);
            if (cs == null) return false;
            current = cs.superClass();
        }
        return false;
    }

    /**
     * Invariante central de resolução (P0, R6): método inexistente em
     * NAMESPACE builtin (db, log, http, mq, time, security, ...) ou tipo
     * de plataforma (web.app/SseConnection). Regra única: primeiro SEM025,
     * depois UNKNOWN para error recovery — UNKNOWN NUNCA declara método
     * implícito. Todos os ramos de namespace passam aqui (não espalhar
     * if(namespace) pelo typer).
     */
    private static Type unknownNamespaceMethod(SemanticAnalyzer sa, String ns,
                                               String method) {
        if (sa.diagnostics() != null) {
            sa.diagnostics().error("", 0, 0, 0,
                    "Cannot resolve method '" + method + "' on namespace '" + ns + "'",
                    "SEM025");
        }
        return Type.UnknownType.UNKNOWN;
    }
}

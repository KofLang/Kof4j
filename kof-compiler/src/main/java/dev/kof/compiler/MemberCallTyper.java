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
        if (mc.receiver() instanceof IdentifierExpr tokR && KofUiTokens.isTokenNamespace(tokR.name())) {
            // Fase 10: tokens são CONSTANTES — um método neles é SEM079 (R6),
            // nunca queda silenciosa p/ void.
            if (sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "token '" + tokR.name() + "' has no methods — it holds constants: "
                        + KofUiTokens.memberList(tokR.name()), "SEM079");
            }
            return Type.PrimitiveType.VOID;
        }
        // Nome de CLASSE KOF (de qualquer pacote do modulo) como
        // receiver para metodo ESTATICO: Desconto.aplicar(c)
        if (mc.receiver() instanceof IdentifierExpr krid
                && !SemExpressionTyper.isLocalName(scope, krid.name())
                && sa.allClasses().containsKey(krid.name())) {
            SymbolTable.Symbol km = MemberResolver.resolveInHierarchy(sa, krid.name(), mc.methodName());
            if (km instanceof SymbolTable.MethodSet set) {
                List<Type> argTypes0 = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes0.add(SemExpressionTyper.inferType(sa, arg, scope));
                SymbolTable.MethodSymbol kms = set.select(mc.arguments().size(), argTypes0);
                if (kms != null) {
                    SymbolTable.ClassSymbol kt = sa.allClasses().get(krid.name());
                    sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol(
                            kms.name(), kt.internalName(), kms.returnType(),
                            kms.parameterTypes(), kms.accessFlags(),
                            SymbolTable.DispatchKind.STATIC));
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes0, kms.parameterTypes());
                    return kms.returnType();
                }
            }
            if (km instanceof SymbolTable.MethodSymbol kms
                    && kms.parameterTypes().size() == mc.arguments().size()) {
                SymbolTable.ClassSymbol kt = sa.allClasses().get(krid.name());
                sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol(
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
            Type q = MemberResolver.qualifyViaImports(sa.unit(), rid.name(), sa.externalTypes());
            if (q == null && rid.name().contains(".")) {
                q = MemberResolver.qualifiedType(Type.of(rid.name()));
            }
            if (q instanceof Type.ClassType qt && sa.isExternal(qt)) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                ExternalClasspath.MethodSignature sig = sa.externalTypes().resolveMethodWithArgs(
                        qt.internalName(), mc.methodName(), mc.arguments().size(), argTypes);
                if (sig != null) {
                    List<Type> params = new ArrayList<>();
                    for (String d : sig.parameterDescriptors()) {
                        params.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                    sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol(mc.methodName(),
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
            // SG-012 (inferência contextual): lambda de map/filter/reduce sem
            // anotação herda o tipo do ELEMENTO da lista — antes caía em
            // Object/Unknown e forçava `(x: Int)` mesmo com contexto óbvio.
            if (("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn))
                    && !(elemType instanceof Type.UnknownType)) {
                for (int i = 0; i < mc.arguments().size(); i++) {
                    if (mc.arguments().get(i) instanceof LambdaExpr le) {
                        mc.arguments().set(i, contextualLambda(le, elemType));
                    }
                }
            }
            // inferir args para detectar identificadores não declarados (ghost) nos argumentos/lambdas
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            // #336 — `add`/`push`/`append` são APPEND de UM elemento no Kof
            // (learn/12, training/idioms/collections). `l.add(i, v)` (o
            // insert posicional do Java) compilava e quebrava DIFERENTE em
            // cada alvo: JVM VerifyError no load, JS/Script engoliam o índice
            // e faziam append silencioso (divergência rule-5, R6). Não existe
            // insert posicional na linguagem; `set(i, v)` (replace) sim.
            // Rejeição universal na semântica compartilhada (mesma face do
            // CatchTypeCheck #332 — um gate, os 4 alvos reportam).
            if (("add".equals(mn) || "push".equals(mn) || "append".equals(mn))
                    && mc.arguments().size() != 1 && sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "List." + mn + " appends exactly one element; there is no positional insert — "
                                + "use set(index, value) to replace at an index",
                        "SEM072");
            }
            // #361 (§269(b)) — `reduce` SEMPRE takes TWO arguments: the lambda
            // and the seed, in either documented order (collections.md:
            // "(lambda, init)" and "(init, lambda) is also accepted"). A
            // 1-arg `reduce((a,b)->...)` compiled silently and died in ASM
            // frame computation (NegativeArraySizeException -1 in Frame.merge
            // — the emit stacked list+lambda against the 3-slot runtime
            // signature `(ArrayList,Object,Object)`). Reject at the shared
            // typer (same face as SEM072/#336): one gate, all four targets.
            if ("reduce".equals(mn) && mc.arguments().size() != 2 && sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "List.reduce takes exactly two arguments: the lambda AND the seed — "
                                + "reduce((a: Int, b: Int) -> a + b, 0) or reduce(0, (a: Int, b: Int) -> a + b)",
                        "SEM073");
            }
            if ("get".equals(mn)) return elemType;
            if ("remove".equals(mn)) return elemType;
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                    || "set".equals(mn) || "clear".equals(mn))
                return Type.PrimitiveType.VOID;
            // #334 — `map` devolvia recvType (ELEMENTO-FONTE) e `reduce`
            // devolvia elemType: a expressao era cacheada com o tipo errado
            // (inferType guarda o resultado no no), entao `strs.get(0)`
            // emitia checkcast do tipo FONTE sobre o valor real da lambda →
            // ClassCastException silenciosa. Agora espelha o EMIT
            // (`MethodCallTyper` #149 / `CollectionMethodTyper`): map →
            // List<retorno-da-lambda>, reduce → retorno, filter → recvType
            // (mesmo elemento, correto). Lambda sem retorno inferido =
            // UNKNOWN honesto (o emit trata igual) — nunca mentir com o
            // tipo da fonte.
            if ("map".equals(mn) || "filter".equals(mn) || "reduce".equals(mn)) {
                Type lamRet = Type.UnknownType.UNKNOWN;
                for (ExpressionNode arg : mc.arguments()) {
                    if (arg instanceof LambdaExpr || !(arg instanceof MethodCallExpr)) {
                        if (sa.expressionTypes().get(arg) instanceof Type.FunctionType ft) {
                            lamRet = ft.returnType();
                            break;
                        }
                    }
                }
                if ("filter".equals(mn)) return recvType;
                if (lamRet instanceof Type.UnknownType) return Type.UnknownType.UNKNOWN;
                if ("map".equals(mn)) {
                    return new Type.ClassType("kof", "List", List.of(lamRet));
                }
                return lamRet;
            }
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
            if ("get".equals(mn)) {
                // SG-008 (bug 87): get() devolve V? para TODO valor — ausência
                // é null comparável, nunca NPE por unbox
                return new Type.NullableType(valueType);
            }
            // D-NULL-INTENT/I7: mesma razão do `get` acima — Java Map
            // contract (valor anterior/removido OU null quando ausente).
            if ("remove".equals(mn)) return new Type.NullableType(valueType);
            if ("put".equals(mn)) return new Type.NullableType(valueType);
            if ("getOrDefault".equals(mn)) return valueType;
            if ("size".equals(mn) || "length".equals(mn) || "count".equals(mn))
                return Type.PrimitiveType.INT;
            if ("containsKey".equals(mn) || "contains".equals(mn) || "isEmpty".equals(mn))
                return Type.PrimitiveType.BOOL;
            if ("clear".equals(mn)) return Type.PrimitiveType.VOID;
            if ("keys".equals(mn)) return new Type.ClassType("kof", "List", List.of(keyType));
            if ("values".equals(mn)) return new Type.ClassType("kof", "List", List.of(valueType));
            if (sa.diagnostics() != null) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Cannot resolve method '" + mn + "' on type 'Map' (valid: put/get/getOrDefault/remove/containsKey/contains/size/clear/isEmpty/keys/values)",
                        "SEM025");
            }
        }
        if (BuiltinTypes.isSet(recvType)) {
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
        Type nsType = MemberCallNamespaces.inferStatic(sa, mc, scope, recvType);
        if (nsType != null) return nsType;
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
            // §131 (10a): MethodSet = sobrecarga por assinatura; seleciona
            // por aridade + compatibilidade de args.
            if (m instanceof SymbolTable.MethodSet set) {
                List<Type> argTypes0 = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes0.add(SemExpressionTyper.inferType(sa, arg, scope));
                SymbolTable.MethodSymbol ms = set.select(mc.arguments().size(), argTypes0);
                if (ms != null) {
                    checkMemberAccess(sa, ms.accessFlags(), ms.ownerClass(),
                            "'" + ct.name() + "." + mc.methodName() + "'");
                    sa.putResolvedMethod(mc, ms);
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes0, ms.parameterTypes());
                    return ms.returnType();
                }
            }
            if (m instanceof SymbolTable.MethodSymbol ms) {
                // SG-013 (SEM046): private/protected checados em compile-time
                // (antes viravam flags JVM e acesso indevido só explodia em
                // runtime com IllegalAccessError).
                checkMemberAccess(sa, ms.accessFlags(), ms.ownerClass(),
                        "'" + ct.name() + "." + mc.methodName() + "'");
                sa.putResolvedMethod(mc, ms);
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes());
                return ms.returnType();
            }
            // receiver de classe EXTERNA (android.* etc.): assinatura
            // vem do classpath — sem isso o lowering emitiria
            // invokevirtual com owner vazio
            if (sa.isExternal(ct)) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                ExternalClasspath.MethodSignature sig = sa.externalTypes().resolveMethodWithArgs(
                        ct.internalName(), mc.methodName(), mc.arguments().size(), argTypes);
                if (sig != null) {
                    List<Type> params = new ArrayList<>();
                    for (String d : sig.parameterDescriptors()) {
                        params.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                    sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol(mc.methodName(),
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
     * SG-012: reescreve a lambda com os params sem anotação tipados pelo
     * contexto (elemento da coleção). Params anotados são preservados.
     */
    static LambdaExpr contextualLambda(LambdaExpr le, Type paramType) {
        String typeName = paramTypeToSource(paramType);
        if (typeName == null) return le;
        List<FormalParameterNode> newParams = new ArrayList<>();
        boolean changed = false;
        for (FormalParameterNode p : le.parameters()) {
            if (p.type() == null || "Object".equals(p.type())) {
                newParams.add(new FormalParameterNode(p.position(), p.modifiers(),
                        typeName, p.name(), p.defaultExpression(), p.annotations()));
                changed = true;
            } else {
                newParams.add(p);
            }
        }
        if (!changed) return le;
        return new LambdaExpr(le.position(), newParams, le.body());
    }

    /** Nome de tipo fonte para um Type (usado pela reescrita da lambda). */
    static String paramTypeToSource(Type t) {
        if (t == Type.PrimitiveType.INT) return "Int";
        if (t == Type.PrimitiveType.LONG) return "Long";
        if (t == Type.PrimitiveType.DOUBLE) return "Double";
        if (t == Type.PrimitiveType.BOOL) return "Bool";
        if (t == Type.PrimitiveType.CHAR) return "Char";
        if (t instanceof Type.ClassType ct) {
            return ct.packageName().isEmpty() ? ct.name()
                    : ct.packageName() + "." + ct.name();
        }
        return null;
    }

    /**
     * SG-013 (SEM046): visibilidade em compile-time. `private` só é acessível
     * dentro da própria classe declarante; `protected` dentro da declarante ou
     * subclasses. Chamada fora → erro SEM046 (antes: IllegalAccessError runtime).
     */
    static void checkMemberAccess(SemanticAnalyzer sa, int accessFlags,
                                  String ownerClass,
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

    /**
     * #331/#327 — face de CAMPO do mesmo contrato SG-013: private só na
     * declarante, protected na declarante/subclasses (JVM aplica em runtime;
     * sem o cheque o compile deixava passar e o load estourava
     * IllegalAccessError — R6/Q7: nunca silencioso). O método-irmão já fazia
     * isso (SEM046); o campo não fazia porque o FieldSymbol perdia os
     * modificadores no SymbolTableBuilder. `this.x`/`x` nu na própria classe
     * passa (owner == caller).
     */
    static void checkFieldAccess(SemanticAnalyzer sa, SymbolTable.FieldSymbol fs) {
        checkMemberAccess(sa, fs.accessFlags(), fs.ownerClass(),
                "field '" + fs.name() + "'");
    }

    /**
     * #327 — escrita em campo `final`: o JVM so aceita putfield de um campo
     * final no <init> DA CLASSE DECLARANTE (JVMS 4.4). Fora disso o runtime
     * estoura IllegalAccessError ("Update to non-static final field ...
     * attempted from a different class") em silencio no compile (R6/Q7).
     * O inicializador `final Int x = 30` passa pelo fieldInits/<clinit>
     * sintetizado (nao e um statement do usuario) — nunca chega aqui.
     */
    static void checkFinalFieldWrite(SemanticAnalyzer sa, SymbolTable.FieldSymbol fs) {
        if (sa == null || sa.diagnostics() == null) return;
        if ((fs.accessFlags() & AccessFlags.FINAL) == 0) return;
        boolean staticField = (fs.accessFlags() & AccessFlags.STATIC) != 0;
        boolean legal = sa.inConstructor && !staticField
                && fs.ownerClass().equals(sa.currentClassName());
        if (legal) return;
        sa.diagnostics().error("", 0, 0, 0,
                "cannot assign to final field '" + fs.name() + "' (declared in '"
                        + fs.ownerClass() + "') from outside its constructor",
                "SEM065");
    }

    /** caller está na hierarquia de `base` (caller == base ou estende transitivamente)? */
    static boolean isInHierarchy(SemanticAnalyzer sa, String caller, String base) {
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

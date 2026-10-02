package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;


/**
 * Lowering de MethodCallExpr SEM receiver (chamadas nuas: super/driver,
 * metodo da propria classe, construtor por nome, lambda-var e funcao de topo).
 */
public final class ExpressionBareCallLowerer {

    private ExpressionBareCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            String owner, int localIdx, List<IRLocalVariable> locals) {
        if (("super".equals(mc.methodName()) || "driver".equals(mc.methodName()))
                && driver.semanticAnalyzer != null && owner != null && !owner.isEmpty()) {
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
            SymbolTable.ConstructorSymbol ctor = targetCs != null
                    ? SymbolTable.constructorFor(targetCs.members(), mc.arguments().size()) : null;
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
        if (selfMethod != null && owner != null && !owner.isEmpty()
                && !"<init>".equals(selfMethod.name())
                && selfMethod.ownerClass() != null) {
            Type ownerType = CompilerTypes.ownerTypeFromInternal(selfMethod.ownerClass(), driver.semanticAnalyzer);
            // Método ESTÁTICO da própria classe chamado sem receiver (ex.:
            // `twice(21)` dentro de outra static, ou no <clinit> de um campo
            // estático): invokestatic SEM receiver — antes emitia aload_0 +
            // invokevirtual → IncompatibleClassChangeError (contexto de
            // instância) / VerifyError (contexto estático).
            if ((selfMethod.accessFlags() & AccessFlags.STATIC) != 0) {
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                        ops, owner, localIdx, locals);
                ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                        selfMethod.returnType(), KofCallKind.STATIC));
                // §479: o path de MÓDULO (CLI) sai POR AQUI — o SA resolveu a
                // função como MethodSymbol — e este ramo não adaptava o retorno
                // genérico `T`: o call-site de referência ficava SEM checkcast
                // (VerifyError no load) e o de primitivo sem unbox. Mesma
                // adaptação do §477/#592, agora no caminho selfMethod.
                GenericReturnAdapter.emit(driver, mc, ops, locals, selfMethod.returnType());
                return localIdx;
            }
            ops.add(new KofLoadLocal(ownerType, 0));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), selfMethod.parameterTypes(),
                    ops, owner, localIdx, locals);
            // #213: chamada nua dentro de default method de interface resolve p/ a
            // PRÓPRIA interface — invokestatic/invokevirtual não valem; o JVM exige
            // invokeinterface (senão IncompatibleClassChangeError "Found interface").
            KofCallKind selfKind = KofCallKind.INSTANCE;
            // selfMethod != null so aqui SOMENTE porque o ternario acima passou por
            // driver.semanticAnalyzer != null — o re-check era dead-code (CodeQL #716,
            // confirmado pelo proprio dominance). Removido; nenhum caminho alterado.
            String selfOwner = selfMethod.ownerClass();
            if (selfOwner.contains("/")) selfOwner = selfOwner.substring(selfOwner.lastIndexOf('/') + 1);
            if (driver.semanticAnalyzer.isInterfaceType(selfOwner)) selfKind = KofCallKind.INTERFACE;
            // §557: o typer carimba INTERFACE para owner externo/JDK
            // (interfaceNames é só Kof-local) — mesma regra do #213 acima.
            if (selfKind == KofCallKind.INSTANCE
                    && selfMethod.dispatchKind() == SymbolTable.DispatchKind.INTERFACE) {
                selfKind = KofCallKind.INTERFACE;
            }
            ops.add(new KofCall(ownerType, mc.methodName(), selfMethod.parameterTypes(),
                    selfMethod.returnType(), selfKind));
            // §479: mesma adaptação do ramo STATIC acima (retorno `T` do
            // selfMethod — unbox p/ primitivo, checkcast p/ referência).
            GenericReturnAdapter.emit(driver, mc, ops, locals, selfMethod.returnType());
            return localIdx;
        }
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer != null ? driver.semanticAnalyzer.getClass(mc.methodName()) : null;
        if (cs == null) {
            // §393 (#568): construtor externo implicito (`Greeter()` sem `new`)
            // — espelho do ramo `new` do ExpressionLowerer; classe/funcao
            // declaradas venceram acima (precedencia do typer preservada).
            int extCtor = tryLowerExternalCtor(driver, mc, ops, owner, localIdx, locals);
            if (extCtor >= 0) return extCtor;
        }
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
                // #402/#388: chamada de CAMPO de tipo de função da classe atual.
                // Antes caía no fallback de função de topo e emitia
                // invokestatic phantom em Default/Main.<campo> (método que não
                // existe; retorno Object) → VerifyError no load. O caminho
                // correto espelha o ramo de lambda-local declarado acima:
                // this + getfield + invokeinterface na interface sintética.
                // Local venceu antes (findLocalVar) — declared-local-wins §179.
                if (owner != null && !owner.isEmpty() && driver.semanticAnalyzer != null) {
                    Type selfType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
                    if (selfType instanceof Type.ClassType oct) {
                        SymbolTable.Symbol fsym = MemberResolver.resolveFieldInHierarchy(
                                driver.semanticAnalyzer, oct.name(), mc.methodName());
                        if (fsym instanceof SymbolTable.FieldSymbol fld
                                && fld.type() instanceof Type.FunctionType fft) {
                            ops.add(new KofLoadLocal(oct, 0));
                            ops.add(new KofLoadField(oct, fld.name(), fft));
                            List<Type> fArgTypes = new ArrayList<>();
                            for (ExpressionNode arg : mc.arguments()) fArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), fft.parameterTypes(), ops, owner, localIdx, locals);
                            Type fIface = fft.className() != null
                                    ? new Type.ClassType("", fft.className(), List.of())
                                    : driver.lambdaInterfaceType(fft);
                            ops.add(new KofCall(fIface, "invoke", fArgTypes, fft.returnType(),
                                    fft.className() != null ? KofCallKind.INSTANCE : KofCallKind.INTERFACE));
                            return localIdx;
                        }
                    }
                }
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                Type returnType = Type.UnknownType.UNKNOWN;
                // §479: witness explícito (`idf<Point>`) — hoisted p/ o adapter
                // depois da emissão do KofCall (descritor fica apagado).
                boolean witnessable = false;
                Type boundReturn = returnType;
                if (driver.currentUnit != null) {
                    // SG-011B: mesmo veredicto do typer (frontend único). Único
                    // candidato → caminho idêntico ao antigo; ≥2 → assinatura.
                    List<FunctionDeclarationNode> ovlFns = new ArrayList<>();
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) ovlFns.add(fn);
                    }
                    FunctionDeclarationNode chosen = ovlFns.isEmpty() ? null : ovlFns.get(0);
                    if (ovlFns.size() > 1) {
                        List<TopLevelOverload.Candidate> ovlCands = new ArrayList<>();
                        for (FunctionDeclarationNode fn : ovlFns) {
                            List<Type> pt = fn.parameters().stream()
                                    .map(pp -> CompilerTypes.resolveWithTypeParams(pp.type(), fn.typeParameters(), driver.currentUnit, driver.semanticAnalyzer)).toList();
                            ovlCands.add(new TopLevelOverload.Candidate(fn, pt,
                                    TopLevelOverload.requiredArityOf(fn)));
                        }
                        TopLevelOverload.Status[] st = new TopLevelOverload.Status[1];
                        int sel = TopLevelOverload.pick(ovlCands, argTypes, st);
                        if (sel >= 0) chosen = ovlCands.get(sel).fn();
                    }
                    if (chosen != null) {
                        // §479: função top-level GENÉRICA com witness EXPLÍTITO
                        // (`idf<Point>(...)`) — liga T := Point no retorno E nos
                        // formais. Sem isto o path de MÓDULO (CLI) deixava o
                        // call-site sem o checkcast do GenericReturnAdapter
                        // (VerifyError no load; path de arquivo único cobria
                        // pela cauda do SA) e o emit checava `T` cru (SEM014
                        // falso-positivo). Mesma forma do witness de construtor
                        // do §474/#585.
                        witnessable = !chosen.typeParameters().isEmpty()
                                && mc.typeArguments().size() == chosen.typeParameters().size();
                        List<Type> callWitness = new ArrayList<>();
                        if (witnessable) {
                            for (var ta : mc.typeArguments()) {
                                callWitness.add(MemberResolver.resolveType(driver.semanticAnalyzer, ta, null));
                            }
                        }
                        returnType = CompilerTypes.resolveWithTypeParams(chosen.returnType(), chosen.typeParameters(), driver.currentUnit, driver.semanticAnalyzer);
                        // §479: o descritor do KofCall fica APAGADO (erasure,
                        // idem declaração); o binding alimenta SÓ a adaptação
                        // do retorno — o tipo EFETIVO ligado decide o
                        // unbox/checkcast (o adapter no fim do ramo).
                        if (witnessable) {
                            boundReturn = GenericReturnAdapter.bindTypeVariables(returnType, chosen.typeParameters(), callWitness);
                        }
                        List<Type> fnTypes = new ArrayList<>();
                        for (var pp : chosen.parameters()) fnTypes.add(CompilerTypes.resolveWithTypeParams(pp.type(), chosen.typeParameters(), driver.currentUnit, driver.semanticAnalyzer));
                        boolean hasDefaults = chosen.parameters().stream()
                                .anyMatch(p -> p.defaultExpression() != null);
                        if (hasDefaults && mc.arguments().size() < fnTypes.size()) {
                            argTypes = fnTypes.subList(0, mc.arguments().size());
                        } else {
                            argTypes = fnTypes;
                        }
                    }
                }
                localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), argTypes, ops, owner, localIdx, locals);
                ops.add(new KofCall(CompilerTypes.mainClassType(driver.currentModule), mc.methodName(), argTypes, returnType, KofCallKind.FUNCTION));
                // §592: retorno `T` de FUNÇÃO de topo — mesma adaptação do
                // call-site de instância (unbox p/ primitivo, checkcast p/
                // referência) pelo helper compartilhado; antes só o unbox de
                // primitivo era tratado aqui → `idf<Point>(...)` devolvia
                // Object sem checkcast → NoSuchMethodError/VerifyError.
                if (witnessable) {
                    // §479: witness explícito — o tipo EFETIVO já é conhecido,
                    // sem depender de inferExprType (que no path de módulo/CLI
                    // não registra o tipo da chamada e deixava o call-site de
                    // referência SEM checkcast = VerifyError no load; §477/#592).
                    GenericReturnAdapter.emitBound(driver, ops, boundReturn);
                } else {
                    GenericReturnAdapter.emit(driver, mc, ops, locals, returnType);
                }
            }
        }
        return localIdx;
    }

    /**
     * §393 — #568: baixa `Classe(args)` sem `new` quando `Classe` e uma classe
     * EXTERNA (--classpath/--deps) com construtor PUBLICO de aridade
     * compativel: KofNewObject + DUP + args convertidos aos formais do
     * descritor + INVOKESPECIAL <init> (MESMO plano da face `new` em
     * ExpressionLowerer:235-253, que ja resolvia via resolveConstructor).
     * Sentinela -1 = nao e construtor externo (segue o fluxo de sempre).
     * Guarda de precedencia (freeze regra 2): classe do programa ou funcao
     * top-level/`extern` homonima declara o call-site — nunca sequestra.
     */
    private static int tryLowerExternalCtor(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        // `driver.externalClasspath` é final + sempre construído
        // (CompilerDriverState:75) — o null-check era morto (CodeQL #950).
        String internal = null;
        if (driver.semanticAnalyzer != null) {
            SymbolTable.MethodSymbol m = driver.semanticAnalyzer.getResolvedMethod(mc);
            if (m != null && "<init>".equals(m.name()) && m.ownerClass() != null
                    && m.ownerClass().contains("/")) {
                internal = m.ownerClass();
            }
        }
        if (internal == null) {
            // sem registro do typer (node recriado no desugar): refazer a
            // qualificacao pelo import, com as MESMAS guardas do typer
            if (mc.receiver() != null) return -1;
            if (driver.isLocalVarName(mc.methodName(), locals)) return -1;
            Type q = CompilerTypes.qualifyViaImports(mc.methodName(), driver.currentUnit,
                    driver.externalClasspath);
            if (!(q instanceof Type.ClassType ct) || ct.packageName().isEmpty()) return -1;
            internal = ct.internalName();
        }
        if (driver.semanticAnalyzer != null
                && driver.semanticAnalyzer.getClass(mc.methodName()) != null) return -1;
        if (declaresTopLevelFunction(driver, mc.methodName())) return -1;
        if (!driver.externalClasspath.knows(internal)) return -1;
        ExternalClasspath.MethodSignature sig =
                driver.externalClasspath.resolvePublicConstructor(internal, mc.arguments().size());
        if (sig == null) return -1;
        Type classType = ExternalClasspath.typeFromDescriptor("L" + internal + ";");
        List<Type> formal = new ArrayList<>();
        for (String d : sig.parameterDescriptors()) {
            formal.add(ExternalClasspath.typeFromDescriptor(d));
        }
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) {
            argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        }
        ops.add(new KofNewObject(classType, argTypes));
        ops.add(new KofDup());
        localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), formal, ops, owner,
                localIdx, locals);
        ops.add(new KofCall(classType, "<init>", formal, Type.PrimitiveType.VOID,
                KofCallKind.CONSTRUCTOR));
        return localIdx;
    }

    private static boolean declaresTopLevelFunction(CompilerDriver driver, String name) {
        if (driver.currentUnit == null) return false;
        for (AstNode d : driver.currentUnit.declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)) return true;
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        return false;
    }
}

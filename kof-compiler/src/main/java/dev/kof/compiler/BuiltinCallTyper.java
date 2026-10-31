package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de chamadas builtin (sem receiver, e println/print com
 * receiver — na ordem exata do switch original), extraída do
 * SemanticAnalyzer (REFACTOR-500 fase 6). Retorna null quando nenhuma
 * regra se aplica.
 */
public final class BuiltinCallTyper {

    private BuiltinCallTyper() {}

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        // §243 função (#931/#932): o builtin de nome fixo não intercepta uma
        // chamada que o programa declara (função top-level ou extern) — a
        // resolução segue a cadeia até TopLevelCallTyper, que tipa e registra.
        if (mc.receiver() == null && KofShadowing.userDeclares(sa, mc.methodName())) {
            return null;
        }
        if (mc.receiver() == null && "channel".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            // channel<T>() -> Channel<T>; sem argumento é Channel<Unknown>
            Type elemType = mc.typeArguments().isEmpty()
                    ? Type.UnknownType.UNKNOWN
                    : MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
            return new Type.ClassType("kof.concurrent", "Channel", List.of(elemType));
        }
        if (mc.receiver() == null && "ring1".equals(mc.methodName())
                && mc.arguments().size() == 1 && !hasUserFunctionNamed(sa, scope, "ring1")) {
            // B-6.2b (D-BAREMETAL-RING1-SURFACE): `ring1(fn)` e um marcador
            // builtin que baixa para a transicao ring0->ring1 no perfil x86_64
            // UEFI_RING (NATIVE003 nomeado nos demais alvos, emitido no
            // lowering). O argumento NAO e inferido: nome de funcao top-level
            // em posicao de argumento e SEM011 por design; validamos apenas
            // que e uma fn top-level de zero args.
            if (mc.arguments().get(0) instanceof IdentifierExpr fnId
                    && isZeroArgTopLevelFunction(sa, fnId.name())) {
                return Type.PrimitiveType.VOID;
            }
            return null;
        }
        if (mc.receiver() == null && "listOf".equals(mc.methodName())) {
            // listOf(...) keeps its element type: List<T> must survive
            // the whole pipeline (for-in, get, method resolution).
            Type elemType = Type.UnknownType.UNKNOWN;
            if (!mc.typeArguments().isEmpty()) {
                elemType = MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
            } else if (!mc.arguments().isEmpty()) {
                elemType = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
                if (elemType instanceof Type.FunctionType ft) {
                    // §156: espelho do CompilerTypeSupport — lista de lambdas
                    // da mesma assinatura desce sem className (o analyzer roda
                    // antes da síntese, então className é null aqui; a checagem
                    // é só params+retorno iguais em todos os args).
                    boolean same = true;
                    for (int i = 1; i < mc.arguments().size(); i++) {
                        Type t = SemExpressionTyper.inferType(sa, mc.arguments().get(i), scope);
                        if (!(t instanceof Type.FunctionType oft)
                                || !oft.parameterTypes().equals(ft.parameterTypes())
                                || !oft.returnType().equals(ft.returnType())) {
                            same = false;
                            break;
                        }
                    }
                    if (same && mc.arguments().size() > 1) {
                        elemType = new Type.FunctionType(ft.parameterTypes(), ft.returnType());
                    }
                } else if (elemType instanceof Type.ClassType) {
                    // #360: heterogênea por subtipes relacionados não deve
                    // herdar o cast do PRIMEIRO elemento (Cat -> checkcast Dog
                    // -> CCE no get()). Fecha sobre o ancestral comum; sem um
                    // (Dog+String) mantém o comportamento antigo (r1 — nada
                    // que roda hoje deixa de rodar).
                    for (int i = 1; i < mc.arguments().size(); i++) {
                        Type t = SemExpressionTyper.inferType(sa, mc.arguments().get(i), scope);
                        if (!(t instanceof Type.ClassType)) continue;
                        elemType = HierarchyResolver.widenToCommonSupertype(sa, (Type.ClassType) elemType, (Type.ClassType) t);
                    }
                }
            }
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return new Type.ClassType("kof", "List", List.of(elemType));
        }
        if (mc.receiver() == null && "mapOf".equals(mc.methodName())) {
            Type explicitMap = CollectionLiteralTypes.explicit(sa, mc, scope, "Map", 2);
            if (explicitMap != null) return explicitMap;
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            // pinning no primeiro par (k1, v1, ...) — espelha o emit e o
            // CompilerDriver.inferExprType; sem isso Map<Unknown,Unknown>
            // vazava para var x = mapOf(...) e get() devolvia Unknown
            Type keyType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
                    : SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            Type valueType = mc.arguments().size() < 2 ? Type.UnknownType.UNKNOWN
                    : SemExpressionTyper.inferType(sa, mc.arguments().get(1), scope);
            return new Type.ClassType("kof", "Map", List.of(keyType, valueType));
        }
        if (mc.receiver() == null && "setOf".equals(mc.methodName())) {
            Type explicitSet = CollectionLiteralTypes.explicit(sa, mc, scope, "Set", 1);
            if (explicitSet != null) return explicitSet;
            Type elemType = Type.UnknownType.UNKNOWN;
            if (!mc.arguments().isEmpty()) {
                elemType = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
                if (elemType instanceof Type.ClassType) { // #360 (mesma raiz do listOf)
                    for (int i = 1; i < mc.arguments().size(); i++) {
                        Type t = SemExpressionTyper.inferType(sa, mc.arguments().get(i), scope);
                        if (!(t instanceof Type.ClassType tc)) continue;
                        elemType = HierarchyResolver.widenToCommonSupertype(
                                sa, (Type.ClassType) elemType, tc);
                    }
                }
            }
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return new Type.ClassType("kof", "Set", List.of(elemType));
        }
        if (mc.receiver() == null && sa.allClasses().containsKey(mc.methodName())) {
            // Implicit construction: ClassName(args) without `new`.
            // User classes take precedence over builtin helpers with
            // the same name (e.g. KofUi's Color).
            SymbolTable.ClassSymbol ctorClass = sa.allClasses().get(mc.methodName());
            List<Type> ctorArgTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) {
                ctorArgTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            }
            // SG-017 (SEM041): classe abstrata não pode ser instanciada —
            // cobre tanto `new A()` (SemExpressionTyper) quanto `A()` (aqui).
            if (sa.abstractClasses().contains(mc.methodName()) && sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "cannot instantiate abstract class '" + mc.methodName() + "'",
                        "SEM041");
            }
            // #340 (SEM071): interface não é instanciável (mesma face de `A()`).
            ClassShapeChecks.checkInstantiable(sa, mc.methodName());
            SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                    ctorClass.members(), mc.arguments().size(), ctorArgTypes);
            if (ctor != null) {
                List<Type> ctorParamTypes = ctor.effectiveParameterTypes(mc.arguments().size());
                sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                        ctor.type(), ctorParamTypes, ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                // #323: `A("x")` num ctor `(Int)` — resolucao por aridade sem
                // conferir TIPO inventava <init>(String)V (VerifyError mudo
                // no load, R6). Sobrecarga com irmao compativel passa (o emit
                // resolve por aridade+assignability).
                TypeChecker.checkCtorArgTypes(sa, mc, ctorClass.members(), mc.methodName(),
                        ctorArgTypes);
            } else {
                // §362/#545: face IMPLICITA sem aridade casada caia aqui em
                // silencio — `P(1,2)` em `record P(Int x)` compilava "clean" e
                // morria em NoSuchMethodError no JVM (Script imprimia phantom,
                // JS nem Main.js emitia). MESMO diagnostico SEM023 do caminho
                // `new` (SemExpressionTyper) — R6: nunca silencioso. Classe sem
                // construtor declarado (default implicito) nao casa os guards do
                // else e continua legal (`Z()` com `class Z {}` e o contrato).
                reportNoCtorArity(sa, ctorClass, mc);
            }
            // #585: o witness do call-site (`Box<Point>(...)`, forma idiomática
            // de training/idioms/records.md) era descartado — a inferência
            // devolvia a classe CRUA (typeArguments=[]) e a substituição de `T`
            // no receptor virava no-op (`get(): T` → Methodref java/lang/Object
            // → NoSuchMethodError no JVM com argumento reference-type; a face
            // primitiva §288 mascarava o furo). Resolve o witness como o
            // listOf/records fazem acima.
            // §713: sem witness explícito, INFERE os type-args dos tipos dos
            // argumentos contra os parâmetros DECLARADOS do construtor. Sem
            // isto o receptor fica com args vazios, o `T` do campo/método não
            // resolve e o JVM/nativo usam Object apagado (VerifyError /
            // ClassCastException).
            List<Type> witnessArgs = resolveWitnessTypeArgs(sa, mc, scope);
            if (witnessArgs.isEmpty() && ctor != null) {
                List<Type> inferred = GenericTypeArgInference.infer(
                        sa.typeParamNames(ctorClass.name()), ctor.parameterTypes(), ctorArgTypes);
                if (inferred != null) witnessArgs = inferred;
            }
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(), witnessArgs);
        }
        if (mc.receiver() == null && ("println".equals(mc.methodName()) || "print".equals(mc.methodName()))) {
            // #495 (maintainer 19/09: "empty println should not compile"): o
            // builtin aceita exatamente UM valor. Zero argumentos passava pelo
            // typer e caía no emissor genérico de método → NoSuchMethodError no
            // runtime (R6 — diagnóstico no compile, nunca falha muda).
            // Não sombreia (espelha a checagem do `sleep` abaixo e a varredura
            // de FunctionDeclarationNode do próprio typer): função ou método de
            // CLASSE do usuário chamado `println`/`print` continua legal — foi
            // o que o hunt Q4 pegou (função top-level 0-args homônima virava
            // falso-positivo; backward compat, freeze regra 2).
            if (mc.arguments().isEmpty() && !hasUserZeroArgDeclaration(sa, scope, mc.methodName())
                    && sa.diagnostics() != null) {
                sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                        mc.position() != null ? mc.position().line() : 0,
                        mc.position() != null ? mc.position().column() : 0, 0,
                        mc.methodName() + "() needs an argument — println and print take the"
                                + " value to print (println(x)); for a blank line use println(\"\")",
                        "SEM096");
            }
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return Type.PrimitiveType.VOID;
        }
        if (mc.receiver() == null && "now".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            return Type.PrimitiveType.LONG;
        }
        // #108 (opção 1, 13/09): `sleep(ms)` sem receiver resolve como
        // `time.sleep(ms)` — espelha o `now()` sem receiver acima.
        // Só vale sem receiver E sem local/função `sleep` do usuário
        // (não sombreia: resolve() acha local, param, função ou classe).
        if (mc.receiver() == null && "sleep".equals(mc.methodName())
                && scope.resolve("sleep") == null) {
            List<Type> sleepArgs = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) sleepArgs.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofTime.TimeCall sleepCall = KofTime.staticCall("sleep", sleepArgs);
            if (sleepCall != null) return sleepCall.returnType();
        }
        if (mc.receiver() == null && "readLine".equals(mc.methodName()) && mc.arguments().isEmpty()) {
            return BuiltinTypes.STRING;
        }
        if (mc.receiver() == null && KofWeb.isContextFunction(mc.methodName())
                && !isCallableLocal(scope, mc.methodName())) {
            // um local CHAMÁVEL com o mesmo nome (ex.: o parâmetro-lambda `body`
            // do helper `withDb` de kof.test.db) VENCE o nome de contexto — a
            // chamada é um lambda comum, não a função web (regressão 0.6.2).
            // Um local NÃO-chamável (ex.: o parâmetro sintético `sse` do handler)
            // NÃO vence: o `sse("...")` nu continua sendo a função de contexto.
        
            List<Type> webArgTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) webArgTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            KofWeb.WebCall webCtx = KofWeb.contextCall(mc.methodName(), webArgTypes);
            if (webCtx != null) return webCtx.returnType();
            // §665 Boundary item 1 (ledger §668): nome de contexto conhecido
            // sem aridade casando NÃO pode cair em silêncio — o
            // isTopLevelCandidate exclui esses nomes da resolução top-level
            // (e do SEM015), então sem este diagnóstico a chamada some em
            // todos os caminhos (status(401) cru compilava limpo).
            if (sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "Cannot resolve function '" + mc.methodName()
                                + "' on the web request context (arity mismatch)",
                        "SEM025");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if ((mc.receiver() == null && KofScheduler.isSchedulerMethod(mc.methodName()))
                || (mc.receiver() instanceof IdentifierExpr rid2 && KofScheduler.isSchedulerNamespace(rid2.name())
                        && KofScheduler.isSchedulerMethod(mc.methodName()))) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            if ("cancel".equals(mc.methodName())) {
                // cancel(Handle<T>) é o cancel de concorrência (retorna Bool);
                // cancel(String taskId) é o do scheduler (VOID). Distingue pelo
                // tipo do argumento para o + string converter o Bool certo.
                Type a0 = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
                if (TypeChecker.isConcurrentHandle(a0)) {
                    return Type.PrimitiveType.BOOL;
                }
                return Type.PrimitiveType.VOID;
            }
            else return BuiltinTypes.STRING;
        }
        if (mc.receiver() == null && "transaction".equals(mc.methodName())
                && mc.arguments().size() == 1) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return Type.PrimitiveType.VOID;
        }
        if (mc.receiver() == null && "uiNodesLive".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            // kof.ui probe (testes de leak): nº de componentes vivos.
            return Type.PrimitiveType.INT;
        }
        if (mc.receiver() == null && "emit".equals(mc.methodName())
                && mc.arguments().size() == 2) {
            // Fase 5: dispara evento (bubbling) — args inferidos.
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return Type.PrimitiveType.VOID;
        }
        if (mc.receiver() == null && "storesLive".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            // kof.ui probe de leak de stores.
            return Type.PrimitiveType.INT;
        }
        if (mc.receiver() == null && "subscriptionsLive".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            // kof.ui probe de leak de subscriptions (D-COMPLETE-FIRST item 4).
            return Type.PrimitiveType.INT;
        }
        if (mc.receiver() == null && "readFile".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return BuiltinTypes.STRING;
        }
        if (mc.receiver() == null && "writeFile".equals(mc.methodName()) && mc.arguments().size() == 2) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return Type.PrimitiveType.INT;
        }
        if (mc.receiver() == null && KofIo.isConstructor(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofIo.constructorType(mc.methodName());
        }
        Type ui = BuiltinUiCallTyper.infer(sa, mc, scope);
        if (ui != null) return ui;
        return null;
    }

    /**
     * Cauda do case MethodCallExpr (após os branches com/sem receiver):
     * local function, chamada implícita (this), super/this ctor, helpers
     * de concorrência, função top-level (SEM015), construção implícita e
     * a API String — na ordem exata e com as guardas originais (alguns
     * branches só valem sem receiver).
     */

    /** B-6.2b: `ring1` so vale como builtin se nenhuma declaracao do usuario
     *  (funcao top-level, externa, membro da classe atual ou local) ja usa o
     *  nome — nao sombreia (freeze regra 2). */
    private static boolean hasUserFunctionNamed(SemanticAnalyzer sa, SymbolTable scope, String name) {
        if (scope != null && scope.resolve(name) != null) return true;
        for (AstNode d : sa.unit().declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)) return true;
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        return sa.currentClassName() != null && !sa.currentClassName().isEmpty()
                && MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), name) != null;
    }

    /** B-6.2b: o alvo de `ring1(fn)` tem de ser uma funcao top-level de zero
     *  args (o codigo ring1 nao recebe parametros por enquanto, B-6.2b). */
    private static boolean isZeroArgTopLevelFunction(SemanticAnalyzer sa, String name) {
        for (AstNode d : sa.unit().declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)
                    && fn.parameters().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** #495: o diagnostico de aridade so vale para o BUILTIN. Se uma
     *  funcao/metodo do usuario com o MESMO nome existe (top-level com
     *  qualquer aridade, ou membro da classe atual com 0 params), o call
     *  site e dela — nao SEM096 (backward compat, freeze regra 2). Medido no
     *  hunt Q4: `void println() { ... }` + `println()` virava falso-positivo. */
    private static boolean hasUserZeroArgDeclaration(SemanticAnalyzer sa,
            SymbolTable scope, String name) {
        for (AstNode d : sa.unit().declarations()) {
            if (d instanceof FunctionDeclarationNode fn && fn.name().equals(name)
                    && TopLevelOverload.requiredArityOf(fn) == 0) {
                return true;
            }
            if (d instanceof ExternalFunctionNode ext && ext.name().equals(name)) return true;
        }
        if (sa.currentClassName() != null && !sa.currentClassName().isEmpty()
                && MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), name) != null) {
            return true;
        }
        return scope.resolve(name) != null;
    }


    /** local/parâmetro CHAMÁVEL (lambda) — vence o nome de contexto web (regressão 0.6.2). */
    private static boolean isCallableLocal(SymbolTable scope, String name) {
        SymbolTable.Symbol sym = scope != null ? scope.resolve(name) : null;
        return sym != null && sym.type() instanceof Type.FunctionType;
    }

    /** Cauda do dispatch — extraída para BuiltinCallTailTyper (REFACTOR-500,
     *  check_500 crítico); mesma assinatura/semântica, zero mudança. */
    static Type inferTail(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        return BuiltinCallTailTyper.inferTail(sa, mc, scope);
    }

    // §362/#545: o call-site implicito `Class(args)` sem aridade casada —
    // MESMO SEM023 (e MESMAS excecoes) do caminho `new` em SemExpressionTyper:
    // so reclama quando EXISTE construtor declarado (a aridade chamada e que
    // nao casa); `class Z {}` + `Z()` (default implicito) fica legal.
    static void reportNoCtorArity(SemanticAnalyzer sa, SymbolTable.ClassSymbol ctorClass,
            MethodCallExpr mc) {
        if (sa.diagnostics() == null) return;
        SymbolTable.Symbol anyInit = ctorClass.members().resolve("<init>");
        if (anyInit instanceof SymbolTable.ConstructorSymbol c) {
            sa.diagnostics().error(mc,
                    "no constructor of '" + mc.methodName() + "' with "
                            + mc.arguments().size() + " argument(s) (expected "
                                    + SymbolTable.describeExpectedConstructorArity(c) + ")",
                    "SEM023");
        } else if (anyInit instanceof SymbolTable.ConstructorSet set
                && !set.constructors().isEmpty()) {
            sa.diagnostics().error(mc,
                    "no constructor of '" + mc.methodName() + "' with "
                            + mc.arguments().size() + " argument(s)",
                    "SEM023");
        }
    }
    /** #585: resolve os type-arguments explícitos do call-site de construção
     *  implícita (`ClassName<T>(...)`); sem witness, raw (compat aditivo).
     *  Package-private: também chamado por BuiltinCallTailTyper. */
    static List<Type> resolveWitnessTypeArgs(SemanticAnalyzer sa, MethodCallExpr mc,
            SymbolTable scope) {
        if (mc.typeArguments().isEmpty()) return List.of();
        List<Type> resolved = new ArrayList<>();
        for (var ta : mc.typeArguments()) {
            resolved.add(MemberResolver.resolveType(sa, ta, scope));
        }
        return resolved;
    }

}

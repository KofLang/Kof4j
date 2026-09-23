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

    /**
     * Implicit construction (`Box<Point>(...)`, no `new`) discarded the
     * call-site type-argument witness (`<Point>`) and always typed the
     * result as the class's RAW ClassType (`List.of()`), never `[Point]`.
     * With no type argument recorded, every later `T`-return call on the
     * constructed value (`Box<Point>(...).get()`) can't resolve `T` via
     * CompilerTypes.substituteTypeVariable, stays an unsubstituted
     * TypeVariable, and the caller-side checkcast/owner ends up emitted
     * against `java.lang.Object` — NoSuchMethodError at runtime (measured:
     * `class Box<T>(T value) { get(): T {...} }`, #585). Resolved through
     * the same sa/scope-aware MemberResolver.resolveType already used for
     * `channel<T>()`/`listOf<T>()` above, not a bare toType, so a
     * user-declared reference type argument carries a proper internal
     * name in the checkcast.
     */
    private static List<Type> implicitCtorTypeArgs(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.typeArguments().isEmpty()) return List.of();
        List<Type> args = new ArrayList<>();
        for (String n : mc.typeArguments()) args.add(MemberResolver.resolveType(sa, n, scope));
        return args;
    }

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null && "channel".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            // channel<T>() -> Channel<T>; sem argumento é Channel<Unknown>
            Type elemType = mc.typeArguments().isEmpty()
                    ? Type.UnknownType.UNKNOWN
                    : MemberResolver.resolveType(sa, mc.typeArguments().get(0), scope);
            return new Type.ClassType("kof.concurrent", "Channel", List.of(elemType));
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
                    ctorClass.members(), mc.arguments().size());
            if (ctor != null) {
                sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                        ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
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
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(),
                    implicitCtorTypeArgs(sa, mc, scope));
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
                && KofWeb.contextCall(mc.methodName(), mc.arguments().size()) != null) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofWeb.contextCall(mc.methodName(), mc.arguments().size()).returnType();
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

    static Type inferTail(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null) {
            SymbolTable.Symbol localSym = scope != null ? scope.resolve(mc.methodName()) : null;
            if (localSym != null && localSym.type() instanceof Type.FunctionType lft) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, lft.parameterTypes(), mc.arguments());
                return lft.returnType();
            }
            if (localSym instanceof SymbolTable.LocalVariableSymbol
                    || localSym instanceof SymbolTable.ParameterSymbol) {
                // variável DECLARADA sendo chamada como função, mas não é
                // uma FunctionType. Distingue de "função inexistente"
                // (SEM015) — ex.: `(s) -> s(1)` com param sem tipo.
                if (sa.diagnostics() != null) {
                    String extra = (localSym.type() instanceof Type.UnknownType)
                            ? " (untyped — declare the type of the lambda parameter)"
                            : "";
                    sa.diagnostics().error(mc,
                            "variable '" + mc.methodName() + "' is not a function"
                                    + " and cannot be called" + extra,
                            "SEM015");
                }
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return Type.UnknownType.UNKNOWN;
            }
            if (sa.currentClassName() != null && !sa.currentClassName().isEmpty()) {
                SymbolTable.Symbol m = MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), mc.methodName());
                if (m instanceof SymbolTable.MethodSymbol ms) {
                    List<Type> argTypes = new ArrayList<>();
                    for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes(), mc.arguments());
                    sa.putResolvedMethod(mc, ms);
                    return ms.returnType();
                }
                // chamada implícita (this) herdada de SUPERCLASSE
                // EXTERNA: setContentView(...) dentro da Activity Kof
                SymbolTable.ClassSymbol self = sa.allClasses().get(sa.currentClassName());
                String superName = self != null ? self.superClass() : null;
                if (superName != null && sa.externalTypes() != null && !"Object".equals(superName)) {
                    String superInternal = superName.contains(".")
                            ? superName.replace('.', '/') : superName;
                    ExternalClasspath.MethodSignature sig = sa.externalTypes().resolveMethod(
                            superInternal, mc.methodName(), mc.arguments().size());
                    if (sig != null) {
                        List<Type> params = new ArrayList<>();
                        for (String d : sig.parameterDescriptors()) {
                            params.add(ExternalClasspath.typeFromDescriptor(d));
                        }
                        Type ret = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                        sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol(mc.methodName(),
                                superInternal, ret, params, 1,
                                SymbolTable.DispatchKind.INSTANCE));
                        return ret;
                    }
                }
            }
        }
        if (mc.receiver() == null
                && ("super".equals(mc.methodName()) || "this".equals(mc.methodName()))) {
            // super(args) / this(args): chamadas de construtor —
            // válidas apenas dentro do corpo de um construtor
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return Type.PrimitiveType.VOID;
        }
        if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
            ExpressionNode body = mc.arguments().get(0);
            Type t = SemExpressionTyper.inferType(sa, body, scope);
            // bug 46: `spawn { return 42 }` — inferType(lambda) dá
            // FunctionType([], Int), mas o Handle é do RETURN da lambda, não
            // do FunctionType. Guardar Handle<FunctionType> fazia `await h`
            // devolver FunctionType → `println` escolhia a sobrecarga String →
            // kof_println_string(42) → deref de ponteiro inválido (SIGSEGV
            // nativo). Espelha o lowerer (ExpressionStaticCallLowerer, bug 29).
            if (t instanceof Type.FunctionType ft) t = ft.returnType();
            // #141: corpo-bloco de UMA expressão ({@code spawn { "ok" }}) É o
            // retorno — o caso LambdaExpr dá VOID sem `return` explícito (a
            // emissão CompilerLambdaClass faz a conversão quando o tipo do
            // SAM não é void; sem este espelho o Handle saía Handle<Void> e o
            // `await` dava SEM033 falso-positivo (a triagem da mantenedora
            // classificou a rejeição como gap do typer, 0.4.1).
            if (Type.PrimitiveType.VOID.equals(t)
                    && body instanceof LambdaExpr sle
                    && sle.body().size() == 1
                    && sle.body().get(0) instanceof ExpressionStmt es) {
                Type et = SemExpressionTyper.inferType(sa, es.expression(), scope);
                if (!Type.UnknownType.UNKNOWN.equals(et) && !Type.isVoid(et)) t = et;
            }
            return new Type.ClassType("kof.concurrent", "Handle", List.of(t));
        }
        if (mc.receiver() == null && "cancel".equals(mc.methodName())
                && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return Type.PrimitiveType.BOOL;
        }
        if (mc.receiver() == null && "cancelled".equals(mc.methodName())
                && mc.arguments().isEmpty()) {
            return Type.PrimitiveType.BOOL;
        }
        if (mc.receiver() == null && "selectAny".equals(mc.methodName())
                && !mc.arguments().isEmpty()) {
            Type t0 = Type.UnknownType.UNKNOWN;
            for (ExpressionNode arg : mc.arguments()) t0 = SemExpressionTyper.inferType(sa, arg, scope);
            if (t0 instanceof Type.ClassType ct
                    && "kof.concurrent".equals(ct.packageName())
                    && !ct.typeArguments().isEmpty()) {
                return ct.typeArguments().get(0);
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() == null && "awaitTimeout".equals(mc.methodName())
                && mc.arguments().size() == 2) {
            Type t0 = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            SemExpressionTyper.inferType(sa, mc.arguments().get(1), scope);
            if (t0 instanceof Type.ClassType ct
                    && "kof.concurrent".equals(ct.packageName())
                    && !ct.typeArguments().isEmpty()) {
                return ct.typeArguments().get(0);
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() == null && ("poll".equals(mc.methodName())
                || "done".equals(mc.methodName()))) {
            Type t0 = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            if ("done".equals(mc.methodName())) return Type.PrimitiveType.BOOL;
            if (t0 instanceof Type.ClassType ct
                    && "kof.concurrent".equals(ct.packageName())
                    && !ct.typeArguments().isEmpty()) {
                return ct.typeArguments().get(0);
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
            Type t = SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            if (t instanceof Type.ClassType ct
                    && "kof.concurrent".equals(ct.packageName())
                    && !ct.typeArguments().isEmpty()) {
                return ct.typeArguments().get(0);
            }
            return Type.UnknownType.UNKNOWN;
        }
        Type topLevel = TopLevelCallTyper.infer(sa, mc, scope);
        if (topLevel != null) return topLevel;
        SymbolTable.ClassSymbol ctorClass = sa.allClasses().get(mc.methodName());
        if (ctorClass != null) {
            List<Type> ctorArgTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) {
                ctorArgTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            }
            SymbolTable.ConstructorSymbol ctor = SymbolTable.constructorFor(
                    ctorClass.members(), mc.arguments().size());
            if (ctor != null) {
                sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                        ctor.type(), ctor.parameterTypes(), ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
                // #323: face IMPLICITA da construcao (`A("x")` sem `new`) —
                // mesma regra da face NewExpr no SemExpressionTyper:
                // resolucao por aridade + conferencia de TIPO dos args,
                // overload-aware (irmao compativel passa). Sem isto a chamada
                // inventava <init>(String)V e o load estourava VerifyError.
                TypeChecker.checkCtorArgTypes(sa, mc, ctorClass.members(), mc.methodName(),
                        ctorArgTypes);
            } else {
                // §362/#545: mesmo gate do site de typper acima (face implicita
                // `Class(args)` sem `new`), fase/visitor irmão.
                reportNoCtorArity(sa, ctorClass, mc);
            }
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(),
                    implicitCtorTypeArgs(sa, mc, scope));
        }
        for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
        // String API: métodos que devolvem Int (indexOf, lastIndexOf,
        // length, compareTo...) — sem isso o var local infere Unknown
        // e o backend emite aload+if_icmp* (VerifyError)
        if (mc.receiver() != null) {
            Type recv = SemExpressionTyper.inferType(sa, mc.receiver(), scope);
            if (Type.isString(recv) || recv instanceof Type.NullableType nt && Type.isString(nt.inner())) {
                // Fonte ÚNICA do retorno dos métodos de String: o mesmo
                // StringMethodRegistry que o emit usa para o descritor JVM.
                // Antes este switch cobria só Int/Bool e o resto virava Unknown
                // — `var arr = s.toCharArray()` tipava o local como Object e o
                // emit gerava `getfield "?".length` / `Class.forName("?")`
                // (issues #158/#146/#147).
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) {
                    argTypes.add(sa.expressionTypes().get(arg));
                }
                StringMethodRegistry.Sig sig = StringMethodRegistry.stringMethodSignature(
                        mc.methodName(), mc.arguments().size(), argTypes);
                if (sig != null && !Type.isVoid(sig.returnType())) {
                    return sig.returnType();
                }
                return switch (mc.methodName()) {
                    case "indexOf", "lastIndexOf", "length", "size", "count",
                         "compareTo", "compareToIgnoreCase", "hashCode" -> Type.PrimitiveType.INT;
                    case "isEmpty" -> Type.PrimitiveType.BOOL;
                    default -> Type.UnknownType.UNKNOWN;
                };
            }
        }
        return Type.UnknownType.UNKNOWN;
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
                            + c.parameterTypes().size() + ")",
                    "SEM023");
        } else if (anyInit instanceof SymbolTable.ConstructorSet set
                && !set.constructors().isEmpty()) {
            sa.diagnostics().error(mc,
                    "no constructor of '" + mc.methodName() + "' with "
                            + mc.arguments().size() + " argument(s)",
                    "SEM023");
        }
    }
}

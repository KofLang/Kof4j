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
                sa.diagnostics().error("", 0, 0, 0,
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
                TypeChecker.checkCtorArgTypes(sa, ctorClass.members(), mc.methodName(),
                        ctorArgTypes);
            }
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
        }
        if (mc.receiver() == null && ("println".equals(mc.methodName()) || "print".equals(mc.methodName()))) {
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
        if (mc.receiver() == null && "Color".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 3)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.COLOR;
        }
        if (mc.receiver() == null && "Window".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.WINDOW;
        }
        if (mc.receiver() == null && "Label".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.LABEL;
        }
        if (mc.receiver() == null && "Button".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.BUTTON;
        }
        if (mc.receiver() == null && "Input".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.INPUT;
        }
        if (mc.receiver() == null && ("Column".equals(mc.methodName()) || "Row".equals(mc.methodName()))
                && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return "Column".equals(mc.methodName()) ? KofUi.COLUMN : KofUi.ROW;
        }
        if (mc.receiver() == null && "View".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.VIEW;
        }
        if (mc.receiver() == null && KofUi.isConstructor(mc.methodName())
                && !mc.arguments().isEmpty() && mc.arguments().size() <= 3) {
            Type ct = KofUi.constructorType(mc.methodName());
            if (KofUi.isLayoutType(ct) || KofUi.isStore(ct)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                return ct;
            }
        }
        if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 4) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.STYLE;
        }
        if (mc.receiver() == null && "Style".equals(mc.methodName()) && mc.arguments().size() == 1) {
            // D-UI-STYLE (UI007): declarative form — parse/validate in the
            // compiler (Q4) with a typed whitelist (Q3). SEM076 (unknown
            // property) / SEM077 (malformed) / SEM078 (invalid value); the
            // lowering re-parses only for the normalized text.
            ExpressionNode arg = mc.arguments().get(0);
            SemExpressionTyper.inferType(sa, arg, scope);
            KofStyleParser.report(sa.diagnostics(), mc.position(),
                    KofStyleParser.literalString(arg));
            return KofUi.STYLE;
        }
        if (mc.receiver() == null && "Link".equals(mc.methodName()) && mc.arguments().size() == 2) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.LINK;
        }
        if (mc.receiver() == null && "Image".equals(mc.methodName()) && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.IMAGE;
        }
        if (mc.receiver() == null && "Canvas".equals(mc.methodName()) && mc.arguments().size() == 2) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.CANVAS;
        }
        if (mc.receiver() == null && "Icon".equals(mc.methodName())
                && (mc.arguments().size() == 1 || mc.arguments().size() == 2)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.ICON;
        }
        if (mc.receiver() == null && "Font".equals(mc.methodName())
                && (mc.arguments().size() == 2 || mc.arguments().size() == 3)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            return KofUi.FONT;
        }
        if (mc.receiver() == null && "Component".equals(mc.methodName())
                && mc.arguments().size() == 1) {
            SemExpressionTyper.inferType(sa, mc.arguments().get(0), scope);
            return KofUi.COMPONENT;
        }
        return null;
    }

    /**
     * Cauda do case MethodCallExpr (após os branches com/sem receiver):
     * local function, chamada implícita (this), super/this ctor, helpers
     * de concorrência, função top-level (SEM015), construção implícita e
     * a API String — na ordem exata e com as guardas originais (alguns
     * branches só valem sem receiver).
     */
    static Type inferTail(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (mc.receiver() == null) {
            SymbolTable.Symbol localSym = scope != null ? scope.resolve(mc.methodName()) : null;
            if (localSym != null && localSym.type() instanceof Type.FunctionType lft) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, lft.parameterTypes());
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
                    sa.diagnostics().error("", 0, 0, 0,
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
                    List<Type> formalTypes = ms.parameterTypes();
                    for (int i = 0; i < mc.arguments().size(); i++) {
                        ExpressionNode arg = mc.arguments().get(i);
                        // #530: lambda arg with untyped params — use formal FunctionType as context
                        if (arg instanceof LambdaExpr le && i < formalTypes.size()
                                && formalTypes.get(i) instanceof Type.FunctionType ft) {
                            argTypes.add(SemExpressionTyper.inferLambdaWithContext(sa, le, scope, ft));
                        } else {
                            argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                        }
                    }
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, formalTypes);
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
        if (mc.receiver() == null && sa.unit() != null
                && !"println".equals(mc.methodName()) && !"print".equals(mc.methodName())
                && !"listOf".equals(mc.methodName()) && !"mapOf".equals(mc.methodName()) && !"setOf".equals(mc.methodName())
                && !"now".equals(mc.methodName()) && !"readLine".equals(mc.methodName())
                && !"readFile".equals(mc.methodName()) && !"writeFile".equals(mc.methodName())
                && !"super".equals(mc.methodName())
                && !KofIo.isConstructor(mc.methodName())
                && !KofUi.isConstructor(mc.methodName())
                && !KofWeb.isContextFunction(mc.methodName())
                && !KofScheduler.isSchedulerMethod(mc.methodName())
                && !"transaction".equals(mc.methodName())
                && !"uiNodesLive".equals(mc.methodName())
                && !"emit".equals(mc.methodName())
                && !"storesLive".equals(mc.methodName())) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            // SG-011B: junta TODOS os candidatos homônimos ELIGÍVEIS (mesmo
            // predicado de antes: sem type params próprios, e com args cobrindo
            // os parâmetros quando há defaults) e resolve por assinatura. Um
            // único candidato → caminho idêntico ao antigo (zero regressão);
            // ≥2 → TopLevelOverload.pick (oracle JVM); ambíguo → SEM057.
            boolean found = false;
            List<TopLevelOverload.Candidate> cands = new ArrayList<>();
            for (AstNode d : sa.unit().declarations()) {
                if (d instanceof FunctionDeclarationNode fn && fn.name().equals(mc.methodName())) {
                    found = true;
                    boolean hasDefaults = fn.parameters().stream()
                            .anyMatch(p -> p.defaultExpression() != null);
                    if (fn.typeParameters().isEmpty() && (!hasDefaults
                            || mc.arguments().size() >= TopLevelOverload.requiredArityOf(fn))) {
                        List<Type> paramTypes = new ArrayList<>();
                        for (FormalParameterNode p : fn.parameters()) paramTypes.add(MemberResolver.resolveType(sa, p.type(), scope));
                        cands.add(new TopLevelOverload.Candidate(fn, paramTypes,
                                TopLevelOverload.requiredArityOf(fn)));
                    }
                } else if (d instanceof ExternalFunctionNode ext && ext.name().equals(mc.methodName())) {
                    // FFI (TIER 2.1): chamada a `extern` declarado resolve pelo
                    // contrato (tipo de retorno), nunca SEM015 — o binding real é
                    // lowering por target (JVM/Native); JS emite FFI002.
                    found = true;
                    Type extRet = MemberResolver.resolveType(sa, ext.returnType(), scope);
                    if (!Type.isVoid(extRet)) {
                        sa.putExpressionType(mc, extRet);
                        return extRet;
                    }
                }
            }
            if (!cands.isEmpty()) {
                TopLevelOverload.Status[] st = new TopLevelOverload.Status[1];
                int sel = TopLevelOverload.pick(cands, argTypes, st);
                if (sel < 0 && st[0] == TopLevelOverload.Status.AMBIGUOUS) {
                    if (sa.diagnostics() != null) {
                        sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                                mc.position() != null ? mc.position().line() : 0,
                                mc.position() != null ? mc.position().column() : 0, 0,
                                "call to '" + mc.methodName() + "' is ambiguous between "
                                        + cands.size() + " overloads — add a cast to pick one",
                                "SEM057");
                    }
                } else {
                    if (sel < 0) sel = 0; // NO_MATCH → reporta SEM013/SEM014 no candidato 0, como antes
                    TopLevelOverload.Candidate chosen = cands.get(sel);
                    // §231: chamada curta num candidato com default resolve pelo
                    // WRAPPER de prefixo (mesmo `subList(0, nArgs)` que o
                    // ExpressionMethodCallLowerer emite) — validar contra os
                    // parâmetros recebidos, não contra a assinatura total, senão
                    // a chamada boa cai em SEM013 "expected N but got nArgs".
                    List<Type> chosenFormals = chosen.paramTypes();
                    if (argTypes.size() < chosen.totalArity()
                            && argTypes.size() >= chosen.requiredArity()) {
                        chosenFormals = chosenFormals.subList(0, argTypes.size());
                    }
                    TypeChecker.checkArgTypes(sa.diagnostics(), mc.methodName(), argTypes, chosenFormals);
                    // #266 (c) — DECISIONS §7: `null` literal em parâmetro
                    // primitivo NÃO-nullable é SEM048 em compile-time, nunca
                    // VerifyError silencioso no load (a chamada top-level não
                    // passa por resolvedMethods, por isso o check direto aqui).
                    SemanticAnalyzer.checkNullArgs(sa.diagnostics(), mc.arguments(),
                            mc.position(), chosenFormals, mc.methodName());
                    // registra o tipo de retorno da função top-level para o var
                    // local inferir (evita Unknown que quebra a resolução de
                    // métodos do receiver)
                    Type fnRet = MemberResolver.resolveType(sa, chosen.fn().returnType(), scope);
                    if (!Type.isVoid(fnRet)) {
                        sa.putExpressionType(mc, fnRet);
                        return fnRet;
                    }
                }
            }
            if (!found && sa.diagnostics() != null && !sa.allClasses().containsKey(mc.methodName())) {
                sa.diagnostics().error("", 0, 0, 0,
                        "Undefined function: '" + mc.methodName() + "'", "SEM015");
            }
        }
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
                TypeChecker.checkCtorArgTypes(sa, ctorClass.members(), mc.methodName(),
                        ctorArgTypes);
            }
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(), List.of());
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
}

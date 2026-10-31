package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Cauda do case MethodCallExpr de {@link BuiltinCallTyper} (após os branches
 * com/sem receiver): local function, chamada implícita (this), super/this
 * ctor, helpers de concorrência, função top-level (SEM015), construção
 * implícita e a API String — na ordem exata e com as guardas originais
 * (alguns branches só valem sem receiver). Extraída de BuiltinCallTyper
 * (REFACTOR-500, check_500 crítico) — pura extração, zero mudança de
 * comportamento; BuiltinCallTyper.inferTail delega aqui.
 */
final class BuiltinCallTailTyper {

    private BuiltinCallTailTyper() {}

    static Type inferTail(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        // §243 função (#931/#932): mesmo veredicto da entrada `infer` — os
        // branches de builtin de nome fixo (poll/done/readFile/writeFile/…)
        // não interceptam um nome que o programa declara; TopLevelCallTyper
        // tipa e registra, void → Unknown como a cauda sempre devolveu.
        // EXCEÇÃO (oráculo Java): DENTRO de uma classe, o método da própria
        // classe (implicit this, resolução a jusante) vence top-level e
        // builtin — `readBits(1)` de Inflate.kf não é o `readBits(Int[],Int,Int)`
        // top-level de Qrcode.kf (#932 face 2).
        if (mc.receiver() == null && KofShadowing.userDeclares(sa, mc.methodName())
                && !(sa.currentClassName() != null && !sa.currentClassName().isEmpty()
                        && MemberResolver.resolveInHierarchy(sa, sa.currentClassName(), mc.methodName()) != null)) {
            Type declared = TopLevelCallTyper.infer(sa, mc, scope);
            return declared != null ? declared : Type.UnknownType.UNKNOWN;
        }
        if (mc.receiver() == null) {
            SymbolTable.Symbol localSym = scope != null ? scope.resolve(mc.methodName()) : null;
            if (localSym != null && localSym.type() instanceof Type.FunctionType lft) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(), argTypes, lft.parameterTypes(), mc.arguments());
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
                    TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(), argTypes, ms.parameterTypes(), mc.arguments());
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
                    ctorClass.members(), mc.arguments().size(), ctorArgTypes);
            if (ctor != null) {
                List<Type> ctorParamTypes = ctor.effectiveParameterTypes(mc.arguments().size());
                sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol("<init>", mc.methodName(),
                        ctor.type(), ctorParamTypes, ctor.accessFlags(), SymbolTable.DispatchKind.STATIC));
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
                BuiltinCallTyper.reportNoCtorArity(sa, ctorClass, mc);
            }
            // #585: MESMO furo do site irmão acima — o witness descartado aqui
            // também deixava o receptor cru (visitor inferTail).
            return new Type.ClassType(ctorClass.packageName(), ctorClass.name(),
                    BuiltinCallTyper.resolveWitnessTypeArgs(sa, mc, scope));
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

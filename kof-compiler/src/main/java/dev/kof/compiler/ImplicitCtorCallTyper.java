package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BuiltinCallTyper 616→500): a tipagem da
 * construção implícita `Classe(args)` sem `new`, nos dois sítios da cadeia de
 * chamadas (§362/#545, #323, #585, §713 — os comentários de raiz viajam com o
 * código). Os sítios ficam separados de propósito: só `infer` recusa classe
 * abstrata/interface e infere type-args. Nada muda no comportamento.
 */
final class ImplicitCtorCallTyper {

    private ImplicitCtorCallTyper() {}

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
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

    static Type inferTail(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope,
            SymbolTable.ClassSymbol ctorClass) {
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
            reportNoCtorArity(sa, ctorClass, mc);
        }
        // #585: MESMO furo do site irmão acima — o witness descartado aqui
        // também deixava o receptor cru (visitor inferTail).
        return new Type.ClassType(ctorClass.packageName(), ctorClass.name(),
                resolveWitnessTypeArgs(sa, mc, scope));
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
     *  implícita (`ClassName<T>(...)`); sem witness, raw (compat aditivo). */
    private static List<Type> resolveWitnessTypeArgs(SemanticAnalyzer sa, MethodCallExpr mc,
            SymbolTable scope) {
        if (mc.typeArguments().isEmpty()) return List.of();
        List<Type> resolved = new ArrayList<>();
        for (var ta : mc.typeArguments()) {
            resolved.add(MemberResolver.resolveType(sa, ta, scope));
        }
        return resolved;
    }
}

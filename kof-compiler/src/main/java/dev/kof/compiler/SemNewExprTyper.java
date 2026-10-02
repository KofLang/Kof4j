package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipo de `new T(...)` (NewExpr), extraída do
 * SemExpressionTyper (§442 — split do gate check_500: a classe-mãe cruzou
 * a linha critica de 600 linhas). Sem estado próprio — recebe o analyzer
 * (estado compartilhado) por parâmetro. Espelha `SemMethodCallTyper.infer`.
 */
final class SemNewExprTyper {

    private SemNewExprTyper() {}

    static Type infer(SemanticAnalyzer sa, NewExpr ne, SymbolTable scope) {
        Type coll = CompilerTypes.builtinCollectionType(ne.typeName(), sa.unit(), sa);
        if (coll != null) {
            // #193/#198: aplicar os type-arguments no tipo da colecao,
            // espelhando o ExpressionTyper do emit (que sempre aplicou).
            // Sem isso `new List<() -> Int>()` tipava como List<Unknown>
            // no SEMANTICO e o get(0) devolvia Unknown -> `f()` dava
            // SEM015 (#193) e o call-chainado `get(0)()` emitia Methodref
            // vazio (ClassFormatError, #198).
            if (!ne.typeArguments().isEmpty() && coll instanceof Type.ClassType ct) {
                // #697: o ramo classe abaixo já qualificava os type-args via
                // toType(…, sa); o ramo coleção usava `Type::of` cru — o arg
                // `pkg.Rotulo`/`Rotulo` de mesmo pacote ficava
                // ClassType("", "dominio.Rotulo") e divergia do tipo declarado
                // (SEM021 espúrio). Mesmo caminho analisador-ciente.
                coll = new Type.ClassType(ct.packageName(), ct.name(),
                        ne.typeArguments().stream()
                                .map(n -> CompilerTypes.toType(n, sa.unit(), sa)).toList());
            }
            return coll;
        }
        SymbolTable.ClassSymbol cs = sa.getClass(ne.typeName());
        if (cs != null) {
            // SG-017 (SEM041): classe abstrata não pode ser instanciada.
            if (sa.abstractClasses().contains(ne.typeName()) && sa.diagnostics() != null) {
                sa.diagnostics().error(ne,
                        "cannot instantiate abstract class '" + ne.typeName() + "'",
                        "SEM041");
            }
            // #340 (SEM071): interface não é instanciável — `new I()`.
            ClassShapeChecks.checkInstantiable(sa, ne.typeName());
            // Inferencia dos argumentos: o efeito colateral importa
            // (cache expressionTypes + diagnostics de SEM nas exprs), a
            // resolucao do construtor e por aridade (constructorFor
            // aceita int) — o container de tipos era write-only
            // (CodeQL unused-container: achado real, nao FP).
            for (ExpressionNode arg : ne.arguments()) {
                SemExpressionTyper.inferType(sa, arg, scope);
            }
            SymbolTable.ConstructorSymbol ctor3 =
                    SymbolTable.constructorFor(cs.members(), ne.arguments().size());
            if (ctor3 != null) {
                sa.putResolvedConstructor(ne, ctor3);
                // #323: a RESOLUCAO era so por aridade; o tipo dos
                // argumentos nunca era conferido contra a assinatura
                // do construtor. Sem isto, `new A("x")` num ctor
                // `(Int)` compila e a chamada inventa <init>(String)V
                // → VerifyError no load (R6/Q7: nunca silencioso).
                // Overload-aware: irmao de mesma aridade que casa
                // (isAssignable) passa — mesmo predicado do emit.
                List<Type> argTypes3 = new ArrayList<>();
                for (ExpressionNode arg : ne.arguments()) {
                    argTypes3.add(SemExpressionTyper.inferType(sa, arg, scope));
                }
                TypeChecker.checkCtorArgTypes(sa, ne, cs.members(), ne.typeName(),
                        argTypes3);
            } else if (sa.diagnostics() != null) {
                SymbolTable.Symbol anyInit = cs.members().resolve("<init>");
                if (anyInit instanceof SymbolTable.ConstructorSymbol c) {
                    sa.diagnostics().error(ne,
                            "no constructor of '" + ne.typeName() + "' with "
                                    + ne.arguments().size() + " argument(s) (expected "
                                    + c.parameterTypes().size() + ")",
                            "SEM023");
                } else if (anyInit instanceof SymbolTable.ConstructorSet set
                        && !set.constructors().isEmpty()) {
                    sa.diagnostics().error(ne,
                            "no constructor of '" + ne.typeName() + "' with "
                                    + ne.arguments().size() + " argument(s)",
                            "SEM023");
                }
            }
            // X5.3 (D-TYPE-VARIANCE): o EMIT (ExpressionTyper:185)
            // sempre aplicou os type-args do NewExpr ao tipo do raw —
            // o SEMANTICO so fazia isso p/ colecoes builtin (#193/#198).
            // Sem alinhar, `Box<Dog>()` tipava raw (args vazios) e a
            // checagem de variancia nunca via args concretos (o §270
            // invariante virava permissivo). Aqui espelha o emit.
            List<Type> newArgs = ne.typeArguments().isEmpty() ? List.of()
                    : ne.typeArguments().stream()
                            .map(n -> CompilerTypes.toType(n, sa.unit(), sa)).toList();
            return new Type.ClassType(cs.packageName(), cs.name(), newArgs);
        }
        // classe EXTERNA (android.webkit.WebView etc.): qualifica pelo
        // import e registra o construtor do classpath — sem isso a
        // variável fica Unknown e toda a cadeia de chamadas seguinte
        // perde o tipo
        String qname = ne.typeName();
        if (!qname.contains(".")) {
            Type viaImport = MemberResolver.qualifyViaImports(sa.unit(), qname,
                    sa.externalTypes());
            if (viaImport != null) qname = viaImport instanceof Type.ClassType qt
                    ? qt.packageName() + "." + qt.name() : qname;
        }
        if (qname.contains(".") && sa.externalTypes() != null) {
            String internal = qname.replace('.', '/');
            if (sa.externalTypes().knows(internal)) {
                ExternalClasspath.MethodSignature sig =
                        sa.externalTypes().resolveConstructor(internal, ne.arguments().size());
                if (sig != null) {
                    List<Type> params = new ArrayList<>();
                    for (String d : sig.parameterDescriptors()) {
                        params.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    sa.putResolvedConstructor(ne, new SymbolTable.ConstructorSymbol(
                            internal.substring(internal.lastIndexOf('/') + 1), params, 1));
                }
                int lastDot = qname.lastIndexOf('.');
                return new Type.ClassType(qname.substring(0, lastDot),
                        qname.substring(lastDot + 1), List.of());
            }
        }
        return Type.UnknownType.UNKNOWN;
    }
}

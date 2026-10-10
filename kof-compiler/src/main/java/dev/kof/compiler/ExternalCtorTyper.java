package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * §393 — #568: construtor EXTERNO implicito (`Greeter(args)` sem `new`) de
 * classe carregada pelo ExternalClasspath (--classpath/--deps, §134). O
 * typer nao consultava os entries e a chamada caia em SEM015 "Undefined
 * function" mesmo com o emit resolvendo o construtor e o programa rodando
 * (bug (ii) do #566; a face `new` ja resolvia em SemExpressionTyper/
 * ExpressionLowerer). Aqui resolve-se pela TABELA DE CONSTRUTORES PUBLICOS
 * do .class (aridade + tipos declarados dos args, Q3) e registra-se o
 * simbolo + o tipo da expressao — o receiver fica narrowed para o `var` e
 * para a cadeia de metodos seguinte. SEM015 continua valendo para nome que
 * nao e funcao nem classe externa (R6, zero bypass): a classe existir fora e
 * nao ter construtor publico compativel e SEM023 honesto de construtor,
 * nunca "Undefined function". Precedencia preservada (freeze regra 2):
 * funcao top-level/`extern` declarada vence — o hook so roda no `!found`.
 */
final class ExternalCtorTyper {

    private ExternalCtorTyper() {}

    /**
     * Veredicto: type != null → construtor resolvido (tipo = classe externa);
     * diagnosed → a classe existe la fora mas nao ha construtor publico
     * compativel (diagnostico SEM023 ja emitido — SEM015 NAO deve vir);
     * senao, nao e classe externa (fluxo SEM015 de sempre).
     */
    record Outcome(Type type, boolean diagnosed) {
        static final Outcome NOT_EXTERNAL = new Outcome(null, false);
    }

    static Outcome infer(SemanticAnalyzer sa, MethodCallExpr mc, List<Type> argTypes) {
        if (sa.externalTypes() == null || sa.unit() == null) return Outcome.NOT_EXTERNAL;
        Type qualified = MemberResolver.qualifyViaImports(sa.unit(), mc.methodName(),
                sa.externalTypes());
        if (!(qualified instanceof Type.ClassType ct) || ct.packageName().isEmpty()) {
            return Outcome.NOT_EXTERNAL;
        }
        String internal = ct.internalName();
        if (!sa.externalTypes().knows(internal)) return Outcome.NOT_EXTERNAL;
        ExternalClasspath.MethodSignature sig =
                sa.externalTypes().resolvePublicConstructor(internal, mc.arguments().size());
        if (sig == null) {
            if (sa.diagnostics() != null) {
                sa.diagnostics().error(mc,
                        "no public constructor of '" + mc.methodName() + "' with "
                                + mc.arguments().size() + " argument(s)",
                        "SEM023");
            }
            return new Outcome(null, true);
        }
        List<Type> formals = new ArrayList<>();
        for (String d : sig.parameterDescriptors()) {
            formals.add(ExternalClasspath.typeFromDescriptor(d));
        }
        // mesmo gate das faces `extern`/top-level: tipo do arg contra o
        // formal declarado (SEM014) e `null` em primitivo (SEM048) — soltar
        // virava <init>(...)V fantasma e VerifyError no load (R6/Q0).
        TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(), argTypes, formals, mc.arguments());
        SemanticAnalyzer.checkNullArgs(sa.diagnostics(), mc.arguments(), mc.position(),
                formals, mc.methodName());
        sa.putResolvedMethod(mc, new SymbolTable.MethodSymbol("<init>", internal, ct,
                formals, AccessFlags.PUBLIC, SymbolTable.DispatchKind.STATIC));
        sa.putExpressionType(mc, ct);
        return new Outcome(ct, false);
    }
}

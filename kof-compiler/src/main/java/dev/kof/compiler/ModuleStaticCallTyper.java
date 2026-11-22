package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Braço estático de CLASSE DO MÓDULO ({@code Desconto.aplicar(c)}) do
 * {@link MemberCallTyper}, extraído para manter o arquivo sob o gate ≤600
 * (mesmo padrão de {@link ExternalStaticCallTyper}, que saiu pela mesma razão).
 *
 * <p>#1179: quando o receiver é uma classe Kof do módulo mas o método não
 * resolve (nome ausente, ou aridade que nenhum overload casa), o front-end
 * recusa com {@code SEM025} — o espelho do braço da classe EXTERNA. Sem esta
 * face a chamada caía adiante sem símbolo resolvido e o lowering emitia uma
 * chamada sem receiver → {@code VerifyError: Operand stack underflow} no load,
 * com o {@code check} dizendo "no errors" (R6/Q7).
 */
final class ModuleStaticCallTyper {

    private ModuleStaticCallTyper() {}

    /**
     * Retorno da chamada estática de classe do módulo, ou {@code null} quando
     * a face não se aplica (o chamador segue a própria cadeia de fallbacks).
     * Quando aplica e o método não existe, emite {@code SEM025} e devolve
     * {@link Type.UnknownType#UNKNOWN}.
     */
    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        if (!(mc.receiver() instanceof IdentifierExpr krid)
                || SemExpressionTyper.isLocalName(scope, krid.name())
                || !sa.allClasses().containsKey(krid.name())
                || sa.importShadowsProjectClass(krid.name())) {
            return null;
        }
        SymbolTable.Symbol km = MemberResolver.resolveInHierarchy(sa, krid.name(), mc.methodName());
        SymbolTable.ClassSymbol kt = sa.allClasses().get(krid.name());
        if (km instanceof SymbolTable.MethodSet set) {
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
            SymbolTable.MethodSymbol kms = set.select(mc.arguments().size(), argTypes);
            if (kms != null) {
                sa.putResolvedMethod(mc, staticSymbol(kms, kt));
                TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(), argTypes, kms.parameterTypes(), mc.arguments());
                return kms.returnType();
            }
        }
        if (km instanceof SymbolTable.MethodSymbol kms
                && kms.parameterTypes().size() == mc.arguments().size()) {
            sa.putResolvedMethod(mc, staticSymbol(kms, kt));
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            TypeChecker.checkArgTypes(sa, sa.diagnostics(), mc.methodName(), MemberCallTyper.inferArgTypes(sa, mc, scope), kms.parameterTypes(), mc.arguments());
            return kms.returnType();
        }
        if (sa.diagnostics() != null) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            sa.diagnostics().error(mc,
                    "Cannot resolve static method '" + mc.methodName()
                            + "' on type '" + krid.name() + "'",
                    "SEM025");
        }
        return Type.UnknownType.UNKNOWN;
    }

    /** Empacota o método estático resolvido de uma classe do módulo como STATIC. */
    private static SymbolTable.MethodSymbol staticSymbol(SymbolTable.MethodSymbol kms,
            SymbolTable.ClassSymbol kt) {
        return new SymbolTable.MethodSymbol(kms.name(), kt.internalName(), kms.returnType(),
                kms.parameterTypes(), kms.accessFlags(), SymbolTable.DispatchKind.STATIC);
    }
}

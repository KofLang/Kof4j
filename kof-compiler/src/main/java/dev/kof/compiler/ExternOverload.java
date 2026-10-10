package dev.kof.compiler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * #763 (`D-MAINT-BATCH-0610`/B): seleção de overload por assinatura nas
 * declarações {@code extern} de mesmo nome. Duas linhas `extern "libc.so.6"
 * syscall(Int): Long` / `syscall(Int, Long): Long` apontam para o MESMO
 * símbolo C (variádico, como `printf`/`syscall`) e são OVERLOADS legítimos,
 * não colisão — o registro por nome antigo (mapa de 1 slot) fazia a última
 * declaração vencer e toda chamada de outra aridade morrer SEM013.
 *
 * <p>Política honesta (R6/Q7): aridade é o desempate; tipos são conferidos no
 * candidato escolhido (SEM014, mesmo texto de antes). Candidatos com a MESMA
 * aridade que a chamada: um único atribível vence; dois atribíveis = SEM057
 * (nunca 50/50 silencioso); nenhum atribível = SEM014 no primeiro declarado
 * (paridade com o comportamento pré-#763). Nenhuma aridade casa = SEM013 no
 * candidato mais próximo (menor diferença; empate → primeiro declarado, o
 * mesmo slot que o registro antigo deixava).
 *
 * <p>O candidato escolhido é gravado por call-site (identidade do nó) e o
 * lowering consome a MESMA resolução — nunca um segundo oracle.
 */
final class ExternOverload {

    private ExternOverload() {}

    private static final Map<MethodCallExpr, ExternalFunctionNode> CHOICES = new IdentityHashMap<>();

    static void record(MethodCallExpr mc, ExternalFunctionNode ext) { CHOICES.put(mc, ext); }

    static ExternalFunctionNode get(MethodCallExpr mc) { return CHOICES.get(mc); }

    /** Devolve o candidato escolhido, ou null quando um erro já foi emitido. */
    static ExternalFunctionNode choose(SemanticAnalyzer sa, MethodCallExpr mc,
                                       List<ExternalFunctionNode> cands, SymbolTable scope,
                                       List<Type> argTypes) {
        if (cands.isEmpty()) return null;
        if (cands.size() == 1) {
            ExternalFunctionNode only = cands.get(0);
            if (!checkArity(sa, mc, only, argTypes)) return null;
            checkTypes(sa, mc, only, scope, argTypes);
            return only;
        }
        List<ExternalFunctionNode> arityMatch = new ArrayList<>();
        for (ExternalFunctionNode ext : cands) {
            if (ext.parameters().size() == argTypes.size()) arityMatch.add(ext);
        }
        if (arityMatch.isEmpty()) {
            ExternalFunctionNode nearest = cands.get(0);
            int best = Integer.MAX_VALUE;
            for (ExternalFunctionNode ext : cands) {
                int d = Math.abs(ext.parameters().size() - argTypes.size());
                if (d < best) { best = d; nearest = ext; }
            }
            checkArity(sa, mc, nearest, argTypes);
            return null;
        }
        if (arityMatch.size() == 1) {
            ExternalFunctionNode only = arityMatch.get(0);
            checkTypes(sa, mc, only, scope, argTypes);
            return only;
        }
        List<ExternalFunctionNode> attrib = new ArrayList<>();
        for (ExternalFunctionNode ext : arityMatch) {
            if (argsAssignable(sa, ext, scope, argTypes)) attrib.add(ext);
        }
        if (attrib.size() == 1) {
            checkTypes(sa, mc, attrib.get(0), scope, argTypes);
            return attrib.get(0);
        }
        if (attrib.isEmpty()) {
            checkTypes(sa, mc, arityMatch.get(0), scope, argTypes);
            return null;
        }
        if (sa.diagnostics() != null) {
            diag(sa, mc, "call to '" + mc.methodName() + "' is ambiguous between "
                    + attrib.size() + " extern overloads — add a cast to pick one", "SEM057");
        }
        return null;
    }

    private static boolean checkArity(SemanticAnalyzer sa, MethodCallExpr mc,
                                      ExternalFunctionNode ext, List<Type> argTypes) {
        if (argTypes.size() == ext.parameters().size()) return true;
        if (sa.diagnostics() != null) {
            diag(sa, mc, "Wrong number of arguments for '" + ext.name() + "': expected "
                    + ext.parameters().size() + " but got " + argTypes.size(), "SEM013");
        }
        return false;
    }

    private static void checkTypes(SemanticAnalyzer sa, MethodCallExpr mc,
                                   ExternalFunctionNode ext, SymbolTable scope,
                                   List<Type> argTypes) {
        List<Type> formals = new ArrayList<>();
        for (FormalParameterNode p : ext.parameters()) {
            formals.add(MemberResolver.resolveType(sa, p.type(), scope));
        }
        for (int ai = 0; ai < argTypes.size() && ai < formals.size(); ai++) {
            Type at = argTypes.get(ai), ft = formals.get(ai);
            if (!Type.isUnknown(at) && !Type.isUnknown(ft)
                    && !TypeChecker.isAssignable(at, ft)) {
                if (sa.diagnostics() != null) {
                    diag(sa, mc, "Argument " + (ai + 1) + " of '" + ext.name() + "': expected '"
                            + Type.display(ft) + "' but got '" + Type.display(at) + "'", "SEM014");
                }
                break;
            }
        }
    }

    private static boolean argsAssignable(SemanticAnalyzer sa, ExternalFunctionNode ext,
                                          SymbolTable scope, List<Type> argTypes) {
        for (int ai = 0; ai < argTypes.size(); ai++) {
            Type at = argTypes.get(ai);
            Type ft = MemberResolver.resolveType(sa, ext.parameters().get(ai).type(), scope);
            if (!Type.isUnknown(at) && !Type.isUnknown(ft) && !TypeChecker.isAssignable(at, ft)) {
                return false;
            }
        }
        return true;
    }

    private static void diag(SemanticAnalyzer sa, MethodCallExpr mc, String msg, String code) {
        sa.diagnostics().error(mc.position() != null ? mc.position().file() : "",
                mc.position() != null ? mc.position().line() : 0,
                mc.position() != null ? mc.position().column() : 0, 0, msg, code);
    }
}

package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * SG-011B — resolução de sobrecarga de função TOP-LEVEL (oracle: comportamento
 * da JVM). Duas ou mais funções homônimas de assinaturas diferentes coexistem;
 * a chamada seleciona o candidato aplicável mais específico, exatamente como o
 * {@code invokestatic} da JVM resolve por descritor. Chamada ambígua ou
 * inexistente é ERRO de compilação (nunca escolha silenciosa — regra R6), e o
 * erro é idêntico nos 5 targets porque acontece no frontend, antes de qualquer
 * backend.
 *
 * <p><b>Invariante de não-regressão:</b> todo programa que compilava antes deste
 * item tinha NO MÁXIMO UMA função homônima (≥2 era SEM047). Quando há um único
 * candidato a seleção retorna-o sempre ({@link #pick} devolve índice 0) e cada
 * chamador segue seu caminho original (default-params, generics) inalterado.
 *
 * <p>{@link #sigTag} é também a base do mangling de símbolo nos backends que
 * não têm sobrecarga nativa (Native: símbolo asm sufixado por assinatura;
 * JS: nome de função sufixado) — os 3 targets compilados passam a usar a
 * MESMA chave de assinatura que o descritor JVM usa, o que é exatamente a
 * paridade pedida (oracle JVM).
 */
public final class TopLevelOverload {

    private TopLevelOverload() {}

    /** Sufixo estável derivado dos tipos de parâmetro (ex.: {@code _I_J}).
     *  Usado como chave de mangle no Native e (quando ambíguo) no JS. */
    public static String sigTag(List<Type> ps) {
        StringBuilder s = new StringBuilder();
        for (Type t : ps) s.append('_').append(typeTag(t));
        return s.toString();
    }

    static String typeTag(Type t) {
        if (t instanceof Type.PrimitiveType pt) return switch (Type.canonicalPrimitiveName(pt.name())) {
            case "int" -> "I"; case "long" -> "J"; case "double" -> "D"; case "float" -> "F";
            case "boolean" -> "Z"; case "byte" -> "B"; case "char" -> "C"; case "short" -> "S";
            default -> "V"; };
        if (t instanceof Type.NullableType nt) return typeTag(nt.inner()) + "q";
        if (t instanceof Type.ArrayType at) return "A" + typeTag(at.componentType());
        if (t instanceof Type.FunctionType) return "L";
        if (t instanceof Type.ClassType ct) {
            String n = ct.name().replace("/", "_").replace(".", "_").replace("-", "_");
            return n.isEmpty() ? "O" : n;
        }
        return "O";
    }

    /** Um candidato: a declaração + seus tipos de parâmetro já resolvidos pelo
     *  chamador (cada sítio resolve à sua maneira) + aridade mínima (parâmetros
     *  antes do primeiro com valor default). */
    record Candidate(FunctionDeclarationNode fn, List<Type> paramTypes, int requiredArity) {
        int totalArity() { return paramTypes.size(); }
    }

    /** Resultado da seleção. */
    enum Status { NO_MATCH, AMBIGUOUS }

    /**
     * Escolhe o índice do candidato mais específico entre {@code cands} para os
     * tipos de argumento {@code argTypes}. Retorna:
     * <ul>
     *   <li>índice {@code >= 0} — candidato selecionado (inclusive quando há um
     *       único candidato: devolve 0, preservando o comportamento antigo);</li>
     *   <li>{@code -1} com {@code out[0] = NO_MATCH} — nenhum aplicável (o
     *       chamador reporta SEM013/SEM014 como antes, sobre o candidato 0);</li>
     *   <li>{@code -1} com {@code out[0] = AMBIGUOUS} — empate entre aplicáveis
     *       (o chamador reporta SEM057).</li>
     * </ul>
     * Candidato é aplicável quando {@code requiredArity <= nArgs <= totalArity}
     * e cada argumento casa (assignable) com o parâmetro correspondente. Entre
     * aplicáveis vence o mais específico: maior contagem de igualdade EXATA de
     * tipo; se o empate persistir, decide-se por subtipagem (A mais específico
     * que B se cada parâmetro de A é assignable ao de B e algum é estrito).
     */
    static int pick(List<Candidate> cands, List<Type> argTypes, Status[] out) {
        if (out != null && out.length > 0) out[0] = null;
        if (cands == null || cands.isEmpty()) return -1;
        // Candidato único: sempre ele (zero-regressão garantida).
        if (cands.size() == 1) return 0;
        int n = argTypes.size();
        List<Integer> applicable = new ArrayList<>();
        for (int i = 0; i < cands.size(); i++) {
            Candidate c = cands.get(i);
            if (n < c.requiredArity() || n > c.totalArity()) continue;
            boolean fits = true;
            for (int p = 0; p < n; p++) {
                Type arg = argTypes.get(p);
                Type par = c.paramTypes().get(p);
                if (!Type.isUnknown(arg) && !Type.isUnknown(par)
                        && !TypeChecker.isAssignable(arg, par)) { fits = false; break; }
            }
            if (fits) applicable.add(i);
        }
        if (applicable.isEmpty()) { if (out != null && out.length > 0) out[0] = Status.NO_MATCH; return -1; }
        if (applicable.size() == 1) return applicable.get(0);
        // Empate de aplicáveis: pontua por igualdade exata de tipo.
        int best = -1; int bestScore = -1; boolean tie = false;
        for (int idx : applicable) {
            Candidate c = cands.get(idx);
            int score = 0;
            for (int p = 0; p < n; p++) {
                if (argTypes.get(p) != null && argTypes.get(p).equals(c.paramTypes().get(p))) score++;
            }
            if (score > bestScore) { bestScore = score; best = idx; tie = false; }
            else if (score == bestScore) tie = true;
        }
        if (!tie) return best;
        // Mesmo score: tentar desempate por especificidade (A ⊑ B em todos os
        // parâmetros e estrito em ao menos um) — como a JVM desempata Number vs Int.
        for (int a : applicable) {
            boolean strictlyBest = true; boolean strictAny = false;
            for (int b : applicable) {
                if (a == b) continue;
                boolean aMoreSpecific = true; boolean anyStrict = false;
                for (int p = 0; p < n; p++) {
                    Type ta = cands.get(a).paramTypes().get(p);
                    Type tb = cands.get(b).paramTypes().get(p);
                    if (!TypeChecker.isAssignable(ta, tb)) { aMoreSpecific = false; break; }
                    if (!tb.equals(ta)) anyStrict = true;
                }
                if (!aMoreSpecific) { strictlyBest = false; break; }
                if (anyStrict) strictAny = true;
            }
            if (strictlyBest && strictAny) return a;
        }
        if (out != null && out.length > 0) out[0] = Status.AMBIGUOUS;
        return -1;
    }
}

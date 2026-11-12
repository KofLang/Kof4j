package dev.kof.compiler;

import java.util.List;

/**
 * Kof.expr fatia 2 (`D-KOF-EXPR`) — as perguntas ESTRUTURAIS que o lowering faz
 * sobre a árvore antes de emitir qualquer estado: o corpo de um quantificador
 * é ANULÁVEL (pode casar texto vazio), há um quantificador SEM TETO dentro dele
 * (a forma exponencial), quantas OCORRÊNCIAS de quantificador a árvore tem (o
 * número de slots de contador), e quantos valores escalares um literal de
 * {@code one_of}/{@code none_of} tem.
 *
 * <p>É a base das três recusas do §12 do plano — {@code EXPR002} (sem teto
 * dentro de sem teto) e {@code EXPR003} (anulável dentro de sem teto) — e da
 * alocação do contador por ocorrência. Extraído de
 * {@code CompilerExprLowering} pelo ratchet de 500 linhas; a lógica é a mesma.
 */
final class KofExprShape {

    private static final java.util.Map<String, int[]> NAMED = java.util.Map.of(
            "optional", new int[]{0, 1},
            "zero_or_more", new int[]{0, -1},
            "one_or_more", new int[]{1, -1});

    private KofExprShape() {}

    /** O intervalo (min, max; max < 0 = sem teto) de um quantificador nomeado. */
    static int[] namedBound(String kind) {
        return NAMED.get(kind);
    }

    /** Pode a unidade casar texto VAZIO? (decide a terminação de um laço sem teto) */
    static boolean nullable(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (!nullable(unit)) return false;
            return true;
        }
        if (node instanceof ExprAtomNode atom) {
            if (atom.kind().equals("start") || atom.kind().equals("end")) return true;
            if (atom.kind().equals("literal")) {
                return atom.values().isEmpty() || atom.values().get(0).isEmpty();
            }
            return false;
        }
        if (node instanceof ExprQuantifierNode q) {
            int min;
            if (q.kind().equals("exactly") || q.kind().equals("between")) min = q.min();
            else if (q.kind().equals("one_or_more")) min = 1;
            else min = 0;
            return min == 0 || nullable(q.target());
        }
        return false;
    }

    /** Algum descendente é um quantificador SEM TETO? (a forma exponencial do §12) */
    static boolean containsUnbounded(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode unit : seq.units()) if (containsUnbounded(unit)) return true;
            return false;
        }
        if (node instanceof ExprQuantifierNode q) {
            boolean unbounded = q.kind().equals("zero_or_more") || q.kind().equals("one_or_more")
                    || (q.kind().equals("between") && q.max() < 0);
            return unbounded || containsUnbounded(q.target());
        }
        return false;
    }

    /** Quantas OCORRÊNCIAS de quantificador há na árvore = slots de contador. */
    static int quantifierCount(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            int total = 0;
            for (ExprNode unit : seq.units()) total += quantifierCount(unit);
            return total;
        }
        if (node instanceof ExprQuantifierNode q) return 1 + quantifierCount(q.target());
        return 0;
    }

    /** As unidades de uma sequência, ou o próprio nó como unidade única. */
    static List<ExprNode> unitsOf(ExprNode node) {
        return node instanceof ExprSequenceNode seq ? seq.units() : List.of(node);
    }

    /** Quantos VALORES ESCALARES Unicode (não unidades UTF-16) o texto tem. */
    static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }
}

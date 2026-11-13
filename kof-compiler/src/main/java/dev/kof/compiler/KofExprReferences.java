package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Deque;

/**
 * A RESOLUÇÃO das referências do `Kof.expr` (a composição por referência, o
 * §3.4 ipv4) — extraída do {@link CompilerExprLowering} pelo ratchet de 500
 * linhas; o comportamento é o mesmo, linha a linha.
 *
 * <p>A árvore com as referências EXPANDIDAS: cada referido tem o corpo in
 * Línea no ponto da referência. O analisador já garante SEM108 (desconhecido)
 * e SEM109 (ciclo); as fronteiras honestas aqui: o referido não pode ter
 * CAPTURAS (as capturas pertencem ao record do próprio padrão) nem ÂNCORAS
 * (SEM113 as liga ao sujeito inteiro, não a um trecho) — cada uma recusa
 * nomeando a fronteira.
 */
final class KofExprReferences {

    private KofExprReferences() {}

    /**
     * A composição por referência (o §3.4 ipv4): a árvore com as referências
     * EXPANDIDAS — cada referido tem o corpo in Línea no ponto da referência.
     * O analisador já garante SEM108 (desconhecido) e SEM109 (ciclo); as
     * fronteiras honestas aqui: o referido não pode ter CAPTURAS (as capturas
     * pertencem ao record do próprio padrão) nem ÂNCORAS (SEM113 as liga ao
     * sujeito inteiro, não a um trecho) — cada uma recusa nomeando a
     * fronteira. @return a árvore resolvida, ou null (a recusa já emitida).
     */
     static ExprNode resolveReferences(CompilerDriver driver, CompilerExprLowering L,
            ExprNode node, java.util.Deque<String> visiting) {
        if (node instanceof ExprSequenceNode seq) {
            List<ExprNode> out = new ArrayList<>();
            for (ExprNode u : seq.units()) {
                ExprNode r = resolveReferences(driver, L, u, visiting);
                if (r == null) return null;
                out.add(r);
            }
            return new ExprSequenceNode(node.position(), out);
        }
        if (node instanceof ExprQuantifierNode q) {
            ExprNode t = resolveReferences(driver, L, q.target(), visiting);
            if (t == null) return null;
            return new ExprQuantifierNode(q.position(), q.kind(), q.min(), q.max(), t);
        }
        if (node instanceof ExprAlternationNode alt) {
            List<ExprNode> out = new ArrayList<>();
            for (ExprNode b : alt.alternatives()) {
                ExprNode r = resolveReferences(driver, L, b, visiting);
                if (r == null) return null;
                out.add(r);
            }
            return new ExprAlternationNode(alt.position(), out);
        }
        if (node instanceof ExprCaptureNode c) {
            ExprNode b = resolveReferences(driver, L, c.body(), visiting);
            if (b == null) return null;
            return new ExprCaptureNode(c.position(), c.name(), b);
        }
        if (node instanceof ExprReferenceNode ref) {
            ExprDeclarationNode target = sibling(driver, ref.alias());
            if (target == null) {
                L.refuse("EXPR001", ref.position(), "pattern reference '" + ref.alias()
                        + "' does not name a declared 'expr' in this unit");
                return null;
            }
            if (visiting.contains(target.name())) {
                L.refuse("EXPR001", ref.position(), "pattern reference '" + ref.alias()
                        + "' re-enters the pattern being expanded (the analyzer's SEM109 "
                        + "cycle refusal should have fired first)");
                return null;
            }
            if (!KofExprShape.captureNames(target.body()).isEmpty()) {
                L.refuse("EXPR005", ref.position(), "pattern reference '" + ref.alias()
                        + "' carries captures " + KofExprShape.captureNames(target.body())
                        + ": the captures belong to the referenced pattern's own match "
                        + "record, so it cannot be inlined into another pattern yet (a "
                        + "later slice composes the records)");
                return null;
            }
            if (carriesAnchors(target.body())) {
                L.refuse("EXPR005", ref.position(), "pattern reference '" + ref.alias()
                        + "' carries start/end anchors: an anchor binds the WHOLE subject "
                        + "(SEM113), so an anchored pattern cannot be inlined into another "
                        + "pattern's middle");
                return null;
            }
            visiting.push(target.name());
            ExprNode expanded = resolveReferences(driver, L, target.body(), visiting);
            visiting.pop();
            return expanded;
        }
        return node;
    }

    /** O `expr` irmão pelo nome — sobre a unidade inteira, não por ordem. */
    private static ExprDeclarationNode sibling(CompilerDriver driver, String name) {
        if (driver.currentUnit == null) return null;
        for (AstNode d : driver.currentUnit.declarations()) {
            if (d instanceof ExprDeclarationNode ex && ex.name().equals(name)) return ex;
        }
        return null;
    }

    /** O corpo carrega âncoras start/end (as âncoras são de sujeito inteiro)? */
    private static boolean carriesAnchors(ExprNode node) {
        if (node instanceof ExprSequenceNode seq) {
            for (ExprNode u : seq.units()) if (carriesAnchors(u)) return true;
            return false;
        }
        if (node instanceof ExprAtomNode a) {
            return a.kind().equals("start") || a.kind().equals("end");
        }
        if (node instanceof ExprQuantifierNode q) return carriesAnchors(q.target());
        if (node instanceof ExprAlternationNode alt) {
            for (ExprNode b : alt.alternatives()) if (carriesAnchors(b)) return true;
            return false;
        }
        if (node instanceof ExprCaptureNode c) return carriesAnchors(c.body());
        return false;
    }
}

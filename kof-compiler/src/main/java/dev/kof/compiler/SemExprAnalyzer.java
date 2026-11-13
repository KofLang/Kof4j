package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kof.expr spike-0 (`D-KOF-EXPR`, mantenedora 03/11) — a validação semântica de
 * um padrão: tudo o que o parser aceita de FORMA mas que não descreve uma
 * intenção válida.
 *
 * <p>O spike-0 não baixa IR nenhum (§17 do plano), então esta é a fronteira em
 * que um `expr` errado é recusado — nunca em runtime, nunca em silêncio (R6).
 * As regras, cada uma com um código próprio que ensina a correção:
 * <ul>
 *   <li>{@code SEM108} — referência a um {@code expr} que não existe;</li>
 *   <li>{@code SEM109} — padrão recursivo (a expansão nunca terminaria);</li>
 *   <li>{@code SEM110} — nome de captura repetido no mesmo padrão;</li>
 *   <li>{@code SEM111} — quantificador impossível ({@code exactly(0)});</li>
 *   <li>{@code SEM112} — {@code between(min, max)} com {@code min > max};</li>
 *   <li>{@code SEM113} — {@code start}/{@code end} fora da primeira/última
 *       posição (uma âncora no meio não descreve intenção nenhuma);</li>
 *   <li>{@code SEM114} — {@code expr} de topo com nome de tipo já declarado.</li>
 * </ul>
 *
 * <p>A resolução das referências é feita sobre a UNIDADE inteira (mais os
 * módulos importados já mesclados), independente da ordem de declaração — o
 * mesmo princípio do pré-lasso de {@code extern} em
 * {@link CompilerUnitLowering}: visibilidade não pode ser ordem de arquivo.
 */
final class SemExprAnalyzer {

    /** Um padrão de topo: nome e corpo, já resolvido pelo mapa da unidade. */
    private record Pattern(String name, ExprDeclarationNode decl, Set<String> references) {}

    private SemExprAnalyzer() {}

    private static final Set<String> ATOM_KINDS = Set.of(
            "start", "end", "any", "letter", "digit", "whitespace", "word", "hex",
            "literal", "one_of", "none_of");

    private static final Set<String> LIST_KINDS = Set.of("one_of", "none_of", "literal");

    static void analyze(SemanticAnalyzer sa) {
        if (sa.unit() == null) return;
        Map<String, ExprDeclarationNode> byName = new HashMap<>();
        List<ExprDeclarationNode> all = new ArrayList<>();
        for (AstNode d : sa.unit().declarations()) {
            if (d instanceof ExprDeclarationNode ex) {
                all.add(ex);
                ExprDeclarationNode prev = byName.putIfAbsent(ex.name(), ex);
                if (prev != null && prev != ex) {
                    report(sa, ex.position(), "duplicate pattern name '" + ex.name()
                            + "'", "SEM110");
                }
                if (sa.allClasses().containsKey(ex.name())) {
                    // Fatia 1 (D-KOF-EXPR): o pre-declare agora registra o
                    // `expr` como CLASSE real — o símbolo sob o próprio nome É
                    // o deleite, não uma colisão. Colide só quando a classe
                    // registrada veio de OUTRA declaração (o caso SEM114).
                    SymbolTable.ClassSymbol registered = sa.allClasses().get(ex.name());
                    SymbolTable.ClassSymbol own = sa.symbolForDecl(ex);
                    if (registered == null || registered != own) {
                        report(sa, ex.position(), "pattern '" + ex.name()
                                + "' collides with an existing type name", "SEM114");
                    }
                }
                if (KofExprUnicode.CLASSES.contains(ex.name())) {
                    // Fatia §5 (D-KOF-EXPR): um `expr` não pode se chamar
                    // letter/digit/whitespace/word/hex/any — o nome é um
                    // ÁTOMO de classe na gramática, e o parser o leria como a
                    // classe, nunca como a referência (medido: `expr word`
                    // produzia kof_expr_is_word no lugar do corpo do
                    // usuário). A ambiguidade é recusada no compile.
                    report(sa, ex.position(), "pattern '" + ex.name()
                            + "' collides with a character class name: bare '"
                            + ex.name() + "' in a pattern body means the class, "
                            + "never this pattern — rename the pattern", "SEM114");
                }
            }
        }
        if (all.isEmpty()) return;
        Map<String, Pattern> patterns = new HashMap<>();
        for (ExprDeclarationNode ex : all) {
            Set<String> refs = new HashSet<>();
            checkBody(sa, ex, ex.body(), refs, new HashSet<>());
            patterns.putIfAbsent(ex.name(), new Pattern(ex.name(), ex, refs));
        }
        checkCycles(sa, patterns);
    }

    /** Percorre a árvore: atoms conhecidos, listas não vazias, contagens, âncoras, capturas e referências. */
    private static void checkBody(SemanticAnalyzer sa, ExprDeclarationNode owner,
                                  ExprNode node, Set<String> refs, Set<String> captureNames) {
        if (node instanceof ExprSequenceNode seq) {
            checkAnchors(sa, seq);
            for (ExprNode u : seq.units()) checkBody(sa, owner, u, refs, captureNames);
        } else if (node instanceof ExprAtomNode atom) {
            checkAtom(sa, atom);
        } else if (node instanceof ExprQuantifierNode q) {
            if (q.kind().equals("exactly")) {
                if (q.min() == 0) {
                    report(sa, q.position(), "'exactly' needs a count of at least 1 "
                            + "(a pattern element that may repeat zero times is 'optional')", "SEM111");
                }
            } else if (q.kind().equals("between") && q.max() >= 0 && q.min() > q.max()) {
                report(sa, q.position(), "'between(" + q.min() + ", " + q.max()
                        + ")' has no possible count: the minimum is larger than the maximum", "SEM112");
            }
            checkBody(sa, owner, q.target(), refs, captureNames);
        } else if (node instanceof ExprCaptureNode cap) {
            if (!captureNames.add(cap.name())) {
                report(sa, cap.position(), "capture name '" + cap.name()
                        + "' is used more than once in pattern '" + owner.name() + "'", "SEM110");
            }
            checkBody(sa, owner, cap.body(), refs, captureNames);
        } else if (node instanceof ExprAlternationNode alt) {
            if (alt.alternatives().isEmpty()) {
                report(sa, alt.position(), "'either' needs at least one alternative", "SEM112");
            }
            for (ExprNode branch : alt.alternatives()) checkBody(sa, owner, branch, refs, captureNames);
        } else if (node instanceof ExprReferenceNode ref) {
            refs.add(ref.alias());
        }
    }

    private static void checkAtom(SemanticAnalyzer sa, ExprAtomNode atom) {
        if (!ATOM_KINDS.contains(atom.kind())) {
            report(sa, atom.position(), "unknown pattern element '" + atom.kind() + "'", "SEM108");
            return;
        }
        if (LIST_KINDS.contains(atom.kind()) && atom.values().isEmpty()) {
            report(sa, atom.position(), "'" + atom.kind() + "' needs at least one text literal", "SEM112");
        }
    }

    /** `start` só na primeira posição e `end` só na última: âncora no meio não significa nada. */
    private static void checkAnchors(SemanticAnalyzer sa, ExprSequenceNode seq) {
        List<ExprNode> units = seq.units();
        for (int i = 0; i < units.size(); i++) {
            ExprNode u = units.get(i);
            String anchor = null;
            if (u instanceof ExprAtomNode a) anchor = a.kind();
            if ("start".equals(anchor) && i != 0) {
                report(sa, u.position(), "'start' anchors the whole subject: it can only "
                        + "appear at the beginning of a pattern", "SEM113");
            } else if ("end".equals(anchor) && i != units.size() - 1) {
                report(sa, u.position(), "'end' anchors the whole subject: it can only "
                        + "appear at the end of a pattern", "SEM113");
            }
        }
    }

    /** Referência desconhecida e ciclo — sobre o mapa completo, nunca por ordem de declaração. */
    private static void checkCycles(SemanticAnalyzer sa, Map<String, Pattern> patterns) {
        for (Pattern p : patterns.values()) {
            for (String ref : p.references()) {
                if (!patterns.containsKey(ref)) {
                    report(sa, p.decl().position(), "pattern '" + p.name()
                            + "' references '" + ref + "', which is not an 'expr'", "SEM108");
                }
            }
        }
        Map<String, String> visiting = new HashMap<>();
        for (Pattern p : patterns.values()) {
            reportCycle(sa, patterns, p.name(), visiting, new ArrayList<>());
        }
    }

    private static void reportCycle(SemanticAnalyzer sa, Map<String, Pattern> patterns, String name,
                                    Map<String, String> done, List<String> path) {
        if (done.containsKey(name)) return;
        int at = path.indexOf(name);
        if (at >= 0) {
            List<String> loop = new ArrayList<>(path.subList(at, path.size()));
            loop.add(name);
            Pattern start = patterns.get(name);
            report(sa, start == null ? null : start.decl().position(),
                    "recursive pattern: " + String.join(" -> ", loop)
                            + " would expand forever", "SEM109");
            return;
        }
        path.add(name);
        Pattern p = patterns.get(name);
        if (p != null) {
            for (String ref : p.references()) {
                if (patterns.containsKey(ref)) reportCycle(sa, patterns, ref, done, path);
            }
        }
        path.remove(path.size() - 1);
        done.put(name, name);
    }

    private static void report(SemanticAnalyzer sa, SourcePosition pos, String message, String code) {
        if (sa.diagnostics() == null) return;
        sa.diagnostics().error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0,
                pos != null ? pos.length() : 0, message, code);
    }
}

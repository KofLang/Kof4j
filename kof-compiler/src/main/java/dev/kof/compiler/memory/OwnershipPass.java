package dev.kof.compiler.memory;

import dev.kof.compiler.ArrayAccessExpr;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.UnaryExpr;
import dev.kof.compiler.VarDeclStmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 1) — primeiro passe de ANALISE de ownership
 * com emissão: as faces retilíneas de O-01/MEM001 e O-02/MEM002 da spec
 * {@code docs/spec/memory-safety.md} §2.2, resolvidas SEM literal {@code null}
 * conforme {@code DECISIONS.md} §`D-COMPLETE-FIRST` (26/09): a transferencia e
 * um FATO INFERIDO do programa — quando o recurso e reivindicado por um
 * binding ({@code x.close()}) e o programa ainda le ou reivindica um dos
 * bindings-irmaos do mesmo recurso, o programa tratou a posse como implicita.
 * Kof nao tem move implicito; a regra de transferencia explicita (O-02) arde.
 *
 * <p><b>Faces desta fatia (retilineas, escopo declarado):</b>
 * <ul>
 *   <li>{@code MEM001} (O-01, dupla reivindicao): {@code x.close()} sobre um
 *       recurso ja reivindicado por QUALQUER binding do mesmo grupo — o
 *       segundo {@code close} e o segundo escopo-dono sem transferencia.</li>
 *   <li>{@code MEM002} (O-02, use-after-move): leitura de {@code y} (binding
 *       do grupo que NAO foi o reivindicante) depois de {@code x.close()} —
 *       transferencia implicita sem o face explicita da spec.</li>
 * </ul>
 *
 * <p><b>Escopo honesto (R6/21/09, fatias subsequentes da MESMA fase):</b> o
 * passe analisa a regiao retilinea do corpo (declarações, expressions e
 * returns de topo). Faces que cruzam controle de fluxo (if/while/try/switch/
 * spawn), closures (E-/C-), containers (O-03) e fronteiras FFI (O-05/MEM005)
 * sao fatias nomeadas do plano — NADA aqui heuristica sobre elas: estados
 * fora da regiao permanecem intocados, portanto zero falso-positivo e
 * zero mudanca de comportamento para programas que nao reivindicam close.
 * O binding reivindicante continua utilisavel (leitura de si mesmo nao e
 * face O-01/O-02; use-after-close do proprio handle e L-02/MEM011, runtime).
 *
 * <p>Nomes: grupos sao raiz-de-cadeia de aliases ({@code var a = r;} amarra
 * {@code a} na cadeia de {@code r}); o estado do recurso vive na raiz. Alias
 * de um binding ainda sem grupo so entra no grupo quando o grupo nasce.
 */
public final class OwnershipPass {

    private OwnershipPass() {
    }

    /** Analisa a regiao retilinea de um corpo; {@code null} diag = no-op. */
    public static void analyze(DiagnosticCollector diag, List<StatementNode> body) {
        if (diag == null || body == null || body.isEmpty()) {
            return;
        }
        new Region(diag).walkBody(body);
    }

    /** Grupo de posse: um recurso, seus bindings e a ultima reivindicacao. */
    private static final class Group {
        final String root;
        boolean claimed;
        String claimer;
        SourcePosition claimSite;

        Group(String root) {
            this.root = root;
        }
    }

    private static final class Region {
        private final DiagnosticCollector diag;
        private final Map<String, Group> groups = new HashMap<>();
        private final Map<String, String> aliasOf = new HashMap<>();
        private StatementNode stmt;

        Region(DiagnosticCollector diag) {
            this.diag = diag;
        }

        void walkBody(List<StatementNode> body) {
            for (StatementNode s : body) {
                stmt = s;
                switch (s) {
                    case VarDeclStmt vds -> {
                        ExpressionNode init = vds.initializer();
                        if (init instanceof IdentifierExpr id) {
                            readName(id.name());
                            aliasOf.put(vds.name(), id.name());
                        } else if (init != null) {
                            readExpr(init);
                        }
                    }
                    case ExpressionStmt es -> readExpr(es.expression());
                    case ReturnStmt ret -> {
                        if (ret.value() != null) {
                            readExpr(ret.value());
                        }
                    }
                    default -> {
                        // Regiao retilinea apenas: faces de controle de fluxo
                        // pertencem as fatias subsequentes da Fase 3 (escopo
                        // declarado acima — nunca heuristica silenciosa).
                    }
                }
            }
        }

        /** Raiz da cadeia de aliases de {@code name} (sem cadeia = name). */
        private String base(String name) {
            String cur = name;
            for (int guard = 0; aliasOf.containsKey(cur) && guard < aliasOf.size() + 1; guard++) {
                cur = aliasOf.get(cur);
            }
            return cur;
        }

        private void claim(String name) {
            String base = base(name);
            Group g = groups.computeIfAbsent(base, Group::new);
            if (g.claimed) {
                diag.error(stmt, "O-01: double ownership claim on '" + base
                        + "' — first claimed via '" + g.claimer + "'"
                        + (g.claimSite != null ? " at line " + g.claimSite.line() : "")
                        + ", second via '" + name + "' (no transfer in between)",
                        "MEM001");
                return;
            }
            g.claimed = true;
            g.claimer = name;
            g.claimSite = stmt == null ? null : stmt.position();
        }

        private void readName(String name) {
            Group g = groups.get(base(name));
            if (g != null && g.claimed && !name.equals(g.claimer)) {
                diag.error(stmt, "O-02: use after move of '" + g.root + "' — ownership was"
                        + " claimed via '" + g.claimer + "'"
                        + (g.claimSite != null ? " at line " + g.claimSite.line() : "")
                        + "; Kof has no implicit transfer, and the source binding '"
                        + name + "' is still read",
                        "MEM002");
            }
        }

        private void readExpr(ExpressionNode e) {
            switch (e) {
                case IdentifierExpr id -> readName(id.name());
                case MethodCallExpr mc -> {
                    // close() sem argumentos sobre um identificador = reivindicacao.
                    if ("close".equals(mc.methodName()) && mc.arguments().isEmpty()
                            && mc.receiver() instanceof IdentifierExpr recv) {
                        claim(recv.name());
                        return;
                    }
                    if (mc.receiver() != null) {
                        readExpr(mc.receiver());
                    }
                    for (ExpressionNode arg : mc.arguments()) {
                        readExpr(arg);
                    }
                }
                case FieldAccessExpr fa -> readExpr(fa.receiver());
                case ArrayAccessExpr aa -> {
                    readExpr(aa.receiver());
                    readExpr(aa.index());
                }
                case BinaryExpr be -> {
                    readExpr(be.left());
                    readExpr(be.right());
                }
                case UnaryExpr ue -> readExpr(ue.operand());
                case AssignmentExpr ae -> {
                    // alvo = escrita (rebinding nao transfere posse nesta fatia)
                    if (ae.value() != null) {
                        readExpr(ae.value());
                    }
                }
                case LambdaExpr lambda -> {
                    // Capturas = faces E-/C- (fatias subsequentes da fase);
                    // o corpo do lambda e regiao propria, nunca lida aqui.
                }
                default -> {
                    // Expressoes sem leitura de binding (literais etc.) ou cujo
                    // formato este passe nao conhece: conservador, nada a fazer.
                }
            }
        }
    }
}

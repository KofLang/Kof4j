package dev.kof.compiler.memory;

import dev.kof.compiler.ArrayAccessExpr;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.DoWhileStmt;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.ForInStmt;
import dev.kof.compiler.ForStmt;
import dev.kof.compiler.FormalParameterNode;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.IfStmt;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SpawnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.UnaryExpr;
import dev.kof.compiler.VarDeclStmt;
import dev.kof.compiler.WhileStmt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 3.2) — responsabilidade separada do
 * {@link OwnershipPass}: coletar, da sub-arvore do corpo de um {@code spawn},
 * os NOMES dos bindings ESCRITOS pelo worker, para que o {@link OwnershipPass}
 * cruze-os com as escritas do corpo-mae (ou entre spawns) e decida a corrida
 * clara B-04/C-03/{@code MEM021}.
 *
 * <p><b>O que conta como escrita do worker:</b>
 * <ul>
 *   <li>mutador de tamanho/indice ({@link #MUTATORS}) sobre um binding capturado
 *       — face objeto original; e</li>
 *   <li>REATRIBUICAO direta do binding capturado ({@code n = ...}) ou
 *       incremento/decremento ({@code n++}/{@code n--}) — face ESCALAR. A
 *       atribuicao dentro da lambda forca o box de representacao
 *       ({@code CompilerCaptureScanner} → {@code mutatedCapturedNames} →
 *       {@code CapturedVarBox}); o worker passa a COMPARTILHAR o slot com a
 *       mae, e uma escrita da mae sem {@code await} entre e corrida real
 *       (medida: {@code 202}/{@code 101}).</li>
 *   <li>chamada a um {@code extern} cujo parametro e um {@code Buffer(U8)} INOUT
 *       (B-03/{@code MEM020}, #668): a C escreve o buffer, logo qualquer
 *       argumento-binding passado naquele indice e escrita do worker.</li>
 * </ul>
 * Leitura pura do binding capturado NAO entra: a captura read-only baixa por
 * VALOR (sem box), logo uma escrita posterior da mae nao alcanca o worker.
 *
 * <p>Caminhada puramente estrutural e CONSERVADORA: um formato de AST que este
 * scanner nao conhece e ignorado (nunca heuristica silenciosa que invente uma
 * corrida). Nomes sombreados por parametros de lambda ou por declaracoes
 * locais do worker sao EXCLUIDOS (zero falso-positivo por construcao).
 * {@code MUTATORS} e a MESMA fonte usada pela B-05/{@code MEM022} para que as
 * duas faces jamais divirjam sobre o que conta como mutacao.
 */
final class SpawnCaptureScanner {

    /** Mutadores de tamanho/indice da List compartilhados com a B-05. */
    static final Set<String> MUTATORS = Set.of("add", "remove", "clear", "addAll");

    private SpawnCaptureScanner() {
    }

    /**
     * Escritas do worker na sub-arvore do spawn: {@code mutated} = bindings
     * mutados/reassignados (MEM021, faces B-04/C-03); {@code ffiBuffers} =
     * bindings passados a um {@code extern} com parametro {@code Buffer(U8)}
     * INOUT (MEM020, face B-03/#668). Os dois conjuntos saem de UMA caminhada.
     */
    record Captures(Set<String> mutated, Set<String> ffiBuffers) {}

    /**
     * @param ffiWriteArgs extern cujo nome mapeia os indices de argumento
     *                     {@code Buffer(U8)} (INOUT) — derivado em
     *                     {@code StatementAnalyzer} a partir das declaracoes.
     */
    static Captures captured(ExpressionNode spawnBody, Map<String, Set<Integer>> ffiWriteArgs) {
        Set<String> mutated = new LinkedHashSet<>();
        Set<String> ffi = new LinkedHashSet<>();
        expr(spawnBody, mutated, ffi, ffiWriteArgs, new LinkedHashSet<>());
        return new Captures(mutated, ffi);
    }

    private static void expr(ExpressionNode e, Set<String> out, Set<String> ffi,
                             Map<String, Set<Integer>> ffiWriteArgs, Set<String> shadowed) {
        if (e == null) {
            return;
        }
        switch (e) {
            case MethodCallExpr mc -> {
                if (MUTATORS.contains(mc.methodName())
                        && mc.receiver() instanceof IdentifierExpr recv
                        && !shadowed.contains(recv.name())) {
                    out.add(recv.name());
                }
                // B-03/MEM020 (#668): extern com Buffer(U8) INOUT escreve o
                // binding passado no argumento NAQUELE indice (chamada bare).
                if (mc.receiver() == null) {
                    Set<Integer> idxs = ffiWriteArgs.get(mc.methodName());
                    if (idxs != null) {
                        for (int i : idxs) {
                            if (i < mc.arguments().size()
                                    && mc.arguments().get(i) instanceof IdentifierExpr bid
                                    && !shadowed.contains(bid.name())) {
                                ffi.add(bid.name());
                            }
                        }
                    }
                }
                expr(mc.receiver(), out, ffi, ffiWriteArgs, shadowed);
                for (ExpressionNode a : mc.arguments()) {
                    expr(a, out, ffi, ffiWriteArgs, shadowed);
                }
            }
            case LambdaExpr lam -> {
                Set<String> inner = new LinkedHashSet<>(shadowed);
                if (lam.parameters() != null) {
                    for (FormalParameterNode p : lam.parameters()) {
                        if (p != null && p.name() != null) {
                            inner.add(p.name());
                        }
                    }
                }
                stmts(lam.body(), out, ffi, ffiWriteArgs, inner);
            }
            case AssignmentExpr ae -> {
                if (ae.target() instanceof IdentifierExpr t && !shadowed.contains(t.name())) {
                    out.add(t.name());
                }
                expr(ae.target(), out, ffi, ffiWriteArgs, shadowed);
                expr(ae.value(), out, ffi, ffiWriteArgs, shadowed);
            }
            case UnaryExpr ue -> {
                if (("++".equals(ue.operator()) || "--".equals(ue.operator()))
                        && ue.operand() instanceof IdentifierExpr t && !shadowed.contains(t.name())) {
                    out.add(t.name());
                }
                expr(ue.operand(), out, ffi, ffiWriteArgs, shadowed);
            }
            case BinaryExpr be -> {
                expr(be.left(), out, ffi, ffiWriteArgs, shadowed);
                expr(be.right(), out, ffi, ffiWriteArgs, shadowed);
            }
            case FieldAccessExpr fa -> expr(fa.receiver(), out, ffi, ffiWriteArgs, shadowed);
            case ArrayAccessExpr aa -> {
                expr(aa.receiver(), out, ffi, ffiWriteArgs, shadowed);
                expr(aa.index(), out, ffi, ffiWriteArgs, shadowed);
            }
            default -> {
            }
        }
    }

    private static void stmts(List<StatementNode> body, Set<String> out, Set<String> ffi,
                              Map<String, Set<Integer>> ffiWriteArgs, Set<String> shadowed) {
        if (body == null) {
            return;
        }
        for (StatementNode s : body) {
            stmt(s, out, ffi, ffiWriteArgs, shadowed);
        }
    }

    private static void stmt(StatementNode s, Set<String> out, Set<String> ffi,
                             Map<String, Set<Integer>> ffiWriteArgs, Set<String> shadowed) {
        if (s == null) {
            return;
        }
        switch (s) {
            case ExpressionStmt es -> expr(es.expression(), out, ffi, ffiWriteArgs, shadowed);
            case VarDeclStmt vds -> {
                expr(vds.initializer(), out, ffi, ffiWriteArgs, shadowed);
                shadowed.add(vds.name());
            }
            case ReturnStmt r -> expr(r.value(), out, ffi, ffiWriteArgs, shadowed);
            case BlockStmt b -> stmts(b.statements(), out, ffi, ffiWriteArgs, new LinkedHashSet<>(shadowed));
            case IfStmt i -> {
                expr(i.condition(), out, ffi, ffiWriteArgs, shadowed);
                stmt(i.thenBranch(), out, ffi, ffiWriteArgs, new LinkedHashSet<>(shadowed));
                stmt(i.elseBranch(), out, ffi, ffiWriteArgs, new LinkedHashSet<>(shadowed));
            }
            case WhileStmt w -> {
                expr(w.condition(), out, ffi, ffiWriteArgs, shadowed);
                stmt(w.body(), out, ffi, ffiWriteArgs, new LinkedHashSet<>(shadowed));
            }
            case DoWhileStmt dw -> {
                stmt(dw.body(), out, ffi, ffiWriteArgs, shadowed);
                expr(dw.condition(), out, ffi, ffiWriteArgs, shadowed);
            }
            case ForStmt fr -> {
                stmt(fr.init(), out, ffi, ffiWriteArgs, shadowed);
                expr(fr.condition(), out, ffi, ffiWriteArgs, shadowed);
                stmt(fr.body(), out, ffi, ffiWriteArgs, new LinkedHashSet<>(shadowed));
                expr(fr.update(), out, ffi, ffiWriteArgs, shadowed);
            }
            case ForInStmt fi -> {
                expr(fi.collection(), out, ffi, ffiWriteArgs, shadowed);
                Set<String> inner = new LinkedHashSet<>(shadowed);
                inner.add(fi.varName());
                stmt(fi.body(), out, ffi, ffiWriteArgs, inner);
            }
            case SpawnStmt ss -> expr(ss.expression(), out, ffi, ffiWriteArgs, shadowed);
            default -> {
            }
        }
    }
}

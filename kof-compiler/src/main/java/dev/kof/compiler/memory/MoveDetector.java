package dev.kof.compiler.memory;

import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.ConcreteLiteralKind;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.LiteralExpr;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.VarDeclStmt;

import java.util.ArrayList;
import java.util.List;

/**
 * D-MEMORY-SAFETY Fase 2 (fatia 4) — reconhecedor do PADRAO de transferencia
 * O-02 (spec §2.2): `var a = b;` imediatamente seguido de `b = null;`. O
 * detector e READ-ONLY: coleta o fato (`MoveTransfer`) com o indice do par,
 * sem emitir diagnostico e sem mudar nenhum comportamento — a emissao
 * MEM002/MEM001 e da Fase 3, e o encaixe nas passagens semanticas existents
 * vira unidade propria.
 *
 * Nota honesta (regra 6, registrada no DOING): hoje `b = null` e SEM048 —
 * a face "source nulled" do padrao O-02 ainda nao e um programa Kof valido.
 * O modelo representa a regra da spec; abrir o canal e decisao da
 * mantenedora, nao deste detector.
 */
public final class MoveDetector {

    /** Um move reconhecido e a posicao do binding de destino na lista. */
    public record At(int index, MoveTransfer move) {
    }

    private MoveDetector() {
    }

    public static List<At> detect(List<StatementNode> stmts) {
        var found = new ArrayList<At>();
        if (stmts == null) {
            return found;
        }
        for (int i = 0; i + 1 < stmts.size(); i++) {
            if (!(stmts.get(i) instanceof VarDeclStmt decl)) {
                continue;
            }
            if (!(decl.initializer() instanceof IdentifierExpr src)) {
                continue;
            }
            if (decl.name().equals(src.name())) {
                continue; // auto-movimento nao transfere posse
            }
            if (!(stmts.get(i + 1) instanceof ExpressionStmt expr)
                    || !(expr.expression() instanceof AssignmentExpr ae)
                    || !ae.operator().equals("=")
                    || !(ae.target() instanceof IdentifierExpr tgt)
                    || !tgt.name().equals(src.name())
                    || !isNullLiteral(ae.value())) {
                continue;
            }
            found.add(new At(i, new MoveTransfer(decl.name(), src.name())));
            i++;
        }
        return found;
    }

    private static boolean isNullLiteral(ExpressionNode e) {
        return e instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL;
    }
}

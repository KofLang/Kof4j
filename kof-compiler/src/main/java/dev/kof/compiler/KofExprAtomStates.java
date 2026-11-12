package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatias 1-5 (`D-KOF-EXPR`) — a emissão dos estados de ÁTOMO do
 * matcher: os literais (startsWith + avanço), as âncoras (`start` = pos==0,
 * `end` = pos==length), os de lista (`one_of`/`none_of` por PONTO DE CÓDIGO,
 * §3.1 do plano) e as classes com tabela (`letter`/`digit`/`whitespace`/
 * `word`/`hex` — o predicado é função de runtime; `any` lê o escalar direto).
 *
 * <p>Extraído de {@code CompilerExprLowering} pelo ratchet de 500 linhas; o
 * comportamento é o mesmo, instrução a instrução. Os helpers do lowering
 * (empilhamento de argumentos, registro de estado, slots) são os mesmos.
 */
final class KofExprAtomStates {

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final int SLOT_S = 0;
    private static final int SLOT_POS = 1;

    private KofExprAtomStates() {}

    /** Um átomo: casa e continua, ou falha com -1. */
    static String atomState(CompilerExprLowering L, ExprAtomNode atom, String tail) {
        String name = L.newName();
        LabelId fail = LabelId.create();
        LabelId ok = LabelId.create();
        LabelId doneJoin = LabelId.create();
        List<KofOperation> ops = new ArrayList<>();
        switch (atom.kind()) {
            case "literal" -> {
                String lit = atom.values().isEmpty() ? "" : atom.values().get(0);
                ops.add(new KofLoadLocal(STR, SLOT_S));
                ops.add(KofLoadLiteral.ofString(lit));
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(new KofCall(STR, "startsWith", List.of(STR, INT), BOOL, KofCallKind.INSTANCE));
                ops.add(KofLoadLiteral.ofBool(true));
                ops.add(new KofConditionalJump(KofComparison.EQ, BOOL, ok, fail));
                ops.add(new KofLabel(ok));
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(KofLoadLiteral.ofInt(lit.length()));
                ops.add(new KofBinary(KofBinaryOp.ADD, INT));
                ops.add(new KofStoreLocal(INT, SLOT_POS));
            }
            case "start" -> {
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(KofLoadLiteral.ofInt(0));
                ops.add(new KofConditionalJump(KofComparison.EQ, INT, ok, fail));
                ops.add(new KofLabel(ok));
            }
            case "one_of", "none_of" -> {
                // membership por PONTO DE CÓDIGO: lê o escalar em pos, guarda e
                // compara contra o ponto único de cada literal. `one_of` casa
                // quando ALGUM bate; `none_of` quando NENHUM bate.
                KofExprCodePoint.emitRead(ops, L.tempSlot());
                ops.add(new KofStoreLocal(INT, L.tempSlot()));
                LabelId hit = LabelId.create();
                for (String v : atom.values()) {
                    LabelId next = LabelId.create();
                    ops.add(new KofLoadLocal(INT, L.tempSlot()));
                    ops.add(KofLoadLiteral.ofInt(v.codePointAt(0)));
                    ops.add(new KofConditionalJump(KofComparison.EQ, INT, hit, next));
                    ops.add(new KofLabel(next));
                }
                // nenhum bateu
                if (atom.kind().equals("one_of")) {
                    ops.add(new KofJump(fail));
                } else {
                    KofExprCodePoint.emitAdvance(ops, L.tempSlot());
                    ops.add(new KofJump(doneJoin));
                }
                ops.add(new KofLabel(hit));
                if (atom.kind().equals("one_of")) {
                    KofExprCodePoint.emitAdvance(ops, L.tempSlot());
                    ops.add(new KofJump(doneJoin));
                } else {
                    ops.add(new KofJump(fail));
                }
                ops.add(new KofLabel(doneJoin));
            }
            case "any" -> {
                // o escalar em pos, seja ele qual for — mas TEM de existir
                KofExprCodePoint.emitRead(ops, L.tempSlot());
                ops.add(new KofStoreLocal(INT, L.tempSlot()));
                LabelId present = LabelId.create();
                ops.add(new KofLoadLocal(INT, L.tempSlot()));
                ops.add(KofLoadLiteral.ofInt(-1));
                ops.add(new KofConditionalJump(KofComparison.NE, INT, present, fail));
                ops.add(new KofLabel(present));
                KofExprCodePoint.emitAdvance(ops, L.tempSlot());
            }
            case "end" -> {
                ops.add(new KofLoadLocal(INT, SLOT_POS));
                ops.add(new KofLoadLocal(STR, SLOT_S));
                ops.add(new KofCall(STR, "length", List.of(), INT, KofCallKind.INSTANCE));
                ops.add(new KofConditionalJump(KofComparison.EQ, INT, ok, fail));
                ops.add(new KofLabel(ok));
            }
            default -> {
                // as classes com tabela: lê o ponto, guarda (o avanço precisa
                // dele) e pergunta ao runtime
                String predicate = KofExprUnicode.predicateOf(atom.kind());
                KofExprCodePoint.emitRead(ops, L.tempSlot());
                ops.add(new KofStoreLocal(INT, L.tempSlot()));
                ops.add(new KofLoadLocal(INT, L.tempSlot()));
                ops.add(new KofCall(STR, predicate, List.of(INT), BOOL, KofCallKind.FUNCTION));
                ops.add(KofLoadLiteral.ofBool(true));
                ops.add(new KofConditionalJump(KofComparison.EQ, BOOL, ok, fail));
                ops.add(new KofLabel(ok));
                KofExprCodePoint.emitAdvance(ops, L.tempSlot());
            }
        }
        L.opsCall(ops, tail);
        ops.add(new KofReturn(INT));
        ops.add(new KofLabel(fail));
        ops.add(KofLoadLiteral.ofInt(-1));
        ops.add(new KofReturn(INT));
        L.states.add(L.stateMethod(name, ops));
        return name;
    }
}
package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Kof.expr fatia 8 (`D-KOF-EXPR`) — a família de BUSCA ({@code find}/
 * {@code findAll}/{@code replace}) e o orçamento do §12, extraída de
 * {@code KofExprMatchResult} pelo ratchet de 500 linhas; o comportamento é o
 * mesmo, instrução a instrução. A emissão das formas é TRUE-FIRST (o label
 * imediatamente após cada CJump é o ramo verdadeiro) — o contrato que o
 * reconstrutor estruturado do JS exige e que os demais backends não negam.
 */
final class KofExprSearch {

    private final KofExprMatchResult surface;

    KofExprSearch(KofExprMatchResult surface) {
        this.surface = surface;
    }

/** {@code find(String): Int} — o primeiro índice de início de ocorrência, -1 quando ausente. */
    IRMethod find() {
    LabelId loop = LabelId.create();
    LabelId tryOne = LabelId.create();
    LabelId hit = LabelId.create();
    LabelId miss = LabelId.create();
    LabelId fail = LabelId.create();
    List<KofOperation> ops = new ArrayList<>();
    surface.emitBudget(ops);
    surface.emitKeysArray(ops, KofExprMatchResult.SLOT_KEYS);
    ops.add(KofLoadLiteral.ofInt(0));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofLabel(loop));
    // start > len(s)? → fail
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofCall(KofExprMatchResult.STR, "length", List.of(), KofExprMatchResult.INT, KofCallKind.INSTANCE));
    ops.add(new KofConditionalJump(KofComparison.LE, KofExprMatchResult.INT, tryOne, fail));
    ops.add(new KofLabel(tryOne));
    List<Type> params = surface.pushChainArgs(ops, KofExprMatchResult.SLOT_KEYS, KofExprMatchResult.SLOT_SEARCH_A);
    ops.add(new KofCall(surface.self, surface.entryFree, params, KofExprMatchResult.INT, KofCallKind.STATIC));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(-1));
    ops.add(new KofConditionalJump(KofComparison.NE, KofExprMatchResult.INT, hit, miss));
    ops.add(new KofLabel(hit));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofReturn(KofExprMatchResult.INT));
    ops.add(new KofLabel(miss));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(fail));
    ops.add(KofLoadLiteral.ofInt(-1));
    ops.add(new KofReturn(KofExprMatchResult.INT));
    List<IRLocalVariable> locals = surface.localsFor(
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_A, "start", KofExprMatchResult.INT),
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_B, "end", KofExprMatchResult.INT));
    return new IRMethod("find", KofExprMatchResult.INT, List.of(KofExprMatchResult.STR),
            AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
            List.of(new IRBasicBlock(0, ops)), locals);
}

/**
 * {@code findAll(String)} — as ocorrências em ordem: os TRECHOS casados
 * (List<String>) num padrão sem captura, o record tipado
 * (List<nameMatch>) num padrão com captura; null quando nenhuma. Um
 * casamento vazio avança 1 (sem laço infinito).
 */
    IRMethod findAll() {
    Type elemType = surface.captures.isEmpty() ? KofExprMatchResult.STR : surface.recordType;
    Type listType = new Type.ClassType("kof", "List", List.of(elemType));
    LabelId loop = LabelId.create();
    LabelId tryOne = LabelId.create();
    LabelId hit = LabelId.create();
    LabelId miss = LabelId.create();
    LabelId emptyAdv = LabelId.create();
    LabelId nonEmpty = LabelId.create();
    LabelId done = LabelId.create();
    List<KofOperation> ops = new ArrayList<>();
    surface.emitBudget(ops);
    surface.emitKeysArray(ops, KofExprMatchResult.SLOT_KEYS);
    ops.add(new KofCall(listType, "kof_list_new", List.of(), listType, KofCallKind.FUNCTION));
    ops.add(new KofStoreLocal(listType, 1));
    ops.add(KofLoadLiteral.ofInt(0));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofLabel(loop));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofCall(KofExprMatchResult.STR, "length", List.of(), KofExprMatchResult.INT, KofCallKind.INSTANCE));
    ops.add(new KofConditionalJump(KofComparison.LE, KofExprMatchResult.INT, tryOne, done));
    ops.add(new KofLabel(tryOne));
    List<Type> params = surface.pushChainArgs(ops, KofExprMatchResult.SLOT_KEYS, KofExprMatchResult.SLOT_SEARCH_A);
    ops.add(new KofCall(surface.self, surface.entryFree, params, KofExprMatchResult.INT, KofCallKind.STATIC));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(-1));
    ops.add(new KofConditionalJump(KofComparison.NE, KofExprMatchResult.INT, hit, miss));
    ops.add(new KofLabel(hit));
    ops.add(new KofLoadLocal(listType, 1));
    surface.emitOccurrence(ops, elemType, KofExprMatchResult.SLOT_KEYS, KofExprMatchResult.SLOT_SEARCH_A, KofExprMatchResult.SLOT_SEARCH_B);
    ops.add(new KofCall(listType, "kof_list_add", List.of(elemType),
            Type.PrimitiveType.VOID, KofCallKind.INSTANCE));
    // avanço: casamento vazio anda 1; casamento normal salta além de si
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofConditionalJump(KofComparison.LE, KofExprMatchResult.INT, emptyAdv, nonEmpty));
    ops.add(new KofLabel(emptyAdv));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(nonEmpty));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(miss));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(done));
    ops.add(new KofLoadLocal(listType, 1));
    ops.add(new KofReturn(listType));
    List<IRLocalVariable> locals = surface.localsFor(
            new IRLocalVariable(1, "list", listType),
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_A, "start", KofExprMatchResult.INT),
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_B, "end", KofExprMatchResult.INT));
    return new IRMethod("findAll", new Type.NullableType(listType), List.of(KofExprMatchResult.STR),
            AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
            List.of(new IRBasicBlock(0, ops)), locals);
}

/**
 * {@code replace(String, String): String} — substituição LITERAL (§7):
 * cada ocorrência vira o texto de reposição; o sujeito sem casamento
 * volta inalterado. Locais: 1 = resultado, 2 = reposição, A = início,
 * B = fim, 5 = copyFrom, 6 = resultado não — 1 e 6 são o MESMO resultado:
 * layout final: 1 = reposição, A = início, B = fim/copyFrom, 5 = resultado.
 */
    IRMethod replace() {
    LabelId loop = LabelId.create();
    LabelId tryOne = LabelId.create();
    LabelId hit = LabelId.create();
    LabelId miss = LabelId.create();
    LabelId emptyAdv = LabelId.create();
    LabelId nonEmpty = LabelId.create();
    LabelId tail = LabelId.create();
    List<KofOperation> ops = new ArrayList<>();
    surface.emitBudget(ops);
    surface.emitKeysArray(ops, KofExprMatchResult.SLOT_KEYS);
    ops.add(KofLoadLiteral.ofString(""));
    ops.add(new KofStoreLocal(KofExprMatchResult.STR, 5));
    ops.add(KofLoadLiteral.ofInt(0));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(0));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(0));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, 6));
    ops.add(new KofLabel(loop));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofCall(KofExprMatchResult.STR, "length", List.of(), KofExprMatchResult.INT, KofCallKind.INSTANCE));
    ops.add(new KofConditionalJump(KofComparison.LE, KofExprMatchResult.INT, tryOne, tail));
    ops.add(new KofLabel(tryOne));
    List<Type> params = surface.pushChainArgs(ops, KofExprMatchResult.SLOT_KEYS, KofExprMatchResult.SLOT_SEARCH_A);
    ops.add(new KofCall(surface.self, surface.entryFree, params, KofExprMatchResult.INT, KofCallKind.STATIC));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(-1));
    ops.add(new KofConditionalJump(KofComparison.NE, KofExprMatchResult.INT, hit, miss));
    ops.add(new KofLabel(hit));
    // result(5) = result(5) + s.substring(copyFrom(B), start(A)) + replacement(2)
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, 5));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, 6));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofCall(KofExprMatchResult.STR, "substring", List.of(KofExprMatchResult.INT, KofExprMatchResult.INT), KofExprMatchResult.STR, KofCallKind.INSTANCE));
    ops.add(new KofCall(KofExprMatchResult.STR, "kof_string_concat", List.of(KofExprMatchResult.STR, KofExprMatchResult.STR), KofExprMatchResult.STR, KofCallKind.FUNCTION));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, 1));
    ops.add(new KofCall(KofExprMatchResult.STR, "kof_string_concat", List.of(KofExprMatchResult.STR, KofExprMatchResult.STR), KofExprMatchResult.STR, KofCallKind.FUNCTION));
    ops.add(new KofStoreLocal(KofExprMatchResult.STR, 5));
    // copyFrom(6) = end da ocorrência
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, 6));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofConditionalJump(KofComparison.LE, KofExprMatchResult.INT, emptyAdv, nonEmpty));
    ops.add(new KofLabel(emptyAdv));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(nonEmpty));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_B));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(miss));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(KofLoadLiteral.ofInt(1));
    ops.add(new KofBinary(KofBinaryOp.ADD, KofExprMatchResult.INT));
    ops.add(new KofStoreLocal(KofExprMatchResult.INT, KofExprMatchResult.SLOT_SEARCH_A));
    ops.add(new KofJump(loop));
    ops.add(new KofLabel(tail));
    // result(5) = result(5) + s.substring(copyFrom(6), len)
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, 5));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofLoadLocal(KofExprMatchResult.INT, 6));
    ops.add(new KofLoadLocal(KofExprMatchResult.STR, KofExprMatchResult.SLOT_S));
    ops.add(new KofCall(KofExprMatchResult.STR, "length", List.of(), KofExprMatchResult.INT, KofCallKind.INSTANCE));
    ops.add(new KofCall(KofExprMatchResult.STR, "substring", List.of(KofExprMatchResult.INT, KofExprMatchResult.INT), KofExprMatchResult.STR, KofCallKind.INSTANCE));
    ops.add(new KofCall(KofExprMatchResult.STR, "kof_string_concat", List.of(KofExprMatchResult.STR, KofExprMatchResult.STR), KofExprMatchResult.STR, KofCallKind.FUNCTION));
    ops.add(new KofReturn(KofExprMatchResult.STR));
    List<IRLocalVariable> locals = surface.localsFor(
            new IRLocalVariable(1, "replacement", KofExprMatchResult.STR),
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_A, "start", KofExprMatchResult.INT),
            new IRLocalVariable(KofExprMatchResult.SLOT_SEARCH_B, "end", KofExprMatchResult.INT),
            new IRLocalVariable(5, "result", KofExprMatchResult.STR),
            new IRLocalVariable(6, "copyFrom", KofExprMatchResult.INT));
    return new IRMethod("replace", KofExprMatchResult.STR, List.of(KofExprMatchResult.STR, KofExprMatchResult.STR),
            AccessFlags.PUBLIC | AccessFlags.STATIC, List.of(),
            List.of(new IRBasicBlock(0, ops)), locals);
}

}

package dev.kof.compiler;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, StatementLowerer 611→<600): a emissão
 * do `try/catch/finally` (§551/DD-01/§549/§617 — os comentários de raiz
 * SIGSEGV/frames viajam com o código). Assinatura idêntica ao padrão dos
 * demais satélites; nada muda no comportamento.
 */
final class StatementLowererTry {

    private StatementLowererTry() {}

    static int emitTryStmt(CompilerDriver driver, TryStmt ts, List<KofOperation> ops, String owner,
                           int localIdx, List<IRLocalVariable> locals, Type returnType) {
        // §774: `try {T} catch {C} finally {F}` is `try { try {T} catch {C} } finally {F}`
        // (JLS 14.20.2): the finally also guards the catch bodies, so a throw
        // inside a catch still runs F before propagating, on every target.
        if (!ts.catchClauses().isEmpty() && !ts.finallyBody().isEmpty()) {
            TryStmt inner = new TryStmt(ts.position(), ts.tryBody(), ts.catchClauses(), List.of());
            return emitTryStmt(driver, new TryStmt(ts.position(), List.of(inner), List.of(), ts.finallyBody()),
                    ops, owner, localIdx, locals, returnType);
        }
                LabelId tryStart = LabelId.create();
                LabelId tryEnd = LabelId.create();
                LabelId doneLabel = LabelId.create();
                boolean hasFinally = !ts.finallyBody().isEmpty();
                LabelId finallyLabel = LabelId.create();
                LabelId rethrowLabel = hasFinally ? LabelId.create() : doneLabel;
                LabelId catchAllLabel = LabelId.create();
                LabelId primaryHandler = LabelId.create();
                boolean hasCatch = !ts.catchClauses().isEmpty();
                String primaryExcType = hasCatch ? ts.catchClauses().getFirst().exceptionType() : "Throwable";
                int primaryExcLocal = localIdx++;
                if (hasCatch) {
                    locals.add(new IRLocalVariable(primaryExcLocal, ts.catchClauses().getFirst().exceptionName(),
                            CompilerTypes.exceptionType(primaryExcType, driver.currentUnit)));
                } else if (hasFinally) {
                    locals.add(new IRLocalVariable(primaryExcLocal, "#excTmp",
                            new Type.ClassType("java.lang", "Throwable", List.of())));
                }
                ops.add(new KofTryStart(tryStart, tryEnd,
                        hasCatch ? primaryHandler : catchAllLabel, primaryExcType, primaryExcLocal));
                // §551: o corpo do try tem o handler VIVO em runtime (o pop é o
                // KofExcUnlink do caminho normal abaixo); os corpos de catch e
                // finally rodam JÁ desvinculados (kof_throw_string desempilha
                // antes de saltar para o handler).
                driver.tryDepth++;
                // DD-01 (bug 45): o frame entra ANTES do corpo do try — o
                // ReturnStmt do corpo precisa vê-lo (store+jump p/ epílogo).
                LabelId returnFinallyLabel = null;
                int retSlot = -1;
                if (hasFinally) {
                    returnFinallyLabel = LabelId.create();
                    if (!Type.isVoid(returnType)) {
                        retSlot = localIdx++;
                        locals.add(new IRLocalVariable(retSlot, "#retVal", returnType));
                    }
                    driver.finallyFrames.push(new CompilerDriverState.FinallyFrame(
                            returnFinallyLabel, rethrowLabel, retSlot, returnType, driver.tryDepth,
                            ts.finallyBody()));
                }
                for (StatementNode s : ts.tryBody()) {
                    localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                }
                // §549: o caminho normal salta por cima do KofTryEnd (que fica
                // no ramo else, inalcançável) — sem este pop o handler nativo
                // continuava vinculado e capturava o throw SEGUINTE ao try.
                ops.add(new KofExcUnlink());
                ops.add(new KofJump(finallyLabel));
                ops.add(new KofLabel(tryEnd));
                // §551: a partir daqui (catch/finally) o handler já foi
                // desvinculado pelo throw — um break/continue/return daqui não
                // desvincula esta região (só as envolventes).
                driver.tryDepth--;
                for (int ci = 0; ci < ts.catchClauses().size(); ci++) {
                    CatchClause cc = ts.catchClauses().get(ci);
                    LabelId handlerLabel = ci == 0 ? primaryHandler : LabelId.create();
                    int excIdx = ci == 0 ? primaryExcLocal : localIdx++;
                    if (ci > 0) {
                        locals.add(new IRLocalVariable(excIdx, cc.exceptionName(),
                                CompilerTypes.exceptionType(cc.exceptionType(), driver.currentUnit)));
                    }
                    ops.add(new KofCatchStart(handlerLabel, cc.exceptionType(), excIdx));
                    // o corpo do catch deve enxergar apenas os locals ATÉ este
                    // catch — com try aninhado de catch de MESMO nome, o local
                    // do catch interno (slot maior) sobrescrevia o externo no
                    // findLocalVar (bug 38: handler externo lia slot do interno).
                    int catchLocalEnd = locals.size();
                    for (int li = locals.size() - 1; li >= 0; li--) {
                        if (locals.get(li).index() == excIdx) { catchLocalEnd = li + 1; break; }
                    }
                    localIdx = driver.emitStatement(new BlockStmt(cc.position(), cc.body()), ops, owner, localIdx,
                            locals.subList(0, catchLocalEnd), returnType);
                    ops.add(new KofJump(finallyLabel));
                }
                if (hasFinally) {
                    // frame já empilhado antes do corpo (DD-01); reusa os ids
                    LabelId returnFinallyL = returnFinallyLabel;
                    int excTmp = hasCatch ? localIdx++ : primaryExcLocal;
                    if (hasCatch) {
                        locals.add(new IRLocalVariable(excTmp, "#excTmp",
                                new Type.ClassType("java.lang", "Throwable", List.of())));
                    }
                    ops.add(new KofCatchStart(catchAllLabel, "Throwable", excTmp));
                    ops.add(new KofJump(rethrowLabel));
                    ops.add(new KofTryEnd());
                    // frame sai ANTES dos epílogos: return DENTRO do finally
                    // faz return direto (sombra — Java: finally return vence)
                    driver.finallyFrames.pop();
                    ops.add(new KofLabel(finallyLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofJump(doneLabel));
                    ops.add(new KofLabel(rethrowLabel));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    ops.add(new KofLoadLocal(new Type.ClassType("java.lang", "Throwable", List.of()), excTmp));
                    ops.add(new KofThrow());
                    // caminho return-no-try/catch: o return site já desvinculou
                    // os handlers vivos (§551 residual); aqui só roda o finally,
                    // encadeia num finally externo ou retorna o valor.
                    ops.add(new KofLabel(returnFinallyL));
                    for (StatementNode s : ts.finallyBody()) {
                        localIdx = driver.emitStatement(s, ops, owner, localIdx, locals, returnType);
                    }
                    if (!driver.finallyFrames.isEmpty()) {
                        CompilerDriverState.FinallyFrame outer = driver.finallyFrames.peek();
                        if (retSlot >= 0) {
                            ops.add(new KofLoadLocal(returnType, retSlot));
                            ops.add(new KofStoreLocal(outer.returnType(), outer.slotValor()));
                        }
                        ops.add(new KofJump(outer.returnFinallyLabel()));
                    } else if (retSlot >= 0) {
                        ops.add(new KofLoadLocal(returnType, retSlot));
                        ops.add(new KofReturn(returnType));
                    } else {
                        ops.add(new KofReturnVoid());
                    }
                } else {
                    ops.add(new KofTryEnd());
                    ops.add(new KofLabel(finallyLabel));
                }
                ops.add(new KofLabel(doneLabel));
        return localIdx;
    }
}

package dev.kof.compiler.js;

import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.LabelId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JsTryParser — reconhece a região try/catch/finally do IR e produz
 * JsIr.JsTry (REFACTOR-500 ratchet §140: extraído de
 * {@link JsControlFlowParser}, mesma responsabilidade separável de
 * JsIfThrowElse/JsLabelParser). DD-01/bug45/bug49 preservados verbatim; o
 * split do §266 só moveu o arquivo (zero mudança de comportamento).
 */
final class JsTryParser {

    private final JsControlFlowParser flow;
    private final JsMethodParser p;

    JsTryParser(JsControlFlowParser flow, JsMethodParser p) {
        this.flow = flow;
        this.p = p;
    }

JsIr.JsStatement parse(MethodCtx ctx, int[] pos) {
        KofTryStart ts = (KofTryStart) ctx.ops.get(pos[0]);
                pos[0]++;
        // DD-01 (bug 45): lookahead do label return-finally — é o alvo do
        // KofJump que aparece logo após o store #retVal no corpo do try.
        // §617 face B: a varredura é CIENTE DE PROFUNDIDADE — um `#retVal` de
        // um try ANINHADO (mesmo nome de local, índice diferente) não pode ser
        // confundido com o deste try (era a causa do ICE `unexpected
        // KofCatchStart` em try/finally aninhado com return interno). O store
        // DESTE try é o primeiro em profundidade relativa 1; aninhados ficam em
        // >=2 e o KofTryEnd do aninhado decrementa de volta. O valor achado é
        // SALVO antes de parsear o corpo: o parse do try aninhado sobrescreve
        // ctx.currentReturnFinallyLabel, então o epílogo externo usaria o label
        // errado.
        LabelId myReturnFinally = null;
        {
            int depth = 1;
            for (int i = pos[0]; i < ctx.ops.size(); i++) {
                if (ctx.ops.get(i) instanceof KofTryStart) { depth++; continue; }
                if (ctx.ops.get(i) instanceof KofTryEnd) {
                    depth--;
                    if (depth == 0) break;
                    continue;
                }
                if (depth == 1 && ctx.ops.get(i) instanceof KofStoreLocal sl
                        && "#retVal".equals(ctx.rawLocalNames.get(sl.index()))) {
                    for (int j = i + 1; j < ctx.ops.size() && j <= i + 3; j++) {
                        if (ctx.ops.get(j) instanceof KofJump kj) { myReturnFinally = kj.target(); break; }
                        if (ctx.ops.get(j) instanceof KofLabel) break;
                    }
                    break;
                }
            }
        }
        ctx.currentReturnFinallyLabel = myReturnFinally;
        List<JsIr.JsStatement> tryBody = flow.parseStatements(ctx, pos, Set.of(ts.endLabel()), new ArrayList<>());
        // DD-01: com return no corpo (sem catch), o body para no primeiro
        // region-exit (jump p/ returnFinally) e sobra o jump do fluxo normal
        // p/ o finally — consumir jumps soltos até o endLabel/TryEnd/CatchStart.
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
            pos[0]++;
        }
        if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel tryEnd
                && tryEnd.label().equals(ts.endLabel())) {
            // The end label may already have been consumed as a region exit
            // (e.g. when the try body ends with throw: the trailing jump is
            // unreachable and the optimizer drops it).
            pos[0]++;
        }
        List<JsIr.JsCatchClause> catches = new ArrayList<>();
        boolean hasFinally = false;
        while (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofCatchStart cs) {
            // A catch-all SINTÉTICA que emula `finally` (StatementLowerer:546)
            // também chega aqui com exceptionType "Throwable". Distinguir do
            // `catch (Throwable e)` ESCRITO PELO USUÁRIO pelo local vinculado:
            // o synth usa o slot temporário "#excTmp" (rawLocalNames com
            // prefixo "#" — nunca um identificador Kof válido); o catch do
            // usuário vincula um nome real ("e"). Sem isto, um
            // `catch(Throwable e)` do usuário caía no ramo de finally e o corpo
            // sobrava -> ICE "try expected KofTryEnd" (pegadinha medida 18/09;
            // `catch(Exception/RuntimeException)` escapava só por ter outra
            // string, não por ser tratado).
            String excLocal = ctx.rawLocalNames.get(cs.localIndex());
            boolean syntheticFinallyCatch = "Throwable".equals(cs.exceptionType())
                    && excLocal != null && excLocal.startsWith("#");
            if (syntheticFinallyCatch) {
                // catch-all + rethrow emulates finally; JS finally is native.
                hasFinally = true;
                pos[0]++;
                if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofJump) {
                    pos[0]++;
                }
                break;
            }
            pos[0]++;
            String param = p.expr.localName(ctx, cs.localIndex());
            List<JsIr.JsStatement> catchBody = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            catches.add(new JsIr.JsCatchClause(param, catchBody));
        }
        if (pos[0] >= ctx.ops.size() || !(ctx.ops.get(pos[0]) instanceof KofTryEnd)) {
            throw new IllegalStateException("KofJS: try expected KofTryEnd at " + pos[0]
                    + " of " + ctx.ops.size() + ": " + (pos[0] < ctx.ops.size() ? ctx.ops.get(pos[0]) : "eof"));
        }
        pos[0]++;
        List<JsIr.JsStatement> finallyBody = List.of();
        // o label do finally é novo da região do try — nunca do loop. Só existe
        // quando o try tem finally (catch-all "Throwable"): um KofLabel que não
        // é o fim de um finally pertence ao try ANINHADO/outer (bug 49).
        if (hasFinally && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel finallyStart
                && !ctx.isLoopLabel(finallyStart.label())) {
            pos[0]++;
            List<LabelId> exits = new ArrayList<>();
            finallyBody = flow.parseStatements(ctx, pos, Set.of(), exits);
            // skip the rethrow machinery: Label(rethrow) ... Label(done)
            // DD-01: se há return-finally (return no corpo), o epílogo vem
            // ANTES do Label(done) — parar o skip nele e parsear o epílogo.
            LabelId done = exits.isEmpty() ? null : exits.get(exits.size() - 1);
            LabelId rf = myReturnFinally;
            if (rf != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(rf))) {
                    pos[0]++;
                }
            } else if (done != null) {
                while (pos[0] < ctx.ops.size() && !(ctx.ops.get(pos[0]) instanceof KofLabel kl
                        && kl.label().equals(done))) {
                    pos[0]++;
                }
                if (pos[0] < ctx.ops.size()) pos[0]++;
            } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel) {
                // no-finally: the trailing empty label (done) ends the try
                pos[0]++;
            }
        } else if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel doneLabel
                && !ctx.isLoopLabel(doneLabel.label())
                && !ctx.isTryEndLabel(doneLabel.label())) {
            // sem finally: o label done (trailing) encerra o try — consome,
            // mas NÃO se for o endLabel de um try aninhado/outer (bug 49).
            pos[0]++;
        }
        if (!finallyBody.isEmpty() && finallyBody.get(finallyBody.size() - 1) instanceof JsIr.JsThrow) {
            // bug 45: o rethrow Kof (`throw _excTmp`) que abre o caminho de
            // exceção do finally é REDUNDANTE no try/finally nativo do JS (que
            // relança automaticamente). Pior: quando o try `return`s, o throw
            // de `_excTmp` (undefined) aborta o retorno → `undefined`. Removê-lo
            // faz o finally rodar e o retorno prevalecer (Java-correct).
            finallyBody = finallyBody.subList(0, finallyBody.size() - 1);
        }
        // DD-01 (bug 45): após o caminho de rethrow vem Label(returnFinally)
        // + finallyBody + (load #retVal + return). No JS o try/finally NATIVO
        // já executa o corpo do finally no caminho do return — o epílogo IR
        // repetiria o corpo (duplicado). Consumimos os ops e descartamos o
        // corpo, preservando SÓ o return final do valor.
        List<JsIr.JsStatement> returnFinally = new ArrayList<>();
        if (hasFinally && pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel rf
                && rf.label().equals(myReturnFinally)) {
            pos[0]++;
            List<JsIr.JsStatement> epilogue = flow.parseStatements(ctx, pos, Set.of(), new ArrayList<>());
            // §617 face B: o epílogo repete o corpo do finally (que o
            // try/finally NATIVO do JS já executa) como PREFIXO exato, antes da
            // cauda real — descartar o prefixo que coincide com finallyBody,
            // preservando a cauda: o `return` final (single-try) OU a cadeia
            // `#retVal = <slot interno>; return #retVal` (try/finally aninhado,
            // onde o valor do return interno precisa sobreviver ao finally
            // externo). Antes, só o JsReturn era preservado e o caso aninhado
            // devolvia `undefined`.
            int epiStart = 0;
            while (epiStart < epilogue.size() && epiStart < finallyBody.size()
                    && epilogue.get(epiStart).equals(finallyBody.get(epiStart))) {
                epiStart++;
            }
            returnFinally.addAll(epilogue.subList(epiStart, epilogue.size()));
            // o Label(done) do try encerra o epílogo — consumir (não é loop:
            // nenhum jump posterior aponta p/ ele depois do epílogo parseado).
            // §617 face B: NÃO consumir se for o endLabel de um try ENVOLVENTE
            // (o KofCatchStart do outer ficaria solto → COMP002).
            if (pos[0] < ctx.ops.size() && ctx.ops.get(pos[0]) instanceof KofLabel dl
                    && !ctx.isLoopLabel(dl.label()) && !ctx.isTryEndLabel(dl.label())) {
                pos[0]++;
            }
        }
        return new JsIr.JsTry(tryBody, catches, finallyBody, returnFinally);
    }

}

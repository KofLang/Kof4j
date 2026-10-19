package dev.kof.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeStatements 713→<600): o
 * reconhecedor recursivo de ESTRUTURAS (struct — if/else, while, do-while,
 * try/catch, break de saída de laço, orientação de valor) move-se verbatim.
 */
final class BytecodeStatementsRecover {

    private BytecodeStatementsRecover() {}

    /**
     * Fase E (15/10): a recovery de REGIAO — o struct sobre UMA SUB-REGIAO
     * (o corpo do try nao-linear), estado global fresco + um `declared`
     * EXTERNO compartilhado (o local hoisted pelo caller). null = o stub
     * honesto do caller (R6).
     */
    static java.util.List<String> recoverRegionStruct(java.util.List<BytecodeReader.Insn> region,
            String[] cp, BytecodeFrame frame, java.util.Set<Integer> declared, int[] extraLeaders) {
        // Fase E (15/10): os alvos dos HANDLERS como LIDERES — sem eles o
        // athrow do handler funde-se ao bloco precedente e MATA a aresta do
        // goto (medido no withFinally: o blk[19) saia succ=[] e o walk
        // parava no finally-inline com early-true)
        java.util.List<BytecodeReader.Block> blocks = BytecodeReader.cfg(region, extraLeaders);
        if (blocks.isEmpty()) return null;
        Map<Integer, BytecodeReader.Block> byStart = new java.util.HashMap<>();
        for (BytecodeReader.Block b : blocks) byStart.put(b.start, b);
        BytecodeStatements.skipFirst = null;
        BytecodeStatements.currentLoopExit = -1;
        BytecodeStatements.builderSlots = new HashSet<>();
        if (BytecodeStatements.skipCount == null) BytecodeStatements.skipCount = new java.util.HashMap<>();
        BytecodeStatements.skipCount.clear();
        java.util.List<String> out = new ArrayList<>();
        Set<Integer> emitted = new HashSet<>();
        boolean ok = struct(blocks.get(0), region, byStart, cp, frame, out, emitted, declared, -1,
                new HashSet<>());
        return ok ? out : null;
    }

static boolean struct(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                              Map<Integer, BytecodeReader.Block> byStart, String[] cp,
                              BytecodeFrame frame, List<String> out,
                              Set<Integer> emitted, Set<Integer> declared, int header,
                              Set<Integer> stops) {
        if (System.getenv("KOF_WALK") != null && b != null)
            System.out.println("WALK enter blk[" + b.start + ") succ=" + b.succ + " header=" + header);
    // Fase C: bloco do PÓS-IF (join de if-sem-else) que ainda NÃO foi
    // emitido = borda do braço: para AQUI sem emitir (a sequela cuida do
    // join). If-aninhado sem else no fim do braço para natural (join do
    // interno cai no mesmo lugar do externo — verificado por tipos).
    if (!stops.isEmpty() && stops.contains(b.start) && !emitted.contains(b.start)) {
        return true;
    }
    // Re-entrância de bloco: só a aresta de volta ao header do loop
    // ATUALMENTE ABERTO é legítima (back-edge normal; continue pode cair
    // no próprio header/incremento do loop). Re-entrar em bloco já emitido
    // que NÃO é o header aberto = ponto de junção compartilhado entre
    // braços de diamond (break, continue de loop externo, &&/||/?: com
    // braços que caem no mesmo bloco) — re-emiti-lo dentro de um ramo
    // perde o fluxo do outro ramo (ex.: `for` com `continue`: incremento
    // emitido só num dos caminhos; `&&`: return final sugado p/ o else
    // interno). Recovery de join estruturado é trabalho futuro; recusar →
    // stub UNKNOWN honesto (R6: nunca código errado compilável).
    if (emitted.contains(b.start)) return b.start == header;
    emitted.add(b.start);

    if (b.succ.isEmpty()) {
        // Fase E (15/10): blkSeq (não insnsWithin) — o skipFirst do
        // if-expression (medido no Ledger.report: o astore_3 do join era
        // re-emitido com a pilha vazia → emitLinear-null) só é consumido
        // pelo blkSeq
        List<String> stmts = BytecodeStatementsEmit.emitLinear(BytecodeStatements.blkSeq(b, insns), cp, frame, declared);
        if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
        out.addAll(stmts);
        return true;
    }
    if (b.succ.size() == 2) {
        // Bottom-tested loop (do-while): o bloco cond tem aresta para si
        // MESMO ou para um bloco ANTERIOR (teste embaixo; o corpo fica
        // fundido no próprio bloco). Em loop top-tested (while/for) o
        // bloco de teste é o menor offset do laço e ambos os sucessores
        // são > start (o back-edge vem de um bloco posterior).
        int back = -1;
        int exit = -1;
        for (int s : b.succ) {
            if (s <= b.start) back = s; else exit = s;
        }
        if (back >= 0) {
            if (back != b.start) { BytecodeStatements.refuse("loop-body-separate"); return false; }  // corpo separado do teste — não recuperar
            List<BytecodeReader.Insn> block = BytecodeDecoder.insnsWithin(b, insns);
            if (block.isEmpty()) { BytecodeStatements.refuse("empty-body"); return false; }
            BytecodeReader.Insn last = block.get(block.size() - 1);
            if (!last.isCond()) { BytecodeStatements.refuse("do-while-no-cond"); return false; }
            int op = last.opcode();
            String cond;
            List<BytecodeReader.Insn> body;
            if (op >= 0x99 && op <= 0x9e) {
                if (block.size() < 2) { BytecodeStatements.refuse("do-while-short"); return false; }
                String a = BytecodeDecoderStack.loadValue(block.get(block.size() - 2), frame);
                if (a == null) { BytecodeStatements.refuse("do-while-load-null"); return false; }
                cond = a + " " + BytecodeStatements.contCond(op);
                body = block.subList(0, block.size() - 2);
            } else if (op >= 0x9f && op <= 0xa4) {
                if (block.size() < 3) { BytecodeStatements.refuse("do-while-short"); return false; }
                String x = BytecodeDecoderStack.loadValue(block.get(block.size() - 3), frame);
                String y = BytecodeDecoderStack.loadValue(block.get(block.size() - 2), frame);
                if (x == null || y == null) { BytecodeStatements.refuse("do-while-load-null"); return false; }
                cond = x + " " + BytecodeStatements.contCond(op) + " " + y;
                body = block.subList(0, block.size() - 3);
            } else {
                { BytecodeStatements.refuse("struct-generic"); return false; }
            }
            if (body.isEmpty()) { BytecodeStatements.refuse("loop-body-empty"); return false; }
            List<String> stmts = BytecodeStatementsEmit.emitLinear(body, cp, frame, declared);
            if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
            out.add("do {");
            out.addAll(stmts);
            out.add("} while (" + cond + ")");
            return struct(byStart.get(exit), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        String cond = BytecodeDecoder.blockCondition(b, insns, frame, cp);
        int exitStart = b.succ.get(0);   // alvo do branch (falso)
        int thenStart = b.succ.get(1);   // fall-through (verdadeiro)
        boolean loop0 = BytecodeReader.isLoopHeader(b);
        // Fase E (14/10): visíveis ao walk do || — o 1º termo é o SUFIXO
        // do bloco fundido (o prólogo já foi emitido pelo fusion)
        List<BytecodeReader.Insn> fusedBlk = null;
        int fusedK = 0;
        int fusedLastOp = -1;
        BytecodeDecoder.TestSplit split = null;
        if (cond == null && !loop0) {
            // Fase C (prologue no mesmo bloco do teste): javac funde
            // `int r = 100; if (x > 5)` num bloco so — o blockCondition
            // classico (so loads) recusa e stubava o metodo inteiro.
            // Conservador: o PREFIXO vai p/ emitLinear (recusa o que nao
            // souber emitir) e o cond sao os ULTIMOS K insns (loads
            // diretos p/ o condicional; pilha do cond vem deles). Path
            // classico (cond != null) NAO toca aqui = byte-identico.
            List<BytecodeReader.Insn> blk = BytecodeStatements.blkSeq(b, insns);
            BytecodeReader.Insn last = blk.get(blk.size() - 1);
            int lop = last.opcode();
            int k = (lop >= 0x9f && lop <= 0xa4) ? 2 : 1;   // if_acmp* = 2 ops; demais 1
            String inv = BytecodeCp.invCond(lop);
            // Fase C (13/10): o SPLIT seguro (prólogo-statements +
            // expressão) — o prólogo termina em store/pop; a expressão
            // avalia EXATAMENTE os `arity` operandos. Fecha o risco do
            // sufixo-fragmento reaproveitar um valor como receptor
            // (medido: `[ldc "(", at]` avaliava mas era código ERRADO).
            split = (inv != null && last.isCond())
                    ? BytecodeDecoder.testSplit(blk, frame, cp, k, inv) : null;
            fusedBlk = blk;
            fusedK = k;
            fusedLastOp = last.opcode();
            if (System.getenv("KOF_CA") != null) {
                StringBuilder ops = new StringBuilder();
                for (BytecodeReader.Insn bi : blk) ops.append(Integer.toHexString(bi.opcode())).append("@").append(bi.offset()).append(" ");
                System.out.println("GATE blk=[" + b.start + ") size=" + blk.size() + " lop=0x"
                        + Integer.toHexString(lop) + " inv=" + inv + " cond=" + cond
                        + " then=" + thenStart + " exit=" + exitStart + " ops=" + ops);
            }

            // Fase E (16/10): o BINDING-PODADO + o CALLARG — as duas formas
            // fundidas que o testSplit classico recusa (medido no Wf.heads):
            // (i) o blkSeq corta o prefixo [valor..., astore] do bloco do
            // leitor (o elemento do for-each fundido com o teste) — o valor
            // e computavel do prefixo; registra o slot ANTES do split para o
            // teste avaliar o local (o v3 declarado = zero codigo solto).
            // (ii) o join = [call 1-arg, pop] com o pre terminando em LOAD
            // (o receiver na pilha) — o split com allowLoad.
            if (split == null && blk.get(0).offset() > b.start) {
                List<BytecodeReader.Insn> lead = new java.util.ArrayList<>();
                for (BytecodeReader.Insn ii : insns)
                    if (ii.offset() >= b.start && ii.offset() < blk.get(0).offset()) lead.add(ii);
                if (System.getenv("KOF_CA") != null)
                    System.out.println("LEAD b=[" + b.start + ") blk0=" + blk.get(0).offset()
                            + " lead=" + lead.size() + " insns=" + insns.size());
                int lop2 = lead.isEmpty() ? -1 : lead.get(lead.size() - 1).opcode();
                if (lop2 == 0x3a || (lop2 >= 0x4b && lop2 <= 0x4e)) {   // astore (a forma normalizada + _n)
                    int slot2 = lop2 == 0x3a
                            ? lead.get(lead.size() - 1).operands()[0]
                            : lop2 - 0x4b;
                    if (System.getenv("KOF_CA") != null)
                        System.out.println("BIND slot=" + slot2);
                    String vVal = BytecodeDecoder.stackValue(
                            lead.subList(0, lead.size() - 1), frame, cp);
                    if (vVal != null) {
                        String nm2 = frame.name(slot2);
                        // O for-each ja declara o elemento no cabecalho
                        // (`for (var v3 in arg0)`) — a binding podada e DELE
                        // (o it.next + checkcast): registra o slot, mas NAO
                        // re-emite a linha (o v2 do iterator nao existe no
                        // codigo emitido).
                        boolean forEachOwned = false;
                        for (String o : out)
                            if (o.contains("for (var " + nm2 + " in ")) forEachOwned = true;
                        if (!forEachOwned) {
                            frame.bindSlot(slot2, nm2);
                            declared.add(slot2);
                            out.add("    var " + nm2 + " = " + vVal);
                        }
                        split = BytecodeDecoder.testSplit(blk, frame, cp, k, inv, true);
                    }
                }
            }
            if (split == null) {
                BytecodeReader.Block pcThen = byStart.get(thenStart);
                if (pcThen != null && pcThen.succ.size() == 1) {
                    BytecodeReader.Block pcJoin = byStart.get(pcThen.succ.get(0));
                    List<BytecodeReader.Insn> pcJ = pcJoin == null ? java.util.List.of()
                            : BytecodeDecoder.insnsWithin(pcJoin, insns);
                    if (pcJ.size() >= 2 && (pcJ.get(0).opcode() == 0xb6 || pcJ.get(0).opcode() == 0xb9)
                            && pcJ.get(1).opcode() == 0x57) {
                        String[] pcM = BytecodeCp.resolveMethodRef(cp, pcJ.get(0).operands()[0]);
                        if (pcM != null && BytecodeCp.argCount(pcM[2]) == 1)
                            split = BytecodeDecoder.testSplit(blk, frame, cp, k, inv, true);
                    }
                }
            }

            if (split != null) {
                List<BytecodeReader.Insn> preInsns = blk.subList(0, split.prologueLen());
                // emitLinear PARA e retorna parcial em branch (linha do
                // 0xa7) — prefixo com fluxo interno truncaria codigo em
                // silencio (codigo errado compilavel). Recusa -> stub.
                for (BytecodeReader.Insn pi : preInsns) {
                    int po = pi.opcode();
                    boolean fluxo = (po >= 0x99 && po <= 0xa7) || (po >= 0xaa && po <= 0xb1) || po == 0xbf;
                    if (fluxo) { BytecodeStatements.refuse("prologue-has-flow"); return false; }
                }
                // Fase E (16/10): o TERNARIO-COMO-ARG — o pre pode terminar
                // em LOAD (o receiver da call do join na pilha). Os bracos =
                // valores (o stackValueStrip); o join = [call 1-arg, pop].
                // A emissao recv.call(if (cond) t else e) + o skip do
                // call/pop no join.
                BytecodeReader.Insn recvI = preInsns.isEmpty() ? null : preInsns.get(preInsns.size() - 1);
                int ro = recvI == null ? -1 : recvI.opcode();
                boolean preLoad = (ro >= 0x15 && ro <= 0x35);
                String recv = preLoad ? BytecodeDecoderStack.loadValue(recvI, frame) : null;
                BytecodeReader.Block joinC2 = null;
                String jmName = null;
                String tVal = null;
                String eVal = null;
                if (preLoad && recv != null) {
                    BytecodeReader.Block thenC2 = byStart.get(thenStart);
                    if (thenC2 != null && thenC2.succ.size() == 1) {
                        joinC2 = byStart.get(thenC2.succ.get(0));
                        List<BytecodeReader.Insn> jI2 = joinC2 == null ? java.util.List.of()
                                : BytecodeDecoder.insnsWithin(joinC2, insns);
                        if (jI2.size() >= 2 && (jI2.get(0).opcode() == 0xb6 || jI2.get(0).opcode() == 0xb9)
                                && jI2.get(1).opcode() == 0x57) {
                            String[] jm2 = BytecodeCp.resolveMethodRef(cp, jI2.get(0).operands()[0]);
                            tVal = BytecodeStatements.stackValueStrip(
                                    BytecodeDecoder.insnsWithin(thenC2, insns), frame, cp);
                            eVal = BytecodeStatements.stackValueStrip(
                                    BytecodeDecoder.insnsWithin(
                                            byStart.get(exitStart), insns), frame, cp);
                            if (jm2 != null && BytecodeCp.argCount(jm2[2]) == 1
                                    && tVal != null && eVal != null) {
                                jmName = jm2[1];
                            } else {
                                joinC2 = null;
                            }
                        } else {
                            joinC2 = null;
                        }
                    } else {
                        joinC2 = null;
                    }
                }
                List<String> pre;
                if (joinC2 != null) {
                    pre = preInsns.size() > 1
                            ? BytecodeStatementsEmit.emitLinear(
                                    preInsns.subList(0, preInsns.size() - 1), cp, frame, declared)
                            : List.of();
                } else {
                    pre = BytecodeStatementsEmit.emitLinear(preInsns, cp, frame, declared);
                }
                if (pre == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
                cond = split.cond();
                if (joinC2 != null) {
                    out.addAll(pre);
                    out.add("    " + recv + "." + jmName + "(if (" + cond + ") " + tVal
                            + " else " + eVal + ")");
                    emitted.add(thenStart);
                    emitted.add(exitStart);
                    BytecodeStatements.skipCount.put(joinC2.start, 2);
                    return struct(joinC2, insns, byStart, cp, frame, out, emitted,
                            declared, header, stops);
                }
                out.addAll(pre);
            }
        }
        if (cond == null) { BytecodeStatements.refuse("cond-null"); return false; }
        // Fase C (13/10, short-circuit ||): o `||` tem a orientacao
        // ESPELHADA do &&. Medido (or2/or3): os testes NAO-finais saltam
        // para o VERDADEIRO (takenCond; succ[0]==exitStart) e o ULTIMO teste
        // cai no verdadeiro (invCond; succ[1]==exitStart) com o jump para o
        // FALSO. O bloco externo (b) E o primeiro termo.
        if (!loop0) {
            int guard = 0;
            BytecodeReader.Block tb = b;
            String orCond = null;
            int realFalse = -1;
            java.util.List<Integer> chainStarts = new java.util.ArrayList<>();
            while (guard < 8 && tb != null && tb.succ.size() == 2
                    && !BytecodeReader.isLoopHeader(tb)) {
                chainStarts.add(tb.start);
                if (tb.succ.get(0) == exitStart) {
                    // nao-final: jump=VERDADEIRO
                    String c2;
                    if (tb == b && split != null) {
                        // Fase E (14/10): o bloco fundido (o prólogo já
                        // emitido): o 1º termo é o SUFIXO do split com o
                        // takenCond — o bloco INTEIRO falharia nas stores
                        // do prólogo (medido no parsePort: o walk quebrava
                        // e o cond flat saía errado)
                        List<BytecodeReader.Insn> suf = fusedBlk.subList(
                                split.prologueLen(), fusedBlk.size() - 1);
                        String top = BytecodeCp.takenCond(fusedLastOp);
                        c2 = top == null ? null
                                : BytecodeDecoder.testExpr(suf, frame, fusedK, top, cp);
                        } else {
                        c2 = BytecodeDecoder.blockConditionTaken(tb, insns, frame, cp);
                    }
                    if (c2 == null) {
                        // Fase E (16/10): o split recusou (o testExpr fechado
                        // não modela o cmp de sinal) — cai no blockCondition
                        // Taken (o par-cmp geral cobre o [ops, cmp, ifXX]).
                        c2 = BytecodeDecoder.blockConditionTaken(tb, insns, frame, cp);
                    }
                    if (System.getenv("KOF_CA") != null)
                        System.out.println("OR-ITER tb=[" + tb.start + ") sz=" + BytecodeDecoder.insnsWithin(tb, insns).size()
                                + " c2=" + (c2 == null ? "NULL" : c2) + " split=" + (split != null));
                    if (c2 == null) break;
                    orCond = orCond == null ? c2 : orCond + " || " + c2;
                    tb = byStart.get(tb.succ.get(1));
                    guard++;
                } else if (tb.succ.get(1) == exitStart && orCond != null) {
                    // ULTIMO: jump=FALSO (invCond), o jump e o falso real
                    String c2 = BytecodeDecoder.blockCondition(tb, insns, frame, cp);
                    if (c2 == null) break;
                    orCond = orCond + " || " + c2;
                    realFalse = tb.succ.get(0);
                    break;
                } else {
                    // Fase E (14/10): o 2º termo do || é uma CADEIA && (a
                    // árvore de condição — `v > 100 || (v > 50 && v < 75)`);
                    // o condTree recursa com limite de profundidade.
                    String rest = condTree(tb, exitStart, tb.succ.get(0),
                            byStart, insns, frame, cp, 0);
                    if (rest == null) break;
                    orCond = orCond + " || (" + rest + ")";
                    realFalse = tb.succ.get(0);
                    break;
                }
            }
            if (System.getenv("KOF_CA") != null)
                System.out.println("OR-WALK orCond=" + orCond + " realFalse=" + realFalse
                        + " cond=" + (cond == null ? "NULL" : cond));
            if (orCond != null && realFalse >= 0) {
                // Fase E (16/10): o CONTINUE-JOIN COMPARTILHADO — o alvo do
                // falso real é a CONTINUAÇÃO do loop (os preds = os testes
                // da cadeia + a QUEDA do then — o then caiu nele) e não o
                // braço falso: emite if-SEM-else e anda o join DEPOIS (o
                // iinc compartilhado fica FORA do if; o else não re-emite;
                // medido no Text.initials). O discriminador: o braço falso
                // puro NÃO tem o pred da queda do then.
                BytecodeReader.Block sjb = byStart.get(realFalse);
                if (sjb != null && sjb.pred.contains(exitStart)
                        && sjb.pred.size() <= chainStarts.size() + 1) {
                    cond = orCond;
                    Set<Integer> jstops = new HashSet<>(stops);
                    jstops.add(realFalse);
                    out.add("if (" + cond + ") {");
                    if (!struct(byStart.get(exitStart), insns, byStart, cp, frame,
                            out, emitted, declared, header, jstops))
                        { BytecodeStatements.refuse("struct-generic"); return false; }
                    out.add("}");
                    return struct(sjb, insns, byStart, cp, frame, out, emitted,
                            declared, header, stops);
                }
                cond = orCond;
                thenStart = exitStart;   // o verdadeiro
                exitStart = realFalse;   // o falso real
            }
        }
        // Fase C (13/10, short-circuit &&): o THEN-bloco é OUTRO teste
        // cujo falso-alvo é o MESMO else → as condições combinam com
        // `&&` (a avaliação em cadeia do bytecode É o curto-circuit do
        // &&; os braços passam a ser os do teste interno).
        // Fase E (14/10): vale TAMBÉM para o header do LOOP — o
        // `while (a && b)` tem o teste interno (fora do corpo) saltando
        // para a MESMA saída; a combinação não altera then/exit (o corpo
        // já é o fall-through do teste interno). Medido: whileComplex
        // stubava porque o guard `!loop0` pulava a combinação.
        java.util.Set<Integer> chainTests = new java.util.HashSet<>();
        {
            int guard = 0;
            BytecodeReader.Block tb = byStart.get(thenStart);
            while (guard < 4 && tb != null && tb.succ.size() == 2
                    && tb.succ.get(0) == exitStart && !BytecodeReader.isLoopHeader(tb)) {
                chainTests.add(tb.start);
                // succ.size() == 2 EXIGIDO: o && clássico tem then-de-teste
                // com DOIS sucessores (o falso-alvo compartilhado + a
                // queda); um teste com succ único (braços que caem no
                // mesmo join) NÃO combina — o get(1) crashava IOOBE
                // (medido no TranslateStatements real, 13/10).
                List<BytecodeReader.Insn> tblk = BytecodeDecoder.insnsWithin(tb, insns);
                if (tblk.isEmpty() || !tblk.get(tblk.size() - 1).isCond()) break;
                String cond2 = BytecodeDecoder.blockCondition(tb, insns, frame, cp);
                if (cond2 == null) break;
                cond = cond + " && " + cond2;
                thenStart = tb.succ.get(1);
                tb = byStart.get(thenStart);
                guard++;
            }
        }
        // Fase C (13/10, return-join ?:): o ELSE-bloco é [push, xreturn]
        // e o THEN termina em goto→[xreturn] — cada braço deixa UM valor
        // na pilha → if-expression Kof: `return if (cond) tVal else eVal`.
        {
            BytecodeReader.Block elseBlk = byStart.get(exitStart);
            BytecodeReader.Block thenBlk = byStart.get(thenStart);
            if (elseBlk != null && thenBlk != null && !elseBlk.succ.isEmpty()) {
                List<BytecodeReader.Insn> eIns = BytecodeDecoder.insnsWithin(elseBlk, insns);
                int joinAfterElse = elseBlk.succ.get(elseBlk.succ.size() - 1);
                BytecodeReader.Block joinBlk = byStart.get(joinAfterElse);
                // o else-bloco é push PURO (o xreturn é o bloco do join) —
                // Fase E (14/10): a sequência pode ser MULTI-insn (medido no
                // avg: [iload, i2d, aload, size, i2d, ddiv]); o stackValue
                // decide (null = recusa, R6)
                boolean eShape = !eIns.isEmpty()
                        && BytecodeDecoder.stackValue(eIns, frame, cp) != null;
                boolean tShape = thenBlk.succ.size() == 1 && thenBlk.succ.get(0) == joinAfterElse
                        && joinBlk != null && joinBlk.succ.isEmpty();
                if (eShape && tShape) {
                    List<BytecodeReader.Insn> tIns = BytecodeDecoder.insnsWithin(thenBlk, insns);
                    List<BytecodeReader.Insn> jIns = BytecodeDecoder.insnsWithin(joinBlk, insns);
                    if (tIns.size() >= 2 && tIns.get(tIns.size() - 1).opcode() == 0xa7
                            && jIns.size() == 1 && jIns.get(0).opcode() >= 0xac
                            && jIns.get(0).opcode() <= 0xb1) {
                        String tVal = BytecodeDecoder.stackValue(
                                tIns.subList(0, tIns.size() - 1), frame, cp);
                        String eVal = BytecodeDecoder.stackValue(eIns, frame, cp);
                        // (o eVal já saiu do gate eShape — reavaliado aqui
                        // só p/ clareza; ambos null-checked abaixo)
                        if (tVal != null && eVal != null) {
                            // coerção Bool: método `Z` com braços 1/0 → true/false
                            // (o Kof exige Bool; medido — `return if (..) 1 else 0`
                            // num Bool dá SEM010). Vale p/ && e || iguais.
                            if ("Z".equals(frame.retType())) {
                                if ("1".equals(tVal)) tVal = "true";
                                if ("0".equals(tVal)) tVal = "false";
                                if ("1".equals(eVal)) eVal = "true";
                                if ("0".equals(eVal)) eVal = "false";
                            }
                            out.add("    return if (" + cond + ") " + tVal + " else " + eVal);
                            emitted.add(exitStart);
                            emitted.add(joinAfterElse);
                            return true;
                        }
                    }
                }
            }
        }
        // Fase E (14/10): o ELSE-RETURN DENTRO do próprio bloco — o else =
        // [push..., xreturn] (o xreturn não é bloco separado) e o THEN salta
        // DIRETO ao xreturn (compartilhado). Medido no avg:
        // `return if (isEmpty) 0.0 else (sum as Double) / size`. Qualquer
        // desvio → segue o walk (R6).
        {
            BytecodeReader.Block elseBlk = byStart.get(exitStart);
            BytecodeReader.Block thenBlk = byStart.get(thenStart);
            if (elseBlk != null && thenBlk != null && !thenBlk.succ.isEmpty()) {
                List<BytecodeReader.Insn> eIns = BytecodeDecoder.insnsWithin(elseBlk, insns);
                if (!eIns.isEmpty()) {
                    int lastOp = eIns.get(eIns.size() - 1).opcode();
                    if (lastOp >= 0xac && lastOp <= 0xb1
                            && thenBlk.succ.get(0) == eIns.get(eIns.size() - 1).offset()
                            && eIns.size() >= 2) {
                        List<BytecodeReader.Insn> tIns = BytecodeDecoder.insnsWithin(thenBlk, insns);
                        if (tIns.size() >= 2 && tIns.get(tIns.size() - 1).opcode() == 0xa7) {
                            String tVal = BytecodeDecoder.stackValue(
                                    tIns.subList(0, tIns.size() - 1), frame, cp);
                            String eVal = BytecodeDecoder.stackValue(
                                    eIns.subList(0, eIns.size() - 1), frame, cp);
                            if (tVal != null && eVal != null) {
                                if ("Z".equals(frame.retType())) {
                                    if ("1".equals(tVal)) tVal = "true";
                                    if ("0".equals(tVal)) tVal = "false";
                                    if ("1".equals(eVal)) eVal = "true";
                                    if ("0".equals(eVal)) eVal = "false";
                                }
                                out.add("    return if (" + cond + ") " + tVal + " else " + eVal);
                                emitted.add(exitStart);
                                emitted.add(eIns.get(eIns.size() - 1).offset());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        boolean loop = loop0;
        if (loop) {
            // Fase E (14/10): for-each — `var it = X.iterator(); while
            // (it.hasNext()) { var x = (it.next() as T); ... }` → o idioma
            // nativo `for (var x in X) { ... }` (o iterator não existe no
            // Kof — SEM025 medida). O binding (4 insns) é consumido via
            // skipCount; a declaração do iterator sai do output.
            if (cond != null && cond.contains(".hasNext()")) {
                // a cond sai PARENTESADA pelo bin2 — `(v1.hasNext() != 0)`;
                // extrair o identificador com regex, não por substring
                var hM = java.util.regex.Pattern
                        .compile("([A-Za-z_][A-Za-z0-9_]*)\\.hasNext\\(\\)").matcher(cond);
                String itVar = hM.find() ? hM.group(1) : null;
                if (itVar != null && !out.isEmpty()) {
                    String last = out.get(out.size() - 1);
                    String declPrefix = "var " + itVar + " = ";
                    // Fase E (15/10): a fatia nomeada — o 2º for-each do
                    // método: o javac REUTILIZA o slot do iterator (o escopo
                    // do 1º loop morre) e o decl sai SEM `var`
                    // (`v3 = arg1.iterator()`), MAS é a ÚLTIMA linha de out
                    // (emitida pelo bloco linear imediatamente anterior) — a
                    // forma sem-var aceita AQUI (não em busca backward, que
                    // corrompia via re-entrância). O `out.remove(last)` de
                    // baixo já é seguro por construção.
                    String barePrefix = itVar + " = ";
                    String src = null;
                    if (last.startsWith(declPrefix) && last.endsWith(".iterator()")) {
                        src = last.substring(declPrefix.length(),
                                last.length() - ".iterator()".length());
                    } else if (last.startsWith(barePrefix) && last.endsWith(".iterator()")) {
                        src = last.substring(barePrefix.length(),
                                last.length() - ".iterator()".length());
                    }
                    if (src != null) {
                        BytecodeReader.Block bodyFirst = byStart.get(thenStart);
                        List<BytecodeReader.Insn> fblk = bodyFirst == null
                                ? List.of() : BytecodeDecoder.insnsWithin(bodyFirst, insns);
                        // o binding: [aload it, next, (checkcast), astore vX]
                        int bindVar = -1;
                        int bindLen = 0;
                        for (int bi = 0; bi < fblk.size(); bi++) {
                            int bop = fblk.get(bi).opcode();
                            // o elemento pode unboxar (Integer.intValue → int):
                            // istore/astore com índice E as formas _n
                            if (bop == 0x36 || bop == 0x3a) {
                                bindVar = fblk.get(bi).operands()[0];
                                bindLen = bi + 1;
                                break;
                            }
                            // Fase E (16/10): os stores WIDE — o for-each
                            // sobre elementos Long/Float/Double (o unboxing
                            // lvalue/floatValue/doubleValue → o dstore;
                            // medido no Bank.balance/overdrawn: `for (double
                            // t : txs)` com o dstore 4)
                            if (bop == 0x37 || bop == 0x38 || bop == 0x39) {
                                bindVar = fblk.get(bi).operands()[0];
                                bindLen = bi + 1;
                                break;
                            }
                            if (bop >= 0x3b && bop <= 0x3e) {
                                bindVar = bop - 0x3b;
                                bindLen = bi + 1;
                                break;
                            }
                            if (bop >= 0x3f && bop <= 0x4a) {
                                bindVar = (bop - 0x3f) % 4;
                                bindLen = bi + 1;
                                break;
                            }
                            if (bop >= 0x4b && bop <= 0x4e) {
                                bindVar = bop - 0x4b;
                                bindLen = bi + 1;
                                break;
                            }
                        }
                        // R6: o source não pode conter chamadas sem
                        // equivalente (medido: `entrySet()` — o Map do Kof
                        // itera keys/values, não há Entry) → stub honesto
                        if (bindVar >= 0 && bindLen >= 2 && !src.contains(".entrySet()")
                                && !src.contains(".keySet()") && !src.contains(".values()")) {
                            String bindName = BytecodeDecoderStack.slotName(bindVar, frame);
                            if (BytecodeStatements.skipCount == null)
                                BytecodeStatements.skipCount = new java.util.HashMap<>();
                            BytecodeStatements.skipCount.put(bodyFirst.start, bindLen);
                            declared.add(bindVar);
                            out.remove(out.size() - 1);
                            out.add("for (var " + bindName + " in " + src + ") {");
                            int savedExit = BytecodeStatements.currentLoopExit;
                            BytecodeStatements.currentLoopExit = exitStart;
                            boolean bodyOk = struct(bodyFirst, insns, byStart, cp, frame,
                                    out, emitted, declared, b.start, stops);
                            BytecodeStatements.currentLoopExit = savedExit;
                            if (!bodyOk) { BytecodeStatements.refuse("struct-generic"); return false; }
                            out.add("}");
                            return struct(byStart.get(exitStart), insns, byStart, cp, frame,
                                    out, emitted, declared, header, stops);
                        }
                    }
                }
                // Fase E (15/10): o shape ITERATOR que o recovery NÃO pegou
                // (o decl do iterator não é a última linha — o 2º loop do
                // método, medido no Mail.recipients: o javac REUTILIZA o slot
                // e o decl sai sem `var`) emitia o while CRU com locals não-
                // declarados = código não-compilável. Recusa honesta → stub
                // (R6); a fatia nomeada fica registrada no DOING.
                if (cond.contains(".hasNext()")) {
                    BytecodeStatements.refuse("iterator-loop-unrecovered");
                    return false;
                }
            }
            out.add("while (" + cond + ") {");
            int savedExit = BytecodeStatements.currentLoopExit;
            BytecodeStatements.currentLoopExit = exitStart;   // `break` = goto para a saída
            boolean bodyOk = struct(byStart.get(thenStart), insns, byStart, cp, frame,
                    out, emitted, declared, b.start, stops);
            BytecodeStatements.currentLoopExit = savedExit;
            if (!bodyOk) { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        // Fase C (join de if-sem-else): when o alvo do branch FALSO é um
        // join cujos únicos predecessors são o próprio if e o bloco then,
        // o `goto` implícito do then cai no pós-if — emite if SEM else e
        // o braço para no join via `stops` (linhas acima); a sequela
        // emite o join UMA vez (caminho falso = queda natural do if).
        BytecodeReader.Block join = byStart.get(exitStart);
        // Fase E (15/10): o join do && tem os falsos de TODOS os testes da
        // cadeia convergindo (preds > 2 POR DESENHO) — os preds extras
        // permitidos são exatamente os testes coletados no walk; qualquer
        // OUTRO pred segue recusando (R6)
        boolean chainJoin = false;
        if (join != null && !chainTests.isEmpty()
                && join.pred.contains(b.start) && join.pred.contains(thenStart)) {
            chainJoin = true;
            for (int pr : join.pred) {
                if (pr != b.start && pr != thenStart && !chainTests.contains(pr)) { chainJoin = false; break; }
            }
        }
        boolean pureIfThen = join != null
                && ((join.pred.size() == 2 && join.pred.contains(b.start)
                        && join.pred.contains(thenStart)) || chainJoin)
                && !BytecodeReader.isLoopHeader(join)
                && byStart.get(thenStart) != null
                && byStart.get(thenStart).succ.equals(List.of(exitStart));
        BytecodeReader.Block thenB = byStart.get(thenStart);
        BytecodeReader.Block elseB = join;
        int tail = thenB != null && thenB.succ.size() == 1 ? thenB.succ.get(0) : -1;
        boolean pureIfElse = tail >= 0 && elseB != null && tail != exitStart && tail != b.start
                && elseB.succ.equals(List.of(tail))
                && !BytecodeReader.isLoopHeader(byStart.get(tail) != null ? byStart.get(tail) : elseB)
                && byStart.get(tail) != null
                && byStart.get(tail).pred.size() == 2
                && byStart.get(tail).pred.contains(thenStart)
                && byStart.get(tail).pred.contains(exitStart);
        // §238 (degrau 2c): um local escrito nos DOIS ramos e lido apos o
        // join sai com `var` escopado dentro do if -> SEM000 nao-compilavel
        // (anti-R6). Ica a declaracao (default por tipo da store) ANTES do
        // if; so no pureIfElse (o pureIfThen nao tem else nem init
        // escapante — Java valido nao o produz). Se um local escapante for
        // float/ref (sem default seguro) -> RECUSAR p/ stub honesto.
        // Fase C (13/10): VALOR PROPAGADO — os braços deixam exatamente 1
        // valor na pilha e o join começa com a store desse valor →
        // if-expression Kof: `var/if name = if (cond) tVal else eVal` (o
        // astore do join é CONSUMIDO pela atribuição — o skip-first).
        {
            BytecodeReader.Block joinB = byStart.get(tail);
            // guardas de null: o tail nem sempre é fronteira de bloco e os
            // blocos dos braços podem não existir no byStart — sem guarda
            // o insnsWithin crashava NPE (medido no corpus real, 13/10)
            if (joinB != null && thenB != null && elseB != null) {
            List<BytecodeReader.Insn> jPre = BytecodeDecoder.insnsWithin(joinB, insns);
            List<BytecodeReader.Insn> tSeq = BytecodeDecoder.insnsWithin(thenB, insns);
            List<BytecodeReader.Insn> eSeq = BytecodeDecoder.insnsWithin(elseB, insns);
            boolean jStore = !jPre.isEmpty() && BytecodeStatements.isStore(jPre.get(0).opcode());
            // Fase E (15/10): o then alcança o tail DIRETO e o else por
            // QUALQUER caminho curto (direto ou pela cadeia de testes —
            // medido no Warehouse.grade: o else é o teste interno que
            // converge via 21/26; o contains estrito recusava)
            boolean bothReach = thenB.succ.contains(tail)
                    && armReachesJoin(elseB.start, tail, byStart, insns, 0);
            // Fase E (15/10): o guard de REGIÃO — o pureIfElse (2 preds)
            // recusava a CADEIA ternária armazenada (medido no Warehouse.grade:
            // o join tinha 3 preds — o goto do then, o goto do then interno e
            // a queda do else interno). Os preds TODOS dentro de
            // [b.start, tail) = dentro do próprio if (um pred externo —
            // back-edge de loop posterior ou goto de código seguinte — teria
            // offset fora da região)
            boolean regionJoin = false;
            if (!pureIfElse && jStore) {
                regionJoin = true;
                for (int pr : joinB.pred) {
                    if (pr < b.start || pr >= tail) { regionJoin = false; break; }
                }
            }
            if ((pureIfElse || regionJoin) && bothReach && jStore) {
                String tVal = BytecodeStatements.stackValueStrip(tSeq, frame, cp);
                String eVal = BytecodeStatements.stackValueStrip(eSeq, frame, cp);
                // Fase E (15/10): o braço TESTE (a cadeia ternária aninhada)
                // vira if-expression via o armExpr (o mesmo builder do
                // ifElseReturn; medido no grade: o else = o teste interno)
                if (tVal == null && thenB.succ.size() == 2) {
                    tVal = BytecodeDecoderCond.armExpr(thenB, byStart.values().stream().toList(),
                            insns, cp, frame, 0);
                }
                if (eVal == null && elseB.succ.size() == 2) {
                    eVal = BytecodeDecoderCond.armExpr(elseB, byStart.values().stream().toList(),
                            insns, cp, frame, 0);
                }
                if (tVal != null && eVal != null) {
                    int slot = BytecodeStatements.storeSlot(jPre.get(0));
                    String name = BytecodeDecoderStack.slotName(slot, frame);
                    boolean first = !declared.contains(slot);
                    declared.add(slot);
                    String assign0 = (first ? "var " + name + " = " : name + " = ")
                            + "if (" + cond + ") " + tVal + " else " + eVal;
                    out.add("    " + assign0);
                    emitted.add(thenStart);
                    emitted.add(exitStart);
                    // NAO marcar tail como emitido: o struct PRECISA
                    // processa-lo (o BytecodeStatements.skipFirst consome o store) — marca-lo
                    // batia na guarda de re-entrancia e stubava o metodo
                    if (BytecodeStatements.skipFirst == null) BytecodeStatements.skipFirst = new HashSet<>();
                    BytecodeStatements.skipFirst.add(tail);
                    return struct(byStart.get(tail), insns, byStart, cp, frame,
                            out, emitted, declared, header, stops);
                }
            }
            }
        }
        if (pureIfElse) {
            List<String> hoist = StructWalker.hoistEscapingLocals(thenB, elseB, insns, frame, declared);
            if (hoist == null) {
                // Fase C (13/10): o fallback do hoist-null É o VALOR
                // PROPAGADO — os braços terminando com a store do MESMO
                // slot e valores extraíveis → `var/if name = if (cond)
                // tVal else eVal` (sem default seguro necessário: cada
                // braço tem o seu valor). Refshape estrito; senão stub.
                List<BytecodeReader.Insn> tI = BytecodeDecoder.insnsWithin(thenB, insns);
                List<BytecodeReader.Insn> eI = BytecodeDecoder.insnsWithin(elseB, insns);
                boolean tJump = !tI.isEmpty() && tI.get(tI.size() - 1).opcode() == 0xa7;
                boolean eJump = !eI.isEmpty() && eI.get(eI.size() - 1).opcode() == 0xa7;
                List<BytecodeReader.Insn> tCore = tJump ? tI.subList(0, tI.size() - 1) : tI;
                List<BytecodeReader.Insn> eCore = eJump ? eI.subList(0, eI.size() - 1) : eI;
                boolean shapeOk = !tCore.isEmpty() && !eCore.isEmpty()
                        && BytecodeStatements.isStore(tCore.get(tCore.size() - 1).opcode())
                        && BytecodeStatements.isStore(eCore.get(eCore.size() - 1).opcode());
                if (shapeOk) {
                    int tSlot = BytecodeStatements.storeSlot(tCore.get(tCore.size() - 1));
                    int eSlot = BytecodeStatements.storeSlot(eCore.get(eCore.size() - 1));
                    if (tSlot == eSlot) {
                        String tVal = BytecodeDecoder.stackValue(
                                tCore.subList(0, tCore.size() - 1), frame, cp);
                        String eVal = BytecodeDecoder.stackValue(
                                eCore.subList(0, eCore.size() - 1), frame, cp);
                        if (tVal != null && eVal != null) {
                            String name = BytecodeDecoderStack.slotName(tSlot, frame);
                            boolean first = !declared.contains(tSlot);
                            declared.add(tSlot);
                            out.add("    " + (first ? "var " + name + " = " : name + " = ")
                                    + "if (" + cond + ") " + tVal + " else " + eVal);
                            emitted.add(thenStart);
                            emitted.add(exitStart);
                            return struct(byStart.get(tail), insns, byStart, cp, frame,
                                    out, emitted, declared, header, stops);
                        }
                    }
                }
                BytecodeStatements.refuse("hoist-null"); return false;
            }
            out.addAll(hoist);
        }
        // Fase E (14/10): ELSE-IF — o braco ELSE (o alvo do branch falso do if
        // externo) e ele MESMO um teste cujo falso == o join externo (o tail
        // do then) → o braco else e um if-SEM-else. Emite
        // `if (A) { .. } else { if (B) { .. } }` e segue no join. Sem isto o
        // shape `if (a>0) r=1; else if (b>0) r=2;` stubava (o join tem 3
        // preds e o pureIfElse exige 2 — medido).
        boolean pureElseIfThen = !pureIfElse && thenB != null && tail >= 0
                && thenB.succ.equals(List.of(tail))
                && elseB != null && elseB.succ.size() == 2
                && elseB.succ.get(0) == tail
                && byStart.get(elseB.succ.get(1)) != null
                && byStart.get(elseB.succ.get(1)).succ.equals(List.of(tail))
                && byStart.get(tail) != null
                && !BytecodeReader.isLoopHeader(byStart.get(tail));
        // ELSE-IF-ELSE: o braco else e um teste cujo verdadeiro E o falso
        // convergem num join INTERNO (ambos com goto para o join externo) →
        // o braco else e um if/else completo.
        int elseIfElseInner = -1;
        if (!pureIfElse && !pureElseIfThen && thenB != null && tail >= 0
                && thenB.succ.equals(List.of(tail))
                && elseB != null && elseB.succ.size() == 2) {
            BytecodeReader.Block t2 = byStart.get(elseB.succ.get(1));
            BytecodeReader.Block e2 = byStart.get(elseB.succ.get(0));
            if (t2 != null && e2 != null && t2 != elseB && e2 != elseB
                    && t2.succ.equals(List.of(tail)) && e2.succ.equals(List.of(tail))) {
                elseIfElseInner = 1;
            }
        }
        if (pureElseIfThen || elseIfElseInner > 0) {
            String innerCond = BytecodeDecoder.blockCondition(elseB, insns, frame, cp);
            if (innerCond == null) { BytecodeStatements.refuse("elseif-cond"); return false; }
            Set<Integer> bstops = new HashSet<>(stops);
            bstops.add(tail);
            out.add("if (" + cond + ") {");
            if (!struct(thenB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("} else {");
            out.add("if (" + innerCond + ") {");
            if (!struct(byStart.get(elseB.succ.get(1)), insns, byStart, cp, frame,
                    out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            if (elseIfElseInner > 0) {
                out.add("else {");
                if (!struct(byStart.get(elseB.succ.get(0)), insns, byStart, cp, frame,
                        out, emitted, declared, header, bstops))
                    { BytecodeStatements.refuse("struct-generic"); return false; }
                out.add("}");
            }
            out.add("}");
            emitted.add(elseB.start);
            return struct(byStart.get(tail), insns, byStart, cp, frame,
                    out, emitted, declared, header, stops);
        }
        // Fase E (14/10): CADEIA else-if PROFUNDA (3+ níveis) — o braço else
        // é um teste PURO cujos braços alcançam o join (direto ou por outros
        // testes puros) → RECURSA em struct(elseB) com o join como stop: o
        // MESMO pureElseIfThen trata o nível seguinte da cadeia. Sem stores
        // no bloco (senão é `else { stmt; if }` — outro shape).
        if (!pureIfElse && !pureElseIfThen && elseIfElseInner < 0 && thenB != null
                && tail >= 0 && thenB.succ.equals(List.of(tail))
                && elseB != null && elseB.succ.size() == 2
                && !emitted.contains(elseB.start)
                && byStart.get(tail) != null
                && !BytecodeReader.isLoopHeader(byStart.get(tail))) {
            List<BytecodeReader.Insn> eblk = BytecodeDecoder.insnsWithin(elseB, insns);
            boolean pureTest = !eblk.isEmpty();
            for (BytecodeReader.Insn ein : eblk) {
                int eop = ein.opcode();
                if (eop >= 0x36 && eop <= 0x4e) { pureTest = false; break; }
            }
            if (pureTest
                    && armReachesJoin(elseB.succ.get(0), tail, byStart, insns, 0)
                    && armReachesJoin(elseB.succ.get(1), tail, byStart, insns, 0)) {
                // Fase E (15/10): o hoist PROFUNDO — o local sem init
                // (`String s;`) armazenado nos DOIS lados da cadeia escapa
                // do if e precisa da declaração default ANTES (medido no
                // Range.size: o `var` nascia no 1º braço e os demais
                // atribuíam slot não-declarado = SEM011)
                StructWalker.cpRef = cp;
                List<String> deepHoist = StructWalker.hoistEscapingLocalsDeep(insns,
                        thenB.start, tail, elseB.start, tail, frame, declared);
                if (deepHoist == null) { BytecodeStatements.refuse("deep-hoist-null"); return false; }
                out.addAll(deepHoist);
                Set<Integer> bstops = new HashSet<>(stops);
                bstops.add(tail);
                out.add("if (" + cond + ") {");
                if (!struct(thenB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                    { BytecodeStatements.refuse("struct-generic"); return false; }
                out.add("} else {");
                if (!struct(elseB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                    { BytecodeStatements.refuse("struct-generic"); return false; }
                out.add("}");
                return struct(byStart.get(tail), insns, byStart, cp, frame,
                        out, emitted, declared, header, stops);
            }
        }
        out.add("if (" + cond + ") {");
        if (pureIfThen) {
            Set<Integer> jstops = new HashSet<>(stops);
            jstops.add(exitStart);
            if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, header, jstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(join, insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        // Fase C degrau 2a (if-else LINEAR com sequela): ambos os ramos
        // terminam no MESMO join P (preds exatos {then,else}, nao-loop) —
        // cada braco para na borda de P (copia propria; dono emite P no
        // fim). SEM a borda o primeiro ramo ANDA p/ P e o segundo cai na
        // recusa 214 = stub do metodo inteiro. O trap 1 (sequela sugada
        // p/ dentro do else) NAO e possivel aqui: preds(P)=={then,else}
        // NAO contem o if — P nao e alvo de branch, e o join dos ramos.
        if (pureIfElse) {
            Set<Integer> bstops = new HashSet<>(stops);
            bstops.add(tail);
            if (!struct(thenB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("} else {");
            if (!struct(elseB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(byStart.get(tail), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, header, stops))
            { BytecodeStatements.refuse("struct-generic"); return false; }
        out.add("} else {");
        if (!struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header, stops))
            { BytecodeStatements.refuse("struct-generic"); return false; }
        out.add("}");
        return true;
    }
    if (b.succ.size() == 1) {
        List<String> stmts = BytecodeStatementsEmit.emitLinear(BytecodeStatements.blkSeq(b, insns), cp, frame, declared);
        if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
        out.addAll(stmts);
        // Fase E (16/10): o terminal puro (o areturn) — sem succ; o emitLinear
        // acima já emitiu o return. Guarda o get(0) (os corpos mais fundos do
        // callarg/binding alcançam este caminho agora).
        if (b.succ.isEmpty()) return true;
        // Fase E (16/10): o goto de VOLTA ao header do próprio loop = o
        // continue implícito — o loop walk fecha o corpo; não re-andar o
        // header (o re-andar duplicava a emissão e quebrava o else do ||
        // dentro do loop; medido no Text.initials).
        if (header >= 0 && b.succ.get(0) == header) return true;
        // Fase C (13/10): goto para a SAÍDA do loop aberto = `break`
        if (BytecodeStatements.currentLoopExit >= 0 && b.succ.get(0) == BytecodeStatements.currentLoopExit) {
            out.add("break");
            return true;
        }
        return struct(byStart.get(b.succ.get(0)), insns, byStart, cp, frame, out, emitted, declared, header, stops);
    }
    { BytecodeStatements.refuse("struct-generic"); return false; }
}
    /** Fase E (14/10): o braço alcança o join? Direto, por queda única, ou
     *  por até 2 níveis de testes puros (a cadeia else-if). Limitado. */
    private static boolean armReachesJoin(int start, int tail,
            Map<Integer, BytecodeReader.Block> byStart,
            List<BytecodeReader.Insn> insns, int depth) {
        if (start == tail) return true;
        if (depth > 2) return false;
        BytecodeReader.Block b = byStart.get(start);
        if (b == null) return false;
        if (b.succ.equals(List.of(tail))) return true;
        if (b.succ.size() == 2) {
            List<BytecodeReader.Insn> blk = BytecodeDecoder.insnsWithin(b, insns);
            boolean pure = !blk.isEmpty();
            for (BytecodeReader.Insn ein : blk) {
                int eop = ein.opcode();
                if (eop >= 0x36 && eop <= 0x4e) { pure = false; break; }
            }
            if (pure
                    && armReachesJoin(b.succ.get(0), tail, byStart, insns, depth + 1)
                    && armReachesJoin(b.succ.get(1), tail, byStart, insns, depth + 1)) return true;
        }
        return false;
    }

    /**
     * Fase E (14/10): a ÁRVORE de condição — o teste do bloco com os alvos
     * (verdadeiro/falso), compondo `||` (o salto = o verdadeiro) e `&&` (o
     * salto = o falso) recursivamente. Profundidade limitada; qualquer
     * desvio → null = stub honesto (R6).
     */
    private static String condTree(BytecodeReader.Block b, int trueTgt, int falseTgt,
            Map<Integer, BytecodeReader.Block> byStart, List<BytecodeReader.Insn> insns,
            BytecodeFrame frame, String[] cp, int depth) {
        if (depth > 4 || b == null || b.succ.size() != 2) return null;
        if (BytecodeReader.isLoopHeader(b)) return null;
        int j = b.succ.get(0);
        int f = b.succ.get(1);
        if (j == trueTgt && f == falseTgt)
            return BytecodeDecoder.blockConditionTaken(b, insns, frame, cp);
        if (j == falseTgt && f == trueTgt)
            return BytecodeDecoder.blockCondition(b, insns, frame, cp);
        if (j == trueTgt) {
            String c1 = BytecodeDecoder.blockConditionTaken(b, insns, frame, cp);
            if (c1 == null) return null;
            String rest = condTree(byStart.get(f), trueTgt, falseTgt,
                    byStart, insns, frame, cp, depth + 1);
            if (rest == null) return null;
            return c1 + " || (" + rest + ")";
        }
        if (j == falseTgt) {
            String c1 = BytecodeDecoder.blockCondition(b, insns, frame, cp);
            if (c1 == null) return null;
            String rest = condTree(byStart.get(f), trueTgt, falseTgt,
                    byStart, insns, frame, cp, depth + 1);
            if (rest == null) return null;
            return c1 + " && (" + rest + ")";
        }
        return null;
    }

}

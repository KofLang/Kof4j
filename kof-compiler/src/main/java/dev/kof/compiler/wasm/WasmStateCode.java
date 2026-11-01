package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

import static dev.kof.compiler.wasm.WasmScalarOps.*;
import static dev.kof.compiler.wasm.WasmTypeOracle.*;

import dev.kof.compiler.wasm.WasmLowering.Ctx;

/**
 * Fase F (01/11, #776 — round 85): a emissao POR ESTADO da maquina `$pc`
 * do WASI — {@code emitBlock} (o estado `if (pc == N)`), {@code emitPlain}
 * (a cadeia de ops) e {@code jumpTo} (o salto por reatribuicao de pc).
 * Comportamento identico — o MESMO codigo movido do {@code WasmLowering}
 * (oavo congelado 511 no ratchet check_500; a fatia TRY-1 precisa da
 * regiao). O satelite segue o padrao WasmCallCode/WasmBinaryEmit
 * (o split por responsabilidade, sem mudanca de semantica).
 */
final class WasmStateCode {

    private WasmStateCode() {
    }

    /**
     * Fase E (24/10, #776): valor VIVO na fronteira de bloco — um produtor
     * imediatamente antes do salto/queda deixa um valor na pilha que a maquina
     * de estados do WASI NAO carrega entre estados (cada estado e um bloco
     * `if` sem resultado). Medido no `if (b1 && b2)` de BOOLs: o valor do
     * segundo operando cruzava o salto e o modulo saia INVALIDO
     * ("expected i32 but nothing on stack"). O subset nao cobre o shape —
     * recusa honesta com o diagnostico nomeado (Q7: nunca artefato invalido).
     */
    static void emitBlock(int blockIdx, List<KofOperation> ops, int nextBlock,
                                  Ctx ctx, List<WasmInstr> out) {
        // Fase E (24/10, #776): o rastreio de largura e POR BLOCO — um valor
        // empilhado num bloco nao pode ditar a largura de um literal no bloco
        // SEGUINTE (a maquina de estados separa statements em blocos): sem o
        // reset, o `println(1)` do estado seguinte herdava lastPush=="bool" e
        // saia i32.const contra o parametro i64 do println -> MODULO INVALIDO
        // ("expected i64, found i32", o defeito medido no bool-if). Os padroes
        // que usam lastPush (literal bool do desugar, null handle, truthiness)
        // sao contiguos DENTRO do bloco.
        ctx.lastPush = null;
        ctx.lastPushWide = false;
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.pcIdx, "pc"));
        out.add(new WasmInstr.Const(0, blockIdx));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));

        // FATIA JOIN (28/10, #776): o valor que cruzou a fronteira chega pelo
        // scratch (o join do ternary e' a ENTRADA deste estado)
        WasmJoinCode.emitJoinEntry(ctx, out);

        int i = 0;
        for (; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofJump kj) {
                ctx.underPending = false;
                WasmJoinCode.crossLiveValue(ops, i, ctx, out);
                jumpTo(ctx.blockOf(kj.target()), 1, ctx, out);
                WasmJoinCode.terminateBlock(cleanTail(ops, i + 1));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            if (op instanceof KofConditionalJump cj) {
                // FATIA JOIN (28/10, #776): o ternary do desugar duplica o
                // operando (o KofDup real no rastro de caixa) e o null-check
                // compara a COPIA — a pilha no salto = [under, lhs, rhs] (a
                // profundidade 3): a DANCA mora no WasmJoinCode (o split do
                // gate 500)
                WasmJoinCode.storeUnderForCondJump(ops, i, ctx, out);
                WasmJoinCode.emitCondJump(cj, ctx, out);
                WasmJoinCode.terminateBlock(cleanTail(ops, i + 1));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            if (op instanceof KofReturn || op instanceof KofReturnVoid) {
                ctx.underPending = false;
                out.add(WasmInstr.Branch.ret());
                WasmJoinCode.terminateBlock(cleanTail(ops, i + 1));
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            emitPlain(ops, i, ctx, out);
        }
        // bloco sem terminador: continua no proximo por ordem (fallthrough do IR linear)
        ctx.underPending = false;
        WasmJoinCode.crossLiveValue(ops, ops.size(), ctx, out);
        if (nextBlock >= 0) {
            jumpTo(nextBlock, 1, ctx, out);
        } else {
            out.add(WasmInstr.Branch.ret());
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }

    /**
     * TRY-1 (01/11, #776 — round 85): o fim do bloco = limpo quando depois do
     * terminador só restam marcadores estruturais {@code KofTryEnd} (no-op —
     * o fechamento do try do desugar cai depois do ÚLTIMO salto do catch; o
     * registro já foi desempilhado). Qualquer op REAL depois de um salto
     * continua a recusa (a lei do terminateBlock).
     */
    private static boolean cleanTail(List<KofOperation> ops, int fromIdx) {
        for (int k = fromIdx; k < ops.size(); k++) {
            if (!(ops.get(k) instanceof KofTryEnd)) return false;
        }
        return true;
    }

    static void emitPlain(List<KofOperation> ops, int idx, Ctx ctx, List<WasmInstr> out) {
        KofOperation op = ops.get(idx);
        if (op instanceof KofLoadLocal ll) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.w(ll.index()), "l" + ll.index()));
            // FATIA MAP-D (28/10, #776): o tipo do LOAD erasa o nullable — o
            // rastro REAL vem do lv.type() (o slot allocation o viu)
            String lt = ctx.localNullableTrace.get(ll.index());
            ctx.lastPush = lt != null ? lt
                    : (ll.type() instanceof Type.ClassType ct && !("String".equals(ct.name())
                    && "java.lang".equals(ct.packageName()))) ? ct.name() : typeName(ll.type());
            ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                    || "double".equalsIgnoreCase(ctx.lastPush);
            // CLASS-2 (28/10, #776): o LoadLocal de classe NÃO faz mais tee
            // no clsIdx (CLASS-2c, 29/10 — o save era peso morto: a rota do
            // despacho lê o LOCAL-FONTE e os tees dos args clobberavam o
            // save do receiver entre o produtor e o call, medido em P6)
        } else if (op instanceof KofStoreLocal sl) {
            // Object/bool slot (i32 no WASI) recebe valor wide do desugar de ==
            // (literal int 0/1 p/ false/true) -> wrap; handle de record (i32) nao
            boolean objSlot = sl.type() instanceof Type.ClassType sct && "Object".equals(sct.name())
                    && "java.lang".equals(sct.packageName());
            if (objSlot && ctx.lastPushWide && !ctx.lastPush.startsWith("record")) {
                out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.w(sl.index()), "l" + sl.index()));
            // Fase E (24/10, #776): o store CONSOME o valor — limpar o rastro
            // (sem isto `var flag = true` + `var i = 0` fazia o zero do i
            // herdar "bool" e sair i32 num slot i64 -> MODULO INVALIDO).
            ctx.lastPush = null;
            ctx.lastPushWide = false;
        } else if (op instanceof KofLoadLiteral lit) {
            if (ctx.wasi && lit.value() instanceof String str) {
                int addr = ctx.internString(str);
                out.add(new WasmInstr.Const(0, addr));
                out.add(new WasmInstr.Const(0, ctx.internLen(str)));
                out.add(new WasmInstr.Call("kof.strLit")); // handle no heap (15.3c)
                ctx.lastPush = "string";
            } else if (lit.value() == null) {
                // null handle (record/Object) -> i32 0; se o operando anterior era wide
                // (bool i64 no merge de ==), o null tem de ter a mesma largura
                if (ctx.lastPushWide) {
                    out.add(new WasmInstr.Const(1, 0));
                } else {
                    out.add(new WasmInstr.Const(0, 0));
                }
                ctx.lastPush = "null";
                ctx.lastPushWide = false;
            } else if ("string".equalsIgnoreCase(typeName(lit.type()))) {
                throw new WasmUnsupportedException("literal String fora do WASI (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            } else if ("bool".equals(ctx.lastPush) && lit.value() instanceof Integer iv
                    && (iv == 0 || iv == 1) && "int".equalsIgnoreCase(typeName(lit.type()))) {
                // `!=` do desugar: EQ contra o literal 0/1 sobre um bool i32
                // (kofRecordEq) — mesma largura, senao o i64.eq nao bate
                out.add(new WasmInstr.Const(0, ((Integer) lit.value()).longValue()));
                ctx.lastPush = "bool";
            } else {
                emitLiteral(lit.value(), lit.type(), out);
                ctx.lastPush = typeName(lit.type());
                ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                        || "double".equalsIgnoreCase(ctx.lastPush);
            }
        } else if (op instanceof KofCheckCast) {
            // no-op: o receiver ja esta na pilha como handle; nao ha dispatch virtual (WASM002)
        } else if (op instanceof KofBinary bin) {
            // CLASS-2 (28/10 — split check_500): a emissão dos binários vive
            // em WasmBinaryEmit (o null de referência, o bool i32, as danças
            // MAP-MX) — comportamento identico.
            WasmBinaryEmit.emit(bin, ops, idx, ctx, out);
        } else if (op instanceof KofUnary un && ctx.wasi && "bool".equals(ctx.lastPush)) {
            out.add(new WasmInstr.Simple(0x45, "i32.eqz"));
            ctx.lastPush = "bool";
            ctx.lastPushWide = false;
        } else if (op instanceof KofUnary un) {
            emitUnary(un, out);
            ctx.lastPush = typeName(un.operandType());
            ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                    || "double".equalsIgnoreCase(ctx.lastPush);
        } else if (op instanceof KofNewObject no && ctx.classOf(no.type()) != null) {
            // GC-2 (29/10, #776): o alloc roteia pela profundidade — o site
            // limpo coleta, o aninhado usa a variante sem coleta
            WasmClassCall.lowerClassAlloc(ctx, no, out,
                    WasmOperandShape.allocName(ops, idx, ctx));
        } else if (op instanceof KofNewObject no) {
            WasmRecordCtor.lowerNewObject(ctx, no, out,
                    WasmOperandShape.allocName(ops, idx, ctx));
        } else if (op instanceof KofDup) {
            // no CONSTRUTOR de record o no-op permanece: o handle colocado pelo
            // KofNewObject permanece na base da pilha e sera consumido pelo
            // KofStoreLocal (mudar aqui regredia records). No DESUGAR DO LIST
            // o dup e' REAL — o receptor da celula duplica para os N adds
            // (tee+get no scratch i32; o wasm nao tem stack dup). A fatia
            // so' torna o dup real no rastro "list" — todo o resto fica
            // no-op como antes (blast radius zero nas shapes existentes).
            if ("list".equals(ctx.lastPush) || "map".equals(ctx.lastPush)
                    || "set".equals(ctx.lastPush)
                    // FATIA JOIN (28/10, #776): o ternary do desugar duplica o
                    // OPERANDO-CAIXA (o null-check consome a copia, o braco
                    // THEN consome o original — o no-op perdia o original e o
                    // strChar despachava sobre a pilha vazia, medido)
                    || WasmOperandShape.isBox(ctx.lastPush)) {
                out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.dupIdx, "dup"));
                out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.dupIdx, "dup"));
            }
        } else if (op instanceof KofLoadField lf && WasmClassOps.isClassFieldOp(lf.ownerType(), ctx)) {
            WasmClassOps.lowerClassLoadField(ctx, lf, out);
        } else if (op instanceof KofStoreField sf) {
            WasmClassOps.lowerStoreField(ctx, sf, out);
        } else if (op instanceof KofLoadField lf) {
            WasmRecordCtor.lowerLoadField(ctx, lf, out);
        } else if (op instanceof KofPop || op instanceof KofPop2) {
            // KofPop2 = o descarte de valor WIDE (categoria 2) — o drop e'
            // o MESMO (o wasm nao distingue largura no descarte); o desugar
            // do remove de List<Int> chega com ele (a fatia LIST-5 mediu)
            out.add(new WasmInstr.Simple(0x1a, "drop"));
        } else if (WasmArrayOps.emit(op, WasmOperandShape.allocName(ops, idx, ctx), ctx, out)) {
            // os ops de array (ARRAY-1) — WasmArrayOps (split check_500); o
            // alloc roteia pela profundidade (GC-2)
        } else if (op instanceof KofGetStatic gs
                && ctx.wasi && "System".equals(ownerSimpleName(gs.ownerType())) && "out".equals(gs.name())) {
            // receiver do println — nao empilha nada; a rota println consome so o argumento
        } else if (op instanceof KofCall kc) {
            // Fase E (27/10 — split check_500): o despacho de chamadas vive
            // em WasmCallCode (records, String intrinsics, valueOf, math,
            // caixa §284, println, fallback) — comportamento identico.
            // GC-4 (29/10, #776) — A TENTATIVA REVERTIDA (o Q7): a dança de
            // staging (o pop/push da pilha completa antes da chamada)
            // estourou a pilha no site do G4a — o sim
            // (WasmOperandShape.stackAt) SUPERCONTA a profundidade num
            // site (o sets = 6 vs a pilha = 5: o validador pegou
            // "expected i32 but nothing on stack"). O staging = SIM-
            // DEPENDENTE e o sim = imperfeito — registrado como o
            // bloqueio da paridade G-6(a) (o cap da memória); o re-trigger
            // precisa do TRACKER de pilha no tempo de EMISSÃO (o contador
            // de profundidade exato do emitPlain) antes do staging.
            WasmCallCode.emitCall(kc, ops, idx, ctx, out);
        } else if (op instanceof KofStatementIf || op instanceof KofContinueLabel) {
            // marcadores estruturais — no-op (mesma decisao medida no JvmOpEmitter)
        } else if (WasmTryCode.emit(op, ctx, out)) {
            // TRY-1 (01/11, #776 — round 85): os cinco ops try/catch/throw —
            // WasmTryCode (o satélite; o despacho vive no wrapper do frame)
        } else {
            // Phase D: the DECLARATION is the contract — see WasmOpSupport.refusal.
            throw WasmOpSupport.refusal(op, ctx.funcName);
        }
        // CLASS-2d (29/10, #776): o produtor de classe = o TEE GATED (só o
        // receiver do próximo despacho) + a TAG ARMADA no produtor
        WasmClassCall.maybeArmReceiver(ctx, ops, idx, out);
    }

    static void jumpTo(int block, int depth, Ctx ctx, List<WasmInstr> out) {
        out.add(new WasmInstr.Const(0, block));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.pcIdx, "pc"));
        out.add(new WasmInstr.Branch("dispatch", depth));
    }
}

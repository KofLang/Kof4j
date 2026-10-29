package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

import static dev.kof.compiler.wasm.WasmScalarOps.*;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.*;
import static dev.kof.compiler.wasm.WasmTypeOracle.*;

/**
 * Lowering IR -> instrucoes wasm do backend direto (TIER 15, #776).
 * Separada de `WasmBackend` (assembly do modulo) por responsabilidade
 * (gate 500, small-parts): aqui vivem dispatcher, blocos, ops e o Ctx.
 */
final class WasmLowering {

    /** O módulo em compilação (o fecho das subclasses anda nele — o CLASS-2;
     *  um módulo por emit, single-threaded). */
    static dev.kof.compiler.IRModule currentModule;
    /** O registro de block types do despacho virtual (CLASS-2; por emit). */
    static WasmBlockTypes currentBlockTypes;

    private WasmLowering() {
    }

    // ------------------------------------------------------------------
    // host WASI preview1 (unidade 15.3 fatia 1): _start + println escalares
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // lowering por funcao
    // ------------------------------------------------------------------

    static WasmFunc lowerMethod(IRMethod m, boolean wasi, boolean printIntrinsic,
            boolean argsHandle, Map<String, Integer> stringPool, List<WasmData> dataSegments,
            Map<String, ClassLayout> records, int recordDepth, DataCursor dataCursor) {
        return lowerMethod(m, wasi, printIntrinsic, argsHandle, stringPool, dataSegments,
                records, Map.of(), recordDepth, dataCursor, false, m.name());
    }

    static WasmFunc lowerMethod(IRMethod m, boolean wasi, boolean printIntrinsic,
            boolean argsHandle, Map<String, Integer> stringPool, List<WasmData> dataSegments,
            Map<String, ClassLayout> records, Map<String, ClassLayout> classes, int recordDepth,
            DataCursor dataCursor, boolean receiver, String fname) {
        List<Integer> params = new ArrayList<>();
        Map<Integer, Integer> slotMap = new LinkedHashMap<>();
        int jvmSlot = 0;
        Map<Integer, String> varNameBySlot = new HashMap<>();
        for (IRLocalVariable lv : m.localVariables()) varNameBySlot.putIfAbsent(lv.index(), lv.name());
        // FATIA MAP-D (28/10, #776): o rastro REAL dos locals nullable-double
        Map<Integer, String> localNullableTrace = new HashMap<>();
        for (IRLocalVariable lv : m.localVariables()) {
            String lvtn = typeName(lv.type());
            if ("nullabledouble".equals(lvtn) || "nullablebool".equals(lvtn)
                    || "nullablechar".equals(lvtn) || "nullableint".equals(lvtn)) {
                localNullableTrace.put(lv.index(), lvtn);
            }
        }
        // Fase E (29/10, #776 — split check_500): o plano de locais vive em
        // WasmLoweringLocals (o gate 500); o slotMap já sai preenchido.
        WasmLoweringLocals.LocalPlan plan = WasmLoweringLocals.plan(m, wasi, argsHandle,
                records, classes, recordDepth, params, slotMap, receiver);
        List<Integer> locals = plan.locals;
        final int dupIdx = plan.dupIdx;
        final int dupWideIdx = plan.dupWideIdx;
        final int pcIdx = plan.pcIdx;
        int wIdx = plan.wIdx, sIdx = plan.sIdx;
        int objIdx = plan.objIdx, vIdx = plan.vIdx, v32Idx = plan.v32Idx, vf64Idx = plan.vf64Idx;
        int eqAccIdx = plan.eqAccIdx, eqEqIdx = plan.eqEqIdx, eqCmpIdx = plan.eqCmpIdx,
                eqDblIdx = plan.eqDblIdx, eqAndIdx = plan.eqAndIdx;
        int[] nestH = plan.nestH, nestA = plan.nestA, nestB = plan.nestB, nestR = plan.nestR;
        int cmp1Idx = plan.cmp1Idx, cmp2Idx = plan.cmp2Idx, armIdx = plan.armIdx,
                joinIdx = plan.joinIdx, leftIdx = plan.leftIdx;
        int clsIdx = plan.clsIdx, tagIdx = plan.tagIdx, tagIdx2 = plan.tagIdx2;
        List<Integer> results = plan.results;
        Map<Integer, Integer> labelToBlock = plan.labelToBlock;
        List<List<KofOperation>> blocks = plan.blocks;
        Ctx ctx = new Ctx(slotMap, pcIdx, labelToBlock, fname, stringPool, dataSegments,
                records, classes, dataCursor, localNullableTrace);
        ctx.cmp1Idx = cmp1Idx;
        ctx.cmp2Idx = cmp2Idx;
        ctx.armIdx = armIdx;
        ctx.joinIdx = joinIdx;
        ctx.leftIdx = leftIdx;
        ctx.clsIdx = clsIdx;
        ctx.tagIdx = tagIdx;
        ctx.tagIdx2 = tagIdx2;
        ctx.wIdx = wIdx;
        ctx.sIdx = sIdx;
        ctx.dupIdx = dupIdx;
        ctx.dupWideIdx = dupWideIdx;
        ctx.objIdx = objIdx;
        ctx.vIdx = vIdx;
        ctx.v32Idx = v32Idx;
        ctx.vf64Idx = vf64Idx;
        ctx.eqAccIdx = eqAccIdx;
        ctx.eqEqIdx = eqEqIdx;
        ctx.eqCmpIdx = eqCmpIdx;
        ctx.eqDblIdx = eqDblIdx;
        ctx.eqAndIdx = eqAndIdx;
        ctx.nestH = nestH;
        ctx.nestA = nestA;
        ctx.nestB = nestB;
        ctx.nestR = nestR;
        ctx.wasi = wasi && printIntrinsic;
        List<KofOperation> flatOps = new ArrayList<>();
        for (IRBasicBlock bb0 : m.basicBlocks()) flatOps.addAll(bb0.operations());
        ctx.flat = flatOps;
        List<WasmInstr> body = new ArrayList<>();
        if (argsHandle) {
            body.add(new WasmInstr.Call("kof.readArgs")); // prologo _start: argv -> handles (15.3c-sliceB)
            body.add(new WasmInstr.Local(WasmInstr.Local.SET, slotMap.get(0), "args"));
        }
        body.add(new WasmInstr.Const(0, 0));
        body.add(new WasmInstr.Local(WasmInstr.Local.SET, pcIdx, "pc"));
        body.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "dispatch", 0x40));
        for (int b = 0; b < blocks.size(); b++) {
            emitBlock(b, blocks.get(b), b + 1 < blocks.size() ? b + 1 : -1, ctx, body);
        }
        body.add(new WasmInstr.Simple(0x00, "unreachable")); // pc invalido
        body.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dispatch", 0x40));
        // o dispatcher nunca cai fora do loop (todo caminho br/return); o topo
        // da funcao precisa ser polimorfico p/ o `end` com resultado i64
        body.add(new WasmInstr.Simple(0x00, "unreachable"));

        return new WasmFunc(fname, params, results, locals, body);
    }

    static final class Ctx {
        final Map<Integer, Integer> slotMap;
        final int pcIdx;
        final Map<Integer, Integer> labelToBlock;
        final String funcName;
        final Map<String, Integer> stringPool;
        final List<WasmData> dataSegments;
        final Map<String, ClassLayout> records;
        final Map<String, ClassLayout> classes;
        final dev.kof.compiler.IRModule module;
        /** CLASS-2: o registro dos block types do despacho virtual (por emit). */
        final WasmBlockTypes blockTypes;
        final DataCursor data;
        int wIdx = -1, sIdx = -1;
        int dupIdx = -1, dupWideIdx = -1;
        int objIdx = -1, vIdx = -1, v32Idx = -1, vf64Idx = -1;
        int eqAccIdx = -1, eqEqIdx = -1, eqCmpIdx = -1, eqDblIdx = -1, eqAndIdx = -1;
        int[] nestH, nestA, nestB, nestR;
        int allocDepth;
        boolean wasi;
        String lastPush;
        boolean lastPushWide;
        List<KofOperation> flat;
        // FATIA MAP-D (28/10, #776): o tipo IR do LOAD de local ERASA o
        // nullable (o KofLoadLocal.type() de um `Double? old` chega `double`
        // — medido: o println(old) despachou writeDouble sobre o handle da
        // caixa) enquanto o SLOT (o mapType do lv.type()) viu o nullable (o
        // local.set validou i32). O mapa guarda o rastro REAL (o typeName do
        // lv.type()) para o emitPlain consultar no load.
        final Map<Integer, String> localNullableTrace;
        // FATIA JOIN (28/10, #776): o ternary do desugar (o print do
        // nullable) — o operando nasce ANTES do null-check (o UNDER: ele
        // cruza para o braco THEN pelo scratch arm) e o VALOR do braco cruza
        // para o join pelo scratch join (o trace diz o que chegou)
        int cmp1Idx = -1, cmp2Idx = -1, armIdx = -1, joinIdx = -1, leftIdx = -1;
        int clsIdx;
        int tagIdx;
        /** CLASS-2e (29/10, #776): o segundo scratch de tag — o despacho
         *  ANINHADO como arg não sobrescreve a tag armada do externo. */
        int tagIdx2;
        /** A profundidade do aninhamento de despachos (o cap = 1 nível —
         *  dois níveis = a recusa honesta CLASS-2f). */
        int tagNest = 0;
        /** CLASS-2d (29/10, #776): o despacho cuja tag foi ARMADA no
         *  produtor (o índice do call no bloco; -1 = nenhuma) — a tag já
         *  está no tagIdx ANTES da avaliação dos args. */
        int tagArmedIdx = -1;
        boolean underPending;
        String underTrace;
        String joinTrace;
        // FATIA JOIN-4: a ESQUERDA do concat (o ternary de profundidade 4)
        boolean leftPending;
        String leftTrace;

        Ctx(Map<Integer, Integer> slotMap, int pcIdx,
                Map<Integer, Integer> labelToBlock, String funcName,
                Map<String, Integer> stringPool, List<WasmData> dataSegments,
                Map<String, ClassLayout> records, DataCursor data,
                Map<Integer, String> localNullableTrace) {
            this(slotMap, pcIdx, labelToBlock, funcName, stringPool, dataSegments,
                    records, Map.of(), data, localNullableTrace);
        }

        Ctx(Map<Integer, Integer> slotMap, int pcIdx,
                Map<Integer, Integer> labelToBlock, String funcName,
                Map<String, Integer> stringPool, List<WasmData> dataSegments,
                Map<String, ClassLayout> records, Map<String, ClassLayout> classes, DataCursor data,
                Map<Integer, String> localNullableTrace) {
            this.slotMap = slotMap;
            this.pcIdx = pcIdx;
            this.labelToBlock = labelToBlock;
            this.funcName = funcName;
            this.stringPool = stringPool;
            this.dataSegments = dataSegments;
            this.records = records;
            this.classes = classes;
            this.data = data;
            this.localNullableTrace = localNullableTrace;
            this.module = currentModule;
            this.blockTypes = currentBlockTypes;
        }

        /** record IR alcancado nesta fatia (15.3d); null = fora do subset. */
        ClassLayout recordOf(Type t) {
            if (records == null || !(t instanceof Type.ClassType ct)) return null;
            return records.get(ct.name());
        }
        /** CLASS-1 (28/10, #776): a CLASSE mutável IR alcancada; null = fora. */
        ClassLayout classOf(Type t) {
            if (classes == null || !(t instanceof Type.ClassType ct)) return null;
            return classes.get(ct.name());
        }
        /** CLASS-2 (28/10, #776): as classes alcançadas (o fecho das impls). */
        Map<String, ClassLayout> classes() {
            return classes;
        }
        /** O módulo IR (o fecho das subclasses anda nele). */
        dev.kof.compiler.IRModule module() {
            return module;
        }
        /** CLASS-2: o registro dos block types do despacho virtual. */
        WasmBlockTypes blockTypes() {
            return blockTypes;
        }
        int internString(String str) {
            return data.intern(str, stringPool, dataSegments);
        }

        int internLen(String str) {
            return str.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        int w(int jvmSlot) {
            Integer i = slotMap.get(jvmSlot);
            if (i == null) {
                throw new WasmUnsupportedException("slot " + jvmSlot + " desconhecido em '" + funcName
                        + "' (WASM002) — docs/wasm-wasi-plan.md (#776)");
            }
            return i;
        }

        int blockOf(LabelId id) {
            Integer b = labelToBlock.get(id.id());
            if (b == null) {
                throw new WasmUnsupportedException("salto a label inexistente em '" + funcName
                        + "' (WASM002) — docs/wasm-wasi-plan.md (#776)");
            }
            return b;
        }
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
    private static void emitBlock(int blockIdx, List<KofOperation> ops, int nextBlock,
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
                WasmJoinCode.terminateBlock(i + 1 == ops.size(), out);
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
                WasmJoinCode.terminateBlock(i + 1 == ops.size(), out);
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            if (op instanceof KofReturn || op instanceof KofReturnVoid) {
                ctx.underPending = false;
                out.add(WasmInstr.Branch.ret());
                WasmJoinCode.terminateBlock(i + 1 == ops.size(), out);
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

    private static void emitPlain(List<KofOperation> ops, int idx, Ctx ctx, List<WasmInstr> out) {
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

    /**
     * #796 — o cursor do pool de dados e de escopo de MODULO (um por
     * {@code WasmBackend.emit}), nao por funcao: antes vivia no {@code Ctx}, que e
     * criado a cada funcao, entao {@code internString} devolvia {@code DATA_BASE}
     * de novo em toda funcao e os segmentos de dados se sobrepunham (saida errada
     * em silencio). Com o cursor de modulo o pool pode passar de {@code HEAP_BASE};
     * a base do heap passa a ser derivada do fim dos dados
     * ({@code WasmBackend}), entao o pool nunca e alcancado pelo bump heap.
     */
    static final class DataCursor {
        int next = DATA_BASE;

        int intern(String str, Map<String, Integer> stringPool, List<WasmData> dataSegments) {
            Integer a = stringPool.get(str);
            if (a != null) return a;
            byte[] bs = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int addr = next;
            stringPool.put(str, addr);
            dataSegments.add(new WasmData(addr, bs));
            next += bs.length + 1; // +1 reserva o byte do '\n'
            next = (next + 3) & ~3;
            return addr;
        }
    }

}

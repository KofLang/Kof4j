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
        ctx.hasTry = plan.hasTry;
        ctx.excEntry = plan.excEntryIdx;
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
        // TRY-1 (01/11, #776 — round 85): o wrapper do try/catch — só funções
        // COM try ganham o try_table do wasm EH (o loop de despacho inteiro
        // fica protegido; frames sem try são transparentes)
        WasmTryCode.openFrame(ctx, body);
        body.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "dispatch", 0x40));
        for (int b = 0; b < blocks.size(); b++) {
            WasmStateCode.emitBlock(b, blocks.get(b), b + 1 < blocks.size() ? b + 1 : -1, ctx, body);
        }
        body.add(new WasmInstr.Simple(0x00, "unreachable")); // pc invalido
        body.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dispatch", 0x40));
        WasmTryCode.closeFrame(ctx, body);
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
        // TRY-1 (01/11, #776 — round 85): a função tem try (o wrapper do
        // WasmTryCode), o local excEntry (a contagem na entrada) e a base da
        // escrivania de exceções na memória (reservada pelo cursor)
        boolean hasTry;
        int excEntry = -1;
        int excBase = -1;
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
        // TRY-1 (01/11, #776 — round 85): o flag de módulo (o WasmBackend o
        // leva ao WasmModule para a seção tag) e a base da escrivania de
        // exceções — reservada LAZILY no primeiro try (módulos sem try NÃO
        // se deslocam: os binários ficam byte-estáveis)
        boolean usesTry;
        int excBase = -1;

        int reserveExc() {
            if (excBase < 0) {
                excBase = next;
                next += 4096; // contagem + msg + 512 registros de handler de 8 bytes
                next = (next + 3) & ~3;
            }
            return excBase;
        }

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

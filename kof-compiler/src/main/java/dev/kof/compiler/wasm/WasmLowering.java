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
        jvmSlot = WasmClassScan.buildParams(m, params, slotMap, receiver);
        List<Integer> locals = new ArrayList<>();
        int nextWasm = params.size();
        for (IRLocalVariable lv : m.localVariables()) {
            if (slotMap.containsKey(lv.index())) continue;
            slotMap.put(lv.index(), nextWasm);
            if (argsHandle && lv.index() == 0) {
                locals.add(WasmFunc.TYPE_I32); // args = handle de array no heap (15.3c-sliceB)
            } else if (lv.type() instanceof Type.ClassType ct && records != null && records.containsKey(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // record = handle no heap (15.3d)
            } else if (wasi && lv.type() instanceof Type.ClassType ct && classes != null && classes.containsKey(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // a classe = o handle i32 (CLASS-1)
            } else if (wasi && lv.type() instanceof Type.ClassType ct && "List".equals(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // List = handle i32 (celula indireta, fatia coleções 1 — 27/10)
            } else if (wasi && lv.type() instanceof Type.ClassType ct && "Map".equals(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // Map = handle i32 (celula indireta, fatia coleções 8 — 27/10)
            } else if (wasi && lv.type() instanceof Type.ClassType ct && "Set".equals(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // Set = handle i32 (celula indireta, fatia coleções 9 — 27/10)
            } else if (wasi && lv.type() instanceof Type.ArrayType) {
                // ARRAY-1 (27/10): o array = o PONTEIRO CRU i32 para
                // [count i32][slots i32×N] no bump heap — o MESMO layout da
                // 15.3c-sliceB (o args); o store/load/length ja andam nele
                locals.add(WasmFunc.TYPE_I32);
            } else {
                locals.add(mapType(typeName(lv.type()), m.name(), "local " + lv.name()));
            }
            nextWasm++;
        }
        int wIdx = -1, sIdx = -1;
        if ("_start".equals(m.name())) {
            wIdx = nextWasm;
            locals.add(WasmFunc.TYPE_I32); // scratch w
            sIdx = nextWasm + 1;
            locals.add(WasmFunc.TYPE_I32); // scratch s
            nextWasm += 2;
        }
        int objIdx = -1, vIdx = -1, v32Idx = -1, vf64Idx = -1;
        int eqAccIdx = -1, eqEqIdx = -1, eqCmpIdx = -1, eqDblIdx = -1, eqAndIdx = -1;
        int[] nestH = null, nestA = null, nestB = null, nestR = null;
        // FATIA WIDE (28/10, #776): o scratch vf64 entra SEMPRE (o store de
        // Double em array consome um slot f64 em programas SEM records — o
        // alocar-condicional deixava vf64Idx = -1 e o modulo invalidava)
        vf64Idx = nextWasm;
        locals.add(WasmFunc.TYPE_F64); // scratch vf64 (Double)
        nextWasm += 1;
        // FATIA JOIN (28/10, #776): os scratches do ternary entram SEMPRE (o
        // cond = o resultado do cmp i32; o arm = o handle do operando; o
        // join = o handle do valor do braco)
        int cmp1Idx = nextWasm;
        locals.add(WasmFunc.TYPE_I32); // scratch cmp1 (o rhs do cmp do salto)
        int cmp2Idx = nextWasm + 1;
        locals.add(WasmFunc.TYPE_I32); // scratch cmp2 (o lhs do cmp do salto)
        int armIdx = nextWasm + 2;
        locals.add(WasmFunc.TYPE_I32); // scratch arm (o operando sob o cmp)
        int joinIdx = nextWasm + 3;
        locals.add(WasmFunc.TYPE_I32); // scratch join (o valor do braco)
        int leftIdx = nextWasm + 4;
        locals.add(WasmFunc.TYPE_I32); // scratch left (a esquerda do concat)
        int clsIdx = nextWasm + 5;
        locals.add(WasmFunc.TYPE_I32); // scratch cls (o receiver do store de campo)
        nextWasm += 6;
        if (records != null && !records.isEmpty()) {
            objIdx = nextWasm;
            locals.add(WasmFunc.TYPE_I32); // scratch obj (handle)
            vIdx = nextWasm + 1;
            locals.add(WasmFunc.TYPE_I64); // scratch v (valor i64)
            v32Idx = nextWasm + 2;
            locals.add(WasmFunc.TYPE_I32); // scratch v32 (handle i32: Bool/Char/String)
            eqAccIdx = nextWasm + 3;
            locals.add(WasmFunc.TYPE_I32); // scratch eqAcc (and do fold de ==)
            eqEqIdx = nextWasm + 4;
            locals.add(WasmFunc.TYPE_I32); // scratch eqEq (handle R)
            eqCmpIdx = nextWasm + 5;
            locals.add(WasmFunc.TYPE_I32); // scratch eqCmp (cmp de campo)
            eqDblIdx = nextWasm + 6;
            locals.add(WasmFunc.TYPE_I64); // scratch eqDbl (bits de Double)
            eqAndIdx = nextWasm + 7;
            locals.add(WasmFunc.TYPE_I32); // scratch eqAnd (AND do fold; eqAcc guarda o handle L)
            nextWasm += 8;
            // slice D (15.3d inc2): um slot por nivel de aninhamento alem da raiz —
            // toString usa 1 handle por profundidade; equals usa par (L,R) por profundidade
            if (recordDepth >= 1) {
                nestH = new int[recordDepth + 1];
                nestA = new int[recordDepth + 1];
                nestB = new int[recordDepth + 1];
                nestR = new int[recordDepth + 1];
                for (int d = 1; d <= recordDepth; d++) {
                    nestH[d] = nextWasm;
                    locals.add(WasmFunc.TYPE_I32);
                    nestA[d] = nextWasm + 1;
                    locals.add(WasmFunc.TYPE_I32);
                    nestB[d] = nextWasm + 2;
                    locals.add(WasmFunc.TYPE_I32);
                    nestR[d] = nextWasm + 3;
                    locals.add(WasmFunc.TYPE_I32);
                    nextWasm += 4;
                }
            }
        }
        // Fase E (27/10, #776): o scratch do KofDup REAL (o desugar do listOf
        // duplica o handle da celula para os N adds; o wasm nao tem stack dup:
        // tee+get no scratch — a largura pelo rastro do produtor)
        final int dupIdx = nextWasm;
        locals.add(WasmFunc.TYPE_I32);
        final int dupWideIdx = nextWasm + 1;
        locals.add(WasmFunc.TYPE_I64);
        nextWasm += 2;
        final int pcIdx = nextWasm;
        locals.add(WasmFunc.TYPE_I32); // $pc
        List<Integer> results = new ArrayList<>();
        if (m.returnType() instanceof Type.PrimitiveType pr && !"void".equalsIgnoreCase(pr.name())) {
            results.add(mapType(pr.name(), m.name(), "retorno"));
        } else if (m.returnType() instanceof Type.ClassType rt && "String".equals(rt.name())
                && "java.lang".equals(rt.packageName())) {
            results.add(WasmFunc.TYPE_I32); // o retorno String = o handle (CLASS-1)
        }

        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock bb : m.basicBlocks()) ops.addAll(bb.operations());

        // blocos: prologo = bloco 0; cada KofLabel abre o proximo bloco
        Map<Integer, Integer> labelToBlock = new LinkedHashMap<>();
        List<List<KofOperation>> blocks = new ArrayList<>();
        blocks.add(new ArrayList<>());
        for (KofOperation op : ops) {
            if (op instanceof KofLabel kl) {
                if (labelToBlock.containsKey(kl.label().id())) continue; // label duplicado -> mesmo bloco
                labelToBlock.put(kl.label().id(), blocks.size());
                blocks.add(new ArrayList<>());
            } else {
                blocks.get(blocks.size() - 1).add(op);
            }
        }

        Ctx ctx = new Ctx(slotMap, pcIdx, labelToBlock, m.name(), stringPool, dataSegments,
                records, classes, dataCursor, localNullableTrace);
        ctx.cmp1Idx = cmp1Idx;
        ctx.cmp2Idx = cmp2Idx;
        ctx.armIdx = armIdx;
        ctx.joinIdx = joinIdx;
        ctx.leftIdx = leftIdx;
        ctx.clsIdx = clsIdx;
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
            if (ctx.wasi && bin.operandType() instanceof Type.ClassType) {
                // comparacao de REFERENCIA contra o literal `null` (ramo proprio
                // do desugar, nunca o kofRecordEq de conteudo): handle i32 vs 0
                String ref = operandTypeBefore(ops, idx);
                if ("null".equals(ref)) {
                    out.add(new WasmInstr.Simple(bin.op() == KofBinaryOp.EQ ? 0x46 : 0x47,
                            bin.op() == KofBinaryOp.EQ ? "i32.eq" : "i32.ne"));
                    ctx.lastPush = "bool";
                    ctx.lastPushWide = false;
                    return;
                }
            }
            if (bin.operandType() instanceof Type.ClassType) {
                throw new WasmUnsupportedException("operador binario '" + bin.op()
                        + "' com operando de classe '" + typeName(bin.operandType())
                        + "' fora do subset WASI (WASM002) — igualdade de records chega como"
                        + " kofRecordEq; identidade de Objetos nao esta no plano; docs/wasm-wasi-plan.md (#776)");
            }
            if (ctx.wasi && "bool".equals(ctx.lastPush)
                    && (bin.op() == KofBinaryOp.EQ || bin.op() == KofBinaryOp.NE)
                    && "int".equalsIgnoreCase(typeName(bin.operandType()))) {
                // `!=` do desugar sobre o bool i32 do kofRecordEq: i32, nao o
                // caminho i64 do escalar 15.2
                out.add(new WasmInstr.Simple(bin.op() == KofBinaryOp.EQ ? 0x46 : 0x47,
                        bin.op() == KofBinaryOp.EQ ? "i32.eq" : "i32.ne"));
                ctx.lastPush = "bool";
                ctx.lastPushWide = false;
                return;
            }
            String binT = typeName(bin.operandType());
            if (ctx.wasi && "double".equalsIgnoreCase(binT)) {
                // FATIA MAP-MX (28/10, #776): as TRÊS danças pelas formas dos
                // operandos (o rastro do topo diz op2; a simulação
                // WasmOperandShape diz op1). O null (o handle 0) trapa no
                // load = a paridade do NPE do unbox do JVM; a forma NÃO-coberta
                // (ex.: op1 desconhecido + op2 caixa) recusa no validate
                boolean op2Box = "mapval".equals(ctx.lastPush) || "nullabledouble".equals(ctx.lastPush);
                String[] pair = WasmOperandShape.pairAt(ops, idx, ctx);
                boolean op1Box = pair != null && WasmOperandShape.isBox(pair[0]);
                if (op2Box && op1Box) {
                    // caixa+caixa (o get+get — a lei MAP-D): [h2] load → v2 no
                    // scratch vf64; [h1] load → v1; o op
                    out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                    out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.vf64Idx, "vf64"));
                    out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                    out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.vf64Idx, "vf64"));
                } else if (op2Box) {
                    // escalar+caixa: [v1, h2] — o load 16 do op2 basta
                    out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                } else if (op1Box) {
                    // caixa+escalar: [h1, v2] — o v2 no scratch vf64, o load
                    // do h1, o v2 de volta
                    out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.vf64Idx, "vf64"));
                    out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 16));
                    out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.vf64Idx, "vf64"));
                }
            }
            emitBinaryOp(bin.op(), binT, out);
            ctx.lastPush = binT;
            ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                    || "double".equalsIgnoreCase(ctx.lastPush);
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
            WasmClassCall.lowerClassAlloc(ctx, no, out); // o alloc + o zero-fill (CLASS-1)
        } else if (op instanceof KofNewObject no) {
            WasmRecordCtor.lowerNewObject(ctx, no, out);
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
        } else if (WasmArrayOps.emit(op, ctx, out)) {
            // os ops de array (ARRAY-1) — WasmArrayOps (split check_500)
        } else if (op instanceof KofGetStatic gs
                && ctx.wasi && "System".equals(ownerSimpleName(gs.ownerType())) && "out".equals(gs.name())) {
            // receiver do println — nao empilha nada; a rota println consome so o argumento
        } else if (op instanceof KofCall kc) {
            // Fase E (27/10 — split check_500): o despacho de chamadas vive
            // em WasmCallCode (records, String intrinsics, valueOf, math,
            // caixa §284, println, fallback) — comportamento identico.
            WasmCallCode.emitCall(kc, ops, idx, ctx, out);
        } else if (op instanceof KofStatementIf || op instanceof KofContinueLabel) {
            // marcadores estruturais — no-op (mesma decisao medida no JvmOpEmitter)
        } else {
            // Phase D: the DECLARATION is the contract — see WasmOpSupport.refusal.
            throw WasmOpSupport.refusal(op, ctx.funcName);
        }
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

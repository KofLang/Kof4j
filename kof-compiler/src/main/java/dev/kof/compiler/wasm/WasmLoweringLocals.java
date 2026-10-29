package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.kof.compiler.wasm.WasmScalarOps.mapType;
import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Fase E (29/10, #776 — split check_500): a alocação de PARAMS/LOCALS/SCRATCHES
 * do lowering WASI (o plano de locais de `WasmLowering.lowerMethod`), extraída
 * verbatim (o gate 500: 633→637 ≥ 600). Responsabilidade única: o slotMap dos
 * params, os locals por largura de tipo (o handle de record/classe/coleção/
 * array, os nullable), os scratches SEMPRE alocados (vf64/cmp1..tagIdx2, o
 * obj/v/eq família de record, o nest de aninhamento, o dup/pc) e o rastro
 * nullable dos locals. Comportamento idêntico (freeze rule 3).
 */
final class WasmLoweringLocals {

    private WasmLoweringLocals() {
    }

    /** O plano completo de locais da função (o slotMap já preenchido). */
    static final class LocalPlan {
        final List<Integer> locals;
        final int jvmSlot;
        final int wIdx, sIdx;
        final int dupIdx, dupWideIdx;
        final int objIdx, vIdx, v32Idx, vf64Idx;
        final int eqAccIdx, eqEqIdx, eqCmpIdx, eqDblIdx, eqAndIdx;
        final int[] nestH, nestA, nestB, nestR;
        final int cmp1Idx, cmp2Idx, armIdx, joinIdx, leftIdx;
        final int clsIdx, tagIdx, tagIdx2;
        final int pcIdx;
        final List<Integer> results;
        final Map<Integer, Integer> labelToBlock;
        final List<List<KofOperation>> blocks;

        LocalPlan(List<Integer> locals, int jvmSlot, int wIdx, int sIdx,
                int dupIdx, int dupWideIdx, int objIdx, int vIdx, int v32Idx, int vf64Idx,
                int eqAccIdx, int eqEqIdx, int eqCmpIdx, int eqDblIdx, int eqAndIdx,
                int[] nestH, int[] nestA, int[] nestB, int[] nestR,
                int cmp1Idx, int cmp2Idx, int armIdx, int joinIdx, int leftIdx,
                int clsIdx, int tagIdx, int tagIdx2, int pcIdx,
                List<Integer> results, Map<Integer, Integer> labelToBlock,
                List<List<KofOperation>> blocks) {
            this.locals = locals;
            this.jvmSlot = jvmSlot;
            this.wIdx = wIdx;
            this.sIdx = sIdx;
            this.dupIdx = dupIdx;
            this.dupWideIdx = dupWideIdx;
            this.objIdx = objIdx;
            this.vIdx = vIdx;
            this.v32Idx = v32Idx;
            this.vf64Idx = vf64Idx;
            this.eqAccIdx = eqAccIdx;
            this.eqEqIdx = eqEqIdx;
            this.eqCmpIdx = eqCmpIdx;
            this.eqDblIdx = eqDblIdx;
            this.eqAndIdx = eqAndIdx;
            this.nestH = nestH;
            this.nestA = nestA;
            this.nestB = nestB;
            this.nestR = nestR;
            this.cmp1Idx = cmp1Idx;
            this.cmp2Idx = cmp2Idx;
            this.armIdx = armIdx;
            this.joinIdx = joinIdx;
            this.leftIdx = leftIdx;
            this.clsIdx = clsIdx;
            this.tagIdx = tagIdx;
            this.tagIdx2 = tagIdx2;
            this.pcIdx = pcIdx;
            this.results = results;
            this.labelToBlock = labelToBlock;
            this.blocks = blocks;
        }
    }

    static LocalPlan plan(IRMethod m, boolean wasi, boolean argsHandle,
            Map<String, ClassLayout> records, Map<String, ClassLayout> classes,
            int recordDepth, List<Integer> params, Map<Integer, Integer> slotMap,
            boolean receiver) {
        int jvmSlot = WasmClassScan.buildParams(m, params, slotMap, receiver, classes, records);
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
            } else if (wasi && lv.type() instanceof Type.NullableType nt
                    && nt.inner() instanceof Type.ClassType ct && classes != null && classes.containsKey(ct.name())) {
                // CLASS-2h (29/10, #776): o local nullable-CLASS = o handle
                // i32 (o null = o handle 0 — a MESMA lei do zero-fill dos
                // campos; o valor do m.get("a") de Map<String, Dog>)
                locals.add(WasmFunc.TYPE_I32);
            } else if (wasi && lv.type() instanceof Type.NullableType nt
                    && nt.inner() instanceof Type.ClassType ct && records != null && records.containsKey(ct.name())) {
                // MAP-REC (29/10, #776): o local nullable-RECORD = o handle
                // i32 (o null = o 0 — a MESMA lei; o valor do m.get("a")
                // de Map<String, Point>)
                locals.add(WasmFunc.TYPE_I32);
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
        int tagIdx = nextWasm + 6;
        locals.add(WasmFunc.TYPE_I32); // scratch tag (o despacho virtual — CLASS-2)
        int tagIdx2 = nextWasm + 7;
        locals.add(WasmFunc.TYPE_I32); // scratch tag 2 (o despacho ANINHADO como arg — CLASS-2e)
        nextWasm += 8;
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
        } else if (m.returnType() instanceof Type.ClassType) {
            // o retorno de CLASSE (String ou a alcançada — CLASS-2b) = o
            // handle i32 (a func NÃO sai void: o call site espera o handle
            // na pilha — o defeito da 56 corrigido só para o String, medido
            // em P4: `pick(): Animal` saía type 6 void e o tee do call site
            // estourava "expected i32 but nothing on stack")
            results.add(WasmFunc.TYPE_I32);
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

        return new LocalPlan(locals, jvmSlot, wIdx, sIdx, dupIdx, dupWideIdx,
                objIdx, vIdx, v32Idx, vf64Idx, eqAccIdx, eqEqIdx, eqCmpIdx, eqDblIdx,
                eqAndIdx, nestH, nestA, nestB, nestR, cmp1Idx, cmp2Idx, armIdx,
                joinIdx, leftIdx, clsIdx, tagIdx, tagIdx2, pcIdx, results,
                labelToBlock, blocks);
    }
}

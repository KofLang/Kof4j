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
            Map<String, ClassLayout> records) {
        List<Integer> params = new ArrayList<>();
        Map<Integer, Integer> slotMap = new LinkedHashMap<>();
        int jvmSlot = 0;
        Map<Integer, String> varNameBySlot = new HashMap<>();
        for (IRLocalVariable lv : m.localVariables()) varNameBySlot.putIfAbsent(lv.index(), lv.name());
        for (int i = 0; i < m.parameterTypes().size(); i++) {
            Type t = m.parameterTypes().get(i);
            params.add(mapType(typeName(t), m.name(), "parametro"));
            slotMap.put(jvmSlot, i);
            jvmSlot += isWide(t) ? 2 : 1;
        }
        List<Integer> locals = new ArrayList<>();
        int nextWasm = params.size();
        for (IRLocalVariable lv : m.localVariables()) {
            if (slotMap.containsKey(lv.index())) continue;
            slotMap.put(lv.index(), nextWasm);
            if (argsHandle && lv.index() == 0) {
                locals.add(WasmFunc.TYPE_I32); // args = handle de array no heap (15.3c-sliceB)
            } else if (lv.type() instanceof Type.ClassType ct && records != null && records.containsKey(ct.name())) {
                locals.add(WasmFunc.TYPE_I32); // record = handle no heap (15.3d)
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
        if (records != null && !records.isEmpty()) {
            objIdx = nextWasm;
            locals.add(WasmFunc.TYPE_I32); // scratch obj (handle)
            vIdx = nextWasm + 1;
            locals.add(WasmFunc.TYPE_I64); // scratch v (valor i64)
            v32Idx = nextWasm + 2;
            locals.add(WasmFunc.TYPE_I32); // scratch v32 (handle i32: Bool/Char/String)
            vf64Idx = nextWasm + 3;
            locals.add(WasmFunc.TYPE_F64); // scratch vf64 (Double)
            nextWasm += 4;
        }
        final int pcIdx = nextWasm;
        locals.add(WasmFunc.TYPE_I32); // $pc
        List<Integer> results = new ArrayList<>();
        if (m.returnType() instanceof Type.PrimitiveType pr && !"void".equalsIgnoreCase(pr.name())) {
            results.add(mapType(pr.name(), m.name(), "retorno"));
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

        Ctx ctx = new Ctx(slotMap, pcIdx, labelToBlock, m.name(), stringPool, dataSegments, records);
        ctx.wIdx = wIdx;
        ctx.sIdx = sIdx;
        ctx.objIdx = objIdx;
        ctx.vIdx = vIdx;
        ctx.v32Idx = v32Idx;
        ctx.vf64Idx = vf64Idx;
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

        return new WasmFunc(m.name(), params, results, locals, body);
    }

    static final class Ctx {
        final Map<Integer, Integer> slotMap;
        final int pcIdx;
        final Map<Integer, Integer> labelToBlock;
        final String funcName;
        final Map<String, Integer> stringPool;
        final List<WasmData> dataSegments;
        final Map<String, ClassLayout> records;
        int dataNext = DATA_BASE;
        int wIdx = -1, sIdx = -1;
        int objIdx = -1, vIdx = -1, v32Idx = -1, vf64Idx = -1;
        boolean wasi;
        String lastPush;
        boolean lastPushWide;
        List<KofOperation> flat;

        Ctx(Map<Integer, Integer> slotMap, int pcIdx,
                Map<Integer, Integer> labelToBlock, String funcName,
                Map<String, Integer> stringPool, List<WasmData> dataSegments,
                Map<String, ClassLayout> records) {
            this.slotMap = slotMap;
            this.pcIdx = pcIdx;
            this.labelToBlock = labelToBlock;
            this.funcName = funcName;
            this.stringPool = stringPool;
            this.dataSegments = dataSegments;
            this.records = records;
        }

        /** record IR alcancado nesta fatia (15.3d); null = fora do subset. */
        ClassLayout recordOf(Type t) {
            if (records == null || !(t instanceof Type.ClassType ct)) return null;
            return records.get(ct.name());
        }
        int internString(String str) {
            Integer a = stringPool.get(str);
            if (a != null) return a;
            byte[] bs = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int addr = dataNext;
            stringPool.put(str, addr);
            dataSegments.add(new WasmData(addr, bs));
            dataNext += bs.length + 1; // +1 reserva o byte do '\n'
            dataNext = (dataNext + 3) & ~3;
            return addr;
        }

        int internLen(String str) {
            return str.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        int w(int jvmSlot) {
            Integer i = slotMap.get(jvmSlot);
            if (i == null) {
                throw new WasmUnsupportedException("slot " + jvmSlot + " desconhecido em '" + funcName
                        + "' (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
            return i;
        }

        int blockOf(LabelId id) {
            Integer b = labelToBlock.get(id.id());
            if (b == null) {
                throw new WasmUnsupportedException("salto a label inexistente em '" + funcName
                        + "' (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
            return b;
        }
    }

    private static void emitBlock(int blockIdx, List<KofOperation> ops, int nextBlock,
                                  Ctx ctx, List<WasmInstr> out) {
        out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.pcIdx, "pc"));
        out.add(new WasmInstr.Const(0, blockIdx));
        out.add(new WasmInstr.Simple(0x46, "i32.eq"));
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));

        int i = 0;
        for (; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            if (op instanceof KofJump kj) {
                jumpTo(ctx.blockOf(kj.target()), 1, ctx, out);
                terminateBlock(i + 1 == ops.size(), out);
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            if (op instanceof KofConditionalJump cj) {
                if (cj.operandType() instanceof Type.ClassType jct && ctx.records != null
                        && (ctx.records.containsKey(jct.name())
                            || ("java.lang".equals(jct.packageName())
                                && "Object".equals(jct.name())))) {
                    String src = operandTypeBefore(ops, i);
                    if ("null".equals(src) || "object".equalsIgnoreCase(src)) {
                        int j = i - 1;
                        while (j >= 0 && !(ops.get(j) instanceof KofLoadLiteral nl && nl.value() == null)) j--;
                        if (j > 0) { src = operandTypeBefore(ops, j); }
                    }
                    if (src != null && ctx.records.containsKey(src)) {
                        throw new WasmUnsupportedException("igualdade '==' de record fora da 15.3d inc2 (WASM002)"
                                + " — o desugar JVM faz merge por fluxo de controle (ternario/labels) que"
                                + " cruza blocos; a unidade 15.2 lineariza por pc e precisa de um plano"
                                + " proprio (accumulator-local). docs/development/wasm-wasi-plan.md (#776)");
                    }
                }
                emitCondJump(cj, ctx, out);
                terminateBlock(i + 1 == ops.size(), out);
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            if (op instanceof KofReturn || op instanceof KofReturnVoid) {
                out.add(WasmInstr.Branch.ret());
                terminateBlock(i + 1 == ops.size(), out);
                out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
                return;
            }
            emitPlain(ops, i, ctx, out);
        }
        // bloco sem terminador: continua no proximo por ordem (fallthrough do IR linear)
        if (nextBlock >= 0) {
            jumpTo(nextBlock, 1, ctx, out);
        } else {
            out.add(WasmInstr.Branch.ret());
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }

    private static void emitCondJump(KofConditionalJump cj, Ctx ctx, List<WasmInstr> out) {
        if (cj.operandType() instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())) {
            boolean wide = ctx.lastPushWide; // null-check do desugar: i64 p/ bool, i32 p/ handle
            switch (cj.comparison()) {
                case EQ -> out.add(new WasmInstr.Simple(wide ? 0x51 : 0x46, wide ? "i64.eq" : "i32.eq"));
                case NE -> out.add(new WasmInstr.Simple(wide ? 0x52 : 0x47, wide ? "i64.ne" : "i32.ne"));
                default -> throw new WasmUnsupportedException("comparacao de handle '" + cj.comparison()
                        + "' fora do subset (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
        } else {
            switch (cj.comparison()) {
                case EQ -> out.add(cmp(cj, "eq", 0x51, 0x61));
                case NE -> out.add(cmp(cj, "ne", 0x52, 0x62));
                case LT -> out.add(cmp(cj, "lt_s", 0x53, 0x63));
                case GT -> out.add(cmp(cj, "gt_s", 0x55, 0x64));
                case LE -> out.add(cmp(cj, "le_s", 0x57, 0x65));
                case GE -> out.add(cmp(cj, "ge_s", 0x59, 0x66));
            }
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        jumpTo(ctx.blockOf(cj.trueLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        jumpTo(ctx.blockOf(cj.falseLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }

    private static void terminateBlock(boolean cleanEnd, List<WasmInstr> out) {
        // ops apos um terminador = CFG nao-linear desconhecido -> bloco so
        // termina limpo se o terminador era a ultima op
        if (!cleanEnd) {
            throw new WasmUnsupportedException("operacoes apos salto/retorno no mesmo bloco"
                    + " (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
        }
    }
    private static void emitPlain(List<KofOperation> ops, int idx, Ctx ctx, List<WasmInstr> out) {
        KofOperation op = ops.get(idx);
        if (op instanceof KofLoadLocal ll) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.w(ll.index()), "l" + ll.index()));
            ctx.lastPush = (ll.type() instanceof Type.ClassType ct && !("String".equals(ct.name())
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
                        + " docs/development/wasm-wasi-plan.md (#776)");
            } else {
                emitLiteral(lit.value(), lit.type(), out);
                ctx.lastPush = typeName(lit.type());
                ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                        || "double".equalsIgnoreCase(ctx.lastPush);
            }
        } else if (op instanceof KofCheckCast) {
            // no-op: o receiver ja esta na pilha como handle; nao ha dispatch virtual (WASM002)
        } else if (op instanceof KofBinary bin) {
            if (bin.operandType() instanceof Type.ClassType) {
                throw new WasmUnsupportedException("operador binario '" + bin.op()
                        + "' com operando de classe '" + typeName(bin.operandType())
                        + "' fora da 15.3d inc2 (WASM002) — igualdade de records chega na proxima"
                        + " fatia; docs/development/wasm-wasi-plan.md (#776)");
            }
            emitBinaryOp(bin.op(), typeName(bin.operandType()), out);
            ctx.lastPush = typeName(bin.operandType());
            ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                    || "double".equalsIgnoreCase(ctx.lastPush);
        } else if (op instanceof KofUnary un) {
            emitUnary(un, out);
            ctx.lastPush = typeName(un.operandType());
            ctx.lastPushWide = "int".equalsIgnoreCase(ctx.lastPush) || "long".equalsIgnoreCase(ctx.lastPush)
                    || "double".equalsIgnoreCase(ctx.lastPush);
        } else if (op instanceof KofNewObject no) {
            ClassLayout layout = ctx.recordOf(no.type());
            if (layout == null || ctx.objIdx < 0) {
                throw new WasmUnsupportedException("alocacao de '" + no.type()
                        + "' fora do subset de records da 15.3d (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
            // 15.3d incremento 1: slots de 8 bytes gravados como i64 — so campos
            // Int/Long; String/Bool/Char/Double/record (i32/f64 no stack) recusam
            // honesto (WASM002, SEM artefato) em vez de emitir modulo invalido (Q7).
            for (FieldLayout f : layout.fields()) {
                if (!WasmRecordOps.isWideField(f.type()) && !WasmRecordOps.isI32Field(f.type()) && !WasmRecordOps.isF64Field(f.type())) {
                    throw new WasmUnsupportedException("record '" + no.type() + "' campo '" + f.name()
                            + "' de tipo '" + typeName(f.type()) + "' fora do subset de larguras da 15.3d inc2 (WASM002)"
                            + " — campos record-aninhados chegam depois; docs/development/wasm-wasi-plan.md (#776)");
                }
            }
            // h = global 0; global 0 += totalSize; empilha h
            out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.objIdx, "obj"));
            out.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
            out.add(new WasmInstr.Const(0, layout.totalSize()));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "obj"));
            ctx.lastPush = "record";
        } else if (op instanceof KofDup) {
            // no-op no contexto de construtor de record: o handle colocado pelo
            // KofNewObject permanece na base da pilha e sera consumido pelo KofStoreLocal
        } else if (op instanceof KofLoadField lf) {
            ClassLayout layout = ctx.recordOf(lf.ownerType());
            if (layout == null) {
                throw new WasmUnsupportedException("leitura de campo '" + lf.name() + "' em tipo nao-record '"
                        + lf.ownerType() + "' (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
            // pilha tem o receiver handle (i32) -> soma offset -> load pelo tipo da largura
            out.add(new WasmInstr.Const(0, layout.fieldOffset(lf.name())));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            if (WasmRecordOps.isWideField(lf.fieldType())) {
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
            } else if (WasmRecordOps.isF64Field(lf.fieldType())) {
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, 0));
            } else {
                out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0)); // i32: Bool/Char/String-handle
            }
            ctx.lastPush = WasmRecordOps.isStringField(lf.fieldType()) ? "string" : typeName(lf.fieldType());
            ctx.lastPushWide = WasmRecordOps.isI64Field(lf.fieldType());
        } else if (op instanceof KofPop) {
            out.add(new WasmInstr.Simple(0x1a, "drop"));
        } else if (op instanceof KofArrayLength) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0)); // [arr] = count (15.3c-sliceB)
            out.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s")); // Int = i64 (D-WASM-02)
            ctx.lastPush = "int";
        } else if (op instanceof KofArrayLoad al) {
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("leitura de array fora de '_start' (WASM002) —"
                        + " docs/development/wasm-wasi-plan.md (#776)");
            }
            // [arr, idx(i64)] -> bounds-check explícito (trap deterministico, nunca lixo)
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64")); // [arr, idx(i32)]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "idx"));  // [arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.wIdx, "arr"));  // []
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));   // [count]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));  // [count, idx]
            out.add(new WasmInstr.Simple(0x4c, "i32.le_u"));     // count <= idx => fora
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
            out.add(new WasmInstr.Simple(0x00, "unreachable"));  // D-WASM-03: trap honesto
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));      // [arr, idx*4]
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*4]
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*4 + 4]
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            ctx.lastPush = typeName(al.elementType());
        } else if (op instanceof KofGetStatic gs
                && ctx.wasi && "System".equals(ownerSimpleName(gs.ownerType())) && "out".equals(gs.name())) {
            // receiver do println — nao empilha nada; a rota println consome so o argumento
        } else if (op instanceof KofCall kc) {
            if (kc.kind() == KofCallKind.FUNCTION) { }
            if (ctx.wasi && kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())
                    && ctx.recordOf(kc.ownerType()) != null) {
                // armazena os argumentos empilhados nos offsets do ClassLayout (ordem reversa)
                ClassLayout layout = ctx.recordOf(kc.ownerType());
                List<FieldLayout> fls = layout.fields();
                for (int fi = fls.size() - 1; fi >= 0; fi--) {
                    FieldLayout f = fls.get(fi);
                    int sLocal;
                    int storeOp;
                    if (WasmRecordOps.isWideField(f.type())) {
                        sLocal = ctx.vIdx; storeOp = WasmInstr.Mem.STORE64;
                    } else if (WasmRecordOps.isF64Field(f.type())) {
                        sLocal = ctx.vf64Idx; storeOp = WasmInstr.Mem.STORE_F64;
                    } else {
                        sLocal = ctx.v32Idx; storeOp = WasmInstr.Mem.STORE;
                    }
                    out.add(new WasmInstr.Local(WasmInstr.Local.SET, sLocal, "v_w"));
                    out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.objIdx, "obj"));
                    out.add(new WasmInstr.Const(0, f.offset()));
                    out.add(new WasmInstr.Simple(0x6a, "i32.add"));
                    out.add(new WasmInstr.Local(WasmInstr.Local.GET, sLocal, "v_w"));
                    out.add(new WasmInstr.Mem(storeOp, 0));
                }
                ctx.lastPush = "record:" + layout.className();
            } else if (ctx.wasi && "kof_string_concat".equals(kc.methodName())) {
                out.add(new WasmInstr.Call("kof.strConcat")); // a+b de strings (15.3c)
                ctx.lastPush = "string";
            } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                    && printlnFollows(ops, idx)) {
                // String.valueOf(X) seguido de println: o valor bruto ja esta na
                // pilha; pula o valueOf e deixa o helper escalar consumir
                String v = (!kc.parameterTypes().isEmpty()
                        && !(kc.parameterTypes().get(0) instanceof Type.UnknownType))
                        ? typeName(kc.parameterTypes().get(0))
                        : operandTypeBefore(ops, idx);
                if (v == null) {
                    // o desugar do == passa o bool por um local `Object` entre blocos;
                    // o lastPush do equals/Boolean.valueOf anterior ja diz o tipo real
                    v = ctx.lastPush;
                }
                ctx.lastPush = v;
            } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                    && "String".equals(ownerSimpleName(kc.ownerType()))) {
                // String.valueOf como VALOR (variavel/concat) — paridade JVM:
                // Int|Long -> kof.intToStr (handle `[len][digits]` no bump heap);
                // String -> identidade (o `+` do JVM reaplica valueOf no handle);
                // outros tipos recusam honesto (WASM002) sem artefato (Q7).
                if ("string".equals(ctx.lastPush)) {
                    ctx.lastPush = "string";
                } else {
                    Type p0 = kc.parameterTypes().isEmpty() ? null : kc.parameterTypes().get(0);
                    String vt = (p0 instanceof Type.PrimitiveType)
                            ? typeName(p0)
                            : operandTypeBefore(ops, idx);
                    ClassLayout vl = ctx.records == null ? null : ctx.records.get(vt);
                    if ("string".equalsIgnoreCase(vt)) {
                        ctx.lastPush = "string";
                    } else if ("int".equalsIgnoreCase(vt) || "long".equalsIgnoreCase(vt)) {
                        out.add(new WasmInstr.Call("kof.intToStr"));
                        ctx.lastPush = "string";
                    } else if (vl != null) {
                        WasmRecordCode.emitToString(vl, vt, ctx, out);
                        ctx.lastPush = "string";
                    } else {
                        throw new WasmUnsupportedException("String.valueOf('" + vt
                                + "') fora da 15.3d inc2 (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
                    }
                }
            } else if (ctx.wasi && kc.kind() == KofCallKind.STATIC
                    && "valueOf".equals(kc.methodName())
                    && "Boolean".equals(ownerSimpleName(kc.ownerType()))) {
                // Boolean.valueOf(bool) e identidade no backend WASI (bool i32); se o
                // merge do desugar deixou i64 na pilha (literal int 0/1), wrap p/ i32.
                if (ctx.lastPushWide) {
                    out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
                }
                ctx.lastPush = "bool";
                ctx.lastPushWide = false;
            } else if (ctx.wasi && "toString".equals(kc.methodName())
                    && ctx.recordOf(kc.ownerType()) != null) {
                ClassLayout sl = ctx.recordOf(kc.ownerType());
                WasmRecordCode.emitToString(sl, sl.className(), ctx, out);
                ctx.lastPush = "string";
            } else if (ctx.wasi && "equals".equals(kc.methodName())
                    && ctx.recordOf(kc.ownerType()) != null) {
                throw new WasmUnsupportedException("igualdade '==' de record fora da 15.3d inc2 (WASM002)"
                        + " — o desugar JVM faz merge por fluxo de controle (ternario/labels) que"
                        + " cruza blocos; a unidade 15.2 lineariza por pc e precisa de um plano"
                        + " proprio (accumulator-local). docs/development/wasm-wasi-plan.md (#776)");
            } else if (ctx.wasi && "println".equals(kc.methodName())) {
                String t = ctx.lastPush == null ? "?" : ctx.lastPush.toLowerCase();
                if ("?".equals(t)) {
                    String v = operandTypeBefore(ctx.flat, ctx.flat.indexOf(kc));
                    if (v != null) { t = v.toLowerCase(); ctx.lastPush = v; }
                }
                String recKey = t.startsWith("record:") ? ctx.lastPush.substring(7) : ctx.lastPush;
                ClassLayout recLayout = ctx.records == null ? null : ctx.records.get(recKey);
                if (recLayout != null) {
                    WasmRecordCode.emitToString(recLayout, recKey, ctx, out);
                    out.add(new WasmInstr.Call("kof.writeStr"));
                    ctx.lastPush = "string";
                    return;
                }
                switch (t) {
                    case "int", "long" -> out.add(new WasmInstr.Call("kof.writeInt"));
                    case "bool", "boolean" -> out.add(new WasmInstr.Call("kof.writeBool"));
                    case "char" -> out.add(new WasmInstr.Call("kof.writeChar"));
                    case "string" -> out.add(new WasmInstr.Call("kof.writeStr"));
                    default -> throw new WasmUnsupportedException("println '" + t
                            + "' fora da fatia 1 da unidade 15.3 (WASM002) — o runtime de strings/"
                            + "records/colecoes chega com o runtime (D-WASM-03/04);"
                            + " docs/development/wasm-wasi-plan.md (#776)");
                }
            } else if (kc.kind() == KofCallKind.FUNCTION || kc.kind() == KofCallKind.STATIC) {
                out.add(new WasmInstr.Call(kc.methodName()));
            } else {
                throw new WasmUnsupportedException("chamada '" + kc.methodName()
                        + "' fora do subset escalar 15.2 (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
        } else if (op instanceof KofStatementIf || op instanceof KofContinueLabel) {
            // marcadores estruturais — no-op (mesma decisao medida no JvmOpEmitter)
        } else {
            throw new WasmUnsupportedException("construcao fora do subset escalar 15.2 (WASM002): '"
                    + op.getClass().getSimpleName() + "' em '" + ctx.funcName
                    + "' — docs/development/wasm-wasi-plan.md (#776)");
        }
    }

    private static void jumpTo(int block, int depth, Ctx ctx, List<WasmInstr> out) {
        out.add(new WasmInstr.Const(0, block));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.pcIdx, "pc"));
        out.add(new WasmInstr.Branch("dispatch", depth));
    }

}

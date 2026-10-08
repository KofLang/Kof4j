package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

import static dev.kof.compiler.wasm.WasmScalarOps.*;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.*;

/**
 * Lowering IR -> instrucoes wasm do backend direto (TIER 15, #776).
 * Separada de `WasmBackend` (assembly do modulo) por responsabilidade
 * (gate 500, small-parts): aqui vivem dispatcher, blocos, ops e o Ctx.
 */
final class WasmLowering {

    private WasmLowering() {
    }

    static boolean isScalarSignature(IRMethod m) {
        for (Type t : m.parameterTypes()) {
            if (!(t instanceof Type.PrimitiveType pt) || !scalar(pt.name())) return false;
        }
        return m.returnType() instanceof Type.PrimitiveType pr
                && (scalar(pr.name()) || "void".equalsIgnoreCase(pr.name()));
    }

    private static boolean scalar(String name) {
        return switch (name.toLowerCase()) {
            case "int", "long", "double", "bool", "boolean", "char" -> true;
            default -> false;
        };
    }

    /** tipo do ultimo produtor de pilha antes da chamada (IR de pilha:
   receiver do println via KofGetStatic e ignorado). */
    static String operandTypeBefore(List<KofOperation> ops, int idx) {
        for (int i = idx - 1; i >= 0; i--) {
            KofOperation op = ops.get(i);
            if (op instanceof KofGetStatic || op instanceof KofLabel || op instanceof KofPop) {
                continue;
            }
            if (op instanceof KofLoadLiteral lit) return typeName(lit.type());
            if (op instanceof KofLoadLocal ll) return typeName(ll.type());
            if (op instanceof KofArrayLoad al) return typeName(al.elementType());
            if (op instanceof KofArrayLength) return "int";
            if (op instanceof KofBinary bin) return typeName(bin.operandType());
            if (op instanceof KofUnary un) return typeName(un.operandType());
            if (op instanceof KofCall kc) {
                if ("valueOf".equals(kc.methodName()) && !kc.parameterTypes().isEmpty()
                        && !(kc.parameterTypes().get(0) instanceof Type.UnknownType)) {
                    return typeName(kc.parameterTypes().get(0));
                }
                if ("valueOf".equals(kc.methodName())) {
                    return operandTypeBefore(ops, i); // Unknown p/ valueOf: anda p/ o valor real
                }
                return typeName(kc.returnType());
            }
            return null;
        }
        return null;
    }

    static boolean usesStringConcat(Iterable<IRMethod> ms) {
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && "kof_string_concat".equals(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static java.util.Set<String> printOperandTypes(Iterable<IRMethod> ms) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (IRMethod m : ms) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                List<KofOperation> ops = bb.operations();
                for (int i = 0; i < ops.size(); i++) {
                    if (ops.get(i) instanceof KofCall kc && "println".equals(kc.methodName())) {
                        String t = operandTypeBefore(ops, i);
                        out.add(t == null ? "?" : t.toLowerCase());
                    }
                }
            }
        }
        return out;
    }

    static boolean printableScalar(String name) {
        if ("string".equals(name.toLowerCase())) return true; // 15.3b (literal)
        return switch (name.toLowerCase()) {
            case "int", "long", "bool", "boolean", "char" -> true;
            default -> false;
        };
    }

    private static boolean usesPrintOf(List<IRMethod> entries, IRMethod main, String kind) {
        List<IRMethod> all = new ArrayList<>(entries);
        if (main != null) all.add(main);
        for (IRMethod m : all) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (var op : bb.operations()) {
                    if (op instanceof KofCall kc && ("println".equals(kc.methodName())
                            || "print".equals(kc.methodName()))
                            && !kc.parameterTypes().isEmpty()
                            && kc.parameterTypes().get(0) instanceof Type.PrimitiveType pp
                            && pp.name().equalsIgnoreCase(kind)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // host WASI preview1 (unidade 15.3 fatia 1): _start + println escalares
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // lowering por funcao
    // ------------------------------------------------------------------

    static WasmFunc lowerMethod(IRMethod m, boolean wasi, boolean printIntrinsic,
            boolean argsHandle, Map<String, Integer> stringPool, List<WasmData> dataSegments) {
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
        }
        final int pcIdx = nextWasm + ("_start".equals(m.name()) ? 2 : 0);
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

        Ctx ctx = new Ctx(slotMap, pcIdx, labelToBlock, m.name(), stringPool, dataSegments);
        ctx.wIdx = wIdx;
        ctx.sIdx = sIdx;
        ctx.wasi = wasi && printIntrinsic;
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

    private static final class Ctx {
        final Map<Integer, Integer> slotMap;
        final int pcIdx;
        final Map<Integer, Integer> labelToBlock;
        final String funcName;
        final Map<String, Integer> stringPool;
        final List<WasmData> dataSegments;
        int dataNext = DATA_BASE;
        int wIdx = -1, sIdx = -1;
        boolean wasi;
        String lastPush;

        Ctx(Map<Integer, Integer> slotMap, int pcIdx,
                Map<Integer, Integer> labelToBlock, String funcName,
                Map<String, Integer> stringPool, List<WasmData> dataSegments) {
            this.slotMap = slotMap;
            this.pcIdx = pcIdx;
            this.labelToBlock = labelToBlock;
            this.funcName = funcName;
            this.stringPool = stringPool;
            this.dataSegments = dataSegments;
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
        switch (cj.comparison()) {
            case EQ -> out.add(cmp(cj, "eq", 0x51, 0x61));
            case NE -> out.add(cmp(cj, "ne", 0x52, 0x62));
            case LT -> out.add(cmp(cj, "lt_s", 0x53, 0x63));
            case GT -> out.add(cmp(cj, "gt_s", 0x55, 0x64));
            case LE -> out.add(cmp(cj, "le_s", 0x57, 0x65));
            case GE -> out.add(cmp(cj, "ge_s", 0x59, 0x66));
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

    private static boolean printlnFollows(List<KofOperation> ops, int idx) {
        for (int i = idx + 1; i < ops.size(); i++) {
            KofOperation next = ops.get(i);
            if (next instanceof KofGetStatic || next instanceof KofLabel
                    || (next instanceof KofCall nk && "valueOf".equals(nk.methodName()))) {
                continue; // caixa intermediaria da cadeia de valueOf
            }
            return next instanceof KofCall kc && "println".equals(kc.methodName());
        }
        return false;
    }

    private static void emitPlain(List<KofOperation> ops, int idx, Ctx ctx, List<WasmInstr> out) {
        KofOperation op = ops.get(idx);
        if (op instanceof KofLoadLocal ll) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.w(ll.index()), "l" + ll.index()));
        } else if (op instanceof KofStoreLocal sl) {
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.w(sl.index()), "l" + sl.index()));
        } else if (op instanceof KofLoadLiteral lit) {
            if (ctx.wasi && lit.value() instanceof String str) {
                int addr = ctx.internString(str);
                out.add(new WasmInstr.Const(0, addr));
                out.add(new WasmInstr.Const(0, ctx.internLen(str)));
                out.add(new WasmInstr.Call("kof.strLit")); // handle no heap (15.3c)
                ctx.lastPush = "string";
            } else if ("string".equalsIgnoreCase(typeName(lit.type()))) {
                throw new WasmUnsupportedException("literal String fora do WASI (WASM002) —"
                        + " docs/development/wasm-wasi-plan.md (#776)");
            } else {
                emitLiteral(lit.value(), lit.type(), out);
                ctx.lastPush = typeName(lit.type());
            }
        } else if (op instanceof KofBinary bin) {
            emitBinaryOp(bin.op(), typeName(bin.operandType()), out);
            ctx.lastPush = typeName(bin.operandType());
        } else if (op instanceof KofUnary un) {
            emitUnary(un, out);
            ctx.lastPush = typeName(un.operandType());
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
            if (ctx.wasi && "kof_string_concat".equals(kc.methodName())) {
                out.add(new WasmInstr.Call("kof.strConcat")); // a+b de strings (15.3c)
                ctx.lastPush = "string";
            } else if (ctx.wasi && "valueOf".equals(kc.methodName())
                    && (printlnFollows(ops, idx) || "String".equals(ownerSimpleName(kc.ownerType())))) {
                // String.valueOf(X) seguido de println: o valor bruto ja esta na
                // pilha; pula o valueOf e deixa o helper escalar consumir
                ctx.lastPush = (!kc.parameterTypes().isEmpty()
                        && !(kc.parameterTypes().get(0) instanceof Type.UnknownType))
                        ? typeName(kc.parameterTypes().get(0))
                        : operandTypeBefore(ops, idx);
            } else if (ctx.wasi && "println".equals(kc.methodName())) {
                String t = ctx.lastPush == null ? "?" : ctx.lastPush.toLowerCase();
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

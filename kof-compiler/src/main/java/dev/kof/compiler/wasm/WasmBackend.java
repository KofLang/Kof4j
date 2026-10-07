package dev.kof.compiler.wasm;

import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.*;
import dev.kof.compiler.backend.Backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * WasmBackend — o backend WebAssembly direto (D-WASM-01: emissao direta, sem
 * toolchain intermediaria; D-WASM-02: Int = i64).
 *
 * Subset da unidade 15.2 (#776): funcoes top-level ESCALARES (Int/Long/
 * Double/Bool/Char por valor; arimetica, comparacao, if/while do IR via
 * labels; chamada direta entre funcoes escalares do mesmo modulo). Fora do
 * subset: recusa honesta WASM002 e NENHUM artefato (R6/Q7). Classes,
 * colecoes, strings, IO e main chegam nas unidades 15.3+ do plano.
 *
 * O CFG do IR (labels + saltos arbitrarios) vira o padrao ESTRUTURADO
 * dispatcher-medidor-de-pc: `loop $dispatch` + um `if` por bloco basico —
 * medido valido no wasmtime (pino collatz=111) antes desta implementacao.
 */
public class WasmBackend implements Backend {

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        emit(module, outputDir, false);
    }

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        List<IRMethod> entries = new ArrayList<>();
        java.util.Map<String, IRMethod> decls = new java.util.LinkedHashMap<>();
        String entryClass = null;
        for (IRClass clazz : module.classes()) {
            for (IRMethod m : clazz.methods()) {
                if ((m.accessFlags() & AccessFlags.STATIC) == 0) continue;
                if (!"Default/Main".equals(clazz.name())) continue;
                // main e a entrada com IO — host chega na unidade 15.3; seu corpo
                // NAO e alcancavel e ele nao e exportado nesta unidade
                if ("main".equals(m.name()) && "void".equalsIgnoreCase(typeName(m.returnType()))) {
                    continue;
                }
                decls.putIfAbsent(m.name(), m);
                if (isScalarSignature(m)) {
                    entries.add(m);
                    entryClass = clazz.name();
                }
            }
        }
        // fecho transitivo de chamadas a partir das entradas escalares: qualquer
        // declaracao FORA do subset que seja de fato alcancada recusa honesto
        // (WASM002) — helper injetado pela stdlib que o programa nao usa nao
        // conta; nunca sucesso parcial silencioso (Q7)
        java.util.Deque<IRMethod> work = new java.util.ArrayDeque<>(entries);
        java.util.Set<IRMethod> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!work.isEmpty()) {
            IRMethod cur = work.poll();
            if (!seen.add(cur)) continue;
            for (IRBasicBlock bb : cur.basicBlocks()) {
                for (var op : bb.operations()) {
                    if (!(op instanceof KofCall kc)) continue;
                    IRMethod t = decls.get(kc.methodName());
                    if (t == null) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' nao resolvida no subset escalar da unidade 15.2 (WASM002) —"
                                + " IO/stdlib/intrinsics recebem host nas unidades 15.3+;"
                                + " docs/development/wasm-wasi-plan.md (#776)");
                    }
                    if (!isScalarSignature(t)) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' fora do subset escalar da unidade 15.2 (WASM002) —"
                                + " classes/colecoes/IO/void estao fora; main com host chega em 15.3;"
                                + " docs/development/wasm-wasi-plan.md (#776)");
                    }
                    work.add(t);
                }
            }
        }
        if (entryClass == null) {
            throw new WasmUnsupportedException("nenhuma funcao top-level escalar para emitir (WASM002) —"
                    + " classes/colecoes/main com IO estao fora do subset da unidade 15.2 —"
                    + " docs/development/wasm-wasi-plan.md (#776)");
        }
        entries.sort(Comparator.comparing(IRMethod::name));

        List<WasmFunc> funcs = new ArrayList<>();
        for (IRMethod m : entries) funcs.add(lowerMethod(m));
        byte[] bin = new WasmModule(funcs).serialize();
        String rel = entryClass.replace('/', java.io.File.separatorChar);
        Path wasmPath = outputDir.resolve(rel + ".wasm");
        Files.createDirectories(wasmPath.getParent());
        Files.write(wasmPath, bin);
    }

    private static boolean isScalarSignature(IRMethod m) {
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

    // ------------------------------------------------------------------
    // lowering por funcao
    // ------------------------------------------------------------------

    private static WasmFunc lowerMethod(IRMethod m) {
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
            locals.add(mapType(typeName(lv.type()), m.name(), "local " + lv.name()));
            nextWasm++;
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

        Ctx ctx = new Ctx(slotMap, pcIdx, labelToBlock, m.name());
        List<WasmInstr> body = new ArrayList<>();
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

    private record Ctx(Map<Integer, Integer> slotMap, int pcIdx,
                       Map<Integer, Integer> labelToBlock, String funcName) {
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
            emitPlain(op, ctx, out);
        }
        // bloco sem terminador: continua no proximo por ordem (fallthrough do IR linear)
        if (nextBlock >= 0) {
            jumpTo(nextBlock, 1, ctx, out);
        } else {
            out.add(WasmInstr.Branch.ret());
        }
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

    private static void emitPlain(KofOperation op, Ctx ctx, List<WasmInstr> out) {
        if (op instanceof KofLoadLocal ll) {
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.w(ll.index()), "l" + ll.index()));
        } else if (op instanceof KofStoreLocal sl) {
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.w(sl.index()), "l" + sl.index()));
        } else if (op instanceof KofLoadLiteral lit) {
            emitLiteral(lit.value(), lit.type(), out);
        } else if (op instanceof KofBinary bin) {
            emitBinaryOp(bin.op(), typeName(bin.operandType()), out);
        } else if (op instanceof KofUnary un) {
            emitUnary(un, out);
        } else if (op instanceof KofPop) {
            out.add(new WasmInstr.Simple(0x1a, "drop"));
        } else if (op instanceof KofCall kc) {
            if (kc.kind() == KofCallKind.FUNCTION || kc.kind() == KofCallKind.STATIC) {
                out.add(new WasmInstr.Call(kc.methodName()));
            } else {
                throw new WasmUnsupportedException("chamada '" + kc.methodName()
                        + "' fora do subset escalar 15.2 (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
            }
        } else if (op instanceof KofStatementIf || op instanceof KofContinueLabel) {
            // marcadores estruturais — no-op (mesma decisao medida no JvmOpEmitter)
        } else {
            throw new WasmUnsupportedException("construcao fora do subset escalar 15.2 (WASM002): '"
                    + op.getClass().getSimpleName() + "' em '" + ctx.funcName()
                    + "' — docs/development/wasm-wasi-plan.md (#776)");
        }
    }

    private static void jumpTo(int block, int depth, Ctx ctx, List<WasmInstr> out) {
        out.add(new WasmInstr.Const(0, block));
        out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.pcIdx, "pc"));
        out.add(new WasmInstr.Branch("dispatch", depth));
    }

    private static void emitLiteral(Object val, Type type, List<WasmInstr> out) {
        if (val instanceof Boolean b) {
            out.add(new WasmInstr.Const(0, b ? 1 : 0));
        } else if (val instanceof Character c) {
            out.add(new WasmInstr.Const(0, c));
        } else if (val instanceof Number n) {
            if (type instanceof Type.PrimitiveType pt && "double".equalsIgnoreCase(pt.name())) {
                out.add(new WasmInstr.Const(n.doubleValue()));
            } else {
                out.add(new WasmInstr.Const(1, n.longValue()));
            }
        } else {
            throw new WasmUnsupportedException("literal fora do subset escalar (WASM002): " + val);
        }
    }

    private static void emitBinaryOp(KofBinaryOp op, String type, List<WasmInstr> out) {
        boolean dbl = "double".equalsIgnoreCase(type);
        if (dbl) {
            switch (op) {
                case ADD -> out.add(new WasmInstr.Simple(0xa0, "f64.add"));
                case SUB -> out.add(new WasmInstr.Simple(0xa1, "f64.sub"));
                case MUL -> out.add(new WasmInstr.Simple(0xa2, "f64.mul"));
                case DIV -> out.add(new WasmInstr.Simple(0xa3, "f64.div"));
                case EQ -> out.add(new WasmInstr.Simple(0x61, "f64.eq"));
                case NE -> out.add(new WasmInstr.Simple(0x62, "f64.ne"));
                case LT -> out.add(new WasmInstr.Simple(0x63, "f64.lt"));
                case GT -> out.add(new WasmInstr.Simple(0x64, "f64.gt"));
                case LE -> out.add(new WasmInstr.Simple(0x65, "f64.le"));
                case GE -> out.add(new WasmInstr.Simple(0x66, "f64.ge"));
                default -> throw new WasmUnsupportedException("op f64 '" + op + "' fora do subset 15.2 (WASM002)");
            }
            return;
        }
        switch (op) {
            case ADD -> out.add(new WasmInstr.Simple(0x7c, "i64.add"));
            case SUB -> out.add(new WasmInstr.Simple(0x7d, "i64.sub"));
            case MUL -> out.add(new WasmInstr.Simple(0x7e, "i64.mul"));
            case DIV -> out.add(new WasmInstr.Simple(0x7f, "i64.div_s"));
            case MOD -> out.add(new WasmInstr.Simple(0x81, "i64.rem_s"));
            case AND -> out.add(new WasmInstr.Simple(0x83, "i64.and"));
            case OR -> out.add(new WasmInstr.Simple(0x84, "i64.or"));
            case XOR -> out.add(new WasmInstr.Simple(0x85, "i64.xor"));
            case SHL -> out.add(new WasmInstr.Simple(0x86, "i64.shl"));
            case SHR -> out.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
            case USHR -> out.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
            case EQ -> out.add(new WasmInstr.Simple(0x51, "i64.eq"));
            case NE -> out.add(new WasmInstr.Simple(0x52, "i64.ne"));
            case LT -> out.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
            case GT -> out.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
            case LE -> out.add(new WasmInstr.Simple(0x56, "i64.le_s"));
            case GE -> out.add(new WasmInstr.Simple(0x57, "i64.ge_s"));
            default -> throw new WasmUnsupportedException("op '" + op + "' fora do subset 15.2 (WASM002)");
        }
    }

    private static void emitUnary(KofUnary un, List<WasmInstr> out) {
        String t = typeName(un.operandType());
        switch (un.op()) {
            case NEG -> {
                if ("double".equalsIgnoreCase(t)) {
                    out.add(new WasmInstr.Simple(0x9a, "f64.neg"));
                } else {
                    out.add(new WasmInstr.NegTop());
                }
            }
            case NOT -> out.add(new WasmInstr.Simple(0x45, "i32.eqz"));
            default -> throw new WasmUnsupportedException("unario '" + un.op()
                    + "' fora do subset 15.2 (WASM002) — conversions pertencem as unidades 15.3+");
        }
    }

    private static WasmInstr cmp(KofConditionalJump cj, String suffix, int i64Op, int f64Op) {
        boolean dbl = "double".equalsIgnoreCase(typeName(cj.operandType()));
        int op = dbl ? f64Op : i64Op;
        String name = (dbl ? "f64." : "i64.") + suffix.replace("_s", "");
        return new WasmInstr.Simple(op, name);
    }

    private static void emitCondJump(KofConditionalJump cj, Ctx ctx, List<WasmInstr> out) {
        switch (cj.comparison()) {
            case EQ -> out.add(cmp(cj, "eq", 0x51, 0x61));
            case NE -> out.add(cmp(cj, "ne", 0x52, 0x62));
            case LT -> out.add(cmp(cj, "lt_s", 0x53, 0x63));
            case GT -> out.add(cmp(cj, "gt_s", 0x55, 0x64));
            case LE -> out.add(cmp(cj, "le_s", 0x56, 0x65));
            case GE -> out.add(cmp(cj, "ge_s", 0x57, 0x66));
        }
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        jumpTo(ctx.blockOf(cj.trueLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        jumpTo(ctx.blockOf(cj.falseLabel()), 2, ctx, out);
        out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }

    private static String typeName(Type t) {
        if (t instanceof Type.PrimitiveType pt) return pt.name();
        return t.toString();
    }

    private static int mapType(String name, String context, String role) {
        return switch (name.toLowerCase()) {
            case "int", "long" -> WasmFunc.TYPE_I64;
            case "double" -> WasmFunc.TYPE_F64;
            case "bool", "boolean", "char" -> WasmFunc.TYPE_I32;
            default -> throw new WasmUnsupportedException(role + " '" + name + "' em '" + context
                    + "' fora do subset escalar 15.2 (WASM002) — docs/development/wasm-wasi-plan.md (#776)");
        };
    }

    private static boolean isWide(Type t) {
        return t instanceof Type.PrimitiveType pt
                && ("long".equalsIgnoreCase(pt.name()) || "double".equalsIgnoreCase(pt.name()));
    }
}

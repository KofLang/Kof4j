package dev.kof.compiler.wasm;

import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.*;
import dev.kof.compiler.backend.Backend;

import static dev.kof.compiler.wasm.WasmScalarOps.*;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.*;

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

    private final boolean wasi;

    public WasmBackend() {
        this(false);
    }

    public WasmBackend(boolean wasi) {
        this.wasi = wasi;
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        emit(module, outputDir, false);
    }

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        Map<String, Integer> stringPool = new LinkedHashMap<>();
        List<WasmData> dataSegments = new ArrayList<>();
        List<IRMethod> entries = new ArrayList<>();
        java.util.Map<String, IRMethod> decls = new java.util.LinkedHashMap<>();
        IRMethod mainDecl = null;
        String entryClass = null;
        for (IRClass clazz : module.classes()) {
            for (IRMethod m : clazz.methods()) {
                if ((m.accessFlags() & AccessFlags.STATIC) == 0) continue;
                if (!"Default/Main".equals(clazz.name())) continue;
                boolean isMain = "main".equals(m.name())
                        && "void".equalsIgnoreCase(typeName(m.returnType()));
                if (isMain) {
                    if (!wasi) continue; // wasm puro: main pede host — 15.3 (WASI)
                    mainDecl = m;
                    decls.putIfAbsent(m.name(), m);
                    entryClass = clazz.name();
                    continue;
                }
                decls.putIfAbsent(m.name(), m);
                if (isScalarSignature(m)) {
                    entries.add(m);
                    entryClass = clazz.name();
                }
            }
        }
        // println e um builtin do compilador em TODOS os backends (JsCallEmitter/
        // ExpressionPrintLowerer); nunca sombreavel por declaracao de usuario
        boolean printIntrinsic = wasi;
        // fecho transitivo de chamadas a partir das entradas escalares: qualquer
        // declaracao FORA do subset que seja de fato alcancada recusa honesto
        // (WASM002) — helper injetado pela stdlib que o programa nao usa nao
        // conta; nunca sucesso parcial silencioso (Q7)
        java.util.Deque<IRMethod> work = new java.util.ArrayDeque<>(entries);
        if (wasi && mainDecl != null) work.add(mainDecl); // corpo de main entra no fecho
        java.util.Set<IRMethod> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!work.isEmpty()) {
            IRMethod cur = work.poll();
            if (!seen.add(cur)) continue;
            for (IRBasicBlock bb : cur.basicBlocks()) {
                List<KofOperation> cops = bb.operations();
                for (int ci = 0; ci < cops.size(); ci++) {
                    var op = cops.get(ci);
                    if (!(op instanceof KofCall kc)) continue;
                    if (wasi && printIntrinsic && "valueOf".equals(kc.methodName())
                            && printlnFollows(cops, ci)) {
                        continue; // consumido pelo host de println (cadeia de valueOf)
                    }
                    boolean isPrintCall = wasi && printIntrinsic
                            && "println".equals(kc.methodName());
                    if (isPrintCall) {
                        String t = null;
                        for (IRBasicBlock scan : cur.basicBlocks()) {
                            List<KofOperation> sop = scan.operations();
                            for (int si = 0; si < sop.size(); si++) {
                                if (sop.get(si) == kc) {
                                    t = operandTypeBefore(sop, si);
                                }
                            }
                        }
                        if (t == null || !printableScalar(t)) {
                            throw new WasmUnsupportedException("println '" + t
                                    + "' fora da fatia 1 da unidade 15.3 (WASM002) — o host de "
                                    + "strings de operacoes/records/colecoes/double chega com o runtime"
                                    + " docs/development/wasm-wasi-plan.md (#776)");
                        }
                        continue;
                    }
                    IRMethod t = decls.get(kc.methodName());
                    if (t == null) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' nao resolvida no subset escalar da unidade 15.2 (WASM002) —"
                                + " IO/stdlib/intrinsics recebem host nas unidades 15.3+;"
                                + " docs/development/wasm-wasi-plan.md (#776)");
                    }
                    if (!isScalarSignature(t) && !(wasi && t == mainDecl)) {
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
        List<WasmFunc> lowered = new ArrayList<>();
        for (IRMethod m : entries) lowered.add(lowerMethod(m, wasi, printIntrinsic, stringPool, dataSegments));
        IRMethod startM = null;
        if (wasi) {
            if (mainDecl != null) {
                for (IRBasicBlock bb : mainDecl.basicBlocks()) {
                    for (var op : bb.operations()) {
                        if ((op instanceof KofLoadLocal ll && (ll.index() == 0 || ll.index() == 1))
                                || (op instanceof KofStoreLocal sl && (sl.index() == 0 || sl.index() == 1))) {
                            throw new WasmUnsupportedException("leitura/escrita de 'args' na unidade 15.3 fatia 1 (WASM002)"
                                    + " — args_get chega na fatia 15.3b; docs/development/wasm-wasi-plan.md (#776)");
                        }
                    }
                }
                var startLocals = new ArrayList<IRLocalVariable>();
                for (IRLocalVariable lv : mainDecl.localVariables()) {
                    if (lv.index() >= 2) startLocals.add(lv); // slots 0/1 = args (ArrayType)
                }
                startM = new IRMethod("_start", mainDecl.returnType(), java.util.List.<Type>of(),
                        mainDecl.accessFlags(), mainDecl.thrownExceptions(), mainDecl.basicBlocks(),
                        startLocals, KofDebugInfo.EMPTY, List.of(), List.of());
            }
            java.util.List<IRMethod> scanAll = new java.util.ArrayList<>(entries);
            if (startM != null) scanAll.add(startM);
            java.util.Set<String> printed = printOperandTypes(scanAll);
            if (printed.contains("int") || printed.contains("long")) funcs.add(kofWriteInt());
            if (printed.contains("bool") || printed.contains("boolean")) funcs.add(kofWriteBool());
            if (printed.contains("char")) funcs.add(kofWriteChar());
                if (printed.contains("string")) funcs.add(kofWriteString());
        }
        funcs.addAll(lowered);
        if (startM != null) funcs.add(lowerMethod(startM, true, printIntrinsic, stringPool, dataSegments));
        List<WasmImport> imports = wasi
                ? List.of(WasmImport.wasi("fd_write", List.of(0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f)),
                          WasmImport.wasi("proc_exit", List.of(0x7f), List.of()))
                : List.of();
        byte[] bin = new WasmModule(imports, funcs, dataSegments).serialize();
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

    private static WasmFunc lowerMethod(IRMethod m, boolean wasi, boolean printIntrinsic,
            Map<String, Integer> stringPool, List<WasmData> dataSegments) {
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
            if (ctx.wasi && lit.value() instanceof String str && printlnFollows(ops, idx)) {
                int addr = ctx.internString(str);
                out.add(new WasmInstr.Const(0, addr));
                out.add(new WasmInstr.Const(0, ctx.internLen(str)));
                ctx.lastPush = "string";
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
        } else if (op instanceof KofGetStatic gs
                && ctx.wasi && "System".equals(ownerSimpleName(gs.ownerType())) && "out".equals(gs.name())) {
            // receiver do println — nao empilha nada; a rota println consome so o argumento
        } else if (op instanceof KofCall kc) {
            if (ctx.wasi && "valueOf".equals(kc.methodName()) && printlnFollows(ops, idx)) {
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
                    case "string" -> out.add(new WasmInstr.Call("kof.writeString"));
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

package dev.kof.compiler.wasm;

import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.*;
import dev.kof.compiler.backend.Backend;

import static dev.kof.compiler.wasm.WasmScalarOps.*;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.*;
import static dev.kof.compiler.wasm.WasmLowering.*;
import static dev.kof.compiler.wasm.WasmTypeOracle.*;
import static dev.kof.compiler.wasm.WasmArgsRuntime.*;

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
        List<Integer> globals = List.of();
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
        // records da 15.3d: classe cujo superName e java/lang/Record; o handle e um
        // bloco no bump heap com o layout do ClassLayout (slots de 8 bytes)
        Map<String, ClassLayout> records = new java.util.LinkedHashMap<>();
        for (IRClass clazz : module.classes()) {
            if (!"java/lang/Record".equals(clazz.superName())) continue;
            String simple = clazz.name().substring(clazz.name().lastIndexOf('/') + 1);
            records.put(simple, ClassLayout.build(clazz));
        }
        boolean usesRecords = !records.isEmpty();
        final int recDepth = maxRecordNesting(records);
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
                    if (wasi && printIntrinsic && ("valueOf".equals(kc.methodName())
                            || "kof_string_concat".equals(kc.methodName()))) {
                        continue; // wrapper intrinseco (println / concat) tratado na emissao
                    }
                    if (wasi && usesRecords && "kofRecordEq".equals(kc.methodName())) {
                        continue; // igualdade de record: fold inline no call site (15.3d inc2 C2)
                    }
                    if (wasi && usesRecords && kc.kind() == KofCallKind.CONSTRUCTOR
                            && "<init>".equals(kc.methodName())
                            && records.containsKey(ownerSimpleName(kc.ownerType()))) {
                        continue; // construtor de record inlinado (15.3d)
                    }
                    if (wasi && usesRecords && (kc.kind() == KofCallKind.INSTANCE
                            || kc.kind() == KofCallKind.INTERFACE)
                            && records.containsKey(ownerSimpleName(kc.ownerType()))
                            && ("toString".equals(kc.methodName())
                                || "equals".equals(kc.methodName()))) {
                        continue; // toString/equals de record inlinados (15.3d inc2 fatia C)
                    }
                    boolean isPrintCall = wasi && printIntrinsic
                            && "println".equals(kc.methodName());
                    if (isPrintCall) {
                        String t = null;
                        java.util.List<KofOperation> flat = new java.util.ArrayList<>();
                        for (IRBasicBlock scan : cur.basicBlocks()) {
                            flat.addAll(scan.operations());
                        }
                        for (int si = 0; si < flat.size(); si++) {
                            if (flat.get(si) == kc) {
                                t = operandTypeBefore(flat, si);
                                break;
                            }
                        }
                        boolean recOk = usesRecords && t != null && records.containsKey(t);
                        if (t == null || (!printableScalar(t) && !recOk)) {
                            throw new WasmUnsupportedException("println '" + t
                                    + "' fora da fatia 1 da unidade 15.3 (WASM002) — o host de "
                                    + "strings de operacoes/records/colecoes/double chega com o runtime"
                                    + " docs/wasm-wasi-plan.md (#776)");
                        }
                        continue;
                    }
                    IRMethod t = decls.get(kc.methodName());
                    if (t == null) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' nao resolvida no subset escalar da unidade 15.2 (WASM002) —"
                                + " IO/stdlib/intrinsics recebem host nas unidades 15.3+;"
                                + " docs/wasm-wasi-plan.md (#776)");
                    }
                    if (!isScalarSignature(t) && !(wasi && t == mainDecl)) {
                        throw new WasmUnsupportedException("chamada '" + kc.methodName()
                                + "' fora do subset escalar da unidade 15.2 (WASM002) —"
                                + " classes/colecoes/IO/void estao fora; main com host chega em 15.3;"
                                + " docs/wasm-wasi-plan.md (#776)");
                    }
                    work.add(t);
                }
            }
        }
        if (entryClass == null) {
            throw new WasmUnsupportedException("nenhuma funcao top-level escalar para emitir (WASM002) —"
                    + " classes/colecoes/main com IO estao fora do subset da unidade 15.2 —"
                    + " docs/wasm-wasi-plan.md (#776)");
        }
        entries.sort(Comparator.comparing(IRMethod::name));

        List<WasmFunc> funcs = new ArrayList<>();
        List<WasmFunc> lowered = new ArrayList<>();
        for (IRMethod m : entries) lowered.add(lowerMethod(m, wasi, printIntrinsic, false, stringPool, dataSegments, records, recDepth));
        IRMethod startM = null;
        boolean usesArgs = false;
        if (wasi) {
            if (mainDecl != null) {
                for (IRBasicBlock bb : mainDecl.basicBlocks()) {
                    for (var op : bb.operations()) {
                        if ((op instanceof KofLoadLocal ll && ll.index() == 0)
                                || (op instanceof KofStoreLocal sl && sl.index() == 0)) {
                            usesArgs = true; // 15.3c-sliceB: args via kof.readArgs
                        }
                    }
                }
                var startLocals = new ArrayList<IRLocalVariable>();
                for (IRLocalVariable lv : mainDecl.localVariables()) {
                    if (usesArgs || lv.index() >= 1) startLocals.add(lv); // slot 0 = handle i32 (args)
                }
                startM = new IRMethod("_start", mainDecl.returnType(), java.util.List.<Type>of(),
                        mainDecl.accessFlags(), mainDecl.thrownExceptions(), mainDecl.basicBlocks(),
                        startLocals, KofDebugInfo.EMPTY, List.of(), List.of());
            }
            java.util.List<IRMethod> scanAll = new java.util.ArrayList<>(entries);
            if (startM != null) scanAll.add(startM);
            java.util.Set<String> printed = printOperandTypes(scanAll);
            if (usesStringOps(scanAll) || usesRecords) printed.add("string"); // records com String/Char/Bool (15.3d inc2 fatia C)
            if (printed.contains("int") || printed.contains("long")) funcs.add(kofWriteInt());
            if (printed.contains("bool") || printed.contains("boolean")) funcs.add(kofWriteBool());
            if (printed.contains("char")) funcs.add(kofWriteChar());
                if (printed.contains("string")) {
                    funcs.add(kofStrLit());
                    funcs.add(kofWriteStr());
                    funcs.add(kofIntToStr());
                    if (usesRecords) {
                        funcs.add(kofStrBool()); // toString de campo Bool (15.3d inc2 fatia C)
                        funcs.add(kofStrChar()); // toString de campo Char
                        funcs.add(kofStrEq());   // campo String do fold de == (15.3d inc2 fatia C2)
                    }
                    if (usesStringConcat(scanAll) || usesRecords) funcs.add(kofStrConcat());
                    globals = java.util.List.of(HEAP_BASE); // bump pointer global 0
                }
            if (usesArgs) {
                funcs.add(kofReadArgs());
                globals = java.util.List.of(HEAP_BASE); // heap p/ handles de args
            }
            if (usesRecords) {
                globals = java.util.List.of(HEAP_BASE); // heap p/ blocos de record (15.3d)
            }
        }
        funcs.addAll(lowered);
        if (startM != null) funcs.add(lowerMethod(startM, true, printIntrinsic, usesArgs, stringPool, dataSegments, records, recDepth));
        List<WasmImport> imports;
        if (!wasi) {
            imports = List.of();
        } else if (usesArgs) {
            imports = List.of(WasmImport.wasi("fd_write", List.of(0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f)),
                              WasmImport.wasi("proc_exit", List.of(0x7f), List.of()),
                              WasmImport.wasi("args_sizes_get", List.of(0x7f, 0x7f), List.of(0x7f)),
                              WasmImport.wasi("args_get", List.of(0x7f, 0x7f), List.of(0x7f)));
        } else {
            imports = List.of(WasmImport.wasi("fd_write", List.of(0x7f, 0x7f, 0x7f, 0x7f), List.of(0x7f)),
                              WasmImport.wasi("proc_exit", List.of(0x7f), List.of()));
        }
        byte[] bin = new WasmModule(imports, funcs, dataSegments, globals).serialize();
        String rel = entryClass.replace('/', java.io.File.separatorChar);
        Path wasmPath = outputDir.resolve(rel + ".wasm");
        Files.createDirectories(wasmPath.getParent());
        Files.write(wasmPath, bin);
    }

    /** Profundidade maxima de aninhamento record-em-record (slice D): os slots de
     * scratch por nivel sao alocados em funcao disso; record e valor — ciclo e
     * impossivel, mas o stack de defesa devolve 0 em vez de recursar o programa. */
    static int maxRecordNesting(Map<String, ClassLayout> records) {
        int best = 0;
        for (String name : records.keySet()) {
            best = Math.max(best, recordNesting(name, records, new java.util.HashSet<>(), 0));
        }
        return best;
    }

    private static int recordNesting(String name, Map<String, ClassLayout> records,
            java.util.Set<String> stack, int depth) {
        if (!stack.add(name)) return 0;
        ClassLayout lay = records.get(name);
        int best = depth;
        if (lay != null) {
            for (FieldLayout f : lay.fields()) {
                if (f.type() instanceof Type.ClassType ct) {
                    String inner = WasmScalarOps.typeName(ct);
                    if (records.containsKey(inner)) {
                        best = Math.max(best, 1 + recordNesting(inner, records, stack, depth + 1));
                    }
                }
            }
        }
        stack.remove(name);
        return best;
    }
}

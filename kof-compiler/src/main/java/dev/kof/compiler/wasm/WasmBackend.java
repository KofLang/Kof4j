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
        // #796: cursor do pool de dados UNICO por modulo (nao por funcao).
        WasmLowering.DataCursor dataCursor = new WasmLowering.DataCursor();
        WasmLowering.currentModule = module; // o fecho das subclasses anda nele (CLASS-2)
        WasmBlockTypes blockTypes = new WasmBlockTypes(); // os block types do despacho (CLASS-2)
        WasmLowering.currentBlockTypes = blockTypes;
        Map<String, ClassLayout> classes = WasmClassScan.buildClassLayouts(module);
        boolean usesClasses = !classes.isEmpty();

        boolean usesHeap = false;
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
        WasmClassScan.declareClassMethods(module, classes, decls);
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
        // FATIA F face 2 (28/10, #776): algum record tem campo Double — a
        // familia Schubfach + o kof.doubleToStr entram pelo MESMO gancho
        // do println/concat (o toString do record consome o doubleToStr)
        boolean recordDoubles = false;
        for (ClassLayout rl : records.values()) {
            for (FieldLayout fl : rl.fields()) {
                if (WasmRecordOps.isF64Field(fl.type())) { recordDoubles = true; break; }
            }
            if (recordDoubles) break;
        }
        final int recDepth = maxRecordNesting(records);
        // fecho transitivo de chamadas a partir das entradas escalares: qualquer
        // declaracao FORA do subset que seja de fato alcancada recusa honesto
        // (WASM002) — helper injetado pela stdlib que o programa nao usa nao
        // conta; nunca sucesso parcial silencioso (Q7)
        java.util.Set<IRMethod> seen = WasmClosureScan.scan(module, records, usesRecords, classes,
                usesClasses, decls, entries, mainDecl, wasi, printIntrinsic);
        if (entryClass == null) {
            throw new WasmUnsupportedException("nenhuma funcao top-level escalar para emitir (WASM002) —"
                    + " classes/colecoes/main com IO estao fora do subset da unidade 15.2 —"
                    + " docs/wasm-wasi-plan.md (#776)");
        }
        entries.sort(Comparator.comparing(IRMethod::name));

        List<WasmFunc> funcs = new ArrayList<>();
        List<WasmFunc> lowered = new ArrayList<>();
        for (IRMethod m : entries) lowered.add(lowerMethod(m, wasi, printIntrinsic, false, stringPool, dataSegments, records, recDepth, dataCursor));
        // CLASS-2b (29/10, #776): as funções top-level de assinatura de
        // CLASSE alcançadas pelo fecho (fora da semente escalar) = lowered
        // — o handle i32; o IdentitySet evita o re-lower de uma entry
        java.util.Set<IRMethod> entrySet = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        entrySet.addAll(entries);
        for (IRMethod m : seen) {
            if (entrySet.contains(m) || m == mainDecl) continue;
            // os MÉTODOS de classe = a rota reachedClassMethods (o receiver
            // — baixá-los como statics aqui duplicava inc/add sem
            // qualificação, medido em C2 29/10)
            if (classes.containsKey(WasmClassScan.ownerSimpleNameOf(m, module))) continue;
            if (WasmClassScan.classScalarSignature(m, classes, records)) {
                lowered.add(lowerMethod(m, wasi, printIntrinsic, false, stringPool, dataSegments,
                        records, classes, recDepth, dataCursor, false, m.name()));
            }
        }
        // CLASS-1 (28/10, #776): os métodos de instância/ctors ALCANÇADOS =
        // as funcs com o receiver (o param 0 = o this); os não-alcançados
        // não saem (a mesma regra de alcance dos statics)
        for (IRMethod m : WasmClassScan.reachedClassMethods(module, classes, seen)) {
            String simple = WasmClassScan.ownerSimpleNameOf(m, module);
            lowered.add(lowerMethod(m, wasi, printIntrinsic, false, stringPool, dataSegments,
                    records, classes, recDepth, dataCursor, true, simple + "." + m.name()));
        }
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
            // records com String/Char/Bool (15.3d inc2 fatia C) + `==` de String
            // entre variaveis, que nao carrega literal nenhum (15.3d fatia F)
            if (usesStringOps(scanAll) || usesRecords || usesStringEquals(scanAll)
                    || usesStringLength(scanAll)
                    || usesStringMethod(scanAll, "isEmpty")
                    || usesStringMethod(scanAll, "equals")
                    || usesStringMethod(scanAll, "concat")
                    || usesStringMethod(scanAll, "startsWith")
                    || usesStringMethod(scanAll, "endsWith")
                    || usesStringMethod(scanAll, "indexOf")
                    || usesStringMethod(scanAll, "lastIndexOf")
                    || usesStringMethod(scanAll, "charAt")
                    || usesStringMethod(scanAll, "substring")
                    || usesStringMethod(scanAll, "trim")
                    || usesStringMethod(scanAll, "compareTo")
                    || usesStringMethod(scanAll, "replace")
                    || usesStringMethod(scanAll, "contains")) printed.add("string");
            if (printed.contains("int") || printed.contains("long")) funcs.add(kofWriteInt());
            if (printed.contains("bool") || printed.contains("boolean")) funcs.add(kofWriteBool());
            if (printed.contains("char")) funcs.add(WasmCharOut.kofWriteChar());
            // Fase E (27/10, #776): a familia SCHUBFACH — as tabelas g/pow10 e
            // os literais especiais vao no cursor de dados (o heap comeca
            // DEPOIS, o global e' calculado no fim do emit — mesmo padrao do
            // strCase) e as 11 funcoes entram quando um double e' impresso ou
            // vai para a caixa (o kof.doubleToStr delega ao kof.schubFull).
            boolean schub = false;
            if (printed.contains("double") || WasmTypeOracle.usesDoubleValueOf(scanAll)
                    || recordDoubles
                    || (wasi && WasmBoxRuntime.usesAny(scanAll))
                    // a fatia MAP: o boxToStr (do get nullable) referencia
                    // doubleToStr/schubFull ESTATICAMENTE — a familia vem
                    || (wasi && WasmTypeOracle.usesMap(scanAll))) {
                byte[] gB = WasmSchubfachRuntime.gTable();
                int gTab = dataCursor.next;
                dataSegments.add(new WasmData(gTab, gB));
                dataCursor.next = (dataCursor.next + gB.length + 3) & ~3;
                byte[] pB = WasmSchubfachRuntime.pow10Table();
                int pTab = dataCursor.next;
                dataSegments.add(new WasmData(pTab, pB));
                dataCursor.next = (dataCursor.next + pB.length + 3) & ~3;
                String[] lits = {"0.0", "-0.0", "NaN", "Infinity", "-Infinity"};
                int[] litA = new int[lits.length];
                for (int i = 0; i < lits.length; i++) {
                    byte[] lb = lits[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    litA[i] = dataCursor.next;
                    dataSegments.add(new WasmData(litA[i], lb));
                    dataCursor.next = (dataCursor.next + lb.length + 3) & ~3;
                }
                WasmSchubfachRuntime.bindTables(gTab, pTab);
                WasmSchubfachFormat.bindLiterals(litA[0], litA[1], litA[2], litA[3], litA[4]);
                funcs.addAll(WasmSchubfachRuntime.all());
                // o ENTRY do consumidor: o doubleToStr (println/concat/caixa)
                // vem JUNTO com a familia (o dedupe por nome torna o add
                // redundante seguro nos outros blocos)
                funcs.add(WasmCharOut.kofDoubleToStr());
                schub = true;
            }
                if (printed.contains("double")) {
                    // Um programa so' com double ainda escreve pelo writeStr, entao
                    // MARCA "string" e deixa o bloco de string registrar o resto —
                    // registrar aqui tambem duplicava kof.intToStr/kof.strLit.
                    funcs.add(WasmCharOut.kofWriteDouble());
                    // Fase E (27/10): a CONSTRUCAO mora em kof.doubleToStr
                    // (o writeDouble = str + write) — vem JUNTO, com ou sem
                    // caixa (o dup SAFE: o dedupe por nome fecha).
                    funcs.add(WasmCharOut.kofDoubleToStr());
                    funcs.add(WasmStrSearch.kofStrAppendByte());
                    funcs.add(WasmStrSearch.kofStrAlloc());
                    funcs.add(WasmStrSearch.kofStrAppend());
                    printed.add("string");
                }
                if (printed.contains("string")) {
                    funcs.add(kofStrLit());
                    funcs.add(kofWriteStr());
                    funcs.add(kofIntToStr());
                    if (usesRecords || WasmTypeOracle.usesCharValueOf(scanAll)) {
                        if (usesRecords) {
                            funcs.add(kofStrBool()); // toString de campo Bool (15.3d inc2 fatia C)
                            funcs.add(kofStrEq());   // campo String do fold de == (15.3d inc2 fatia C2)
                        }
                        funcs.add(kofStrChar()); // toString de campo Char E String.valueOf(char)/concat (27/10)
                    }
                    if (usesStringConcat(scanAll) || usesRecords || usesStringMethod(scanAll, "concat")) {
                        funcs.add(kofStrConcat());
                    }
                    if (usesStringMethod(scanAll, "startsWith")) funcs.add(WasmStrInspect.kofStrAffix(false));
                    if (usesStringMethod(scanAll, "endsWith")) funcs.add(WasmStrInspect.kofStrAffix(true));
                    // Fase E (27/10, #776): o strByteOff e' DEPENDENCIA comum de
                    // indexOf/lastIndexOf/contains E de charAt/substring — com os
                    // dois grupos na mesma fonte o handle era registrado DUAS
                    // VEZES e o modulo saia com export duplicado (invalido:
                    // `s.contains(x)` + `s.substring(i)`). Os guardas de
                    // dependencia agora se acumulam numa condicao unica.
                    boolean needsByteOff = usesStringMethod(scanAll, "indexOf")
                            || usesStringMethod(scanAll, "lastIndexOf")
                            || usesStringMethod(scanAll, "contains")
                            || usesStringMethod(scanAll, "charAt")
                            || usesStringMethod(scanAll, "substring");
                    if (usesStringMethod(scanAll, "indexOf")
                            || usesStringMethod(scanAll, "lastIndexOf")
                            || usesStringMethod(scanAll, "contains")) {
                        funcs.add(WasmStrInspect.kofStrIndexOf());
                        funcs.add(WasmStrInspect.kofStrLastIndexOf());
                        funcs.add(WasmStrSearch.kofStrMatchAt());
                        funcs.add(WasmStrSearch.kofStrUnitsBefore());
                    }
                    if (needsByteOff) {
                        funcs.add(WasmStrSlice.kofStrByteOff());
                    }
                    if (usesStringMethod(scanAll, "charAt")) funcs.add(WasmStrSlice.kofStrCharAt());
                    if (usesStringMethod(scanAll, "substring")) funcs.add(WasmStrSlice.kofStrSub());
                    if (usesStringMethod(scanAll, "trim")) funcs.add(WasmStrInspect.kofStrTrim());
                    // Fase E (26/10, #776): a familia INT do math (abs/sign/
                    // min/max/clamp + os predicados) — aritmetica i64 pura,
                    // SEM host imports (o WASM tambem poderia usa-las).
                    if (wasi && WasmMathRuntime.usesAny(scanAll)) {
                        funcs.addAll(WasmMathRuntime.all());
                    }
                    // Fase E (27/10, #776): a fatia LIST<Int>/LIST<String> —
                    // new/add/size/get no bump heap (celula indireta; o add
                    // que cresce nao invalida o handle do chamador — contrato
                    // ArrayList) + as consultas (a fatia 4 compara STRINGS
                    // pelo kof.strEq — o runtime entra junto, dedupe-safe).
                    // ARRAY-1 (27/10, #776): `new Int[n]` — os ops de array
                    // sao memoria pura; so o alocador compartilhado entra
                    if (wasi && WasmTypeOracle.usesArray(scanAll)) {
                        funcs.add(WasmListRuntime.heapAlloc());
                    }
                    if (wasi && WasmTypeOracle.usesList(scanAll)) {
                        funcs.addAll(WasmListRuntime.all());
                        funcs.add(kofStrEq());
                        // LIST-REC (29/10, #776): os ELEMENTOS de RECORD — o
                        // fold compartilhado (o MESMO kof.recEq.<Rec> do
                        // SET-REC, o dedupe por nome cuida do dup) + as três
                        // buscas por conteudo
                        List<ClassLayout> listRecs =
                                WasmTypeOracle.listRecordElems(scanAll, records);
                        funcs.addAll(WasmListRecRuntime.all(listRecs, recDepth, records));
                    }                    // Fase E (27/10, #776): a fatia MAP<String, Int> — o get
                    // nullable anda na CAIXA (boxInt no hit, 0=null na falta)
                    // e a chave compara pelo strEq: os dois entram junto
                    if (wasi && WasmTypeOracle.usesMap(scanAll)) {
                        funcs.add(WasmListRuntime.heapAlloc());  // o alocador COMPARTILHADO
                        funcs.addAll(WasmMapRuntime.all());
                        funcs.addAll(WasmBoxRuntime.all());
                        funcs.add(kofStrEq());
                    }
                    // Fase E (27/10, #776): a fatia SET<Int> — o mesmo
                    // alocador compartilhado
                    if (wasi && WasmTypeOracle.usesSet(scanAll)) {
                        funcs.add(WasmListRuntime.heapAlloc());
                        funcs.addAll(WasmSetRuntime.all());
                        // SET-REC (29/10, #776): os ELEMENTOS de RECORD — o
                        // fold por record (kof.recEq.<Rec>) + os ops de scan
                        // por conteudo; o strEq entra quando o record tem
                        // campo String (o fold chama kof.strEq)
                        List<ClassLayout> setRecs =
                                WasmTypeOracle.setRecordElems(scanAll, records);
                        for (ClassLayout rl : setRecs) {
                            for (FieldLayout fl : rl.fields()) {
                                if (WasmRecordOps.isStringField(fl.type())) {
                                    funcs.add(kofStrEq());
                                    break;
                                }
                            }
                        }
                        funcs.addAll(WasmSetRecRuntime.all(setRecs, recDepth, records));
                    }
                    // MAP-REC (29/10, #776): as CHAVES de RECORD — o fold
                    // compartilhado + os quatro ops de busca por conteudo
                    // (o fold entra junto, dedupe-safe; o keys()/values() do
                    // mapa de chaves record extrai uma List<Rec> — a família
                    // da LIST-REC também entra para o rastro "list")
                    List<ClassLayout> mapRecs =
                            WasmTypeOracle.mapRecordKeys(scanAll, records);
                    if (!mapRecs.isEmpty()) {
                        funcs.addAll(WasmMapRecRuntime.all(mapRecs));
                        funcs.addAll(WasmListRecRuntime.all(mapRecs, recDepth, records));
                    }
                    // Fase E (27/10, #776): a CAIXA DE ERASURE (§284, 3º
                    // backend) — boxToStr despacha por intToStr/strBool/
                    // doubleToStr: as DEPENDENCIAS vem junto (o dedupe por
                    // nome antes da serializacao torna o add redundante seguro).
                    if (wasi && WasmBoxRuntime.usesAny(scanAll)) {
                        funcs.addAll(WasmBoxRuntime.all());
                        funcs.add(kofStrBool());
                        funcs.add(WasmCharOut.kofDoubleToStr());
                        // deps do doubleToStr (o caminho do zero negativo):
                        // strAlloc + strAppendByte + strAppend
                        funcs.add(WasmStrSearch.kofStrAlloc());
                        funcs.add(WasmStrSearch.kofStrAppendByte());
                        funcs.add(WasmStrSearch.kofStrAppend());
                    }
                    // Fase E (26/10, #776): o case fold Unicode (D-STR-UNICODE)
                    // — as tabelas BMP vao no cursor de dados (o heap comeca
                    // DEPOIS delas, o global e' calculado no fim do emit).
                    if (usesStringMethod(scanAll, "toUpperCase")
                            || usesStringMethod(scanAll, "toLowerCase")) {
                        byte[] upT = WasmStrCase.tableBytes(true);
                        byte[] loT = WasmStrCase.tableBytes(false);
                        int tabUp = dataCursor.next;
                        dataSegments.add(new WasmData(tabUp, upT));
                        dataCursor.next = (dataCursor.next + upT.length + 3) & ~3;
                        int tabLo = dataCursor.next;
                        dataSegments.add(new WasmData(tabLo, loT));
                        dataCursor.next = (dataCursor.next + loT.length + 3) & ~3;
                        funcs.add(WasmStrCase.kofStrCaseAt("kof.strCaseUp", tabUp, true));
                        funcs.add(WasmStrCase.kofStrCaseAt("kof.strCaseLo", tabLo, false));
                        if (usesStringMethod(scanAll, "toUpperCase")) funcs.add(WasmStrCase.kofStrFold("kof.strUpper", "kof.strCaseUp"));
                        if (usesStringMethod(scanAll, "toLowerCase")) funcs.add(WasmStrCase.kofStrFold("kof.strLower", "kof.strCaseLo"));
                        // o fold aloca/preenche via strAlloc/strAppendByte — sem o
                        // bloco do double eles nao foram registrados
                        if (!printed.contains("double")) {
                            funcs.add(WasmStrSearch.kofStrAppendByte());
                            funcs.add(WasmStrSearch.kofStrAlloc());
                        }
                    }
                    if (usesStringMethod(scanAll, "compareTo")) {
                        funcs.add(WasmStrSearch.kofStrCompare());
                        funcs.add(WasmStrSlice.kofStrCharAt());
                        funcs.add(WasmStrInspect.kofStrLen());
                    }
                    if (usesStringMethod(scanAll, "replace")) {
                        funcs.add(WasmStrSearch.kofStrReplace());
                        funcs.add(WasmStrSearch.kofStrAlloc());
                        funcs.add(WasmStrSearch.kofStrAppend());
                        funcs.add(WasmStrSearch.kofStrAppendByte());
                        funcs.add(WasmStrSearch.kofStrMatchAt());
                        funcs.add(WasmStrInspect.kofStrLen());
                        // FATIA R-CHAR: a forma (Char, Char) delega ao
                        // strReplaceChar (strChar por dentro) — os dois entram
                        // pelo gancho proprio (o programa pode nao ter valueOf
                        // de char nem records)
                        if (WasmTypeOracle.usesStringReplaceChar(scanAll)) {
                            funcs.add(WasmStrSearch.kofStrReplaceChar());
                            funcs.add(WasmStrSearch.kofUtf8Enc());
                        }
                    }
                    // 15.3d fatia F: `==`/`!=` de String. Antes so entrava via
                    // records (fold de campo String); agora a comparacao direta
                    // entre duas variaveis tambem precisa do helper.
                    if (!usesRecords && usesStringEquals(scanAll)) funcs.add(kofStrEq());
                    if (usesStringLength(scanAll)) funcs.add(WasmStrInspect.kofStrLen());
                    if (usesStringMethod(scanAll, "isEmpty")) funcs.add(WasmStrInspect.kofStrIsEmpty());
                    if (!usesRecords && usesStringMethod(scanAll, "equals")) funcs.add(kofStrEq());

                    usesHeap = true; // bump pointer global 0
                }
            if (usesArgs) {
                funcs.add(kofReadArgs());
                usesHeap = true; // heap p/ handles de args
            }
            if (usesRecords) {
                usesHeap = true; // heap p/ blocos de record (15.3d)
            }
            if (usesClasses) {
                usesHeap = true; // heap p/ blocos de classe (CLASS-1, 28/10)
            }
        }
        funcs.addAll(lowered);
        if (startM != null) funcs.add(lowerMethod(startM, true, printIntrinsic, usesArgs, stringPool, dataSegments, records, classes, recDepth, dataCursor, false, startM.name()));
        // #796: o heap comeca DEPOIS do ultimo byte de dados (nunca em cima do
        // pool de literais). Com o cursor de modulo o pool pode passar de
        // HEAP_BASE; `DataCursor.intern` ja recusou um literal que nao caiba, aqui
        // so deslocamos a base do heap para nao sobrepor o que coube.
        if (usesHeap) {
            globals = java.util.List.of(Math.max(HEAP_BASE, (dataCursor.next + 7) & ~7));
        }
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
        // Fase E (27/10, #776): DEDUPE por nome preservando a primeira
        // ocorrencia — os blocos de registro por metodo (indexOf vs compareTo
        // vs replace vs length) compartilham DEPENDENCIAS (strByteOff,
        // strLen, strMatchAt, strAlloc/strAppendByte) e dois blocos na mesma
        // fonte registravam a mesma funcao DUAS VEZES: export duplicado,
        // modulo invalido (`s.contains(x)` + `s.substring(i)` medido).
        java.util.LinkedHashMap<String, WasmFunc> byName = new java.util.LinkedHashMap<>();
        for (WasmFunc f : funcs) byName.putIfAbsent(f.name(), f);
        funcs = new ArrayList<>(byName.values());
        byte[] bin = new WasmModule(imports, funcs, dataSegments, globals, blockTypes).serialize();
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

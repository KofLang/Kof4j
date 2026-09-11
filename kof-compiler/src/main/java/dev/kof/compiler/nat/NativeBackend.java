package dev.kof.compiler.nat;
import dev.kof.compiler.backend.Backend;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofDebugInfo;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.NativeRuntime;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Target;
import dev.kof.compiler.Type;
import dev.kof.compiler.runtime.RuntimeDb1;
import dev.kof.compiler.runtime.RuntimeDb2;
import dev.kof.compiler.runtime.RuntimeDb3;
import dev.kof.compiler.runtime.RuntimeDb4;
import dev.kof.compiler.runtime.RuntimeDb5;
import dev.kof.compiler.runtime.RuntimeDb6;
import dev.kof.compiler.runtime.RuntimeMap;
import dev.kof.compiler.runtime.RuntimeMemory;
import dev.kof.compiler.runtime.RuntimeSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NativeBackend implements Backend {
    private NativeArchEmitter nativeArch;
    private NativeMethodEmitter nativeMethods;

    final Target target;
    final Map<LabelId, String> labelMap = new HashMap<>();
    int labelCounter = 0;
    final List<String[]> stringLiterals = new ArrayList<>();
    int stringCounter = 0;
    int inlineSeq = 0;   // labels inline (split etc.) — únicas por call site

    /** Campos estáticos: chave "owner|name" → label no .data (bug 41). */
    final java.util.LinkedHashMap<String, String> staticFieldSymbols = new java.util.LinkedHashMap<>();
    final java.util.LinkedHashMap<String, Object> staticFieldValues = new java.util.LinkedHashMap<>();
    Type lastPushedType = Type.UnknownType.UNKNOWN;
    IRClass currentClass = null;
    boolean usesDb = false;
    boolean usesHttp = false;
    boolean usesMysql = false;
    boolean usesConcurrency = false;
    final Map<String, String> functionMangleMap = new HashMap<>();
    private final Map<String, ClassLayout> layoutCache = new HashMap<>();
    Map<String, IRClass> allClassesMap = new HashMap<>();
    /** Debug info nativa (DWARF .debug_line via .file/.loc). */
    boolean debugInfo = false;
    String sourceFile = "";
    private NativeRiscvCrossEmit crossEmitInst;
    private NativeJsonSchema jsonSchemaInst;
    private NativeX86Calls x86CallsInst;

    private NativeX86Calls x86Calls() {
        if (x86CallsInst == null) x86CallsInst = new NativeX86Calls(this);
        return x86CallsInst;
    }

    private NativeJsonSchema jsonSchema() {
        if (jsonSchemaInst == null) jsonSchemaInst = new NativeJsonSchema(this);
        return jsonSchemaInst;
    }

    NativeRiscvCrossEmit crossEmit() {
        if (crossEmitInst == null) crossEmitInst = new NativeRiscvCrossEmit(this);
        return crossEmitInst;
    }

    public NativeBackend() { this(Target.NATIVE); }
    public NativeBackend(Target target) { this.target = target; nativeMethods = new NativeMethodEmitter(this); nativeArch = new NativeArchEmitter(this); }

    String resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> ".Lkof_" + (labelCounter++));
    }

    String sanitizeName(String name) {
        return name.replace("/", "_").replace(".", "_").replace("-", "_")
                .replace("<", "").replace(">", "");
    }



    String internString(String value) {
        for (String[] entry : stringLiterals) {
            if (entry[0].equals(value)) return entry[1];
        }
        String label = ".Lstr_" + (stringCounter++);
        stringLiterals.add(new String[]{value, label});
        return label;
    }

    ClassLayout getLayout(IRClass clazz) {
        return layoutCache.computeIfAbsent(clazz.name(), k ->
            ClassLayout.buildWithSuper(clazz, name -> allClassesMap.get(name)));
    }

    /** Registra um campo estático e devolve o símbolo .data (bug 41). */
    String staticSymbol(String ownerKey, String fieldName) {
        return staticSymbol(ownerKey, fieldName, null);
    }

    String staticSymbol(String ownerKey, String fieldName, Object initialValue) {
        String key = ownerKey + "|" + fieldName;
        String label = staticFieldSymbols.get(key);
        if (label == null) {
            label = "kof_static_" + sanitizeName(ownerKey) + "_" + sanitizeName(fieldName);
            staticFieldSymbols.put(key, label);
        }
        if (initialValue != null && !staticFieldValues.containsKey(key)) {
            staticFieldValues.put(key, initialValue);
        }
        return label;
    }

    /** Chave normalizada do dono (internal name) para o símbolo estático. */
    String staticKey(Type ownerType) {
        if (ownerType instanceof Type.ClassType ct) return ct.internalName();
        return ownerType.toString();
    }

    /** Coleta os campos estáticos de todas as classes E operações (bug 41). */
    void collectStaticFields() {
        for (IRClass clazz : allClassesMap.values()) {
            for (IRField field : clazz.fields()) {
                if ((field.accessFlags() & dev.kof.compiler.AccessFlags.STATIC) != 0) {
                    staticSymbol(clazz.name(), field.name(), field.initialValue());
                }
            }
            for (IRMethod method : clazz.methods()) {
                for (IRBasicBlock block : method.basicBlocks()) {
                    for (KofOperation op : block.operations()) {
                        if (op instanceof KofGetStatic gs) staticSymbol(staticKey(gs.ownerType()), gs.name());
                        else if (op instanceof KofPutStatic ps) staticSymbol(staticKey(ps.ownerType()), ps.name());
                    }
                }
            }
        }
    }

    /** Emite os slots dos campos estáticos no .data (um .quad por campo). */
    void emitStaticData(StringBuilder sb) {
        for (java.util.Map.Entry<String, String> e : staticFieldSymbols.entrySet()) {
            Object v = staticFieldValues.get(e.getKey());
            if (v instanceof String s) {
                // strings estáticas são OBJETOS Kof (header+length+chars), não
                // `.asciz` — o kof_print_string lê length@16 e chars@24.
                String objLabel = e.getValue() + "_obj";
                emitStaticStringObject(sb, objLabel, s);
                sb.append(e.getValue()).append(": .quad ").append(objLabel).append("\n");
            } else {
                sb.append(e.getValue()).append(": .quad ").append(staticInitialText(v)).append("\n");
            }
        }
    }

    private void emitStaticStringObject(StringBuilder sb, String label, String s) {
        String bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "";
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\t", "\\t");
        sb.append(label).append(":\n");
        sb.append("    .long 1\n");
        sb.append("    .long 0\n");
        sb.append("    .quad 0\n");
        sb.append("    .long ").append(bytes).append("\n");
        sb.append("    .long 0\n");
        sb.append("    .ascii \"").append(escaped).append("\"\n");
        sb.append("    .byte 0\n");
        sb.append("    .balign 8\n");
    }

    private String staticInitialText(Object v) {
        if (v == null) return "0";
        if (v instanceof Boolean b) return b ? "1" : "0";
        if (v instanceof Integer i) return Integer.toString(i);
        if (v instanceof Long l) return Long.toString(l);
        if (v instanceof Double d) return "0x" + Long.toHexString(Double.doubleToLongBits(d));
        if (v instanceof Float f) return "0x" + Long.toHexString(Double.doubleToLongBits(f.doubleValue()));
        return "0";
    }

    ClassLayout getLayoutForType(Type type) {
        if (type instanceof Type.ClassType ct) {
            String name = ct.name();
            for (IRClass clazz : allClassesMap.values()) {
                if (clazz.name().equals(name) || clazz.name().endsWith("/" + name) || name.endsWith("/" + clazz.name())) {
                    return getLayout(clazz);
                }
            }
        }
        return null;
    }

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        // DWARF .debug_line nativo (fase 5 do debugger): .file/.loc gerados a
        // partir do KofDebugInfo (mesma fonte das line tables do JVM).
        this.debugInfo = debugInfo;
        this.sourceFile = (module.sourceName() != null && !module.sourceName().isBlank())
                ? module.sourceName() : "Main.kf";
        emit(module, outputDir);
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        if (target == Target.NATIVE_RISCV64) {
            emitRiscv(module, outputDir);
            return;
        }
        if (target == Target.NATIVE_AARCH64) {
            emitAarch64(module, outputDir);
            return;
        }
        if (module.classes().isEmpty()) return;
        labelCounter = 0;
        labelMap.clear();
        stringLiterals.clear();
        stringCounter = 0;
        inlineSeq = 0;
        functionMangleMap.clear();
        layoutCache.clear();
        allClassesMap.clear();
        for (IRClass clazz : module.classes()) {
            allClassesMap.put(clazz.name(), clazz);
        }
        StringBuilder sb = new StringBuilder();
        if (debugInfo) {
            sb.append(".file 1 \"").append(sourceFile).append("\"\n");
        }
        sb.append(".section .data\n");
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            getLayout(clazz);
            collectStrings(clazz);
        }
        jsonSchema().collectJsonSchemas();
        collectStaticFields();
        emitStringData(sb);
        emitStaticData(sb);
        jsonSchema().emitJsonSchemaData(sb);
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            emitMethodTable(sb, clazz);
        }
        sb.append("\n.section .text\n");
        sb.append(NativeRuntime.generateRuntimeAssembly());
        RuntimeMemory.emitInitObject(sb);
        // kof.db on the native target: link the DB client library directly
        // (no JDBC driver) — the same direct-.so pattern as kof-webview.
        for (IRClass clazz : module.classes()) {
            for (IRMethod method : clazz.methods()) {
                for (IRBasicBlock block : method.basicBlocks()) {
                    List<KofOperation> ops = block.operations();
                    for (int i = 0; i < ops.size(); i++) {
                        KofOperation op = ops.get(i);
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_http_")) {
                            usesHttp = true;
                        }
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_db_")) {
                            usesDb = true;
                            if (kc.methodName().equals("kof_db_connect")
                                    || kc.methodName().equals("kof_db_connect2")) {
                                usesMysql |= connectsToMysql(i, ops);
                            }
                        }
                        if (op instanceof KofCall kc && (kc.methodName().equals("kof_spawn")
                                || kc.methodName().equals("kof_spawn_result"))) {
                            usesConcurrency = true;
                        }
                    }
                }
            }
        }
        if (usesDb) {
            RuntimeDb1.emit(sb);
            RuntimeDb2.emit(sb);
            RuntimeDb3.emit(sb);
            RuntimeDb4.emit(sb);
            RuntimeDb5.emit(sb);
            RuntimeDb6.emit(sb);
            NativeDbPrepared.emitMysqlPrepared(sb);
        }
        if (usesHttp) {
            NativeHttpRuntime.emitHttpFunctions(sb);
        }
        NativeWebRuntime.emitWebFunctions(sb);
        IRClass mainClass = null;
        // pré-registro do mangle de TODOS os métodos antes de emitir —
        // forward reference de função top-level (callee depois do caller)
        // não pode cair no fallback não-mangled (undefined reference no ld)
        for (IRClass clazz : module.classes()) {
            for (IRMethod method : clazz.methods()) {
                if ("<clinit>".equals(method.name())) continue;
                String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(method.name());
                if ("<init>".equals(method.name())) {
                    mangled += "_" + method.parameterTypes().size();
                }
                functionMangleMap.putIfAbsent(method.name(), mangled);
            }
        }
        for (IRClass clazz : module.classes()) {
            currentClass = clazz;
            for (IRMethod method : clazz.methods()) {
                if ("main".equals(method.name())) {
                    mainClass = clazz;
                    continue;
                }
                emitMethod(sb, clazz, method);
            }
        }
        if (mainClass != null) {
            currentClass = mainClass;
            for (IRMethod method : mainClass.methods()) {
                if ("main".equals(method.name())) {
                    emitMethod(sb, mainClass, method);
                }
            }
            emitStart(sb, mainClass);
        }
        String mainClassName = mainClass != null ? mainClass.name() : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(mainClassName + ".s");
        Path binFile = outputDir.resolve(mainClassName);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        try { Files.writeString(java.nio.file.Path.of("/tmp/kof_asm_debug.s"), sb.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING); } catch(Exception ignore){}
        System.err.println("NativeBackend: Generated " + asmFile + " (" + Files.size(asmFile) + " bytes)");
        assemble(asmFile, binFile);
    }

    void collectStrings(IRClass clazz) {
        for (IRMethod method : clazz.methods()) {
            for (IRBasicBlock block : method.basicBlocks()) {
                for (KofOperation op : block.operations()) {
                    if (op instanceof KofLoadLiteral lit && lit.value() instanceof String s) {
                        internString(s);
                    }
                }
            }
        }
    }


    private void emitMethodTable(StringBuilder sb, IRClass clazz) {
        List<String> methods = collectVirtualMethods(clazz);
        if (methods.isEmpty()) {
            sb.append(".balign 8\n");
            sb.append(sanitizeName(clazz.name()) + "_vtable:\n");
            sb.append("    .quad 0\n");
            return;
        }
        NativeRuntime.generateMethodTable(sb, sanitizeName(clazz.name()), methods);
    }







    void emitNewArray(StringBuilder sb, KofNewArray na) {
        sb.append("    popq %rdi\n");
        sb.append("    movl $").append(elementTypeSize(na.elementType())).append(", %esi\n");
        sb.append("    call kof_array_alloc\n");
        sb.append("    pushq %rax\n");
    }

    void emitNewMultiArray(StringBuilder sb, KofNewMultiArray ma) {
        sb.append("    movl $").append(ma.dims()).append(", %edx\n");
        sb.append("    movl $").append(elementTypeSize(ma.baseType())).append(", %ebx\n");
        sb.append("    movl $1, %esi\n");
        sb.append("    call kof_multi_alloc\n");
        sb.append("    addq $").append(8 * ma.dims()).append(", %rsp\n");
        sb.append("    pushq %rax\n");
    }

    void emitArrayLoad(StringBuilder sb, KofArrayLoad al) {
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_get\n");
        sb.append("    pushq %rax\n");
    }

    void emitArrayStore(StringBuilder sb, KofArrayStore as) {
        sb.append("    popq %rdx\n");
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_set\n");
    }

    void emitArrayLength(StringBuilder sb) {
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_length\n");
        sb.append("    movslq %eax, %rax\n");
        sb.append("    pushq %rax\n");
    }





    void emitCall(StringBuilder sb, KofCall kc) {
        x86Calls().emitCall(sb, kc);
    }


    /**
     * Prefixo de mangle de uma classe com PACKAGE: o call site precisa do
     * internal name (com/acme/User → com_acme_User), não do nome simples
     * (User) — senão `C()` de uma classe importada vira undefined reference
     * `C_init_0` (a definição usa clazz.name()). Bug 22.
     */
    String classTypeManglePrefix(Type.ClassType ct) {
        String internal = ct.packageName() != null && !ct.packageName().isEmpty()
                ? ct.packageName().replace('.', '/') + "/" + ct.name()
                : ct.name();
        return sanitizeName(internal);
    }



    /** Detecta o protocolo do URL de conexão quando é um literal em
     *  compile-time (intenção conhecida pelo compilador): mysql/mariadb
     *  exigem a lib do cliente no link; sqlite, não. URLs dinâmicos
     *  linkam as duas (default conservador). */
    private boolean connectsToMysql(int callIndex, List<KofOperation> ops) {
        for (int j = callIndex - 1; j >= 0 && j >= callIndex - 8; j--) {
            if (ops.get(j) instanceof KofLoadLiteral lit && lit.value() instanceof String url) {
                String u = url.toLowerCase();
                return !u.startsWith("sqlite:");
            }
        }
        return true;
    }

    void runCommand(String[] cmd, String name) throws IOException {
        NativeAssembler.runCommand(cmd, name);
    }

    void assemble(Path asmFile, Path binFile) throws IOException {
        NativeAssembler.assemble(asmFile, binFile, usesDb, usesMysql, usesConcurrency);
    }

    // ---------------------------------------------------------------------
    // NATIVE002 — lowering riscv64 + runtime EM ASSEMBLY PURO (sem C).
    //
    // Kof é Kof: o runtime é asm puro (raw syscalls, layout de objeto idêntico
    // ao x86_64 em NativeRuntime), compilado com riscv64-linux-gnu-as e
    // linkado com riscv64-linux-gnu-ld — binário estático, sem C.
    //
    // A stack machine é a MESMA do x86_64 (operandos numa pilha), com a ABI
    // RISC-V: `s11` = frame pointer (locais em `s11-(idx+1)*8`), `s2` =
    // ponteiro da pilha de operandos (callee-saved — sobrevive a calls), e
    // `ra`/`s2` preservados no topo do frame.
    //
    // Caminho feliz (validado em qemu-riscv64): println(String/Int),
    // var x = n, aritmética Int (ADD/SUB/MUL/DIV/MOD), comparações
    // (EQ/NE/LT/LE/GT/GE), if/else. Ops fora disso → diagnóstico NATIVE002
    // (nunca binário mudo).
    // ---------------------------------------------------------------------


    // ---- NATIVE002-stdlib: HTTP client riscv64 (asm puro) -----------------
    // Port de NativeHttpRuntime (x86_64) para a convenção riscv64: args em
    // a0..a7, resultado em a0, syscalls asm-generic (socket=198, connect=203,
    // write=64, read=63, close=57 — MESMA tabela do aarch64). O aarch64 herda
    // via translateRiscvToAarch64 (por isso a3 é evitado: colide com gp=x3).
    // HTTP/1.1 + Connection: close + read-ate-EOF; body após \r\n\r\n.
    static void emitRiscvHttp(StringBuilder sb) {
        NativeRiscvHttpSupport.emit(sb);
        NativeRiscvHttpCore.emit(sb);
    }


    // ---- NATIVE002-stdlib: spawn/await riscv64 (clone+futex, asm puro) ----
    // qemu-riscv64 8.2.2 NÃO implementa clone3 (ENOSYS) — usa clone(220) com o
    // flag-set da glibc (0x3D0F00 = VM|FS|FILES|SIGHAND|THREAD|SYSVSEM|SETTLS|
    // PARENT_SETTID|CHILD_CLEARTID), que é aceito. O filho herda os registradores
    // do pai no ecall (a0=0, s0=handle) e roda o trampoline; await espera via
    // futex em handle->done (sem pthread_join). exit(93) mata só a thread.
    static boolean usesSpawn(IRModule module) {
        return NativeRiscvSpawn.usesSpawn(module);
    }

    static void emitRiscvSpawn(StringBuilder sb) {
        NativeRiscvSpawn.emitRiscvSpawn(sb);
    }


    // Runtime riscv64 EM ASSEMBLY PURO (Kof é Kof — sem C; mesmo estilo do
    // x86_64 em NativeRuntime: raw syscall + layout de objeto idêntico).
    // Layout de String: typeId@0(i32) super@4(i32) vtable@8(ptr) len@16(i32)
    // pad@20(i32) data@24(inline). KOF_STRING_TYPE_ID=1.



    // ---- NATIVE002-stdlib: Map/Set riscv64 (aarch64 herda via tradutor) ----
    // Port linear-scan do RuntimeMap/RuntimeSet (x86_64): arrays paralelos
    // keys@24 / vals@32, size@16, cap@20, header 24B (typeId@0 super@4
    // vtable@8). Chaves comparadas por kof_string_equals (paridade x86_64 —
    // Int-key map não é suportado em nenhum native). Set = lista com tag
    // (1=string → equals; 0 → pointer, igual x86_64). Convenção riscv:
    // a0=receiver, a1..=args, resultado em a0.



    /** Toolchain ausente (binário não encontrado) — gracioso: mantém asm,
     *  assumeToolchain() pula o teste. NÃO confundir com falha de as/ld. */
    private void emitMethod(StringBuilder sb, IRClass clazz, IRMethod method) {
        nativeMethods.emitMethod(sb, clazz, method);
    }
    private void emitOperation(StringBuilder sb, KofOperation op, IRMethod currentMethod) {
        nativeMethods.emitOperation(sb, op, currentMethod);
    }
    private void emitStart(StringBuilder sb, IRClass clazz) {
        nativeMethods.emitStart(sb, clazz);
    }

    private void emitRiscv(IRModule module, Path outputDir) throws IOException {
        nativeArch.emitRiscv(module, outputDir);
    }
    private void emitAarch64(IRModule module, Path outputDir) throws IOException {
        nativeArch.emitAarch64(module, outputDir);
    }

    void emitNewObject(StringBuilder sb, KofNewObject no) { NativeOpHelpers.emitNewObject(this, sb, no); }
    int elementTypeSize(Type elemType) { return NativeOpHelpers.elementTypeSize(this, elemType); }
    void emitLoadLiteral(StringBuilder sb, KofLoadLiteral lit) { NativeOpHelpers.emitLoadLiteral(this, sb, lit); }
    void emitConditionalJump(StringBuilder sb, KofConditionalJump kc) { NativeOpHelpers.emitConditionalJump(this, sb, kc); }
    String resolveCalleeName(KofCall kc) { return NativeOpHelpers.resolveCalleeName(this, kc); }
    int resolveFieldOffset(Type ownerType, String fieldName) { return NativeOpHelpers.resolveFieldOffset(this, ownerType, fieldName); }

    List<String> collectVirtualMethods(IRClass clazz) { return NativeClassMeta.collectVirtualMethods(this, clazz); }
    int findVirtualMethodIndex(String ownerTypeName, String methodName) { return NativeClassMeta.findVirtualMethodIndex(this, ownerTypeName, methodName); }
    void emitStringData(StringBuilder sb) { NativeClassMeta.emitStringData(this, sb); }

}

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class NativeBackend implements Backend {
    private NativeArchEmitter nativeArch;
    private NativeMethodEmitter nativeMethods;

    final Target target;
    final Map<LabelId, String> labelMap = new HashMap<>();
    int labelCounter = 0;
    final List<String[]> stringLiterals = new ArrayList<>();
    int stringCounter = 0;
    // §107 record/nested (19/09): rótulo único por descritor de impressão
    // emitido no call-site (NativePrintDescriptors.emit).
    int printDescriptorCounter = 0;
    int inlineSeq = 0;   // labels inline (split etc.) — únicas por call site

    /** Campos estáticos: chave "owner|name" → label no .data (bug 41). */
    final NativeStaticData staticData = new NativeStaticData(this);
    Type lastPushedType = Type.UnknownType.UNKNOWN;
    IRClass currentClass = null;
    boolean usesDb = false;
    boolean usesOrm = false;
    /** F2b: className das entidades usadas com {@code orm.find} (para o
     *  resolver {@code kof_orm_ctors} que constrói o record no runtime). */
    final Set<String> ormCtorClasses = new LinkedHashSet<>();
    boolean usesHttp = false;

    /** kof.process no nativo (RuntimeProcess): execvp/PATH = libc do host. */
    boolean usesProcess = false;
    boolean usesMysql = false;
    boolean usesConcurrency = false;
    boolean usesPow = false;
    /** #431: bibliotecas dos `extern` bound (ligadas no ld, link-by-use). */
    final Set<String> ffiLibs = new LinkedHashSet<>();
    boolean ffiUsesCstr = false;
    /** D6-2/3.7: algum extern recebe array `T[]`→`ptr` (pede `kof_ffi_pack_array`). */
    boolean ffiUsesArray = false;
    final Map<String, String> functionMangleMap = new HashMap<>();
    private final Map<String, ClassLayout> layoutCache = new HashMap<>();
    Map<String, IRClass> allClassesMap = new HashMap<>();
    /** Debug info nativa (DWARF .debug_line via .file/.loc). */
    boolean debugInfo = false;
    /** Frente 4 fatia 1: CU DWARF propria (.debug_info/.debug_abbrev). */
    final NativeDwarf kofDwarf = new NativeDwarf();
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

    /** B-1: perfil de link (HOST padrão; FREESTANDING = estático, sem libc no x86_64;
     *  B-2: UEFI = herda o link estático sem libc + entry MS x64 e PE32+). */
    public NativeBackend profile(NativeProfile p) {
        this.freestanding = p == NativeProfile.FREESTANDING || p == NativeProfile.UEFI
                || p == NativeProfile.UEFI_RING || p == NativeProfile.BIOS;
        this.uefi = p == NativeProfile.UEFI || p == NativeProfile.UEFI_RING;
        this.rings = p == NativeProfile.UEFI_RING;
        this.bios = p == NativeProfile.BIOS;
        NativeProfile.active = p;
        return this;
    }

    boolean freestanding = false;
    /** B-2: perfil UEFI — entry {@code _start} MS x64 + corpos de costura EFI + PE32+. */
    boolean uefi = false;
    /** B-6.1: perfil {@code uefi-ring} — instala GDT/IDT/TSS próprios no {@code _start}. */
    boolean rings = false;
    /** B-3: perfil BIOS — entry {@code _start} 16-bit real-mode e imagem de setor
     *  de boot de 512 bytes ({@code 0xAA55}). */
    boolean bios = false;

    String resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> ".Lkof_" + (labelCounter++));
    }

    String sanitizeName(String name) {
        return NativeSymbolMangling.sanitizeNameStatic(name);
    }

    // SG-011B/§131: mangling de símbolo extraído p/ NativeSymbolMangling
    // (split ≤500, 13/09) — a responsabilidade é só nomear, sem estado.


    /** Registra um campo estático e devolve o símbolo .data (bug 41). */
    String staticSymbol(String ownerKey, String fieldName) {
        return staticData.symbol(ownerKey, fieldName);
    }
    String staticSymbol(String ownerKey, String fieldName, Object initialValue) {
        return staticData.symbol(ownerKey, fieldName, initialValue);
    }
    String staticKey(Type ownerType) { return staticData.key(ownerType); }
    void collectStaticFields() { staticData.collect(); }
    void emitStaticData(StringBuilder sb) { staticData.emit(sb); }


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
            NativeFfiCall.scanExterns(this, module);
            emitRiscv(module, outputDir);
            return;
        }
        if (target == Target.NATIVE_AARCH64) {
            NativeFfiCall.scanExterns(this, module);
            emitAarch64(module, outputDir);
            return;
        }
        if (target == Target.NATIVE_RISCV32) {
            dev.kof.compiler.nat.mcu.NativeMcuRiscv32.emit(module, outputDir);
            return;
        }
        if (target == Target.NATIVE_MCU_ARM) {
            dev.kof.compiler.nat.mcu.NativeMcuArm.emit(module, outputDir);
            return;
        }
        if (module.classes().isEmpty()) return;
        labelCounter = 0;
        labelMap.clear();
        kofDwarf.fns.clear();
        kofDwarf.arch = NativeDwarf.Arch.X86_64;
        stringLiterals.clear();
        stringCounter = 0;
        printDescriptorCounter = 0;
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
        // #113: ABERTURA do intervalo de raízes do GC conservador ANTES de
        // qualquer dado do programa (.data merged: strings, kof_static_*,
        // schemas, method tables + runtime) — estáticos do usuário apontando
        // p/ heap eram raízes invisíveis ao mark (abaixo do root_start antigo,
        // que ficava no preâmbulo do runtime). O sentinel .quad 0 é a primeira
        // palavra varrida (nunca pointer-plausível, mark ignora).
        sb.append(".globl kof_heap_root_start\n");
        sb.append("kof_heap_root_start:\n");
        sb.append(".quad 0\n");
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
        int rtStart = sb.length();
        sb.append(NativeRuntime.generateRuntimeAssembly());
        int rtEnd = sb.length();
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
                        if (op instanceof KofCall kc && (kc.methodName().equals("kof_process_run")
                                    || kc.methodName().equals("kof_process_spawn")
                                    || kc.methodName().equals("kof_shell_runwith"))) {
                            usesProcess = true;
                        }
                        if (op instanceof KofCall kc && kc.methodName().equals("kof_math_pow")) {
                            usesPow = true; // R2: unico caminho ao shim (KofMath.pow; recusado no cross)
                        }
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_db_")) {
                            usesDb = true;
                            if (kc.methodName().equals("kof_db_connect")
                                    || kc.methodName().equals("kof_db_connect2")) {
                                usesMysql |= NativeLinkPolicy.connectsToMysql(i, ops);
                            }
                        }
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_orm_")) {
                            usesOrm = true;
                            if ((kc.methodName().equals("kof_orm_find")
                                        && kc.parameterTypes().size() == 5)
                                    || (kc.methodName().equals("kof_orm_all")
                                        && kc.parameterTypes().size() == 4)
                                    || (kc.methodName().equals("kof_orm_where")
                                        && kc.parameterTypes().size() == 6)
                                    || (kc.methodName().equals("kof_orm_page")
                                        && kc.parameterTypes().size() == 6)
                                    || (kc.methodName().equals("kof_orm_where_op")
                                        && kc.parameterTypes().size() == 7)) {
                                // 5º arg = className literal (KofLoadLiteral STRING
                                // emitido logo antes do call pelo lowering ORM)
                                for (int j = i - 1; j >= i - 2 && j >= 0; j--) {
                                    if (ops.get(j) instanceof KofLoadLiteral lit
                                            && lit.value() instanceof String s) {
                                        ormCtorClasses.add(s);
                                        break;
                                    }
                                }
                            }
                        }
                        if (op instanceof KofCall kc && (kc.methodName().equals("kof_spawn")
                                || kc.methodName().equals("kof_spawn_result"))) {
                            usesConcurrency = true;
                        }
                        if (op instanceof KofCall kc && NativeFfiCall.isExternCall(kc)) {
                            // #431: o extern liga a `library()` declarada no ld
                            // (link-by-use, padrão DB001/sqlite) — sem ela o
                            // `call sym@PLT` não resolve.
                            NativeFfiCall.noteExtern(this, kc);
                        }
                    }
                }
            }
        }
        NativeOrmEmit.emitRuntimeSlices(this, sb);

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
                String mangled = NativeSymbolMangling.fnSymbol(clazz.name(), method, allClassesMap);
                functionMangleMap.putIfAbsent(NativeSymbolMangling.fnKey(clazz.name(), method, allClassesMap), mangled);
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
        NativeFfiCall.emitHelpers(this, sb);
        if (debugInfo && target == Target.NATIVE) {
            kofDwarf.emit(sb, sourceFile);
        }
        // #113/S-5(x86): o FECHAMENTO explicito (kof_heap_root_end) entra JUNTO
        // do --gc-sections no x86, NAO aqui: medir hoje mostra _end ~33KB acima
        // de um rotulo no .bss final (a arena do heap continua alem), entao
        // trocar o topo por root_end encolheria o intervalo e under-marcaria
        // (regressao). O topo fica _end; a correcao do bug e so o root_start
        // (abaixo, na abertura do .data do programa).
        String mainClassName = mainClass != null ? mainClass.name() : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(mainClassName + ".s");
        Path binFile = outputDir.resolve(mainClassName);
        Files.createDirectories(asmFile.getParent());
        String fullAsm = RuntimeSlices.pruneRuntime(sb, rtStart, rtEnd);
        Files.writeString(asmFile, fullAsm);
        System.err.println("NativeBackend: Generated " + asmFile + " (" + Files.size(asmFile) + " bytes)");
        NativeLinkPolicy.assemble(this, asmFile, binFile);
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
        NativeClassMeta.emitMethodTable(this, sb, clazz);
    }







    void emitNewArray(StringBuilder sb, KofNewArray na) { NativeOpHelpers.emitNewArray(this, sb, na); }

    void emitNewMultiArray(StringBuilder sb, KofNewMultiArray ma) { NativeOpHelpers.emitNewMultiArray(this, sb, ma); }

    void emitArrayLoad(StringBuilder sb, KofArrayLoad al) { NativeOpHelpers.emitArrayLoad(this, sb, al); }

    void emitArrayStore(StringBuilder sb, KofArrayStore as) { NativeOpHelpers.emitArrayStore(this, sb, as); }

    void emitArrayLength(StringBuilder sb) { NativeOpHelpers.emitArrayLength(this, sb); }





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



    void runCommand(String[] cmd, String name) throws IOException {
        NativeAssembler.runCommand(cmd, name);
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
        NativeRiscvHttpVerbs.emit(sb);
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
    int findVirtualMethodIndex(String ownerTypeName, String methodName) { return NativeClassMeta.findVirtualMethodIndex(this, ownerTypeName, methodName, -1); }
    int findVirtualMethodIndex(String ownerTypeName, String methodName, int argCount) { return NativeClassMeta.findVirtualMethodIndex(this, ownerTypeName, methodName, argCount); }
    int findVirtualMethodIndex(String ownerTypeName, String methodName, List<Type> argTypes) { return NativeClassMeta.findVirtualMethodIndex(this, ownerTypeName, methodName, argTypes); }
    void emitStringData(StringBuilder sb) { NativeClassMeta.emitStringData(this, sb); }

}

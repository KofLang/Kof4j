package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NativeBackend implements Backend {

    private final Target target;
    private final Map<LabelId, String> labelMap = new HashMap<>();
    private int labelCounter = 0;
    private final List<String[]> stringLiterals = new ArrayList<>();
    private int stringCounter = 0;
    private Type lastPushedType = Type.UnknownType.UNKNOWN;
    private IRClass currentClass = null;
    private boolean usesDb = false;
    private boolean usesHttp = false;
    private boolean usesMysql = false;
    private boolean usesConcurrency = false;
    private final Map<String, String> functionMangleMap = new HashMap<>();
    private final Map<String, ClassLayout> layoutCache = new HashMap<>();
    private Map<String, IRClass> allClassesMap = new HashMap<>();
    /** Debug info nativa (DWARF .debug_line via .file/.loc). */
    private boolean debugInfo = false;
    private String sourceFile = "";
    private NativeRiscvCrossEmit crossEmitInst;

    private NativeRiscvCrossEmit crossEmit() {
        if (crossEmitInst == null) crossEmitInst = new NativeRiscvCrossEmit(this);
        return crossEmitInst;
    }

    public NativeBackend() { this(Target.NATIVE); }
    public NativeBackend(Target target) { this.target = target; }

    private String resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> ".Lkof_" + (labelCounter++));
    }

    private String sanitizeName(String name) {
        return name.replace("/", "_").replace(".", "_").replace("-", "_")
                .replace("<", "").replace(">", "");
    }


    /**
     * JSN002: coleta as tabelas de schema JSON por classe (nome+offset+
     * tipo de cada campo, inclusive herdados via ClassLayout). Campos de
     * tipos nao suportados (FP, List, Map) sao omitidos — o gate no
     * CompilerDriver diagnostica essas classes antes delas chegarem aqui.
     * Os nomes dos campos sao internados AQUI (antes de emitStringData).
     */
    private record JsonSchemaEntry(String tokenCstr, long offset, long typeCode, String className,
                                    String auxCstr) {}
    private record JsonSchemaTable(String tableLabel, String classCstr, long totalSize,
                                   java.util.List<JsonSchemaEntry> entries) {}

    private final java.util.List<JsonSchemaTable> jsonSchemas = new java.util.ArrayList<>();

    private Long jsonFieldTypeCode(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int", "char", "byte", "short" -> 1L;
                case "long" -> 2L;
                case "bool" -> 3L;
                default -> null; // float/double: gap FP (JSN001)
            };
        }
        if (BuiltinTypes.isString(t)) return 4L;
        if (t instanceof Type.ClassType ct) {
            String simple = ct.name();
            for (IRClass c : allClassesMap.values()) {
                if (c.name().equals(simple) || c.name().endsWith("/" + simple)) return 5L;
            }
        }
        return null;
    }

    private void collectJsonSchemas() {
        jsonSchemas.clear();
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.fields().isEmpty()) continue;
            ClassLayout layout = getLayout(clazz);
            java.util.List<JsonSchemaEntry> entries = new java.util.ArrayList<>();
            boolean any = false;
            boolean allSupported = true;
            for (FieldLayout f : layout.fields()) {
                Long code = jsonFieldTypeCode(f.type());
                if (code == null) { allSupported = false; continue; }
                if (f.type() instanceof Type.ClassType ct) {
                    // campo aninhado: so se a classe alvo tambem tiver tabela
                    boolean has = false;
                    for (IRClass c : allClassesMap.values()) {
                        if (c.name().equals(ct.name()) || c.name().endsWith("/" + ct.name())) {
                            has = !getLayout(c).fields().isEmpty();
                            break;
                        }
                    }
                    if (!has) { allSupported = false; continue; }
                }
                // token '"nome":' serve encode (parte literal) e decode (busca)
                String tokLabel = internString("\"" + f.name() + "\":");
                String auxCstr = null;
                if (code == 5L && f.type() instanceof Type.ClassType ct) {
                    String cn = ct.packageName().isEmpty() ? ct.name()
                            : ct.packageName() + "." + ct.name();
                    auxCstr = internString(cn);
                }
                entries.add(new JsonSchemaEntry(tokLabel, f.offset(), code, f.name(), auxCstr));
                any = true;
            }
            if (!any || !allSupported) continue;
            String tableLabel = ".Lsch_" + sanitizeName(clazz.name());
            String classCstr = internString(clazz.name());
            jsonSchemas.add(new JsonSchemaTable(tableLabel, classCstr,
                    layout.totalSize(), java.util.List.copyOf(entries)));
        }
    }

    /** Emite as tabelas de schema + registro + finder (apos emitStringData). */
    private void emitJsonSchemaData(StringBuilder sb) {
        if (jsonSchemas.isEmpty()) return;
        sb.append(".section .data\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append(t.tableLabel()).append(":\n");
            sb.append("    .quad ").append(t.totalSize()).append("\n");
            sb.append("    .quad ").append(t.entries().size()).append("\n");
            for (JsonSchemaEntry e : t.entries()) {
                sb.append("    .quad ").append(e.tokenCstr()).append("\n");
                sb.append("    .quad ").append(e.offset()).append("\n");
                sb.append("    .quad ").append(e.typeCode()).append("\n");
                sb.append("    .quad ").append(e.auxCstr() == null ? 0 : e.auxCstr()).append("\n");
            }
        }
        sb.append(".Lsch_registry:\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append("    .quad ").append(t.classCstr()).append("\n");
            sb.append("    .quad ").append(t.tableLabel()).append("\n");
        }
        sb.append("    .quad 0\n");
        sb.append("""
            .section .text
            .globl kof_json_schema_find
            .type kof_json_schema_find, @function
            kof_json_schema_find:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx             # nome C-string
                leaq .Lsch_registry(%rip), %r9
            .Lschf_loop:
                movq (%r9), %rax            # name cstr da entrada
                testq %rax, %rax
                jz .Lschf_notfound
                xorq %rcx, %rcx
            .Lschf_cmp:
                movzbl (%rbx,%rcx), %edx
                movzbl (%rax,%rcx), %esi
                cmpl %esi, %edx
                jne .Lschf_next
                testl %edx, %edx
                jz .Lschf_found
                incq %rcx
                jmp .Lschf_cmp
            .Lschf_next:
                addq $16, %r9
                jmp .Lschf_loop
            .Lschf_found:
                movq 8(%r9), %rax           # table ptr
                jmp .Lschf_exit
            .Lschf_notfound:
                xorl %eax, %eax
            .Lschf_exit:
                popq %r12
                popq %rbx
                ret
            """);
    }

    /** Tabela de schema para uma classe (ou null se ausente), pelo nome. */
    private String schemaLabelFor(String className) {
        for (JsonSchemaTable t : jsonSchemas) {
            if (t.classCstr().equals(className)) return t.tableLabel();
        }
        return null;
    }

    private String internString(String value) {
        for (String[] entry : stringLiterals) {
            if (entry[0].equals(value)) return entry[1];
        }
        String label = ".Lstr_" + (stringCounter++);
        stringLiterals.add(new String[]{value, label});
        return label;
    }

    private ClassLayout getLayout(IRClass clazz) {
        return layoutCache.computeIfAbsent(clazz.name(), k ->
            ClassLayout.buildWithSuper(clazz, name -> allClassesMap.get(name)));
    }

    private ClassLayout getLayoutForType(Type type) {
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
        collectJsonSchemas();
        emitStringData(sb);
        emitJsonSchemaData(sb);
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

    private void collectStrings(IRClass clazz) {
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

    private List<String> collectVirtualMethods(IRClass clazz) {
        List<String> methods = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            IRClass superClazz = allClassesMap.get(current);
            if (superClazz == null) break;
            for (IRMethod m : superClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(sanitizeName(superClazz.name()) + "_" + sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : superClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
            current = superClazz.superName();
        }
        while (!queue.isEmpty()) {
            String ifaceName = queue.poll();
            IRClass ifaceClazz = allClassesMap.get(ifaceName);
            if (ifaceClazz == null) continue;
            for (IRMethod m : ifaceClazz.methods()) {
                if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                        && !m.name().startsWith("kof_")) {
                    if (!methodNames.contains(m.name())) {
                        methodNames.add(m.name());
                        methods.add(sanitizeName(ifaceClazz.name()) + "_" + sanitizeName(m.name()));
                    }
                }
            }
            for (String iface : ifaceClazz.interfaces()) {
                if (!visited.contains(iface)) {
                    visited.add(iface);
                    queue.add(iface);
                }
            }
        }
        for (IRMethod m : clazz.methods()) {
            if (!"<init>".equals(m.name()) && !"<clinit>".equals(m.name())
                    && !m.name().startsWith("kof_")) {
                int idx = methodNames.indexOf(m.name());
                if (idx >= 0) {
                    methods.set(idx, sanitizeName(clazz.name()) + "_" + sanitizeName(m.name()));
                } else {
                    methodNames.add(m.name());
                    methods.add(sanitizeName(clazz.name()) + "_" + sanitizeName(m.name()));
                }
            }
        }
        return methods;
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

    private int findVirtualMethodIndex(String ownerTypeName, String methodName) {
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.name().equals(ownerTypeName) || clazz.name().endsWith("/" + ownerTypeName)
                    || ownerTypeName.endsWith("/" + clazz.name()) || ownerTypeName.equals(sanitizeName(clazz.name()))) {
                List<String> methods = collectVirtualMethods(clazz);
                String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(methodName);
                for (int i = 0; i < methods.size(); i++) {
                    if (methods.get(i).equals(mangled)) {
                        return i;
                    }
                }
                for (IRMethod m : clazz.methods()) {
                    if (m.name().equals(methodName) && !"<init>".equals(m.name()) && !"<clinit>".equals(m.name())) {
                        String m2 = sanitizeName(clazz.name()) + "_" + sanitizeName(m.name());
                        for (int i = 0; i < methods.size(); i++) {
                            if (methods.get(i).equals(m2)) {
                                return i;
                            }
                        }
                    }
                }
                break;
            }
        }
        return -1;
    }

    private void emitStringData(StringBuilder sb) {
        for (String[] entry : stringLiterals) {
            String value = entry[0];
            String label = entry[1];
            String escaped = value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t");
            sb.append(label).append(": .asciz \"").append(escaped).append("\"\n");
        }
        sb.append(".Lnewline: .asciz \"\\n\"\n");
        sb.append(".Lkof_str_true: .asciz \"true\"\n");
        sb.append(".Lkof_str_false: .asciz \"false\"\n");
        sb.append(".balign 8\n");
        sb.append("kof_super_table:\n");
        for (IRClass clazz : allClassesMap.values()) {
            if (clazz.typeId() == 0) continue;
            int superTypeId = 0;
            if (clazz.superName() != null && !clazz.superName().isEmpty()) {
                String superSimple = clazz.superName().substring(clazz.superName().lastIndexOf('/') + 1);
                for (IRClass other : allClassesMap.values()) {
                    if (other.name().equals(clazz.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .long ").append(clazz.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .long 0, 0\n");
    }

    private void emitMethod(StringBuilder sb, IRClass clazz, IRMethod method) {
        if ("<clinit>".equals(method.name())) return;

        currentClass = clazz;

        String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(method.name());
        if ("<init>".equals(method.name())) {
            mangled += "_" + method.parameterTypes().size();
        }
        functionMangleMap.put(method.name(), mangled);
        sb.append("\n.globl ").append(mangled).append("\n");
        sb.append(".type ").append(mangled).append(", @function\n");
        sb.append(mangled).append(":\n");

        sb.append("    pushq %rbp\n");
        sb.append("    movq %rsp, %rbp\n");

        int maxSlot = method.localVariables().stream()
                .mapToInt(IRLocalVariable::index).max().orElse(0);
        // Scan for CONSTRUCTOR calls with stack args to reserve frame space
        int maxCtorStackArgs = 0;
        for (IRBasicBlock bb : method.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofCall kc && kc.kind() == KofCallKind.CONSTRUCTOR
                        && "<init>".equals(kc.methodName())) {
                    int sa = Math.max(0, kc.parameterTypes().size() - 5);
                    maxCtorStackArgs = Math.max(maxCtorStackArgs, sa);
                }
            }
        }
        int extraFrame = maxCtorStackArgs > 0 ? 256 + maxCtorStackArgs * 8 : 0;
        int frameSize = Math.max((maxSlot + 1) * 8, 16) + extraFrame;
        frameSize = (frameSize + 15) & ~15;
        if (frameSize > 0) {
            sb.append("    subq $").append(frameSize).append(", %rsp\n");
        }

        int intArgIdx = 0;
        // bug 9: capturas de lambda NÃO são args de entrada (são carregadas
        // dos campos do objeto via ops). O prologue antigo iterava os locals
        // na ordem de inserção [this, capture, param] e consumia rsi/rdx para
        // a captura — o param real ficava com registro errado (lixo).
        // Params ocupam os slots 1..(soma das larguras) — só eles recebem
        // registros; capturas (slots acima) são preenchidas pelas ops.
        int paramSlotMax = 1;
        for (Type pt : method.parameterTypes()) paramSlotMax += NativeTypeKinds.isDoubleWidthSlot(pt) ? 2 : 1;
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        java.util.List<IRLocalVariable> sortedLocals = new java.util.ArrayList<>(method.localVariables());
        sortedLocals.sort(java.util.Comparator.comparingInt(lv -> lv.index()));
        for (IRLocalVariable lv : sortedLocals) {
            if (lv.name().equals("this")) {
                sb.append("    movq %rdi, -").append((lv.index() + 1) * 8).append("(%rbp)\n");
                intArgIdx++;
                continue;
            }
            if (lv.index() >= paramSlotMax) {
                // captura de lambda: preenchida pelas ops (KofLoadField) —
                // NÃO consome registro de entrada
                continue;
            }
            if (intArgIdx < 6) {
                sb.append("    movq ").append(intRegs[intArgIdx]).append(", -").append((lv.index() + 1) * 8).append("(%rbp)\n");
            } else {
                // Args beyond register capacity are on the stack.
                // After push %rbp, stack layout is: [saved_rbp][ret_addr][arg7][arg8]...
                int stackOffset = 16 + (intArgIdx - 6) * 8;
                sb.append("    movq ").append(stackOffset).append("(%rbp), %rax\n");
                sb.append("    movq %rax, -").append((lv.index() + 1) * 8).append("(%rbp)\n");
            }
            intArgIdx++;
        }

        boolean endsWithReturn = false;
        for (IRBasicBlock block : method.basicBlocks()) {
            for (KofOperation op : block.operations()) {
                if (op instanceof KofReturn || op instanceof KofReturnVoid) endsWithReturn = true;
                emitOperation(sb, op, method);
            }
        }

        if (!endsWithReturn) {
            if (usesConcurrency && "main".equals(method.name())) {
                // join implícito: nenhuma tarefa spawnada fica orfa
                sb.append("    call kof_spawn_join_all\n");
            }
            sb.append("    movq %rbp, %rsp\n");
            sb.append("    popq %rbp\n");
            sb.append("    ret\n");
        }
    }

    private void emitOperation(StringBuilder sb, KofOperation op, IRMethod currentMethod) {
        if (debugInfo && currentMethod.debugInfo() != null) {
            SourcePosition dbg = currentMethod.debugInfo().positions().get(op);
            if (dbg != null && dbg.line() > 0) {
                // .loc <file> <line> <col>: o as gera .debug_line (DWARF)
                sb.append("    .loc 1 ").append(dbg.line()).append(" 0\n");
            }
        }
        if (op instanceof KofLoadLiteral lit) {
            lastPushedType = lit.type();
        } else if (op instanceof KofLoadLocal ll) {
            lastPushedType = ll.type();
        } else if (op instanceof KofLoadField lf) {
            lastPushedType = lf.fieldType();
        } else if (op instanceof KofArrayLength) {
            lastPushedType = Type.PrimitiveType.INT;
        } else if (op instanceof KofBinary kb) {
            lastPushedType = kb.operandType();
        } else if (op instanceof KofUnary ku) {
            lastPushedType = ku.operandType();
        } else if (op instanceof KofCall kc) {
            lastPushedType = kc.returnType();
        }

        switch (op) {
            case KofLoadLiteral lit -> emitLoadLiteral(sb, lit);
            case KofLoadLocal ll -> {
                sb.append("    movq -").append((ll.index() + 1) * 8).append("(%rbp), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreLocal sl -> {
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, -").append((sl.index() + 1) * 8).append("(%rbp)\n");
            }
            case KofLoadField lf -> {
                if (lf.ownerType() instanceof Type.ClassType ctLF && "MemEntry".equals(ctLF.name()) && "key".equals(lf.name())) {
                }
                sb.append("    popq %rax\n");
                int offset = resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    movq ").append(offset).append("(%rax), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreField sf -> {
                sb.append("    popq %rax\n");
                sb.append("    popq %rcx\n");
                int offset = resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    movq %rax, ").append(offset).append("(%rcx)\n");
            }
            case KofBinary kb -> NativeX86Arith.emitBinary(sb, kb);
            case KofUnary ku -> NativeX86Arith.emitUnary(sb, ku);
            case KofReturn kr -> {
                if (usesConcurrency && "main".equals(currentMethod.name())) {
                    // join implícito no fim do main — nenhuma tarefa órfã.
                    // (main sempre termina em um return explícito/implícito,
                    //  então o join vai no epílogo do return, não no bloco
                    //  !endsWithReturn — que nunca roda para o main.)
                    sb.append("    call kof_spawn_join_all\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rbp, %rsp\n");
                sb.append("    popq %rbp\n");
                sb.append("    ret\n");
            }
            case KofReturnVoid rv -> {
                if (usesConcurrency && "main".equals(currentMethod.name())) {
                    sb.append("    call kof_spawn_join_all\n");
                }
                sb.append("    movq %rbp, %rsp\n");
                sb.append("    popq %rbp\n");
                sb.append("    ret\n");
            }
            case KofLabel kl -> sb.append(resolveLabel(kl.label())).append(":\n");
            case KofCatchStart kcs -> {
                sb.append(resolveLabel(kcs.handlerLabel())).append(":\n");
                sb.append("    addq $32, %rsp\n");
                sb.append("    movq %rdi, -").append((kcs.localIndex() + 1) * 8).append("(%rbp)\n");
            }
            case KofTryStart kts -> {
                sb.append(resolveLabel(kts.startLabel())).append(":\n");
                sb.append("    subq $32, %rsp\n");
                sb.append("    leaq ").append(resolveLabel(kts.handlerLabel())).append("(%rip), %rax\n");
                sb.append("    movq %rax, 0(%rsp)\n");
                sb.append("    movq %rsp, 8(%rsp)\n");
                sb.append("    movq %rbp, 16(%rsp)\n");
                sb.append("    movq kof_exc_chain(%rip), %rcx\n");
                sb.append("    movq %rcx, 24(%rsp)\n");
                sb.append("    movq %rsp, kof_exc_chain(%rip)\n");
            }
            case KofTryEnd kte -> {
                sb.append("    movq 24(%rsp), %rcx\n");
                sb.append("    movq %rcx, kof_exc_chain(%rip)\n");
                sb.append("    addq $32, %rsp\n");
            }
            case KofJump kj -> sb.append("    jmp ").append(resolveLabel(kj.target())).append("\n");
            case KofConditionalJump kc -> emitConditionalJump(sb, kc);
            case KofCall kc -> emitCall(sb, kc);
            case KofNewObject no -> emitNewObject(sb, no);
            case KofDup dup -> sb.append("    movq (%rsp), %rax\n    pushq %rax\n");
            case KofDupX1 x1 -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %rbx
                    pushq %rax
                    pushq %rbx
                    pushq %rax
                """.stripIndent());
            case KofDupX2 x2 -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %rbx
                    movq 16(%rsp), %rcx
                    pushq %rax
                    pushq %rcx
                    pushq %rbx
                    pushq %rax
                """.stripIndent());
            case KofPop pop -> sb.append("    addq $8, %rsp\n");
            case KofGetStatic gs -> { }
            case KofPutStatic ps -> sb.append("    addq $8, %rsp\n");
            case KofCheckCast cc -> { }
            case KofInstanceOf io -> {
                int targetTypeId = 0;
                if (BuiltinTypes.isString(io.type())) {
                    targetTypeId = NativeRuntime.KOF_STRING_TYPE_ID;
                } else if (io.type() instanceof Type.ClassType ct) {
                    for (IRClass clazz : allClassesMap.values()) {
                        if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                                || ct.name().endsWith("/" + clazz.name()) || ct.name().equals(sanitizeName(clazz.name()))) {
                            targetTypeId = clazz.typeId();
                            break;
                        }
                    }
                }
                sb.append("    popq %rdi\n");
                sb.append("    movl $").append(targetTypeId).append(", %esi\n");
                sb.append("    call kof_instanceof\n");
                sb.append("    pushq %rax\n");
            }
            case KofNewArray na -> emitNewArray(sb, na);
            case KofArrayLoad al -> emitArrayLoad(sb, al);
            case KofArrayStore as -> emitArrayStore(sb, as);
            case KofArrayLength al -> emitArrayLength(sb);
            case KofThrow thr -> {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_throw_string\n");
            }
            default -> { }
        }
    }

    private void emitNewObject(StringBuilder sb, KofNewObject no) {
        ClassLayout layout = null;
        String className = null;
        int typeId = 0;
        if (no.type() instanceof Type.ClassType ct) {
            className = ct.name();
            for (IRClass clazz : allClassesMap.values()) {
                if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                        || className.endsWith("/" + clazz.name()) || className.equals(sanitizeName(clazz.name()))) {
                    layout = getLayout(clazz);
                    className = clazz.name();
                    typeId = clazz.typeId();
                    break;
                }
            }
        }
        int size = layout != null ? layout.totalSize() : ClassLayout.HEADER_SIZE + 64;
        sb.append("    movq $").append(size).append(", %rdi\n");
        sb.append("    call kof_alloc\n");
        if (className != null) {
            String mangled = sanitizeName(className);
            sb.append("    movq %rax, %rdi\n");
            sb.append("    movl $").append(typeId).append(", %esi\n");
            sb.append("    leaq ").append(mangled).append("_vtable(%rip), %rdx\n");
            sb.append("    call kof_init_object\n");
        }
        sb.append("    pushq %rax\n");
    }

    private int elementTypeSize(Type elemType) {
        return switch (elemType) {
            case Type.PrimitiveType pt -> switch (pt.name()) {
                case "byte", "Byte", "bool", "Bool", "boolean" -> 1;
                case "short", "Short" -> 2;
                case "int", "Int", "char", "Char" -> 4;
                case "long", "Long" -> 8;
                case "float", "Float" -> 4;
                case "double", "Double" -> 8;
                default -> 8;
            };
            default -> 8;
        };
    }

    private void emitNewArray(StringBuilder sb, KofNewArray na) {
        sb.append("    popq %rdi\n");
        sb.append("    movl $").append(elementTypeSize(na.elementType())).append(", %esi\n");
        sb.append("    call kof_array_alloc\n");
        sb.append("    pushq %rax\n");
    }

    private void emitArrayLoad(StringBuilder sb, KofArrayLoad al) {
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_get\n");
        sb.append("    pushq %rax\n");
    }

    private void emitArrayStore(StringBuilder sb, KofArrayStore as) {
        sb.append("    popq %rdx\n");
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_set\n");
    }

    private void emitArrayLength(StringBuilder sb) {
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_length\n");
        sb.append("    movslq %eax, %rax\n");
        sb.append("    pushq %rax\n");
    }

    private void emitLoadLiteral(StringBuilder sb, KofLoadLiteral lit) {
        if (lit.value() instanceof Integer i) {
            sb.append("    movq $").append(i).append(", %rax\n");
        } else if (lit.value() instanceof Long l) {
            sb.append("    movq $").append(l).append(", %rax\n");
        } else if (lit.value() instanceof Float f) {
            sb.append("    movq $").append(Float.floatToIntBits(f)).append(", %rax\n");
        } else if (lit.value() instanceof Double d) {
            sb.append("    movq $").append(Double.doubleToLongBits(d)).append(", %rax\n");
        } else if (lit.value() instanceof String s) {
            String label = internString(s);
            int byteLen = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            sb.append("    leaq ").append(label).append("(%rip), %rdi\n");
            sb.append("    movl $").append(byteLen).append(", %esi\n");
            sb.append("    call kof_string_from_literal\n");
        } else if (lit.value() instanceof Boolean b) {
            sb.append("    movq $").append(b ? 1 : 0).append(", %rax\n");
        } else if (lit.value() == null) {
            sb.append("    movq $0, %rax\n");
        }
        sb.append("    pushq %rax\n");
    }



    private void emitConditionalJump(StringBuilder sb, KofConditionalJump kc) {
        Type opTy = kc.operandType();
        if (opTy != null && NativeTypeKinds.isFloatType(opTy)) {
            sb.append("    popq %rax\n");
            sb.append("    popq %rcx\n");
            sb.append("    movd %ecx, %xmm0\n");
            sb.append("    movd %eax, %xmm1\n");
            sb.append("    ucomiss %xmm1, %xmm0\n");
            String jmp;
            switch (kc.comparison()) {
                case EQ -> jmp = "je";
                case NE -> jmp = "jne";
                case LT -> jmp = "jb";
                case LE -> jmp = "jbe";
                case GT -> jmp = "ja";
                case GE -> jmp = "jae";
                default -> jmp = "je";
            }
            // NaN handling: ordered compares must be false when unordered (PF=1)
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LE
                    || kc.comparison() == KofComparison.GE
                    || kc.comparison() == KofComparison.EQ;
            if (needsOrderedCheck) {
                // if unordered (PF=1) skip the true branch
                sb.append("    jp ").append(resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(resolveLabel(kc.trueLabel())).append("\n");
                // still need fallback: if NaN, we already jumped to true
            }
            sb.append("    ").append(jmp).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        if (opTy != null && NativeTypeKinds.isDoubleType(opTy)) {
            sb.append("    popq %rax\n");
            sb.append("    popq %rcx\n");
            sb.append("    movq %rcx, %xmm0\n");
            sb.append("    movq %rax, %xmm1\n");
            sb.append("    ucomisd %xmm1, %xmm0\n");
            String jmp;
            switch (kc.comparison()) {
                case EQ -> jmp = "je";
                case NE -> jmp = "jne";
                case LT -> jmp = "jb";
                case LE -> jmp = "jbe";
                case GT -> jmp = "ja";
                case GE -> jmp = "jae";
                default -> jmp = "je";
            }
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LE
                    || kc.comparison() == KofComparison.GE
                    || kc.comparison() == KofComparison.EQ;
            if (needsOrderedCheck) {
                sb.append("    jp ").append(resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(resolveLabel(kc.trueLabel())).append("\n");
            }
            sb.append("    ").append(jmp).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
            return;
        }
        sb.append("    popq %rax\n");
        sb.append("    popq %rcx\n");
        String cond = switch (kc.comparison()) {
            case EQ -> "je";
            case NE -> "jne";
            case LT -> "jl";
            case LE -> "jle";
            case GT -> "jg";
            case GE -> "jge";
        };
        if (opTy != null && NativeTypeKinds.isInt32Type(opTy)) {
            sb.append("    cmpl %eax, %ecx\n");
        } else {
            sb.append("    cmpq %rax, %rcx\n");
        }
        sb.append("    ").append(cond).append(" ").append(resolveLabel(kc.trueLabel())).append("\n");
        sb.append("    jmp ").append(resolveLabel(kc.falseLabel())).append("\n");
    }

    private void emitCall(StringBuilder sb, KofCall kc) {
        if ("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName())) {

            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "println".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (argType instanceof Type.PrimitiveType pt && ("int".equals(pt.name()) || "char".equals(pt.name())
                    || "long".equals(pt.name()) || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_int\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println_string\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_println\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "print".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (BuiltinTypes.isString(argType)) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print_string\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print\n");
            }
            return;
        }
        if (NativeX86StringCalls.emit(sb, kc)) return;
        // CONC001: spawn/await no Native
        if ("kof_spawn".equals(kc.methodName()) || "kof_spawn_result".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            if ("kof_spawn_result".equals(kc.methodName())) sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_await".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_await\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // CONC001 (residual): done/poll não-bloqueantes — 1 arg (handle), valor em rax
        if ("kof_done".equals(kc.methodName()) || "kof_poll".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // cancel(handle) -> bool; selectAny(list) -> valor pronto
        if ("kof_cancel".equals(kc.methodName()) || "kof_select_any".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(kc.methodName()).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_cancelled".equals(kc.methodName())) {
            sb.append("    call kof_cancelled\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // awaitTimeout(handle, ms): 2 args (handle em rdi, ms em esi); valor em rax
        if ("kof_await_timeout".equals(kc.methodName())) {
            sb.append("    popq %r12\n");
            sb.append("    popq %rdi\n");
            sb.append("    movl %r12d, %esi\n");
            sb.append("    call kof_await_timeout\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_spawn_join_all".equals(kc.methodName())) {
            sb.append("    call kof_spawn_join_all\n");
            return;
        }
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(kc.methodName())) {
            Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
            if (argType instanceof Type.PrimitiveType pt && "char".equals(pt.name())) {
                // char → string UTF-8 (kof_int_to_string imprimia o
                // número do codepoint: String.valueOf(0xE9 as Char)
                // devolvia "233" em vez de "é")
                sb.append("    popq %rdi\n");
                sb.append("    call kof_char_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && ("int".equals(pt.name())
                    || "short".equals(pt.name()) || "byte".equals(pt.name()))) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_int_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && "long".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_long_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && "bool".equals(pt.name())) {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_bool_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_float_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && NativeTypeKinds.isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_double_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.ClassType ct && !BuiltinTypes.isString(argType)) {
                // valueOf(objeto) → obj.toString() via vtable (records têm
                // toString no IR; String é identity). Paridade com o JVM.
                int tosIdx = findVirtualMethodIndex(ct.name(), "toString");
                if (tosIdx >= 0) {
                    sb.append("    popq %rax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    movq 8(%rax), %rbx\n");
                    sb.append("    addq $").append(tosIdx * 8).append(", %rbx\n");
                    sb.append("    movq (%rbx), %rbx\n");
                    sb.append("    popq %rdi\n");
                    sb.append("    call *%rbx\n");
                    sb.append("    pushq %rax\n");
                }
            }
            return;
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            int stackArgs = Math.max(0, argCount - 5);
            if (stackArgs > 0) {
                // Save stack args to local frame (high offsets to avoid collision)
                for (int s = stackArgs - 1; s >= 0; s--) {
                    int off = 256 + s * 8;
                    sb.append("    popq %rax\n");
                    sb.append("    movq %rax, -").append(off).append("(%rbp)\n");
                }
                // Pop 5 register args
                for (int i = 4; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                // Pop this
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                // Push stack args back (arg6 por último → fica no topo da
                // stack → 16(%rbp) no callee; SysV exige essa ordem)
                for (int s = stackArgs - 1; s >= 0; s--) {
                    int off = 256 + s * 8;
                    sb.append("    pushq -").append(off).append("(%rbp)\n");
                }
            } else {
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
            }
            String ctorLabel = resolveCalleeName(kc);
            sb.append("    call ").append(ctorLabel).append("\n");
            if (stackArgs > 0) {
                // callee é caller-clean: remove os stack args empilhados de
                // volta. O pop subsequente do consumidor desempilha o push
                // duplicado do receptor (mesmo contrato do caminho <=5 args).
                sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
            }
            return;
        }

        if (kc.kind() == KofCallKind.INSTANCE
                && (BuiltinTypes.isMap(kc.ownerType()) || BuiltinTypes.isSet(kc.ownerType()))) {
            String collFn = (kc.methodName().startsWith("kof_map_") || kc.methodName().startsWith("kof_set_"))
                    ? kc.methodName() : null;
            if (collFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(collFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isChannel(kc.ownerType())) {
            // Canais: receiver (Channel) + args na pilha; asm: chan=rdi, value=rsi.
            String chFn = kc.methodName().startsWith("kof_channel_") ? kc.methodName() : null;
            if (chFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(chFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isList(kc.ownerType())) {
            String listFn = kc.methodName().startsWith("kof_list_") ? kc.methodName() : null;
            if (listFn != null) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call ").append(listFn).append("\n");
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INSTANCE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = findVirtualMethodIndex(ct.name(), kc.methodName());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    // salva stack args em slots do frame (ordem: argN no slot alto)
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    popq %r10\n");
                        sb.append("    movq %r10, -").append(off).append("(%rbp)\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    // arg6 por último → topo → 16(%rbp) no callee
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    pushq -").append(off).append("(%rbp)\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }
        if (kc.kind() == KofCallKind.INTERFACE && kc.ownerType() instanceof Type.ClassType ct) {
            int vtableIdx = findVirtualMethodIndex(ct.name(), kc.methodName());
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
                int stackArgs = Math.max(0, argCount - 5);
                if (stackArgs > 0) {
                    // salva stack args em slots do frame (ordem: argN no slot alto)
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    popq %r10\n");
                        sb.append("    movq %r10, -").append(off).append("(%rbp)\n");
                    }
                }
                for (int i = Math.min(argCount, 5) - 1; i >= 0; i--) {
                    sb.append("    popq ").append(intRegs[i + 1]).append("\n");
                }
                sb.append("    popq %rax\n");
                sb.append("    movq %rax, %rdi\n");
                if (stackArgs > 0) {
                    // arg6 por último → topo → 16(%rbp) no callee
                    for (int s = stackArgs - 1; s >= 0; s--) {
                        int off = 256 + s * 8;
                        sb.append("    pushq -").append(off).append("(%rbp)\n");
                    }
                }
                sb.append("    movq 8(%rax), %rbx\n");
                sb.append("    addq $").append(vtableIdx * 8).append(", %rbx\n");
                sb.append("    movq (%rbx), %rbx\n");
                sb.append("    call *%rbx\n");
                if (stackArgs > 0) {
                    sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
                }
                if (!Type.isVoid(kc.returnType())) {
                    sb.append("    pushq %rax\n");
                }
                return;
            }
        }

        int argCount = kc.parameterTypes().size();
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        int stackArgs = Math.max(0, argCount - 6);
        if (stackArgs > 0) {
            sb.append("    addq $").append(stackArgs * 8).append(", %rsp\n");
        }
        for (int i = 5; i >= 0; i--) {
            if (i < argCount) {
                sb.append("    popq ").append(intRegs[i]).append("\n");
            }
        }
        String callee = resolveCalleeName(kc);
        sb.append("    call ").append(callee).append("\n");
        if (!Type.isVoid(kc.returnType())) {
            sb.append("    pushq %rax\n");
        }
    }

    private String resolveCalleeName(KofCall kc) {
        // builtins de coleção são símbolos globais do runtime — nunca
        // mangle com o dono (Map_kof_map_put etc.)
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_")) {
            return mn;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            return functionMangleMap.getOrDefault(kc.methodName(), sanitizeName(kc.methodName()));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            if (kc.ownerType() instanceof Type.ClassType ct) {
                return classTypeManglePrefix(ct) + "_" + sanitizeName("<init>") + "_" + kc.parameterTypes().size();
            }
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + kc.methodName();
            return functionMangleMap.getOrDefault(key,
                    classTypeManglePrefix(ct) + "_" + sanitizeName(kc.methodName()));
        }
        return sanitizeName(kc.methodName());
    }

    /**
     * Prefixo de mangle de uma classe com PACKAGE: o call site precisa do
     * internal name (com/acme/User → com_acme_User), não do nome simples
     * (User) — senão `C()` de uma classe importada vira undefined reference
     * `C_init_0` (a definição usa clazz.name()). Bug 22.
     */
    private String classTypeManglePrefix(Type.ClassType ct) {
        String internal = ct.packageName() != null && !ct.packageName().isEmpty()
                ? ct.packageName().replace('.', '/') + "/" + ct.name()
                : ct.name();
        return sanitizeName(internal);
    }

    private int resolveFieldOffset(Type ownerType, String fieldName) {
        ClassLayout layout = getLayoutForType(ownerType);
        if (layout != null) {
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        if (currentClass != null) {
            layout = getLayout(currentClass);
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        return ClassLayout.HEADER_SIZE;
    }

    private void emitStart(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        // grava o TID do main thread (SYS_gettid=186) — limita GC ao main
        sb.append("    movq $186, %rax\n");
        sb.append("    syscall\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (mainHasArgs) {
            // N3: passa array vazio — evita segfault ao tratar argc como ponteiro
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        sb.append("    call ").append(sanitizeName(clazz.name())).append("_main\n");
        // M32.3: SYS_exit_group (231) — SYS_exit (60) só mata a thread
        // chamadora; com threads do driver Vulkan o processo fica pendurado.
        sb.append("    movq $231, %rax\n");
        sb.append("    xorq %rdi, %rdi\n");
        sb.append("    syscall\n");
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

    private void assemble(Path asmFile, Path binFile) throws IOException {
        Path objFile = asmFile.resolveSibling(asmFile.getFileName() + ".o");
        System.err.println("NativeBackend: assembling " + asmFile);
        try {
            runCommand(new String[]{"as", "-o", objFile.toString(), asmFile.toString()}, "as");
        } catch (IOException e) {
            System.err.println("NativeBackend: as failed: " + e.getMessage());
            throw e;
        }
        // Native always needs dynamic linker + libc now (printf for float, db optionally)
        // to keep single codegen path; plain integer programs still work via ld+ld.so.
        boolean needsDynamic = true;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (needsDynamic && os.contains("linux")) {
            java.util.List<String> cmdL = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "ld", "-o", binFile.toString(), objFile.toString(),
                    "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc"));
            if (usesDb) {
                cmdL.add(usesMysql ? "-l:libsqlite3.so.0" : "-l:libsqlite3.so.0");
                if (usesMysql) cmdL.add("-l:libmariadb.so.3");
            }
            if (usesConcurrency) cmdL.add("-l:libpthread.so.0");
            runCommand(cmdL.toArray(new String[0]), "ld");
        } else {
            if (usesDb) {
                String os2 = System.getProperty("os.name", "").toLowerCase();
                if (os2.contains("linux")) {
                    String[] extra = usesMysql
                            ? new String[]{"-l:libsqlite3.so.0", "-l:libmariadb.so.3"}
                            : new String[]{"-l:libsqlite3.so.0"};
                    String[] cmd = new String[7 + extra.length];
                    cmd[0] = "ld"; cmd[1] = "-o"; cmd[2] = binFile.toString(); cmd[3] = objFile.toString();
                    cmd[4] = "-dynamic-linker"; cmd[5] = "/lib64/ld-linux-x86-64.so.2"; cmd[6] = "-lc";
                    System.arraycopy(extra, 0, cmd, 7, extra.length);
                    runCommand(cmd, "ld");
                } else {
                    runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
                }
            } else {
                runCommand(new String[]{"ld", "-o", binFile.toString(), objFile.toString()}, "ld");
            }
        }
        Files.deleteIfExists(objFile);
        if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
        binFile.toFile().setExecutable(true);
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

    private void emitRiscv(IRModule module, Path outputDir) throws IOException {
        labelCounter = 0;
        labelMap.clear();
        stringLiterals.clear();
        stringCounter = 0;
        allClassesMap.clear();
        for (IRClass c : module.classes()) allClassesMap.put(c.name(), c);

        IRClass mainClass = null;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("main".equals(m.name())) { mainClass = c; break; }
            }
            if (mainClass != null) break;
        }
        // pré-registro do mangle de TODOS os métodos (forward reference de
        // função top-level — idem x86_64; sem isso `fib` cai no fallback
        // não-mangled e o ld falha).
        functionMangleMap.clear();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                String mg = sanitizeName(c.name()) + "_" + sanitizeName(m.name());
                if ("<init>".equals(m.name())) mg += "_" + m.parameterTypes().size();
                functionMangleMap.putIfAbsent(m.name(), mg);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(".option arch, rv64g\n");
        sb.append(".section .data\n");
        for (IRClass c : module.classes()) {
            currentClass = c;
            collectStrings(c);
        }
        for (String[] e : stringLiterals) {
            String esc = e[0].replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t");
            sb.append(e[1]).append(": .asciz \"").append(esc).append("\"\n");
        }
        // kof_super_table: pares (typeId, superTypeId) terminados por (0,0) —
        // usado por kof_instanceof (mesmo layout do x86_64).
        sb.append(".align 4\n");
        sb.append("kof_super_table:\n");
        for (IRClass c : module.classes()) {
            if (c.typeId() == 0) continue;
            int superTypeId = 0;
            if (c.superName() != null && !c.superName().isEmpty()) {
                String superSimple = c.superName().substring(c.superName().lastIndexOf('/') + 1);
                for (IRClass other : module.classes()) {
                    if (other.name().equals(c.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            sb.append("    .word ").append(c.typeId()).append(", ").append(superTypeId).append("\n");
        }
        sb.append("    .word 0, 0\n");
        // vtables por classe (offset 8 do header aponta para elas)
        for (IRClass c : module.classes()) {
            currentClass = c;
            crossEmit().emitMethodTableRiscv(sb, c);
        }
        sb.append(".section .text\n");
        // pop <reg>: desempilha o topo da pilha de operandos (sp) em <reg>
        sb.append(".macro pop r\n");
        sb.append("    ld \\r, 0(sp)\n");
        sb.append("    addi sp, sp, 8\n");
        sb.append(".endm\n");
        boolean usesSpawn = usesSpawn(module);
        for (IRClass c : module.classes()) {
            currentClass = c;
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                crossEmit().emitCrossMethodRiscv(sb, c, m, usesSpawn && "main".equals(m.name()));
            }
        }

        // Ponto de entrada: chama <mainClass>_main e sai via exit_group(94).
        // O runtime é asm puro — binário estático. exit_group (não exit/93)
        // mata as threads do scheduler que não foram canceladas (daemon-like)
        // — senão o processo fica pendurado esperando a thread do timer.
        String mainEntry = mainClass != null ? sanitizeName(mainClass.name()) + "_main" : "kof_main";
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        sb.append("    andi sp, sp, -16\n");
        sb.append("    call ").append(mainEntry).append("\n");
        sb.append("    li a0, 0\n");
        sb.append("    li a7, 94\n");
        sb.append("    ecall\n");
        sb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);

        // NATIVE002-stdlib: http.get/post/status riscv64 (asm puro, syscalls
        // asm-generic — mesmos números do aarch64; aarch64 herda via tradutor).
        boolean usesHttp = false;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_http_")) {
                            usesHttp = true;
                        }
                    }
                }
                if (usesHttp) break;
            }
            if (usesHttp) break;
        }
        if (usesHttp) emitRiscvHttp(sb);
        if (usesSpawn) emitRiscvSpawn(sb);

        String className = module.classes().isEmpty() ? "Default/Main" : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path binFile = outputDir.resolve(className);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        System.err.println("NativeBackend: generated riscv64 " + asmFile);

        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            // --no-relax (as+ld): sem gp-relaxation. Nosso _start não inicializa
            // gp (binário estático, sem C runtime); `la` relaxado vira `addi rd,gp,off`
            // e faulta (gp=0). Forçado PC-relative (auipc+addi) — sempre correto.
            runCommand(new String[]{"riscv64-linux-gnu-as", "-mno-relax", "-o", objFile.toString(), asmFile.toString()}, "riscv64-as");
            runCommand(new String[]{"riscv64-linux-gnu-ld", "--no-relax", "-o", binFile.toString(), objFile.toString()}, "riscv64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (ToolchainMissing e) {
            // toolchain ausente: gracioso (assumeToolchain pula o teste)
            System.err.println("NativeBackend: riscv64 toolchain ausente (NATIVE002), keeping asm: " + e.getMessage());
        }
        // as/ld FALHOU (ex.: undefined reference) → propaga como erro de
        // compilação (R6: nunca success=true sem binário).
    }

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
        NativeRiscvSpawn.emit(sb);
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

    private void emitAarch64(IRModule module, Path outputDir) throws IOException {
        // AArch64 = tradução linha-a-linha do riscv64 (mesmo modelo de pilha/layout).
        // Gera o asm riscv64 em memória via lowering já validado e traduz p/ ARMv8-A.
        labelCounter = 0;
        labelMap.clear();
        stringLiterals.clear();
        stringCounter = 0;
        allClassesMap.clear();
        for (IRClass c : module.classes()) allClassesMap.put(c.name(), c);
        IRClass mainClass = null;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("main".equals(m.name())) { mainClass = c; break; }
            }
            if (mainClass != null) break;
        }
        functionMangleMap.clear();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                String mg = sanitizeName(c.name()) + "_" + sanitizeName(m.name());
                if ("<init>".equals(m.name())) mg += "_" + m.parameterTypes().size();
                functionMangleMap.putIfAbsent(m.name(), mg);
            }
        }
        StringBuilder riscvSb = new StringBuilder();
        riscvSb.append(".option arch, rv64g\n");
        riscvSb.append(".section .data\n");
        for (IRClass c : module.classes()) {
            currentClass = c;
            collectStrings(c);
        }
        for (String[] e : stringLiterals) {
            String esc = e[0].replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t");
            riscvSb.append(e[1]).append(": .asciz \"").append(esc).append("\"\n");
        }
        riscvSb.append(".align 4\n");
        riscvSb.append("kof_super_table:\n");
        for (IRClass c : module.classes()) {
            if (c.typeId() == 0) continue;
            int superTypeId = 0;
            if (c.superName() != null && !c.superName().isEmpty()) {
                String superSimple = c.superName().substring(c.superName().lastIndexOf('/') + 1);
                for (IRClass other : module.classes()) {
                    if (other.name().equals(c.superName()) || other.name().endsWith("/" + superSimple)
                            || superSimple.equals(sanitizeName(other.name()))) {
                        superTypeId = other.typeId();
                        break;
                    }
                }
            }
            riscvSb.append("    .word ").append(c.typeId()).append(", ").append(superTypeId).append("\n");
        }
        riscvSb.append("    .word 0, 0\n");
        for (IRClass c : module.classes()) {
            currentClass = c;
            crossEmit().emitMethodTableRiscv(riscvSb, c);
        }
        riscvSb.append(".section .text\n");
        riscvSb.append(".macro pop r\n");
        riscvSb.append("    ld \\r, 0(sp)\n");
        riscvSb.append("    addi sp, sp, 8\n");
        riscvSb.append(".endm\n");
        boolean usesSpawnA = usesSpawn(module);
        for (IRClass c : module.classes()) {
            currentClass = c;
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) continue;
                crossEmit().emitCrossMethodRiscv(riscvSb, c, m, usesSpawnA && "main".equals(m.name()));
            }
        }
        String mainEntry = mainClass != null ? sanitizeName(mainClass.name()) + "_main" : "kof_main";
        riscvSb.append("\n.globl _start\n");
        riscvSb.append("_start:\n");
        riscvSb.append("    andi sp, sp, -16\n");
        riscvSb.append("    call ").append(mainEntry).append("\n");
        riscvSb.append("    li a0, 0\n");
        riscvSb.append("    li a7, 93\n");
        riscvSb.append("    ecall\n");
        riscvSb.append(NativeRiscvAsm.RISCV_RUNTIME_ASM).append(NativeRiscvAsm.RISCV_STRN002_ASM).append(NativeRiscvAsm.RISCV_RUNTIME_ASM_B).append(NativeRiscvAsm.RISCV_MAPSET_ASM);

        // NATIVE002-stdlib: http riscv64 → aarch64 (traduzido). Mesma detecção
        // de uso do emitRiscv; o aarch64 herda linha-a-linha do riscv64.
        boolean usesHttpA = false;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_http_")) {
                            usesHttpA = true;
                        }
                    }
                }
                if (usesHttpA) break;
            }
            if (usesHttpA) break;
        }
        if (usesHttpA) emitRiscvHttp(riscvSb);
        if (usesSpawnA) emitRiscvSpawn(riscvSb);

        // traduz linha-a-linha
        StringBuilder sb = new StringBuilder();
        for (String line : riscvSb.toString().split("\n", -1)) {
            List<String> tr = NativeAarch64Translator.translateRiscvToAarch64(line);
            for (String t : tr) sb.append(t).append("\n");
        }
        String className = module.classes().isEmpty() ? "Default/Main" : module.classes().getFirst().name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path binFile = outputDir.resolve(className);
        Files.createDirectories(asmFile.getParent());
        Files.writeString(asmFile, sb.toString());
        System.err.println("NativeBackend: generated aarch64 " + asmFile);
        try {
            Path objFile = asmFile.resolveSibling("kof.o");
            runCommand(new String[]{"aarch64-linux-gnu-as", "-o", objFile.toString(), asmFile.toString()}, "aarch64-as");
            runCommand(new String[]{"aarch64-linux-gnu-ld", "-o", binFile.toString(), objFile.toString()}, "aarch64-ld");
            Files.deleteIfExists(objFile);
            if (System.getenv("KOF_KEEP_ASM") == null) Files.deleteIfExists(asmFile);
            binFile.toFile().setExecutable(true);
        } catch (ToolchainMissing e) {
            System.err.println("NativeBackend: aarch64 toolchain ausente (NATIVE002), keeping asm: " + e.getMessage());
        }
        // as/ld FALHOU → propaga como erro de compilação (R6).
    }


    /** Toolchain ausente (binário não encontrado) — gracioso: mantém asm,
     *  assumeToolchain() pula o teste. NÃO confundir com falha de as/ld. */
    static final class ToolchainMissing extends IOException {
        ToolchainMissing(String m) { super(m); }
    }

    private void runCommand(String[] cmd, String name) throws IOException {
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            throw new ToolchainMissing(name + " not available: " + e.getMessage());
        }
        try {
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new IOException(name + " failed (exit " + p.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " interrupted");
        }
    }
}

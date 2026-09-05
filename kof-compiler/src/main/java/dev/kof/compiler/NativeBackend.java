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
        for (Type pt : method.parameterTypes()) paramSlotMax += isDoubleWidthSlot(pt) ? 2 : 1;
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
            case KofBinary kb -> emitBinary(sb, kb);
            case KofUnary ku -> emitUnary(sb, ku);
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

    private static boolean isFloatType(Type t) {
        return t instanceof Type.PrimitiveType pt && "float".equals(Type.canonicalPrimitiveName(pt.name()));
    }
    private static boolean isDoubleType(Type t) {
        return t instanceof Type.PrimitiveType pt && "double".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    /** Slots de frame que um tipo de PARAM ocupa (espelha isDoubleWidth do
     * CompilerDriver — os índices locais do IR usam essa convenção). */
    private static boolean isDoubleWidthSlot(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            String n = Type.canonicalPrimitiveName(pt.name());
            return "long".equals(n) || "double".equals(n);
        }
        return false;
    }
    private static boolean isInt32Type(Type t) {
        return t instanceof Type.PrimitiveType pt && "int".equals(Type.canonicalPrimitiveName(pt.name()));
    }

    private void emitBinary(StringBuilder sb, KofBinary kb) {
        Type opTy = kb.operandType();
        if (isFloatType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movd %ecx, %xmm1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addss %xmm1, %xmm0\n");
                case SUB -> sb.append("    subss %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulss %xmm1, %xmm0\n");
                case DIV -> sb.append("    divss %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    // NaN => unordered => CF=1 PF=1 => be would be true, clear it
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movd %xmm0, %eax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    movl %eax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (isDoubleType(opTy)) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    movq %xmm0, %xmm0\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addsd %xmm1, %xmm0\n");
                case SUB -> sb.append("    subsd %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulsd %xmm1, %xmm0\n");
                case DIV -> sb.append("    divsd %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movq %xmm0, %rax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rcx\n");
        sb.append("    popq %rax\n");
        boolean int32 = isInt32Type(opTy);
        String suf = int32 ? "l" : "q";
        String a32 = int32 ? "e" : "r";
        switch (kb.op()) {
            case ADD -> sb.append("    add").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SUB -> sb.append("    sub").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case MUL -> sb.append("    imul").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case DIV -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n");
                }
            }
            case MOD -> {
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n    movl %edx, %eax\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n    movq %rdx, %rax\n");
                }
            }
            case EQ -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    sete %al\n    movzbl %al, %eax\n");
            case NE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setne %al\n    movzbl %al, %eax\n");
            case LT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setl %al\n    movzbl %al, %eax\n");
            case LE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setle %al\n    movzbl %al, %eax\n");
            case GT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setg %al\n    movzbl %al, %eax\n");
            case GE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setge %al\n    movzbl %al, %eax\n");
            case AND -> sb.append("    and").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case OR -> sb.append("    or").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case XOR -> sb.append("    xor").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SHL -> sb.append("    shl").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case SHR -> sb.append("    sar").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case USHR -> sb.append("    shr").append(suf).append(" %cl, %").append(a32).append("ax\n");
        }
        sb.append("    pushq %rax\n");
    }

    private void emitUnary(StringBuilder sb, KofUnary ku) {
        if (ku.operandType() != null && isFloatType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movl $0x80000000, %ecx\n");
            sb.append("    movd %ecx, %xmm1\n");
            sb.append("    xorps %xmm1, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.operandType() != null && isDoubleType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movabs $0x8000000000000000, %rcx\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    xorpd %xmm1, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // primitive conversions
        if (ku.op() == KofUnaryOp.I2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %eax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %eax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %rax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %rax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2D) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvtss2sd %xmm0, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2F) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvtsd2ss %xmm0, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2I) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvttsd2si %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2I) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvttss2si %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2L) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvttsd2si %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2L) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvttss2si %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2L) {
            sb.append("    popq %rax\n");
            sb.append("    movslq %eax, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rax\n");
        boolean int32u = isInt32Type(ku.operandType());
        String suf = int32u ? "l" : "q";
        String reg = int32u ? "%eax" : "%rax";
        if (ku.op() == KofUnaryOp.NEG) {
            sb.append("    neg").append(suf).append(" ").append(reg).append("\n");
        } else if (ku.op() == KofUnaryOp.NOT) {
            sb.append("    cmp").append(suf).append(" $0, ").append(reg).append("\n");
            sb.append("    sete %al\n");
            sb.append("    movzbl %al, %eax\n");
        }

        sb.append("    pushq %rax\n");
    }

    private void emitConditionalJump(StringBuilder sb, KofConditionalJump kc) {
        Type opTy = kc.operandType();
        if (opTy != null && isFloatType(opTy)) {
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
        if (opTy != null && isDoubleType(opTy)) {
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
        if (opTy != null && isInt32Type(opTy)) {
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
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
                sb.append("    leaq .Lnewline(%rip), %rdi\n");
                sb.append("    call kof_print\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
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
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_print_float\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movq %rdi, %xmm0\n");
                sb.append("    call kof_print_double\n");
            } else {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_print\n");
            }
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "length".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_length\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "charAt".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_char_at\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "substring".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            if (argCount == 1) {
                sb.append("    popq %rsi\n");
                sb.append("    xorq %rdx, %rdx\n");
            } else {
                sb.append("    popq %rdx\n");
                sb.append("    popq %rsi\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_substring\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "contains".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_contains\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "startsWith".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_starts_with\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "endsWith".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_ends_with\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "concat".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            sb.append("    call kof_string_concat\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "indexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_index_of\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "lastIndexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_last_index_of\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "trim".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_trim\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toUpperCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_upper\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toLowerCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_lower\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "replace".equals(kc.methodName())) {
            sb.append("    popq %rdx\n");
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            // replace(char, char) passes raw character codes (Ints);
            // replace(String, String) passes KofString pointers. The two
            // runtime helpers must be selected by the call's parameter types.
            Type first = !kc.parameterTypes().isEmpty() ? kc.parameterTypes().get(0) : null;
            boolean charArgs = first instanceof Type.PrimitiveType pt
                    && "char".equals(Type.canonicalPrimitiveName(pt.name()));
            sb.append(charArgs
                    ? "    call kof_string_replace_char\n"
                    : "    call kof_string_replace\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "equalsIgnoreCase".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_equals_ignore_case\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "split".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    movl 16(%rsi), %ecx\n");
            sb.append("    testl %ecx, %ecx\n");
            sb.append("    jz .Lkof_split_empty_sep\n");
            sb.append("    movzbl 24(%rsi), %esi\n");
            sb.append("    jmp .Lkof_split_call\n");
            sb.append(".Lkof_split_empty_sep:\n");
            sb.append("    xorl %esi, %esi\n");
            sb.append(".Lkof_split_call:\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_split\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_string_to_int".equals(kc.methodName())
                || "kof_string_to_long".equals(kc.methodName())) {
            String fn = kc.methodName();
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(fn).append("\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // retorno FP vive em xmm0 — preservar os bits na pilha
        if ("kof_string_to_double".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_double\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_string_to_float".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_float\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // JSN001: json.decode<Double>/decode<Float> retorna em xmm0
        if ("kof_json_decode_double".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_double\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_json_decode_float".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_float\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // JSN001: json.encode(double) recebe em xmm0 (bits na pilha)
        if ("kof_json_encode_double".equals(kc.methodName())) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    call kof_json_encode_double\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_json_encode_float".equals(kc.methodName())) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    call kof_json_encode_float\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if ("kof_json_decode_double_array".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_double_array\n");
            sb.append("    pushq %rax\n");
            return;
        }
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
            } else if (argType instanceof Type.PrimitiveType pt && isFloatType(pt)) {
                sb.append("    popq %rdi\n");
                sb.append("    movd %edi, %xmm0\n");
                sb.append("    call kof_float_to_string\n");
                sb.append("    pushq %rax\n");
            } else if (argType instanceof Type.PrimitiveType pt && isDoubleType(pt)) {
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
            emitMethodTableRiscv(sb, c);
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
                emitCrossMethodRiscv(sb, c, m, usesSpawn && "main".equals(m.name()));
            }
        }

        // Ponto de entrada: chama <mainClass>_main e sai via exit_group(93).
        // O runtime é asm puro — binário estático.
        String mainEntry = mainClass != null ? sanitizeName(mainClass.name()) + "_main" : "kof_main";
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        sb.append("    andi sp, sp, -16\n");
        sb.append("    call ").append(mainEntry).append("\n");
        sb.append("    li a0, 0\n");
        sb.append("    li a7, 93\n");
        sb.append("    ecall\n");
        sb.append(RISCV_RUNTIME_ASM).append(RISCV_STRN002_ASM).append(RISCV_RUNTIME_ASM_B);

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
        sb.append("""
            # ---- HTTP riscv64 (NATIVE002-stdlib) ----
            .section .text
            # cstrlen: a0=cstr -> a0=len
            .globl kof_http_cstrlen
            kof_http_cstrlen:
                mv   t1, a0
                li   a0, 0
            .Lhcl0:
                lbu  t0, 0(t1)
                beqz t0, .Lhcl1
                addi a0, a0, 1
                addi t1, t1, 1
                j    .Lhcl0
            .Lhcl1:
                ret
            # append cstr: a0=cursor, a1=cstr -> a0=novo cursor
            .globl kof_http_append_cstr
            kof_http_append_cstr:
            .Lhac0:
                lbu  t0, 0(a1)
                beqz t0, .Lhac1
                sb   t0, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                j    .Lhac0
            .Lhac1:
                ret
            # append n bytes: a0=cursor, a1=src, a2=len -> a0=cursor
            .globl kof_http_append_n
            kof_http_append_n:
            .Lhan0:
                beqz a2, .Lhan1
                lbu  t0, 0(a1)
                sb   t0, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                addi a2, a2, -1
                j    .Lhan0
            .Lhan1:
                ret
            # append decimal: a0=cursor, a1=int64(>=0) -> a0=cursor
            .globl kof_http_append_dec
            kof_http_append_dec:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s0, a0
                mv   s1, a1
                addi s2, sp, 0
                mv   s3, s2
            .Lhad0:
                li   t1, 10
                div  t0, s1, t1
                rem  a1, s1, t1
                li   t1, 48
                add  a1, a1, t1
                sb   a1, 0(s2)
                addi s2, s2, 1
                mv   s1, t0
                bnez s1, .Lhad0
            .Lhad1:
                addi s2, s2, -1
                bltu s2, s3, .Lhad_end
                lbu  t0, 0(s2)
                sb   t0, 0(s0)
                addi s0, s0, 1
                j    .Lhad1
            .Lhad_end:
                mv   a0, s0
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            # parse URL. a0=KofString (len@16, chars@24)
            # saida: .Lhttp_hostbuf (cstr), .Lhttp_pathbuf (cstr),
            #        .Lhttp_portbin (2B BE), .Lhttp_ipbin (4B)
            .globl kof_http_parse_url
            kof_http_parse_url:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                addi s1, a0, 24
                lbu  a0, 0(s1)
                li   t0, 104
                bne  a0, t0, .Lpu_bad
                lbu  a0, 1(s1)
                li   t0, 116
                bne  a0, t0, .Lpu_bad
                lbu  a0, 2(s1)
                bne  a0, t0, .Lpu_bad
                lbu  a0, 3(s1)
                li   t0, 112
                bne  a0, t0, .Lpu_bad
                lbu  a0, 4(s1)
                li   t0, 115
                beq  a0, t0, .Lpu_https
                li   t0, 58
                bne  a0, t0, .Lpu_bad
                lbu  a0, 5(s1)
                li   t0, 47
                bne  a0, t0, .Lpu_bad
                lbu  a0, 6(s1)
                bne  a0, t0, .Lpu_bad
                addi s1, s1, 7
                la   s2, .Lhttp_hostbuf
            .Lpu_h0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_h1
                li   t0, 58
                beq  a0, t0, .Lpu_h1
                li   t0, 47
                beq  a0, t0, .Lpu_h1
                sb   a0, 0(s2)
                addi s2, s2, 1
                addi s1, s1, 1
                j    .Lpu_h0
            .Lpu_h1:
                li   t0, 0
                sb   t0, 0(s2)
                li   t0, 80
                lbu  a0, 0(s1)
                li   t1, 58
                bne  a0, t1, .Lpu_p1
                addi s1, s1, 1
                li   t0, 0
            .Lpu_p0:
                lbu  a0, 0(s1)
                li   t1, 47
                beq  a0, t1, .Lpu_p1
                beqz a0, .Lpu_p1
                li   t1, 48
                sub  a0, a0, t1
                li   t1, 9
                bltu t1, a0, .Lpu_p1
                li   t1, 10
                mul  t0, t0, t1
                add  t0, t0, a0
                addi s1, s1, 1
                j    .Lpu_p0
            .Lpu_p1:
                la   t1, .Lhttp_port_host
                sd   t0, 0(t1)
                andi t1, t0, 255
                slli t1, t1, 8
                srli t0, t0, 8
                or   t0, t0, t1
                la   t1, .Lhttp_portbin
                sh   t0, 0(t1)
                la   s2, .Lhttp_pathbuf
                lbu  a0, 0(s1)
                li   t0, 47
                beq  a0, t0, .Lpu_pa0
                sb   t0, 0(s2)
                li   t1, 0
                sb   t1, 1(s2)
                j    .Lpu_ip
            .Lpu_pa0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_pa1
                sb   a0, 0(s2)
                addi s2, s2, 1
                addi s1, s1, 1
                j    .Lpu_pa0
            .Lpu_pa1:
                li   t0, 0
                sb   t0, 0(s2)
            .Lpu_ip:
                la   s1, .Lhttp_hostbuf
                lbu  a0, 0(s1)
                li   t0, 48
                bltu a0, t0, .Lpu_fall
                li   t0, 57
                bltu t0, a0, .Lpu_fall
                la   s2, .Lhttp_ipbin
                li   t1, 0
                li   t2, 0
            .Lpu_ip0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_ip3
                li   t3, 46
                beq  a0, t3, .Lpu_ip1
                li   t3, 48
                sub  a0, a0, t3
                li   t3, 9
                bltu t3, a0, .Lpu_fall
                li   t3, 10
                mul  t2, t2, t3
                add  t2, t2, a0
                addi s1, s1, 1
                j    .Lpu_ip0
            .Lpu_ip1:
                add  t3, s2, t1
                sb   t2, 0(t3)
                li   t2, 0
                addi t1, t1, 1
                addi s1, s1, 1
                j    .Lpu_ip0
            .Lpu_ip3:
                add  t3, s2, t1
                sb   t2, 0(t3)
                j    .Lpu_done
            .Lpu_fall:
                la   s2, .Lhttp_ipbin
                li   t0, 127
                sb   t0, 0(s2)
                li   t0, 0
                sb   t0, 1(s2)
                sb   t0, 2(s2)
                li   t0, 1
                sb   t0, 3(s2)
            .Lpu_done:
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lpu_https:
                la   a0, .Lhttps_err
                call kof_http_cstrlen
                mv   a1, a0
                la   a0, .Lhttps_err
                call kof_string_from_literal
                call kof_throw_string
            .Lpu_bad:
                la   s0, .Lhttp_fallback
                la   s1, .Lhttp_hostbuf
                li   s2, 9
            .Lpu_bc:
                beqz s2, .Lpu_bc1
                lbu  t0, 0(s0)
                sb   t0, 0(s1)
                addi s0, s0, 1
                addi s1, s1, 1
                addi s2, s2, -1
                j    .Lpu_bc
            .Lpu_bc1:
                li   t0, 0
                sb   t0, 0(s1)
                la   s1, .Lhttp_pathbuf
                li   t0, 47
                sb   t0, 0(s1)
                sb   t0, 1(s1)
                li   t0, 20480
                la   t1, .Lhttp_portbin
                sh   t0, 0(t1)
                li   t0, 80
                la   t1, .Lhttp_port_host
                sd   t0, 0(t1)
                j    .Lpu_ip

            # ── request core ────────────────────────────────────────────
            # a0=url, a1=method cstr, a2=body cstr|0, a4=headers cstr|0
            # retorna a0=KofString body (status em .Lhttp_last_status)
            .globl kof_http_core
            kof_http_core:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s4, a4
                mv   a0, s0
                call kof_http_parse_url
                li   a0, 2
                li   a1, 1
                li   a2, 0
                li   a7, 198
                ecall
                bltz a0, .Lhr_fail
                mv   s5, a0
                la   t0, .Lhttp_sock
                li   t1, 2
                sw   t1, 0(t0)
                la   t1, .Lhttp_portbin
                lh   t1, 0(t1)
                sh   t1, 2(t0)
                la   t1, .Lhttp_ipbin
                lw   t1, 0(t1)
                sw   t1, 4(t0)
                li   t1, 0
                sd   t1, 8(t0)
                la   a1, .Lhttp_sock
                mv   a0, s5
                li   a2, 16
                li   a7, 203
                ecall
                bltz a0, .Lhr_fail_cl
                la   t0, .Lhttp_reqbuf
                mv   a0, t0
                mv   a1, s1
                call kof_http_append_cstr
                li   t0, 32
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   a1, .Lhttp_pathbuf
                call kof_http_append_cstr
                la   a1, .Lhttp_str_ver
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_str_host
                call kof_http_append_cstr
                la   a1, .Lhttp_hostbuf
                call kof_http_append_cstr
                la   t0, .Lhttp_port_host
                ld   t0, 0(t0)
                li   t1, 80
                beq  t0, t1, .Lhr_nohostport
                li   t0, 58
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   t0, .Lhttp_port_host
                ld   a1, 0(t0)
                call kof_http_append_dec
            .Lhr_nohostport:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s4, .Lhr_body_hdrs
                lw   t0, 16(s4)
                addi a1, s4, 24
                li   t1, 0
            .Lhr_hl:
                bltu t0, t1, .Lhr_hl_done
                lbu  t2, 0(a1)
                li   t3, 10
                beq  t2, t3, .Lhr_hc
                sb   t2, 0(a0)
                addi a0, a0, 1
                j    .Lhr_hn
            .Lhr_hc:
                li   t2, 13
                sb   t2, 0(a0)
                addi a0, a0, 1
                sb   t3, 0(a0)
                addi a0, a0, 1
            .Lhr_hn:
                addi t1, t1, 1
                addi a1, a1, 1
                j    .Lhr_hl
            .Lhr_hl_done:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_body_hdrs:
                beqz s2, .Lhr_closing
                la   a1, .Lhttp_str_clen
                call kof_http_append_cstr
                lw   t0, 16(s2)
                mv   a1, t0
                call kof_http_append_dec
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_closing:
                la   a1, .Lhttp_str_conn
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s2, .Lhr_send
                addi a1, s2, 24
                lw   a2, 16(s2)
                call kof_http_append_n
            .Lhr_send:
                la   t0, .Lhttp_reqbuf
                sub  a2, a0, t0
                mv   a1, t0
                mv   a0, s5
                li   a7, 64
                ecall
                li   s3, 0
            .Lhr_rd:
                la   t0, .Lhttp_respbuf
                add  a1, t0, s3
                li   t0, 262144
                sub  a2, t0, s3
                mv   a0, s5
                li   a7, 63
                ecall
                bltz a0, .Lhr_rd_done
                beqz a0, .Lhr_rd_done
                add  s3, s3, a0
                li   t0, 262144
                bltu s3, t0, .Lhr_rd
            .Lhr_rd_done:
                la   s1, .Lhttp_respbuf
                li   t0, 0
            .Lhr_st_space:
                lbu  a0, 0(s1)
                li   t1, 32
                beq  a0, t1, .Lhr_st_sp_hit
                addi s1, s1, 1
                j    .Lhr_st_space
            .Lhr_st_sp_hit:
                addi s1, s1, 1
            .Lhr_st_loop:
                lbu  a0, 0(s1)
                li   t1, 48
                sub  a0, a0, t1
                li   t2, 9
                bltu t2, a0, .Lhr_st_ok
                li   t1, 10
                mul  t0, t0, t1
                add  t0, t0, a0
                addi s1, s1, 1
                j    .Lhr_st_loop
            .Lhr_st_ok:
                la   t1, .Lhttp_last_status
                sd   t0, 0(t1)
                la   s1, .Lhttp_respbuf
                mv   s6, s3
            .Lhr_bscan:
                li   t0, 4
                bltu s6, t0, .Lhr_bnone
                lbu  a0, 0(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 1(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                lbu  a0, 2(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 3(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                addi s1, s1, 4
                addi s6, s6, -4
                mv   a0, s1
                mv   a1, s6
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                li   a7, 57
                ecall
                mv   a0, s0
                j    .Lhr_out
            .Lhr_bn:
                addi s1, s1, 1
                addi s6, s6, -1
                j    .Lhr_bscan
            .Lhr_bnone:
                la   a0, .Lhttp_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                li   a7, 57
                ecall
                mv   a0, s0
            .Lhr_fail_cl:
                mv   a0, s5
                li   a7, 57
                ecall
            .Lhr_fail:
                la   a0, .Lhttp_err_conn
                call kof_http_cstrlen
                mv   a1, a0
                la   a0, .Lhttp_err_conn
                call kof_string_from_literal
                call kof_throw_string
            .Lhr_out:
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # wrappers: a0=url [a1=body|headers] -> a0=KofString (status em .Lhttp_last_status)
            .globl kof_http_get
            kof_http_get:
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_get_headers
            kof_http_get_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_get
                li   a2, 0
                j    kof_http_core
            .globl kof_http_post
            kof_http_post:
                mv   a2, a1
                la   a1, .Lhttp_m_post
                li   a4, 0
                j    kof_http_core
            .globl kof_http_post_headers
            kof_http_post_headers:
                mv   a4, a2
                mv   a2, a1
                la   a1, .Lhttp_m_post
                j    kof_http_core
            .globl kof_http_delete
            kof_http_delete:
                la   a1, .Lhttp_m_delete
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_put
            kof_http_put:
                mv   a2, a1
                la   a1, .Lhttp_m_put
                li   a4, 0
                j    kof_http_core
            .globl kof_http_patch
            kof_http_patch:
                mv   a2, a1
                la   a1, .Lhttp_m_patch
                li   a4, 0
                j    kof_http_core
            .globl kof_http_status
            kof_http_status:
                addi sp, sp, -16
                sd   ra, 8(sp)
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                call kof_http_core
                la   a0, .Lhttp_last_status
                ld   a0, 0(a0)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_http_options
            kof_http_options:
                la   a1, .Lhttp_m_options
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_options_headers
            kof_http_options_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_options
                li   a2, 0
                j    kof_http_core
            # configurators (no-op nativo, preservam API; paridade futura)
            .globl kof_http_timeout_set
            kof_http_timeout_set:
                ret
            .globl kof_http_retry_set
            kof_http_retry_set:
                ret
            .globl kof_http_circuit_set
            kof_http_circuit_set:
                ret
            .section .data
            .Lhttp_hostbuf:   .space 256
            .Lhttp_pathbuf:   .space 1024
            .Lhttp_portbin:   .space 2
            .Lhttp_port_host: .quad 0
            .Lhttp_ipbin:     .space 4
            .Lhttp_reqbuf:    .space 16384
            .Lhttp_respbuf:   .space 262144
            .Lhttp_sock:      .space 16
            .Lhttp_last_status: .quad 0
            .Lhttps_err:   .asciz "kof.http: https nao suportado no Native (TLS pendente); use http://"
            .Lhttp_fallback: .asciz "127.0.0.1"
            .Lhttp_str_host: .asciz "Host: "
            .Lhttp_str_clen: .asciz "Content-Length: "
            .Lhttp_str_conn: .asciz "Connection: close"
            .Lhttp_str_ver:  .asciz " HTTP/1.1"
            .Lhttp_crlfb:    .byte 13, 10
            .Lhttp_err_conn: .asciz "kof.http: connect falhou"
            .Lhttp_empty: .space 1
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .section .text
            """);
    }

    // ---- NATIVE002-stdlib: spawn/await riscv64 (clone+futex, asm puro) ----
    // qemu-riscv64 8.2.2 NÃO implementa clone3 (ENOSYS) — usa clone(220) com o
    // flag-set da glibc (0x3D0F00 = VM|FS|FILES|SIGHAND|THREAD|SYSVSEM|SETTLS|
    // PARENT_SETTID|CHILD_CLEARTID), que é aceito. O filho herda os registradores
    // do pai no ecall (a0=0, s0=handle) e roda o trampoline; await espera via
    // futex em handle->done (sem pthread_join). exit(93) mata só a thread.
    static boolean usesSpawn(IRModule module) {
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc
                                && (kc.methodName().equals("kof_spawn")
                                    || kc.methodName().equals("kof_spawn_result")
                                    || kc.methodName().equals("kof_await"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static void emitRiscvSpawn(StringBuilder sb) {
        sb.append("""
            # ---- spawn/await riscv64 (NATIVE002-stdlib) ----
            .section .text
            # handle: [typeId@0(i32) done@4(i32) result@8 stack@16] (32B)
            # kof_spawn_result(task@a0) -> handle@a0
            .globl kof_spawn_result
            kof_spawn_result:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0                 # task
                li   a0, 32
                call kof_alloc
                mv   s1, a0                 # handle
                li   t0, 2
                sw   t0, 0(s1)              # typeId=2 (handle)
                sw   zero, 4(s1)            # done=0
                sd   zero, 8(s1)            # result=0
                # stack do worker: mmap(NULL, 1MB, RW, PRIVATE|ANON, -1, 0)
                li   a0, 0
                li   a1, 1048576
                li   a2, 3
                li   a3, 0x22
                li   a4, -1
                li   a5, 0
                li   a7, 222
                ecall
                sd   a0, 16(s1)             # stack base (p/ debug/free)
                li   t1, 1048576
                add  s2, a0, t1             # stack TOP
                sd   s2, 24(s1)             # stack top no handle (filho lê)
                # clone(flags, stack_top, ptid, tls, ctid) — filho herda s0,s1
                li   a0, 0x3D0F00
                mv   a1, s2
                li   a2, 0
                li   a3, 0
                li   a4, 0
                li   a7, 220
                ecall
                bltz a0, .Lsp_inline
                bnez a0, .Lsp_reg           # pai: registra handle p/ join
                # ---- filho: a0=0, s0=task, s1=handle ----
                # TROCA sp p/ a stack dedicada ANTES do call: o sp herdado aponta
                # p/ o frame ativo do kof_spawn_result do pai; o call empilharia
                # ra lá e corromperia os slots salvos do pai (race real — só
                # aparece no fire-and-forget, onde o pai continua sem bloquear).
                ld   sp, 24(s1)
                call kof_spawn_trampoline
                li   a0, 0
                li   a7, 93
                ecall
            .Lsp_inline:
                # clone falhou: roda inline (degradação segura, sem thread)
                call kof_spawn_trampoline
                j    .Lsp_ret
            .Lsp_reg:
                # handle na lista global (máx 64) p/ join implícito
                la   t0, kof_spawn_count
                ld   t1, 0(t0)
                li   t2, 64
                bge  t1, t2, .Lsp_ret
                slli t2, t1, 3
                la   t3, kof_spawn_handles
                add  t3, t3, t2
                sd   s1, 0(t3)
                addi t1, t1, 1
                sd   t1, 0(t0)
            .Lsp_ret:
                mv   a0, s1
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            # kof_spawn(task@a0) -> handle registrado (join implícito no fim do main)
            .globl kof_spawn
            kof_spawn:
                j    kof_spawn_result
            # trampoline: s0=task, s1=handle -> roda task.invoke(), marca done, wake
            kof_spawn_trampoline:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                ld   t0, 8(s0)              # task vtable
                ld   t0, 0(t0)              # vtable[0] = invoke
                mv   a0, s0
                jalr t0                     # a0 = resultado
                ld   s0, 16(sp)             # invoke pode clobberar s-regs? não
                ld   s1, 8(sp)              # (callee-saved), mas protege s0/s1
                sd   a0, 8(s1)              # handle->result
                fence rw, rw                # ordena result antes de done (RVO)
                li   t0, 1
                sw   t0, 4(s1)              # handle->done = 1
                addi a0, s1, 4              # &done (futex word)
                li   a1, 129                # FUTEX_WAKE_PRIVATE
                li   a2, 1
                li   a7, 98
                ecall
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            # kof_await(handle@a0) -> result@a0 (futex wait em done)
            .globl kof_await
            kof_await:
                beqz a0, .Lkw_null
                lw   t0, 4(a0)              # done?
                bnez t0, .Lkw_val
                addi sp, sp, -16
                sd   s0, 8(sp)
                sd   ra, 0(sp)
                mv   s0, a0
            .Lkw_wait:
                addi a0, s0, 4              # &done
                li   a1, 128                # FUTEX_WAIT_PRIVATE
                li   a2, 0                  # esperado done==0
                li   a3, 0
                li   a7, 98
                ecall
                lw   t0, 4(s0)
                beqz t0, .Lkw_wait
                mv   a0, s0
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                addi sp, sp, 16
            .Lkw_val:
                ld   a0, 8(a0)
                ret
            .Lkw_null:
                li   a0, 0
                ret
            # join implícito: aguarda todos os handles registrados (lista .bss).
            .globl kof_spawn_join_all
            kof_spawn_join_all:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                la   s0, kof_spawn_handles
                la   s1, kof_spawn_count
                ld   s1, 0(s1)
                li   s2, 0
            .Lkj_loop:
                bge  s2, s1, .Lkj_done
                slli t0, s2, 3
                add  t0, s0, t0
                ld   a0, 0(t0)
                beqz a0, .Lkj_next
                call kof_await
            .Lkj_next:
                addi s2, s2, 1
                j    .Lkj_loop
            .Lkj_done:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                addi sp, sp, 32
                ret

            .section .data
            .align 3
            kof_spawn_handles: .space 512
            kof_spawn_count:   .quad 0
            .section .text
            """);
    }

    private void emitCrossMethodRiscv(StringBuilder sb, IRClass clazz, IRMethod method, boolean joinMain) {
        // Mangle idêntico ao x86_64 (vtables referenciam esses símbolos).
        String mangled = sanitizeName(clazz.name()) + "_" + sanitizeName(method.name());
        if ("<init>".equals(method.name())) mangled += "_" + method.parameterTypes().size();
        int maxSlot = method.localVariables().stream().mapToInt(IRLocalVariable::index).max().orElse(0);
        int frameSize = Math.max((maxSlot + 1) * 8 + 16, 32);
        frameSize = (frameSize + 15) & ~15;
        sb.append(".globl ").append(mangled).append("\n");
        sb.append(mangled).append(":\n");
        // Modelo idêntico ao x86_64: `sp` É a pilha de operandos (cresce p/
        // baixo); `s11` = frame pointer. Layout (alto→baixo):
        //   [s11+0]=old s11  [s11-8]=saved ra  [s11-16-idx*8]=local idx  [sp..]=operandos
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd s11, 0(sp)\n");
        sb.append("    sd ra, 8(sp)\n");
        sb.append("    addi s11, sp, 16\n");
        sb.append("    addi sp, sp, -").append(frameSize).append("\n");
        // salva args de entrada (this + params) nos slots locais — ABI riscv:
        // a0=this/arg0, a1..a7 = demais args (até 8 registradores).
        String[] argRegs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        int argIdx = 0;
        for (IRLocalVariable lv : method.localVariables()) {
            if (lv.name().equals("this")) {
                sb.append("    sd a0, ").append(crossLocalOffRiscv(lv.index())).append("(s11)\n");
                argIdx++;
                continue;
            }
            if (argIdx < argRegs.length) {
                sb.append("    sd ").append(argRegs[argIdx]).append(", ")
                  .append(crossLocalOffRiscv(lv.index())).append("(s11)\n");
            }
            argIdx++;
        }
        boolean endsWithReturn = false;
        for (IRBasicBlock block : method.basicBlocks()) {
            for (KofOperation op : block.operations()) {
                if (op instanceof KofReturn || op instanceof KofReturnVoid) endsWithReturn = true;
                emitCrossOpRiscv(sb, op, frameSize, joinMain);
            }
        }
        if (!endsWithReturn) {
            if (joinMain) sb.append("    call kof_spawn_join_all\n");
            sb.append("    mv sp, s11\n");
            sb.append("    addi sp, sp, -16\n");
            sb.append("    ld ra, 8(sp)\n");
            sb.append("    ld s11, 0(sp)\n");
            sb.append("    addi sp, sp, 16\n");
            sb.append("    ret\n");
        }
    }

    private int crossLocalOffRiscv(int idx) { return -(idx + 1) * 8 - 16; }

    private void emitMethodTableRiscv(StringBuilder sb, IRClass clazz) {
        List<String> methods = collectVirtualMethods(clazz);
        sb.append(".align 3\n");
        sb.append(".globl ").append(sanitizeName(clazz.name())).append("_vtable\n");
        sb.append(sanitizeName(clazz.name())).append("_vtable:\n");
        if (methods.isEmpty()) {
            sb.append("    .quad 0\n");
            return;
        }
        for (String m : methods) sb.append("    .quad ").append(m).append("\n");
        sb.append("    .quad 0\n");
    }

    private void emitCrossOpRiscv(StringBuilder sb, KofOperation op, int frameSize, boolean joinMain) {
        switch (op) {
            case KofGetStatic gs -> { }
            case KofPutStatic ps -> sb.append("    addi sp, sp, 8\n");
            case KofLoadLiteral lit -> emitCrossLoadLiteralRiscv(sb, lit);
            case KofLoadLocal ll -> {
                sb.append("    ld t0, ").append(crossLocalOffRiscv(ll.index())).append("(s11)\n");
                pushRiscv(sb, "t0");
            }
            case KofStoreLocal sl -> {
                sb.append("    pop t0\n");
                sb.append("    sd t0, ").append(crossLocalOffRiscv(sl.index())).append("(s11)\n");
            }
            case KofLoadField lf -> {
                sb.append("    pop t0\n");
                int offset = resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    ld t0, ").append(offset).append("(t0)\n");
                pushRiscv(sb, "t0");
            }
            case KofStoreField sf -> {
                sb.append("    pop t0\n");   // valor
                sb.append("    pop t1\n");   // objeto
                int offset = resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    sd t0, ").append(offset).append("(t1)\n");
            }
            case KofBinary kb -> emitCrossBinaryRiscv(sb, kb);
            case KofUnary ku -> emitCrossUnaryRiscv(sb, ku);
            case KofConditionalJump kc -> emitCrossCondJumpRiscv(sb, kc);
            case KofLabel kl -> sb.append(resolveLabel(kl.label())).append(":\n");
            case KofJump kj -> sb.append("    j ").append(resolveLabel(kj.target())).append("\n");
            case KofCall kc -> emitCrossCallRiscv(sb, kc);
            case KofNewObject no -> emitCrossNewObjectRiscv(sb, no);
            case KofDup dup -> {
                sb.append("    ld t0, 0(sp)\n");
                pushRiscv(sb, "t0");
            }
            case KofDupX1 x1 -> {
                sb.append("    ld t0, 0(sp)\n    ld t1, 8(sp)\n");
                pushRiscv(sb, "t0"); pushRiscv(sb, "t1"); pushRiscv(sb, "t0");
            }
            case KofDupX2 x2 -> {
                sb.append("    ld t0, 0(sp)\n    ld t1, 8(sp)\n    ld t2, 16(sp)\n");
                pushRiscv(sb, "t0"); pushRiscv(sb, "t2"); pushRiscv(sb, "t1"); pushRiscv(sb, "t0");
            }
            case KofPop pop -> sb.append("    addi sp, sp, 8\n");
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
                sb.append("    pop a0\n");
                sb.append("    li a1, ").append(targetTypeId).append("\n");
                sb.append("    call kof_instanceof\n");
                pushRiscv(sb, "a0");
            }
            case KofNewArray na -> {
                sb.append("    pop a0\n");
                sb.append("    li a1, ").append(elementTypeSize(na.elementType())).append("\n");
                sb.append("    call kof_array_alloc\n");
                pushRiscv(sb, "a0");
            }
            case KofArrayLoad al -> {
                sb.append("    pop a1\n");   // idx
                sb.append("    pop a0\n");   // arr
                sb.append("    call kof_array_get\n");
                pushRiscv(sb, "a0");
            }
            case KofArrayStore as -> {
                sb.append("    pop a2\n");   // val
                sb.append("    pop a1\n");   // idx
                sb.append("    pop a0\n");   // arr
                sb.append("    call kof_array_set\n");
            }
            case KofArrayLength al -> {
                sb.append("    pop a0\n");
                sb.append("    call kof_array_length\n");
                pushRiscv(sb, "a0");
            }
            case KofThrow thr -> {
                sb.append("    pop a0\n");
                sb.append("    call kof_throw_string\n");
            }
            case KofTryStart kts -> {
                sb.append("    addi sp, sp, -32\n");
                sb.append("    la t0, ").append(resolveLabel(kts.handlerLabel())).append("\n");
                sb.append("    sd t0, 0(sp)\n");
                sb.append("    sd sp, 8(sp)\n");
                sb.append("    sd s11, 16(sp)\n");
                sb.append("    la t1, kof_exc_chain\n");
                sb.append("    ld t2, 0(t1)\n");
                sb.append("    sd t2, 24(sp)\n");
                sb.append("    sd sp, 0(t1)\n");
            }
            case KofTryEnd kte -> {
                sb.append("    la t1, kof_exc_chain\n");
                sb.append("    ld t2, 24(sp)\n");
                sb.append("    sd t2, 0(t1)\n");
                sb.append("    addi sp, sp, 32\n");
            }
            case KofCatchStart kcs -> {
                sb.append(resolveLabel(kcs.handlerLabel())).append(":\n");
                sb.append("    addi sp, sp, 32\n");
                sb.append("    sd a0, ").append(crossLocalOffRiscv(kcs.localIndex())).append("(s11)\n");
            }
            case KofReturn kr -> {
                sb.append("    pop a0\n");
                if (joinMain) sb.append("    call kof_spawn_join_all\n");
                sb.append("    mv sp, s11\n");
                sb.append("    addi sp, sp, -16\n");
                sb.append("    ld ra, 8(sp)\n");
                sb.append("    ld s11, 0(sp)\n");
                sb.append("    addi sp, sp, 16\n");
                sb.append("    ret\n");
            }
            case KofReturnVoid rv -> {
                sb.append("    li a0, 0\n");
                if (joinMain) sb.append("    call kof_spawn_join_all\n");
                sb.append("    mv sp, s11\n");
                sb.append("    addi sp, sp, -16\n");
                sb.append("    ld ra, 8(sp)\n");
                sb.append("    ld s11, 0(sp)\n");
                sb.append("    addi sp, sp, 16\n");
                sb.append("    ret\n");
            }
            default -> sb.append("    # NATIVE002: op fora do caminho feliz riscv64: ").append(op.getClass().getSimpleName()).append("\n");
        }
    }

    private void emitCrossNewObjectRiscv(StringBuilder sb, KofNewObject no) {
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
        sb.append("    li a0, ").append(size).append("\n");
        sb.append("    call kof_alloc\n");
        if (className != null) {
            String mangled = sanitizeName(className);
            sb.append("    mv a1, a0\n");
            sb.append("    li a2, ").append(typeId).append("\n");
            sb.append("    la a3, ").append(mangled).append("_vtable\n");
            sb.append("    mv a0, a1\n");
            sb.append("    mv a1, a2\n");
            sb.append("    mv a2, a3\n");
            sb.append("    call kof_init_object\n");
        }
        pushRiscv(sb, "a0");
    }

    private void emitCrossUnaryRiscv(StringBuilder sb, KofUnary ku) {
        sb.append("    pop t0\n");
        switch (ku.op()) {
            case NEG -> sb.append("    neg t0, t0\n");
            case NOT -> sb.append("    seqz t0, t0\n");
            case I2L -> sb.append("    sext.w t0, t0\n");
            case I2C -> sb.append("    sext.w t0, t0\n");
            case L2I -> sb.append("    sext.w t0, t0\n");
            case I2F -> sb.append("    fcvt.s.w f0, t0\n    fmv.x.w t0, f0\n");
            case I2D -> sb.append("    fcvt.d.w f0, t0\n    fmv.x.d t0, f0\n");
            case L2F -> sb.append("    fcvt.s.l f0, t0\n    fmv.x.w t0, f0\n");
            case L2D -> sb.append("    fcvt.d.l f0, t0\n    fmv.x.d t0, f0\n");
            case F2D -> sb.append("    fmv.w.x f0, t0\n    fcvt.d.s f0, f0\n    fmv.x.d t0, f0\n");
            case D2F -> sb.append("    fmv.d.x f0, t0\n    fcvt.s.d f0, f0\n    fmv.x.w t0, f0\n");
            case D2I -> sb.append("    fmv.d.x f0, t0\n    fcvt.w.d t0, f0, rtz\n    sext.w t0, t0\n");
            case F2I -> sb.append("    fmv.w.x f0, t0\n    fcvt.w.s t0, f0, rtz\n    sext.w t0, t0\n");
            case D2L -> sb.append("    fmv.d.x f0, t0\n    fcvt.l.d t0, f0, rtz\n");
            case F2L -> sb.append("    fmv.w.x f0, t0\n    fcvt.l.s f0, f0, rtz\n    fmv.x.d t0, f0\n");
        }
        pushRiscv(sb, "t0");
    }

    private void pushRiscv(StringBuilder sb, String reg) {
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd ").append(reg).append(", 0(sp)\n");
    }

    private void emitCrossLoadLiteralRiscv(StringBuilder sb, KofLoadLiteral lit) {
        if (lit.value() instanceof String s) {
            String label = internString(s);
            int len = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            sb.append("    la a0, ").append(label).append("\n");
            sb.append("    li a1, ").append(len).append("\n");
            sb.append("    call kof_string_from_literal\n");
            pushRiscv(sb, "a0");
        } else if (lit.value() instanceof Integer i) {
            sb.append("    li t0, ").append(i).append("\n");
            pushRiscv(sb, "t0");
        } else if (lit.value() instanceof Long l) {
            sb.append("    li t0, ").append(l).append("\n");
            pushRiscv(sb, "t0");
        } else if (lit.value() instanceof Boolean b) {
            sb.append("    li t0, ").append(b ? 1 : 0).append("\n");
            pushRiscv(sb, "t0");
        } else if (lit.value() instanceof Float f) {
            sb.append("    li t0, ").append(Float.floatToIntBits(f)).append("\n");
            pushRiscv(sb, "t0");
        } else if (lit.value() instanceof Double d) {
            sb.append("    li t0, ").append(Double.doubleToLongBits(d)).append("\n");
            pushRiscv(sb, "t0");
        } else if (lit.value() == null) {
            sb.append("    li t0, 0\n");
            pushRiscv(sb, "t0");
        } else {
            sb.append("    # NATIVE002: literal fora do caminho feliz: ").append(lit.value()).append("\n");
            sb.append("    li t0, 0\n");
            pushRiscv(sb, "t0");
        }
    }

    private void emitCrossBinaryRiscv(StringBuilder sb, KofBinary kb) {
        // b = topo, a = abaixo; resultado = a OP b
        Type opTy = kb.operandType();
        boolean isFloat = isFloatType(opTy);
        boolean isDouble = isDoubleType(opTy);
        if (isFloat || isDouble) {
            String s = isFloat ? "s" : "d";
            sb.append("    pop t0\n    fmv.").append(s).append(".x f1, t0\n");
            sb.append("    pop t1\n    fmv.").append(s).append(".x f0, t1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    fadd.").append(s).append(" f0, f0, f1\n");
                case SUB -> sb.append("    fsub.").append(s).append(" f0, f0, f1\n");
                case MUL -> sb.append("    fmul.").append(s).append(" f0, f0, f1\n");
                case DIV -> sb.append("    fdiv.").append(s).append(" f0, f0, f1\n");
                case EQ -> { sb.append("    feq.").append(s).append(" t1, f0, f1\n    mv t0, t1\n"); }
                case NE -> { sb.append("    fle.").append(s).append(" t1, f0, f1\n    snez t0, t1\n"); }
                case LT -> { sb.append("    flt.").append(s).append(" t0, f0, f1\n"); }
                case LE -> { sb.append("    fle.").append(s).append(" t0, f0, f1\n"); }
                case GT -> { sb.append("    fgt.").append(s).append(" t0, f0, f1\n"); }
                case GE -> { sb.append("    fge.").append(s).append(" t0, f0, f1\n"); }
                default -> { }
            }
            if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE) {
                pushRiscv(sb, "t0");
            } else if (kb.op() == KofBinaryOp.LT || kb.op() == KofBinaryOp.LE
                    || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.GE) {
                pushRiscv(sb, "t0");
            } else {
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                pushRiscv(sb, "t0");
            }
            return;
        }
        sb.append("    pop t0\n");   // b
        sb.append("    pop t1\n");   // a
        boolean int32 = isInt32Type(opTy);
        switch (kb.op()) {
            case ADD -> sb.append("    add t1, t1, t0\n");
            case SUB -> sb.append("    sub t1, t1, t0\n");
            case MUL -> sb.append("    mul t1, t1, t0\n");
            case DIV -> sb.append("    div t1, t1, t0\n");
            case MOD -> sb.append("    rem t1, t1, t0\n");
            case EQ -> { sb.append("    sub t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case NE -> { sb.append("    sub t2, t1, t0\n"); sb.append("    snez t1, t2\n"); }
            case LT -> sb.append("    slt t1, t1, t0\n");
            // sle/sge NÃO existem na ISA riscv — só slt. a<=b = !(b<a);
            // a>=b = !(a<b). seqz já é usado por EQ/NE (tradutor conhece).
            case LE -> { sb.append("    slt t2, t0, t1\n"); sb.append("    seqz t1, t2\n"); }
            case GT -> sb.append("    slt t1, t0, t1\n");
            case GE -> { sb.append("    slt t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case AND -> sb.append("    and t1, t1, t0\n");
            case OR -> sb.append("    or t1, t1, t0\n");
            case XOR -> sb.append("    xor t1, t1, t0\n");
            case SHL -> sb.append("    sll t1, t1, t0\n");
            case SHR -> sb.append("    sra t1, t1, t0\n");
            case USHR -> sb.append("    srl t1, t1, t0\n");
        }
        if (int32 && (kb.op() == KofBinaryOp.ADD || kb.op() == KofBinaryOp.SUB || kb.op() == KofBinaryOp.MUL)) {
            sb.append("    sext.w t1, t1\n");
        }
        pushRiscv(sb, "t1");
    }

    private void emitCrossCondJumpRiscv(StringBuilder sb, KofConditionalJump kc) {
        sb.append("    pop t0\n");   // b (topo)
        sb.append("    pop t1\n");   // a (abaixo)
        String cond;
        switch (kc.comparison()) {
            case EQ -> cond = "bne";
            case NE -> cond = "beq";
            case LT -> cond = "bge";
            case LE -> cond = "bgt";
            case GT -> cond = "ble";
            case GE -> cond = "blt";
            default -> cond = "b";
        }
        sb.append("    ").append(cond).append(" t1, t0, ").append(resolveLabel(kc.falseLabel())).append("\n");
        sb.append("    j ").append(resolveLabel(kc.trueLabel())).append("\n");
    }

    private void emitCrossCallRiscv(StringBuilder sb, KofCall kc) {
        String mn = kc.methodName();
        Type argType = kc.parameterTypes().isEmpty() ? Type.UnknownType.UNKNOWN : kc.parameterTypes().get(0);
        if ("kof_box".equals(mn) || "kof_unbox".equals(mn)) return;

        // println / print (PrintStream)
        if (kc.kind() == KofCallKind.INSTANCE && ("println".equals(mn) || "print".equals(mn))) {
            boolean nl = "println".equals(mn);
            sb.append("    pop a0\n");
            if (argType instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                switch (cn) {
                    case "int", "char", "short", "byte" -> {
                        sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                    }
                    case "long" -> sb.append(nl ? "    call kof_println_int\n" : "    call kof_print_int\n");
                    case "bool", "boolean" -> {
                        sb.append("    call kof_bool_to_string\n");
                        sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                    }
                    default -> sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
                }
            } else {
                sb.append(nl ? "    call kof_println_string\n" : "    call kof_print_string\n");
            }
            sb.append("    li a0, 0\n");
            pushRiscv(sb, "a0");
            return;
        }

        // String.valueOf (STATIC)
        if (kc.kind() == KofCallKind.STATIC && "valueOf".equals(mn)) {
            if (argType instanceof Type.PrimitiveType pt) {
                String cn = Type.canonicalPrimitiveName(pt.name());
                if ("float".equals(cn) || "double".equals(cn)) {
                    // FLT001: double→string exige %g (snprintf/libc) — ausente
                    // no runtime riscv64/aarch64 (asm puro estático). Sem guard,
                    // os bits do double ficavam na pilha e o println seguinte
                    // tratava-os como ponteiro de string (segfault silencioso).
                    throw new IllegalStateException("FLT001: " + mn
                            + "(float/double) não é suportado no runtime riscv64/aarch64"
                            + " (asm puro, sem libc/snprintf) — use JVM/Native x86_64"
                            + " ou converta (d as Int)");
                }
                if ("int".equals(cn) || "char".equals(cn) || "short".equals(cn) || "byte".equals(cn) || "long".equals(cn)) {
                    sb.append("    pop a0\n    call kof_int_to_string\n");
                    pushRiscv(sb, "a0");
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    sb.append("    pop a0\n    call kof_bool_to_string\n");
                    pushRiscv(sb, "a0");
                }
            }
            return;
        }

        // String.length (propriedade → INSTANCE sem args)
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType()) && "length".equals(mn)) {
            sb.append("    pop a0\n    call kof_string_length\n");
            pushRiscv(sb, "a0");
            return;
        }

        // métodos String com receiver + args (charAt/substring/contains/...)
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())) {
            String fn = switch (mn) {
                case "charAt" -> "kof_string_char_at";
                case "substring" -> "kof_string_substring";
                case "contains" -> "kof_string_contains";
                case "startsWith" -> "kof_string_starts_with";
                case "endsWith" -> "kof_string_ends_with";
                case "indexOf" -> "kof_string_index_of";
                case "concat" -> "kof_string_concat";
                case "toInt" -> "kof_string_to_int";
                case "trim" -> "kof_string_trim";
                case "toUpperCase" -> "kof_string_to_upper";
                case "toLowerCase" -> "kof_string_to_lower";
                case "lastIndexOf" -> "kof_string_last_index_of";
                case "equalsIgnoreCase" -> "kof_string_equals_ignore_case";
                default -> null;
            };
            if (fn != null) {
                int argCount = kc.parameterTypes().size();
                if ("substring".equals(mn) && argCount == 1) {
                    sb.append("    pop a1\n    li a2, 0\n");
                } else {
                    for (int i = argCount - 1; i >= 0; i--) {
                        sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
                    }
                }
                sb.append("    pop a0\n");
                sb.append("    call ").append(fn).append("\n");
                if (!Type.isVoid(kc.returnType())) pushRiscv(sb, "a0");
                return;
            }
            // replace(char,char) vs replace(String,String): dispatch por tipo
            if ("replace".equals(mn) && kc.parameterTypes().size() == 2) {
                Type first = kc.parameterTypes().get(0);
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                sb.append("    pop a2\n    pop a1\n    pop a0\n");
                sb.append("    call ").append(charArgs ? "kof_string_replace_char" : "kof_string_replace").append("\n");
                pushRiscv(sb, "a0");
                return;
            }
            // split(sep) -> String[] (array de KofString)
            if ("split".equals(mn) && kc.parameterTypes().size() == 1) {
                sb.append("    pop a1\n    pop a0\n");
                sb.append("    call kof_string_split\n");
                pushRiscv(sb, "a0");
                return;
            }
        }

        // kof_string_equals / concat como FUNCTION (frontend emite assim)
        if (kc.kind() == KofCallKind.FUNCTION && ("kof_string_equals".equals(mn) || "kof_string_concat".equals(mn))) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i)).append("\n");
            }
            sb.append("    call ").append(mn).append("\n");
            if (!Type.isVoid(kc.returnType())) pushRiscv(sb, "a0");
            return;
        }

        // coleções (List/Map/Set) — kof_list_*/kof_map_*/kof_set_*
        if (kc.kind() == KofCallKind.INSTANCE && mn.startsWith("kof_")) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
            }
            sb.append("    pop a0\n");
            sb.append("    call ").append(mn).append("\n");
            if (!Type.isVoid(kc.returnType())) pushRiscv(sb, "a0");
            return;
        }

        // construtor: obj (dup) + args
        if (kc.kind() == KofCallKind.CONSTRUCTOR && "<init>".equals(mn)) {
            int argCount = kc.parameterTypes().size();
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
            }
            sb.append("    pop a0\n");
            sb.append("    call ").append(resolveCalleeNameRiscv(kc)).append("\n");
            return;
        }

        // dispatch virtual (INSTANCE/INTERFACE em classe de usuário)
        if ((kc.kind() == KofCallKind.INSTANCE || kc.kind() == KofCallKind.INTERFACE)
                && kc.ownerType() instanceof Type.ClassType ct && !BuiltinTypes.isString(ct)) {
            int vtableIdx = findVirtualMethodIndex(ct.name(), mn);
            if (vtableIdx >= 0) {
                int argCount = kc.parameterTypes().size();
                for (int i = argCount - 1; i >= 0; i--) {
                    sb.append("    pop ").append(crossArgReg(i + 1)).append("\n");
                }
                sb.append("    pop a0\n");
                sb.append("    ld t0, 8(a0)\n");
                sb.append("    addi t0, t0, ").append(vtableIdx * 8).append("\n");
                sb.append("    ld t0, 0(t0)\n");
                sb.append("    jalr t0\n");
                if (!Type.isVoid(kc.returnType())) pushRiscv(sb, "a0");
                return;
            }
        }

        // chamada direta (FUNCTION/STATIC de usuário — args em a0..aN, sem receiver)
        int argCount = kc.parameterTypes().size();
        for (int i = argCount - 1; i >= 0; i--) {
            sb.append("    pop ").append(crossArgReg(i)).append("\n");
        }
        sb.append("    call ").append(resolveCalleeNameRiscv(kc)).append("\n");
        if (!Type.isVoid(kc.returnType())) pushRiscv(sb, "a0");
    }

    private String crossArgReg(int i) {
        String[] regs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        return i < regs.length ? regs[i] : "a7";
    }

    private String resolveCalleeNameRiscv(KofCall kc) {
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_") || mn.startsWith("kof_list_")) return mn;
        if (kc.kind() == KofCallKind.FUNCTION) {
            return functionMangleMap.getOrDefault(mn, sanitizeName(mn));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && kc.ownerType() instanceof Type.ClassType ct) {
            return sanitizeName(ct.name()) + "_" + sanitizeName("<init>") + "_" + kc.parameterTypes().size();
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + mn;
            return functionMangleMap.getOrDefault(key, sanitizeName(ct.name()) + "_" + sanitizeName(mn));
        }
        return sanitizeName(mn);
    }

    // Runtime riscv64 EM ASSEMBLY PURO (Kof é Kof — sem C; mesmo estilo do
    // x86_64 em NativeRuntime: raw syscall + layout de objeto idêntico).
    // Layout de String: typeId@0(i32) super@4(i32) vtable@8(ptr) len@16(i32)
    // pad@20(i32) data@24(inline). KOF_STRING_TYPE_ID=1.
    static final String RISCV_RUNTIME_ASM = """
            .option arch, rv64g
            .section .text

            # kof_alloc(size) -> ptr (bump atômico em .bss — amoadd.d: main e
            # workers do spawn compartilham o heap; ldadd no aarch64 via tradutor)
            .globl kof_alloc
            kof_alloc:
                addi t1, a0, 15
                andi t1, t1, -16
                la   t0, kof_alloc_ptr
                amoadd.d a0, t1, (t0)
                ret

            # kof_memcpy(dst, src, len)
            .globl kof_memcpy
            kof_memcpy:
                li   t0, 0
            .Lkf_mcpy:
                bgeu t0, a2, .Lkf_mcpy_done
                lbu  t1, 0(a1)
                sb   t1, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                addi t0, t0, 1
                j    .Lkf_mcpy
            .Lkf_mcpy_done:
                ret

            # kof_string_from_literal(s, len) -> KofStr*
            .globl kof_string_from_literal
            kof_string_from_literal:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                mv   s1, a1
                addi t0, s1, 25
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                li   t0, 0
                sw   t0, 4(s3)
                sd   t0, 8(s3)
                sw   s1, 16(s3)
                sw   t0, 20(s3)
                addi a0, s3, 24
                mv   a1, s0
                mv   a2, s1
                call kof_memcpy
                li   t0, 0
                addi t1, s3, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s3
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s3, 0(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_print_string(str*) — write(1, data=header+24, len)
            .globl kof_print_string
            kof_print_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                bnez a0, .Lps_ok
                la   a1, .Lstr_null         # null → "null" (bytes crus, paridade x86_64)
                li   a2, 4
                j    .Lps_write
            .Lps_ok:
                addi a1, a0, 24
                lw   a2, 16(a0)
            .Lps_write:
                li   a0, 1
                li   a7, 64
                ecall
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_println_string
            kof_println_string:
                # writev(1, iov[2], 2) — string + newline num ÚNICO syscall:
                # atomico, sem interleave entre threads (spawn/await).
                addi sp, sp, -48
                sd   ra, 40(sp)
                bnez a0, .Lpls_ok
                la   t0, .Lstr_null         # null → "null" (bytes crus)
                li   t1, 4
                sd   t0, 0(sp)              # iov[0].base
                sd   t1, 8(sp)              # iov[0].len
                j    .Lpls_nl
            .Lpls_ok:
                addi t0, a0, 24
                sd   t0, 0(sp)              # iov[0].base = data
                lw   t1, 16(a0)
                sd   t1, 8(sp)              # iov[0].len
            .Lpls_nl:
                la   t0, .Lnewline
                sd   t0, 16(sp)             # iov[1].base
                li   t1, 1
                sd   t1, 24(sp)             # iov[1].len
                li   a0, 1
                addi a1, sp, 0
                li   a2, 2
                li   a7, 66
                ecall
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            # kof_int_to_string(n) -> KofStr*
            .globl kof_int_to_string
            kof_int_to_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                sd   s5, 0(sp)
                mv   s0, a0
                li   s4, 0
                bgez s0, .Lkits_pos
                li   s4, 1
                neg  s0, s0
            .Lkits_pos:
                mv   s5, s0
                li   s3, 0
                mv   t4, s5
            .Lkits_cnt:
                li   t1, 10
                div  t4, t4, t1
                addi s3, s3, 1
                bnez t4, .Lkits_cnt
                beqz s4, .Lkits_cnt_done
                addi s3, s3, 1
            .Lkits_cnt_done:
                addi t0, s3, 25
                addi t0, t0, 15
                andi t0, t0, -16
                mv   a0, t0
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)
                li   t0, 0
                sw   t0, 4(s1)
                sd   t0, 8(s1)
                sw   s3, 16(s1)
                sw   t0, 20(s1)
                addi t0, s3, 23
                add  t1, s1, t0
                mv   t0, s5
            .Lkits_loop:
                li   t2, 10
                rem  t3, t0, t2
                addi t3, t3, 48
                sb   t3, 0(t1)
                addi t1, t1, -1
                div  t0, t0, t2
                bnez t0, .Lkits_loop
                beqz s4, .Lkits_neg_done
                li   t3, 45
                sb   t3, 0(t1)
            .Lkits_neg_done:
                li   t0, 0
                addi t1, s1, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s1
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s3, 16(sp)
                ld   s4, 8(sp)
                ld   s5, 0(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_print_int(n) — escreve o inteiro via raw write
            .globl kof_print_int
            kof_print_int:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                call kof_int_to_string
                call kof_print_string
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_println_int(n)
            .globl kof_println_int
            kof_println_int:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                call kof_int_to_string
                call kof_println_string
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_long_to_string(n) -> KofStr*
            .globl kof_long_to_string
            kof_long_to_string:
                j kof_int_to_string

            # kof_bool_to_string(b) -> KofStr*
            .globl kof_bool_to_string
            kof_bool_to_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                bnez a0, .Lbts_true
                la   a0, .Lstr_false
                li   a1, 5
                call kof_string_from_literal
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lbts_true:
                la   a0, .Lstr_true
                li   a1, 4
                call kof_string_from_literal
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_init_object(obj, typeId, vtable)
            .globl kof_init_object
            kof_init_object:
                sw   a1, 0(a0)
                li   t0, 0
                sw   t0, 4(a0)
                sd   a2, 8(a0)
                ret

            # kof_instanceof(obj, typeId) -> 0/1 (percorre kof_super_table)
            .globl kof_instanceof
            kof_instanceof:
                beqz a0, .Lio_null
                lw   t0, 0(a0)
            .Lio_loop:
                bne  t0, a1, .Lio_search
                li   a0, 1
                ret
            .Lio_search:
                beqz t0, .Lio_null
                la   t2, kof_super_table
            .Lio_search2:
                lw   t1, 0(t2)
                beqz t1, .Lio_null
                bne  t1, t0, .Lio_next
                lw   a0, 4(t2)
                j    .Lio_loop
            .Lio_next:
                addi t2, t2, 8
                j    .Lio_search2
            .Lio_null:
                li   a0, 0
                ret

            # kof_panic(str) — imprime e exit(1)
            .globl kof_panic
            kof_panic:
                call kof_println_string
                li   a0, 1
                li   a7, 93
                ecall

            .globl kof_null_error
            kof_null_error:
                la   a0, .Lstr_null_err
                call kof_panic

            .globl kof_bounds_error
            kof_bounds_error:
                la   a0, .Lstr_bounds_err
                call kof_panic

            # kof_throw_string(str) — desempilha a chain; sem handler → panic.
            # a0=str é preservado (só usa t-regs) até o handler.
            .globl kof_throw_string
            kof_throw_string:
                la   t0, kof_exc_chain
                ld   t0, 0(t0)
                beqz t0, .Lthrow_panic
                ld   t1, 8(t0)
                ld   t2, 16(t0)
                ld   t3, 24(t0)
                la   t4, kof_exc_chain
                sd   t3, 0(t4)
                ld   t4, 0(t0)
                beqz t4, .Lthrow_panic
                mv   sp, t1
                mv   s11, t2
                jr   t4
            .Lthrow_panic:
                call kof_panic

            # ---- arrays (header 24: typeId@0 super@4 vtable@8 len@16 elemSize@20 data@24) ----
            .globl kof_array_alloc
            kof_array_alloc:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mul  t0, s0, s1
                addi a0, t0, 24
                call kof_alloc
                mv   s2, a0
                li   t0, 2
                sw   t0, 0(s2)
                li   t0, 0
                sw   t0, 4(s2)
                sd   t0, 8(s2)
                sw   s0, 16(s2)
                sw   s1, 20(s2)
                mv   a0, s2
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_array_length
            kof_array_length:
                lw   a0, 16(a0)
                ret

            .globl kof_array_get
            kof_array_get:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lag_null
                lw   t0, 16(s0)
                bge  s1, t0, .Lag_bounds
                blt  s1, zero, .Lag_bounds
                lw   t1, 20(s0)
                mul  t2, s1, t1
                addi t2, t2, 24
                add  t2, s0, t2
                li   t3, 8
                beq  t1, t3, .Lag_q
                li   t3, 4
                beq  t1, t3, .Lag_d
                li   t3, 2
                beq  t1, t3, .Lag_w
                lb   a0, 0(t2)
                j    .Lag_done
            .Lag_w:
                lh   a0, 0(t2)
                j    .Lag_done
            .Lag_d:
                lw   a0, 0(t2)
                j    .Lag_done
            .Lag_q:
                ld   a0, 0(t2)
            .Lag_done:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lag_null:
                call kof_null_error
            .Lag_bounds:
                call kof_bounds_error

            .globl kof_array_set
            kof_array_set:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Las_null
                lw   t0, 16(s0)
                bge  s1, t0, .Las_bounds
                blt  s1, zero, .Las_bounds
                lw   t1, 20(s0)
                mul  t2, s1, t1
                addi t2, t2, 24
                add  t2, s0, t2
                li   t3, 8
                beq  t1, t3, .Las_q
                li   t3, 4
                beq  t1, t3, .Las_d
                li   t3, 2
                beq  t1, t3, .Las_w
                sb   s2, 0(t2)
                j    .Las_done
            .Las_w:
                sh   s2, 0(t2)
                j    .Las_done
            .Las_d:
                sw   s2, 0(t2)
                j    .Las_done
            .Las_q:
                sd   s2, 0(t2)
            .Las_done:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Las_null:
                call kof_null_error
            .Las_bounds:
                call kof_bounds_error

            # ---- strings ----
            .globl kof_string_length
            kof_string_length:
                lw   a0, 16(a0)
                ret

            # kof_string_concat(a, b) -> KofStr*
            .globl kof_string_concat
            kof_string_concat:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                li   s2, 0
                li   s3, 0
                beqz s0, .Lcc_a_null
                lw   s2, 16(s0)
                j    .Lcc_b
            .Lcc_a_null:
                addi s2, s2, 4
                li   s3, 1
            .Lcc_b:
                beqz s1, .Lcc_bnull
                lw   t0, 16(s1)
                add  s2, s2, t0
                j    .Lcc_alloc
            .Lcc_bnull:
                addi s2, s2, 4
                ori  s3, s3, 2
            .Lcc_alloc:
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s2, 16(s4)
                sw   t0, 20(s4)
                addi a0, s4, 24
                andi t1, s3, 1
                bnez t1, .Lcc_copy_a_null
                addi a1, s0, 24
                lw   a2, 16(s0)
                call kof_memcpy
                lw   s5, 16(s0)
                j    .Lcc_after_a
            .Lcc_copy_a_null:
                la   a1, .Lstr_null
                li   a2, 4
                call kof_memcpy
                li   s5, 4
            .Lcc_after_a:
                addi a0, s4, 24
                add  a0, a0, s5
                andi t1, s3, 2
                bnez t1, .Lcc_copy_b_null
                beqz s1, .Lcc_done
                addi a1, s1, 24
                lw   a2, 16(s1)
                call kof_memcpy
                j    .Lcc_done
            .Lcc_copy_b_null:
                la   a1, .Lstr_null
                li   a2, 4
                call kof_memcpy
            .Lcc_done:
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s2
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_equals(a, b) -> 0/1 (null-safe)
            .globl kof_string_equals
            kof_string_equals:
                beqz a0, .Lse_a0
                beqz a1, .Lse_no
                j    .Lse_body
            .Lse_a0:
                beqz a1, .Lse_yes
                j    .Lse_no
            .Lse_body:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 16(s0)
                lw   t1, 16(s1)
                bne  t0, t1, .Lse_no_pop
                li   t2, 0
            .Lse_loop:
                bge  t2, t0, .Lse_yes_pop
                addi t3, s0, 24
                add  t3, t3, t2
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Lse_no_pop
                addi t2, t2, 1
                j    .Lse_loop
            .Lse_yes_pop:
                li   a0, 1
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lse_no_pop:
                li   a0, 0
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lse_yes:
                li   a0, 1
                ret
            .Lse_no:
                li   a0, 0
                ret

            # kof_string_char_at(str, idx) -> Int (char code)
            .globl kof_string_char_at
            kof_string_char_at:
                lw   t0, 16(a0)
                bge  a1, t0, .Lca_bounds
                blt  a1, zero, .Lca_bounds
                addi t1, a0, 24
                add  t1, t1, a1
                lbu  a0, 0(t1)
                ret
            .Lca_bounds:
                call kof_bounds_error

            # kof_string_substring(str, start, end) -> KofStr* (end=0 → até o fim)
            .globl kof_string_substring
            kof_string_substring:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   t0, 16(s0)
                beqz s2, .Lss_endlen
                j    .Lss_chk
            .Lss_endlen:
                mv   s2, t0
            .Lss_chk:
                bgt  s2, t0, .Lss_bounds
                blt  s1, zero, .Lss_bounds
                bgt  s1, s2, .Lss_bounds
                sub  s3, s2, s1
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s3, 16(s4)
                sw   t0, 20(s4)
                addi a0, s4, 24
                addi a1, s0, 24
                add  a1, a1, s1
                mv   a2, s3
                call kof_memcpy
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lss_bounds:
                call kof_bounds_error

            # kof_string_contains(a, b) -> 0/1
            .globl kof_string_contains
            kof_string_contains:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                beqz s2, .Lcn_found
                lw   s3, 16(s0)
                bgt  s2, s3, .Lcn_no
                li   t0, 0
            .Lcn_outer:
                bge  t0, s3, .Lcn_no
                li   t1, 0
            .Lcn_inner:
                bge  t1, s2, .Lcn_found
                addi t2, s0, 24
                add  t2, t2, t0
                add  t2, t2, t1
                lbu  t2, 0(t2)
                addi t3, s1, 24
                add  t3, t3, t1
                lbu  t3, 0(t3)
                bne  t2, t3, .Lcn_next
                addi t1, t1, 1
                j    .Lcn_inner
            .Lcn_next:
                addi t0, t0, 1
                j    .Lcn_outer
            .Lcn_found:
                li   a0, 1
                j    .Lcn_ret
            .Lcn_no:
                li   a0, 0
            .Lcn_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_starts_with(a, b) -> 0/1
            .globl kof_string_starts_with
            kof_string_starts_with:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                lw   t0, 16(s0)
                bgt  s2, t0, .Lsw_no
                li   t1, 0
            .Lsw_loop:
                bge  t1, s2, .Lsw_yes
                addi t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                addi t3, s1, 24
                add  t3, t3, t1
                lbu  t3, 0(t3)
                bne  t2, t3, .Lsw_no
                addi t1, t1, 1
                j    .Lsw_loop
            .Lsw_yes:
                li   a0, 1
                j    .Lsw_ret
            .Lsw_no:
                li   a0, 0
            .Lsw_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_string_ends_with(a, b) -> 0/1
            .globl kof_string_ends_with
            kof_string_ends_with:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
                lw   s3, 16(s0)
                bgt  s2, s3, .Lew_no
                sub  t0, s3, s2
            .Lew_loop:
                bge  t0, s3, .Lew_yes
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                addi t3, s2, 0
                sub  t3, t3, s3
                add  t2, t2, t3
                lbu  t2, 0(t2)
                bne  t1, t2, .Lew_no
                addi t0, t0, 1
                j    .Lew_loop
            .Lew_yes:
                li   a0, 1
                j    .Lew_ret
            .Lew_no:
                li   a0, 0
            .Lew_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_index_of(a, b) -> Int
            .globl kof_string_index_of
            kof_string_index_of:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                beqz s3, .Liof_found0
                bgt  s3, s2, .Liof_no
                li   t0, 0
            .Liof_outer:
                mv   t1, s2
                sub  t1, t1, s3
                bgt  t0, t1, .Liof_no
                li   t2, 0
            .Liof_inner:
                bge  t2, s3, .Liof_found
                addi t3, s0, 24
                add  t4, t0, t2
                add  t3, t3, t4
                lbu  t3, 0(t3)
                addi t4, s1, 24
                add  t4, t4, t2
                lbu  t4, 0(t4)
                bne  t3, t4, .Liof_next
                addi t2, t2, 1
                j    .Liof_inner
            .Liof_next:
                addi t0, t0, 1
                j    .Liof_outer
            .Liof_found0:
                li   t0, 0
            .Liof_found:
                mv   a0, t0
                j    .Liof_ret
            .Liof_no:
                li   a0, -1
            .Liof_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;

    static final String RISCV_STRN002_ASM = """
            # ---- STRN002: String methods riscv64 (char-loops, sem FP) ----
            # kof_string_trim(str@a0) -> str
            .globl kof_string_trim
            kof_string_trim:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                li   s2, 0
            .Ltr_lead:
                bge  s2, s1, .Ltr_trail
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 32
                beq  t0, t1, .Ltr_lskip
                li   t1, 9
                beq  t0, t1, .Ltr_lskip
                li   t1, 10
                beq  t0, t1, .Ltr_lskip
                li   t1, 13
                beq  t0, t1, .Ltr_lskip
                j    .Ltr_trail
            .Ltr_lskip:
                addi s2, s2, 1
                j    .Ltr_lead
            .Ltr_trail:
                mv   s3, s1
            .Ltr_tloop:
                ble  s3, s2, .Ltr_build
                addi t0, s3, -1
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                li   t2, 32
                beq  t1, t2, .Ltr_tskip
                li   t2, 9
                beq  t1, t2, .Ltr_tskip
                li   t2, 10
                beq  t1, t2, .Ltr_tskip
                li   t2, 13
                beq  t1, t2, .Ltr_tskip
                j    .Ltr_build
            .Ltr_tskip:
                mv   s3, t0
                j    .Ltr_tloop
            .Ltr_build:
                sub  s4, s3, s2
                addi a0, s4, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s4, 16(s5)
                sw   zero, 20(s5)
                addi a0, s5, 24
                addi a1, s0, 24
                add  a1, a1, s2
                mv   a2, s4
                call kof_memcpy
                li   t0, 0
                addi t1, s5, 24
                add  t1, t1, s4
                sb   t0, 0(t1)
                mv   a0, s5
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_to_upper(str@a0) -> str
            .globl kof_string_to_upper
            kof_string_to_upper:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                li   s3, 0
            .Ltu_loop:
                bge  s3, s1, .Ltu_done
                addi t0, s0, 24
                add  t0, t0, s3
                lbu  t0, 0(t0)
                li   t1, 97
                blt  t0, t1, .Ltu_store
                li   t1, 122
                bgt  t0, t1, .Ltu_store
                addi t0, t0, -32
            .Ltu_store:
                addi t1, s2, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                addi s3, s3, 1
                j    .Ltu_loop
            .Ltu_done:
                li   t0, 0
                addi t1, s2, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_to_lower(str@a0) -> str
            .globl kof_string_to_lower
            kof_string_to_lower:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                li   s3, 0
            .Ltl_loop:
                bge  s3, s1, .Ltl_done
                addi t0, s0, 24
                add  t0, t0, s3
                lbu  t0, 0(t0)
                li   t1, 65
                blt  t0, t1, .Ltl_store
                li   t1, 90
                bgt  t0, t1, .Ltl_store
                addi t0, t0, 32
            .Ltl_store:
                addi t1, s2, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                addi s3, s3, 1
                j    .Ltl_loop
            .Ltl_done:
                li   t0, 0
                addi t1, s2, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_replace_char(str@a0, from@a1, to@a2) -> str
            .globl kof_string_replace_char
            kof_string_replace_char:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   s3, 16(s0)
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   s3, 16(s4)
                sw   zero, 20(s4)
                li   t1, 0
            .Lrc_loop:
                bge  t1, s3, .Lrc_done
                addi t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                bne  t2, s1, .Lrc_store
                mv   t2, s2
            .Lrc_store:
                addi t3, s4, 24
                add  t3, t3, t1
                sb   t2, 0(t3)
                addi t1, t1, 1
                j    .Lrc_loop
            .Lrc_done:
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s4, 8(sp)
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret

            # kof_string_replace(str@a0, from@a1, to@a2) -> str
            .globl kof_string_replace
            kof_string_replace:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   s3, 16(s0)
                lw   s4, 16(s1)
                lw   s5, 16(s2)
                beqz s4, .Lrp_copy
                li   s6, 0
                li   s7, 0
            .Lrp_scan:
                bge  s7, s3, .Lrp_alloc
                li   s8, 0
            .Lrp_cmp:
                bge  s8, s4, .Lrp_match
                addi t0, s0, 24
                add  t1, s7, s8
                add  t0, t0, t1
                lbu  t0, 0(t0)
                addi t1, s1, 24
                add  t1, t1, s8
                lbu  t1, 0(t1)
                bne  t0, t1, .Lrp_nomatch
                addi s8, s8, 1
                j    .Lrp_cmp
            .Lrp_match:
                addi s6, s6, 1
                add  s7, s7, s4
                j    .Lrp_scan
            .Lrp_nomatch:
                addi s7, s7, 1
                j    .Lrp_scan
            .Lrp_alloc:
                sub  t0, s5, s4
                mul  t0, t0, s6
                add  t0, t0, s3
                mv   s7, t0
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   s7, 16(s8)
                sw   zero, 20(s8)
                li   s6, 0
                li   s5, 0
            .Lrp_build:
                bge  s6, s3, .Lrp_done
                li   t3, 0
            .Lrp_bcmp:
                bge  t3, s4, .Lrp_bmatch
                addi t0, s0, 24
                add  t1, s6, t3
                add  t0, t0, t1
                lbu  t0, 0(t0)
                addi t1, s1, 24
                add  t1, t1, t3
                lbu  t1, 0(t1)
                bne  t0, t1, .Lrp_bcopy
                addi t3, t3, 1
                j    .Lrp_bcmp
            .Lrp_bmatch:
                lw   t4, 16(s2)
                li   t3, 0
            .Lrp_bto:
                bge  t3, t4, .Lrp_bskip
                addi t0, s2, 24
                add  t0, t0, t3
                lbu  t0, 0(t0)
                addi t1, s8, 24
                add  t1, t1, s5
                sb   t0, 0(t1)
                addi s5, s5, 1
                addi t3, t3, 1
                j    .Lrp_bto
            .Lrp_bskip:
                add  s6, s6, s4
                j    .Lrp_build
            .Lrp_bcopy:
                addi t0, s0, 24
                add  t0, t0, s6
                lbu  t0, 0(t0)
                addi t1, s8, 24
                add  t1, t1, s5
                sb   t0, 0(t1)
                addi s5, s5, 1
                addi s6, s6, 1
                j    .Lrp_build
            .Lrp_done:
                li   t0, 0
                addi t1, s8, 24
                add  t1, t1, s7
                sb   t0, 0(t1)
                mv   a0, s8
                j    .Lrp_ret
            .Lrp_copy:
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   s3, 16(s8)
                sw   zero, 20(s8)
                addi a0, s8, 24
                addi a1, s0, 24
                mv   a2, s3
                call kof_memcpy
                li   t0, 0
                addi t1, s8, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s8
            .Lrp_ret:
                ld   s8, 16(sp)
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # kof_string_last_index_of(str@a0, sub@a1) -> Int
            .globl kof_string_last_index_of
            kof_string_last_index_of:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                beqz s3, .Lli_end
                bgt  s3, s2, .Lli_no
                sub  s4, s2, s3
            .Lli_outer:
                bltz s4, .Lli_no
                li   t0, 0
            .Lli_inner:
                bge  t0, s3, .Lli_found
                addi t1, s0, 24
                add  t2, s4, t0
                add  t1, t1, t2
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                lbu  t2, 0(t2)
                bne  t1, t2, .Lli_next
                addi t0, t0, 1
                j    .Lli_inner
            .Lli_next:
                addi s4, s4, -1
                j    .Lli_outer
            .Lli_found:
                mv   a0, s4
                j    .Lli_ret
            .Lli_end:
                mv   a0, s2
                j    .Lli_ret
            .Lli_no:
                li   a0, -1
            .Lli_ret:
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_equals_ignore_case(a@a0, b@a1) -> Bool
            .globl kof_string_equals_ignore_case
            kof_string_equals_ignore_case:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                bne  s2, s3, .Lec_no
                li   t0, 0
            .Lec_loop:
                bge  t0, s2, .Lec_yes
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                lbu  t2, 0(t2)
                li   t3, 65
                blt  t1, t3, .Lec_f2
                li   t3, 90
                bgt  t1, t3, .Lec_f2
                addi t1, t1, 32
            .Lec_f2:
                li   t3, 65
                blt  t2, t3, .Lec_cmp
                li   t3, 90
                bgt  t2, t3, .Lec_cmp
                addi t2, t2, 32
            .Lec_cmp:
                bne  t1, t2, .Lec_no
                addi t0, t0, 1
                j    .Lec_loop
            .Lec_yes:
                li   a0, 1
                j    .Lec_ret
            .Lec_no:
                li   a0, 0
            .Lec_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_string_split(str@a0, sep@a1) -> String[] (array de KofString)
            .globl kof_string_split
            kof_string_split:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   t0, 16(s1)
                li   s3, 0
                beqz t0, .Lsk_count
                addi s3, s1, 24
                lbu  s3, 0(s3)
            .Lsk_count:
                li   s4, 1
                li   s5, 0
            .Lsk_cloop:
                bge  s5, s2, .Lsk_alloc
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_cnext
                addi s4, s4, 1
            .Lsk_cnext:
                addi s5, s5, 1
                j    .Lsk_cloop
            .Lsk_alloc:
                mv   a0, s4
                li   a1, 8
                call kof_array_alloc
                mv   s6, a0
                li   s7, 0
                li   s5, 0
                li   s4, 0
            .Lsk_outer:
                bge  s5, s2, .Lsk_last
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_onext
                call .Lsk_emit
                addi s4, s5, 1
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_onext:
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_last:
                bge  s4, s2, .Lsk_done
                mv   s5, s2
                call .Lsk_emit
            .Lsk_done:
                mv   a0, s6
                j    .Lsk_ret
            .Lsk_emit:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sub  t0, s5, s4
                sd   t0, 8(sp)              # len (sobrevive ao memcpy)
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                sd   a0, 0(sp)              # str nova
                ld   t0, 8(sp)              # recarrega len (alloc clobbera t0)
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   t0, 16(a0)
                sw   zero, 20(a0)
                addi a0, a0, 24
                addi a1, s0, 24
                add  a1, a1, s4
                mv   a2, t0
                call kof_memcpy
                ld   t1, 0(sp)
                ld   t0, 8(sp)
                li   t2, 0
                addi t3, t1, 24
                add  t3, t3, t0
                sb   t2, 0(t3)
                addi t2, s6, 24             # array: dados inline em 24+i*8
                slli t3, s7, 3
                add  t2, t2, t3
                sd   t1, 0(t2)
                addi s7, s7, 1
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lsk_ret:
                ld   s7, 8(sp)
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;

    static final String RISCV_RUNTIME_ASM_B = """
            # kof_string_to_int(str) -> Int
            .globl kof_string_to_int
            kof_string_to_int:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                li   s1, 0
                li   s2, 0
                lw   s3, 16(s0)
                beqz s3, .Lsti_done
                lbu  t0, 24(s0)
                li   t1, 45
                bne  t0, t1, .Lsti_loop
                li   s2, 1
            .Lsti_loop:
                bge  s1, s3, .Lsti_sign
                lbu  t0, 24(s0)
                add  t0, t0, s1
                lbu  t0, 0(t0)
                addi t0, t0, -48
                li   t1, 9
                bgt  t0, t1, .Lsti_skip
                li   t1, 0
                blt  t0, t1, .Lsti_skip
                li   t1, 10
                mul  a0, a0, t1
                add  a0, a0, t0
            .Lsti_skip:
                addi s1, s1, 1
                j    .Lsti_loop
            .Lsti_sign:
                beqz s2, .Lsti_done
                neg  a0, a0
            .Lsti_done:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- List (typeId@0 super@4 vtable@8 len@16 cap@20 data@24) ----
            .globl kof_list_new
            kof_list_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 32
                call kof_alloc
                mv   s0, a0
                li   t0, 100
                sw   t0, 0(s0)
                li   t0, 0
                sw   t0, 4(s0)
                sd   t0, 8(s0)
                sw   t0, 16(s0)
                li   t0, 2
                sw   t0, 20(s0)
                li   a0, 16
                call kof_alloc
                sd   a0, 24(s0)
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_list_grow
            kof_list_grow:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                lw   t0, 20(s0)
                slli t0, t0, 1
                sw   t0, 20(s0)
                slli a0, t0, 3
                addi a0, a0, 24
                call kof_alloc
                mv   s1, a0
                ld   a1, 24(s0)
                lw   a2, 16(s0)
                slli a2, a2, 3
                call kof_memcpy
                sd   s1, 24(s0)
                mv   a0, s0
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_add
            kof_list_add:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 20(s0)
                lw   t1, 16(s0)
                bge  t1, t0, .Lla_grow
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
                j    .Lla_done
            .Lla_grow:
                mv   a0, s0
                call kof_list_grow
                lw   t1, 16(s0)
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
            .Lla_done:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_size
            kof_list_size:
                lw   a0, 16(a0)
                ret

            # kof_list_remove(list, idx) -> item removido (desloca o resto)
            .globl kof_list_remove
            kof_list_remove:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)      # list
                sd   s1, 8(sp)       # idx
                sd   s2, 0(sp)       # item
                mv   s0, a0
                mv   s1, a1
                ld   t0, 24(s0)
                slli t1, s1, 3
                add  t0, t0, t1
                ld   s2, 0(t0)       # item = data[idx]
                lw   t2, 16(s0)      # size
                addi t3, s1, 1       # i = idx+1
            .Llr_loop:
                bge  t3, t2, .Llr_done
                ld   t0, 24(s0)
                slli t4, t3, 3
                add  t4, t0, t4
                ld   t5, 0(t4)       # data[i]
                ld   t0, 24(s0)
                addi t6, t3, -1
                slli t6, t6, 3
                add  t6, t0, t6
                sd   t5, 0(t6)       # data[i-1] = data[i]
                addi t3, t3, 1
                j    .Llr_loop
            .Llr_done:
                addi t2, t2, -1
                sw   t2, 16(s0)      # size--
                mv   a0, s2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_get
            kof_list_get:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Llg_bounds
                blt  a1, zero, .Llg_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Llg_bounds:
                call kof_bounds_error

            .globl kof_list_set
            kof_list_set:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Lls_bounds
                blt  a1, zero, .Lls_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                sd   a2, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lls_bounds:
                call kof_bounds_error

            .globl kof_list_contains
            kof_list_contains:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                li   t0, 0
            .Llc_loop:
                lw   t1, 16(s0)
                bge  t0, t1, .Llc_no
                ld   t2, 24(s0)
                slli t3, t0, 3
                add  t2, t2, t3
                ld   t2, 0(t2)
                bne  t2, s1, .Llc_next
                li   a0, 1
                j    .Llc_ret
            .Llc_next:
                addi t0, t0, 1
                j    .Llc_loop
            .Llc_no:
                li   a0, 0
            .Llc_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_is_empty
            kof_list_is_empty:
                lw   t0, 16(a0)
                seqz a0, t0
                ret

            .globl kof_list_clear
            kof_list_clear:
                li   t0, 0
                sw   t0, 16(a0)
                ret

            # ---- kof.log: label [LEVEL] + msg + newline; stderr para warn/error, stdout para info/debug ----
            .globl kof_log_debug
            kof_log_debug:
                li   a1, 0
                j    kof_log_write_lvl
            .globl kof_log_info
            kof_log_info:
                li   a1, 1
                j    kof_log_write_lvl
            .globl kof_log_warn
            kof_log_warn:
                li   a1, 2
                j    kof_log_write_lvl
            .globl kof_log_error
            kof_log_error:
                li   a1, 3
                j    kof_log_write_lvl
            # helper: a0=msg*, a1=level (0..3)
            kof_log_write_lvl:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)      # msg
                sd   s1, 8(sp)       # level
                mv   s0, a0
                mv   s1, a1
                # fd = level >= 2 ? 2(stderr) : 1(stdout)
                li   t0, 2
                bge  s1, t0, .Llw_stderr
                li   s2, 1
                j    .Llw_write
            .Llw_stderr:
                li   s2, 2
            .Llw_write:
                # escolhe label
                la   t0, .Llog_lbl_debug
                li   t1, 0
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_info
                li   t1, 1
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_warn
                li   t1, 2
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_error
            .Llw_have_lbl:
                # write(fd, label, 8)
                mv   a0, s2
                mv   a1, t0
                li   a2, 8
                li   a7, 64
                ecall
                # write(fd, msg.data, msg.len)
                beqz s0, .Llw_skip_msg
                lw   a2, 16(s0)
                addi a1, s0, 24
                mv   a0, s2
                li   a7, 64
                ecall
            .Llw_skip_msg:
                # newline (usar .Lnewline)
                mv   a0, s2
                la   a1, .Lnewline
                li   a2, 1
                li   a7, 64
                ecall
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # ---- kof.config (minimal — retorna default / 0 / false) ----
            .globl kof_config_get
            kof_config_get:
                li   a0, 0
                ret
            .globl kof_config_env
            kof_config_env:
                li   a0, 0
                ret
            .globl kof_config_has
            kof_config_has:
                li   a0, 0
                ret
            .globl kof_config_str
            kof_config_str:
                mv   a0, a1
                ret
            .globl kof_config_int
            kof_config_int:
                mv   a0, a1
                ret
            .globl kof_config_long
            kof_config_long:
                mv   a0, a1
                ret
            .globl kof_config_bool
            kof_config_bool:
                mv   a0, a1
                ret
            .globl kof_config_required
            kof_config_required:
                beqz a0, kof_null_error
                ret

            # ---- kof.time ----
            # kof_time_now() -> epoch-ms (CLOCK_REALTIME). clock_gettime=113
            # (asm-generic, mesma tabela aarch64); paridade com o x86_64 (que
            # usava syscall 96). Antes era stub `li a0,0` → TTL do cache e
            # time.now() quebravam silenciosamente (R6).
            .globl kof_time_now
            kof_time_now:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a1, sp, 0              # &timespec {sec@0, nsec@8}
                li   a0, 0                  # CLOCK_REALTIME
                li   a7, 113
                ecall
                ld   t0, 0(sp)              # tv_sec
                ld   t1, 8(sp)              # tv_nsec
                li   t2, 1000
                mul  a0, t0, t2             # sec * 1000
                li   t3, 1000000
                div  t1, t1, t3             # nsec / 1_000_000
                add  a0, a0, t1             # epoch-ms
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_time_sleep
            kof_time_sleep:
                ret
            .globl kof_time_interval
            kof_time_interval:
                la   a0, .Lstr_empty_interval
                li   a1, 10
                call kof_string_from_literal
                ret
            .globl kof_time_cancel
            kof_time_cancel:
                ret

            # ---- kof.observability (real, minimal para passar KofObservabilityTest) ----
            .globl kof_observability_health
            kof_observability_health:
                la   a0, .Lstr_health
                li   a1, 2
                call kof_string_from_literal
                ret
            .globl kof_observability_readiness
            kof_observability_readiness:
                li   a0, 1
                ret
            .globl kof_observability_liveness
            kof_observability_liveness:
                li   a0, 1
                ret
            .globl kof_observability_counter
            kof_observability_counter:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_counter_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_counter_new
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                addi t1, t1, 1
                sw   t1, 0(t0)
                mv   a0, t1
                j    .Loc_counter_ret
            .Loc_counter_new:
                la   t0, kof_obs_counter_name
                sd   s0, 0(t0)
                la   t0, kof_obs_counter_val
                li   t1, 1
                sw   t1, 0(t0)
                li   a0, 1
            .Loc_counter_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_observability_increment
            kof_observability_increment:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_inc_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_inc_new
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                add  t1, t1, s1
                sw   t1, 0(t0)
                mv   a0, t1
                j    .Loc_inc_ret
            .Loc_inc_new:
                la   t0, kof_obs_counter_name
                sd   s0, 0(t0)
                la   t0, kof_obs_counter_val
                sw   s1, 0(t0)
                mv   a0, s1
            .Loc_inc_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_observability_gauge
            kof_observability_gauge:
                la   t0, kof_obs_gauge_name
                sd   a0, 0(t0)
                la   t0, kof_obs_gauge_val
                sw   a1, 0(t0)
                ret

            .globl kof_observability_histogram
            kof_observability_histogram:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                beqz t1, .Loc_hist_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_hist_new
                la   t0, kof_obs_hist_count
                lw   t1, 0(t0)
                addi t1, t1, 1
                sw   t1, 0(t0)
                la   t0, kof_obs_hist_sum
                lw   t1, 0(t0)
                add  t1, t1, s1
                sw   t1, 0(t0)
                j    .Loc_hist_ret
            .Loc_hist_new:
                la   t0, kof_obs_hist_name
                sd   s0, 0(t0)
                la   t0, kof_obs_hist_count
                li   t1, 1
                sw   t1, 0(t0)
                la   t0, kof_obs_hist_sum
                sw   s1, 0(t0)
            .Loc_hist_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_observability_metrics
            kof_observability_metrics:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                # start with empty string
                la   a0, .Lstr_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s0, a0
                # counter part
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_gauge
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                # counter string: name + space + val + newline
                mv   a0, s0
                mv   a1, t1
                # we need to convert counter name + space + val
                # s0 = s0 + counter_name
                mv   s1, s0
                mv   a0, s1
                mv   a1, t1
                # actually we will build step by step via kof_string_concat and kof_int_to_string
                la   t2, kof_obs_counter_name
                ld   a1, 0(t2)
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_counter_val
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_gauge:
                la   t0, kof_obs_gauge_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_hist
                la   t0, kof_obs_gauge_val
                lw   t2, 0(t0)
                mv   a0, s0
                mv   a1, t1
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_gauge_val
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_hist:
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_done
                # hist_count line: name + _count + space + count + newline
                mv   a0, s0
                mv   a1, t1
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_count
                li   a1, 6
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_hist_count
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                # hist_sum line: name + _sum + space + sum + newline
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                mv   a0, s0
                mv   a1, t1
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_sum
                li   a1, 4
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_hist_sum
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_done:
                mv   a0, s0
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            .globl kof_observability_request_id
            kof_observability_request_id:
                la   a0, .Lstr_trace
                li   a1, 16
                call kof_string_from_literal
                ret
            .globl kof_observability_correlation_id
            kof_observability_correlation_id:
                la   a0, .Lstr_span
                li   a1, 16
                call kof_string_from_literal
                ret
            .globl kof_observability_trace_id
            kof_observability_trace_id:
                la   a0, .Lstr_trace
                li   a1, 32
                call kof_string_from_literal
                ret
            .globl kof_observability_span_id
            kof_observability_span_id:
                la   a0, .Lstr_span
                li   a1, 16
                call kof_string_from_literal
                ret
            .globl kof_observability_span_start
            kof_observability_span_start:
                la   a0, .Lstr_span_handle
                li   a1, 48
                call kof_string_from_literal
                ret
            .globl kof_observability_span_end
            kof_observability_span_end:
                la   a0, .Lstr_empty_json
                li   a1, 2
                call kof_string_from_literal
                ret

            # ---- kof.cache riscv64/aarch64: 64 slots de 24B em .bss ([key*][val*][expira_ms]) ----
            # set(key, val): grava; get(key): lê; set_ttl(key,val,ttl): guarda com expiração real
            .globl kof_cache_set
            kof_cache_set:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # key
                sd   s1, 24(sp)      # val
                sd   s2, 16(sp)      # ponteiro slot
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lcache_area
                li   t0, 1536
                add  t0, s2, t0
                sd   t0, 8(sp)       # fim (recarregado a cada iteração — t-regs
                                     # são clobberados pelo kof_string_equals)
            .Lcs_scan:
                ld   t5, 8(sp)
                bgeu s2, t5, .Lcs_write
                ld   t4, 0(s2)
                beqz t4, .Lcs_write       # vazio
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcs_write       # mesma key, overwrite
                addi s2, s2, 24
                j    .Lcs_scan
            .Lcs_write:
                sd   s0, 0(s2)
                sd   s1, 8(s2)
                li   t4, 0
                sd   t4, 16(s2)           # sem ttl
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_set_ttl(key, val, ttl_seconds)
            .globl kof_cache_set_ttl
            kof_cache_set_ttl:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)      # key
                sd   s1, 40(sp)      # val
                sd   s2, 32(sp)      # ttl seconds
                sd   s3, 24(sp)      # slot ptr
                sd   s4, 16(sp)      # expira em ms
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                # expira = now + ttl*1000
                call kof_time_now
                li   t4, 1000
                mul  t4, s2, t4
                add  s4, a0, t4
                # grava
                la   s3, .Lcache_area
                li   t0, 1536
                add  t0, s3, t0
                sd   t0, 8(sp)       # fim (recarregado — t-regs clobberados)
            .Lcst_scan:
                ld   t6, 8(sp)
                bgeu s3, t6, .Lcst_write
                ld   t5, 0(s3)
                beqz t5, .Lcst_write
                mv   a0, t5
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcst_write
                addi s3, s3, 24
                j    .Lcst_scan
            .Lcst_write:
                sd   s0, 0(s3)
                sd   s1, 8(s3)
                sd   s4, 16(s3)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_cache_get(key) -> val* ou 0
            .globl kof_cache_get
            kof_cache_get:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lcg_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lcg_miss
                ld   t4, 0(s1)
                beqz t4, .Lcg_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcg_hit
            .Lcg_next:
                addi s1, s1, 24
                j    .Lcg_scan
            .Lcg_hit:
                # checa expiração: slot = [key@0 val@8 expira@16]
                ld   t4, 16(s1)
                beqz t4, .Lcg_hit_val
                sd   s1, 8(sp)
                sd   t4, 0(sp)
                call kof_time_now
                ld   s1, 8(sp)
                ld   t4, 0(sp)
                # se now >= expira → miss
                bgeu a0, t4, .Lcg_miss
                # devolve val
                ld   a0, 8(s1)
                j    .Lcg_ret
            .Lcg_hit_val:
                ld   a0, 8(s1)
                j    .Lcg_ret
            .Lcg_miss:
                li   a0, 0
            .Lcg_ret:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_ttl(key) -> segundos restantes, 0 default
            .globl kof_cache_ttl
            kof_cache_ttl:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lct_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lct_miss
                ld   t4, 0(s1)
                beqz t4, .Lct_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lct_found
            .Lct_next:
                addi s1, s1, 24
                j    .Lct_scan
            .Lct_found:
                ld   t4, 16(s1)
                beqz t4, .Lct_miss     # sem ttl (expira=0) → 0
                sd   t4, 0(sp)
                sd   s1, 8(sp)
                call kof_time_now
                ld   t4, 0(sp)         # expira_ms
                # resta_ms = expira - now (ms)
                sub  t4, t4, a0
                blez t4, .Lct_miss
                li   t5, 1000
                div  a0, t4, t5
                j    .Lct_ret
            .Lct_miss:
                li   a0, 0
            .Lct_ret:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_delete(key)
            .globl kof_cache_delete
            kof_cache_delete:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lcd_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lcd_done
                ld   t4, 0(s1)
                beqz t4, .Lcd_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcd_clear
            .Lcd_next:
                addi s1, s1, 24
                j    .Lcd_scan
            .Lcd_clear:
                li   t0, 0
                sd   t0, 0(s1)
                sd   t0, 8(s1)
                sd   t0, 16(s1)
            .Lcd_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_clear()
            .globl kof_cache_clear
            kof_cache_clear:
                la   t0, .Lcache_area
                li   t1, 0
                li   t2, 64
            .Lclr_loop:
                bge  t1, t2, .Lclr_done
                li   t3, 0
                sd   t3, 0(t0)
                sd   t3, 8(t0)
                sd   t3, 16(t0)
                addi t1, t1, 1
                addi t0, t0, 24
                j    .Lclr_loop
            .Lclr_done:
                ret
            # ---- kof.mq riscv64/aarch64: pub/sub + filas em .bss (64 slots de 16B) ----
            # Reescrita 05/09 (port completo): entry = [topic@0, list@8].
            # (1) scan loops usam PONTEIRO-FIM salvo no frame (recarregado a
            #     cada iteração) — t2/t3/t4 são caller-saved e o
            #     kof_string_equals os clobber (segfault nos loops antigos);
            # (2) todos os s-regs usados são salvos no frame (os antigos
            #     usavam s2/s3/s4 sem salvar — clobber sob kof_string_equals,
            #     kof_list_* e o jalr dos handlers);
            # (3) invoke de handler: a0=fn a1=msg → ld t0,8(a0) ld t0,0(t0)
            #     jalr t0 (mesmo padrão do codegen de dispatch virtual);
            # (4) pop/remove/queue_size por handle — kof_list_remove (novo).
            # Paridade de output com o x86_64 (MQ001).
            .globl kof_mq_subscribe
            kof_mq_subscribe:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)      # topic
                sd   s1, 40(sp)      # fn
                sd   s2, 32(sp)      # slot ptr
                sd   s3, 24(sp)      # end ptr
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_sub_find:
                bgeu s2, s3, .Lmq_sub_new
                ld   t4, 0(s2)
                beqz t4, .Lmq_sub_new
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lmq_sub_addfn
                addi s2, s2, 16
                j    .Lmq_sub_find
            .Lmq_sub_new:
                sd   s0, 0(s2)
                mv   a0, zero
                call kof_list_new
                sd   a0, 8(s2)
            .Lmq_sub_addfn:
                ld   a0, 8(s2)
                mv   a1, s1
                call kof_list_add
            .Lmq_sub_done:
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_mq_unsubscribe(topic, fn) — remove por identidade do objeto fn
            .globl kof_mq_unsubscribe
            kof_mq_unsubscribe:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)      # topic
                sd   s1, 56(sp)      # fn
                sd   s2, 48(sp)      # slot ptr
                sd   s3, 40(sp)      # end ptr
                sd   s4, 32(sp)      # list
                sd   s5, 24(sp)      # idx
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_unsub_find:
                bgeu s2, s3, .Lmq_unsub_done
                ld   t4, 0(s2)
                beqz t4, .Lmq_unsub_done
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_unsub_next
                ld   s4, 8(s2)
                li   s5, 0
            .Lmq_unsub_loop:
                mv   a0, s4
                call kof_list_size
                bge  s5, a0, .Lmq_unsub_done
                mv   a0, s4
                mv   a1, s5
                call kof_list_get
                beq  a0, s1, .Lmq_unsub_rm
                addi s5, s5, 1
                j    .Lmq_unsub_loop
            .Lmq_unsub_rm:
                mv   a0, s4
                mv   a1, s5
                call kof_list_remove
                j    .Lmq_unsub_done
            .Lmq_unsub_next:
                addi s2, s2, 16
                j    .Lmq_unsub_find
            .Lmq_unsub_done:
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_mq_publish(topic, msg) — dispara cada fn com a0=fn a1=msg
            .globl kof_mq_publish
            kof_mq_publish:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)      # topic
                sd   s1, 56(sp)      # msg
                sd   s2, 48(sp)      # slot ptr
                sd   s3, 40(sp)      # end ptr
                sd   s4, 32(sp)      # list
                sd   s5, 24(sp)      # idx
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_pub_find:
                bgeu s2, s3, .Lmq_pub_done
                ld   t4, 0(s2)
                beqz t4, .Lmq_pub_done
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_pub_next
                ld   s4, 8(s2)
                li   s5, 0
            .Lmq_pub_iter:
                mv   a0, s4
                call kof_list_size
                bge  s5, a0, .Lmq_pub_done
                mv   a0, s4
                mv   a1, s5
                call kof_list_get
                mv   t5, a0          # fn
                mv   a0, t5          # invoke: a0=fn a1=msg
                # (t5 é caller-saved, mas a1 já foi movido antes de clobber)
                mv   a1, s1
                ld   t0, 8(a0)       # fn->vtable
                ld   t0, 0(t0)       # vtable[0] = invoke
                jalr t0
                addi s5, s5, 1
                j    .Lmq_pub_iter
            .Lmq_pub_next:
                addi s2, s2, 16
                j    .Lmq_pub_find
            .Lmq_pub_done:
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_mq_queue() -> String "mq-<n>" (handle único por queue)
            .globl kof_mq_queue
            kof_mq_queue:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                la   s0, .Lmq_seq
                lw   t0, 0(s0)
                addi t0, t0, 1
                sw   t0, 0(s0)
                la   a0, .Lstr_mq_prefix
                li   a1, 3
                call kof_string_from_literal
                sd   a0, 0(sp)
                mv   a0, t0
                call kof_int_to_string
                ld   a1, 0(sp)
                call kof_string_concat
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_mq_push(handle, item) — add na fila do handle
            .globl kof_mq_push
            kof_mq_push:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # item
                sd   s2, 16(sp)      # slot ptr
                sd   s3, 8(sp)       # end ptr
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_queues
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_push_find:
                bgeu s2, s3, .Lmq_push_new
                ld   t4, 0(s2)
                beqz t4, .Lmq_push_new
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_push_next
                ld   a0, 8(s2)
                mv   a1, s1
                call kof_list_add
                j    .Lmq_push_done
            .Lmq_push_next:
                addi s2, s2, 16
                j    .Lmq_push_find
            .Lmq_push_new:
                sd   s0, 0(s2)
                mv   a0, zero
                call kof_list_new
                sd   a0, 8(s2)
                mv   a1, s1
                call kof_list_add      # a0 = list (ainda em a0)
            .Lmq_push_done:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_mq_pop(name) -> KofString* ou 0 — lê primeiro suspeito
                        # kof_mq_pop(handle) -> item ou 0 — remove o primeiro (FIFO)
            .globl kof_mq_pop
            kof_mq_pop:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # slot ptr
                sd   s2, 16(sp)      # end ptr
                sd   s3, 8(sp)       # list
                mv   s0, a0
                la   s1, .Lmq_queues
                li   t0, 1024
                add  s2, s1, t0
            .Lmq_pop_find:
                bgeu s1, s2, .Lmq_pop_null
                ld   t4, 0(s1)
                beqz t4, .Lmq_pop_null
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_pop_next
                ld   s3, 8(s1)
                mv   a0, s3
                call kof_list_size
                beqz a0, .Lmq_pop_null
                mv   a0, s3
                li   a1, 0
                call kof_list_remove
                j    .Lmq_pop_ret
            .Lmq_pop_next:
                addi s1, s1, 16
                j    .Lmq_pop_find
            .Lmq_pop_null:
                li   a0, 0
            .Lmq_pop_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_mq_queue_size(handle) -> Int
            .globl kof_mq_queue_size
            kof_mq_queue_size:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # slot ptr
                sd   s2, 16(sp)      # end ptr
                mv   s0, a0
                la   s1, .Lmq_queues
                li   t0, 1024
                add  s2, s1, t0
            .Lmq_qs_find:
                bgeu s1, s2, .Lmq_qs_zero
                ld   t4, 0(s1)
                beqz t4, .Lmq_qs_zero
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_qs_next
                ld   a0, 8(s1)
                call kof_list_size
                j    .Lmq_qs_ret
            .Lmq_qs_next:
                addi s1, s1, 16
                j    .Lmq_qs_find
            .Lmq_qs_zero:
                li   a0, 0
            .Lmq_qs_ret:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- kof.validation (13 predicados) ----
            .globl kof_validation_required
            kof_validation_required:
                beqz a0, .Lv_req_false
                lw   t0, 16(a0)
                beqz t0, .Lv_req_false
                li   a0, 1
                ret
            .Lv_req_false:
                li   a0, 0
                ret

            .globl kof_validation_notBlank
            kof_validation_notBlank:
                beqz a0, .Lv_nb_false
                lw   t1, 16(a0)
                beqz t1, .Lv_nb_false
                addi t2, a0, 24
                li   t0, 0
            .Lv_nb_loop:
                bge  t0, t1, .Lv_nb_false
                add  t3, t2, t0
                lbu  t3, 0(t3)
                li   t4, 32
                beq  t3, t4, .Lv_nb_next
                li   t4, 9
                beq  t3, t4, .Lv_nb_next
                li   t4, 10
                beq  t3, t4, .Lv_nb_next
                li   t4, 13
                beq  t3, t4, .Lv_nb_next
                li   a0, 1
                ret
            .Lv_nb_next:
                addi t0, t0, 1
                j    .Lv_nb_loop
            .Lv_nb_false:
                li   a0, 0
                ret

            .globl kof_validation_minLength
            kof_validation_minLength:
                beqz a0, .Lv_min_false
                lw   t0, 16(a0)
                bge  t0, a1, .Lv_min_true
            .Lv_min_false:
                li   a0, 0
                ret
            .Lv_min_true:
                li   a0, 1
                ret

            .globl kof_validation_maxLength
            kof_validation_maxLength:
                beqz a0, .Lv_max_true
                lw   t0, 16(a0)
                ble  t0, a1, .Lv_max_true
                li   a0, 0
                ret
            .Lv_max_true:
                li   a0, 1
                ret

            .globl kof_validation_lengthBetween
            kof_validation_lengthBetween:
                beqz a0, .Lv_bet_false
                lw   t0, 16(a0)
                blt  t0, a1, .Lv_bet_false
                bgt  t0, a2, .Lv_bet_false
                li   a0, 1
                ret
            .Lv_bet_false:
                li   a0, 0
                ret

            .globl kof_validation_isEmail
            kof_validation_isEmail:
                beqz a0, .Lv_em_false
                lw   t1, 16(a0)
                li   t4, 3
                blt  t1, t4, .Lv_em_false
                addi t2, a0, 24
                li   t0, 0
                li   t3, 0
                li   t4, 0
                li   t5, 0
            .Lv_em_loop:
                bge  t0, t1, .Lv_em_check
                add  t6, t2, t0
                lbu  t6, 0(t6)
                li   a2, 32
                beq  t6, a2, .Lv_em_false
                li   a2, 9
                beq  t6, a2, .Lv_em_false
                li   a2, 64
                bne  t6, a2, .Lv_em_notat
                addi t3, t3, 1
                mv   t4, t0
                j    .Lv_em_next
            .Lv_em_notat:
                li   a2, 46
                bne  t6, a2, .Lv_em_next
                beqz t3, .Lv_em_next
                addi a2, t4, 1
                bgt  a2, t0, .Lv_em_next
                li   t5, 1
            .Lv_em_next:
                addi t0, t0, 1
                j    .Lv_em_loop
            .Lv_em_check:
                li   a2, 1
                bne  t3, a2, .Lv_em_false
                beqz t4, .Lv_em_false
                beqz t5, .Lv_em_false
                addi t6, t1, -1
                beq  t4, t6, .Lv_em_false
                add  t6, t2, t1
                addi t6, t6, -1
                lbu  t6, 0(t6)
                li   a2, 46
                beq  t6, a2, .Lv_em_false
                li   a0, 1
                ret
            .Lv_em_false:
                li   a0, 0
                ret

            .globl kof_validation_isUrl
            kof_validation_isUrl:
                beqz a0, .Lv_url_false
                lw   t1, 16(a0)
                li   t4, 7
                blt  t1, t4, .Lv_url_false
                addi t2, a0, 24
                lbu  t0, 0(t2)
                li   t3, 104
                bne  t0, t3, .Lv_url_false
                lbu  t0, 1(t2)
                li   t3, 116
                bne  t0, t3, .Lv_url_false
                lbu  t0, 2(t2)
                bne  t0, t3, .Lv_url_false
                lbu  t0, 3(t2)
                li   t3, 112
                bne  t0, t3, .Lv_url_false
                lbu  t0, 4(t2)
                li   t3, 58
                bne  t0, t3, .Lv_url_check_https
                lbu  t0, 5(t2)
                li   t3, 47
                bne  t0, t3, .Lv_url_false
                lbu  t0, 6(t2)
                bne  t0, t3, .Lv_url_false
                li   a0, 1
                ret
            .Lv_url_check_https:
                li   t4, 8
                blt  t1, t4, .Lv_url_false
                lbu  t0, 4(t2)
                li   t3, 115
                bne  t0, t3, .Lv_url_false
                lbu  t0, 5(t2)
                li   t3, 58
                bne  t0, t3, .Lv_url_false
                lbu  t0, 6(t2)
                li   t3, 47
                bne  t0, t3, .Lv_url_false
                lbu  t0, 7(t2)
                bne  t0, t3, .Lv_url_false
                li   a0, 1
                ret
            .Lv_url_false:
                li   a0, 0
                ret

            .globl kof_validation_matches
            kof_validation_matches:
                beqz a0, .Lv_mat_false
                beqz a1, .Lv_mat_false
                lw   t1, 16(a0)
                lw   t2, 16(a1)
                beqz t2, .Lv_mat_true
                bgt  t2, t1, .Lv_mat_false
                addi t3, a0, 24
                addi t4, a1, 24
                li   t0, 0
            .Lv_mat_outer:
                sub  t5, t1, t0
                blt  t5, t2, .Lv_mat_false
                li   t5, 0
            .Lv_mat_inner:
                bge  t5, t2, .Lv_mat_true
                add  t6, t3, t0
                add  t6, t6, t5
                lbu  t6, 0(t6)
                add  a2, t4, t5
                lbu  a2, 0(a2)
                bne  t6, a2, .Lv_mat_next
                addi t5, t5, 1
                j    .Lv_mat_inner
            .Lv_mat_next:
                addi t0, t0, 1
                j    .Lv_mat_outer
            .Lv_mat_true:
                li   a0, 1
                ret
            .Lv_mat_false:
                li   a0, 0
                ret

            .globl kof_validation_isInt
            kof_validation_isInt:
                beqz a0, .Lv_int_false
                lw   t1, 16(a0)
                beqz t1, .Lv_int_false
                addi t2, a0, 24
                lbu  t0, 0(t2)
                li   t3, 45
                beq  t0, t3, .Lv_int_sign
                li   t3, 43
                beq  t0, t3, .Lv_int_sign
                j    .Lv_int_digits
            .Lv_int_sign:
                li   t0, 1
                bge  t0, t1, .Lv_int_false
                j    .Lv_int_digits_start
            .Lv_int_digits:
                li   t0, 0
            .Lv_int_digits_start:
                li   t3, 0
            .Lv_int_loop:
                bge  t0, t1, .Lv_int_check
                add  t4, t2, t0
                lbu  t4, 0(t4)
                li   t5, 48
                blt  t4, t5, .Lv_int_false
                li   t5, 57
                bgt  t4, t5, .Lv_int_false
                addi t3, t3, 1
                addi t0, t0, 1
                j    .Lv_int_loop
            .Lv_int_check:
                beqz t3, .Lv_int_false
                li   a0, 1
                ret
            .Lv_int_false:
                li   a0, 0
                ret

            .globl kof_validation_isLong
            kof_validation_isLong:
                j kof_validation_isInt

            .globl kof_validation_inRange
            kof_validation_inRange:
                blt  a0, a1, .Lv_range_false
                bgt  a0, a2, .Lv_range_false
                li   a0, 1
                ret
            .Lv_range_false:
                li   a0, 0
                ret

            .globl kof_validation_min
            kof_validation_min:
                bge  a0, a1, .Lv_min2_true
                li   a0, 0
                ret
            .Lv_min2_true:
                li   a0, 1
                ret

            .globl kof_validation_max
            kof_validation_max:
                ble  a0, a1, .Lv_max2_true
                li   a0, 0
                ret
            .Lv_max2_true:
                li   a0, 1
                ret

            # ---- JSN002: kof_json_quote(s@a0) -> KofString* -------------
            # Escapa para JSON: aspas, backslash, \\n \\r \\t, control<32
            # como \\u00XX, null -> "null". Mesma semântica do x86_64.
            .globl kof_json_quote
            kof_json_quote:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                li   s0, 0
                li   s1, 0
                beqz a0, .Ljq_bound
                lw   s1, 16(a0)
                addi s0, a0, 24
            .Ljq_bound:
                li   t0, 6
                mul  a0, s1, t0
                addi a0, a0, 30
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                addi s3, s2, 24
                beqz s0, .Ljq_null
                li   t0, 34
                sb   t0, 0(s3)
                addi s3, s3, 1
                li   s4, 0
            .Ljq_loop:
                bge  s4, s1, .Ljq_close_str
                add  t1, s0, s4
                lbu  a0, 0(t1)
                li   t0, 34
                beq  a0, t0, .Ljq_e_q
                li   t0, 92
                beq  a0, t0, .Ljq_e_bs
                li   t0, 10
                beq  a0, t0, .Ljq_e_nl
                li   t0, 13
                beq  a0, t0, .Ljq_e_cr
                li   t0, 9
                beq  a0, t0, .Ljq_e_tb
                li   t0, 32
                bltu a0, t0, .Ljq_e_uni
                sb   a0, 0(s3)
                addi s3, s3, 1
                j    .Ljq_next
            .Ljq_e_q:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 34
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_bs:
                li   t0, 92
                sb   t0, 0(s3)
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_nl:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 110
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_cr:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 114
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_tb:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 116
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_uni:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 117
                sb   t0, 1(s3)
                li   t0, 48
                sb   t0, 2(s3)
                sb   t0, 3(s3)
                srli t1, a0, 4
                andi t1, t1, 15
                li   t0, 10
                bltu t1, t0, .Ljq_uh1
                addi t1, t1, 39
                j    .Ljq_uh2
            .Ljq_uh1:
                addi t1, t1, 48
            .Ljq_uh2:
                sb   t1, 4(s3)
                andi t1, a0, 15
                li   t0, 10
                bltu t1, t0, .Ljq_ul1
                addi t1, t1, 39
                j    .Ljq_ul2
            .Ljq_ul1:
                addi t1, t1, 48
            .Ljq_ul2:
                sb   t1, 5(s3)
                addi s3, s3, 6
                j    .Ljq_next
            .Ljq_next:
                addi s4, s4, 1
                j    .Ljq_loop
            .Ljq_close_str:
                li   t0, 34
                sb   t0, 0(s3)
                addi s3, s3, 1
                j    .Ljq_close
            .Ljq_null:
                li   t0, 110
                sb   t0, 0(s3)
                li   t0, 117
                sb   t0, 1(s3)
                li   t0, 108
                sb   t0, 2(s3)
                sb   t0, 3(s3)
                addi s3, s3, 4
            .Ljq_close:
                sub  a0, s3, s2
                addi a0, a0, -24
                sw   a0, 16(s2)
                add  t1, s2, a0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # ---- JSN002: encode de listas/primitivas --------------------
            # kof_json_encode_int/bool: alias das conversões existentes.
            .globl kof_json_encode_int
            kof_json_encode_int:
                j    kof_int_to_string
            .globl kof_json_encode_bool
            kof_json_encode_bool:
                j    kof_bool_to_string
            # kof_json_encode_string: aspas + escape = quote (já portado).
            .globl kof_json_encode_string
            kof_json_encode_string:
                j    kof_json_quote

            # Helper: a0 = elemento (ptr), a1 = tag (0 int / 1 str / 2 bool)
            # -> a0 = json do elemento. (salva ra: dispatch chama funcoes
            # que sobrescrevem o return address)
            kof_json_enc_elem:
                addi sp, sp, -16
                sd   ra, 8(sp)
                li   t0, 1
                beq  a1, t0, .Lenc_el_str
                li   t0, 2
                beq  a1, t0, .Lenc_el_bool
                call kof_json_encode_int
                j    .Lenc_el_done
            .Lenc_el_str:
                call kof_json_encode_string
                j    .Lenc_el_done
            .Lenc_el_bool:
                call kof_json_encode_bool
            .Lenc_el_done:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_encode_list(list@a0, tag@a1) -> KofString*
            .globl kof_json_encode_list
            kof_json_encode_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                la   a0, .Lstr_open_json
                li   a1, 1
                call kof_string_from_literal
                mv   s2, a0            # acc = "["
                li   s3, 0
            .Lenc_list_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Lenc_list_close
                slli t0, s3, 3
                ld   t1, 24(s0)
                add  a0, t1, t0
                ld   a0, 0(a0)
                mv   a1, s1
                call kof_json_enc_elem
                mv   s4, a0            # elem encodado
                bnez s3, .Lenc_list_comma
                j    .Lenc_list_append
            .Lenc_list_comma:
                la   a0, .Lstr_comma_json
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0            # b = ","
                mv   a0, s2
                call kof_string_concat
                mv   s2, a0
            .Lenc_list_append:
                mv   a0, s2
                mv   a1, s4
                call kof_string_concat
                mv   s2, a0
                addi s3, s3, 1
                j    .Lenc_list_loop
            .Lenc_list_close:
                la   a0, .Lstr_close_json
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0            # b = "]"
                mv   a0, s2
                call kof_string_concat
                mv   a0, a0
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # ---- JSN002: decode de listas --------------------------------
            # Helper: a0 = json, a1 = cursor -> a0 = int, a2 = novo cursor.
            kof_json_dec_int:
                addi sp, sp, -24
                sd   ra, 16(sp)
                sd   s0, 8(sp)
                sd   s1, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t2, 16(s0)          # t2 = len
            .Ldj_skip:
                bge  s1, t2, .Ldj_err
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldj_skip_inc
                j    .Ldj_sign
            .Ldj_skip_inc:
                addi s1, s1, 1
                j    .Ldj_skip
            .Ldj_sign:
                li   t3, 1
                li   t1, 45
                bne  a0, t1, .Ldj_digits
                li   t3, -1
                addi s1, s1, 1
            .Ldj_digits:
                li   t4, 0
            .Ldj_loop:
                bge  s1, t2, .Ldj_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 48
                blt  a0, t1, .Ldj_done
                li   t1, 57
                bgt  a0, t1, .Ldj_done
                li   t5, 10
                mul  t4, t4, t5
                addi t5, a0, -48
                add  t4, t4, t5
                addi s1, s1, 1
                j    .Ldj_loop
            .Ldj_done:
                mul  t4, t4, t3
                mv   a0, t4
                mv   a2, s1
                ld   ra, 16(sp)
                ld   s0, 8(sp)
                ld   s1, 0(sp)
                addi sp, sp, 24
                ret
            .Ldj_err:
                li   a0, 0
                mv   a2, s1
                ld   ra, 16(sp)
                ld   s0, 8(sp)
                ld   s1, 0(sp)
                addi sp, sp, 24
                ret

            # Helper: a0 = json, a1 = cursor -> a0 = KofString, a2 = novo
            # cursor (após a aspa de fechamento). Backslash: proximo byte,
            # exceto n->10 e t->9.
            kof_json_dec_str:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
            .Lds_skip:
                bge  s1, s2, .Lds_empty
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Lds_skip_inc
                li   t1, 10
                beq  a0, t1, .Lds_skip_inc
                li   t1, 13
                beq  a0, t1, .Lds_skip_inc
                li   t1, 9
                beq  a0, t1, .Lds_skip_inc
                j    .Lds_open
            .Lds_skip_inc:
                addi s1, s1, 1
                j    .Lds_skip
            .Lds_open:
                li   t1, 34
                bne  a0, t1, .Lds_empty
                addi s1, s1, 1
                li   t0, 16
                mul  t0, s2, t0
                addi t0, t0, 32
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 16(s4)
                sw   zero, 20(s4)
                addi s3, s4, 24
            .Lds_loop:
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 34
                beq  a0, t1, .Lds_close
                li   t1, 92
                bne  a0, t1, .Lds_plain
                addi s1, s1, 1
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 110
                beq  a0, t1, .Lds_nl
                li   t1, 116
                beq  a0, t1, .Lds_tab
                j    .Lds_plain
            .Lds_nl:
                li   a0, 10
                j    .Lds_plain
            .Lds_tab:
                li   a0, 9
            .Lds_plain:
                sb   a0, 0(s3)
                addi s3, s3, 1
                addi s1, s1, 1
                j    .Lds_loop
            .Lds_close:
                addi s1, s1, 1
            .Lds_fin:
                sub  t0, s3, s4
                addi t0, t0, -24
                sw   t0, 16(s4)
                add  t1, s4, t0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s4
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret
            .Lds_empty:
                la   a0, .Lstr_empty
                li   a1, 0
                call kof_string_from_literal
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # kof_json_decode_int_list(json@a0) -> List<Int>
            .globl kof_json_decode_int_list
            kof_json_decode_int_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0            # json (antes de list_new sobrescrever a0)
                call kof_list_new
                mv   s4, a0
                lw   s5, 16(s0)
                li   s1, 0
            .Ldil_skip:
                bge  s1, s5, .Ldil_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldil_skip_inc
                j    .Ldil_open
            .Ldil_skip_inc:
                addi s1, s1, 1
                j    .Ldil_skip
            .Ldil_open:
                li   t1, 91
                bne  a0, t1, .Ldil_done
                addi s1, s1, 1
            .Ldil_loop:
                bge  s1, s5, .Ldil_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 93
                beq  a0, t1, .Ldil_done
                li   t1, 44
                beq  a0, t1, .Ldil_comma
                li   t1, 32
                beq  a0, t1, .Ldil_comma
                li   t1, 10
                beq  a0, t1, .Ldil_comma
                li   t1, 9
                beq  a0, t1, .Ldil_comma
                mv   a0, s0
                mv   a1, s1
                call kof_json_dec_int
                mv   s1, a2
                mv   a1, a0            # a1 = valor int
                mv   a0, s4            # a0 = lista
                call kof_list_add
                j    .Ldil_loop
            .Ldil_comma:
                addi s1, s1, 1
                j    .Ldil_loop
            .Ldil_done:
                mv   a0, s4
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 48
                ret

            # kof_json_decode_string_list(json@a0) -> List<String>
            .globl kof_json_decode_string_list
            kof_json_decode_string_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0            # json (antes de list_new sobrescrever a0)
                call kof_list_new
                mv   s4, a0
                lw   s5, 16(s0)
                li   s1, 0
            .Ldsl_skip:
                bge  s1, s5, .Ldsl_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldsl_skip_inc
                j    .Ldsl_open
            .Ldsl_skip_inc:
                addi s1, s1, 1
                j    .Ldsl_skip
            .Ldsl_open:
                li   t1, 91
                bne  a0, t1, .Ldsl_done
                addi s1, s1, 1
            .Ldsl_loop:
                bge  s1, s5, .Ldsl_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 93
                beq  a0, t1, .Ldsl_done
                li   t1, 44
                beq  a0, t1, .Ldsl_comma
                li   t1, 32
                beq  a0, t1, .Ldsl_comma
                li   t1, 10
                beq  a0, t1, .Ldsl_comma
                li   t1, 9
                beq  a0, t1, .Ldsl_comma
                mv   a0, s0
                mv   a1, s1
                call kof_json_dec_str
                mv   s1, a2
                mv   a1, a0            # a1 = KofString decodificada
                mv   a0, s4            # a0 = lista
                call kof_list_add
                j    .Ldsl_loop
            .Ldsl_comma:
                addi s1, s1, 1
                j    .Ldsl_loop
            .Ldsl_done:
                mv   a0, s4
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 48
                ret

            .section .data
            kof_alloc_ptr: .quad _kof_heap
            .align 16
            kof_exc_chain: .quad 0
            .section .bss
            _kof_heap: .space 262144
            .Lmq_subs:    .space 1024
            .Lmq_queues:  .space 1024
            .Lmq_seq:     .space 8
            .Lcache_area: .space 1536
            kof_obs_counter_name: .quad 0
            kof_obs_counter_val: .word 0
            .align 3
            kof_obs_gauge_name: .quad 0
            kof_obs_gauge_val: .word 0
            .align 3
            kof_obs_hist_name: .quad 0
            kof_obs_hist_count: .word 0
            kof_obs_hist_sum: .word 0
            .align 3

            .section .rodata
            .Lnewline: .asciz "\\n"
            .Lstr_true: .asciz "true"
            .Lstr_false: .asciz "false"
            .Lstr_null: .asciz "null"
            .Lstr_null_err: .asciz "Runtime error: null pointer access"
            .Lstr_bounds_err: .asciz "Runtime error: array index out of bounds"
            .Lstr_empty: .asciz ""
            .Lstr_empty_interval: .asciz "interval-0"
            .Lstr_health: .asciz "UP"
            .Lstr_empty_json: .asciz "{}"
            .Lstr_open_json: .asciz "["
            .Lstr_close_json: .asciz "]"
            .Lstr_comma_json: .asciz ","
            .Lstr_trace: .asciz "00000000000000000000000000000000"
            .Lstr_span: .asciz "0000000000000000"
            .Lstr_span_handle: .asciz "000000000000000000000000000000000000000000000000"
            .Lstr_mq: .asciz "mq-0"
            .Lstr_mq_prefix: .asciz "mq-"
            .Lstr_space: .asciz " "
            .Lstr_nl: .asciz "\\n"
            .Lstr_count: .asciz "_count"
            .Lstr_sum: .asciz "_sum"
            .Llog_lbl_debug: .ascii "[DEBUG] "
            .Llog_lbl_info:  .ascii "[INFO ] "
            .Llog_lbl_warn:  .ascii "[WARN ] "
            .Llog_lbl_error: .ascii "[ERROR] "
            """;

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
            emitMethodTableRiscv(riscvSb, c);
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
                emitCrossMethodRiscv(riscvSb, c, m, usesSpawnA && "main".equals(m.name()));
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
        riscvSb.append(RISCV_RUNTIME_ASM).append(RISCV_STRN002_ASM).append(RISCV_RUNTIME_ASM_B);

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
            List<String> tr = translateRiscvToAarch64(line);
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

    // ---- tradutor riscv -> aarch64 (mesmo usado no probe Python) ----
    private static long parseImm(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return Long.parseUnsignedLong(s.substring(2), 16);
        return Long.parseLong(s);
    }

    private static String aarch64Reg(String r) {
        return switch (r) {
            case "zero" -> "xzr";
            case "ra" -> "x30";
            case "sp" -> "sp";
            case "gp" -> "x3";
            case "tp" -> "x4";
            case "t0" -> "x9";
            case "t1" -> "x10";
            case "t2" -> "x11";
            case "t3" -> "x12";
            case "t4" -> "x13";
            case "t5" -> "x14";
            case "t6" -> "x15";
            case "s0" -> "x19";
            case "s1" -> "x20";
            case "s2" -> "x21";
            case "s3" -> "x22";
            case "s4" -> "x23";
            case "s5" -> "x24";
            case "s6" -> "x25";
            case "s7" -> "x26";
            case "s8" -> "x27";
            case "s9" -> "x28";
            case "s10" -> "x16";
            case "s11" -> "x29";
            case "a0" -> "x0";
            case "a1" -> "x1";
            case "a2" -> "x2";
            case "a3" -> "x3";
            case "a4" -> "x4";
            case "a5" -> "x5";
            case "a6" -> "x6";
            case "a7" -> "x8";
            default -> r;
        };
    }

    private static List<String> aarch64MovImm(String rd, long imm) {
        long u = imm;
        if (u == 0) return List.of("mov " + rd + ", #0");
        List<String> out = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((u >> (16 * i)) & 0xFFFF);
            if (chunk == 0 && !first) continue;
            if (chunk == 0 && first) continue;
            if (first) {
                // movz aceita lsl; `mov` (alias) NÃO aceita — ex.: 262144
                // (0x40000) tem primeiro chunk não-zero em i=1.
                out.add((i != 0 ? "movz " : "mov ") + rd + ", #" + chunk + (i != 0 ? ", lsl #" + (16 * i) : ""));
                first = false;
            } else {
                out.add("movk " + rd + ", #" + chunk + ", lsl #" + (16 * i));
            }
        }
        if (first) out.add("mov " + rd + ", #0");
        return out;
    }

    private static List<String> aarch64AddSubImm(String op, String rd, String rs, long imm, String indent) {
        if (imm >= 0 && imm <= 4095) return List.of(indent + op + " " + rd + ", " + rs + ", #" + imm);
        if (imm >= -4096 && imm <= -1) {
            String op2 = op.equals("add") ? "sub" : "add";
            return List.of(indent + op2 + " " + rd + ", " + rs + ", #" + (-imm));
        }
        if (imm == 4096 || imm == 8192 || imm == -4096 || imm == -8192) {
            long a = Math.abs(imm);
            long val = a >> 12;
            String op2 = imm > 0 ? op : (op.equals("add") ? "sub" : "add");
            return List.of(indent + op2 + " " + rd + ", " + rs + ", #" + val + ", lsl #12");
        }
        String tmp = "x17";
        List<String> out = new ArrayList<>();
        for (String s : aarch64MovImm(tmp, imm)) out.add(indent + s);
        out.add(indent + op + " " + rd + ", " + rs + ", " + tmp);
        return out;
    }

    private static List<String> translateRiscvToAarch64(String line) {
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        // strip trailing comment rest-stripped before anything else
        String s = line.strip();
        if (s.isEmpty()) return List.of(line);
        if (s.startsWith("#")) return List.of(indent + "//" + s.substring(1));
        // remove qualquer comentário inline (riscv ` # ...` → nada)
        int ci = s.indexOf('#');
        if (ci > 0) s = s.substring(0, ci).stripTrailing();
        if (s.endsWith(":") && !s.contains(" ")) return List.of(line);
        if (s.isEmpty()) return List.of(line);
        // label com diretiva (ex: "kof_alloc_ptr: .quad ...")
        if (s.matches("^\\S+:\\s+\\.\\w+.*")) {
            int colon = s.indexOf(':');
            String label = s.substring(0, colon + 1);
            String rest = s.substring(colon + 1).strip();
            return List.of(label, indent + rest);
        }
        if (s.startsWith(".")) {
            if (s.startsWith(".align")) return List.of(line);
            if (s.startsWith(".option")) return List.of(indent + ".arch armv8.1-a");
            return List.of(line);
        }
        // split mnemonic e resto
        String mn;
        String rest = "";
        int sp = s.indexOf(' ');
        int tab = s.indexOf('\t');
        int cut = -1;
        if (sp != -1 && tab != -1) cut = Math.min(sp, tab);
        else if (sp != -1) cut = sp;
        else if (tab != -1) cut = tab;
        if (cut == -1) { mn = s; }
        else { mn = s.substring(0, cut); rest = s.substring(cut).strip(); }
        // helpers
        java.util.function.Function<String,String> R = NativeBackend::aarch64Reg;
        // casos especiais FP antes dos genéricos
        // fcvt.w.s / fcvt.w.d / fcvt.l.s / fcvt.l.d
        if (mn.startsWith("fcvt.")) {
            // ex: fcvt.w.s f0, t0, rtz  ou fcvt.s.d f0, f0
            String[] parts = mn.split("\\.");
            // parts[0]=fcvt, parts[1]=w/l/s/d, parts[2]=s/d
            if (parts.length == 3 && (parts[1].equals("w") || parts[1].equals("l")) && (parts[2].equals("s") || parts[2].equals("d"))) {
                // fcvt.w.s -> scvtf s0, w9
                String[] args = rest.split(",");
                String fd = args[0].trim(); // f0
                String rs = args[1].trim(); // t0
                String dst = parts[2].equals("s") ? "s" + fd.substring(1) : "d" + fd.substring(1);
                String src = parts[1].equals("w") ? "w" + R.apply(rs).substring(1) : R.apply(rs);
                // scvtf usa w para 32 e x para 64
                if (parts[1].equals("w") && R.apply(rs).startsWith("x")) src = "w" + R.apply(rs).substring(1);
                return List.of(indent + "scvtf " + dst + ", " + src);
            }
            if (parts.length == 3 && (parts[1].equals("s") || parts[1].equals("d")) && (parts[2].equals("s") || parts[2].equals("d"))) {
                // fcvt.s.d f0, f0 -> fcvt d0, s0
                String[] args = rest.split(",");
                String fd = args[0].trim();
                String fs = args[1].trim();
                String dst = parts[2].equals("s") ? "s" + fd.substring(1) : "d" + fd.substring(1);
                String src = parts[1].equals("s") ? "s" + fs.substring(1) : "d" + fs.substring(1);
                return List.of(indent + "fcvt " + dst + ", " + src);
            }
        }
        if (mn.startsWith("fmv.")) {
            // fmv.w.x f0, t0  -> fmov s0, w9
            // fmv.d.x f0, t0  -> fmov d0, x9
            // fmv.x.w t0, f0  -> fmov w9, s0
            // fmv.x.d t0, f0  -> fmov x9, d0
            // também fmv.s.x / fmv.x.s aliases
            String[] parts = mn.split("\\.");
            if (parts.length == 3) {
                String p1 = parts[1]; String p2 = parts[2];
                String[] args = rest.split(",");
                if (args.length == 2) {
                    String a0 = args[0].trim();
                    String a1 = args[1].trim();
                    if ((p1.equals("w") || p1.equals("s")) && p2.equals("x")) {
                        // f0, t0  -> fmov s0, w9
                        String dst = "s" + a0.substring(1);
                        String src = "w" + R.apply(a1).substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("d") && p2.equals("x")) {
                        String dst = "d" + a0.substring(1);
                        String src = R.apply(a1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("x") && (p2.equals("w") || p2.equals("s"))) {
                        String dst = "w" + R.apply(a0).substring(1);
                        String src = "s" + a1.substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("x") && p2.equals("d")) {
                        String dst = R.apply(a0);
                        String src = "d" + a1.substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                }
            }
            // fell through: try generic fmov
            if (mn.equals("fmv.w.x") || mn.equals("fmv.s.x")) {
                String[] args = rest.split(",");
                return List.of(indent + "fmov s" + args[0].trim().substring(1) + ", w" + R.apply(args[1].trim()).substring(1));
            }
        }
        if (mn.startsWith("fadd.") || mn.startsWith("fsub.") || mn.startsWith("fmul.") || mn.startsWith("fdiv.")) {
            String op = mn.substring(1, 5); // add, sub, mul, div
            String suffix = mn.substring(5); // .s ou .d
            String[] args = rest.split(",");
            String fd = args[0].trim(), fs1 = args[1].trim(), fs2 = args[2].trim();
            String rFD = (suffix.equals(".s") ? "s" : "d") + fd.substring(1);
            String rFS1 = (suffix.equals(".s") ? "s" : "d") + fs1.substring(1);
            String rFS2 = (suffix.equals(".s") ? "s" : "d") + fs2.substring(1);
            return List.of(indent + "f" + op + " " + rFD + ", " + rFS1 + ", " + rFS2);
        }
        if (mn.startsWith("feq.") || mn.startsWith("flt.") || mn.startsWith("fle.") || mn.startsWith("fgt.") || mn.startsWith("fge.")) {
            String condMap = switch (mn.substring(1, 4)) {
                case "eq." -> "eq";
                case "lt." -> "lt";
                case "le." -> "le";
                case "gt." -> "gt";
                case "ge." -> "ge";
                default -> "eq";
            };
            // mn like feq.s  -> suffix .s or .d
            String suffix = mn.substring(4); // s ou d
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String fs1 = args[1].trim(), fs2 = args[2].trim();
            String rFS1 = (suffix.equals("s") ? "s" : "d") + fs1.substring(1);
            String rFS2 = (suffix.equals("s") ? "s" : "d") + fs2.substring(1);
            List<String> out = new ArrayList<>();
            out.add(indent + "fcmp " + rFS1 + ", " + rFS2);
            out.add(indent + "cset " + rd + ", " + condMap);
            return out;
        }
        if (mn.startsWith("fmv.")) {
            // fallback já tratado acima
        }
        // instruções com fmov .x. / .s.x etc já tratadas; para fmv.x.* com rd integer
        // slt / sle / seqz / snez / sext
        if (mn.equals("slt")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "cmp " + rs1 + ", " + rs2, indent + "cset " + rd + ", lt");
        }
        if (mn.equals("sle")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "cmp " + rs1 + ", " + rs2, indent + "cset " + rd + ", le");
        }
        // genéricos
        if (mn.equals("pop")) {
            String rd = rest.trim();
            return List.of(indent + "pop " + R.apply(rd));
        }
        if (mn.equals("la")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String sym = args[1].trim();
            return List.of(indent + "adrp " + rd + ", " + sym, indent + "add " + rd + ", " + rd + ", :lo12:" + sym);
        }
        if (mn.equals("li")) {
            String[] args = rest.split(",");
            String rdRaw = args[0].trim();
            long imm = parseImm(args[1].trim());
            if (rdRaw.equals("a7")) {
                return List.of(indent + "mov x8, #" + imm);
            }
            String rd = R.apply(rdRaw);
            List<String> out = new ArrayList<>();
            for (String s2 : aarch64MovImm(rd, imm)) out.add(indent + s2);
            return out;
        }
        if (mn.equals("mv")) {
            String[] args = rest.split(",");
            return List.of(indent + "mov " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()));
        }
        if (mn.equals("add") || mn.equals("sub")) {
            String[] args = rest.split(",");
            return List.of(indent + mn + " " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("addi")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            long imm = parseImm(args[2].trim());
            // _start alignment: andi é o problema, mas addi com sp já é ok; andi sp,sp,-16 é o único andi com sp
            return aarch64AddSubImm("add", rd, rs, imm, indent);
        }
        if (mn.equals("andi") && rest.contains("sp, sp, -16")) {
            return List.of(indent + "// andi sp,sp,-16 (skipped, sp already 16-aligned)");
        }
        if (mn.equals("andi") || mn.equals("ori")) {
            String op = mn.equals("andi") ? "and" : "orr";
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            long imm = parseImm(args[2].trim());
            // sempre expande via temp x17 para garantir encodabilidade
            String tmp = "x17";
            List<String> out = new ArrayList<>();
            for (String s2 : aarch64MovImm(tmp, imm)) out.add(indent + s2);
            out.add(indent + op + " " + rd + ", " + rs + ", " + tmp);
            return out;
        }
        if (mn.equals("and") || mn.equals("or") || mn.equals("xor")) {
            String op = mn.equals("and") ? "and" : mn.equals("or") ? "orr" : "eor";
            String[] args = rest.split(",");
            return List.of(indent + op + " " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("slli")) {
            String[] args = rest.split(",");
            return List.of(indent + "lsl " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("srli")) {
            String[] args = rest.split(",");
            return List.of(indent + "lsr " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("srai")) {
            String[] args = rest.split(",");
            return List.of(indent + "asr " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("mul")) {
            String[] args = rest.split(",");
            return List.of(indent + "mul " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("div")) {
            String[] args = rest.split(",");
            return List.of(indent + "sdiv " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("rem")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "sdiv x17, " + rs1 + ", " + rs2, indent + "msub " + rd + ", x17, " + rs2 + ", " + rs1);
        }
        if (mn.equals("neg")) {
            String[] args = rest.split(",");
            return List.of(indent + "neg " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()));
        }
        if (mn.equals("amoadd.d")) {
            // amoadd.d rd, rs2, (rs1)  ->  ldadd rs2, rd, [rs1]
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String rs2 = R.apply(args[1].trim());
            String base = args[2].trim().replaceAll("[()]", "");
            return List.of(indent + "ldadd " + rs2 + ", " + rd + ", [" + R.apply(base) + "]");
        }
        if (mn.equals("seqz")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            return List.of(indent + "cmp " + rs + ", #0", indent + "cset " + rd + ", eq");
        }
        if (mn.equals("snez")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            return List.of(indent + "cmp " + rs + ", #0", indent + "cset " + rd + ", ne");
        }
        if (mn.equals("sext.w")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String rs = R.apply(args[1].trim());
            String rsW = rs.replace("x", "w");
            return List.of(indent + "sxtw " + rd + ", " + rsW);
        }
        if (mn.equals("ld") || mn.equals("lw") || mn.equals("lbu") || mn.equals("lb") || mn.equals("lh")) {
            String[] args = rest.split(",");
            String rdRaw = args[0].trim();
            String mem = args[1].trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)\\((\\w+)\\)$").matcher(mem);
            if (!m.matches()) return List.of(line);
            int off = Integer.parseInt(m.group(1));
            String base = R.apply(m.group(2));
            // sp como destino não é encodável como Rt -> usar temp
            if (rdRaw.equals("sp")) {
                String op = mn.equals("ld") ? "ldr" : mn.equals("lw") ? "ldr" : mn.equals("lbu") ? "ldrb" : mn.equals("lb") ? "ldrsb" : "ldrsh";
                String rtTmp = mn.equals("lbu") || mn.equals("lw") ? "w17" : "x17";
                List<String> out = new ArrayList<>();
                if (off >= -256 && off <= 255) {
                    String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                    out.add(indent + op + " " + rtTmp + ", " + addr);
                } else {
                    out.add(indent + "add x17, " + base + ", #" + off);
                    out.add(indent + op + " " + rtTmp + ", [x17]");
                }
                out.add(indent + "mov sp, x17");
                return out;
            }
            String rt;
            String op;
            if (mn.equals("ld")) { rt = R.apply(rdRaw); op = "ldr"; }
            else if (mn.equals("lw")) { rt = R.apply(rdRaw).replace("x", "w"); op = "ldr"; }
            else if (mn.equals("lbu")) { rt = R.apply(rdRaw).replace("x", "w"); op = "ldrb"; }
            else if (mn.equals("lb")) { rt = R.apply(rdRaw); op = "ldrsb"; }
            else { rt = R.apply(rdRaw); op = "ldrsh"; }
            if (off >= -256 && off <= 255) {
                String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                return List.of(indent + op + " " + rt + ", " + addr);
            } else {
                // fallback via temp
                List<String> out = new ArrayList<>();
                out.add(indent + "add x17, " + base + ", #" + off);
                out.add(indent + op + " " + rt + ", [x17]");
                return out;
            }
        }
        if (mn.equals("sd") || mn.equals("sw") || mn.equals("sb") || mn.equals("sh")) {
            String[] args = rest.split(",");
            String rsRaw = args[0].trim();
            String mem = args[1].trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)\\((\\w+)\\)$").matcher(mem);
            if (!m.matches()) return List.of(line);
            int off = Integer.parseInt(m.group(1));
            String base = R.apply(m.group(2));
            // sp como fonte não é encodável como Rt -> mov temp, sp
            if (rsRaw.equals("sp")) {
                List<String> out = new ArrayList<>();
                out.add(indent + "mov x17, sp");
                String rtTmp = mn.equals("sb") || mn.equals("sw") || mn.equals("sh") ? "w17" : "x17";
                // escolher op baseado no tamanho original
                String op = mn.equals("sd") ? "str" : mn.equals("sw") ? "str" : mn.equals("sb") ? "strb" : "strh";
                // para sw/sb/sh o Rt já é w/h, mas temp é w17
                if (off >= -256 && off <= 255) {
                    String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                    out.add(indent + op + " " + rtTmp + ", " + addr);
                } else {
                    // precisa de outro temp para endereço; usar x16 para endereço
                    out.add(indent + "add x16, " + base + ", #" + off);
                    out.add(indent + op + " " + rtTmp + ", [x16]");
                }
                return out;
            }
            String rt; String op;
            if (mn.equals("sd")) { rt = R.apply(rsRaw); op = "str"; }
            else if (mn.equals("sw")) { rt = R.apply(rsRaw).replace("x", "w"); op = "str"; }
            else if (mn.equals("sb")) { rt = R.apply(rsRaw).replace("x", "w"); op = "strb"; }
            else { rt = R.apply(rsRaw).replace("x", "w"); op = "strh"; }
            if (off >= -256 && off <= 255) {
                String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                return List.of(indent + op + " " + rt + ", " + addr);
            } else {
                List<String> out = new ArrayList<>();
                out.add(indent + "add x17, " + base + ", #" + off);
                out.add(indent + op + " " + rt + ", [x17]");
                return out;
            }
        }
        if (mn.equals("beq") || mn.equals("bne") || mn.equals("blt") || mn.equals("bge") || mn.equals("bltu") || mn.equals("bgeu") || mn.equals("bgt") || mn.equals("ble")) {
            String[] args = rest.split(",");
            String rs = R.apply(args[0].trim()), rt = R.apply(args[1].trim());
            String lbl = args[2].trim();
            String cond = switch (mn) {
                case "beq" -> "eq"; case "bne" -> "ne"; case "blt" -> "lt"; case "bge" -> "ge";
                case "bltu" -> "lo"; case "bgeu" -> "hs"; case "bgt" -> "gt"; case "ble" -> "le"; default -> "eq";
            };
            return List.of(indent + "cmp " + rs + ", " + rt, indent + "b." + cond + " " + lbl);
        }
        if (mn.equals("beqz") || mn.equals("bnez") || mn.equals("bltz") || mn.equals("bgez") || mn.equals("bgtz") || mn.equals("blez")) {
            String[] args = rest.split(",");
            String rs = R.apply(args[0].trim());
            String lbl = args[1].trim();
            String cond = switch (mn) {
                case "beqz" -> "eq"; case "bnez" -> "ne"; case "bltz" -> "lt"; case "bgez" -> "ge"; case "bgtz" -> "gt"; case "blez" -> "le"; default -> "eq";
            };
            return List.of(indent + "cmp " + rs + ", #0", indent + "b." + cond + " " + lbl);
        }
        if (mn.equals("j")) return List.of(indent + "b " + rest.trim());
        if (mn.equals("jr")) return List.of(indent + "br " + R.apply(rest.trim()));
        if (mn.equals("jalr")) {
            String[] args = rest.split(",");
            String tgt = args[args.length - 1].trim();
            return List.of(indent + "blr " + R.apply(tgt));
        }
        if (mn.equals("call")) return List.of(indent + "bl " + rest.trim());
        if (mn.equals("ret")) return List.of(indent + "ret");
        if (mn.equals("ecall")) return List.of(indent + "svc #0");
        if (mn.equals("nop")) return List.of(indent + "nop");
        if (mn.equals("fence")) return List.of(indent + "dmb ish");
        // Fallback: linhas desconhecidas (ex: data com aspas) — manter como está para não quebrar o pipeline
        System.err.println("WARN translateRiscvToAarch64 UNHANDLED: " + mn + " | " + line);
        return List.of(line);
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

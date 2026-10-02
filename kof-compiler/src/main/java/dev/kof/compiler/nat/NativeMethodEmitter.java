package dev.kof.compiler.nat;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPop2;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofExcUnlink;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.NativeRuntime;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.Type;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.TypeMetrics;
import dev.kof.compiler.CompilerTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRBasicBlock;
import java.util.ArrayList;
import java.util.List;

/** F3: emissão de métodos/operações x86 (emitMethod/emitOperation/emitStart). */
final class NativeMethodEmitter {
    private final NativeBackend nb;
    NativeMethodEmitter(NativeBackend nb) { this.nb = nb; }

    void emitMethod(StringBuilder sb, IRClass clazz, IRMethod method) {
        // #133 (§186): <clinit> AGORA é emitido (método estático normal — sem
        // `this`, sem parâmetros); era descartado, e os inicializadores
        // estáticos não-constantes nunca rodavam (ficavam 0/null/undefined).
        // O _start chama cada <clinit> antes de main.

        nb.currentClass = clazz;

        String mangled = NativeSymbolMangling.fnSymbol(clazz.name(), method, nb.allClassesMap);
        nb.functionMangleMap.put(NativeSymbolMangling.fnKey(clazz.name(), method, nb.allClassesMap), mangled);
        sb.append("\n.globl ").append(mangled).append("\n");
        sb.append(".type ").append(mangled).append(", @function\n");
        sb.append(mangled).append(":\n");

        sb.append("    pushq %rbp\n");
        sb.append("    movq %rsp, %rbp\n");

        int maxSlot = method.localVariables().stream()
                .mapToInt(IRLocalVariable::index).max().orElse(0);
        // §541/§542: reserva o rascunho dos args de pilha para TODO call com
        // stack args (não só construtores) — o call-site x86 guarda os args
        // 6+/7+ em slots do frame ANTES de re-empurrá-los. A base do rascunho
        // é logo abaixo dos locais (nb.scratchOffset), então os locais reais
        // nunca são sobrescritos. Antes: offset fixo -256-s*8 colidia com
        // locais >32 slots (corrupção silenciosa no x86).
        int maxCallStackArgs = 0;
        for (IRBasicBlock bb : method.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofCall kc) {
                    // reserva n slots de rascunho (cobre o spill genérico
                    // n-5 e os temporários por-arg do FFI, indexados por i).
                    maxCallStackArgs = Math.max(maxCallStackArgs, kc.parameterTypes().size());
                }
            }
        }
        int localsBytes = (maxSlot + 1) * 8;
        nb.frameLocalsBytes = localsBytes;
        int extraFrame = maxCallStackArgs > 0 ? maxCallStackArgs * 8 : 0;
        int frameSize = Math.max(localsBytes, 16) + extraFrame;
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
            if (nb.usesConcurrency && "main".equals(method.name())) {
                // join implícito: nenhuma tarefa spawnada fica orfa
                sb.append("    call kof_spawn_join_all\n");
            }
            sb.append("    movq %rbp, %rsp\n");
            sb.append("    popq %rbp\n");
            sb.append("    ret\n");
        }
        if (nb.debugInfo) {
            // frente 4 fatia 1/2: fim da funcao p/ DW_AT_high_pc (offset) + registro
            sb.append(".Lfe_").append(mangled).append(":\n");
            int declLine = 1;
            if (method.debugInfo() != null && !method.debugInfo().positions().isEmpty()) {
                for (SourcePosition pos : method.debugInfo().positions().values()) {
                    if (pos.line() > 0 && (declLine == 1 || pos.line() < declLine)) declLine = pos.line();
                }
            }
            java.util.List<NativeDwarf.Local> params = new java.util.ArrayList<>();
            java.util.List<NativeDwarf.Local> locals = new java.util.ArrayList<>();
            for (IRLocalVariable lv : method.localVariables()) {
                if (lv.name() == null || lv.name().isEmpty() || lv.name().startsWith("tmp")
                        || lv.name().startsWith("cap") || lv.name().startsWith("lambda$")) {
                    continue; // temporarios do lowering nao sao nome Kof
                }
                NativeDwarf.Local slot = new NativeDwarf.Local(lv.name(),
                        NativeDwarf.slotOffset(lv.index()), dwarfKindOf(lv.type()));
                if (lv.name().equals("this") || lv.index() < paramSlotMax) {
                    params.add(slot);
                } else {
                    locals.add(slot);
                }
            }
            nb.kofDwarf.add(mangled, method.name(), declLine,
                    dwarfKindOf(method.returnType()), params, locals);
        }
    }

    static String dwarfKindOf(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int" -> "Int";
                case "long" -> "Long";
                case "short" -> "Short";
                case "byte" -> "Byte";
                case "float" -> "Float";
                case "double" -> "Double";
                case "bool", "boolean" -> "Bool";
                case "char" -> "Char";
                case "void" -> "Void";
                default -> "Opaque";
            };
        }
        return "Opaque"; // String/classes/arrays: handle 8B opaco (sem DW_TAG_structure p/ agora)
    }

    @SuppressWarnings("unused")
    void emitOperation(StringBuilder sb, KofOperation op, IRMethod currentMethod) {
        if (nb.debugInfo && currentMethod.debugInfo() != null) {
            SourcePosition dbg = currentMethod.debugInfo().positions().get(op);
            if (dbg != null && dbg.line() > 0) {
                // .loc <file> <line> <col>: o as gera .debug_line (DWARF)
                sb.append("    .loc 1 ").append(dbg.line()).append(" 0\n");
            }
        }
        switch (op) {
            case KofLoadLiteral lit -> {
                nb.lastPushedType = lit.type();
            }
            case KofLoadLocal ll -> {
                nb.lastPushedType = ll.type();
            }
            case KofLoadField lf -> {
                nb.lastPushedType = lf.fieldType();
            }
            case KofArrayLength _ -> {
                nb.lastPushedType = Type.PrimitiveType.INT;
            }
            case KofBinary kb -> {
                nb.lastPushedType = kb.operandType();
            }
            case KofUnary ku -> {
                nb.lastPushedType = ku.operandType();
            }
            case KofCall kc -> {
                nb.lastPushedType = kc.returnType();
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else)
        }

        switch (op) {
            case KofLoadLiteral lit -> nb.emitLoadLiteral(sb, lit);
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
                sb.append("    popq %rdi\n");
                if (BuiltinTypes.isString(lf.ownerType()) && "length".equals(lf.name())) {
                    // String.length conta code units UTF-16 (paridade JVM/JS),
                    // não o byte length @16 (bug 43).
                    sb.append("    call kof_string_length\n");
                    sb.append("    pushq %rax\n");
                    break;
                }
                int offset = nb.resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    movq ").append(offset).append("(%rdi), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofStoreField sf -> {
                sb.append("    popq %rax\n");
                sb.append("    popq %rcx\n");
                int offset = nb.resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    movq %rax, ").append(offset).append("(%rcx)\n");
            }
            case KofBinary kb -> NativeX86Arith.emitBinary(sb, kb);
            case KofUnary ku -> NativeX86Arith.emitUnary(sb, ku);
            case KofReturn _ -> {
                if (nb.usesConcurrency && "main".equals(currentMethod.name())) {
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
            case KofReturnVoid _ -> {
                if (nb.usesConcurrency && "main".equals(currentMethod.name())) {
                    sb.append("    call kof_spawn_join_all\n");
                }
                sb.append("    movq %rbp, %rsp\n");
                sb.append("    popq %rbp\n");
                sb.append("    ret\n");
            }
            case KofLabel kl -> sb.append(nb.resolveLabel(kl.label())).append(":\n");
            case KofCatchStart kcs -> {
                sb.append(nb.resolveLabel(kcs.handlerLabel())).append(":\n");
                sb.append("    addq $32, %rsp\n");
                sb.append("    movq %rdi, -").append((kcs.localIndex() + 1) * 8).append("(%rbp)\n");
            }
            case KofTryStart kts -> {
                sb.append(nb.resolveLabel(kts.startLabel())).append(":\n");
                sb.append("    subq $32, %rsp\n");
                sb.append("    leaq ").append(nb.resolveLabel(kts.handlerLabel())).append("(%rip), %rax\n");
                sb.append("    movq %rax, 0(%rsp)\n");
                sb.append("    movq %rsp, 8(%rsp)\n");
                sb.append("    movq %rbp, 16(%rsp)\n");
                sb.append("    movq %fs:kof_exc_chain@tpoff, %rcx\n");
                sb.append("    movq %rcx, 24(%rsp)\n");
                sb.append("    movq %rsp, %fs:kof_exc_chain@tpoff\n");
            }
            case KofStatementIf _ -> {
                // §267: marcador de if de statement (uso exclusivo do dispatcher JS) — no-op
            }
            case KofContinueLabel _ -> {
                // §266: marcador estrutural (fronteira corpo/update do for) — no-op
            }
            case KofExcUnlink _ -> {
                // §549/§551: pop de handler control-flow (caminho normal do try,
                // break/continue/return que atravessam região). A base do frame
                // NÃO é necessariamente `rsp` — lê do TOPO da cadeia (o TLS
                // kof_exc_chain guarda a base salva no KofTryStart), religa o
                // prev e restaura rsp antes de transferir o controle.
                sb.append("    movq %fs:kof_exc_chain@tpoff, %rax\n");
                sb.append("    movq 24(%rax), %rcx\n");
                sb.append("    movq %rcx, %fs:kof_exc_chain@tpoff\n");
                sb.append("    movq %rax, %rsp\n");
                sb.append("    addq $32, %rsp\n");
            }
            case KofTryEnd _ -> {
                sb.append("    movq 24(%rsp), %rcx\n");
                sb.append("    movq %rcx, %fs:kof_exc_chain@tpoff\n");
                sb.append("    addq $32, %rsp\n");
            }
            case KofJump kj -> sb.append("    jmp ").append(nb.resolveLabel(kj.target())).append("\n");
            case KofConditionalJump kc -> nb.emitConditionalJump(sb, kc);
            case KofCall kc -> nb.emitCall(sb, kc);
            case KofFunctionAddress fa -> NativeOpHelpers.emitFunctionAddress(nb, sb, fa);
            case KofNewObject no -> nb.emitNewObject(sb, no);
            case KofDup _ -> sb.append("    movq (%rsp), %rax\n    pushq %rax\n");
            case KofDup2 _ -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %r11
                    pushq %r11
                    pushq %rax
                    """);
            case KofDupX1 _ -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %r11
                    pushq %rax
                    pushq %r11
                    pushq %rax
                """.stripIndent());
            case KofDupX2 _ -> sb.append("""
                    movq (%rsp), %rax
                    movq 8(%rsp), %r11
                    movq 16(%rsp), %rcx
                    pushq %rax
                    pushq %rcx
                    pushq %r11
                    pushq %rax
                """.stripIndent());
            case KofPop _ -> sb.append("    addq $8, %rsp\n");
            // §142 (12/09): no nativo TODO valor de pilha é 1 qword — inclusive
            // Long/Double (o 2º slot da convenção JVM não existe aqui; o frame
            // de LOCAIS reserva 2 slots, mas o valor empilhado é 1). O POP2
            // herdado do JVM (addq $16) desbalanceava a pilha e o push seguinte
            // pisava no local — `mapOf(_,1L).put(_,2L); println(m.size)` dava
            // SIGSEGV (o mapa lido era o System.out). Descartar 1 qword.
            case KofPop2 _ -> sb.append("    addq $8, %rsp\n");
            case KofGetStatic gs -> {
                // campo estático (bug 41): slot global no .data, não no objeto.
                String sym = nb.staticSymbol(nb.staticKey(gs.ownerType()), gs.name());
                sb.append("    leaq ").append(sym).append("(%rip), %rax\n");
                sb.append("    movq 0(%rax), %rax\n");
                sb.append("    pushq %rax\n");
            }
            case KofPutStatic ps -> {
                String sym = nb.staticSymbol(nb.staticKey(ps.ownerType()), ps.name());
                sb.append("    popq %rax\n");
                sb.append("    leaq ").append(sym).append("(%rip), %rcx\n");
                sb.append("    movq %rax, 0(%rcx)\n");
            }
            case KofCheckCast _ -> { }
            case KofInstanceOf io -> {
                int targetTypeId = 0;
                if (BuiltinTypes.isString(io.type())) {
                    targetTypeId = NativeRuntime.KOF_STRING_TYPE_ID;
                } else if (io.type() instanceof Type.ClassType ct) {
                    for (IRClass clazz : nb.allClassesMap.values()) {
                        if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                                || ct.name().endsWith("/" + clazz.name()) || ct.name().equals(nb.sanitizeName(clazz.name()))) {
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
            case KofNewArray na -> nb.emitNewArray(sb, na);
            case KofNewMultiArray ma -> nb.emitNewMultiArray(sb, ma);
            case KofArrayLoad al -> nb.emitArrayLoad(sb, al);
            case KofArrayStore as -> nb.emitArrayStore(sb, as);
            case KofArrayLength _ -> nb.emitArrayLength(sb);
            case KofThrow _ -> {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_throw_string\n");
            }
            default -> throw new UnsupportedOperationException(
                    "operation with no x86 lowering: " + op.getClass().getSimpleName()
                    + " (R6: never silent) in method " + currentMethod.name());
        }
    }

    void emitStart(StringBuilder sb, IRClass clazz) {
        if (nb.bios) {
            emitStartBios(sb, clazz);
            return;
        }
        if (nb.uefi) {
            emitStartUefi(sb, clazz);
            return;
        }
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        if (nb.freestanding) {
            // B-1: pilha vinda do linker script, nao a que o carregador deu.
            sb.append("    leaq __kof_stack_top(%rip), %rsp\n");
        }
        // G-6b (16/09): fundo da pilha da thread main (rsp na entrada, antes de
        // qualquer push) — o kof_gc_mark varre a pilha INTEIRA ate aqui, nao so
        // o frame corrente (causa (1) do §260: String viva no frame de main
        // enquanto um helper aloca era INVISIVEL ao mark -> sweep liberava vivo)
        sb.append("    movq %rsp, kof_main_stack_bottom(%rip)\n");
        // grava o TID do main thread — limita GC ao main (B-0: via costura kof_plat_)
        sb.append("    call kof_plat_thread_id\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (mainHasArgs) {
            // N3: passa array vazio — evita segfault ao tratar argc como ponteiro
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        // #133 (§186): chama cada <clinit> antes do main (ordem de classes no
        // módulo — link-ordem estática; não há dependência dinâmica declarada).
        emitClinitCalls(sb);
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // #431: com externs bindados a libc flusha o stdio DA C antes do
        // exit_group cru — puts/printf da lib ficam no buffer do processo e
        // um _start sem atexit() perde tudo (medição 19/09: puts retornava 10
        // e a linha nunca aparecia). Sem externs: binário não toca libc.
        if (!nb.ffiLibs.isEmpty()) {
            // fflush(NULL): rdi PRECISA ser 0 (lixo = SEGV, medido 19/09) e o
            // glibc usa SSE com stack 16-align — no _start cru o %rsp não é
            // garantido; mesma técnica do shim `pow` (guarda rbp-rsp em rbx,
            // andq $-16, chama, restaura).
            sb.append("    movq %rsp, %rbx\n");
            sb.append("    andq $-16, %rsp\n");
            sb.append("    xorl %edi, %edi\n");
            sb.append("    call fflush@PLT\n");
            sb.append("    movq %rbx, %rsp\n");
        }
        // M32.3: SYS_exit_group (231) — SYS_exit (60) só mata a thread
        // chamadora; com threads do driver Vulkan o processo fica pendurado.
        // B-0 (D-BAREMETAL-BOOT): a saída cruza a costura kof_plat_exit_group.
        sb.append("    xorl %edi, %edi\n");
        sb.append("    call kof_plat_exit_group\n");
    }

    private void emitClinitCalls(StringBuilder sb) {
        for (IRClass c : nb.allClassesMap.values()) {
            for (IRMethod m : c.methods()) {
                if ("<clinit>".equals(m.name())) {
                    sb.append("    call ").append(NativeSymbolMangling.fnSymbol(
                            c.name(), m.name(), m.parameterTypes(), nb.allClassesMap)).append("\n");
                }
            }
        }
    }

    /**
     * B-2 (PLAN-BAREMETAL-BOOT): entry UEFI x86_64 — PE32+ com
     * {@code objcopy --target=pei-x86-64} (NativeAssembler). UEFI x86_64 é
     * MS x64: {@code RCX=ImageHandle, RDX=SystemTable} (medição B-2: chamar
     * OutputString com This/String em RCX/RDX + rsp%16==0 na chamada). O
     * fim é {@code BootServices->Exit} via a costura ({@code RuntimeUefi}) —
     * nunca retorna, análogo ao {@code exit_group} do perfil host.
     */
    private void emitStartUefi(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.globl _start\n");
        sb.append("_start:\n");
        // MS x64: RCX=ImageHandle, RDX=SystemTable -> globals (RuntimeUefi).
        sb.append("    call kof_efi_save_args\n");
        // G-6b: fundo da pilha do main ANTES do alinhamento (mesmo contrato do host).
        sb.append("    movq %rsp, kof_main_stack_bottom(%rip)\n");
        sb.append("    andq $-16, %rsp\n");       // o firmware entra MS: rsp%16==8
        sb.append("    call kof_plat_thread_id\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (nb.rings) sb.append("    call kof_rings_init\n    call kof_rings_selftest\n    call kof_ring1_selftest\n    call kof_ring1_gp_selftest\n    call kof_ring1_sabotage_selftest\n    call kof_rings_restore\n"); // B-6.1/B-6.2a/B-6.3
        if (mainHasArgs) {
            // N3: array vazio — mesmo contrato do _start host.
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        emitClinitCalls(sb);
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // Saída pela costura: no UEFI o corpo DEVOLVE o status em RAX
        // (retorno ao StartImage) — o _start termina com ret, nunca cai fora.
        sb.append("    xorl %edi, %edi\n");
        sb.append("    call kof_plat_exit_group\n");
        sb.append("    ret\n");
    }

    /**
     * B-3 (PLAN-BAREMETAL-BOOT): entry LEGACY BIOS. A emissão do setor de
     * boot (modo real 16-bit, carga do payload e long mode) vive em
     * {@link NativeBiosBootEmitter} (gate <=500 linhas); aqui só delegamos.
     * B-3b-3: o entry do PROGRAMA Kof é emitido como {@code kof_payload_entry}
     * (mesmo corpo do _start freestanding; rótulo distinto evita colisão com
     * o {@code _start} do setor de boot) — o boot salta para ele após copiar
     * o staging para a base 0x100000, tornando o programa REACHABLE (o
     * --gc-sections não poda mais o runtime).
     */
    private void emitStartBios(StringBuilder sb, IRClass clazz) {
        NativeBiosBootEmitter.emit(sb, clazz);
        emitPayloadEntry(sb, clazz);
    }

    private void emitPayloadEntry(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        boolean mainHasArgs = clazz.methods().stream()
                .filter(m -> "main".equals(m.name()))
                .anyMatch(m -> !m.parameterTypes().isEmpty());
        sb.append("\n.section .text\n");
        sb.append(".globl kof_payload_entry\n");
        sb.append("kof_payload_entry:\n");
        // B-1: pilha vinda do linker script (arena de heap+pilha do payload).
        sb.append("    leaq __kof_stack_top(%rip), %rsp\n");
        // G-6b: fundo da pilha da thread main — contrato idêntico ao _start.
        sb.append("    movq %rsp, kof_main_stack_bottom(%rip)\n");
        sb.append("    call kof_plat_thread_id\n");
        sb.append("    movq %rax, kof_main_tid(%rip)\n");
        if (mainHasArgs) {
            // N3: array vazio — mesmo contrato do _start host.
            sb.append("    xorl %edi, %edi\n");
            sb.append("    movl $8, %esi\n");
            sb.append("    call kof_array_alloc\n");
            sb.append("    movq %rax, %rdi\n");
        }
        emitClinitCalls(sb);
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // B-3b-3: a saída cruza a costura kof_plat_exit_group (corpo BIOS:
        // cli;hlt — nunca retorna ao boot).
        sb.append("    xorl %edi, %edi\n");
        sb.append("    call kof_plat_exit_group\n");
    }

}
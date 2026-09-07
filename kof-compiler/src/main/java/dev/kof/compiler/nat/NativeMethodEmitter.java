package dev.kof.compiler.nat;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofThrow;
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
        if ("<clinit>".equals(method.name())) return;

        nb.currentClass = clazz;

        String mangled = nb.sanitizeName(clazz.name()) + "_" + nb.sanitizeName(method.name());
        if ("<init>".equals(method.name())) {
            mangled += "_" + method.parameterTypes().size();
        }
        nb.functionMangleMap.put(method.name(), mangled);
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
            if (nb.usesConcurrency && "main".equals(method.name())) {
                // join implícito: nenhuma tarefa spawnada fica orfa
                sb.append("    call kof_spawn_join_all\n");
            }
            sb.append("    movq %rbp, %rsp\n");
            sb.append("    popq %rbp\n");
            sb.append("    ret\n");
        }
    }

    void emitOperation(StringBuilder sb, KofOperation op, IRMethod currentMethod) {
        if (nb.debugInfo && currentMethod.debugInfo() != null) {
            SourcePosition dbg = currentMethod.debugInfo().positions().get(op);
            if (dbg != null && dbg.line() > 0) {
                // .loc <file> <line> <col>: o as gera .debug_line (DWARF)
                sb.append("    .loc 1 ").append(dbg.line()).append(" 0\n");
            }
        }
        if (op instanceof KofLoadLiteral lit) {
            nb.lastPushedType = lit.type();
        } else if (op instanceof KofLoadLocal ll) {
            nb.lastPushedType = ll.type();
        } else if (op instanceof KofLoadField lf) {
            nb.lastPushedType = lf.fieldType();
        } else if (op instanceof KofArrayLength) {
            nb.lastPushedType = Type.PrimitiveType.INT;
        } else if (op instanceof KofBinary kb) {
            nb.lastPushedType = kb.operandType();
        } else if (op instanceof KofUnary ku) {
            nb.lastPushedType = ku.operandType();
        } else if (op instanceof KofCall kc) {
            nb.lastPushedType = kc.returnType();
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
                sb.append("    popq %rax\n");
                int offset = nb.resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    movq ").append(offset).append("(%rax), %rax\n");
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
            case KofReturn kr -> {
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
            case KofReturnVoid rv -> {
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
                sb.append("    movq kof_exc_chain(%rip), %rcx\n");
                sb.append("    movq %rcx, 24(%rsp)\n");
                sb.append("    movq %rsp, kof_exc_chain(%rip)\n");
            }
            case KofTryEnd kte -> {
                sb.append("    movq 24(%rsp), %rcx\n");
                sb.append("    movq %rcx, kof_exc_chain(%rip)\n");
                sb.append("    addq $32, %rsp\n");
            }
            case KofJump kj -> sb.append("    jmp ").append(nb.resolveLabel(kj.target())).append("\n");
            case KofConditionalJump kc -> nb.emitConditionalJump(sb, kc);
            case KofCall kc -> nb.emitCall(sb, kc);
            case KofNewObject no -> nb.emitNewObject(sb, no);
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
            case KofArrayLoad al -> nb.emitArrayLoad(sb, al);
            case KofArrayStore as -> nb.emitArrayStore(sb, as);
            case KofArrayLength al -> nb.emitArrayLength(sb);
            case KofThrow thr -> {
                sb.append("    popq %rdi\n");
                sb.append("    call kof_throw_string\n");
            }
            default -> { }
        }
    }

    void emitStart(StringBuilder sb, IRClass clazz) {
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
        sb.append("    call ").append(nb.sanitizeName(clazz.name())).append("_main\n");
        // M32.3: SYS_exit_group (231) — SYS_exit (60) só mata a thread
        // chamadora; com threads do driver Vulkan o processo fica pendurado.
        sb.append("    movq $231, %rax\n");
        sb.append("    xorq %rdi, %rdi\n");
        sb.append("    syscall\n");
    }

}
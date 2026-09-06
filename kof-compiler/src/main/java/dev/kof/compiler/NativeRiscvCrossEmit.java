package dev.kof.compiler;

/**
 *  * Emissão cross riscv64 (parte 1): método, vtable, dispatch de ops,
 * Extraído verbatim de NativeBackend (FASE 3, REFACTOR-500); estado do
 * backend acessado via campo `nb` (padrão CompilerClassLowering).
 */
final class NativeRiscvCrossEmit {

    private final NativeBackend nb;

    private final NativeRiscvCrossOps other;

    NativeRiscvCrossEmit(NativeBackend nb) { this.nb = nb; this.other = new NativeRiscvCrossOps(nb); }

    void emitCrossMethodRiscv(StringBuilder sb, IRClass clazz, IRMethod method, boolean joinMain) {
        // Mangle idêntico ao x86_64 (vtables referenciam esses símbolos).
        String mangled = nb.sanitizeName(clazz.name()) + "_" + nb.sanitizeName(method.name());
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

    int crossLocalOffRiscv(int idx) { return -(idx + 1) * 8 - 16; }

    void emitMethodTableRiscv(StringBuilder sb, IRClass clazz) {
        List<String> methods = nb.collectVirtualMethods(clazz);
        sb.append(".align 3\n");
        sb.append(".globl ").append(nb.sanitizeName(clazz.name())).append("_vtable\n");
        sb.append(nb.sanitizeName(clazz.name())).append("_vtable:\n");
        if (methods.isEmpty()) {
            sb.append("    .quad 0\n");
            return;
        }
        for (String m : methods) sb.append("    .quad ").append(m).append("\n");
        sb.append("    .quad 0\n");
    }

    void emitCrossOpRiscv(StringBuilder sb, KofOperation op, int frameSize, boolean joinMain) {
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
                int offset = nb.resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    ld t0, ").append(offset).append("(t0)\n");
                pushRiscv(sb, "t0");
            }
            case KofStoreField sf -> {
                sb.append("    pop t0\n");   // valor
                sb.append("    pop t1\n");   // objeto
                int offset = nb.resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    sd t0, ").append(offset).append("(t1)\n");
            }
            case KofBinary kb -> other.emitCrossBinaryRiscv(sb, kb);
            case KofUnary ku -> emitCrossUnaryRiscv(sb, ku);
            case KofConditionalJump kc -> other.emitCrossCondJumpRiscv(sb, kc);
            case KofLabel kl -> sb.append(nb.resolveLabel(kl.label())).append(":\n");
            case KofJump kj -> sb.append("    j ").append(nb.resolveLabel(kj.target())).append("\n");
            case KofCall kc -> other.emitCrossCallRiscv(sb, kc);
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
                    for (IRClass clazz : nb.allClassesMap.values()) {
                        if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                                || ct.name().endsWith("/" + clazz.name()) || ct.name().equals(nb.sanitizeName(clazz.name()))) {
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
                sb.append("    li a1, ").append(nb.elementTypeSize(na.elementType())).append("\n");
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
                sb.append("    la t0, ").append(nb.resolveLabel(kts.handlerLabel())).append("\n");
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
                sb.append(nb.resolveLabel(kcs.handlerLabel())).append(":\n");
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

    void emitCrossNewObjectRiscv(StringBuilder sb, KofNewObject no) {
        ClassLayout layout = null;
        String className = null;
        int typeId = 0;
        if (no.type() instanceof Type.ClassType ct) {
            className = ct.name();
            for (IRClass clazz : nb.allClassesMap.values()) {
                if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                        || className.endsWith("/" + clazz.name()) || className.equals(nb.sanitizeName(clazz.name()))) {
                    layout = nb.getLayout(clazz);
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
            String mangled = nb.sanitizeName(className);
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

    void emitCrossUnaryRiscv(StringBuilder sb, KofUnary ku) {
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

    void pushRiscv(StringBuilder sb, String reg) {
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd ").append(reg).append(", 0(sp)\n");
    }

    void emitCrossLoadLiteralRiscv(StringBuilder sb, KofLoadLiteral lit) {
        if (lit.value() instanceof String s) {
            String label = nb.internString(s);
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

}

package dev.kof.compiler;

import java.util.List;

/**
 *  * Emissão cross riscv64 (parte 2): binary, cond-jump, call, ABI de regs,
 * Extraído verbatim de NativeBackend (FASE 3, REFACTOR-500); estado do
 * backend acessado via campo `nb` (padrão CompilerClassLowering).
 */
final class NativeRiscvCrossOps {

    private final NativeBackend nb;

    private final NativeRiscvCrossEmit other;

    NativeRiscvCrossOps(NativeBackend nb, NativeRiscvCrossEmit other) { this.nb = nb; this.other = other; }

    void emitCrossBinaryRiscv(StringBuilder sb, KofBinary kb) {
        // b = topo, a = abaixo; resultado = a OP b
        Type opTy = kb.operandType();
        boolean isFloat = NativeTypeKinds.isFloatType(opTy);
        boolean isDouble = NativeTypeKinds.isDoubleType(opTy);
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
                other.pushRiscv(sb, "t0");
            } else if (kb.op() == KofBinaryOp.LT || kb.op() == KofBinaryOp.LE
                    || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.GE) {
                other.pushRiscv(sb, "t0");
            } else {
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                other.pushRiscv(sb, "t0");
            }
            return;
        }
        sb.append("    pop t0\n");   // b
        sb.append("    pop t1\n");   // a
        boolean int32 = NativeTypeKinds.isInt32Type(opTy);
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
        other.pushRiscv(sb, "t1");
    }

    void emitCrossCondJumpRiscv(StringBuilder sb, KofConditionalJump kc) {
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
        sb.append("    ").append(cond).append(" t1, t0, ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
        sb.append("    j ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
    }

    void emitCrossCallRiscv(StringBuilder sb, KofCall kc) {
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
            other.pushRiscv(sb, "a0");
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
                    other.pushRiscv(sb, "a0");
                } else if ("bool".equals(cn) || "boolean".equals(cn)) {
                    sb.append("    pop a0\n    call kof_bool_to_string\n");
                    other.pushRiscv(sb, "a0");
                }
            }
            return;
        }

        // String.length (propriedade → INSTANCE sem args)
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType()) && "length".equals(mn)) {
            sb.append("    pop a0\n    call kof_string_length\n");
            other.pushRiscv(sb, "a0");
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
                if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
                return;
            }
            // replace(char,char) vs replace(String,String): dispatch por tipo
            if ("replace".equals(mn) && kc.parameterTypes().size() == 2) {
                Type first = kc.parameterTypes().get(0);
                boolean charArgs = first instanceof Type.PrimitiveType pt
                        && "char".equals(Type.canonicalPrimitiveName(pt.name()));
                sb.append("    pop a2\n    pop a1\n    pop a0\n");
                sb.append("    call ").append(charArgs ? "kof_string_replace_char" : "kof_string_replace").append("\n");
                other.pushRiscv(sb, "a0");
                return;
            }
            // split(sep) -> String[] (array de KofString)
            if ("split".equals(mn) && kc.parameterTypes().size() == 1) {
                sb.append("    pop a1\n    pop a0\n");
                sb.append("    call kof_string_split\n");
                other.pushRiscv(sb, "a0");
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
            if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
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
            if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
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
            int vtableIdx = nb.findVirtualMethodIndex(ct.name(), mn);
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
                if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
                return;
            }
        }

        // chamada direta (FUNCTION/STATIC de usuário — args em a0..aN, sem receiver)
        int argCount = kc.parameterTypes().size();
        for (int i = argCount - 1; i >= 0; i--) {
            sb.append("    pop ").append(crossArgReg(i)).append("\n");
        }
        sb.append("    call ").append(resolveCalleeNameRiscv(kc)).append("\n");
        if (!Type.isVoid(kc.returnType())) other.pushRiscv(sb, "a0");
    }

    String crossArgReg(int i) {
        String[] regs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        return i < regs.length ? regs[i] : "a7";
    }

    String resolveCalleeNameRiscv(KofCall kc) {
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_") || mn.startsWith("kof_list_")) return mn;
        if (kc.kind() == KofCallKind.FUNCTION) {
            return nb.functionMangleMap.getOrDefault(mn, nb.sanitizeName(mn));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR && kc.ownerType() instanceof Type.ClassType ct) {
            return nb.sanitizeName(ct.name()) + "_" + nb.sanitizeName("<init>") + "_" + kc.parameterTypes().size();
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + mn;
            return nb.functionMangleMap.getOrDefault(key, nb.sanitizeName(ct.name()) + "_" + nb.sanitizeName(mn));
        }
        return nb.sanitizeName(mn);
    }

}

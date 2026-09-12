package dev.kof.compiler.nat;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.Type;

import dev.kof.compiler.*;
import java.util.List;

/** F3: helpers de emissão de ops (condicional, literal, new, resolve). */
final class NativeOpHelpers {
    private NativeOpHelpers() {}

    static void emitNewObject(NativeBackend nb, StringBuilder sb, KofNewObject no) {
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
        sb.append("    movq $").append(size).append(", %rdi\n");
        sb.append("    call kof_alloc\n");
        if (className != null) {
            String mangled = nb.sanitizeName(className);
            sb.append("    movq %rax, %rdi\n");
            sb.append("    movl $").append(typeId).append(", %esi\n");
            sb.append("    leaq ").append(mangled).append("_vtable(%rip), %rdx\n");
            sb.append("    call kof_init_object\n");
        }
        sb.append("    pushq %rax\n");
    }

    static int elementTypeSize(NativeBackend nb, Type elemType) {
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

    static void emitLoadLiteral(NativeBackend nb, StringBuilder sb, KofLoadLiteral lit) {
        if (lit.value() instanceof Integer i) {
            sb.append("    movq $").append(i).append(", %rax\n");
        } else if (lit.value() instanceof Long l) {
            sb.append("    movq $").append(l).append(", %rax\n");
        } else if (lit.value() instanceof Float f) {
            sb.append("    movq $").append(Float.floatToIntBits(f)).append(", %rax\n");
        } else if (lit.value() instanceof Double d) {
            sb.append("    movq $").append(Double.doubleToLongBits(d)).append(", %rax\n");
        } else if (lit.value() instanceof String s) {
            String label = nb.internString(s);
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

    static void emitConditionalJump(NativeBackend nb, StringBuilder sb, KofConditionalJump kc) {
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
                sb.append("    jp ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
                // still need fallback: if NaN, we already jumped to true
            }
            sb.append("    ").append(jmp).append(" ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
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
                sb.append("    jp ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
            } else if (kc.comparison() == KofComparison.NE) {
                sb.append("    jp ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
            }
            sb.append("    ").append(jmp).append(" ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
            sb.append("    jmp ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
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
        sb.append("    ").append(cond).append(" ").append(nb.resolveLabel(kc.trueLabel())).append("\n");
        sb.append("    jmp ").append(nb.resolveLabel(kc.falseLabel())).append("\n");
    }

    static String resolveCalleeName(NativeBackend nb, KofCall kc) {
        // builtins de coleção são símbolos globais do runtime — nunca
        // mangle com o dono (Map_kof_map_put etc.)
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_")) {
            return mn;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            String key = NativeBackend.fnKey(NativeBackend.internalOwner(kc.ownerType()),
                    kc.methodName(), kc.parameterTypes());
            return nb.functionMangleMap.getOrDefault(key, nb.sanitizeName(kc.methodName()));
        }
        if (kc.kind() == KofCallKind.CONSTRUCTOR) {
            if (kc.ownerType() instanceof Type.ClassType ct) {
                return nb.classTypeManglePrefix(ct) + "_" + nb.sanitizeName("<init>") + "_" + kc.parameterTypes().size();
            }
        }
        if (kc.ownerType() instanceof Type.ClassType ct) {
            String key = ct.name() + "." + kc.methodName();
            return nb.functionMangleMap.getOrDefault(key,
                    nb.classTypeManglePrefix(ct) + "_" + nb.sanitizeName(kc.methodName()));
        }
        return nb.sanitizeName(kc.methodName());
    }

    static int resolveFieldOffset(NativeBackend nb, Type ownerType, String fieldName) {
        ClassLayout layout = nb.getLayoutForType(ownerType);
        if (layout != null) {
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        if (nb.currentClass != null) {
            layout = nb.getLayout(nb.currentClass);
            int offset = layout.fieldOffset(fieldName);
            if (offset >= 0) return offset;
        }
        return ClassLayout.HEADER_SIZE;
    }

}
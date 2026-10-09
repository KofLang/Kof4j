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

    /** Classe concreta resolvida no mapa do backend (mesma regra de casamento do
     *  `emitNewObject`, agora compartilhada com o retorno de struct da 3.7). */
    record Resolved(String name, int typeId, ClassLayout layout) {}

    static Resolved resolveClass(NativeBackend nb, Type type) {
        if (!(type instanceof Type.ClassType ct)) return null;
        String className = ct.name();
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                    || className.endsWith("/" + clazz.name()) || className.equals(nb.sanitizeName(clazz.name()))) {
                return new Resolved(clazz.name(), clazz.typeId(), nb.getLayout(clazz));
            }
        }
        return null;
    }

    /** Aloca+inicializa um objeto da classe resolvida; deixa o ponteiro em %rax. */
    static void emitAllocObject(NativeBackend nb, StringBuilder sb, Resolved r) {
        int size = r != null ? r.layout().totalSize() : ClassLayout.HEADER_SIZE + 64;
        sb.append("    movq $").append(size).append(", %rdi\n");
        sb.append("    call kof_alloc\n");
        if (r != null) {
            String mangled = nb.sanitizeName(r.name());
            sb.append("    movq %rax, %rdi\n");
            sb.append("    movl $").append(r.typeId()).append(", %esi\n");
            sb.append("    leaq ").append(mangled).append("_vtable(%rip), %rdx\n");
            sb.append("    call kof_init_object\n");
        }
    }

    static void emitNewObject(NativeBackend nb, StringBuilder sb, KofNewObject no) {
        Resolved r = resolveClass(nb, no.type());
        emitAllocObject(nb, sb, r);
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
        switch (lit.value()) {
            case Integer i -> sb.append("    movq $").append(i).append(", %rax\n");
            case Long l -> sb.append("    movq $").append(l).append(", %rax\n");
            case Float f -> sb.append("    movq $").append(Float.floatToIntBits(f)).append(", %rax\n");
            case Double d -> sb.append("    movq $").append(Double.doubleToLongBits(d)).append(", %rax\n");
            case String s -> {
                String label = nb.internString(s);
                int byteLen = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                sb.append("    leaq ").append(label).append("(%rip), %rdi\n");
                sb.append("    movl $").append(byteLen).append(", %esi\n");
                sb.append("    call kof_string_from_literal\n");
            }
            case Boolean b -> sb.append("    movq $").append(b ? 1 : 0).append(", %rax\n");
            case null -> sb.append("    movq $0, %rax\n");
            default -> { }
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
            // NaN handling: ordered compares must be false when unordered (PF=1).
            // §101: LT também (jb com CF=1 no NaN daria true) — igual ao LE/GE.
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LT
                    || kc.comparison() == KofComparison.LE
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
            // §101: LT também (jb com CF=1 no NaN daria true).
            boolean needsOrderedCheck = kc.comparison() == KofComparison.LT
                    || kc.comparison() == KofComparison.LE
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

    /** B-6.2b: {@code ring1(fn)} — empilha o endereco do simbolo da funcao
     *  top-level (leaq <sym>(%rip),%rax; pushq %rax). */
    static void emitFunctionAddress(NativeBackend nb, StringBuilder sb, KofFunctionAddress fa) {
        KofCall ref = new KofCall(fa.ownerType(), fa.name(), fa.parameterTypes(),
                Type.PrimitiveType.VOID, KofCallKind.FUNCTION);
        sb.append("    leaq ").append(resolveCalleeName(nb, ref)).append("(%rip), %rax\n");
        sb.append("    pushq %rax\n");
    }

    static String resolveCalleeName(NativeBackend nb, KofCall kc) {        // builtins de coleção são símbolos globais do runtime — nunca
        // mangle com o dono (Map_kof_map_put etc.)
        String mn = kc.methodName();
        if (mn.startsWith("kof_map_") || mn.startsWith("kof_set_")) {
            return mn;
        }
        if (kc.kind() == KofCallKind.FUNCTION) {
            String key = NativeSymbolMangling.fnKey(NativeSymbolMangling.internalOwner(kc.ownerType()),
                    kc.methodName(), kc.parameterTypes(), nb.allClassesMap);
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

    /**
     * §235 native face: the wrapper statics (`Int.parseInt`, `Double.isNaN`, …)
     * have no JDK class in the native runtime — without this dispatch the
     * generic call emits `java_lang_Integer_parseInt` and the LINK fails
     * (COMP001, R6-honest but not rule-5 parity). Both backends classify the
     * call here and then map it to the existing runtime helpers (parse*) or to
     * an inline IEEE predicate (is*).
     */
    enum WrapperStatic {
        PARSE_INT, PARSE_LONG, PARSE_DOUBLE, PARSE_FLOAT, PARSE_BOOL,
        IS_NAN_D, IS_NAN_F, IS_INF_D, IS_INF_F, IS_FIN_D, IS_FIN_F, NONE
    }

    static WrapperStatic wrapperStatic(KofCall kc) {
        if (kc.kind() != KofCallKind.STATIC) return WrapperStatic.NONE;
        if (kc.parameterTypes().size() != 1) return WrapperStatic.NONE;
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return WrapperStatic.NONE;
        String owner = ct.name();
        boolean d = "Double".equals(owner);
        boolean f = "Float".equals(owner);
        return switch (kc.methodName()) {
            case "parseInt" -> "Integer".equals(owner) ? WrapperStatic.PARSE_INT : WrapperStatic.NONE;
            case "parseLong" -> "Long".equals(owner) ? WrapperStatic.PARSE_LONG : WrapperStatic.NONE;
            case "parseDouble" -> d ? WrapperStatic.PARSE_DOUBLE : WrapperStatic.NONE;
            case "parseFloat" -> f ? WrapperStatic.PARSE_FLOAT : WrapperStatic.NONE;
            case "parseBoolean" -> "Boolean".equals(owner) ? WrapperStatic.PARSE_BOOL : WrapperStatic.NONE;
            case "isNaN" -> d ? WrapperStatic.IS_NAN_D : f ? WrapperStatic.IS_NAN_F : WrapperStatic.NONE;
            case "isInfinite" -> d ? WrapperStatic.IS_INF_D : f ? WrapperStatic.IS_INF_F : WrapperStatic.NONE;
            case "isFinite" -> d ? WrapperStatic.IS_FIN_D : f ? WrapperStatic.IS_FIN_F : WrapperStatic.NONE;
            default -> WrapperStatic.NONE;
        };
    }

    /**
     * #772: {@code Wrapper.valueOf(primitivo)} — o BOX de erasure do slot
     * {@code T?} no native (§284 MAGIC box) — compartilha o nome {@code valueOf}
     * com o açúcar {@code String.valueOf} do println. Sem o DONO, os dois
     * backends convertiam o box para string e o consumidor do slot lia o
     * PONTEIRO MAGIC como inteiro. Devolve o nome da runtime {@code kof_box_*}
     * quando o dono é um wrapper numerico/boolean e o arg é o primitivo CRU
     * (um {@code T?} já chega boxed e não passa por aqui). {@code null} para
     * qualquer outro {@code valueOf} (String etc.).
     */
    static String wrapperValueOfBoxFn(KofCall kc) {
        if (kc.kind() != KofCallKind.STATIC) return null;
        if (!"valueOf".equals(kc.methodName())) return null;
        if (kc.parameterTypes().size() != 1) return null;
        if (!(kc.ownerType() instanceof Type.ClassType ct)) return null;
        String prim = switch (ct.name()) {
            case "Integer", "Short", "Byte", "Character" -> "int";
            case "Long" -> "long";
            case "Double" -> "double";
            case "Float" -> "float";
            case "Boolean" -> "bool";
            default -> null;
        };
        if (prim == null) return null;
        if (kc.parameterTypes().get(0) instanceof Type.NullableType) return null;
        return NativeBoxTags.boxFn(prim);
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

    // ---- emitters de array x86_64 (extraídos do NativeBackend, split 13/09) ----

    static void emitNewArray(NativeBackend nb, StringBuilder sb, KofNewArray na) {
        sb.append("    popq %rdi\n");
        sb.append("    movl $").append(elementTypeSize(nb, na.elementType())).append(", %esi\n");
        sb.append("    call kof_array_alloc\n");
        sb.append("    pushq %rax\n");
    }

    static void emitNewMultiArray(NativeBackend nb, StringBuilder sb, KofNewMultiArray ma) {
        sb.append("    movl $").append(ma.dims()).append(", %edx\n");
        sb.append("    movl $").append(elementTypeSize(nb, ma.baseType())).append(", %ebx\n");
        sb.append("    movl $1, %esi\n");
        sb.append("    call kof_multi_alloc\n");
        sb.append("    addq $").append(8 * ma.dims()).append(", %rsp\n");
        sb.append("    pushq %rax\n");
    }

    static void emitArrayLoad(NativeBackend nb, StringBuilder sb, KofArrayLoad al) {
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_get\n");
        sb.append("    pushq %rax\n");
    }

    static void emitArrayStore(@SuppressWarnings("unused") NativeBackend nb,
            StringBuilder sb, KofArrayStore as) {
        sb.append("    popq %rdx\n");
        sb.append("    popq %rsi\n");
        sb.append("    popq %rdi\n");
        // §187: `Char[]` trunca a 16 bits no store (JVM `CASTORE`). O
        // `elementTypeSize` mantém 4 (stride/aloc/JSON intactos) e o load
        // `movslq` continua correto porque o valor gravado fica em [0,65535].
        if (NativeTypeKinds.isCharType(as.elementType())) {
            sb.append("    movzwl %dx, %edx\n");
        }
        sb.append("    call kof_array_set\n");
    }

    static void emitArrayLength(NativeBackend nb, StringBuilder sb) {
        sb.append("    popq %rdi\n");
        sb.append("    call kof_array_length\n");
        sb.append("    movslq %eax, %rax\n");
        sb.append("    pushq %rax\n");
    }

}
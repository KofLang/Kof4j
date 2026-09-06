package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): emissão x86_64 de calls de String/JSON nativos
 * (length/charAt/substring/contains/…/split + kof_string_to_* +
 * kof_json_*_double/float). Extraído verbatim de NativeBackend.emitCall —
 * 23 ramos mutuamente exclusivos, cada um termina em return; emit() devolve
 * true quando um ramo casou (o chamador retorna). Zero estado do backend.
 */
final class NativeX86StringCalls {

    private NativeX86StringCalls() {}

    static boolean emit(StringBuilder sb, KofCall kc) {
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "length".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_length\n");
            sb.append("    pushq %rax\n");
            return true;
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
            return true;
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
            return true;
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
            return true;
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
            return true;
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
            return true;
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
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "indexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_index_of\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "lastIndexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_last_index_of\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "trim".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_trim\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toUpperCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_upper\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "toLowerCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_lower\n");
            sb.append("    pushq %rax\n");
            return true;
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
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "equalsIgnoreCase".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_equals_ignore_case\n");
            sb.append("    pushq %rax\n");
            return true;
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
            return true;
        }
        if ("kof_string_to_int".equals(kc.methodName())
                || "kof_string_to_long".equals(kc.methodName())) {
            String fn = kc.methodName();
            sb.append("    popq %rdi\n");
            sb.append("    call ").append(fn).append("\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        // retorno FP vive em xmm0 — preservar os bits na pilha
        if ("kof_string_to_double".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_double\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if ("kof_string_to_float".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_float\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        // JSN001: json.decode<Double>/decode<Float> retorna em xmm0
        if ("kof_json_decode_double".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_double\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if ("kof_json_decode_float".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_float\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        // JSN001: json.encode(double) recebe em xmm0 (bits na pilha)
        if ("kof_json_encode_double".equals(kc.methodName())) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    call kof_json_encode_double\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if ("kof_json_encode_float".equals(kc.methodName())) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    call kof_json_encode_float\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if ("kof_json_decode_double_array".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_json_decode_double_array\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        return false;
    }
}

package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.Type;

/**
 * FASE 3 (REFACTOR-500): emissão x86_64 de calls de String/JSON nativos
 * (length/charAt/substring/contains/…/split + kof_string_to_* +
 * kof_json_*_double/float). Extraído verbatim de NativeBackend.emitCall —
 * 23 ramos mutuamente exclusivos, cada um termina em return; emit() devolve
 * true quando um ramo casou (o chamador retorna). Zero estado do backend.
 */
public final class NativeX86StringCalls {

    private NativeX86StringCalls() {}

    static boolean emit(NativeBackend nb, StringBuilder sb, KofCall kc) {
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "length".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_length\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "charAt".equals(kc.methodName())) {
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "substring".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            if (argCount == 1) {
                sb.append("    popq %rsi\n");
                // §111: sentinela "até o fim" era 0 — colidia com o end=0
                // LEGÍTIMO da forma 2-arg ("hello".substring(0,0) devolvia a
                // string toda). -1 é impossível como índice (bounds já rejeitam
                // <0) e o helper trata só -1 como toend.
                sb.append("    movq $-1, %rdx\n");
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "contains".equals(kc.methodName())) {
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "startsWith".equals(kc.methodName())) {
            int argCount = kc.parameterTypes().size();
            String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = argCount - 1; i >= 0; i--) {
                sb.append("    popq ").append(intRegs[i + 1]).append("\n");
            }
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %rdi\n");
            // §102: startsWith(prefix, from) — o 2º arg (offset em code units
            // UTF-16) estava sendo IGNORADO (mesmo helper de 1 arg). Com 2
            // args, %rdx já vem carregado pelo pop loop acima (regs[i+1]).
            sb.append("    call ")
              .append(argCount >= 2
                      ? "kof_string_starts_with2\n" : "kof_string_starts_with\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "endsWith".equals(kc.methodName())) {
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "concat".equals(kc.methodName())) {
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "indexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            // §102: o 2º arg (from) já está em %rdx quando há 2 parâmetros —
            // roteia p/ o helper _2 (JVM: clamps UTF-16 + cut de par). 1-arg
            // segue o helper byte-index (rdx é lixo, ele ignora).
            sb.append("    call ")
              .append(kc.parameterTypes().size() >= 2
                      ? "kof_string_index_of2\n" : "kof_string_index_of\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "lastIndexOf".equals(kc.methodName())) {
            String[] regs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
            for (int i = kc.parameterTypes().size() - 1; i >= 0; i--) {
                sb.append("    popq ").append(regs[i + 1]).append("\n");
            }
            sb.append("    popq %rdi\n");
            sb.append("    call ")
              .append(kc.parameterTypes().size() >= 2
                      ? "kof_string_last_index_of2\n" : "kof_string_last_index_of\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && "equals".equals(kc.methodName())
                && BuiltinTypes.isString(kc.ownerType())
                && kc.parameterTypes().size() == 1) {
            // bug 97 (continuação): `.equals` em String é conteúdo (mesma função
            // do `==`, null-safe) — mas o método NÃO era roteado → undefined
            // reference java_lang_String_equals no link (JVM/Script rodam).
            // Guard isString: record.equals é gerado campo-a-campo, NUNCA deve
            // cair aqui. type-system.md:258 documenta ".equals funciona (probe)
            // mas é anti-pattern — use ==".
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_equals\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "compareTo".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_compare_to\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "hashCode".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_hash_code\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "trim".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_trim\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "toUpperCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_upper\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "toLowerCase".equals(kc.methodName())) {
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_to_lower\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "replace".equals(kc.methodName())) {
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
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "equalsIgnoreCase".equals(kc.methodName())) {
            sb.append("    popq %rsi\n");
            sb.append("    popq %rdi\n");
            sb.append("    call kof_string_equals_ignore_case\n");
            sb.append("    pushq %rax\n");
            return true;
        }
        if (kc.kind() == KofCallKind.INSTANCE && BuiltinTypes.isString(kc.ownerType())
                && "split".equals(kc.methodName())) {
            // bug 95: as labels do ramo inline viviam num nome FIXO — um 2º
            // split no mesmo programa redefinía o símbolo → "symbol .Lkof_split_*
            // is already defined" no assembler (COMP001, qualquer programa com
            // 2+ splits, ex.: parsear 2 strings CSV). Sequência única p/ call
            // site (nb.inlineSeq, resetado por programa).
            int seq = nb.inlineSeq++;
            String empty = ".Lkof_split_empty_sep" + seq;
            String call = ".Lkof_split_call" + seq;
            sb.append("    popq %rsi\n");
            sb.append("    movl 16(%rsi), %ecx\n");
            sb.append("    testl %ecx, %ecx\n");
            sb.append("    jz " + empty + "\n");
            sb.append("    movzbl 24(%rsi), %esi\n");
            sb.append("    jmp " + call + "\n");
            sb.append(empty + ":\n");
            sb.append("    xorl %esi, %esi\n");
            sb.append(call + ":\n");
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

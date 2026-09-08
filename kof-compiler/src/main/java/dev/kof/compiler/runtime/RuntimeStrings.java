package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de kof.strings (STDLIB S2a) do runtime nativo x86_64.
 * Predicados de char plano (String→Bool, sem alocação): isAlpha/isNumeric.
 * Layout de string Kof: length em 16(%rdi), bytes em 24(%rdi) (ASCII).
 * Paridade (travada na matriz): "" / null => false.
 */
public final class RuntimeStrings {

    private RuntimeStrings() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_strings_isAlpha(rdi=str) -> 1/0  (só [A-Za-z], não-vazio)
            .globl kof_strings_isAlpha
            .type kof_strings_isAlpha, @function
            kof_strings_isAlpha:
                testq %rdi, %rdi
                jz .Lv_str_alpha_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_alpha_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_str_alpha_loop:
                cmpq %rcx, %rax
                jge .Lv_str_alpha_true
                movzbl (%r8,%rax), %edx
                cmpl $65, %edx            # 'A'
                jl .Lv_str_alpha_false
                cmpl $90, %edx            # 'Z'
                jle .Lv_str_alpha_next
                cmpl $97, %edx            # 'a'
                jl .Lv_str_alpha_false
                cmpl $122, %edx           # 'z'
                jg .Lv_str_alpha_false
            .Lv_str_alpha_next:
                incq %rax
                jmp .Lv_str_alpha_loop
            .Lv_str_alpha_true:
                movl $1, %eax
                ret
            .Lv_str_alpha_false:
                xorl %eax, %eax
                ret

            # kof_strings_isNumeric(rdi=str) -> 1/0  (só [0-9], não-vazio)
            .globl kof_strings_isNumeric
            .type kof_strings_isNumeric, @function
            kof_strings_isNumeric:
                testq %rdi, %rdi
                jz .Lv_str_num_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_num_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_str_num_loop:
                cmpq %rcx, %rax
                jge .Lv_str_num_true
                movzbl (%r8,%rax), %edx
                cmpl $48, %edx            # '0'
                jl .Lv_str_num_false
                cmpl $57, %edx            # '9'
                jg .Lv_str_num_false
                incq %rax
                jmp .Lv_str_num_loop
            .Lv_str_num_true:
                movl $1, %eax
                ret
            .Lv_str_num_false:
                xorl %eax, %eax
                ret
        """);
    }
}

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

            # kof_strings_isAlphaNumeric(rdi=str) -> 1/0  (só [A-Za-z0-9], não-vazio)
            .globl kof_strings_isAlphaNumeric
            .type kof_strings_isAlphaNumeric, @function
            kof_strings_isAlphaNumeric:
                testq %rdi, %rdi
                jz .Lv_str_an_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_an_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_str_an_loop:
                cmpq %rcx, %rax
                jge .Lv_str_an_true
                movzbl (%r8,%rax), %edx
                cmpl $48, %edx            # '0'
                jb .Lv_str_an_alpha
                cmpl $57, %edx            # '9'
                jbe .Lv_str_an_next       # dígito ok
            .Lv_str_an_alpha:
                cmpl $65, %edx            # 'A'
                jb .Lv_str_an_false
                cmpl $90, %edx            # 'Z'
                jbe .Lv_str_an_next
                cmpl $97, %edx            # 'a'
                jb .Lv_str_an_false
                cmpl $122, %edx           # 'z'
                ja .Lv_str_an_false
            .Lv_str_an_next:
                incq %rax
                jmp .Lv_str_an_loop
            .Lv_str_an_true:
                movl $1, %eax
                ret
            .Lv_str_an_false:
                xorl %eax, %eax
                ret

            # kof_strings_isAscii(rdi=str) -> 1/0 (não-vazio, todos os bytes < 128)
            .globl kof_strings_isAscii
            .type kof_strings_isAscii, @function
            kof_strings_isAscii:
                testq %rdi, %rdi
                jz .Lv_str_asc_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_asc_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_str_asc_loop:
                cmpq %rcx, %rax
                jge .Lv_str_asc_true
                movzbl (%r8,%rax), %edx
                cmpl $128, %edx
                jge .Lv_str_asc_false
                incq %rax
                jmp .Lv_str_asc_loop
            .Lv_str_asc_true:
                movl $1, %eax
                ret
            .Lv_str_asc_false:
                xorl %eax, %eax
                ret

            # kof_strings_isUpperCase(rdi=str) -> 1/0
            # não-vazio, sem [a-z], com ≥1 [A-Z]; demais chars ignorados.
            .globl kof_strings_isUpperCase
            .type kof_strings_isUpperCase, @function
            kof_strings_isUpperCase:
                testq %rdi, %rdi
                jz .Lv_str_uu_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_uu_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax          # i
                xorq %r9, %r9            # hasLetter
            .Lv_str_uu_loop:
                cmpq %rcx, %rax
                jge .Lv_str_uu_check
                movzbl (%r8,%rax), %edx
                cmpl $97, %edx           # 'a'
                jge .Lv_str_uu_lower
                cmpl $65, %edx           # 'A'
                jl .Lv_str_uu_next
                cmpl $90, %edx           # 'Z'
                jg .Lv_str_uu_next
                movl $1, %r9d            # [A-Z]
                jmp .Lv_str_uu_next
            .Lv_str_uu_lower:
                cmpl $122, %edx          # 'z'
                jg .Lv_str_uu_next       # >z (não-ASCII minúsculo) ignora
                jmp .Lv_str_uu_false     # [a-z] => false
            .Lv_str_uu_next:
                incq %rax
                jmp .Lv_str_uu_loop
            .Lv_str_uu_check:
                testq %r9, %r9
                jz .Lv_str_uu_false
                movl $1, %eax
                ret
            .Lv_str_uu_false:
                xorl %eax, %eax
                ret

            # kof_strings_isLowerCase(rdi=str) -> 1/0
            # não-vazio, sem [A-Z], com ≥1 [a-z]; demais chars ignorados.
            .globl kof_strings_isLowerCase
            .type kof_strings_isLowerCase, @function
            kof_strings_isLowerCase:
                testq %rdi, %rdi
                jz .Lv_str_ll_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jle .Lv_str_ll_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
                xorq %r9, %r9
            .Lv_str_ll_loop:
                cmpq %rcx, %rax
                jge .Lv_str_ll_check
                movzbl (%r8,%rax), %edx
                cmpl $65, %edx           # 'A'
                jl .Lv_str_ll_nonupper
                cmpl $90, %edx           # 'Z'
                jg .Lv_str_ll_nonupper
                jmp .Lv_str_ll_false     # [A-Z] => false
            .Lv_str_ll_nonupper:
                cmpl $97, %edx           # 'a'
                jl .Lv_str_ll_next
                cmpl $122, %edx          # 'z'
                jg .Lv_str_ll_next
                movl $1, %r9d            # [a-z]
                jmp .Lv_str_ll_next
            .Lv_str_ll_next:
                incq %rax
                jmp .Lv_str_ll_loop
            .Lv_str_ll_check:
                testq %r9, %r9
                jz .Lv_str_ll_false
                movl $1, %eax
                ret
            .Lv_str_ll_false:
                xorl %eax, %eax
                ret

            # kof_strings_count(rdi=s, rsi=sub) -> Int (não-sobrepostas)
            # "" / null em qualquer lado => 0. len_sub > len_s => 0.
            .globl kof_strings_count
            .type kof_strings_count, @function
            kof_strings_count:
                pushq %rbx
                testq %rdi, %rdi
                jz .Lv_str_cnt_false
                testq %rsi, %rsi
                jz .Lv_str_cnt_false
                movl 16(%rdi), %ecx      # len_s
                testl %ecx, %ecx
                jle .Lv_str_cnt_false
                movl 16(%rsi), %ebx      # len_sub
                testl %ebx, %ebx
                jle .Lv_str_cnt_false
                cmpq %rcx, %rbx
                ja .Lv_str_cnt_false     # sub maior que s
                leaq 24(%rdi), %r8       # s ptr
                leaq 24(%rsi), %r9       # sub ptr
                xorq %r10, %r10          # count
                xorq %rax, %rax          # i
            .Lv_str_cnt_outer:
                movq %rcx, %r11
                subq %rbx, %r11          # último início = len_s - len_sub
                cmpq %r11, %rax
                jg .Lv_str_cnt_done
                xorq %rdx, %rdx          # j
            .Lv_str_cnt_inner:
                cmpq %rbx, %rdx
                jge .Lv_str_cnt_matched
                leaq (%rax,%rdx), %rsi   # o = i + j
                movzbl (%r8,%rsi), %esi  # s[o]
                movzbl (%r9,%rdx), %r11d # sub[j]
                cmpl %r11d, %esi
                jne .Lv_str_cnt_nomatch
                incq %rdx
                jmp .Lv_str_cnt_inner
            .Lv_str_cnt_matched:
                incq %r10
                addq %rbx, %rax          # i += len_sub (não-sobreposta)
                jmp .Lv_str_cnt_outer
            .Lv_str_cnt_nomatch:
                incq %rax                # i += 1
                jmp .Lv_str_cnt_outer
            .Lv_str_cnt_done:
                movq %r10, %rax
                popq %rbx
                ret
            .Lv_str_cnt_false:
                xorl %eax, %eax
                popq %rbx
                ret
        """);
        // ordem de emissão preservada do arquivo único original (.s byte-idêntico)
        RuntimeStringsConv.emit(sb);
        RuntimeStringsWords.emit(sb);
        RuntimeStringsEsc.emit(sb);
        RuntimeStringWs.emit(sb);
        RuntimeStringsEscJson.emit(sb);
    }
}

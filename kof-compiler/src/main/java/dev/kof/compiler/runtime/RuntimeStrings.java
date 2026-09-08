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

            # kof_strings_capitalize(rdi=str) -> String (1º byte a-z => -32)
            # null/"" => retorna o ponteiro original (paridade JVM).
            .globl kof_strings_capitalize
            .type kof_strings_capitalize, @function
            kof_strings_capitalize:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_str_cap_ret_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_str_cap_ret_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13            # novo obj
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movzbl 24(%rbx), %eax
                cmpl $97, %eax             # 'a'
                jl .Lv_str_cap_put
                cmpl $122, %eax            # 'z'
                jg .Lv_str_cap_put
                subl $32, %eax             # -> maiúscula
            .Lv_str_cap_put:
                movb %al, 24(%r13)
                # resto: memcpy(novo+25, orig+25, len-1)
                leaq 25(%r13), %rdi
                leaq 25(%rbx), %rsi
                movl %r12d, %edx
                decl %edx
                jz .Lv_str_cap_term
                call kof_memcpy
            .Lv_str_cap_term:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                jmp .Lv_str_cap_done
            .Lv_str_cap_ret_orig:
                movq %rbx, %rax
            .Lv_str_cap_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_reverse(rdi=str) -> String (byte-reverso, ASCII)
            # null/"" => retorna o ponteiro original (paridade JVM).
            .globl kof_strings_reverse
            .type kof_strings_reverse, @function
            kof_strings_reverse:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_str_rev_ret_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_str_rev_ret_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                xorq %r14, %r14            # i
            .Lv_str_rev_loop:
                cmpq %r12, %r14
                jge .Lv_str_rev_term
                movq %r12, %r15
                subq %r14, %r15
                decq %r15
                movzbl 24(%rbx,%r15), %eax # s[len-1-i]
                movb %al, 24(%r13,%r14)
                incq %r14
                jmp .Lv_str_rev_loop
            .Lv_str_rev_term:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                jmp .Lv_str_rev_done
            .Lv_str_rev_ret_orig:
                movq %rbx, %rax
            .Lv_str_rev_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_repeat(rdi=str, rsi=n) -> String
            # null/""/n<=0 => "". total = len*n. kof_memcpy(rdi,rsi,rdx) e
            # kof_alloc destroem r10/r11/rcx — manter estado só em callee-saved
            # (rbx,r12-r15) + cursor i num stack slot (manter rsp 16-alinhado).
            .globl kof_strings_repeat
            .type kof_strings_repeat, @function
            kof_strings_repeat:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16, %rsp           # [rsp] = cursor i
                movq %rdi, %rbx          # str
                movl %esi, %r12d         # n
                testq %rbx, %rbx
                jz .Lv_str_rep_emp
                testl %r12d, %r12d
                jle .Lv_str_rep_emp
                movl 16(%rbx), %r13d     # len (orig)
                testl %r13d, %r13d
                jle .Lv_str_rep_emp
                movl %r13d, %r14d
                imull %r12d, %r14d       # total = len * n
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15          # novo
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                movq $0, (%rsp)          # i = 0
            .Lv_str_rep_outer:
                cmpq %r12, (%rsp)
                jge .Lv_str_rep_term
                movq (%rsp), %rax
                imulq %r13, %rax         # off = i * len
                leaq 24(%r15), %rdi
                addq %rax, %rdi          # dst = novo.bytes + off
                leaq 24(%rbx), %rsi      # src = str.bytes
                movl %r13d, %edx         # n = len (orig intacto)
                call kof_memcpy
                incq (%rsp)
                jmp .Lv_str_rep_outer
            .Lv_str_rep_term:
                movl %r14d, %eax
                movb $0, 24(%r15,%rax)   # NUL no fim (total)
                movq %r15, %rax
                jmp .Lv_str_rep_done
            .Lv_str_rep_emp:
                movl $40, %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl $0, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                movq %r15, %rax
            .Lv_str_rep_done:
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_truncate(rdi=str, rsi=n) -> String
            # null=>null; n<=0=>""; len<=n=>original; senao substring(0,n).
            .globl kof_strings_truncate
            .type kof_strings_truncate, @function
            kof_strings_truncate:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # str
                movl %esi, %r12d         # n
                testq %rbx, %rbx
                jz .Lv_str_tru_null
                movl 16(%rbx), %r13d     # len
                testl %r12d, %r12d
                jle .Lv_str_tru_empty
                cmpl %r13d, %r12d
                jge .Lv_str_tru_orig
                # alocar n + 25 (n < len)
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                leaq 24(%r15), %rdi
                leaq 24(%rbx), %rsi
                movl %r12d, %edx
                call kof_memcpy
                movb $0, 24(%r15,%r12)
                movq %r15, %rax
                jmp .Lv_str_tru_done
            .Lv_str_tru_orig:
                movq %rbx, %rax
                jmp .Lv_str_tru_done
            .Lv_str_tru_empty:
                leal 25, %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl $0, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                movq %r15, %rax
                jmp .Lv_str_tru_done
            .Lv_str_tru_null:
                xorl %eax, %eax
            .Lv_str_tru_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

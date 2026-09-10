package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Conversores kof.strings (STDLIB S2b/S2b.2/S2b.3/S2b.4) — os que ALOCAM
 * String nova no x86_64 (capitalize/reverse/repeat/truncate/pad/joinWords).
 * Extraído de RuntimeStrings (gate ≤500); ordem de emissão preservada
 * (RuntimeStrings.emit → RuntimeStringsConv.emit) — .s byte-idêntico.
 */
public final class RuntimeStringsConv {

    private RuntimeStringsConv() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
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

            # kof_strings_uncapitalize(rdi=str) -> String (1º byte A-Z->a-z;
            # null/""/fora-de-A-Z => original — espelho do capitalize, S2b ASCII)
            .globl kof_strings_uncapitalize
            .type kof_strings_uncapitalize, @function
            kof_strings_uncapitalize:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_str_unc_ret_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_str_unc_ret_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13            # novo obj
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movzbl 24(%rbx), %eax
                cmpl $65, %eax             # 'A'
                jl .Lv_str_unc_put
                cmpl $90, %eax            # 'Z'
                jg .Lv_str_unc_put
                addl $32, %eax             # -> minúscula
            .Lv_str_unc_put:
                movb %al, 24(%r13)
                # resto: memcpy(novo+25, orig+25, len-1)
                leaq 25(%r13), %rdi
                leaq 25(%rbx), %rsi
                movl %r12d, %edx
                decl %edx
                jz .Lv_str_unc_term
                call kof_memcpy
            .Lv_str_unc_term:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                jmp .Lv_str_unc_done
            .Lv_str_unc_ret_orig:
                movq %rbx, %rax
            .Lv_str_unc_done:
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

            # kof_strings_padLeft(rdi=v, esi=n, rdx=pad) -> String
            # null=>0; pad null/"" ou len(v)>=n => v; senao (n-len)×pad[0] + v.
            .globl kof_strings_padLeft
            .type kof_strings_padLeft, @function
            kof_strings_padLeft:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16, %rsp
                movq %rdi, %rbx          # v
                movl %esi, %r12d         # n
                testq %rbx, %rbx
                jz .Lv_str_pl_null
                testq %rdx, %rdx
                jz .Lv_str_pl_orig
                movl 16(%rdx), %eax
                testl %eax, %eax
                jle .Lv_str_pl_orig
                movzbl 24(%rdx), %r14d   # pad[0]
                movl 16(%rbx), %r13d     # vlen
                cmpl %r13d, %r12d
                jle .Lv_str_pl_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, (%rsp)        # novo
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl %r12d, 16(%rax)
                movl $0, 20(%rax)
                movq (%rsp), %r15
                # preenche pad: i em [0, n-vlen)
                xorq %rcx, %rcx
            .Lv_str_pl_fill:
                movl %r12d, %eax
                subl %r13d, %eax
                cmpl %eax, %ecx
                jge .Lv_str_pl_copy
                movb %r14b, 24(%r15,%rcx)
                incl %ecx
                jmp .Lv_str_pl_fill
            .Lv_str_pl_copy:
                leaq 24(%r15), %rdi
                addq %rcx, %rdi
                leaq 24(%rbx), %rsi
                movl %r13d, %edx
                testl %edx, %edx
                jle .Lv_str_pl_term
                call kof_memcpy
            .Lv_str_pl_term:
                movq (%rsp), %r15
                movl %r12d, %eax
                movb $0, 24(%r15,%rax)
                movq %r15, %rax
                jmp .Lv_str_pl_done
            .Lv_str_pl_orig:
                movq %rbx, %rax
                jmp .Lv_str_pl_done
            .Lv_str_pl_null:
                xorl %eax, %eax
            .Lv_str_pl_done:
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_padRight(rdi=v, esi=n, rdx=pad) -> String
            # v + (n-len)×pad[0]; mesmas regras de borda.
            .globl kof_strings_padRight
            .type kof_strings_padRight, @function
            kof_strings_padRight:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16, %rsp
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lv_str_pr_null
                testq %rdx, %rdx
                jz .Lv_str_pr_orig
                movl 16(%rdx), %eax
                testl %eax, %eax
                jle .Lv_str_pr_orig
                movzbl 24(%rdx), %r14d
                movl 16(%rbx), %r13d
                cmpl %r13d, %r12d
                jle .Lv_str_pr_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                # copia v primeiro: memcpy(novo.bytes, v.bytes, vlen)
                leaq 24(%r15), %rdi
                leaq 24(%rbx), %rsi
                movl %r13d, %edx
                call kof_memcpy
                # preenche cauda: i em [vlen, n)
                movl %r13d, %ecx
            .Lv_str_pr_fill:
                cmpl %r12d, %ecx
                jge .Lv_str_pr_term
                movb %r14b, 24(%r15,%rcx)
                incl %ecx
                jmp .Lv_str_pr_fill
            .Lv_str_pr_term:
                movl %r12d, %eax
                movb $0, 24(%r15,%rax)
                movq %r15, %rax
                jmp .Lv_str_pr_done
            .Lv_str_pr_orig:
                movq %rbx, %rax
                jmp .Lv_str_pr_done
            .Lv_str_pr_null:
                xorl %eax, %eax
            .Lv_str_pr_done:
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

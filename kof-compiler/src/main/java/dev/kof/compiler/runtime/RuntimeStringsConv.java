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

            # kof_strings_reverse(rdi=str) -> String (inverso por CODE POINT)
            # Inverte a sequência de code points UTF-8, mantendo cada sequência
            # multi-byte intacta — mesmo resultado do JVM
            # (StringBuilder.reverse, que preserva pares surrogate) e do JS
            # ([...v].reverse()). O store é UTF-8 (length@16 = bytes), então o
            # cursor de escrita anda de trás para frente copiando cada code
            # point inteiro (D-FULL-PARITY-050 linha 11).
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
                movq %r12, %r14            # i = byteLen (cursor de leitura)
                xorq %r15, %r15            # o = 0 (cursor de escrita, do início)
            .Lv_str_rev_loop:
                testq %r14, %r14
                jle .Lv_str_rev_term
                movq %r14, %rcx            # j = i-1
                decq %rcx
            .Lv_str_rev_scan:
                testq %rcx, %rcx
                jle .Lv_str_rev_start
                movzbl 24(%rbx,%rcx), %eax
                andl $0xC0, %eax
                cmpl $0x80, %eax           # byte de continuação 10xxxxxx?
                jne .Lv_str_rev_start
                decq %rcx
                jmp .Lv_str_rev_scan
            .Lv_str_rev_start:
                movq %r14, %rdx            # len = i - j
                subq %rcx, %rdx
                xorq %r8, %r8              # k
            .Lv_str_rev_copy:
                cmpq %rdx, %r8
                jge .Lv_str_rev_copied
                movzbl 24(%rbx,%rcx), %eax # in[j+k]
                movb %al, 24(%r13,%r15)    # out[o+k]
                incq %rcx
                incq %r15
                incq %r8
                jmp .Lv_str_rev_copy
            .Lv_str_rev_copied:
                subq %rdx, %rcx            # rcx = j (início do code point)
                movq %rcx, %r14
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
            # §747 (D-FULL-PARITY-050 NAT-STR01): o pad e o PRIMEIRO CARACTERE
            # do pad repetido ate n CARACTERES (contrato do JVM/JS/Script,
            # medido: padLeft("7",5,"ab") = "aaaa7"). O helper antigo escrevia
            # UM BYTE por slot e contava o comprimento da vitima em BYTES ->
            # pad nao-ASCII corrompia a String (UTF-8 invalido) e uma vitima
            # multi-byte recebia pads a menos.
            kof_strings_padLeft:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lv_str_pl_null
                testq %rdx, %rdx
                jz .Lv_str_pl_orig
                movl 16(%rdx), %eax
                testl %eax, %eax
                jle .Lv_str_pl_orig
                movq %rdx, %r14
                movl $1, %r11d
                movzbl 24(%r14), %edx
                movl %edx, %eax
                andl $0xE0, %eax
                cmpl $0xC0, %eax
                jne .Lv_pl_l3
                movl $2, %r11d
                jmp .Lv_pl_plen
            .Lv_pl_l3:
                movl %edx, %eax
                andl $0xF0, %eax
                cmpl $0xE0, %eax
                jne .Lv_pl_l4
                movl $3, %r11d
                jmp .Lv_pl_plen
            .Lv_pl_l4:
                movl %edx, %eax
                andl $0xF8, %eax
                cmpl $0xF0, %eax
                jne .Lv_pl_plen
                movl $4, %r11d
            .Lv_pl_plen:
                movl %r11d, 8(%rsp)
                movl 16(%rbx), %r13d
                xorl %r10d, %r10d
                xorl %ecx, %ecx
            .Lv_pl_cnt:
                cmpl %r13d, %ecx
                jge .Lv_pl_cntd
                movzbl 24(%rbx,%rcx), %eax
                andl $0xC0, %eax
                cmpl $0x80, %eax
                je .Lv_pl_cntn
                incl %r10d
            .Lv_pl_cntn:
                incq %rcx
                jmp .Lv_pl_cnt
            .Lv_pl_cntd:
                movl %r10d, 16(%rsp)
                cmpl %r10d, %r12d
                jle .Lv_str_pl_orig
                movl %r12d, %eax
                subl %r10d, %eax
                imull 8(%rsp), %eax
                addl %r13d, %eax
                movl %eax, 24(%rsp)
                leal 25(%rax), %edi
                call kof_alloc
                movq %rax, (%rsp)
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl 24(%rsp), %ecx
                movl %ecx, 16(%rax)
                movl $0, 20(%rax)
                movq (%rsp), %r15
                xorq %rcx, %rcx
            .Lv_pl_pad:
                movl 24(%rsp), %r11d
                subl %r13d, %r11d
            .Lv_pl_pchk:
                cmpl %r11d, %ecx
                jge .Lv_pl_copy
                xorl %r10d, %r10d
            .Lv_pl_pbyte:
                cmpl 8(%rsp), %r10d
                jge .Lv_pl_pchk
                movzbl 24(%r14,%r10), %eax
                movb %al, 24(%r15,%rcx)
                incq %rcx
                incl %r10d
                jmp .Lv_pl_pbyte
            .Lv_pl_copy:
                leaq 24(%r15), %rdi
                addq %rcx, %rdi
                leaq 24(%rbx), %rsi
                movl %r13d, %edx
                testl %edx, %edx
                jle .Lv_pl_term
                call kof_memcpy
            .Lv_pl_term:
                movq (%rsp), %r15
                movl 24(%rsp), %eax
                movb $0, 24(%r15,%rax)
                movq %r15, %rax
                jmp .Lv_str_pl_done
            .Lv_str_pl_orig:
                movq %rbx, %rax
                jmp .Lv_str_pl_done
            .Lv_str_pl_null:
                xorl %eax, %eax
            .Lv_str_pl_done:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_padRight(rdi=v, esi=n, rdx=pad) -> String
            # §747: vitima PRIMEIRO, depois o pad (primeiro caractere repetido).
            .globl kof_strings_padRight
            .type kof_strings_padRight, @function
            kof_strings_padRight:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lv_str_pr_null
                testq %rdx, %rdx
                jz .Lv_str_pr_orig
                movl 16(%rdx), %eax
                testl %eax, %eax
                jle .Lv_str_pr_orig
                movq %rdx, %r14
                movl $1, %r11d
                movzbl 24(%r14), %edx
                movl %edx, %eax
                andl $0xE0, %eax
                cmpl $0xC0, %eax
                jne .Lv_pr_l3
                movl $2, %r11d
                jmp .Lv_pr_plen
            .Lv_pr_l3:
                movl %edx, %eax
                andl $0xF0, %eax
                cmpl $0xE0, %eax
                jne .Lv_pr_l4
                movl $3, %r11d
                jmp .Lv_pr_plen
            .Lv_pr_l4:
                movl %edx, %eax
                andl $0xF8, %eax
                cmpl $0xF0, %eax
                jne .Lv_pr_plen
                movl $4, %r11d
            .Lv_pr_plen:
                movl %r11d, 8(%rsp)
                movl 16(%rbx), %r13d
                xorl %r10d, %r10d
                xorl %ecx, %ecx
            .Lv_pr_cnt:
                cmpl %r13d, %ecx
                jge .Lv_pr_cntd
                movzbl 24(%rbx,%rcx), %eax
                andl $0xC0, %eax
                cmpl $0x80, %eax
                je .Lv_pr_cntn
                incl %r10d
            .Lv_pr_cntn:
                incq %rcx
                jmp .Lv_pr_cnt
            .Lv_pr_cntd:
                movl %r10d, 16(%rsp)
                cmpl %r10d, %r12d
                jle .Lv_str_pr_orig
                movl %r12d, %eax
                subl %r10d, %eax
                imull 8(%rsp), %eax
                addl %r13d, %eax
                movl %eax, 24(%rsp)
                leal 25(%rax), %edi
                call kof_alloc
                movq %rax, (%rsp)
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl 24(%rsp), %ecx
                movl %ecx, 16(%rax)
                movl $0, 20(%rax)
                movq (%rsp), %r15
                leaq 24(%r15), %rdi
                leaq 24(%rbx), %rsi
                movl %r13d, %edx
                testl %edx, %edx
                jle .Lv_pr_pad
                call kof_memcpy
            .Lv_pr_pad:
                movq (%rsp), %r15
                movq %r13, %rcx
                movl 24(%rsp), %r11d
            .Lv_pr_pchk:
                cmpl %r11d, %ecx
                jge .Lv_pr_term
                xorl %r10d, %r10d
            .Lv_pr_pbyte:
                cmpl 8(%rsp), %r10d
                jge .Lv_pr_pchk
                movzbl 24(%r14,%r10), %eax
                movb %al, 24(%r15,%rcx)
                incq %rcx
                incl %r10d
                jmp .Lv_pr_pbyte
            .Lv_pr_term:
                movq (%rsp), %r15
                movl 24(%rsp), %eax
                movb $0, 24(%r15,%rax)
                movq %r15, %rax
                jmp .Lv_str_pr_done
            .Lv_str_pr_orig:
                movq %rbx, %rax
                jmp .Lv_str_pr_done
            .Lv_str_pr_null:
                xorl %eax, %eax
            .Lv_str_pr_done:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

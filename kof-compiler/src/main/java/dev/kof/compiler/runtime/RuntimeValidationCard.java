package dev.kof.compiler.runtime;

/**
 * Cartao de credito no x86_64 (STDLIB S12c): kof_validation_creditCardBrand
 * e kof_validation_last4. Reusa as MESMAS regras do kof_validation_isCreditCard
 * (RuntimeValidationNet): extrai digitos, >19 invalido; brand exige Luhn
 * valido e 13..19 digitos; prefixos Visa 4 / Mastercard 51..55 / Amex 34,37 /
 * Discover 6011,65; invalido ou marca desconhecida => "". last4: ultimos 4
 * digitos (>=4, sem exigir Luhn; janela rolante, >19 => "").
 * LAYOUT (idem RuntimeTimeMonthIso/StartEndOf): tag=1@0, 0@4, 0@8, len@16,
 * 0@20, bytes@24; kof_alloc preserva callee-saved.
 */
public final class RuntimeValidationCard {

    private RuntimeValidationCard() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_validation_creditCardBrand(rdi=str) -> String (S12c)
            .globl kof_validation_creditCardBrand
            .type kof_validation_creditCardBrand, @function
            kof_validation_creditCardBrand:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $24, %rsp           # buf[19] em (%rsp); 16-aligned
                movq %rdi, %rbx
                movq %rsp, %r12          # buf
                xorl %r13d, %r13d        # n
                testq %rbx, %rbx
                jz .Lvcd_empty
                movl 16(%rbx), %ecx      # len
                xorl %edx, %edx          # i
            .Lvcd_collect:
                cmpl %ecx, %edx
                jge .Lvcd_nchk
                movzbl 24(%rbx,%rdx), %eax
                incl %edx
                subl $48, %eax
                cmpl $9, %eax
                ja .Lvcd_collect
                cmpl $19, %r13d
                jae .Lvcd_empty          # >19 digitos
                movb %al, 0(%r12,%r13)
                incl %r13d
                jmp .Lvcd_collect
            .Lvcd_nchk:
                cmpl $13, %r13d
                jb .Lvcd_empty           # marca exige 13..19
                xorl %r8d, %r8d          # j
                xorl %r9d, %r9d          # sum
            .Lvcd_sum:
                cmpl %r13d, %r8d
                jge .Lvcd_mod
                movl %r13d, %eax
                subl %r8d, %eax
                decl %eax
                andl $1, %eax
                movzbl 0(%r12,%r8), %r10d   # v
                testl %eax, %eax
                jz .Lvcd_add
                addl %r10d, %r10d        # v*2
                cmpl $9, %r10d
                jbe .Lvcd_add
                subl $9, %r10d
            .Lvcd_add:
                addl %r10d, %r9d
                incl %r8d
                jmp .Lvcd_sum
            .Lvcd_mod:
                movl %r9d, %eax
                xorl %edx, %edx
                movl $10, %ecx
                divl %ecx
                testl %edx, %edx
                jnz .Lvcd_empty          # Luhn invalido
                # prefixos: d0=d[0], d1=d[1], d2=d[2], d3=d[3] (n>=13 => >=4)
                movzbl 0(%r12), %r11d
                movzbl 1(%r12), %r8d
                movzbl 2(%r12), %r9d
                movzbl 3(%r12), %r10d
                cmpl $4, %r11d
                je .Lvcd_visa
                cmpl $5, %r11d
                jne .Lvcd_c3
                cmpl $1, %r8d
                jb .Lvcd_empty
                cmpl $5, %r8d
                ja .Lvcd_empty
                movl $10, %eax           # "Mastercard"
                jmp .Lvcd_alloc
            .Lvcd_c3:
                cmpl $3, %r11d
                jne .Lvcd_c6
                cmpl $4, %r8d
                je .Lvcd_amex
                cmpl $7, %r8d
                jne .Lvcd_empty
            .Lvcd_amex:
                movl $4, %eax
                jmp .Lvcd_alloc
            .Lvcd_c6:
                cmpl $6, %r11d
                jne .Lvcd_empty
                cmpl $5, %r8d
                je .Lvcd_disc
                cmpl $0, %r8d
                jne .Lvcd_empty
                cmpl $1, %r9d
                jne .Lvcd_empty
                cmpl $1, %r10d
                jne .Lvcd_empty
            .Lvcd_disc:
                movl $8, %eax
                jmp .Lvcd_alloc
            .Lvcd_visa:
                movl $4, %eax
                jmp .Lvcd_alloc
            .Lvcd_empty:
                xorl %eax, %eax
                jmp .Lvcd_alloc
            .Lvcd_alloc:                 # eax = len da marca (0=vazio)
                movl %eax, %r14d         # len (r14 sobrevive ao call)
                leal 25(%r14), %edi
                call kof_alloc
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl %r14d, 16(%rax)
                movl $0, 20(%rax)
                movb $0, 24(%rax)
                cmpl $4, %r14d
                je .Lvcd_put_word
                cmpl $8, %r14d
                je .Lvcd_put_disc
                cmpl $10, %r14d
                je .Lvcd_put_mc
                jmp .Lvcd_done
            .Lvcd_put_word:              # "Visa" (4) ou "Amex" (4) — d[0]==4?
                cmpb $4, 0(%r12)
                jne .Lvcd_put_amex
                movl $0x61736956, 24(%rax)   # V i s a
                movb $0, 28(%rax)
                jmp .Lvcd_done
            .Lvcd_put_amex:
                movl $0x78656D41, 24(%rax)   # A m e x
                movb $0, 28(%rax)
                jmp .Lvcd_done
            .Lvcd_put_disc:                    # pares LE: D i|s c|o v|e r
                movabsq $0x6944, %r8
                movabsq $0x6373, %r9
                shlq $16, %r9
                orq %r9, %r8
                movabsq $0x766F, %r9
                shlq $32, %r9
                orq %r9, %r8
                movabsq $0x7265, %r9
                shlq $48, %r9
                orq %r9, %r8
                movq %r8, 24(%rax)             # D i s c o v e r
                movb $0, 32(%rax)
                jmp .Lvcd_done
            .Lvcd_put_mc:                      # M a|s t|e r|c a + r d
                movabsq $0x614D, %r8
                movabsq $0x7473, %r9
                shlq $16, %r9
                orq %r9, %r8
                movabsq $0x7265, %r9
                shlq $32, %r9
                orq %r9, %r8
                movabsq $0x6163, %r9
                shlq $48, %r9
                orq %r9, %r8
                movq %r8, 24(%rax)             # M a s t e r c a
                movw $0x6472, 32(%rax)          # r d
                movb $0, 34(%rax)
                jmp .Lvcd_done
            .Lvcd_done:
                addq $24, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_last4(rdi=str) -> String (S12c)
            # janela rolante de 19: ao encher, desloca 1 e marca estouro;
            # >19 digitos => ""; <4 => ""; senao os 4 ultimos.
            .globl kof_validation_last4
            .type kof_validation_last4, @function
            kof_validation_last4:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $24, %rsp
                movq %rdi, %rbx
                movq %rsp, %r12          # buf
                xorl %r13d, %r13d        # n
                xorl %r14d, %r14d        # overflow
                testq %rbx, %rbx
                jz .Lv4_empty
                movl 16(%rbx), %ecx
                xorl %edx, %edx
            .Lv4_collect:
                cmpl %ecx, %edx
                jge .Lv4_nchk
                movzbl 24(%rbx,%rdx), %eax
                incl %edx
                subl $48, %eax
                cmpl $9, %eax
                ja .Lv4_collect
                cmpl $19, %r13d
                jb .Lv4_store
                movl %eax, %r11d         # v (sobrevive ao loop de shift)
                movl $1, %r14d
                jmp .Lv4_shift           # descarta o mais antigo
            .Lv4_store:
                movb %al, 0(%r12,%r13)
                incl %r13d
                jmp .Lv4_collect
            .Lv4_shift:                  # memmove(buf, buf+1, 18) + buf[18]=v
                xorl %r8d, %r8d
            .Lv4_move:
                cmpl $18, %r8d
                jge .Lv4_mend
                movzbl 1(%r12,%r8), %eax
                movb %al, 0(%r12,%r8)
                incl %r8d
                jmp .Lv4_move
            .Lv4_mend:
                movb %r11b, 18(%r12)
                jmp .Lv4_collect
            .Lv4_nchk:
                testl %r14d, %r14d
                jnz .Lv4_empty
                cmpl $4, %r13d
                jb .Lv4_empty
                movl %r13d, %r8d
                subl $4, %r8d            # j0 = n-4
                movl $4, %edi
                addl $25, %edi
                call kof_alloc
                movq %rax, %r14            # obj (rax fica livre p/ digitos)
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl $4, 16(%r14)
                movl $0, 20(%r14)
                leaq (%r12,%r8), %r9
                movzbl 0(%r9), %eax
                addl $48, %eax
                movb %al, 24(%r14)
                movzbl 1(%r9), %eax
                addl $48, %eax
                movb %al, 25(%r14)
                movzbl 2(%r9), %eax
                addl $48, %eax
                movb %al, 26(%r14)
                movzbl 3(%r9), %eax
                addl $48, %eax
                movb %al, 27(%r14)
                movb $0, 28(%r14)
                movq %r14, %rax
                jmp .Lv4_done
            .Lv4_empty:
                xorl %edi, %edi
                movl $25, %edi
                call kof_alloc
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 16(%rax)
                movl $0, 20(%rax)
                movb $0, 24(%rax)
            .Lv4_done:
                addq $24, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}

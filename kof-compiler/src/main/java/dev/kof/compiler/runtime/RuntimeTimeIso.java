package dev.kof.compiler.runtime;

/**
 * Emissão do ASM das operações kof.time addDays/diffDays em data ISO (STDLIB
 * S7c) do runtime nativo x86. Domínio isolado de RuntimeTime (regra <=500
 * linhas/classe) — reusa .Lkd_valid/.Lkd_epoch emitidos por RuntimeTime no
 * MESMO arquivo .s (mesmo StringBuilder em NativeRuntime).
 */
public final class RuntimeTimeIso {

    private RuntimeTimeIso() {}

    public static void emitTimeIsoFunctions(StringBuilder sb) {
        sb.append("""
            # ── kof.time (STDLIB S7c) — addDays/diffDays em data ISO (x86) ──
            # Reusa a máquina civil do wedge (.Lkd_epoch/.Lkd_valid) + inversa
            # canônica Hinnant (.Lka_civil). String Kof Native: len @16, bytes
            # @24, NUL @24+len (layout kof_string_from_literal). Inválido =>
            # "" (add) / 0 (diff); fora de 1..9999 => "". Paridade byte-idêntica
            # JVM/JS (matriz stdtime2; x86 roda local = prova; harness C 200k
            # fuzz 0 fails). riscv64/aarch64: fatia B própria (TIME002 mantém).

            .Lka_parse2:
                testq %rdi, %rdi
                jz .Lka_p_bad
                cmpl $10, 16(%rdi)
                jne .Lka_p_bad
                cmpb $45, 28(%rdi)
                jne .Lka_p_bad
                cmpb $45, 31(%rdi)
                jne .Lka_p_bad
                xorl %eax, %eax
            .Lka_p_digit:
                cmpl $10, %eax
                jae .Lka_p_num
                cmpb $45, 24(%rdi,%rax)
                je .Lka_p_skipc
                cmpb $47, 24(%rdi,%rax)
                jle .Lka_p_bad
                cmpb $58, 24(%rdi,%rax)
                jge .Lka_p_bad
            .Lka_p_skipc:
                incl %eax
                jmp .Lka_p_digit
            .Lka_p_num:
                xorl %r8d, %r8d
                xorl %ecx, %ecx
            .Lka_p_yl:
                movzbl 24(%rdi,%rcx), %eax
                subl $48, %eax
                imull $10, %r8d, %r8d
                addl %eax, %r8d
                incl %ecx
                cmpl $4, %ecx
                jb .Lka_p_yl
                movl %r8d, 0(%rsi)
                xorl %r8d, %r8d
                movl $5, %ecx
            .Lka_p_ml:
                movzbl 24(%rdi,%rcx), %eax
                subl $48, %eax
                imull $10, %r8d, %r8d
                addl %eax, %r8d
                incl %ecx
                cmpl $7, %ecx
                jb .Lka_p_ml
                movl %r8d, 4(%rsi)
                xorl %r8d, %r8d
                movl $8, %ecx
            .Lka_p_dl:
                movzbl 24(%rdi,%rcx), %eax
                subl $48, %eax
                imull $10, %r8d, %r8d
                addl %eax, %r8d
                incl %ecx
                cmpl $10, %ecx
                jb .Lka_p_dl
                movl %r8d, 8(%rsi)
                movl 0(%rsi), %r8d
                movl 4(%rsi), %r9d
                movl 8(%rsi), %edx
                movl %r8d, %edi
                movl %r9d, %esi
                call .Lkd_valid
                ret
            .Lka_p_bad:
                xorl %eax, %eax
                ret

            .Lka_civil:
                addl $719468, %eax
                xorl %edx, %edx
                movl $146097, %ecx
                divl %ecx
                movl %eax, %r9d
                movl %edx, %r10d
                movl %r10d, %eax
                xorl %edx, %edx
                movl $1460, %ecx
                divl %ecx
                movl %r10d, %r8d
                subl %eax, %r8d
                movl %r10d, %eax
                xorl %edx, %edx
                movl $36524, %ecx
                divl %ecx
                addl %eax, %r8d
                movl %r10d, %eax
                xorl %edx, %edx
                movl $146096, %ecx
                divl %ecx
                subl %eax, %r8d
                movl %r8d, %eax
                xorl %edx, %edx
                movl $365, %ecx
                divl %ecx
                movl %eax, %r8d
                movl %r9d, %edi
                imull $400, %edi, %edi
                addl %r8d, %edi
                movl %r8d, %r11d
                imull $365, %r11d, %r11d
                movl %r8d, %eax
                shrl $2, %eax
                addl %eax, %r11d
                movl %r8d, %eax
                xorl %edx, %edx
                movl $100, %ecx
                divl %ecx
                subl %eax, %r11d
                movl %r10d, %edx
                subl %r11d, %edx
                movl %edx, %esi
                imull $5, %edx, %eax
                addl $2, %eax
                xorl %edx, %edx
                movl $153, %ecx
                divl %ecx
                movl %eax, %r8d
                imull $153, %eax, %eax
                addl $2, %eax
                xorl %edx, %edx
                movl $5, %ecx
                divl %ecx
                movl %esi, %edx
                subl %eax, %edx
                incl %edx
                cmpl $10, %r8d
                jae .Lka_c_late
                addl $3, %r8d
                movl %r8d, %esi
                ret
            .Lka_c_late:
                subl $9, %r8d
                movl %r8d, %esi
                incl %edi
                ret

            .Lka_put4:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $1000, %ecx
                divl %ecx
                addl $48, %eax
                movb %al, (%rdi)
                incq %rdi
                movl %edx, %eax
                xorl %edx, %edx
                movl $100, %ecx
                divl %ecx
                addl $48, %eax
                movb %al, (%rdi)
                incq %rdi
                movl %edx, %eax
                xorl %edx, %edx
                movl $10, %ecx
                divl %ecx
                addl $48, %eax
                movb %al, (%rdi)
                incq %rdi
                addl $48, %edx
                movb %dl, (%rdi)
                incq %rdi
                ret
            .Lka_put2:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $10, %ecx
                divl %ecx
                addl $48, %eax
                movb %al, (%rdi)
                incq %rdi
                addl $48, %edx
                movb %dl, (%rdi)
                incq %rdi
                ret

            .globl kof_time_addDays
            .type kof_time_addDays, @function
            kof_time_addDays:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                xorl %r14d, %r14d
                movslq %esi, %r13
                movq %rsp, %rsi
                call .Lka_parse2
                testl %eax, %eax
                jz .Lka_ad_render
                movl 0(%rsp), %edi
                movl 4(%rsp), %esi
                movl 8(%rsp), %edx
                call .Lkd_epoch
                cltq
                addq %r13, %rax
                cmpq $-719162, %rax
                jl .Lka_ad_render
                cmpq $2932896, %rax
                jg .Lka_ad_render
                call .Lka_civil
                movl %edi, 0(%rsp)
                movl %esi, 4(%rsp)
                movl %edx, 8(%rsp)
                movl $1, %r14d
            .Lka_ad_render:
                testl %r14d, %r14d
                jz .Lka_ad_len0
                movl $10, %r12d
                jmp .Lka_ad_alloc
            .Lka_ad_len0:
                xorl %r12d, %r12d
            .Lka_ad_alloc:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                testl %r14d, %r14d
                jz .Lka_ad_done
                leaq 24(%r15), %rdi
                movl 0(%rsp), %r13d
                call .Lka_put4
                movb $45, (%rdi)
                incq %rdi
                movl 4(%rsp), %r13d
                call .Lka_put2
                movb $45, (%rdi)
                incq %rdi
                movl 8(%rsp), %r13d
                call .Lka_put2
                movb $0, (%rdi)
            .Lka_ad_done:
                movq %r15, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_time_diffDays
            .type kof_time_diffDays, @function
            kof_time_diffDays:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $32, %rsp
                movq %rsi, %r12
                movq %rsp, %rsi
                call .Lka_parse2
                testl %eax, %eax
                jz .Lka_dd0
                movq %r12, %rdi
                leaq 12(%rsp), %rsi
                call .Lka_parse2
                testl %eax, %eax
                jz .Lka_dd0
                movl 0(%rsp), %edi
                movl 4(%rsp), %esi
                movl 8(%rsp), %edx
                call .Lkd_epoch
                movl %eax, %r13d
                movl 12(%rsp), %edi
                movl 16(%rsp), %esi
                movl 20(%rsp), %edx
                call .Lkd_epoch
                subl %r13d, %eax
                jmp .Lka_dd_done
            .Lka_dd0:
                xorl %eax, %eax
            .Lka_dd_done:
                addq $32, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}

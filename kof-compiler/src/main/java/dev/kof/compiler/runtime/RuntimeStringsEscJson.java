package dev.kof.compiler.runtime;

/**
 * kof.strings JSON escape (STDLIB S3.1c): escapeJson — corpo de string
 * literal JSON (RFC 8259). Encadeado em RuntimeStrings.emit (mantém a família
 * de escapes &lt;=500). Mesma máquina JVM/JS/riscv (oracle Python).
 */
public final class RuntimeStringsEscJson {

    private RuntimeStringsEscJson() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.strings escapeJson (STDLIB S3.1c) ─────────────────────
            # kof_strings_escapeJson(rdi=v) -> String. Corpo de string
            # literal JSON (RFC 8259): \\ -> \\\\, " -> \", \b \f \n \r \t
            # 2-char, ctrl <0x20 -> \\u00xx (hex minúsculo), demais (incl
            # >=128) copiados. null/vazia => original (paridade escapeHtml).
            .globl kof_strings_escapeJson
            .type kof_strings_escapeJson, @function
            kof_strings_escapeJson:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_ej_orig
                movl 16(%rbx), %r12d     # len
                testl %r12d, %r12d
                jle .Lv_ej_orig
                imull $6, %r12d, %edi    # 6*len + 25, align 16
                addl $25, %edi
                addl $15, %edi
                andl $-16, %edi
                call kof_alloc
                movq %rax, %r13          # novo
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                xorl %r14d, %r14d        # i
                xorl %r15d, %r15d        # pos
            .Lv_ej_loop:
                cmpl %r12d, %r14d
                jge .Lv_ej_term
                movzbl 24(%rbx,%r14), %eax
                cmpl $92, %eax           # \\
                je .Lv_ej_bs
                cmpl $34, %eax           # "
                je .Lv_ej_dq
                cmpl $8, %eax
                je .Lv_ej_b
                cmpl $12, %eax
                je .Lv_ej_f
                cmpl $10, %eax
                je .Lv_ej_n
                cmpl $13, %eax
                je .Lv_ej_r
                cmpl $9, %eax
                je .Lv_ej_t
                cmpl $32, %eax
                jb .Lv_ej_u              # ctrl -> \\u00xx
                movb %al, 24(%r13,%r15)  # literal
                incl %r15d
                incl %r14d
                jmp .Lv_ej_loop
            .Lv_ej_bs:
                movb $92, 24(%r13,%r15)
                movb $92, 25(%r13,%r15)
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_dq:
                movb $92, 24(%r13,%r15)
                movb $34, 25(%r13,%r15)
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_b:
                movb $92, 24(%r13,%r15)
                movb $98, 25(%r13,%r15)  # b
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_f:
                movb $92, 24(%r13,%r15)
                movb $102, 25(%r13,%r15) # f
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_n:
                movb $92, 24(%r13,%r15)
                movb $110, 25(%r13,%r15) # n
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_r:
                movb $92, 24(%r13,%r15)
                movb $114, 25(%r13,%r15) # r
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_t:
                movb $92, 24(%r13,%r15)
                movb $116, 25(%r13,%r15) # t
                addl $2, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_u:
                # \\u00 + 2 hex (minúsculo) do byte c (0..0x1f)
                movb $92, 24(%r13,%r15)
                movb $117, 25(%r13,%r15)  # u
                movb $48, 26(%r13,%r15)    # 0
                movb $48, 27(%r13,%r15)    # 0
                movl %eax, %ecx
                shrl $4, %ecx
                andl $15, %ecx             # nibble ALTO (0/1) -> 1º dígito
                cmpl $10, %ecx
                jl .Lv_ej_u1
                addl $87, %ecx
                jmp .Lv_ej_u2
            .Lv_ej_u1:
                addl $48, %ecx
            .Lv_ej_u2:
                movb %cl, 28(%r13,%r15)
                movl %eax, %ecx
                andl $15, %ecx             # nibble BAIXO -> 2º dígito
                cmpl $10, %ecx
                jl .Lv_ej_u3
                addl $87, %ecx
                jmp .Lv_ej_u4
            .Lv_ej_u3:
                addl $48, %ecx
            .Lv_ej_u4:
                movb %cl, 29(%r13,%r15)
                addl $6, %r15d
                jmp .Lv_ej_adv
            .Lv_ej_adv:
                incl %r14d
                jmp .Lv_ej_loop
            .Lv_ej_term:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lv_ej_done
            .Lv_ej_orig:
                movq %rbx, %rax
            .Lv_ej_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

package dev.kof.compiler.runtime;

/**
 * kof.strings HTML escape (STDLIB S3.1): escapeHtml — 5 chars especiais ->
 * entidade (&amp; &lt; &gt; &quot; &#39;), demais bytes copiados (saída só
 * toca ASCII nas entidades). Buffer de 6*len. Mesma máquina JVM/JS/riscv
 * (oracle Python; null/"" => original). unescapeHtml é a próxima unidade.
 */
public final class RuntimeStringsEsc {

    private RuntimeStringsEsc() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.strings escapeHtml (STDLIB S3.1) ─────────────────────
            # kof_strings_escapeHtml(rdi=v) -> String; 6*len buffer.
            .globl kof_strings_escapeHtml
            .type kof_strings_escapeHtml, @function
            kof_strings_escapeHtml:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # v
                testq %rbx, %rbx
                jz .Lv_esc_ret_orig
                movl 16(%rbx), %r12d     # len
                testl %r12d, %r12d
                jle .Lv_esc_ret_orig
                imull $6, %r12d, %edi    # 6*len + 25
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
            .Lv_esc_loop:
                cmpl %r12d, %r14d
                jge .Lv_esc_term
                movzbl 24(%rbx,%r14), %eax
                cmpl $38, %eax           # &
                je .Lv_esc_amp
                cmpl $60, %eax           # <
                je .Lv_esc_lt
                cmpl $62, %eax           # >
                je .Lv_esc_gt
                cmpl $34, %eax           # "
                je .Lv_esc_quot
                cmpl $39, %eax           # '
                je .Lv_esc_apos
                movb %al, 24(%r13,%r15)
                incl %r15d
                incl %r14d
                jmp .Lv_esc_loop
            .Lv_esc_amp:
                call .Lv_esc_amp2
                addl $5, %r15d
                jmp .Lv_esc_adv
            .Lv_esc_amp2:
                movb $38, 24(%r13,%r15)  # &
                movb $97, 25(%r13,%r15)  # a
                movb $109, 26(%r13,%r15) # m
                movb $112, 27(%r13,%r15) # p
                movb $59, 28(%r13,%r15)  # ;
                ret
            .Lv_esc_lt:
                movb $38, 24(%r13,%r15)
                movb $108, 25(%r13,%r15) # l
                movb $116, 26(%r13,%r15) # t
                movb $59, 27(%r13,%r15)
                addl $4, %r15d
                jmp .Lv_esc_adv
            .Lv_esc_gt:
                movb $38, 24(%r13,%r15)
                movb $103, 25(%r13,%r15) # g
                movb $116, 26(%r13,%r15)
                movb $59, 27(%r13,%r15)
                addl $4, %r15d
                jmp .Lv_esc_adv
            .Lv_esc_quot:
                movb $38, 24(%r13,%r15)
                movb $113, 25(%r13,%r15) # q
                movb $117, 26(%r13,%r15) # u
                movb $111, 27(%r13,%r15) # o
                movb $116, 28(%r13,%r15)
                movb $59, 29(%r13,%r15)
                addl $6, %r15d
                jmp .Lv_esc_adv
            .Lv_esc_apos:
                movb $38, 24(%r13,%r15)
                movb $35, 25(%r13,%r15)  # #
                movb $51, 26(%r13,%r15)  # 3
                movb $57, 27(%r13,%r15)  # 9
                movb $59, 28(%r13,%r15)
                addl $5, %r15d
                jmp .Lv_esc_adv
            .Lv_esc_adv:
                incl %r14d
                jmp .Lv_esc_loop
            .Lv_esc_term:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lv_esc_done
            .Lv_esc_ret_orig:
                movq %rbx, %rax
            .Lv_esc_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

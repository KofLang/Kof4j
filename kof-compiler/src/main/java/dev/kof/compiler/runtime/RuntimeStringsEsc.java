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

            # kof_strings_unescapeHtml(rdi=v) -> String (S3.1b).
            # 5 nomeadas (&amp; &lt; &gt; &quot; &apos;) + numéricos
            # &#DDD;/&#xHH; (valid: >0, <0x10000, não-surogate). Qualquer
            # outro "&" fica LITERAL. Saída <= len (entidade 4-8 chars ->
            # <=3 bytes UTF-8; & literal 1->1) => buffer len+25. >=128 cópia
            # (mesma paridade do escapeHtml: entidades são ASCII).
            .globl kof_strings_unescapeHtml
            .type kof_strings_unescapeHtml, @function
            kof_strings_unescapeHtml:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_un_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_un_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                xorl %r14d, %r14d        # i
                xorl %r15d, %r15d        # pos
            .Lv_un_loop:
                cmpl %r12d, %r14d
                jge .Lv_un_term
                movzbl 24(%rbx,%r14), %eax
                cmpl $38, %eax           # &
                jne .Lv_un_plain
                # &amp;  (i+5<=len; 38 97 109 112 59)
                leal 5(%r14), %ecx
                cmpl %r12d, %ecx
                ja .Lv_un_lt
                cmpb $97, 25(%rbx,%r14)
                jne .Lv_un_lt
                cmpb $109, 26(%rbx,%r14)
                jne .Lv_un_lt
                cmpb $112, 27(%rbx,%r14)
                jne .Lv_un_lt
                cmpb $59, 28(%rbx,%r14)
                jne .Lv_un_lt
                movb $38, 24(%r13,%r15)
                incl %r15d
                movl %ecx, %r14d
                jmp .Lv_un_loop
            .Lv_un_lt:                   # &lt; (4)
                leal 4(%r14), %ecx
                cmpl %r12d, %ecx
                ja .Lv_un_num
                cmpb $108, 25(%rbx,%r14)  # 'l'
                jne .Lv_un_gt
                cmpb $116, 26(%rbx,%r14)  # 't'
                jne .Lv_un_gt
                cmpb $59, 27(%rbx,%r14)   # ';'
                jne .Lv_un_gt
                movb $60, 24(%r13,%r15)
                incl %r15d
                movl %ecx, %r14d
                jmp .Lv_un_loop
            .Lv_un_gt:                   # &gt; (4)
                leal 4(%r14), %ecx
                cmpl %r12d, %ecx
                ja .Lv_un_num
                cmpb $103, 25(%rbx,%r14)  # 'g'
                jne .Lv_un_quot
                cmpb $116, 26(%rbx,%r14)  # 't'
                jne .Lv_un_quot
                cmpb $59, 27(%rbx,%r14)
                jne .Lv_un_quot
                movb $62, 24(%r13,%r15)
                incl %r15d
                movl %ecx, %r14d
                jmp .Lv_un_loop
            .Lv_un_quot:                 # &quot; (6)
                leal 6(%r14), %ecx
                cmpl %r12d, %ecx
                ja .Lv_un_apos
                cmpb $113, 25(%rbx,%r14)  # q
                jne .Lv_un_apos
                cmpb $117, 26(%rbx,%r14)  # u
                jne .Lv_un_apos
                cmpb $111, 27(%rbx,%r14)  # o
                jne .Lv_un_apos
                cmpb $116, 28(%rbx,%r14)  # t
                jne .Lv_un_apos
                cmpb $59, 29(%rbx,%r14)
                jne .Lv_un_apos
                movb $34, 24(%r13,%r15)
                incl %r15d
                movl %ecx, %r14d
                jmp .Lv_un_loop
            .Lv_un_apos:                 # &apos; (6)
                leal 6(%r14), %ecx
                cmpl %r12d, %ecx
                ja .Lv_un_num
                cmpb $97, 25(%rbx,%r14)   # a  (amp já falhou p/ byte2 m, então aqui é outro)
                jne .Lv_un_num
                cmpb $112, 26(%rbx,%r14)  # p
                jne .Lv_un_num
                cmpb $111, 27(%rbx,%r14)  # o
                jne .Lv_un_num
                cmpb $115, 28(%rbx,%r14)  # s
                jne .Lv_un_num
                cmpb $59, 29(%rbx,%r14)
                jne .Lv_un_num
                movb $39, 24(%r13,%r15)
                incl %r15d
                movl %ecx, %r14d
                jmp .Lv_un_loop
            .Lv_un_num:                  # &#...?
                leal 1(%r14), %ecx
                cmpl %r12d, %ecx
                jae .Lv_un_lit
                cmpb $35, 25(%rbx,%r14)  # '#'
                jne .Lv_un_lit
                xorl %esi, %esi          # hx
                leal 2(%r14), %r8d       # j = i+2 (dígitos começam após &#)
                cmpl %r12d, %r8d
                jae .Lv_un_lit
                movzbl 24(%rbx,%r8), %eax
                cmpl $120, %eax          # x
                je .Lv_un_sethx
                cmpl $88, %eax           # X
                jne .Lv_un_dig0
            .Lv_un_sethx:
                movl $1, %esi
                incl %r8d
            .Lv_un_dig0:
                movl %r8d, %r9d          # k = j
                xorl %r10d, %r10d        # acc
            .Lv_un_digits:
                cmpl %r12d, %r9d
                jae .Lv_un_digitsend
                movzbl 24(%rbx,%r9), %eax
                movl %eax, %r11d
                subl $48, %r11d
                cmpl $9, %r11d
                jbe .Lv_un_dok
                testl %esi, %esi
                jz .Lv_un_digitsend
                movl %eax, %r11d
                subl $97, %r11d
                cmpl $5, %r11d
                jbe .Lv_un_dok10
                movl %eax, %r11d
                subl $65, %r11d
                cmpl $5, %r11d
                ja .Lv_un_digitsend
                addl $10, %r11d
                jmp .Lv_un_dok
            .Lv_un_dok10:
                addl $10, %r11d
            .Lv_un_dok:
                imull $10, %r10d, %eax
                testl %esi, %esi
                jz .Lv_un_acc
                shll $4, %r10d
                orl %r11d, %r10d
                jmp .Lv_un_ovf
            .Lv_un_acc:
                movl %eax, %r10d
                addl %r11d, %r10d
            .Lv_un_ovf:
                cmpl $0x10FFFF, %r10d
                ja .Lv_un_digitsend      # estouro: inválida (fica literal)
                incl %r9d
                jmp .Lv_un_digits
            .Lv_un_digitsend:
                cmpl %r8d, %r9d
                je .Lv_un_lit            # nenhum dígito
                cmpl %r12d, %r9d
                jae .Lv_un_lit           # ';' ausente
                cmpb $59, 24(%rbx,%r9)
                jne .Lv_un_lit
                testl %r10d, %r10d
                jz .Lv_un_lit            # 0 inválido
                cmpl $0x10000, %r10d
                jae .Lv_un_lit
                movl %r10d, %eax
                subl $0xD800, %eax
                cmpl $0x7FF, %eax
                jbe .Lv_un_lit           # surrogate
                # UTF-8 emit (1/2/3 bytes)
                cmpl $0x80, %r10d
                jb .Lv_un_u1
                cmpl $0x800, %r10d
                jb .Lv_un_u2
                movl %r10d, %eax
                shrl $12, %eax
                orl $0xE0, %eax
                movb %al, 24(%r13,%r15)
                movl %r10d, %eax
                shrl $6, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                movl %r10d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 26(%r13,%r15)
                addl $3, %r15d
                jmp .Lv_un_afternum
            .Lv_un_u2:
                movl %r10d, %eax
                shrl $6, %eax
                orl $0xC0, %eax
                movb %al, 24(%r13,%r15)
                movl %r10d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                addl $2, %r15d
                jmp .Lv_un_afternum
            .Lv_un_u1:
                movb %r10b, 24(%r13,%r15)
                incl %r15d
            .Lv_un_afternum:
                leal 1(%r9), %r14d       # i = k+1 (pula o ';')
                jmp .Lv_un_loop
            .Lv_un_lit:
                movb $38, 24(%r13,%r15)
                incl %r15d
                incl %r14d
                jmp .Lv_un_loop
            .Lv_un_plain:
                movb %al, 24(%r13,%r15)
                incl %r15d
                incl %r14d
                jmp .Lv_un_loop
            .Lv_un_gt2:
            .Lv_un_term:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lv_un_done
            .Lv_un_orig:
                movq %rbx, %rax
            .Lv_un_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

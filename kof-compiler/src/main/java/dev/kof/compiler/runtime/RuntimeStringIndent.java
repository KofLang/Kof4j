package dev.kof.compiler.runtime;

/**
 * STDLIB S3.3 (x86_64) — kof.strings indent / dedent.
 * indent(rdi=v, esi=n): prefixa cada linha não-vazia com n espaços.
 * dedent(rdi=v): remove a indentação comum mínima de linhas com conteúdo.
 */
public final class RuntimeStringIndent {

    private RuntimeStringIndent() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_strings_indent(rdi=v, esi=n) -> String
            .globl kof_strings_indent
            .type kof_strings_indent, @function
            kof_strings_indent:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp           # [rsp] = dst, [rsp+8] = out_pos, [rsp+16] = n
                movq %rdi, %rbx          # v
                movl %esi, %r12d         # n
                testq %rbx, %rbx
                jz .Lsi_ret_orig
                testl %r12d, %r12d
                jle .Lsi_ret_orig
                movl 16(%rbx), %r13d     # len
                testl %r13d, %r13d
                jle .Lsi_ret_orig

                # 1. Contar quebras de linha para dimensionar o buffer com folga
                xorl %r14d, %r14d        # i = 0
                movl $1, %r15d           # line_count = 1
            .Lsi_cnt_lines:
                cmpl %r13d, %r14d
                jge .Lsi_alloc
                movzbl 24(%rbx,%r14), %eax
                incl %r14d
                cmpl $10, %eax           # '\\n'
                jne .Lsi_cnt_lines
                incl %r15d
                jmp .Lsi_cnt_lines

            .Lsi_alloc:
                # max_len = len + line_count * n
                movl %r15d, %eax
                imull %r12d, %eax
                addl %r13d, %eax
                leal 25(%rax), %edi
                call kof_alloc
                movq %rax, (%rsp)        # dst
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 20(%rax)
                movq $0, 8(%rsp)         # out_pos = 0
                movl %r12d, 16(%rsp)     # n

                xorl %r14d, %r14d        # i = 0 (in_pos)
                movl $1, %r15d           # at_line_start = 1
            .Lsi_loop:
                cmpl %r13d, %r14d
                jge .Lsi_term
                movzbl 24(%rbx,%r14), %eax
                incl %r14d
                cmpl $10, %eax           # '\\n'
                je .Lsi_is_lf
                cmpl $13, %eax           # '\\r'
                je .Lsi_put_char

                # Caractere normal. Se estiver no início da linha, insere n espaços
                testl %r15d, %r15d
                jz .Lsi_put_char
                # emite n espaços
                xorl %ecx, %ecx
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
            .Lsi_pad_loop:
                cmpl 16(%rsp), %ecx
                jge .Lsi_pad_done
                movb $32, 24(%rdi,%rsi)
                incq %rsi
                incl %ecx
                jmp .Lsi_pad_loop
            .Lsi_pad_done:
                movq %rsi, 8(%rsp)
                xorl %r15d, %r15d        # at_line_start = 0

            .Lsi_put_char:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movb %al, 24(%rdi,%rsi)
                incq 8(%rsp)
                jmp .Lsi_loop

            .Lsi_is_lf:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movb $10, 24(%rdi,%rsi)
                incq 8(%rsp)
                movl $1, %r15d           # at_line_start = 1
                jmp .Lsi_loop

            .Lsi_term:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movl %esi, 16(%rdi)      # len
                movb $0, 24(%rdi,%rsi)   # NUL
                movq %rdi, %rax
                jmp .Lsi_done

            .Lsi_ret_orig:
                movq %rbx, %rax
            .Lsi_done:
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_dedent(rdi=v) -> String
            .globl kof_strings_dedent
            .type kof_strings_dedent, @function
            kof_strings_dedent:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp           # [rsp]=dst, [rsp+8]=out_pos, [rsp+16]=min_indent
                movq %rdi, %rbx          # v
                testq %rbx, %rbx
                jz .Lsd_ret_orig
                movl 16(%rbx), %r12d     # len
                testl %r12d, %r12d
                jle .Lsd_ret_orig

                # Passo 1: Calcular min_indent entre linhas com conteúdo
                movl $-1, %r13d          # min_indent = -1
                xorl %r14d, %r14d        # i = 0
            .Lsd_scan_line:
                cmpl %r12d, %r14d
                jge .Lsd_scan_done
                # Conta espaços e tabs no início da linha
                xorl %r15d, %r15d        # ws = 0
            .Lsd_cnt_ws:
                leal (%r14,%r15), %eax
                cmpl %r12d, %eax
                jge .Lsd_check_content
                movzbl 24(%rbx,%rax), %ecx
                cmpl $32, %ecx           # ' '
                je .Lsd_inc_ws
                cmpl $9, %ecx            # '\\t'
                je .Lsd_inc_ws
                jmp .Lsd_check_content
            .Lsd_inc_ws:
                incl %r15d
                jmp .Lsd_cnt_ws

            .Lsd_check_content:
                leal (%r14,%r15), %eax
                cmpl %r12d, %eax
                jge .Lsd_skip_to_lf
                movzbl 24(%rbx,%rax), %ecx
                cmpl $10, %ecx           # '\\n'
                je .Lsd_skip_to_lf
                cmpl $13, %ecx           # '\\r'
                je .Lsd_skip_to_lf
                # Linha tem conteúdo não-espaço!
                cmpl $-1, %r13d
                je .Lsd_set_min
                cmpl %r13d, %r15d
                jge .Lsd_skip_to_lf
            .Lsd_set_min:
                movl %r15d, %r13d

            .Lsd_skip_to_lf:
                cmpl %r12d, %r14d
                jge .Lsd_scan_done
                movzbl 24(%rbx,%r14), %eax
                incl %r14d
                cmpl $10, %eax
                jne .Lsd_skip_to_lf
                jmp .Lsd_scan_line

            .Lsd_scan_done:
                # Se min_indent <= 0 ou -1, retorna original
                cmpl $0, %r13d
                jle .Lsd_ret_orig

                # Passo 2: Alocar buffer (saída <= len) e remover min_indent
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, (%rsp)
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 20(%rax)
                movq $0, 8(%rsp)         # out_pos = 0
                movl %r13d, 16(%rsp)     # min_indent

                xorl %r14d, %r14d        # in_pos = 0
                movl $1, %r15d           # at_line_start = 1
                xorl %ecx, %ecx          # ws_skipped = 0
            .Lsd_emit_loop:
                cmpl %r12d, %r14d
                jge .Lsd_emit_term
                movzbl 24(%rbx,%r14), %eax
                incl %r14d

                cmpl $10, %eax           # '\\n'
                je .Lsd_emit_lf
                cmpl $13, %eax           # '\\r'
                je .Lsd_put_byte

                testl %r15d, %r15d
                jz .Lsd_put_byte
                # No início da linha: se for espaço/tab e ws_skipped < min_indent, pula
                cmpl $32, %eax
                je .Lsd_try_skip
                cmpl $9, %eax
                je .Lsd_try_skip
                # Não é espaço: para de pular
                xorl %r15d, %r15d
                jmp .Lsd_put_byte

            .Lsd_try_skip:
                cmpl 16(%rsp), %ecx
                jge .Lsd_stop_skip
                incl %ecx
                jmp .Lsd_emit_loop
            .Lsd_stop_skip:
                xorl %r15d, %r15d

            .Lsd_put_byte:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movb %al, 24(%rdi,%rsi)
                incq 8(%rsp)
                jmp .Lsd_emit_loop

            .Lsd_emit_lf:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movb $10, 24(%rdi,%rsi)
                incq 8(%rsp)
                movl $1, %r15d           # at_line_start = 1
                xorl %ecx, %ecx          # ws_skipped = 0
                jmp .Lsd_emit_loop

            .Lsd_emit_term:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movl %esi, 16(%rdi)
                movb $0, 24(%rdi,%rsi)
                movq %rdi, %rax
                jmp .Lsd_done

            .Lsd_ret_orig:
                movq %rbx, %rax
            .Lsd_done:
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

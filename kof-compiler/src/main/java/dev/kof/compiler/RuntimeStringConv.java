package dev.kof.compiler;

/**
 * Emissão do ASM de conversão de tipos → String (kof_int_to_string,
 * kof_char_to_string, kof_long_to_string, kof_bool_to_string,
 * kof_float_to_string, kof_double_to_string) do runtime nativo. Domínio
 * isolado do NativeRuntime -- a extração NÃO muda o corpo (refactor preserva
 * semântica).
 */
final class RuntimeStringConv {

    private RuntimeStringConv() {}

    static void emitIntToString(StringBuilder sb) {
        sb.append("""
            .globl kof_int_to_string
            .type kof_int_to_string, @function
            kof_int_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %eax
                movq $0, %r12
                testl %eax, %eax
                jns .Lkof_int_to_str_pos
                movq $1, %r12
                negl %eax
            .Lkof_int_to_str_pos:
                movl %eax, %r13d
                movq $0, %rbx
                movl $10, %ecx
            .Lkof_int_to_str_count:
                xorl %edx, %edx
                divl %ecx
                incq %rbx
                testl %eax, %eax
                jnz .Lkof_int_to_str_count
                testq %r12, %r12
                jz .Lkof_int_to_str_count_done
                incq %rbx
            .Lkof_int_to_str_count_done:
                leaq 25(%rbx), %rdi
                call kof_alloc
                pushq %rax
                leaq 23(%rax), %rsi
                addq %rbx, %rsi
                movl %r13d, %eax
                movl $10, %ecx
            .Lkof_int_to_str_loop:
                xorl %edx, %edx
                divl %ecx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                testl %eax, %eax
                jnz .Lkof_int_to_str_loop
                testq %r12, %r12
                jz .Lkof_int_to_str_negdone
                movb $45, (%rsi)
            .Lkof_int_to_str_negdone:
                testq %r12, %r12
                jnz .Lkof_int_to_str_ready
                incq %rsi
            .Lkof_int_to_str_ready:
                popq %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %ebx, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
             """);
    }

    // kof_char_to_string: %edi = codepoint (Char 16-bit nativo) → String
    // UTF-8 no layout do kof: +0 refcount, +16 len (bytes), +24 bytes.
    // 0..0x7F → 1 byte; 0x80..0x7FF → 2; 0x800..0xFFFF → 3.
    static void emitCharToString(StringBuilder sb) {
        sb.append("""
            .globl kof_char_to_string
            .type kof_char_to_string, @function
            kof_char_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %r13d        # salva o codepoint (kof_alloc suja os regs caller-saved)
                movl $0, %r12d          # r12 = nº de bytes utf8
                cmpl $128, %r13d
                jge .Lkof_char_2
                movl $1, %r12d
                jmp .Lkof_char_alloc
            .Lkof_char_2:
                cmpl $2048, %r13d
                jge .Lkof_char_3
                movl $2, %r12d
                jmp .Lkof_char_alloc
            .Lkof_char_3:
                movl $3, %r12d
            .Lkof_char_alloc:
                leaq 25(, %r12, 1), %rdi
                call kof_alloc
                pushq %rax
                leaq 24(%rax), %rsi     # base dos bytes
                cmpl $128, %r13d
                jge .Lkof_char_enc2
                movl %r13d, %ecx
                movb %cl, (%rsi)
                jmp .Lkof_char_done
            .Lkof_char_enc2:
                cmpl $2048, %r13d
                jge .Lkof_char_enc3
                movl %r13d, %ecx
                shrl $6, %ecx
                orl $0xC0, %ecx
                movb %cl, (%rsi)
                movl %r13d, %ecx
                andl $0x3F, %ecx
                orl $0x80, %ecx
                movb %cl, 1(%rsi)
                jmp .Lkof_char_done
            .Lkof_char_enc3:
                movl %r13d, %ecx
                shrl $12, %ecx
                orl $0xE0, %ecx
                movb %cl, (%rsi)
                movl %r13d, %ecx
                shrl $6, %ecx
                andl $0x3F, %ecx
                orl $0x80, %ecx
                movb %cl, 1(%rsi)
                movl %r13d, %ecx
                andl $0x3F, %ecx
                orl $0x80, %ecx
                movb %cl, 2(%rsi)
            .Lkof_char_done:
                popq %rax
                movl $1, 0(%rax)        # refcount
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl %r12d, 16(%rax)    # len
                movl $0, 20(%rax)
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitLongToString(StringBuilder sb) {
        sb.append("""
            .globl kof_long_to_string
            .type kof_long_to_string, @function
            kof_long_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rax
                movq $0, %r12
                testq %rax, %rax
                jns .Lkof_long_to_str_pos
                movq $1, %r12
                negq %rax
            .Lkof_long_to_str_pos:
                movq %rax, %r13
                movq $0, %rbx
                movq $10, %rcx
            .Lkof_long_to_str_count:
                xorq %rdx, %rdx
                divq %rcx
                incq %rbx
                testq %rax, %rax
                jnz .Lkof_long_to_str_count
                testq %r12, %r12
                jz .Lkof_long_to_str_count_done
                incq %rbx
            .Lkof_long_to_str_count_done:
                leaq 25(%rbx), %rdi
                call kof_alloc
                pushq %rax
                leaq 23(%rax), %rsi
                addq %rbx, %rsi
                movq %r13, %rax
                movq $10, %rcx
            .Lkof_long_to_str_loop:
                xorq %rdx, %rdx
                divq %rcx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                testq %rax, %rax
                jnz .Lkof_long_to_str_loop
                testq %r12, %r12
                jz .Lkof_long_to_str_negdone
                movb $45, (%rsi)
            .Lkof_long_to_str_negdone:
                testq %r12, %r12
                jnz .Lkof_long_to_str_ready
                incq %rsi
            .Lkof_long_to_str_ready:
                popq %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %ebx, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitBoolToString(StringBuilder sb) {
        sb.append("""
            .globl kof_bool_to_string
            .type kof_bool_to_string, @function
            kof_bool_to_string:
                testl %edi, %edi
                jz .Lkof_bool_to_str_false
                leaq .Lkof_str_true(%rip), %rdi
                movl $4, %esi
                jmp .Lkof_bool_to_str_make
            .Lkof_bool_to_str_false:
                leaq .Lkof_str_false(%rip), %rdi
                movl $5, %esi
            .Lkof_bool_to_str_make:
                jmp kof_string_from_literal
            """);
    }

    static void emitFloatToString(StringBuilder sb) {
        sb.append("""
            .globl kof_float_to_string
            .type kof_float_to_string, @function
            kof_float_to_string:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                subq $80, %rsp
                leaq -72(%rbp), %r12
                cvtss2sd %xmm0, %xmm0
                movq %r12, %rdi
                movq $64, %rsi
                leaq .Lfmt_float(%rip), %rdx
                movl $1, %eax
                movq %rsp, %rbx
                andq $-16, %rsp             # alinha para snprintf
                call snprintf
                movq %rbx, %rsp
                xorl %edx, %edx
            .Lkof_flt_str_len:
                cmpb $0, (%r12,%rdx)
                je .Lkof_flt_str_gotlen
                incq %rdx
                jmp .Lkof_flt_str_len
            .Lkof_flt_str_gotlen:
                movl %edx, %esi
                movq %r12, %rdi
                call kof_string_from_literal
                addq $80, %rsp
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

    static void emitDoubleToString(StringBuilder sb) {
        sb.append("""
            .globl kof_double_to_string
            .type kof_double_to_string, @function
            kof_double_to_string:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                subq $80, %rsp
                leaq -72(%rbp), %r12
                movq %r12, %rdi
                movq $64, %rsi
                leaq .Lfmt_double(%rip), %rdx
                movl $1, %eax
                movq %rsp, %rbx
                andq $-16, %rsp             # alinha para snprintf
                call snprintf
                movq %rbx, %rsp
                xorl %edx, %edx
            .Lkof_dbl_str_len:
                cmpb $0, (%r12,%rdx)
                je .Lkof_dbl_str_gotlen
                incq %rdx
                jmp .Lkof_dbl_str_len
            .Lkof_dbl_str_gotlen:
                movl %edx, %esi
                movq %r12, %rdi
                call kof_string_from_literal
                addq $80, %rsp
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }
}
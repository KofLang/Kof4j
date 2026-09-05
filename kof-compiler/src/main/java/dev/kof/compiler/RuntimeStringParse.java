package dev.kof.compiler;

/**
 * Emissão do ASM de conversão String → número (kof_string_to_int/long/double/float)
 * do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeStringParse {

    private RuntimeStringParse() {}

    static void emitStringToInt(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_int
            .type kof_string_to_int, @function
            kof_string_to_int:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                testq %rdi, %rdi
                jz .Lkof_str_to_int_zero
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorq %r13, %r13
                xorl %ecx, %ecx
                xorl %r14d, %r14d
                testl %r12d, %r12d
                je .Lkof_str_to_int_done
                movzbl 24(%rbx), %eax
                cmpl $45, %eax
                jne .Lkof_str_to_int_loop
                incl %ecx
                incl %r14d
            .Lkof_str_to_int_loop:
                cmpl %r12d, %ecx
                jge .Lkof_str_to_int_neg
                movq %r13, %rax
                shlq $3, %r13
                addq %rax, %r13
                addq %rax, %r13
                movzbl 24(%rbx,%rcx), %eax
                subl $48, %eax
                movslq %eax, %rax
                addq %rax, %r13
                incl %ecx
                jmp .Lkof_str_to_int_loop
            .Lkof_str_to_int_neg:
                testl %r14d, %r14d
                jz .Lkof_str_to_int_done
                negq %r13
            .Lkof_str_to_int_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_str_to_int_zero:
                xorq %rax, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitStringToLong(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_long
            .type kof_string_to_long, @function
            kof_string_to_long:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                testq %rdi, %rdi
                jz .Lkof_str_to_long_zero
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorq %r13, %r13
                xorl %ecx, %ecx
                xorl %r14d, %r14d
                testl %r12d, %r12d
                je .Lkof_str_to_long_done
                movzbl 24(%rbx), %eax
                cmpl $45, %eax
                jne .Lkof_str_to_long_loop
                incl %ecx
                incl %r14d
            .Lkof_str_to_long_loop:
                cmpl %r12d, %ecx
                jge .Lkof_str_to_long_neg
                movq %r13, %rax
                shlq $3, %r13
                addq %rax, %r13
                addq %rax, %r13
                movzbl 24(%rbx,%rcx), %eax
                subl $48, %eax
                movslq %eax, %rax
                addq %rax, %r13
                incl %ecx
                jmp .Lkof_str_to_long_loop
            .Lkof_str_to_long_neg:
                testl %r14d, %r14d
                jz .Lkof_str_to_long_done
                negq %r13
            .Lkof_str_to_long_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_str_to_long_zero:
                xorq %rax, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitStringToDouble(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_dbl_ten:  .double 10.0
            .Lkof_dbl_one:  .double 1.0
            .Lkof_dbl_48:   .double 48.0
            .Lkof_dbl_neg:  .double -1.0
            .section .text
            .globl kof_string_to_double
            .type kof_string_to_double, @function
            kof_string_to_double:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                testq %rdi, %rdi
                jz .Lkof_str_to_dbl_zero
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorq %r13, %r13            # parte inteira acumulada
                xorl %ecx, %ecx            # cursor
                xorl %r14d, %r14d          # neg
                cmpl $0, %r12d
                je .Lkof_str_to_dbl_done
                movzbl 24(%rbx), %eax
                cmpl $45, %eax
                jne .Lkof_str_to_dbl_int
                incl %ecx
                movl $1, %r14d
            .Lkof_str_to_dbl_int:
                cmpl %r12d, %ecx
                jge .Lkof_str_to_dbl_frac
                movzbl 24(%rbx,%rcx), %eax
                cmpl $46, %eax
                je .Lkof_str_to_dbl_frac
                cmpb $101, %al
                je .Lkof_str_to_dbl_exp
                cmpb $69, %al
                je .Lkof_str_to_dbl_exp
                movq $10, %rax
                imulq %r13, %rax
                movzbl 24(%rbx,%rcx), %edx
                subl $48, %edx
                movslq %edx, %rdx
                addq %rdx, %rax
                movq %rax, %r13
                incl %ecx
                jmp .Lkof_str_to_dbl_int
            .Lkof_str_to_dbl_frac:
                vcvtsi2sd %r13, %xmm0, %xmm0      # xmm0 = parte inteira
                incl %ecx                          # pula o '.'
                movsd .Lkof_dbl_one(%rip), %xmm2   # scale = 1.0
            .Lkof_str_to_dbl_frac_loop:
                cmpl %r12d, %ecx
                jge .Lkof_str_to_dbl_finish
                movzbl 24(%rbx,%rcx), %eax
                cmpb $101, %al
                je .Lkof_str_to_dbl_exp
                cmpb $69, %al
                je .Lkof_str_to_dbl_exp
                cmpb $48, %al
                jb .Lkof_str_to_dbl_finish
                cmpb $57, %al
                ja .Lkof_str_to_dbl_finish
                movsd %xmm2, %xmm3                 # peso atual
                divsd .Lkof_dbl_ten(%rip), %xmm2   # scale /= 10
                movsd %xmm2, %xmm3                 # peso = scale/10 (apos o ponto)
                movzbl 24(%rbx,%rcx), %eax
                subl $48, %eax
                vcvtsi2sd %eax, %xmm4, %xmm4       # digito
                mulsd %xmm3, %xmm4                 # digito * peso
                addsd %xmm4, %xmm0                 # acc += ...
                incl %ecx
                jmp .Lkof_str_to_dbl_frac_loop
            .Lkof_str_to_dbl_exp:
                # expoente: 'e'/'E' [+-] digits -- aplica por multiplicacao
                incl %ecx
                xorl %r15d, %r15d                  # exp neg?
                xorq %r13, %r13                    # exp value
                cmpl %r12d, %ecx
                jge .Lkof_str_to_dbl_finish
                movzbl 24(%rbx,%rcx), %eax
                cmpb $45, %al
                jne .Lkof_str_to_dbl_exp_pos
                movl $1, %r15d
                incl %ecx
                jmp .Lkof_str_to_dbl_exp_digits
            .Lkof_str_to_dbl_exp_pos:
                cmpb $43, %al
                jne .Lkof_str_to_dbl_exp_digits
                incl %ecx
            .Lkof_str_to_dbl_exp_digits:
                cmpl %r12d, %ecx
                jge .Lkof_str_to_dbl_exp_apply
                movzbl 24(%rbx,%rcx), %eax
                cmpb $48, %al
                jb .Lkof_str_to_dbl_exp_apply
                cmpb $57, %al
                ja .Lkof_str_to_dbl_exp_apply
                imulq $10, %r13
                subl $48, %eax
                movslq %eax, %rax
                addq %rax, %r13
                incl %ecx
                jmp .Lkof_str_to_dbl_exp_digits
            .Lkof_str_to_dbl_exp_apply:
                movsd .Lkof_dbl_one(%rip), %xmm1
            .Lkof_str_to_dbl_exp_mul:
                testq %r13, %r13
                jz .Lkof_str_to_dbl_exp_sign
                mulsd .Lkof_dbl_ten(%rip), %xmm1
                decq %r13
                jmp .Lkof_str_to_dbl_exp_mul
            .Lkof_str_to_dbl_exp_sign:
                testl %r15d, %r15d
                jz .Lkof_str_to_dbl_exp_mul2
                divsd %xmm1, %xmm0
                jmp .Lkof_str_to_dbl_finish
            .Lkof_str_to_dbl_exp_mul2:
                mulsd %xmm1, %xmm0
                jmp .Lkof_str_to_dbl_finish
            .Lkof_str_to_dbl_finish:
                testl %r14d, %r14d
                jz .Lkof_str_to_dbl_done
                movsd .Lkof_dbl_neg(%rip), %xmm1
                mulsd %xmm1, %xmm0
            .Lkof_str_to_dbl_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_str_to_dbl_zero:
                xorpd %xmm0, %xmm0
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitStringToFloat(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_float
            .type kof_string_to_float, @function
            kof_string_to_float:
                call kof_string_to_double
                cvtsd2ss %xmm0, %xmm0
                ret
            """);
    }
}

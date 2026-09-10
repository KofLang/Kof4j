package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de conversão String → número (kof_string_to_int/long/double/float)
 * do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeStringParse {

    private RuntimeStringParse() {}

    public static void emitStringToInt(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_string_to_int_msg: .asciz "Invalid number"
            .section .text
            .globl kof_string_to_int
            .type kof_string_to_int, @function
kof_string_to_int:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                # Contrato = Integer.parseLong/Int(s.trim()) do JVM (regra 5):
                # trim (byte<=32), sinal +/-, digito-a-digito, overflow -> EXCECAO String
                # (kof_throw_string; sem try outer = panic com codigo, nunca numero
                # silencioso). Acumulacao NEGATIVA a la JDK: acc<=0 no caso negativo
                # (limit=MIN) e acc>=-MAX no positivo; %r9=1 => negativo (nao nega no fim).
                testq %rdi, %rdi
                jz .Lkof_string_to_int_throw
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorl %r13d, %r13d
                movl %r12d, %r14d
            .Lkof_string_to_int_tl:
                cmpl %r12d, %r13d
                jae .Lkof_string_to_int_throw
                movzbl 24(%rbx,%r13), %eax
                cmpl $32, %eax
                ja .Lkof_string_to_int_th0
                incl %r13d
                jmp .Lkof_string_to_int_tl
            .Lkof_string_to_int_th0:
                cmpl %r13d, %r14d
                jbe .Lkof_string_to_int_throw
                movl %r14d, %eax
                decl %eax
                movzbl 24(%rbx,%rax), %eax
                cmpl $32, %eax
                ja .Lkof_string_to_int_sign
                decl %r14d
                jmp .Lkof_string_to_int_th0
            .Lkof_string_to_int_sign:
                xorl %r9d, %r9d
                movabsq $-2147483647, %r15
                movzbl 24(%rbx,%r13), %eax
                cmpl $45, %eax
                jne .Lkof_string_to_int_plus
                incl %r13d
                movabsq $-2147483648, %r15
                movl $1, %r9d
                jmp .Lkof_string_to_int_body
            .Lkof_string_to_int_plus:
                cmpl $43, %eax
                je .Lkof_string_to_int_p1
                cmpl $48, %eax
                jb .Lkof_string_to_int_throw
                jmp .Lkof_string_to_int_body
            .Lkof_string_to_int_p1:
                incl %r13d
            .Lkof_string_to_int_body:
                cmpl %r14d, %r13d
                jae .Lkof_string_to_int_throw
                movabsq $-214748364, %rsi
                xorq %rax, %rax
            .Lkof_string_to_int_loop:
                cmpl %r14d, %r13d
                jae .Lkof_string_to_int_fin
                movzbl 24(%rbx,%r13), %ecx
                subl $48, %ecx
                cmpl $9, %ecx
                ja .Lkof_string_to_int_throw
                cmpq %rsi, %rax
                jl .Lkof_string_to_int_throw
                imulq $10, %rax
                movq %r15, %r8
                addq %rcx, %r8
                cmpq %r8, %rax
                jl .Lkof_string_to_int_throw
                subq %rcx, %rax
                incl %r13d
                jmp .Lkof_string_to_int_loop
            .Lkof_string_to_int_fin:
                testl %r9d, %r9d
                jnz .Lkof_string_to_int_done
                negq %rax
            .Lkof_string_to_int_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_string_to_int_throw:
                leaq .Lkof_string_to_int_msg(%rip), %rdi
                movl $14, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            """);
    }

    public static void emitStringToLong(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_string_to_long_msg: .asciz "Invalid number"
            .section .text
            .globl kof_string_to_long
            .type kof_string_to_long, @function
kof_string_to_long:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                # Contrato = Long.parseLong/Int(s.trim()) do JVM (regra 5):
                # trim (byte<=32), sinal +/-, digito-a-digito, overflow -> EXCECAO String
                # (kof_throw_string; sem try outer = panic com codigo, nunca numero
                # silencioso). Acumulacao NEGATIVA a la JDK: acc<=0 no caso negativo
                # (limit=MIN) e acc>=-MAX no positivo; %r9=1 => negativo (nao nega no fim).
                testq %rdi, %rdi
                jz .Lkof_string_to_long_throw
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorl %r13d, %r13d
                movl %r12d, %r14d
            .Lkof_string_to_long_tl:
                cmpl %r12d, %r13d
                jae .Lkof_string_to_long_throw
                movzbl 24(%rbx,%r13), %eax
                cmpl $32, %eax
                ja .Lkof_string_to_long_th0
                incl %r13d
                jmp .Lkof_string_to_long_tl
            .Lkof_string_to_long_th0:
                cmpl %r13d, %r14d
                jbe .Lkof_string_to_long_throw
                movl %r14d, %eax
                decl %eax
                movzbl 24(%rbx,%rax), %eax
                cmpl $32, %eax
                ja .Lkof_string_to_long_sign
                decl %r14d
                jmp .Lkof_string_to_long_th0
            .Lkof_string_to_long_sign:
                xorl %r9d, %r9d
                movabsq $-9223372036854775807, %r15
                movzbl 24(%rbx,%r13), %eax
                cmpl $45, %eax
                jne .Lkof_string_to_long_plus
                incl %r13d
                movabsq $-9223372036854775808, %r15
                movl $1, %r9d
                jmp .Lkof_string_to_long_body
            .Lkof_string_to_long_plus:
                cmpl $43, %eax
                je .Lkof_string_to_long_p1
                cmpl $48, %eax
                jb .Lkof_string_to_long_throw
                jmp .Lkof_string_to_long_body
            .Lkof_string_to_long_p1:
                incl %r13d
            .Lkof_string_to_long_body:
                cmpl %r14d, %r13d
                jae .Lkof_string_to_long_throw
                movabsq $-922337203685477580, %rsi
                xorq %rax, %rax
            .Lkof_string_to_long_loop:
                cmpl %r14d, %r13d
                jae .Lkof_string_to_long_fin
                movzbl 24(%rbx,%r13), %ecx
                subl $48, %ecx
                cmpl $9, %ecx
                ja .Lkof_string_to_long_throw
                cmpq %rsi, %rax
                jl .Lkof_string_to_long_throw
                imulq $10, %rax
                movq %r15, %r8
                addq %rcx, %r8
                cmpq %r8, %rax
                jl .Lkof_string_to_long_throw
                subq %rcx, %rax
                incl %r13d
                jmp .Lkof_string_to_long_loop
            .Lkof_string_to_long_fin:
                testl %r9d, %r9d
                jnz .Lkof_string_to_long_done
                negq %rax
            .Lkof_string_to_long_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_string_to_long_throw:
                leaq .Lkof_string_to_long_msg(%rip), %rdi
                movl $14, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            """);
    }

    public static void emitStringToDouble(StringBuilder sb) {
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

    public static void emitStringToFloat(StringBuilder sb) {
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

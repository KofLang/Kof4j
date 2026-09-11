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
}

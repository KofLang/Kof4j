package dev.kof.compiler;

/**
Emissão do ASM de impressão numérica (kof_print_int/float/double) do
 * runtime nativo. Domínio isolado do NativeRuntime — refactor preserva semântica.
 */
final class RuntimePrintNum {

    private RuntimePrintNum() {}

    static void emitPrintInt(StringBuilder sb) {
        sb.append("""
            .globl kof_print_int
            .type kof_print_int, @function
            kof_print_int:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %eax
                movq $0, %r12
                testl %eax, %eax
                jns .Lkof_print_int_pos
                movq $1, %r12
                negl %eax
            .Lkof_print_int_pos:
                movl %eax, %r13d
                movq $0, %rbx
                movl $10, %ecx
            .Lkof_print_int_count:
                xorl %edx, %edx
                divl %ecx
                incq %rbx
                testl %eax, %eax
                jnz .Lkof_print_int_count
                testq %r12, %r12
                jz .Lkof_print_int_count_done
                incq %rbx
            .Lkof_print_int_count_done:
                leaq -48(%rsp), %rsi
                addq %rbx, %rsi
                movl %r13d, %eax
                movq $0, %r13
                movl $10, %ecx
            .Lkof_print_int_loop:
                xorl %edx, %edx
                divl %ecx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                incq %r13
                testl %eax, %eax
                jnz .Lkof_print_int_loop
                testq %r12, %r12
                jz .Lkof_print_int_negdone
                movb $45, (%rsi)
                incq %r13
            .Lkof_print_int_negdone:
                testq %r12, %r12
                jnz .Lkof_print_int_ready
                incq %rsi
            .Lkof_print_int_ready:
                movq %r13, %rdx
                movq $1, %rax
                movq $1, %rdi
                syscall
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitPrintFloat(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lfmt_float: .asciz "%g"
            .Lfmt_double: .asciz "%g"
            .section .text
            .globl kof_print_float
            .type kof_print_float, @function
            kof_print_float:
                pushq %rbp
                movq %rsp, %rbp
                subq $32, %rsp
                cvtss2sd %xmm0, %xmm0
                leaq .Lfmt_float(%rip), %rdi
                movl $1, %eax
                call printf
                leave
                ret
            .globl kof_print_double
            .type kof_print_double, @function
            kof_print_double:
                pushq %rbp
                movq %rsp, %rbp
                subq $32, %rsp
                leaq .Lfmt_double(%rip), %rdi
                movl $1, %eax
                call printf
                leave
                ret
            """);
    }

    static void emitPrintDouble(StringBuilder sb) {
        // emitted together with emitPrintFloat (keep for symmetry)
    }

}
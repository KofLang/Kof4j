package dev.kof.compiler;

/**
 * Emissão do ASM de tempo (kof_time e kof_io_time) do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeTime {

    private RuntimeTime() {}

    static void emitIoTimeFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lstr_read_err: .asciz "Runtime error: cannot read"
            .section .text
            .globl kof_now
            .type kof_now, @function
            kof_now:
                subq $16, %rsp
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $96, %rax
                syscall
                movq 8(%rsp), %rax
                xorq %rdx, %rdx
                movq $1000, %r8
                divq %r8
                movq 0(%rsp), %rcx
                imulq $1000, %rcx
                addq %rcx, %rax
                addq $16, %rsp
                ret

            .globl kof_read_line
            .type kof_read_line, @function
            kof_read_line:
                pushq %rbx
                pushq %r12
                subq $512, %rsp
                movq %rsp, %rbx
                xorq %r12, %r12
            .Lkof_read_line_loop:
                cmpq $511, %r12
                jge .Lkof_read_line_done
                movq $0, %rax
                movq $0, %rdi
                movq $1, %rsi
                movq %rbx, %rdx
                addq %r12, %rdx
                syscall
                testq %rax, %rax
                jle .Lkof_read_line_eof
                movq %rbx, %rcx
                addq %r12, %rcx
                cmpb $10, (%rcx)
                je .Lkof_read_line_done
                incq %r12
                jmp .Lkof_read_line_loop
            .Lkof_read_line_eof:
                # EOF sem nenhum byte lido -> null (paridade com o JVM,
                # que devolve null no fim do stdin); linha parcial -> devolve
                cmpq $0, %r12
                jne .Lkof_read_line_done
                xorl %eax, %eax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret
            .Lkof_read_line_done:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                movq %rcx, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movq %rcx, %r13
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                addq $512, %rsp
                popq %r12
                popq %rbx
                ret

            .globl kof_read_file
            .type kof_read_file, @function
            kof_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $0, %rdx
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_read_file_err
                movq %rax, %r12
                subq $144, %rsp
                movq %r12, %rdi
                movq %rsp, %rsi
                movq $5, %rax
                syscall
                movq 48(%rsp), %r13
                addq $144, %rsp
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r12, %rdi
                movq %r14, %rsi
                addq $24, %rsi
                movl %r13d, %edx
                movq $0, %rax
                syscall
                movq %r12, %rdi
                movq $3, %rax
                syscall
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_read_file_err:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_write_file
            .type kof_write_file, @function
            kof_write_file:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $-100, %rdi
                leaq 24(%rbx), %rsi
                movq $577, %rdx
                movq $420, %r10
                movq $257, %rax
                syscall
                testq %rax, %rax
                js .Lkof_write_file_fail
                movq %rax, %rdi
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                movq $1, %rax
                syscall
                movq %rdi, %rdi
                movq $3, %rax
                syscall
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_write_file_fail:
                movq $-1, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitKofTimeFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_time_now
            .type kof_time_now, @function
            kof_time_now:
                jmp kof_now

            .globl kof_time_sleep
            .type kof_time_sleep, @function
            kof_time_sleep:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %ebx, %eax
                xorl %edx, %edx
                movl $1000, %ecx
                divl %ecx
                movl %eax, %r12d
                movl %edx, %r13d
                imull $1000000, %r13d
                subq $16, %rsp
                movq %r12, (%rsp)
                movq %r13, 8(%rsp)
                movq %rsp, %rdi
                xorq %rsi, %rsi
                movq $35, %rax
                syscall
                addq $16, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

}
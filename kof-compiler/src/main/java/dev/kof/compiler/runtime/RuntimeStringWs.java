package dev.kof.compiler.runtime;

/**
 * STDLIB S3.2 (x86_64) — kof.strings removeWhitespace/normalizeWhitespace.
 * WS = {9..13,32}; >=128 cópia (paridade capitalize). Buffer len+25 (saída
 * <= len). null/vazia => original (paridade JVM). Mesma máquina JVM/JS/riscv.
 */
public final class RuntimeStringWs {

    private RuntimeStringWs() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_strings_removeWhitespace(rdi=v) -> String
            .globl kof_strings_removeWhitespace
            .type kof_strings_removeWhitespace, @function
            kof_strings_removeWhitespace:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_rw_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_rw_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                xorl %r14d, %r14d        # i
                xorl %ecx, %ecx          # pos
            .Lv_rw_loop:
                cmpl %r12d, %r14d
                jge .Lv_rw_term
                movzbl 24(%rbx,%r14), %eax
                incl %r14d
                cmpl $32, %eax
                je .Lv_rw_loop
                cmpl $9, %eax
                jl .Lv_rw_put
                cmpl $13, %eax
                jbe .Lv_rw_loop          # 9..13 => WS
            .Lv_rw_put:
                movb %al, 24(%r13,%rcx)
                incl %ecx
                jmp .Lv_rw_loop
            .Lv_rw_term:
                movl %ecx, 16(%r13)
                movb $0, 24(%r13,%rcx)
                movq %r13, %rax
                jmp .Lv_rw_done
            .Lv_rw_orig:
                movq %rbx, %rax
            .Lv_rw_done:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_strings_normalizeWhitespace(rdi=v) -> String
            .globl kof_strings_normalizeWhitespace
            .type kof_strings_normalizeWhitespace, @function
            kof_strings_normalizeWhitespace:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_nw_orig
                movl 16(%rbx), %r12d
                testl %r12d, %r12d
                jle .Lv_nw_orig
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                xorl %r14d, %r14d        # i
                xorl %r15d, %r15d        # pos
                xorl %ecx, %ecx          # inws
                xorl %edx, %edx          # started
            .Lv_nw_loop:
                cmpl %r12d, %r14d
                jge .Lv_nw_term
                movzbl 24(%rbx,%r14), %eax
                incl %r14d
                # WS?
                cmpl $32, %eax
                je .Lv_nw_ws
                cmpl $9, %eax
                jl .Lv_nw_nws
                cmpl $13, %eax
                jbe .Lv_nw_ws
                jmp .Lv_nw_nws
            .Lv_nw_ws:
                testl %edx, %edx
                jz .Lv_nw_loop
                movl $1, %ecx
                jmp .Lv_nw_loop
            .Lv_nw_nws:
                testl %ecx, %ecx
                jz .Lv_nw_emit
                movb $32, 24(%r13,%r15)
                incl %r15d
                xorl %ecx, %ecx
            .Lv_nw_emit:
                movb %al, 24(%r13,%r15)
                incl %r15d
                movl $1, %edx
                jmp .Lv_nw_loop
            .Lv_nw_term:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lv_nw_done
            .Lv_nw_orig:
                movq %rbx, %rax
            .Lv_nw_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

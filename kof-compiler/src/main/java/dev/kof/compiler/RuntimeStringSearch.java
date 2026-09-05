package dev.kof.compiler;

/**
 * Emissão do ASM de busca em String (kof_string_contains/startsWith/endsWith/
 * indexOf/lastIndexOf) do runtime nativo. Domínio isolado do NativeRuntime --
 * refactor preserva semântica.
 */
final class RuntimeStringSearch {

    private RuntimeStringSearch() {}

    static void emitStringContains(StringBuilder sb) {
        sb.append("""
            .globl kof_string_contains
            .type kof_string_contains, @function
            kof_string_contains:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                testl %r13d, %r13d
                jz .Lkof_strcontains_found
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strcontains_no
                xorq %rcx, %rcx
            .Lkof_strcontains_outer:
                cmpl %r14d, %ecx
                jge .Lkof_strcontains_no
                leaq 24(%rbx,%rcx), %rax
                xorq %rdx, %rdx
            .Lkof_strcontains_inner:
                cmpl %r13d, %edx
                jge .Lkof_strcontains_found
                movzbl (%rax,%rdx), %r8d
                movzbl 24(%r12,%rdx), %r9d
                cmpl %r9d, %r8d
                jne .Lkof_strcontains_next
                incq %rdx
                jmp .Lkof_strcontains_inner
            .Lkof_strcontains_next:
                incq %rcx
                jmp .Lkof_strcontains_outer
            .Lkof_strcontains_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strcontains_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    static void emitStringStartsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_starts_with
            .type kof_string_starts_with, @function
            kof_string_starts_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jg .Lkof_strstarts_no
                xorq %rcx, %rcx
            .Lkof_strstarts_loop:
                cmpl %r13d, %ecx
                jge .Lkof_strstarts_found
                movzbl 24(%rbx,%rcx), %eax
                cmpb %al, 24(%r12,%rcx)
                jne .Lkof_strstarts_no
                incq %rcx
                jmp .Lkof_strstarts_loop
            .Lkof_strstarts_found:
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strstarts_no:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    static void emitStringEndsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_ends_with
            .type kof_string_ends_with, @function
            kof_string_ends_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strends_no
                movl %r14d, %ecx
                subl %r13d, %ecx
            .Lkof_strends_loop:
                cmpl %r14d, %ecx
                jge .Lkof_strends_found
                movzbl 24(%rbx,%rcx), %eax
                movl %ecx, %edx
                addl %r13d, %edx
                subl %r14d, %edx
                movzbl 24(%r12,%rdx), %edx
                cmpl %edx, %eax
                jne .Lkof_strends_no
                incq %rcx
                jmp .Lkof_strends_loop
            .Lkof_strends_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strends_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    static void emitStringIndexOf(StringBuilder sb) {
        sb.append("""
            .globl kof_string_index_of
            .type kof_string_index_of, @function
            kof_string_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                testl %r14d, %r14d
                jz .Lkof_idx_found0
                cmpl %r13d, %r14d
                jg .Lkof_idx_notfound
                xorl %r15d, %r15d
            .Lkof_idx_outer:
                movl %r13d, %eax
                subl %r14d, %eax
                cmpl %eax, %r15d
                jg .Lkof_idx_notfound
                xorl %ecx, %ecx
            .Lkof_idx_inner:
                cmpl %r14d, %ecx
                jge .Lkof_idx_found
                movl %r15d, %eax
                addl %ecx, %eax
                movzbl 24(%rbx,%rax), %eax
                movzbl 24(%r12,%rcx), %edx
                cmpl %edx, %eax
                jne .Lkof_idx_next
                incq %rcx
                jmp .Lkof_idx_inner
            .Lkof_idx_next:
                incl %r15d
                jmp .Lkof_idx_outer
            .Lkof_idx_found0:
                xorl %r15d, %r15d
            .Lkof_idx_found:
                movl %r15d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_idx_notfound:
                movl $-1, %eax
                popq %r15
                popq %r14
                popq %r13
                 popq %r12
                 popq %rbx
                 ret
             """);
     }

    /** lastIndexOf: varre do fim para o início; retorna -1 se não achar. */

    static void emitStringLastIndexOf(StringBuilder sb) {
        sb.append("""
            .globl kof_string_last_index_of
            .type kof_string_last_index_of, @function
            kof_string_last_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                testl %r14d, %r14d
                jz .Lkof_lidx_found_end
                cmpl %r13d, %r14d
                jg .Lkof_lidx_notfound
                movl %r13d, %r15d
                subl %r14d, %r15d
            .Lkof_lidx_outer:
                testl %r15d, %r15d
                js .Lkof_lidx_notfound
                xorl %ecx, %ecx
            .Lkof_lidx_inner:
                cmpl %r14d, %ecx
                jge .Lkof_lidx_found
                movl %r15d, %eax
                addl %ecx, %eax
                movzbl 24(%rbx,%rax), %eax
                movzbl 24(%r12,%rcx), %edx
                cmpl %edx, %eax
                jne .Lkof_lidx_next
                incq %rcx
                jmp .Lkof_lidx_inner
            .Lkof_lidx_next:
                decl %r15d
                jmp .Lkof_lidx_outer
            .Lkof_lidx_found:
                movl %r15d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_lidx_found_end:
                movl %r13d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_lidx_notfound:
                movl $-1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


}

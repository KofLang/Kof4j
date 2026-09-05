package dev.kof.compiler;

/**
 * Emissão do ASM do builder JSON (kof_json_builder_new/grow/char/str/result) do runtime
 * nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeJsonBuilder {

    private RuntimeJsonBuilder() {}

    static void emitJsonBuilder(StringBuilder sb) {
        sb.append("""
            .globl kof_json_builder_new
            .type kof_json_builder_new, @function
            kof_json_builder_new:
                pushq %rbx
                movq $32, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $101, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $64, 20(%rbx)
                movq $64, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq %rbx, %rax
                popq %rbx
                ret

            .globl kof_json_builder_grow
            .type kof_json_builder_grow, @function
            kof_json_builder_grow:
                pushq %rbx
                pushq %r13
                movq %rdi, %rbx
                movl 20(%rbx), %eax
                shll $1, %eax
                movl %eax, 20(%rbx)
                movslq %eax, %rdi
                call kof_alloc
                movq %rax, %rcx
                movq 24(%rbx), %rsi
                movl 16(%rbx), %r13d
                movslq %r13d, %r13
                xorq %rdx, %rdx
            .Lkof_json_bgr_copy:
                cmpq %r13, %rdx
                jge .Lkof_json_bgr_done
                movb (%rsi,%rdx), %al
                movb %al, (%rcx,%rdx)
                incq %rdx
                jmp .Lkof_json_bgr_copy
            .Lkof_json_bgr_done:
                movq %rcx, 24(%rbx)
                movq %rbx, %rax
                popq %r13
                popq %rbx
                ret

            .globl kof_json_builder_char
            .type kof_json_builder_char, @function
            kof_json_builder_char:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %esi, %r12d
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkof_json_bch_ok
                movq %rbx, %rdi
                call kof_json_builder_grow
            .Lkof_json_bch_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movb %r12b, (%rdx,%rcx)
                addl $1, 16(%rbx)
                popq %r12
                popq %rbx
                ret

            .globl kof_json_builder_str
            .type kof_json_builder_str, @function
            kof_json_builder_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
            .Lkof_json_bst_grow_loop:
                movl 16(%rbx), %eax
                addl %r13d, %eax
                cmpl 20(%rbx), %eax
                jle .Lkof_json_bst_ok
                movq %rbx, %rdi
                call kof_json_builder_grow
                jmp .Lkof_json_bst_grow_loop
            .Lkof_json_bst_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                leaq 24(%r12), %rsi
                xorq %r8, %r8
            .Lkof_json_bst_copy:
                cmpq %r13, %r8
                jge .Lkof_json_bst_done
                movb (%rsi,%r8), %al
                movb %al, (%rdx,%rcx)
                incq %r8
                incq %rcx
                jmp .Lkof_json_bst_copy
            .Lkof_json_bst_done:
                movl 16(%rbx), %eax
                addl %r13d, %eax
                movl %eax, 16(%rbx)
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_builder_result
            .type kof_json_builder_result, @function
            kof_json_builder_result:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq 24(%rbx), %rsi
                leaq 24(%r13), %rdi
                movslq %r12d, %rdx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

}
package dev.kof.compiler;

/**
 * Emissão do ASM das funções de array (kof_array_alloc/length/get/set) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeArray {

    private RuntimeArray() {}

    static void emitArrayAlloc(StringBuilder sb) {
        sb.append("""
            .globl kof_array_alloc
            .type kof_array_alloc, @function
            kof_array_alloc:
                pushq %rbx
                pushq %r12
                movl %edi, %ebx
                movl %esi, %r12d
                movq %rbx, %rax
                imulq %r12, %rax
                addq $24, %rax
                movq %rax, %rdi
                call kof_alloc
                movq %rax, %rcx
                movl $2, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %ebx, 16(%rcx)
                movl %r12d, 20(%rcx)
                movq %rcx, %rax
                popq %r12
                popq %rbx
                ret
            """);
    }
    static void emitArrayLength(StringBuilder sb) {
        sb.append("""
            .globl kof_array_length
            .type kof_array_length, @function
            kof_array_length:
                movl 16(%rdi), %eax
                ret
            """);
    }
    static void emitArrayGet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_get
            .type kof_array_get, @function
            kof_array_get:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lkof_array_get_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_get_bounds
                cmpl $0, %r12d
                jl .Lkof_array_get_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_get_q
                cmpl $4, %edx
                je .Lkof_array_get_d
                cmpl $2, %edx
                je .Lkof_array_get_w
                movsbq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_w:
                movswq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_d:
                movslq (%rax), %rax
                jmp .Lkof_array_get_done
            .Lkof_array_get_q:
                movq (%rax), %rax
            .Lkof_array_get_done:
                popq %r12
                popq %rbx
                ret
            .Lkof_array_get_null:
                call kof_null_error
            .Lkof_array_get_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }
    static void emitArraySet(StringBuilder sb) {
        sb.append("""
            .globl kof_array_set
            .type kof_array_set, @function
            kof_array_set:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                movq %rdx, %r13
                testq %rbx, %rbx
                jz .Lkof_array_set_null
                movl 16(%rbx), %ecx
                cmpl %ecx, %r12d
                jge .Lkof_array_set_bounds
                cmpl $0, %r12d
                jl .Lkof_array_set_bounds
                movl 20(%rbx), %edx
                movq %r12, %rax
                imulq %rdx, %rax
                addq $24, %rax
                addq %rbx, %rax
                cmpl $8, %edx
                je .Lkof_array_set_q
                cmpl $4, %edx
                je .Lkof_array_set_d
                cmpl $2, %edx
                je .Lkof_array_set_w
                movb %r13b, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_w:
                movw %r13w, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_d:
                movl %r13d, (%rax)
                jmp .Lkof_array_set_done
            .Lkof_array_set_q:
                movq %r13, (%rax)
            .Lkof_array_set_done:
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_array_set_null:
                call kof_null_error
            .Lkof_array_set_bounds:
                movl %r12d, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error
            """);
    }}

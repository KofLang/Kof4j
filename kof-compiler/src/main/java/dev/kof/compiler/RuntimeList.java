package dev.kof.compiler;

/**
 * Emissão do ASM das funções de lista (kof_list_new/grow/add/get/set/size/...
 * map/filter/reduce) do runtime nativo. Domínio isolado do NativeRuntime --
 * refactor preserva semântica.
 */
final class RuntimeList {

    private RuntimeList() {}

    static void emitListFunctions(StringBuilder sb) {
        sb.append("""
            .globl kof_list_new
            .type kof_list_new, @function
            kof_list_new:
                pushq %rbx
                movq $64, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $100, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $2, 20(%rbx)
                movq $16, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq %rbx, %rax
                popq %rbx
                ret

            .globl kof_list_grow
            .type kof_list_grow, @function
            kof_list_grow:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 20(%rbx), %r12d
                movl %r12d, %r13d
                shll $1, %r13d
                movl %r13d, 20(%rbx)
                movslq %r13d, %rdi
                shlq $3, %rdi
                addq $24, %rdi
                call kof_alloc
                movq %rax, %rcx
                movq 24(%rbx), %rsi
                movl 16(%rbx), %r13d
                movslq %r13d, %r13
                xorq %rdx, %rdx
            .Lkof_list_grow_copy:
                cmpq %r13, %rdx
                jge .Lkof_list_grow_done
                movq (%rsi,%rdx,8), %rax
                movq %rax, (%rcx,%rdx,8)
                incq %rdx
                jmp .Lkof_list_grow_copy
            .Lkof_list_grow_done:
                movq %rcx, 24(%rbx)
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_add
            .type kof_list_add, @function
            kof_list_add:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkof_list_add_ok
                movq %rbx, %rdi
                call kof_list_grow
            .Lkof_list_add_ok:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movq %r12, (%rdx,%rcx,8)
                addl $1, 16(%rbx)
                popq %r12
                popq %rbx
                ret

            .globl kof_list_get
            .type kof_list_get, @function
            kof_list_get:
                pushq %rbx
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_get_bounds
                testl %esi, %esi
                jl .Lkof_list_get_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq (%rax,%rcx,8), %rax
                popq %rbx
                ret
            .Lkof_list_get_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_set
            .type kof_list_set, @function
            kof_list_set:
                pushq %rbx
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_set_bounds
                testl %esi, %esi
                jl .Lkof_list_set_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq %rdx, (%rax,%rcx,8)
                popq %rbx
                ret
            .Lkof_list_set_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_size
            .type kof_list_size, @function
            kof_list_size:
                movslq 16(%rdi), %rax
                ret

            .globl kof_list_contains
            .type kof_list_contains, @function
            kof_list_contains:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                movl 16(%rbx), %r14d
                xorl %r15d, %r15d
            .Lkof_list_contains_loop:
                cmpl %r14d, %r15d
                jge .Lkof_list_contains_no
                movq 24(%rbx), %rax
                movq (%rax,%r15,8), %rax
                cmpl $1, %r13d
                je .Lkof_list_contains_str
                cmpq %r12, %rax
                je .Lkof_list_contains_yes
                jmp .Lkof_list_contains_next
            .Lkof_list_contains_str:
                movq %rax, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lkof_list_contains_yes
            .Lkof_list_contains_next:
                incl %r15d
                jmp .Lkof_list_contains_loop
            .Lkof_list_contains_yes:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_list_contains_no:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_contains_tag
            .type kof_list_contains_tag, @function
            kof_list_contains_tag:
                jmp kof_list_contains

            .globl kof_list_is_empty
            .type kof_list_is_empty, @function
            kof_list_is_empty:
                cmpl $0, 16(%rdi)
                sete %al
                movzbl %al, %eax
                ret

            .globl kof_list_remove
            .type kof_list_remove, @function
            kof_list_remove:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %eax
                cmpl %eax, %esi
                jge .Lkof_list_remove_bounds
                testl %esi, %esi
                jl .Lkof_list_remove_bounds
                movslq %esi, %rcx
                movq 24(%rbx), %rax
                movq (%rax,%rcx,8), %r12
            .Lkof_list_remove_shift:
                movl 16(%rbx), %eax
                decl %eax
                cmpl %eax, %ecx
                jge .Lkof_list_remove_done
                movq 24(%rbx), %rax
                movq 8(%rax,%rcx,8), %rdx
                movq 24(%rbx), %rax
                movq %rdx, (%rax,%rcx,8)
                incq %rcx
                jmp .Lkof_list_remove_shift
            .Lkof_list_remove_done:
                movl 16(%rbx), %eax
                decl %eax
                movl %eax, 16(%rbx)
                movq %r12, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_list_remove_bounds:
                movl %esi, %edi
                movl 16(%rbx), %esi
                call kof_bounds_error

            .globl kof_list_clear
            .type kof_list_clear, @function
            kof_list_clear:
                movl $0, 16(%rdi)
                ret

            .globl kof_list_map
            .type kof_list_map, @function
            kof_list_map:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                call kof_list_new
                movq %rax, %r14
                xorl %r15d, %r15d
            .Lkof_list_map_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_map_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                movq %rax, %rsi
                movq %r14, %rdi
                call kof_list_add
                incl %r15d
                jmp .Lkof_list_map_loop
            .Lkof_list_map_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_filter
            .type kof_list_filter, @function
            kof_list_filter:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                call kof_list_new
                movq %rax, %r14
                xorl %r15d, %r15d
            .Lkof_list_filter_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_filter_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jz .Lkof_list_filter_skip
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r14, %rdi
                call kof_list_add
            .Lkof_list_filter_skip:
                incl %r15d
                jmp .Lkof_list_filter_loop
            .Lkof_list_filter_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_list_reduce
            .type kof_list_reduce, @function
            kof_list_reduce:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                xorl %r15d, %r15d
            .Lkof_list_reduce_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_reduce_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rdx
                movq %r13, %rsi
                movq %r14, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                movq %rax, %r13
                incl %r15d
                jmp .Lkof_list_reduce_loop
            .Lkof_list_reduce_done:
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }}

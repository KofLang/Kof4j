package dev.kof.compiler.runtime;

/**
 * x86_64 list sort routines: comparator-ordered insertion sort, extracted
 * from {@link RuntimeListQuantifiers} (#685) so both classes stay under the
 * 500-line gate. {@code kof_list_sort_cmp} sorts in place;
 * {@code kof_list_sorted_cmp} returns a sorted copy; both invoke the
 * comparator lambda through the vtable slot and are stable for pure
 * comparators.
 */
final class RuntimeListSort {

    private RuntimeListSort() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            # kof_list_sort_cmp(rdi=list, rsi=cmp) — in-place insertion sort
            # ordered by the comparator (negative/zero/positive Int); stable
            # for pure comparators; the receiver IS mutated (#685 enum sort).
            # Lambda call mirrors kof_list_sorted_cmp (rdi=lambda, rsi=a,
            # rdx=b through the vtable slot).
            .globl kof_list_sort_cmp
            .type kof_list_sort_cmp, @function
            kof_list_sort_cmp:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %rbp             # list
                movq %rsi, %r12             # cmp lambda
                movl 16(%rbp), %r15d        # n
                movl $1, %r13d              # i = 1
            .Llsoc_outer:
                cmpl %r15d, %r13d
                jge .Llsoc_done
                movq %rbp, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %r14             # key
                movl %r13d, %eax
                decl %eax
                movl %eax, 0(%rsp)          # j = i - 1
            .Llsoc_inner:
                movl 0(%rsp), %ecx
                cmpl $0, %ecx
                jl .Llsoc_insert
                movq %rbp, %rdi
                movslq %ecx, %rsi
                call kof_list_get           # rax = a = get(j)
                movq %r14, %rsi             # arg0 = key
                movq %rax, %rdx             # arg1 = a
                movq %r12, %rdi             # cmp lambda
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax                  # eax = cmp(a, key)
                testl %eax, %eax
                jge .Llsoc_insert           # a >= key: stop (stable)
                movq %rbp, %rdi
                movl 0(%rsp), %esi
                movslq %esi, %rsi
                call kof_list_get           # rax = v = get(j)
                movq %rax, %rdx
                movq %rbp, %rdi
                movl 0(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                call kof_list_set           # set(j+1, v)
                decl 0(%rsp)                # j--
                jmp .Llsoc_inner
            .Llsoc_insert:
                movq %rbp, %rdi
                movl 0(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                movq %r14, %rdx
                call kof_list_set           # set(j+1, key)
                incl %r13d
                jmp .Llsoc_outer
            .Llsoc_done:
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            # kof_list_sorted_cmp(rdi=list, rsi=cmp) -> rax new List ordered
            # by the comparator (negative/zero/positive Int); insertion sort
            # (stable for pure comparators); the receiver is never mutated.
            # Lambda call mirrors kof_list_reduce (rdi=lambda, rsi=a,
            # rdx=b through the vtable slot); raw slots like map/filter.
            # Slice 1g (D-MULTIPARADIGMA-SORTED).
            .globl kof_list_sorted_cmp
            .type kof_list_sorted_cmp, @function
            kof_list_sorted_cmp:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16, %rsp
                # out lives at 0(%rsp) rather than %rbx: a live GC list
                # pointer must survive the comparator call, and the SysV ABI
                # makes no callee-preservation promise the runtime can rely
                # on across an arbitrary lambda body (#685 hardened the
                # generated dispatch to use %r11 too).
                movq %rdi, %rbp             # src (rbp livre: só salvo)
                movq %rsi, %r12             # cmp lambda
                movl 16(%rbp), %r15d        # n
                call kof_list_new
                movq %rax, 0(%rsp)          # out
                xorl %r13d, %r13d           # i = 0 (copy)
            .Llsc_copy:
                cmpl %r15d, %r13d
                jge .Llsc_isort
                movq %rbp, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %rsi
                movq 0(%rsp), %rdi
                call kof_list_add
                incl %r13d
                jmp .Llsc_copy
            .Llsc_isort:
                movl $1, %r13d              # i = 1
            .Llsc_outer:
                cmpl %r15d, %r13d
                jge .Llsc_done
                movq 0(%rsp), %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %r14             # key
                movl %r13d, %eax
                decl %eax
                movl %eax, 8(%rsp)          # j = i - 1
            .Llsc_inner:
                movl 8(%rsp), %ecx
                cmpl $0, %ecx
                jl .Llsc_insert
                movq 0(%rsp), %rdi
                movslq %ecx, %rsi
                call kof_list_get           # rax = a = get(j)
                movq %r14, %rsi             # PROBE: key como arg0
                movq %rax, %rdx             # PROBE: a como arg1
                movq %r12, %rdi             # cmp lambda
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax                  # eax = cmp(a, key)
                testl %eax, %eax
                jge .Llsc_insert             # a >= key: stop (stable)
                movq 0(%rsp), %rdi
                movl 8(%rsp), %esi
                movslq %esi, %rsi
                call kof_list_get           # rax = v = get(j)
                movq %rax, %rdx
                movq 0(%rsp), %rdi
                movl 8(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                call kof_list_set           # set(j+1, v)
                decl 8(%rsp)                # j--
                jmp .Llsc_inner
            .Llsc_insert:
                movq 0(%rsp), %rdi
                movl 8(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                movq %r14, %rdx
                call kof_list_set           # set(j+1, key)
                incl %r13d
                jmp .Llsc_outer
            .Llsc_done:
                movq 0(%rsp), %rax
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            """);
    }
}

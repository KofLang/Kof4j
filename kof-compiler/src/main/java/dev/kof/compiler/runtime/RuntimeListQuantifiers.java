package dev.kof.compiler.runtime;

/**
 * D-MULTIPARADIGMA-PHASE1A (slices 1a+1b) — eager short-circuit search ops
 * {@code any}/{@code all}/{@code none} + {@code find}/{@code count(pred)} on
 * x86_64. Loop shape mirrors {@code kof_list_filter} (element reload per
 * iteration, lambda invoke via the vtable slot); truth test is the same
 * {@code testq} (nonzero = true); Bool/Int results are raw in {@code eax}
 * (like {@code kof_list_is_empty}); missing {@code find} returns 0 (the
 * Map.get-missing contract). Frame keeps the 5-push shape of the map/filter
 * loops (alignment).
 */
public final class RuntimeListQuantifiers {

    private RuntimeListQuantifiers() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # kof_list_any(list, fn) -> eax 1/0
            .globl kof_list_any
            .type kof_list_any, @function
            kof_list_any:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_any_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_any_false
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_any_true
                incl %r15d
                jmp .Lkof_list_any_loop
            .Lkof_list_any_true:
                movl $1, %eax
                jmp .Lkof_list_any_done
            .Lkof_list_any_false:
                xorl %eax, %eax
            .Lkof_list_any_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_all(list, fn) -> eax 1/0
            .globl kof_list_all
            .type kof_list_all, @function
            kof_list_all:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_all_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_all_true
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jz .Lkof_list_all_false
                incl %r15d
                jmp .Lkof_list_all_loop
            .Lkof_list_all_false:
                xorl %eax, %eax
                jmp .Lkof_list_all_done
            .Lkof_list_all_true:
                movl $1, %eax
            .Lkof_list_all_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_none(list, fn) -> eax 1/0
            .globl kof_list_none
            .type kof_list_none, @function
            kof_list_none:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_none_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_none_true
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_none_false
                incl %r15d
                jmp .Lkof_list_none_loop
            .Lkof_list_none_false:
                xorl %eax, %eax
                jmp .Lkof_list_none_done
            .Lkof_list_none_true:
                movl $1, %eax
            .Lkof_list_none_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_find(list, fn, tag) -> rax element-boxed | 0.
            # Native list slots hold RAW primitives but T? consumers expect
            # boxed values (Map slots are boxed); the tag (NativeBoxTags
            # numbering) selects the box on the hit path, miss stays 0.
            .globl kof_list_find
            .type kof_list_find, @function
            kof_list_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                movl %edx, %r14d
                xorl %r15d, %r15d
            .Lkof_list_find_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_find_miss
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_find_hit
                incl %r15d
                jmp .Lkof_list_find_loop
            .Lkof_list_find_hit:
                cmpl $1, %r14d
                je .Lkof_list_find_pass
                cmpl $6, %r14d
                je .Lkof_list_find_pass
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rdi
                cmpl $2, %r14d
                je .Lkof_list_find_long
                cmpl $3, %r14d
                je .Lkof_list_find_bool
                cmpl $4, %r14d
                je .Lkof_list_find_double
                cmpl $5, %r14d
                je .Lkof_list_find_float
                call kof_box_int
                jmp .Lkof_list_find_done
            .Lkof_list_find_long:
                call kof_box_long
                jmp .Lkof_list_find_done
            .Lkof_list_find_bool:
                call kof_box_bool
                jmp .Lkof_list_find_done
            .Lkof_list_find_double:
                call kof_box_double
                jmp .Lkof_list_find_done
            .Lkof_list_find_float:
                call kof_box_float
                jmp .Lkof_list_find_done
            .Lkof_list_find_pass:
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rax
                jmp .Lkof_list_find_done
            .Lkof_list_find_miss:
                xorl %eax, %eax
            .Lkof_list_find_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        RuntimeListSort.emit(sb);
        sb.append("""
            # kof_list_groupby(rdi=list, rsi=fn, edx=tag) -> rax new Map
            # (groups in encounter order). Buckets are fresh lists; keys use
            # the map machinery (kof_map_new/find/put) with the tag written
            # to the map header (slot 40) like the x86 emitter does for
            # kof_map_* calls (tag <0 keeps the historic default).
            # Lambda call mirrors kof_list_any (raw slots). Slice 1h.
            .globl kof_list_groupby
            .type kof_list_groupby, @function
            kof_list_groupby:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %r12             # src
                movq %rsi, %r13             # lambda
                movl %edx, 0(%rsp)          # tag spill
                call kof_map_new
                movq %rax, %rbx             # map
                movl 0(%rsp), %eax
                cmpl $0, %eax
                jl .Llgb_loop_init
                movl %eax, 40(%rbx)         # key tag
            .Llgb_loop_init:
                xorl %r14d, %r14d           # i = 0
            .Llgb_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r14d
                jge .Llgb_done
                movq 24(%r12), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %rsi    # elem
                movq %r13, %rdi             # lambda
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax                  # rax = key
                movq %rax, %r15             # key
                movq %rbx, %rdi             # map
                movq %r15, %rsi             # key
                call kof_map_find           # eax = idx | -1
                cmpl $-1, %eax
                je .Llgb_miss
                movslq %eax, %rcx
                movq 32(%rbx), %rdx
                movq (%rdx,%rcx,8), %rdi    # existing bucket list
                jmp .Llgb_add
            .Llgb_miss:
                call kof_list_new
                movq %rax, %rbp             # new bucket
                movq %rbx, %rdi             # map
                movq %r15, %rsi             # key
                movq %rbp, %rdx             # bucket
                call kof_map_put
                movq %rbp, %rdi             # bucket list
            .Llgb_add:
                movq 24(%r12), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %rsi    # elem
                call kof_list_add
                incl %r14d
                jmp .Llgb_loop
            .Llgb_done:
                movq %rbx, %rax
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            # kof_list_count_pred(list, fn) -> eax count
            .globl kof_list_count_pred
            .type kof_list_count_pred, @function
            kof_list_count_pred:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lkof_list_count_pred_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_count_pred_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jz .Lkof_list_count_pred_next
                incl %r14d
            .Lkof_list_count_pred_next:
                incl %r15d
                jmp .Lkof_list_count_pred_loop
            .Lkof_list_count_pred_done:
                movl %r14d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_foreach(list, fn) -> void (effect only)
            .globl kof_list_foreach
            .type kof_list_foreach, @function
            kof_list_foreach:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_foreach_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_foreach_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                incl %r15d
                jmp .Lkof_list_foreach_loop
            .Lkof_list_foreach_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_flatmap(list, fn) -> rax new List (concat of fn(elem))
            .globl kof_list_flatmap
            .type kof_list_flatmap, @function
            kof_list_flatmap:
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
            .Lkof_list_flatmap_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_flatmap_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add_all
                incl %r15d
                jmp .Lkof_list_flatmap_loop
            .Lkof_list_flatmap_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            # kof_list_distinct(list, tag) -> rax new List (first occurrences).
            # Equality reuses kof_list_contains (same tag taxonomy: 0=raw,
            # 1=String content, 2=kof_obj_equals) — no divergent comparison.
            .globl kof_list_distinct
            .type kof_list_distinct, @function
            kof_list_distinct:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movl %esi, %r13d
                call kof_list_new
                movq %rax, %r14
                xorl %r15d, %r15d
            .Lkof_list_distinct_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_distinct_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r14, %rdi
                movl %r13d, %edx
                call kof_list_contains
                testl %eax, %eax
                jnz .Lkof_list_distinct_next
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r14, %rdi
                call kof_list_add
            .Lkof_list_distinct_next:
                incl %r15d
                jmp .Lkof_list_distinct_loop
            .Lkof_list_distinct_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}

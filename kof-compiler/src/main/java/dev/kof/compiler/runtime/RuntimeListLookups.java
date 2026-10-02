package dev.kof.compiler.runtime;

/**
 * #382 (fatia 2) — família de busca/corte/ordem da List nativa x86_64:
 * indexOf/lastIndexOf (scan com tag 0=cmpq raw / 1=String, igual
 * kof_list_contains §126), addAll (cópia via kof_list_add; true = mudou),
 * subList (bounds honesto via kof_bounds_error + cópia em lista nova —
 * nunca view viva), sort (seleção com kof_list_cmp: tag 0=signed qword —
 * Int/Long/Bool/Char; 1=kof_string_compare_to; 2=Double e 3=Float (§352/
 * NAT001 fechado 21/09: o slot guarda os 32 bits crus e o runtime alarga com
 * cvtss2sd) com a semântica do Double.compare — NaN maior que tudo e
 * NaN==NaN, -0.0 < 0.0, medida no oráculo JDK 19/09). Slots da List são CRUS
 * (§253/§284) — nada aqui derefença bit cru.
 */
public final class RuntimeListLookups {

    private RuntimeListLookups() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # kof_list_cmp(rdi=a, rsi=b, edx=tag) -> eax negativo/0/positivo
            .globl kof_list_cmp
            .type kof_list_cmp, @function
            kof_list_cmp:
                cmpl $1, %edx
                je .LLC_str
                cmpl $2, %edx
                je .LLC_dbl
                cmpl $3, %edx
                je .LLC_flt
                jmp .LLC_int
            .LLC_dbl:
                movq %rdi, %xmm0
                movq %rsi, %xmm1
                jmp .LLC_fp
            .LLC_flt:
                movd %edi, %xmm0
                movd %esi, %xmm1
                cvtss2sd %xmm0, %xmm0       # Float.compare == Double.compare
                cvtss2sd %xmm1, %xmm1       # após o alargamento (incl. ±0.0/NaN)
            .LLC_fp:
                ucomisd %xmm1, %xmm0
                jp .LLC_unord
                jb .LLC_lt
                ja .LLC_gt
                movq %xmm0, %rax
                movq %xmm1, %rcx
                xorq %rcx, %rax
                jz .LLC_eq
                movq %xmm0, %rax
                testq %rax, %rax            # sinal de a (só ±0.0 chega aqui)
                js .LLC_lt                  # -0.0 < 0.0
                jmp .LLC_gt
            .LLC_unord:
                ucomisd %xmm0, %xmm0
                jp .LLC_aNaN
                ucomisd %xmm1, %xmm1
                jp .LLC_lt                  # b NaN -> a < b
                jmp .LLC_eq                 # ambos NaN -> 0 (Double.compare)
            .LLC_aNaN:
                ucomisd %xmm1, %xmm1
                jp .LLC_eq
                jmp .LLC_gt                 # a NaN > não-NaN
            .LLC_str:
                jmp kof_string_compare_to
            .LLC_int:
                cmpq %rsi, %rdi
                jl .LLC_lt
                jg .LLC_gt
            .LLC_eq:
                xorl %eax, %eax
                ret
            .LLC_lt:
                movl $-1, %eax
                ret
            .LLC_gt:
                movl $1, %eax
                ret

            # kof_list_sort(rdi=list, esi=tag) — seleção in-place (#382)
            .globl kof_list_sort
            .type kof_list_sort, @function
            kof_list_sort:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, %rbx             # list
                movl %esi, %r12d            # tag
                movl 16(%rbx), %r15d        # n
                xorl %r13d, %r13d           # i
            .Lls_i:
                cmpl %r15d, %r13d
                jge .Lls_done
                movl %r13d, %r14d           # min = i
                leal 1(%r13), %eax
                movl %eax, 8(%rsp)          # j
            .Lls_j:
                movl 8(%rsp), %eax
                cmpl %r15d, %eax
                jge .Lls_swap
                movq %rbx, %rdi
                movslq %eax, %rsi
                call kof_list_get
                movq %rax, %rbp             # a = get(j)
                movq %rbx, %rdi
                movslq %r14d, %rsi
                call kof_list_get
                movq %rax, %rsi             # b = get(min)
                movq %rbp, %rdi
                movl %r12d, %edx
                call kof_list_cmp
                testl %eax, %eax
                jge .Lls_next
                movl 8(%rsp), %eax
                movl %eax, %r14d            # min = j
            .Lls_next:
                incl 8(%rsp)
                jmp .Lls_j
            .Lls_swap:
                cmpl %r14d, %r13d
                je .Lls_iinc
                movq %rbx, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, 16(%rsp)         # tmp = get(i)
                movq %rbx, %rdi
                movslq %r14d, %rsi
                call kof_list_get
                movq %rbx, %rdi
                movslq %r13d, %rsi
                movq %rax, %rdx
                call kof_list_set
                movq %rbx, %rdi
                movslq %r14d, %rsi
                movq 16(%rsp), %rdx
                call kof_list_set
            .Lls_iinc:
                incl %r13d
                jmp .Lls_i
            .Lls_done:
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            # kof_list_sorted(rdi=list, esi=tag) -> rax new List, insertion
            # order via kof_list_cmp (stable: equal elements keep their
            # relative order; the receiver is never mutated). Slice 1g.
            .globl kof_list_sorted
            .type kof_list_sorted, @function
            kof_list_sorted:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %r12             # src
                movl %esi, %ebp             # tag (32-bit)
                movl 16(%r12), %ebx         # n
                call kof_list_new
                movq %rax, %r15             # out
                xorl %r13d, %r13d           # i = 0 (copy)
            .Llsorted_copy:
                cmpl %ebx, %r13d
                jge .Llsorted_isort
                movq %r12, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %rsi
                movq %r15, %rdi
                call kof_list_add
                incl %r13d
                jmp .Llsorted_copy
            .Llsorted_isort:
                movl $1, %r13d              # i = 1
            .Llsorted_outer:
                cmpl %ebx, %r13d
                jge .Llsorted_done
                movq %r15, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %r14             # key
                movl %r13d, %eax
                decl %eax
                movl %eax, 0(%rsp)          # j = i - 1 (no stack: calls matam rcx)
            .Llsorted_inner:
                movl 0(%rsp), %ecx
                cmpl $0, %ecx
                jl .Llsorted_insert
                movq %r15, %rdi
                movslq %ecx, %rsi
                call kof_list_get           # rax = b = get(j)
                movq %rax, %rsi
                movq %r14, %rdi             # key
                movl %ebp, %edx
                call kof_list_cmp           # eax = cmp(key, b)
                testl %eax, %eax
                jge .Llsorted_insert        # key >= b: para (estável)
                movq %r15, %rdi
                movl 0(%rsp), %esi
                movslq %esi, %rsi
                call kof_list_get           # rax = v = get(j)
                movq %rax, %rdx
                movq %r15, %rdi
                movl 0(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                call kof_list_set           # set(j+1, v)
                decl 0(%rsp)                # j--
                jmp .Llsorted_inner
            .Llsorted_insert:
                movq %r15, %rdi
                movl 0(%rsp), %esi
                addl $1, %esi
                movslq %esi, %rsi
                movq %r14, %rdx
                call kof_list_set           # set(j+1, key)
                incl %r13d
                jmp .Llsorted_outer
            .Llsorted_done:
                movq %r15, %rax
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            # kof_list_index_of(rdi=list, rsi=elem, edx=tag) -> idx | -1
            .globl kof_list_index_of
            .type kof_list_index_of, @function
            kof_list_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                xorl %r14d, %r14d
            .Llio_loop:
                cmpl 16(%rbx), %r14d
                jge .Llio_none
                movq 24(%rbx), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %r15
                cmpl $1, %r13d
                je .Llio_str
                cmpl $2, %r13d
                je .Llio_obj
                cmpq %r12, %r15
                je .Llio_hit
                jmp .Llio_next
            .Llio_str:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Llio_hit
                jmp .Llio_next
            .Llio_obj:
                testq %r15, %r15
                jz .Llio_next
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_obj_equals
                testl %eax, %eax
                jnz .Llio_hit
            .Llio_next:
                incl %r14d
                jmp .Llio_loop
            .Llio_hit:
                movslq %r14d, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Llio_none:
                movq $-1, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_last_index_of(rdi=list, rsi=elem, edx=tag) -> idx | -1
            .globl kof_list_last_index_of
            .type kof_list_last_index_of, @function
            kof_list_last_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                movl 16(%rbx), %r14d
                decl %r14d
            .Lllo_loop:
                js .Lllo_none
                movq 24(%rbx), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %r15
                cmpl $1, %r13d
                je .Lllo_str
                cmpl $2, %r13d
                je .Lllo_obj
                cmpq %r12, %r15
                je .Lllo_hit
                jmp .Lllo_next
            .Lllo_str:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lllo_hit
                jmp .Lllo_next
            .Lllo_obj:
                testq %r15, %r15
                jz .Lllo_next
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_obj_equals
                testl %eax, %eax
                jnz .Lllo_hit
            .Lllo_next:
                decl %r14d
                jmp .Lllo_loop
            .Lllo_hit:
                movslq %r14d, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lllo_none:
                movq $-1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_add_all(rdi=dst, rsi=src) -> 0/1 mudou
            .globl kof_list_add_all
            .type kof_list_add_all, @function
            kof_list_add_all:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d        # old size
                xorl %r14d, %r14d           # j
            .Llaa_loop:
                cmpl 16(%r12), %r14d
                jge .Llaa_done
                movq %rbx, %rdi
                movq 24(%r12), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %rsi
                call kof_list_add
                incl %r14d
                jmp .Llaa_loop
            .Llaa_done:
                xorl %eax, %eax
                cmpl %r13d, 16(%rbx)
                je .Llaa_ret
                movl $1, %eax
            .Llaa_ret:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_sub_list(rdi=list, esi=begin, edx=end) -> nova List
            # cópia materializada (nunca view viva); fora dos limites →
            # kof_bounds_error (família get(i)), nunca índice negativo lido.
            .globl kof_list_sub_list
            .type kof_list_sub_list, @function
            kof_list_sub_list:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl %esi, %r12d            # begin
                movl %edx, %r13d            # end
                movl 16(%rbx), %eax         # n
                testl %r12d, %r12d
                js .Llsl_bad
                cmpl %eax, %r13d
                jg .Llsl_bad
                cmpl %r13d, %r12d
                jg .Llsl_bad
                movq %rbx, %rdi
                call kof_list_new
                movq %rax, %r14             # nova
                movl %r12d, %ebp            # i = begin (callee-saved: add clobbers rax)
            .Llsl_loop:
                cmpl %r13d, %ebp
                jge .Llsl_done
                movq %r14, %rdi
                movq 24(%rbx), %rcx
                movslq %ebp, %rsi
                movq (%rcx,%rsi,8), %rsi
                call kof_list_add
                incl %ebp
                jmp .Llsl_loop
            .Llsl_done:
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret
            .Llsl_bad:
                movl %r13d, %edi            # end>size / begin>end: índice ofensor = end
                testl %r12d, %r12d
                jns .Llsl_bidx
                movl %r12d, %edi            # begin negativo: o ofensor é o begin
            .Llsl_bidx:
                movl 16(%rbx), %esi
                call kof_bounds_error

            # pagination P1 — kof_list_take(rdi=list, esi=n) -> nova List com
            # os primeiros min(n,size) (clamp honesto); n<0 -> erro nomeado
            # PAGINATION (nunca índice negativo lido).
            .globl kof_list_take
            .type kof_list_take, @function
            kof_list_take:
                pushq %rbx
                movq %rdi, %rbx
                testl %esi, %esi
                js .Ltk_bad
                movl 16(%rbx), %edx         # size
                cmpl %edx, %esi             # n <= size?
                jle .Ltk_ok
                movl %edx, %esi             # n>size -> n=size (clamp)
            .Ltk_ok:
                movl %esi, %edx             # end = n
                xorl %esi, %esi             # begin = 0
                movq %rbx, %rdi
                call kof_list_sub_list
                popq %rbx
                ret
            .Ltk_bad:
                leaq .Lpag_count_msg(%rip), %rdi
                call kof_throw_string
                ud2

            # pagination P1 — kof_list_drop(rdi=list, esi=n) -> nova List a
            # partir de min(n,size) (clamp honesto); n<0 -> PAGINATION.
            .globl kof_list_drop
            .type kof_list_drop, @function
            kof_list_drop:
                pushq %rbx
                movq %rdi, %rbx
                testl %esi, %esi
                js .Ldp_bad
                movl 16(%rbx), %edx         # size (= end)
                cmpl %edx, %esi             # n <= size?
                jle .Ldp_ok
                movl %edx, %esi             # n>size -> begin=size (vazio)
            .Ldp_ok:
                movq %rbx, %rdi
                call kof_list_sub_list
                popq %rbx
                ret
            .Ldp_bad:
                leaq .Lpag_count_msg(%rip), %rdi
                call kof_throw_string
                ud2

            # pagination P1 — kof_list_slice(rdi=list, esi=offset, edx=limit)
            # -> nova List [start, start+min(limit,size-start)); offset<0 ||
            # limit<0 -> PAGINATION; offset>size -> vazia (sem erro).
            .globl kof_list_slice
            .type kof_list_slice, @function
            kof_list_slice:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testl %esi, %esi
                js .Lsc_bad
                testl %edx, %edx
                js .Lsc_bad
                movl 16(%rbx), %r12d        # size
                movl %esi, %r13d            # start = min(offset,size)
                cmpl %r12d, %r13d
                jle .Lsc_ok
                movl %r12d, %r13d
            .Lsc_ok:
                movl %r12d, %eax
                subl %r13d, %eax            # remaining = size - start
                movl %edx, %ecx             # end = start + min(limit,remaining)
                cmpl %eax, %ecx
                jle .Lsc_sum
                movl %eax, %ecx
            .Lsc_sum:
                addl %r13d, %ecx
                movq %rbx, %rdi
                movl %r13d, %esi
                movl %ecx, %edx
                call kof_list_sub_list
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsc_bad:
                leaq .Lpag_ol_msg(%rip), %rdi
                call kof_throw_string
                ud2

            # ---------------------- literais PAGINATION --------------------
            .Lpag_count_msg:
                .long 1
                .long 0
                .quad 0
                .long .Lpag_count_len
                .long 0
            .Lpag_count_body:
                .ascii "PAGINATION: count must be >= 0"
                .byte 0
                .set .Lpag_count_len, . - .Lpag_count_body - 1
            .Lpag_ol_msg:
                .long 1
                .long 0
                .quad 0
                .long .Lpag_ol_len
                .long 0
            .Lpag_ol_body:
                .ascii "PAGINATION: limit/offset must be >= 0"
                .byte 0
                .set .Lpag_ol_len, . - .Lpag_ol_body - 1
            """);
    }
}

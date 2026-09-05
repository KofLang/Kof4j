package dev.kof.compiler;

/**
 * Emissão do ASM de set do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSet {

    private RuntimeSet() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_set_new
            .type kof_set_new, @function
            kof_set_new:
                jmp kof_list_new

            # kof_set_add(rdi=set, rsi=elem, edx=tag 1=string) -> 1 inseriu | 0 existia
            .globl kof_set_add
            .type kof_set_add, @function
            kof_set_add:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                call kof_list_contains_tag
                testl %eax, %eax
                jnz .LKSA_dup
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_list_add
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKSA_dup:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_set_contains(rdi=set, rsi=elem, edx=tag) -> 1/0
            .globl kof_set_contains
            .type kof_set_contains, @function
            kof_set_contains:
                jmp kof_list_contains

            # kof_set_remove(rdi=set, rsi=elem, edx=tag) -> 1/0
            .globl kof_set_remove
            .type kof_set_remove, @function
            kof_set_remove:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # set
                movq %rsi, %r12             # elem alvo
                movl %edx, %r13d            # tag
                xorq %r14, %r14             # i = 0
            .LKSR_scan:
                cmpl 16(%rbx), %r14d
                jge .LKSR_no
                movq 24(%rbx), %rax
                movq (%rax,%r14,8), %rax    # candidato
                cmpl $1, %r13d
                je .LKSR_str
                cmpq %r12, %rax
                je .LKSR_found
                jmp .LKSR_next
            .LKSR_str:
                testq %rax, %rax
                jz .LKSR_next
                movq %rax, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .LKSR_found
            .LKSR_next:
                incq %r14
                jmp .LKSR_scan
            .LKSR_found:
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_list_remove        # remove por índice
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKSR_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_set_size
            .type kof_set_size, @function
            kof_set_size:
                jmp kof_list_size

            .globl kof_set_is_empty
            .type kof_set_is_empty, @function
            kof_set_is_empty:
                jmp kof_list_is_empty

            .globl kof_set_clear
            .type kof_set_clear, @function
            kof_set_clear:
                jmp kof_list_clear

                        # ── kof.security G9 (rate limiting / sessions / API keys) ───────
            # kof_sec_rate_limit(rdi=key String*, rsi=limit int, rdx=window int) -> Bool
            # Simple fixed-window without time (Native best-effort): per-key counter, denies when count >= limit
            """);
    }
}
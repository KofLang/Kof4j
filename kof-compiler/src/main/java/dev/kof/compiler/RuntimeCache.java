package dev.kof.compiler;

/**
 * Emissão do ASM do cache (kof_cache_*) do runtime nativo. Domínio isolado do
 * NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeCache {

    private RuntimeCache() {}

    static void emitCacheFunctions(StringBuilder sb) {
        sb.append("""
            .section .data
            .globl kof_cache_keys
            .globl kof_cache_vals
            .globl kof_cache_exps
            .globl kof_cache_size
            kof_cache_keys: .space 512
            kof_cache_vals: .space 512
            kof_cache_exps: .space 512
            kof_cache_size: .quad 0
            .section .text
            .globl kof_cache_get
            .type kof_cache_get, @function
            kof_cache_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_get_loop:
                cmpq $64, %r12
                jge .kof_cache_get_miss
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_get_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_get_next
                movq kof_cache_exps(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_get_hit
                movq %rax, %r13
                call kof_time_now
                cmpq %rax, %r13
                jle .kof_cache_get_expired
            .kof_cache_get_hit:
                movq kof_cache_vals(,%r12,8), %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_get_expired:
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
            .kof_cache_get_next:
                incq %r12
                jmp .kof_cache_get_loop
            .kof_cache_get_miss:
                xorq %rax, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_set
            .type kof_cache_set, @function
            kof_cache_set:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                xorq %rax, %rax
                call kof_cache_find_slot
                movq %rbx, kof_cache_keys(,%rax,8)
                movq %r12, kof_cache_vals(,%rax,8)
                movq $0, kof_cache_exps(,%rax,8)
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_set_ttl
            .type kof_cache_set_ttl, @function
            kof_cache_set_ttl:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                xorq %rax, %rax
                call kof_cache_find_slot
                movq %rax, %r14
                pushq %r15
                movq %rbx, kof_cache_keys(,%r14,8)
                movq %r12, kof_cache_vals(,%r14,8)
                testl %r13d, %r13d
                jz .kof_cache_set_ttl_noexp
                movl %r13d, %edi
                movq $1000, %rax
                mul %rdi
                movq %rax, %r15
                call kof_time_now
                addq %r15, %rax
                movq %rax, kof_cache_exps(,%r14,8)
                jmp .kof_cache_set_ttl_done
            .kof_cache_set_ttl_noexp:
                movq $0, kof_cache_exps(,%r14,8)
            .kof_cache_set_ttl_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_ttl
            .type kof_cache_ttl, @function
            kof_cache_ttl:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_ttl_loop:
                cmpq $64, %r12
                jge .kof_cache_ttl_miss
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_ttl_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_ttl_next
                movq kof_cache_exps(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_ttl_noexp
                movq %rax, %r13
                call kof_time_now
                movq %r13, %rdi
                subq %rax, %rdi
                js .kof_cache_ttl_expired
                movq %rdi, %rax
                movq $1000, %rcx
                xorq %rdx, %rdx
                divq %rcx
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_noexp:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_expired:
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_next:
                incq %r12
                jmp .kof_cache_ttl_loop
            .kof_cache_ttl_miss:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .kof_cache_ttl_miss2:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_delete
            .type kof_cache_delete, @function
            kof_cache_delete:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                xorq %r12, %r12
            .kof_cache_del_loop:
                cmpq $64, %r12
                jge .kof_cache_del_done
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_del_next
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jz .kof_cache_del_next
                movq $0, kof_cache_keys(,%r12,8)
                movq $0, kof_cache_vals(,%r12,8)
                movq $0, kof_cache_exps(,%r12,8)
                jmp .kof_cache_del_done
            .kof_cache_del_next:
                incq %r12
                jmp .kof_cache_del_loop
            .kof_cache_del_done:
                popq %r12
                popq %rbx
                ret

            .globl kof_cache_clear
            .type kof_cache_clear, @function
            kof_cache_clear:
                xorq %rax, %rax
            .kof_cache_clear_loop:
                cmpq $64, %rax
                jge .kof_cache_clear_done
                movq $0, kof_cache_keys(,%rax,8)
                movq $0, kof_cache_vals(,%rax,8)
                movq $0, kof_cache_exps(,%rax,8)
                incq %rax
                jmp .kof_cache_clear_loop
            .kof_cache_clear_done:
                ret

            .globl kof_cache_find_slot
            .type kof_cache_find_slot, @function
            kof_cache_find_slot:
                pushq %rbx
                pushq %r12
                xorq %r12, %r12
            .kof_cache_find_existing:
                cmpq $64, %r12
                jge .kof_cache_find_first_empty
                movq kof_cache_keys(,%r12,8), %rax
                testq %rax, %rax
                jz .kof_cache_find_first_empty
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_equals
                testq %rax, %rax
                jnz .kof_cache_find_done
                incq %r12
                jmp .kof_cache_find_existing
            .kof_cache_find_first_empty:
                cmpq $64, %r12
                jl .kof_cache_find_done
                xorq %rax, %rax
                jmp .kof_cache_find_ret
            .kof_cache_find_done:
                movq %r12, %rax
            .kof_cache_find_ret:
                popq %r12
                popq %rbx
                ret
            """);
    }

}
package dev.kof.compiler;

/**
 * Emissão do ASM de security10 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity10 {

    private RuntimeSecurity10() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_sec_rate_limit
            .type kof_sec_rate_limit, @function
            kof_sec_rate_limit:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                testl %r12d, %r12d
                jle .Lsec_rl_deny
                testl %r13d, %r13d
                jle .Lsec_rl_deny
                movq .Lkof_sec_rl_len(%rip), %r14
                xorq %r15, %r15
            .Lsec_rl_search:
                cmpq %r14, %r15
                jge .Lsec_rl_notfound
                leaq .Lkof_sec_rl_keys(%rip), %r11
                movq (%r11,%r15,8), %rax
                testq %rbx, %rbx
                jz .Lsec_rl_check_null
                testq %rax, %rax
                jz .Lsec_rl_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lsec_rl_next
                testl %ecx, %ecx
                jz .Lsec_rl_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lsec_rl_cmp:
                cmpq %rcx, %r10
                jge .Lsec_rl_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lsec_rl_next
                incq %r10
                jmp .Lsec_rl_cmp
            .Lsec_rl_check_null:
                testq %rax, %rax
                jnz .Lsec_rl_next
                jmp .Lsec_rl_found
            .Lsec_rl_next:
                incq %r15
                jmp .Lsec_rl_search
            .Lsec_rl_found:
                leaq .Lkof_sec_rl_counts(%rip), %r11
                movl (%r11,%r15,4), %eax
                cmpl %r12d, %eax
                jge .Lsec_rl_deny_found
                incl %eax
                leaq .Lkof_sec_rl_counts(%rip), %r11
                movl %eax, (%r11,%r15,4)
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_rl_deny_found:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_rl_notfound:
                cmpq $32, %r14
                jge .Lsec_rl_deny
                leaq .Lkof_sec_rl_keys(%rip), %r11
                movq %rbx, (%r11,%r14,8)
                leaq .Lkof_sec_rl_counts(%rip), %r11
                movl $1, (%r11,%r14,4)
                incq %r14
                movq %r14, .Lkof_sec_rl_len(%rip)
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_rl_deny:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_session_create(rdi=data String*) -> String id
            .globl kof_sec_session_create
            .type kof_sec_session_create, @function
            kof_sec_session_create:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq $16, %rdi
                call kof_sec_random_hex
                movq %rax, %r12
                testq %r12, %r12
                jz .Lsec_sess_create_fail
                movq .Lkof_sec_sess_len(%rip), %rcx
                cmpq $32, %rcx
                jge .Lsec_sess_create_full
                leaq .Lkof_sec_sess_ids(%rip), %r11
                movq %r12, (%r11,%rcx,8)
                leaq .Lkof_sec_sess_vals(%rip), %r11
                movq %rbx, (%r11,%rcx,8)
                incq %rcx
                movq %rcx, .Lkof_sec_sess_len(%rip)
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_create_full:
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_create_fail:
                xorq %rax, %rax
                popq %r12
                popq %rbx
                ret

            # kof_sec_session_get(rdi=id String*) -> String data or 0
            .globl kof_sec_session_get
            .type kof_sec_session_get, @function
            kof_sec_session_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lsec_sess_get_null
                movq .Lkof_sec_sess_len(%rip), %r12
                xorq %r13, %r13
            .Lsec_sess_get_search:
                cmpq %r12, %r13
                jge .Lsec_sess_get_notfound
                leaq .Lkof_sec_sess_ids(%rip), %r11
                movq (%r11,%r13,8), %rax
                testq %rax, %rax
                jz .Lsec_sess_get_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lsec_sess_get_next
                testl %ecx, %ecx
                jz .Lsec_sess_get_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lsec_sess_get_cmp:
                cmpq %rcx, %r10
                jge .Lsec_sess_get_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lsec_sess_get_next
                incq %r10
                jmp .Lsec_sess_get_cmp
            .Lsec_sess_get_next:
                incq %r13
                jmp .Lsec_sess_get_search
            .Lsec_sess_get_found:
                leaq .Lkof_sec_sess_vals(%rip), %r11
                movq (%r11,%r13,8), %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_get_notfound:
                xorq %rax, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_get_null:
                xorq %rax, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_session_destroy(rdi=id String*) -> Bool
            .globl kof_sec_session_destroy
            .type kof_sec_session_destroy, @function
            kof_sec_session_destroy:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lsec_sess_destroy_null
                movq .Lkof_sec_sess_len(%rip), %r12
                xorq %r13, %r13
            .Lsec_sess_destroy_search:
                cmpq %r12, %r13
                jge .Lsec_sess_destroy_notfound
                leaq .Lkof_sec_sess_ids(%rip), %r11
                movq (%r11,%r13,8), %rax
                testq %rax, %rax
                jz .Lsec_sess_destroy_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lsec_sess_destroy_next
                testl %ecx, %ecx
                jz .Lsec_sess_destroy_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lsec_sess_destroy_cmp:
                cmpq %rcx, %r10
                jge .Lsec_sess_destroy_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lsec_sess_destroy_next
                incq %r10
                jmp .Lsec_sess_destroy_cmp
            .Lsec_sess_destroy_next:
                incq %r13
                jmp .Lsec_sess_destroy_search
            .Lsec_sess_destroy_found:
                decq %r12
                movq %r12, .Lkof_sec_sess_len(%rip)
                cmpq %r13, %r12
                je .Lsec_sess_destroy_last
                leaq .Lkof_sec_sess_ids(%rip), %r11
                movq (%r11,%r12,8), %rax
                leaq .Lkof_sec_sess_ids(%rip), %r11
                movq %rax, (%r11,%r13,8)
                leaq .Lkof_sec_sess_vals(%rip), %r11
                movq (%r11,%r12,8), %rax
                leaq .Lkof_sec_sess_vals(%rip), %r11
                movq %rax, (%r11,%r13,8)
            .Lsec_sess_destroy_last:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_destroy_notfound:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_sess_destroy_null:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_api_key_generate() -> String
            .globl kof_sec_api_key_generate
            .type kof_sec_api_key_generate, @function
            kof_sec_api_key_generate:
                pushq %rbx
                movq $32, %rdi
                call kof_sec_random_hex
                movq %rax, %rbx
                testq %rbx, %rbx
                jz .Lsec_apikey_gen_fail
                movq .Lkof_sec_apikey_len(%rip), %rcx
                cmpq $32, %rcx
                jge .Lsec_apikey_gen_full
                leaq .Lkof_sec_apikeys(%rip), %r11
                movq %rbx, (%r11,%rcx,8)
                incq %rcx
                movq %rcx, .Lkof_sec_apikey_len(%rip)
                movq %rbx, %rax
                popq %rbx
                ret
            .Lsec_apikey_gen_full:
                movq %rbx, %rax
                popq %rbx
                ret
            .Lsec_apikey_gen_fail:
                xorq %rax, %rax
                popq %rbx
                ret

            # kof_sec_api_key_valid(rdi=key String*) -> Bool
            .globl kof_sec_api_key_valid
            .type kof_sec_api_key_valid, @function
            kof_sec_api_key_valid:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lsec_apikey_valid_null
                movq .Lkof_sec_apikey_len(%rip), %r12
                xorq %r13, %r13
            .Lsec_apikey_valid_search:
                cmpq %r12, %r13
                jge .Lsec_apikey_valid_notfound
                leaq .Lkof_sec_apikeys(%rip), %r11
                movq (%r11,%r13,8), %rax
                testq %rax, %rax
                jz .Lsec_apikey_valid_next
                movl 16(%rbx), %ecx
                movl 16(%rax), %edx
                cmpl %edx, %ecx
                jne .Lsec_apikey_valid_next
                testl %ecx, %ecx
                jz .Lsec_apikey_valid_found
                leaq 24(%rbx), %rdi
                leaq 24(%rax), %rsi
                movslq %ecx, %rcx
                xorq %r10, %r10
            .Lsec_apikey_valid_cmp:
                cmpq %rcx, %r10
                jge .Lsec_apikey_valid_found
                movzbl (%rdi,%r10), %eax
                movzbl (%rsi,%r10), %edx
                cmpl %edx, %eax
                jne .Lsec_apikey_valid_next
                incq %r10
                jmp .Lsec_apikey_valid_cmp
            .Lsec_apikey_valid_next:
                incq %r13
                jmp .Lsec_apikey_valid_search
            .Lsec_apikey_valid_found:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_apikey_valid_notfound:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_apikey_valid_null:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_constant_time_equals(rdi=a, rsi=b) → 1/0
            .globl kof_sec_constant_time_equals
            .type kof_sec_constant_time_equals, @function
            kof_sec_constant_time_equals:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %ecx
                cmpl %ecx, %r13d
                je .Lsec_cte_len_ok
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lsec_cte_len_ok:
                movl %r13d, %ecx
                xorl %eax, %eax
            .Lsec_cte_loop:
                testl %ecx, %ecx
                jle .Lsec_cte_done
                movzbl 23(%rbx,%rcx), %edx
                movzbl 23(%r12,%rcx), %r15d
                xorl %r15d, %edx
                orl %edx, %eax
                decq %rcx
                jmp .Lsec_cte_loop
            .Lsec_cte_done:
                testl %eax, %eax
                setz %al
                movzbl %al, %eax
                popq %r13
                popq %r12
                popq %rbx
            """);
    }
}
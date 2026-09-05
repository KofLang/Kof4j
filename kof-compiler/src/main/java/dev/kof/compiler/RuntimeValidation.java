package dev.kof.compiler;

/**
 * Emissão do ASM de validation do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeValidation {

    private RuntimeValidation() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_validation_required
            .type kof_validation_required, @function
            kof_validation_required:
                testq %rdi, %rdi
                jz .Lv_req_false
                movl 16(%rdi), %eax
                testl %eax, %eax
                jz .Lv_req_false
                movl $1, %eax
                ret
            .Lv_req_false:
                xorl %eax, %eax
                ret

            # kof_validation_notBlank(rdi=str) -> 1/0
            .globl kof_validation_notBlank
            .type kof_validation_notBlank, @function
            kof_validation_notBlank:
                testq %rdi, %rdi
                jz .Lv_nb_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jz .Lv_nb_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_nb_loop:
                cmpl %eax, %ecx
                jle .Lv_nb_false
                movzbl (%r8,%rax), %edx
                cmpb $32, %dl
                je .Lv_nb_next
                cmpb $9, %dl
                je .Lv_nb_next
                cmpb $10, %dl
                je .Lv_nb_next
                cmpb $13, %dl
                je .Lv_nb_next
                movl $1, %eax
                ret
            .Lv_nb_next:
                incq %rax
                jmp .Lv_nb_loop
            .Lv_nb_false:
                xorl %eax, %eax
                ret

            # kof_validation_minLength(rdi=str, rsi=min) -> 1/0
            .globl kof_validation_minLength
            .type kof_validation_minLength, @function
            kof_validation_minLength:
                testq %rdi, %rdi
                jz .Lv_min_false
                movl 16(%rdi), %eax
                cmpl %esi, %eax
                jge .Lv_min_true
            .Lv_min_false:
                xorl %eax, %eax
                ret
            .Lv_min_true:
                movl $1, %eax
                ret

            # kof_validation_maxLength(rdi=str, rsi=max) -> 1/0
            .globl kof_validation_maxLength
            .type kof_validation_maxLength, @function
            kof_validation_maxLength:
                testq %rdi, %rdi
                jz .Lv_max_true
                movl 16(%rdi), %eax
                cmpl %esi, %eax
                jle .Lv_max_true2
                xorl %eax, %eax
                ret
            .Lv_max_true:
                movl $1, %eax
                ret
            .Lv_max_true2:
                movl $1, %eax
                ret

            # kof_validation_lengthBetween(rdi=str, rsi=min, rdx=max) -> 1/0
            .globl kof_validation_lengthBetween
            .type kof_validation_lengthBetween, @function
            kof_validation_lengthBetween:
                testq %rdi, %rdi
                jz .Lv_bet_false
                movl 16(%rdi), %eax
                cmpl %esi, %eax
                jl .Lv_bet_false
                cmpl %edx, %eax
                jg .Lv_bet_false
                movl $1, %eax
                ret
            .Lv_bet_false:
                xorl %eax, %eax
                ret

            # kof_validation_isEmail(rdi=str) -> 1/0  (simple: one @, '.' after @, no spaces)
            .globl kof_validation_isEmail
            .type kof_validation_isEmail, @function
            kof_validation_isEmail:
                testq %rdi, %rdi
                jz .Lv_em_false
                movl 16(%rdi), %ecx
                cmpl $3, %ecx
                jl .Lv_em_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
                xorq %r9, %r9          # at count
                xorl %r10d, %r10d       # at pos
                xorq %r11, %r11         # dot after at
            .Lv_em_loop:
                cmpl %ecx, %eax
                jge .Lv_em_check
                movzbl (%r8,%rax), %edx
                cmpb $32, %dl
                je .Lv_em_false
                cmpb $9, %dl
                je .Lv_em_false
                cmpb $64, %dl
                jne .Lv_em_notat
                incq %r9
                movl %eax, %r10d
                jmp .Lv_em_next
            .Lv_em_notat:
                cmpb $46, %dl
                jne .Lv_em_next
                cmpq $0, %r9
                je .Lv_em_next
                movq %r10, %r11
                incq %r11
                cmpq %rax, %r11
                jg .Lv_em_next
                movq $1, %r11
            .Lv_em_next:
                incq %rax
                jmp .Lv_em_loop
            .Lv_em_check:
                cmpq $1, %r9
                jne .Lv_em_false
                cmpq $0, %r10
                je .Lv_em_false
                cmpq $0, %r11
                je .Lv_em_false
                movl %ecx, %eax
                decl %eax
                cmpl %r10d, %eax
                je .Lv_em_false
                movzbl -1(%r8,%rcx), %edx
                cmpb $46, %dl
                je .Lv_em_false
                movl $1, %eax
                ret
            .Lv_em_false:
                xorl %eax, %eax
                ret

            # kof_validation_isUrl(rdi=str) -> 1/0  (http:// or https://)
            .globl kof_validation_isUrl
            .type kof_validation_isUrl, @function
            kof_validation_isUrl:
                testq %rdi, %rdi
                jz .Lv_url_false
                movl 16(%rdi), %ecx
                cmpl $7, %ecx
                jl .Lv_url_false
                leaq 24(%rdi), %r8
                cmpb $104, (%r8)
                jne .Lv_url_false
                cmpb $116, 1(%r8)
                jne .Lv_url_false
                cmpb $116, 2(%r8)
                jne .Lv_url_false
                cmpb $112, 3(%r8)
                jne .Lv_url_false
                cmpb $58, 4(%r8)
                jne .Lv_url_false
                cmpb $47, 5(%r8)
                jne .Lv_url_false
                cmpb $47, 6(%r8)
                jne .Lv_url_check_https
                movl $1, %eax
                ret
            .Lv_url_check_https:
                cmpl $8, %ecx
                jl .Lv_url_false
                cmpb $115, 4(%r8)
                jne .Lv_url_false
                cmpb $58, 5(%r8)
                jne .Lv_url_false
                cmpb $47, 6(%r8)
                jne .Lv_url_false
                cmpb $47, 7(%r8)
                jne .Lv_url_false
                movl $1, %eax
                ret
            .Lv_url_false:
                xorl %eax, %eax
                ret

            # kof_validation_matches(rdi=value, rsi=pattern) -> 1/0  (substring)
            .globl kof_validation_matches
            .type kof_validation_matches, @function
            kof_validation_matches:
                testq %rdi, %rdi
                jz .Lv_mat_false
                testq %rsi, %rsi
                jz .Lv_mat_false
                movl 16(%rdi), %ecx
                movl 16(%rsi), %r14d
                testl %r14d, %r14d
                jz .Lv_mat_true
                cmpl %r14d, %ecx
                jl .Lv_mat_false
                leaq 24(%rdi), %r8
                leaq 24(%rsi), %r9
                xorq %rax, %rax          # i
            .Lv_mat_outer:
                movl %ecx, %r10d
                subl %eax, %r10d
                cmpl %r14d, %r10d
                jl .Lv_mat_false
                xorq %r10, %r10          # j
            .Lv_mat_inner:
                cmpl %r14d, %r10d
                jge .Lv_mat_true
                movq %rax, %r11
                addq %r10, %r11
                movzbl (%r8,%r11), %r11d
                movzbl (%r9,%r10), %edx
                cmpb %r11b, %dl
                jne .Lv_mat_next
                incq %r10
                jmp .Lv_mat_inner
            .Lv_mat_next:
                incq %rax
                jmp .Lv_mat_outer
            .Lv_mat_true:
                movl $1, %eax
                ret
            .Lv_mat_false:
                xorl %eax, %eax
                ret

            # kof_validation_isInt(rdi=str) -> 1/0
            .globl kof_validation_isInt
            .type kof_validation_isInt, @function
            kof_validation_isInt:
                testq %rdi, %rdi
                jz .Lv_int_false
                movl 16(%rdi), %ecx
                testl %ecx, %ecx
                jz .Lv_int_false
                leaq 24(%rdi), %r8
                xorq %rax, %rax
                movzbl (%r8), %edx
                cmpb $45, %dl
                je .Lv_int_sign
                cmpb $43, %dl
                je .Lv_int_sign
                jmp .Lv_int_digits
            .Lv_int_sign:
                incq %rax
                cmpl %ecx, %eax
                jge .Lv_int_false
            .Lv_int_digits:
                xorq %r9, %r9
            .Lv_int_loop:
                cmpl %ecx, %eax
                jge .Lv_int_check
                movzbl (%r8,%rax), %edx
                cmpb $48, %dl
                jl .Lv_int_false
                cmpb $57, %dl
                jg .Lv_int_false
                incq %rax
                incq %r9
                jmp .Lv_int_loop
            .Lv_int_check:
                testq %r9, %r9
                jz .Lv_int_false
                movl $1, %eax
                ret
            .Lv_int_false:
                xorl %eax, %eax
                ret

            # kof_validation_isLong same as isInt
            .globl kof_validation_isLong
            .type kof_validation_isLong, @function
            kof_validation_isLong:
                jmp kof_validation_isInt

            # kof_validation_inRange(rdi=val, rsi=min, rdx=max) -> 1/0
            .globl kof_validation_inRange
            .type kof_validation_inRange, @function
            kof_validation_inRange:
                cmpl %esi, %edi
                jl .Lv_range_false
                cmpl %edx, %edi
                jg .Lv_range_false
                movl $1, %eax
                ret
            .Lv_range_false:
                xorl %eax, %eax
                ret

            # kof_validation_min(rdi=val, rsi=min) -> 1/0
            .globl kof_validation_min
            .type kof_validation_min, @function
            kof_validation_min:
                cmpl %esi, %edi
                jge .Lv_min2_true
                xorl %eax, %eax
                ret
            .Lv_min2_true:
                movl $1, %eax
                ret

            # kof_validation_max(rdi=val, rsi=max) -> 1/0
            .globl kof_validation_max
            .type kof_validation_max, @function
            kof_validation_max:
                cmpl %esi, %edi
                jle .Lv_max2_true
                xorl %eax, %eax
                ret
            .Lv_max2_true:
                movl $1, %eax
                ret

                        .section .bss
            .Lkof_obs_counters: .zero 512
            .Lkof_obs_counter_len: .quad 0
            .Lkof_obs_gauges: .zero 512
            .Lkof_obs_gauge_len: .quad 0
            .Lkof_obs_histograms: .zero 384
            .Lkof_obs_histogram_len: .quad 0
            .section .data
            .Lstr_obs_up: .asciz "UP"
            .Lstr_obs_empty: .asciz ""
            .Lstr_obs_type_counter: .asciz "# TYPE "
            .Lstr_obs_ws_nl_counter: .asciz " counter\\n"
            .Lstr_obs_ws_nl_gauge: .asciz " gauge\\n"
            .Lstr_obs_space: .asciz " "
            .Lstr_obs_nl: .asciz "\\n"
            .Lstr_obs_suffix_count: .asciz "_count"
            .Lstr_obs_suffix_sum: .asciz "_sum"
            .section .text

            # ── kof.observability (G5) ──────────────────────────────────────
            # kof_observability_health() -> String "UP"
            """);
    }
}
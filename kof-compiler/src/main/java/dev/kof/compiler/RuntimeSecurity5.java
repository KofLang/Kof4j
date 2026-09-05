package dev.kof.compiler;

/**
 * Emissão do ASM de security5 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity5 {

    private RuntimeSecurity5() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                jge .Ljs_khash_zero
                movb 192(%rsp,%rcx), %dl
                movb %dl, 0(%rsp,%rcx)
                incq %rcx
                jmp .Ljs_khash_copy
            .Ljs_khash_zero:
                cmpq $64, %rcx
                jge .Ljs_kready
                movb $0, 0(%rsp,%rcx)
                incq %rcx
                jmp .Ljs_khash_zero
            .Ljs_kready:
                xorq %r8, %r8            # datalen
            .Ljs_hcpy:
                cmpq %r13, %r8
                jge .Ljs_hdot
                movb 0(%rbx,%r8), %dl
                movb %dl, 64(%rsp,%r8)
                incq %r8
                jmp .Ljs_hcpy
            .Ljs_hdot:
                movb $46, 64(%rsp,%r8)
                incq %r8
                leaq 1(%r13,%r15), %rcx   # limite = h_len + 1 + p_len
                movq %rcx, 440(%rsp)
            .Ljs_pcpy:
                cmpq 440(%rsp), %r8
                jge .Ljs_data_done
                movq %r8, %rcx
                subq %r13, %rcx
                decq %rcx
                movb 0(%r14,%rcx), %dl
                movb %dl, 64(%rsp,%r8)
                incq %r8
                jmp .Ljs_pcpy
            .Ljs_data_done:
                leaq 320(%rsp), %rdi
                leaq 0(%rsp), %rsi
                leaq 64(%rsp), %rdx
                movq %r8, %rcx
                call kof_sec_hmac_internal
                leaq 352(%rsp), %rdi
                leaq 320(%rsp), %rsi
                movq $32, %rdx
                call kof_b64url_encode_internal
                movq %rax, %r15          # chars (43)
                movl %r15d, %eax
                addl $25, %eax
                movslq %eax, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r15d, 16(%rbx)
                movl $0, 20(%rbx)
                xorq %rcx, %rcx
            .Ljs_out_cp:
                cmpq %r15, %rcx
                jge .Ljs_out_nul
                movb 352(%rsp,%rcx), %al
                movb %al, 24(%rbx,%rcx)
                incq %rcx
                jmp .Ljs_out_cp
            .Ljs_out_nul:
                movb $0, 24(%rbx,%r15)
                movq %rbx, %rax
                addq $448, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_jwt_create_ttl(rdi=claims, rsi=secret, edx=ttl) → token
            .globl kof_sec_jwt_create_ttl
            .type kof_sec_jwt_create_ttl, @function
            kof_sec_jwt_create_ttl:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # claims
                movq %rsi, %r12          # secret
                movl %edx, %r13d         # ttl
                subq $1024, %rsp         # payload 0, hdrb64 300, payloadb64 340
                call kof_now
                movq $1000, %rcx
                xorq %rdx, %rdx
                divq %rcx
                movq %rax, %r14          # now_s
                movl 16(%rbx), %eax
                leaq 24(%rbx), %r8
                xorq %rcx, %rcx          # lastBrace
                xorq %r9, %r9            # i
            .Ljw_brace:
                cmpl %eax, %r9d
                jge .Ljw_brace_done
                cmpb $125, (%r8,%r9)
                jne .Ljw_brace_next
                movq %r9, %rcx
            .Ljw_brace_next:
                incq %r9
                jmp .Ljw_brace
            .Ljw_brace_done:
                testq %rcx, %rcx
                jnz .Ljw_head
                cmpb $125, (%r8)
                jne .Ljw_bad_claims
            .Ljw_head:
                xorq %r15, %r15          # payload len
            .Ljw_hcopy:
                cmpq %rcx, %r15
                jge .Ljw_hsep
                movb (%r8,%r15), %al
                movb %al, (%rsp,%r15)
                incq %r15
                jmp .Ljw_hcopy
            .Ljw_hsep:
                testq %rcx, %rcx
                je .Ljw_sep_none
                movzbl -1(%rsp,%r15), %eax
                cmpb $123, %al
                je .Ljw_sep_none
                movb $44, (%rsp,%r15)
                incq %r15
            .Ljw_sep_none:
                leaq .Lstr_jwt_iat(%rip), %rsi
                movq $6, %rcx
            .Ljw_iat:
                testq %rcx, %rcx
                jz .Ljw_iat_num
                movb (%rsi), %al
                movb %al, (%rsp,%r15)
                incq %rsi
                incq %r15
                decq %rcx
                jmp .Ljw_iat
            .Ljw_iat_num:
                movl %r14d, %edi
                call kof_int_to_string
                movq %rax, %r9
                movl 16(%r9), %edx
                xorq %rcx, %rcx
            .Ljw_iat_cp:
                cmpl %edx, %ecx
                jge .Ljw_iat_done
                movb 24(%r9,%rcx), %al
                movb %al, (%rsp,%r15)
                incq %r15
                incq %rcx
                jmp .Ljw_iat_cp
            .Ljw_iat_done:
                leaq .Lstr_jwt_expk(%rip), %rsi
                movq $7, %rcx
            .Ljw_expk:
                testq %rcx, %rcx
                jz .Ljw_exp_num
                movb (%rsi), %al
                movb %al, (%rsp,%r15)
                incq %rsi
                incq %r15
                decq %rcx
                jmp .Ljw_expk
            .Ljw_exp_num:
                movl %r14d, %eax
                addl %r13d, %eax
                movl %eax, %edi
                call kof_int_to_string
                movq %rax, %r9
                movl 16(%r9), %edx
                xorq %rcx, %rcx
            .Ljw_exp_cp:
                cmpl %edx, %ecx
                jge .Ljw_exp_done
                movb 24(%r9,%rcx), %al
                movb %al, (%rsp,%r15)
                incq %r15
                incq %rcx
                jmp .Ljw_exp_cp
            .Ljw_exp_done:
                movb $125, (%rsp,%r15)
                incq %r15
                leaq 300(%rsp), %rdi
                leaq .Lstr_jwt_header(%rip), %rsi
                movq $27, %rdx
                call kof_b64url_encode_internal
                movq %rax, %r13          # header b64 len (36)
                leaq 340(%rsp), %rdi
                movq %rsp, %rsi
                movq %r15, %rdx
                call kof_b64url_encode_internal
                movq %rax, %r14          # payload b64 len
                movq %r13, 944(%rsp)     # preserva header len
                movq %r14, 952(%rsp)     # preserva payload len
                movq %r12, %rdi
                leaq 300(%rsp), %rsi
                movq %r13, %rdx
                leaq 340(%rsp), %rcx
                movq %r14, %r8
                call kof_sec_jwt_sign_buf
                movq %rax, %r15          # sig KofString*
                movl %r13d, %eax
                addl %r14d, %eax
                addl 16(%r15), %eax
                addl $2, %eax
                movl %eax, %r13d         # total
                leaq 25(%rax), %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r13d, 16(%rbx)
                movl $0, 20(%rbx)
                xorq %rcx, %rcx
                xorq %r8, %r8            # out len
            .Ljw_th:
                cmpl 944(%rsp), %ecx
                jge .Ljw_td1
                movzbl 300(%rsp,%rcx), %eax
                movb %al, 24(%rbx,%r8)
                incq %r8
                incq %rcx
                jmp .Ljw_th
            .Ljw_td1:
                movb $46, 24(%rbx,%r8)
                incq %r8
                xorq %rcx, %rcx
            .Ljw_tp:
                cmpl 952(%rsp), %ecx
                jge .Ljw_td2
                movzbl 340(%rsp,%rcx), %eax
                movb %al, 24(%rbx,%r8)
                incq %r8
                incq %rcx
                jmp .Ljw_tp
            .Ljw_td2:
                movb $46, 24(%rbx,%r8)
                incq %r8
                movl 16(%r15), %edx
                xorq %rcx, %rcx
            .Ljw_ts:
                cmpl %edx, %ecx
                jge .Ljw_tnul
                movb 24(%r15,%rcx), %al
                movb %al, 24(%rbx,%r8)
                incq %r8
                incq %rcx
                jmp .Ljw_ts
            .Ljw_tnul:
                movb $0, 24(%rbx,%r8)
                movq %rbx, %rax
                addq $1024, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljw_bad_claims:
                leaq .Lstr_jwt_invalid(%rip), %rdi
                movq $13, %rsi
                call kof_jwt_fail

            # kof_sec_jwt_create(rdi=claims, rsi=secret) → token (ttl 3600)
            .globl kof_sec_jwt_create
            .type kof_sec_jwt_create, @function
            kof_sec_jwt_create:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $3600, %edx
                call kof_sec_jwt_create_ttl
                popq %r12
                popq %rbx
                ret

            # kof_sec_jwt_verify_iss_aud(rdi=token, rsi=secret, rdx=iss, rcx=aud)
            # → payloadJson (KofString*); lança em falha
            .globl kof_sec_jwt_verify_iss_aud
            .type kof_sec_jwt_verify_iss_aud, @function
            kof_sec_jwt_verify_iss_aud:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # token
                movq %rsi, %r12          # secret
                movq %rdx, %r13          # iss (0 = null)
                movq %rcx, %r14          # aud (0 = null)
                subq $1536, %rsp
                movq 16(%rbx), %rax
                movq %rax, 1528(%rsp)    # token len
                leaq 24(%rbx), %r8
                movq %r8, 1520(%rsp)     # token data
                xorq %r9, %r9            # i
                movq $-1, %r15           # d1
                xorq %r10, %r10          # d2
            .Ljv_dots:
                cmpq %rax, %r9
                jge .Ljv_dots_done
                cmpb $46, (%r8,%r9)
                jne .Ljv_dots_next
                cmpq $-1, %r15
                jne .Ljv_dots_second
                movq %r9, %r15
                jmp .Ljv_dots_next
            .Ljv_dots_second:
                movq %r9, %r10
            .Ljv_dots_next:
                incq %r9
                jmp .Ljv_dots
            .Ljv_dots_done:
                testq %r10, %r10
                jz .Ljv_bad_token
                cmpq $-1, %r15
                je .Ljv_bad_token
                movq %r10, 1504(%rsp)    # d2 preservado (r10 é caller-saved)
                movq %rsp, %rdi
                movq %r8, %rsi
                movq %r15, %rdx
                call kof_b64url_decode_internal
                cmpq $8, %rax
                jb .Ljv_bad_token
                movq %rax, %r9           # hdr len
                xorq %rcx, %rcx
            .Ljv_alg:
                movq %r9, %rax
                subq %rcx, %rax
                cmpq $5, %rax
                jl .Ljv_alg_fail
                leaq .Lstr_hs256(%rip), %rsi
                xorq %rdx, %rdx
            .Ljv_alg_cmp:
                cmpq $5, %rdx
                jge .Ljv_alg_ok
                leaq (%rcx,%rdx), %r8
                movzbl (%rsp,%r8), %eax
                cmpb (%rsi,%rdx), %al
                jne .Ljv_alg_next
                incq %rdx
                jmp .Ljv_alg_cmp
            .Ljv_alg_next:
                incq %rcx
                jmp .Ljv_alg
            .Ljv_alg_fail:
                leaq .Lstr_jwt_alg(%rip), %rdi
                movq $22, %rsi
                call kof_jwt_fail
            .Ljv_alg_ok:
                movq %r12, %rdi
                movq 1520(%rsp), %r8
                movq %r8, %rsi
                movq %r15, %rdx
                leaq 1(%r15,%r8), %rcx
                movq 1504(%rsp), %r8
                subq %r15, %r8
                decq %r8
                call kof_sec_jwt_sign_buf
                movq %rax, 704(%rsp)     # expected
                movq 1528(%rsp), %rax
                subq 1504(%rsp), %rax
                decq %rax
                movq %rax, %r9
                movq 704(%rsp), %rdx
                movl 16(%rdx), %ecx
                cmpl %ecx, %r9d
                jne .Ljv_bad_sig
                movq 1520(%rsp), %r8
                movq 1504(%rsp), %rsi
                leaq 1(%rsi,%r8), %rsi
                xorq %r11, %r11
                xorq %rcx, %rcx
            .Ljv_sig_cmp:
                cmpq %r9, %rcx
                jge .Ljv_sig_done
                movzbl 24(%rdx,%rcx), %eax
                movzbl (%rsi,%rcx), %ebx
                xorl %ebx, %eax
                orq %rax, %r11
                incq %rcx
                jmp .Ljv_sig_cmp
            .Ljv_sig_done:
                testq %r11, %r11
                jnz .Ljv_bad_sig
                movq 1520(%rsp), %r8
                leaq 160(%rsp), %rdi
                leaq 1(%r15,%r8), %rsi
                movq 1504(%rsp), %rdx
                subq %r15, %rdx
                decq %rdx
                call kof_b64url_decode_internal
                movq %rax, %r9
                leaq 160(%rsp), %rdi
                movq %rax, %rsi
                call .Ljf_mkstr
                movq %rax, 672(%rsp)     # payloadJson
                leaq .Lstr_exp_key(%rip), %rdi
                movq $3, %rsi
                call .Ljf_mkstr
                movq %rax, %rsi
                movq 672(%rsp), %rdi
                call kof_json_find_value
                movq %rax, %rbx
                movl 16(%rbx), %ecx
                testl %ecx, %ecx
                jz .Ljv_exp_ok
                movq %rbx, %rdi
                call kof_string_to_long
                movq %rax, %r9
                imulq $1000, %r9
                call kof_now
                cmpq %rax, %r9
            """);
    }
}
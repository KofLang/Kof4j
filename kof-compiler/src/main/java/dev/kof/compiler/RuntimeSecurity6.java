package dev.kof.compiler;

/**
 * Emissão do ASM de security6 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity6 {

    private RuntimeSecurity6() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                jle .Ljv_expired
            .Ljv_exp_ok:
                testq %r13, %r13
                jz .Ljv_aud
                leaq .Lstr_iss_key(%rip), %rdi
                movq $3, %rsi
                call .Ljf_mkstr
                movq %rax, %rsi
                movq 672(%rsp), %rdi
                call kof_json_find_value
                movq %rax, %rsi
                movq %r13, %rdi
                call kof_string_equals
                testl %eax, %eax
                jz .Ljv_iss_fail
            .Ljv_aud:
                testq %r14, %r14
                jz .Ljv_ok
                leaq .Lstr_aud_key(%rip), %rdi
                movq $3, %rsi
                call .Ljf_mkstr
                movq %rax, %rsi
                movq 672(%rsp), %rdi
                call kof_json_find_value
                movq %rax, %rsi
                movq %r14, %rdi
                call kof_string_equals
                testl %eax, %eax
                jz .Ljv_aud_fail
            .Ljv_ok:
                movq 672(%rsp), %rax
                addq $1536, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljv_bad_token:
                leaq .Lstr_jwt_invalid(%rip), %rdi
                movq $13, %rsi
                call kof_jwt_fail
            .Ljv_bad_sig:
                leaq .Lstr_jwt_sig(%rip), %rdi
                movq $17, %rsi
                call kof_jwt_fail
            .Ljv_expired:
                leaq .Lstr_jwt_exp(%rip), %rdi
                movq $13, %rsi
                call kof_jwt_fail
            .Ljv_iss_fail:
                leaq .Lstr_jwt_iss(%rip), %rdi
                movq $15, %rsi
                call kof_jwt_fail
            .Ljv_aud_fail:
                leaq .Lstr_jwt_aud(%rip), %rdi
                movq $17, %rsi
                call kof_jwt_fail

            # kof_sec_jwt_verify(rdi=token, rsi=secret) → payloadJson
            .globl kof_sec_jwt_verify
            .type kof_sec_jwt_verify, @function
            kof_sec_jwt_verify:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rbx, %rdi
                movq %r12, %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                call kof_sec_jwt_verify_iss_aud
                popq %r12
                popq %rbx
                ret

# ── AES-256 (FIPS 197) ───────────────────────────────────────
            # kof_aes_xtime(rdi=byte) → byte*2 em GF(2^8)
            kof_aes_xtime:
                movzbl %dil, %eax
                addb %al, %al
                jnc .Laes_xtime_done
                xorb $0x1b, %al
            .Laes_xtime_done:
                ret

            # kof_aes_mult(rdi=a, rsi=b) → a*b em GF(2^8)
            kof_aes_mult:
                pushq %rbx
                pushq %r12
                movzbl %dil, %ebx
                movzbl %sil, %ecx
                xorl %r12d, %r12d
            .Laes_mult_loop:
                testl %ecx, %ecx
                jz .Laes_mult_done
                testb $1, %cl
                jz .Laes_mult_shift
                xorb %bl, %r12b
            .Laes_mult_shift:
                testb $0x80, %bl
                jz .Laes_mult_shift_ok
                addb %bl, %bl
                xorb $0x1b, %bl
                jmp .Laes_mult_next
            .Laes_mult_shift_ok:
                addb %bl, %bl
            .Laes_mult_next:
                shrb %cl
                jmp .Laes_mult_loop
            .Laes_mult_done:
                movzbl %r12b, %eax
                popq %r12
                popq %rbx
                ret

            # kof_aes_subword(rdi=word) → word com os 4 bytes SBOXED
            kof_aes_subword:
                pushq %rbx
                subq $8, %rsp
                movl %edi, %eax
                movb %al, 0(%rsp)
                shrl $8, %eax
                movb %al, 1(%rsp)
                shrl $8, %eax
                movb %al, 2(%rsp)
                shrl $8, %eax
                movb %al, 3(%rsp)
                leaq .Laes_sbox(%rip), %rbx
                movzbl 0(%rsp), %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%rsp)
                movzbl 1(%rsp), %eax
                movb (%rbx,%rax), %al
                movb %al, 1(%rsp)
                movzbl 2(%rsp), %eax
                movb (%rbx,%rax), %al
                movb %al, 2(%rsp)
                movzbl 3(%rsp), %eax
                movb (%rbx,%rax), %al
                movb %al, 3(%rsp)
                movzbl 0(%rsp), %eax
                movzbl 1(%rsp), %ecx
                shlq $8, %rcx
                orq %rcx, %rax
                movzbl 2(%rsp), %ecx
                shlq $16, %rcx
                orq %rcx, %rax
                movzbl 3(%rsp), %ecx
                shlq $24, %rcx
                orq %rcx, %rax
                addq $8, %rsp
                popq %rbx
                ret

            # kof_aes256_expand_key(rdi=key32, rsi=expanded240)
            kof_aes256_expand_key:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                xorq %rcx, %rcx
            .Laes_ek_copy:
                cmpq $32, %rcx
                jge .Laes_ek_i
                movb (%rbx,%rcx), %al
                movb %al, (%r12,%rcx)
                incq %rcx
                jmp .Laes_ek_copy
            .Laes_ek_i:
                movq $8, %r14
            .Laes_ek_i_loop:
                cmpq $60, %r14
                jge .Laes_ek_done
                movl -4(%r12,%r14,4), %r13d   # temp = w[i-1]
                movq %r14, %rax
                andq $7, %rax
                jnz .Laes_ek_mod4
                movl %r13d, %edi
                rorl $8, %edi                 # RotWord (LE: b1,b2,b3,b0)
                call kof_aes_subword
                movl %eax, %r13d
                movq %r14, %rax
                shrq $3, %rax
                decq %rax
                leaq .Laes_rcon(%rip), %r15
                movzbl (%r15,%rax), %eax
                xorb %al, %r13b
                jmp .Laes_ek_xor
            .Laes_ek_mod4:
                movq %r14, %rax
                andq $7, %rax
                cmpq $4, %rax
                jne .Laes_ek_xor
                movl %r13d, %edi
                call kof_aes_subword
                movl %eax, %r13d
            .Laes_ek_xor:
                movl -32(%r12,%r14,4), %eax
                xorl %r13d, %eax
                movl %eax, (%r12,%r14,4)
                incq %r14
                jmp .Laes_ek_i_loop
            .Laes_ek_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ShiftRows via scratch: (rdi=state, rsp=scratch16)
            kof_aes_shiftrows:
                pushq %rbx
                subq $16, %rsp
                movq %rdi, %r8
                xorq %rcx, %rcx
            .Laes_sr_copy:
                cmpq $16, %rcx
                jge .Laes_sr_build
                movb (%r8,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Laes_sr_copy
            .Laes_sr_build:
                movb 0(%rsp), %al
                movb %al, (%r8)
                movb 5(%rsp), %al
                movb %al, 1(%r8)
                movb 10(%rsp), %al
                movb %al, 2(%r8)
                movb 15(%rsp), %al
                movb %al, 3(%r8)
                movb 4(%rsp), %al
                movb %al, 4(%r8)
                movb 9(%rsp), %al
                movb %al, 5(%r8)
                movb 14(%rsp), %al
                movb %al, 6(%r8)
                movb 3(%rsp), %al
                movb %al, 7(%r8)
                movb 8(%rsp), %al
                movb %al, 8(%r8)
                movb 13(%rsp), %al
                movb %al, 9(%r8)
                movb 2(%rsp), %al
                movb %al, 10(%r8)
                movb 7(%rsp), %al
                movb %al, 11(%r8)
                movb 12(%rsp), %al
                movb %al, 12(%r8)
                movb 1(%rsp), %al
                movb %al, 13(%r8)
                movb 6(%rsp), %al
                movb %al, 14(%r8)
                movb 11(%rsp), %al
                movb %al, 15(%r8)
                addq $16, %rsp
                popq %rbx
                ret

            # InvShiftRows via scratch: (rdi=state, rsp=scratch16)
            kof_aes_invshiftrows:
                pushq %rbx
                subq $16, %rsp
                movq %rdi, %r8
                xorq %rcx, %rcx
            .Laes_isr_copy:
                cmpq $16, %rcx
                jge .Laes_isr_build
                movb (%r8,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Laes_isr_copy
            .Laes_isr_build:
                movb 0(%rsp), %al
                movb %al, (%r8)
                movb 13(%rsp), %al
                movb %al, 1(%r8)
                movb 10(%rsp), %al
                movb %al, 2(%r8)
                movb 7(%rsp), %al
                movb %al, 3(%r8)
                movb 4(%rsp), %al
                movb %al, 4(%r8)
                movb 1(%rsp), %al
                movb %al, 5(%r8)
                movb 14(%rsp), %al
                movb %al, 6(%r8)
                movb 11(%rsp), %al
                movb %al, 7(%r8)
                movb 8(%rsp), %al
                movb %al, 8(%r8)
                movb 5(%rsp), %al
                movb %al, 9(%r8)
                movb 2(%rsp), %al
                movb %al, 10(%r8)
                movb 15(%rsp), %al
                movb %al, 11(%r8)
                movb 12(%rsp), %al
                movb %al, 12(%r8)
                movb 9(%rsp), %al
                movb %al, 13(%r8)
                movb 6(%rsp), %al
                movb %al, 14(%r8)
                movb 3(%rsp), %al
                movb %al, 15(%r8)
                addq $16, %rsp
                popq %rbx
                ret

            # MixColumns: (rdi=state)
            kof_aes_mixcolumns:
                pushq %rbx
                pushq %r12
                movq %rdi, %r12
                xorq %r15, %r15
            .Laes_mc_col:
                cmpq $4, %r15
                jge .Laes_mc_done
                leaq (%r12,%r15,4), %r10
                movzbl 0(%r10), %eax
                movzbl 1(%r10), %ecx
                movzbl 2(%r10), %edx
                movzbl 3(%r10), %esi
                movl %eax, %r8d
                xorl %ecx, %r8d
                xorl %edx, %r8d
                xorl %esi, %r8d           # t
                movzbl %al, %edi
                xorb %cl, %dil
                addb %dil, %dil
                jnc .Laes_m0
                xorb $0x1b, %dil
            .Laes_m0:
                xorl %r8d, %edi
                xorb %dil, 0(%r10)
                movzbl %cl, %edi
                xorb %dl, %dil
                addb %dil, %dil
                jnc .Laes_m1
                xorb $0x1b, %dil
            .Laes_m1:
                xorl %r8d, %edi
                xorb %dil, 1(%r10)
                movzbl %dl, %edi
                xorb %sil, %dil
                addb %dil, %dil
                jnc .Laes_m2
                xorb $0x1b, %dil
            .Laes_m2:
                xorl %r8d, %edi
                xorb %dil, 2(%r10)
                movzbl %sil, %edi
                xorb %al, %dil
                addb %dil, %dil
                jnc .Laes_m3
                xorb $0x1b, %dil
            .Laes_m3:
                xorl %r8d, %edi
                xorb %dil, 3(%r10)
                incq %r15
                jmp .Laes_mc_col
            .Laes_mc_done:
                popq %r12
                popq %rbx
                ret

            # InvMixColumns: (rdi=state) -- mult(0e,·)^mult(0b,·)^mult(0d,·)^mult(09,·)
            kof_aes_invmixcolumns:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %r12
                xorq %r15, %r15
            .Laes_imc_col:
                cmpq $4, %r15
                jge .Laes_imc_done
                leaq (%r12,%r15,4), %r10
                subq $16, %rsp
                movzbl 0(%r10), %eax
                movzbl 1(%r10), %ecx
                movzbl 2(%r10), %edx
                movzbl 3(%r10), %esi
                movb %al, 0(%rsp)
                movb %cl, 1(%rsp)
                movb %dl, 2(%rsp)
                movb %sil, 3(%rsp)
                # n0 = mult(0e,a0) ^ mult(0b,a1) ^ mult(0d,a2) ^ mult(09,a3)
                movzbl 0(%rsp), %edi
                movl $14, %esi
                call kof_aes_mult
                movzbl %al, %r13d
                movzbl 1(%rsp), %edi
                movl $11, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 2(%rsp), %edi
                movl $13, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 3(%rsp), %edi
                movl $9, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movb %r13b, 0(%r10)
                # n1 = mult(09,a0) ^ mult(0e,a1) ^ mult(0b,a2) ^ mult(0d,a3)
                movzbl 0(%rsp), %edi
                movl $9, %esi
                call kof_aes_mult
                movzbl %al, %r13d
                movzbl 1(%rsp), %edi
                movl $14, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 2(%rsp), %edi
            """);
    }
}
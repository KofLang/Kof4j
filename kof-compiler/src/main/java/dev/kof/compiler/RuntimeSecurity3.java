package dev.kof.compiler;

/**
 * Emissão do ASM de security3 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity3 {

    private RuntimeSecurity3() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                xorq %rbx, %rbx          # i
                xorq %r15, %r15          # nbytes saida
                xorq %rcx, %rcx          # acumulador 24 bits
                xorq %r9, %r9            # chars no grupo
            .Lb64d_loop:
                cmpq %r14, %rbx
                jae .Lb64d_end
                movzbl (%r13,%rbx), %eax
                incq %rbx
                cmpb $61, %al            # '=' fim
                je .Lb64d_end
                cmpb $65, %al
                jb .Lb64d_digit
                cmpb $90, %al
                ja .Lb64d_lower
                subb $65, %al
                jmp .Lb64d_got
            .Lb64d_lower:
                cmpb $97, %al
                jb .Lb64d_other
                cmpb $122, %al
                ja .Lb64d_other
                subb $71, %al
                jmp .Lb64d_got
            .Lb64d_digit:
                cmpb $48, %al
                jb .Lb64d_other
                cmpb $57, %al
                ja .Lb64d_other
                addb $4, %al
                jmp .Lb64d_got
            .Lb64d_other:
                cmpb $43, %al
                je .Lb64d_plus
                cmpb $47, %al
                jne .Lb64d_loop
                movb $63, %al
                jmp .Lb64d_got
            .Lb64d_plus:
                movb $62, %al
            .Lb64d_got:
                shlq $6, %rcx
                movzbl %al, %eax
                orq %rax, %rcx
                incq %r9
                cmpq $4, %r9
                jne .Lb64d_loop
                movq %rcx, %rax
                shrq $16, %rax
                movb %al, 0(%r12)
                movq %rcx, %rax
                shrq $8, %rax
                movb %al, 1(%r12)
                movb %cl, 2(%r12)
                addq $3, %r12
                addq $3, %r15
                xorq %rcx, %rcx
                xorq %r9, %r9
                jmp .Lb64d_loop
            .Lb64d_end:
                cmpq $2, %r9
                jb .Lb64d_out
                cmpq $3, %r9
                je .Lb64d_three
                movq %rcx, %rax
                shrq $4, %rax
                movb %al, 0(%r12)
                incq %r12
                incq %r15
                jmp .Lb64d_out
            .Lb64d_three:
                movq %rcx, %rax
                shrq $10, %rax
                movb %al, 0(%r12)
                movq %rcx, %rax
                shrq $2, %rax
                movb %al, 1(%r12)
                addq $2, %r12
                addq $2, %r15
            .Lb64d_out:
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_random_bytes(rdi=buf, rsi=len) -- getrandom syscall
            kof_sec_random_bytes:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %r12
            .Lsec_rand_loop:
                testq %r12, %r12
                jz .Lsec_rand_done
                movq %rbx, %rdi
                movq %r12, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lsec_rand_loop
                addq %rax, %rbx
                subq %rax, %r12
                jmp .Lsec_rand_loop
            .Lsec_rand_done:
                popq %r12
                popq %rbx
                ret

            # ── PBKDF2-HMAC-SHA256 ────────────────────────────────────────
            # kof_sec_hmac_internal(rdi=out32, rsi=k64, rdx=data, rcx=datalen)
            kof_sec_hmac_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # out32
                movq %rsi, %r12          # k64
                movq %rdx, %r13          # data
                movq %rcx, %r14          # datalen
                subq $704, %rsp          # ipad 0, data 64, opad 512, inner 576, outer 608+672
                xorq %r15, %r15
            .Lhmaci_k64:
                cmpq $64, %r15
                jge .Lhmaci_k64_done
                movzbl (%r12,%r15), %eax
                xorl $0x36, %eax
                movb %al, 0(%rsp,%r15)
                movzbl (%r12,%r15), %eax
                xorl $0x5c, %eax
                movb %al, 512(%rsp,%r15)
                incq %r15
                jmp .Lhmaci_k64
            .Lhmaci_k64_done:
                xorq %r15, %r15
            .Lhmaci_datacopy:
                cmpq %r14, %r15
                jge .Lhmaci_datacopy_done
                movb 0(%r13,%r15), %al
                movb %al, 64(%rsp,%r15)
                incq %r15
                jmp .Lhmaci_datacopy
            .Lhmaci_datacopy_done:
                leaq 576(%rsp), %rdi
                leaq 0(%rsp), %rsi
                leaq 64(%rsp,%r14), %rdx
                subq %rsp, %rdx
                call kof_sec_sha256_internal
                xorq %r15, %r15
            .Lhmaci_outeropad:
                cmpq $64, %r15
                jge .Lhmaci_outeropad_done
                movb 512(%rsp,%r15), %al
                movb %al, 608(%rsp,%r15)
                incq %r15
                jmp .Lhmaci_outeropad
            .Lhmaci_outeropad_done:
                xorq %r15, %r15
            .Lhmaci_outerinner:
                cmpq $32, %r15
                jge .Lhmaci_outerinner_done
                movb 576(%rsp,%r15), %al
                movb %al, 672(%rsp,%r15)
                incq %r15
                jmp .Lhmaci_outerinner
            .Lhmaci_outerinner_done:
                movq %rbx, %rdi
                leaq 608(%rsp), %rsi
                movq $96, %rdx
                call kof_sec_sha256_internal
                addq $704, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_pbkdf2_internal(rdi=out32, rsi=password_kstr,
            #                          rdx=salt_ptr, rcx=saltlen, r8=iter)
            kof_sec_pbkdf2_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # out32
                movq %rsi, %r9           # password kstr
                movq %rdx, %r13          # salt ptr
                movq %rcx, %r14          # saltlen
                movq %r8, %r15           # iterations
                subq $288, %rsp          # k64 0, bloco 64, U 132, out 164, scratch 196
                movl 16(%r9), %eax
                cmpl $64, %eax
                jg .Lpbk_khash
                xorq %rcx, %rcx
            .Lpbk_kcopy:
                cmpl %eax, %ecx
                jge .Lpbk_kzero
                movb 24(%r9,%rcx), %dl
                movb %dl, 0(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_kcopy
            .Lpbk_kzero:
                movslq %eax, %rcx
            .Lpbk_kzero_loop:
                cmpq $64, %rcx
                jge .Lpbk_kready
                movb $0, 0(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_kzero_loop
            .Lpbk_khash:
                leaq 196(%rsp), %rdi
                leaq 24(%r9), %rsi
                movslq %eax, %rdx
                call kof_sec_sha256_internal
                xorq %rcx, %rcx
            .Lpbk_khash_copy:
                cmpq $32, %rcx
                jge .Lpbk_khash_zero
                movb 196(%rsp,%rcx), %dl
                movb %dl, 0(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_khash_copy
            .Lpbk_khash_zero:
                cmpq $64, %rcx
                jge .Lpbk_kready
                movb $0, 0(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_khash_zero
            .Lpbk_kready:
                xorq %rcx, %rcx
            .Lpbk_saltcopy:
                cmpq %r14, %rcx
                jge .Lpbk_saltdone
                movb 0(%r13,%rcx), %dl
                movb %dl, 64(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_saltcopy
            .Lpbk_saltdone:
                movb $0, 64(%rsp,%r14)
                movb $0, 65(%rsp,%r14)
                movb $0, 66(%rsp,%r14)
                movb $1, 67(%rsp,%r14)
                xorq %r12, %r12          # iter = 0
                xorq %rcx, %rcx
            .Lpbk_outzero:
                cmpq $32, %rcx
                jge .Lpbk_loop
                movb $0, 164(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_outzero
            .Lpbk_loop:
                cmpq %r15, %r12
                jae .Lpbk_done
                incq %r12
                cmpq $1, %r12
                jne .Lpbk_data_u
                leaq 64(%rsp), %rdx
                leaq 4(%r14), %rcx       # datalen = saltlen + 4 (INT32BE)
                jmp .Lpbk_hmac
            .Lpbk_data_u:
                leaq 132(%rsp), %rdx
                movq $32, %rcx
            .Lpbk_hmac:
                leaq 132(%rsp), %rdi
                leaq 0(%rsp), %rsi
                call kof_sec_hmac_internal
                xorq %rcx, %rcx
            .Lpbk_xor:
                cmpq $32, %rcx
                jge .Lpbk_loop
                movb 132(%rsp,%rcx), %al
                xorb %al, 164(%rsp,%rcx)
                incq %rcx
                jmp .Lpbk_xor
            .Lpbk_done:
                xorq %rcx, %rcx
            .Lpbk_outcopy:
                cmpq $32, %rcx
                jge .Lpbk_outdone
                movb 164(%rsp,%rcx), %al
                movb %al, 0(%rbx,%rcx)
                incq %rcx
                jmp .Lpbk_outcopy
            .Lpbk_outdone:
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_password_hash(rdi=password) → "pbkdf2$sha256$600000$..." 
            .globl kof_sec_password_hash
            .type kof_sec_password_hash, @function
            kof_sec_password_hash:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # password
                subq $128, %rsp          # salt 0, dk 16, saltb64 48, dkb64 73
                movq %rsp, %rdi
                movq $16, %rsi
                call kof_sec_random_bytes
                leaq 16(%rsp), %rdi
                movq %r12, %rsi
                movq %rsp, %rdx
                movq $16, %rcx
                movq $600000, %r8
                call kof_sec_pbkdf2_internal
                leaq 48(%rsp), %rdi
                movq %rsp, %rsi
                movq $16, %rdx
                call kof_b64_encode_internal
                leaq 73(%rsp), %rdi
                leaq 16(%rsp), %rsi
                movq $32, %rdx
                call kof_b64_encode_internal
                movl $91, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $90, 16(%r13)
                movl $0, 20(%r13)
                leaq 24(%r13), %rdi
                leaq .Lsec_pb_prefix(%rip), %rsi
                movq $21, %rcx
            .Lph_pre:
                testq %rcx, %rcx
                jz .Lph_salt
                movb (%rsi), %al
                movb %al, (%rdi)
                incq %rsi
                incq %rdi
                decq %rcx
                jmp .Lph_pre
            .Lph_salt:
                leaq 48(%rsp), %rsi
                movq $24, %rcx
            .Lph_salt_cp:
                testq %rcx, %rcx
                jz .Lph_dollar
                movb (%rsi), %al
                movb %al, (%rdi)
                incq %rsi
                incq %rdi
                decq %rcx
                jmp .Lph_salt_cp
            .Lph_dollar:
                movb $36, (%rdi)
                incq %rdi
                leaq 73(%rsp), %rsi
                movq $44, %rcx
            .Lph_dk_cp:
                testq %rcx, %rcx
                jz .Lph_done
                movb (%rsi), %al
                movb %al, (%rdi)
                incq %rsi
                incq %rdi
                decq %rcx
                jmp .Lph_dk_cp
            .Lph_done:
                movb $0, (%rdi)
                movq %r13, %rax
                addq $128, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_password_verify(rdi=password, rsi=hash) → 1/0
            .globl kof_sec_password_verify
            .type kof_sec_password_verify, @function
            kof_sec_password_verify:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # password
                movq %rsi, %r12          # hash
                testq %r12, %r12
                jz .Lpv_false
                movl 16(%r12), %r13d     # hash len
                cmpl $44, %r13d
                jb .Lpv_false
                leaq 24(%r12), %r14      # hash data
                leaq .Lsec_pb_mid(%rip), %rsi
                xorq %rcx, %rcx
            .Lpv_pre:
                cmpq $14, %rcx
                jae .Lpv_pre_done
                movzbl (%r14,%rcx), %eax
                cmpb (%rsi,%rcx), %al
                jne .Lpv_false
                incq %rcx
                jmp .Lpv_pre
            .Lpv_pre_done:
                leaq 14(%r14), %rsi
                xorq %r15, %r15          # iterations
                xorq %r8, %r8            # digitos
            """);
    }
}
package dev.kof.compiler;

/**
 * Emissão do ASM de security8 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity8 {

    private RuntimeSecurity8() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_gcm_hash(rdi=out16, rsi=data, rdx=len, rcx=H16)
            # GHASH: Y = 0; para cada bloco de 16: X = Y^bloco; Y = mul(X,H)
            kof_gcm_hash:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # out
                movq %rsi, %r12          # data
                movq %rdx, %r13          # len
                movq %rcx, %r14          # H
                subq $80, %rsp           # Y 0..15, bloco 16..31
                xorq %rcx, %rcx
            .Lgch_y0:
                cmpq $16, %rcx
                jge .Lgch_blocks
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lgch_y0
            .Lgch_blocks:
                xorq %r15, %r15
            .Lgch_blocks2:
                cmpq %r13, %r15
                jge .Lgch_done
                # X = Y ^ bloco
                xorq %rcx, %rcx
            .Lgch_x:
                cmpq $16, %rcx
                jge .Lgch_mul
                movzbl (%rsp,%rcx), %eax
                movzbl 0(%r12,%r15), %edx
                xorb %dl, %al
                movb %al, 16(%rsp,%rcx)
                incq %rcx
                jmp .Lgch_x
            .Lgch_mul:
                leaq 16(%rsp), %rdi
                movq %r14, %rsi
                movq %rsp, %rdx
                call kof_gcm_mul
                addq $16, %r15
                jmp .Lgch_blocks2
            .Lgch_done:
                xorq %rcx, %rcx
            .Lgch_out:
                cmpq $16, %rcx
                jge .Lgch_ret
                movb (%rsp,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Lgch_out
            .Lgch_ret:
                addq $80, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_gcm_finish(rdi=S16, rsi=aad_bits, rdx=ct_bits, rcx=H16)
            # S = GHASH(S || aad_len || ct_len)
            kof_gcm_finish:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # S
                movq %rsi, %r12          # aad bits
                movq %rdx, %r13          # ct bits
                movq %rcx, %r14          # H
                subq $64, %rsp           # bloco 0..15, Y 16..31
                movq %r12, %rax
                bswapq %rax
                movq %rax, 0(%rsp)
                movq %r13, %rax
                bswapq %rax
                movq %rax, 8(%rsp)
                xorq %rcx, %rcx
            .Lgcf_x:
                cmpq $16, %rcx
                jge .Lgcf_mul
                movzbl (%rbx,%rcx), %eax
                xorb 0(%rsp,%rcx), %al
                movb %al, 0(%rsp,%rcx)
                incq %rcx
                jmp .Lgcf_x
            .Lgcf_mul:
                movq %rsp, %rdi
                movq %r14, %rsi
                leaq 16(%rsp), %rdx
                call kof_gcm_mul
                xorq %rcx, %rcx
            .Lgcf_out:
                cmpq $16, %rcx
                jge .Lgcf_ret
                movb 16(%rsp,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Lgcf_out
            .Lgcf_ret:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

# kof_sec_aesgcm_encrypt(rdi=plaintext, rsi=keyHex) → "aesgcm$iv$ct"
            .globl kof_sec_aesgcm_encrypt
            .type kof_sec_aesgcm_encrypt, @function
            kof_sec_aesgcm_encrypt:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # plaintext
                movq %rsi, %r12          # keyHex
                subq $1500, %rsp
                # key bytes
                movq %r12, %rdi
                call kof_sec_hex_decode
                movq %rax, %r13
                cmpl $32, 16(%r13)
                jne .Laesgcm_badkey
                xorq %rcx, %rcx
            .Laesgcm_keycp:
                cmpq $32, %rcx
                jge .Laesgcm_expand
                movb 24(%r13,%rcx), %al
                movb %al, 0(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_keycp
            .Laesgcm_expand:
                leaq 0(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_expand_key
                # H = AES(0^128) → 272
                xorq %rcx, %rcx
            .Laesgcm_h0:
                cmpq $16, %rcx
                jge .Laesgcm_h_enc
                movb $0, 272(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_h0
            .Laesgcm_h_enc:
                leaq 272(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                # iv = random 12 → 288
                leaq 288(%rsp), %rdi
                movq $12, %rsi
                call kof_sec_random_bytes
                # J0 = iv || 00000001 → 300
                xorq %rcx, %rcx
            .Laesgcm_j0:
                cmpq $12, %rcx
                jge .Laesgcm_j0done
                movb 288(%rsp,%rcx), %al
                movb %al, 300(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_j0
            .Laesgcm_j0done:
                movb $0, 312(%rsp)
                movb $0, 313(%rsp)
                movb $0, 314(%rsp)
                movb $1, 315(%rsp)
                # counter = J0
                xorq %rcx, %rcx
            .Laesgcm_ctr_init:
                xorq %rcx, %rcx
            .Laesgcm_ctr_init_l:
                cmpq $16, %rcx
                jge .Laesgcm_ctr_ready
                movb 300(%rsp,%rcx), %al
                movb %al, 316(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_ctr_init_l
            .Laesgcm_ctr_ready:
                xorq %r14, %r14
            .Laesgcm_ctr_loop:
                movl 16(%rbx), %r13d
                cmpl %r13d, %r14d
                jge .Laesgcm_ctr_done
                movl 328(%rsp), %eax
                bswapl %eax
                incl %eax
                bswapl %eax
                movl %eax, 328(%rsp)
                xorq %rcx, %rcx
            .Laesgcm_kscopy:
                cmpq $16, %rcx
                jge .Laesgcm_ksenc
                movb 316(%rsp,%rcx), %al
                movb %al, 364(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_kscopy
            .Laesgcm_ksenc:
                leaq 364(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                movl %r13d, %eax
                subl %r14d, %eax
                cmpl $16, %eax
                jl .Laesgcm_partial
                movl $16, %eax
            .Laesgcm_partial:
                xorq %rcx, %rcx
            .Laesgcm_xor:
                cmpl %eax, %ecx
                jge .Laesgcm_ctr_next
                movzbl 24(%rbx,%r14), %edx
                xorb 364(%rsp,%rcx), %dl
                movb %dl, 348(%rsp,%r14)
                incq %rcx
                incq %r14
                jmp .Laesgcm_xor
            .Laesgcm_ctr_next:
                jmp .Laesgcm_ctr_loop
            .Laesgcm_ctr_done:
                # GHASH(C)
                leaq 860(%rsp), %rdi
                leaq 348(%rsp), %rsi
                movslq %r13d, %rdx
                leaq 272(%rsp), %rcx
                call kof_gcm_hash
                movl %r13d, %eax
                shll $3, %eax
                movslq %eax, %rdx
                xorl %esi, %esi
                leaq 272(%rsp), %rcx
                leaq 860(%rsp), %rdi
                call kof_gcm_finish
                # tag = AES(J0) ^ S → 876
                leaq 300(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                xorq %rcx, %rcx
            .Laesgcm_tag:
                cmpq $16, %rcx
                jge .Laesgcm_tagcp
                movzbl 860(%rsp,%rcx), %eax
                xorb 300(%rsp,%rcx), %al
                movb %al, 876(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_tag
            .Laesgcm_tagcp:
                movl %r13d, %eax
                leaq 348(%rsp,%rax), %rdi
                leaq 876(%rsp), %rsi
                movq $16, %rcx
            .Laesgcm_tc:
                testq %rcx, %rcx
                jz .Laesgcm_enc
                movb (%rsi), %al
                movb %al, (%rdi)
                incq %rsi
                incq %rdi
                decq %rcx
                jmp .Laesgcm_tc
            .Laesgcm_enc:
                leaq 920(%rsp), %rdi
                leaq 288(%rsp), %rsi
                movq $12, %rdx
                call kof_b64_encode_internal
                movq %rax, %r14          # iv b64 len (16)
                leaq 940(%rsp), %rdi
                leaq 348(%rsp), %rsi
                movl %r13d, %eax
                addl $16, %eax
                cltq
                movq %rax, %rdx
                call kof_b64_encode_internal
                movq %rax, %r15          # ct b64 len
                # total = 7 + 16 + 1 + ctb64
                leaq 7(%r14,%r15), %rax
                movl %eax, %r13d
                leaq 25(%rax), %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r13d, 16(%rbx)
                movl $0, 20(%rbx)
                leaq .Lstr_aesgcm(%rip), %rsi
                movq $7, %rcx
                xorq %r8, %r8
            .Laesgcm_pfx:
                testq %rcx, %rcx
                jz .Laesgcm_iv
                movb (%rsi), %al
                movb %al, 24(%rbx,%r8)
                incq %rsi
                incq %r8
                decq %rcx
                jmp .Laesgcm_pfx
            .Laesgcm_iv:
                xorq %rcx, %rcx
            .Laesgcm_ivcp:
                cmpq %r14, %rcx
                jge .Laesgcm_dollar
                movb 920(%rsp,%rcx), %al
                movb %al, 24(%rbx,%r8)
                incq %r8
                incq %rcx
                jmp .Laesgcm_ivcp
            .Laesgcm_dollar:
                movb $36, 24(%rbx,%r8)
                incq %r8
                xorq %rcx, %rcx
            .Laesgcm_ctcp:
                cmpq %r15, %rcx
                jge .Laesgcm_nul
                movb 940(%rsp,%rcx), %al
                movb %al, 24(%rbx,%r8)
                incq %r8
                incq %rcx
                jmp .Laesgcm_ctcp
            .Laesgcm_nul:
                movb $0, 24(%rbx,%r8)
                movq %rbx, %rax
                addq $1500, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Laesgcm_badkey:
                leaq .Lstr_jwt_invalid(%rip), %rdi
                movq $13, %rsi
                call kof_jwt_fail

            # kof_sec_aesgcm_decrypt(rdi=ciphertext, rsi=keyHex) → plaintext
            .globl kof_sec_aesgcm_decrypt
            .type kof_sec_aesgcm_decrypt, @function
            kof_sec_aesgcm_decrypt:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # ciphertext (KofString)
                movq %rsi, %r12          # keyHex
                subq $1500, %rsp
                # key bytes
                movq %r12, %rdi
                call kof_sec_hex_decode
                movq %rax, %r13
                cmpl $32, 16(%r13)
                jne .Laesgcm_badkey
                xorq %rcx, %rcx
            .Laesgcm_dkey:
                cmpq $32, %rcx
                jge .Laesgcm_dexpand
                movb 24(%r13,%rcx), %al
                movb %al, 0(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dkey
            .Laesgcm_dexpand:
                leaq 0(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_expand_key
                xorq %rcx, %rcx
            .Laesgcm_dh0:
                cmpq $16, %rcx
                jge .Laesgcm_dh_enc
                movb $0, 272(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dh0
            .Laesgcm_dh_enc:
                leaq 272(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                # parse "aesgcm$iv$ct"
                movl 16(%rbx), %eax
                cmpl $20, %eax
                jl .Laesgcm_badct
                leaq 24(%rbx), %r8
                cmpb $97, (%r8)
                jne .Laesgcm_badct
                cmpb $36, 6(%r8)
                jne .Laesgcm_badct
                # iv b64: 7..dollar2
                leaq 7(%r8), %r9          # iv start
                xorq %r10, %r10           # iv b64 len
            .Laesgcm_div:
                cmpq %rax, %r10
                jge .Laesgcm_badct
                cmpb $36, 0(%r9,%r10)
                je .Laesgcm_divdone
                incq %r10
                jmp .Laesgcm_div
            .Laesgcm_divdone:
                # decode iv → 288 (r9/r10 sao clobbered pelo decode)
                movq %r9, %r15
                movq %r10, %r13
                leaq 288(%rsp), %rdi
                movq %r15, %rsi
                movq %r13, %rdx
                call kof_b64_decode_internal
                cmpq $12, %rax
                jne .Laesgcm_badct
                # ct b64: apos o $ → 940
                leaq 1(%r15,%r13), %rsi
                movq %rsi, %r9
                movl 16(%rbx), %eax
            """);
    }
}
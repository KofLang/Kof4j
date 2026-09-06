package dev.kof.compiler;

/**
 * Emissão do ASM de security2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity2 {

    private RuntimeSecurity2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                addq %r12, 32(%rdx)
                addq %r13, 40(%rdx)
                addq %r14, 48(%rdx)
                addq %r15, 56(%rdx)
                addq $680, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha512_internal(rdi=out64, rsi=src, rdx=len)
            kof_sec_sha512_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                subq $560, %rsp          # h[8]=64 + bloco final 256 + reserva
                movabs $0x6a09e667f3bcc908, %rax
                movq %rax, 0(%rsp)
                movabs $0xbb67ae8584caa73b, %rax
                movq %rax, 8(%rsp)
                movabs $0x3c6ef372fe94f82b, %rax
                movq %rax, 16(%rsp)
                movabs $0xa54ff53a5f1d36f1, %rax
                movq %rax, 24(%rsp)
                movabs $0x510e527fade682d1, %rax
                movq %rax, 32(%rsp)
                movabs $0x9b05688c2b3e6c1f, %rax
                movq %rax, 40(%rsp)
                movabs $0x1f83d9abfb41bd6b, %rax
                movq %rax, 48(%rsp)
                movabs $0x5be0cd19137e2179, %rax
                movq %rax, 56(%rsp)
                xorq %r15, %r15          # offset
            .Ls512_full:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $128, %rax
                jl .Ls512_final
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                call kof_sec_sha512_block
                addq $128, %r15
                jmp .Ls512_full
            .Ls512_final:
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx          # rem
                subq $256, %rsp          # bloco final (2 x 128)
                xorq %rdx, %rdx
            .Ls512_copy:
                cmpq %rcx, %rdx
                jge .Ls512_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rsp,%rdx)
                incq %rdx
                jmp .Ls512_copy
            .Ls512_copy_done:
                movb $0x80, (%rsp,%rcx)
                movq %rcx, %r15          # rem
                leaq 1(%rcx), %rdx
            .Ls512_zeropad:
                cmpq $256, %rdx
                jge .Ls512_zeropad_done
                movb $0, (%rsp,%rdx)
                incq %rdx
                jmp .Ls512_zeropad
            .Ls512_zeropad_done:
                movq %r15, %rax
                addq $9, %rax
                cmpq $128, %rax
                jg .Ls512_len_second
                movq $0, 112(%rsp)
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 120(%rsp)
                leaq 256(%rsp), %rdi
                movq %rsp, %rsi
                call kof_sec_sha512_block
                jmp .Ls512_final_done
            .Ls512_len_second:
                movq $0, 240(%rsp)
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 248(%rsp)
                leaq 256(%rsp), %rdi
                movq %rsp, %rsi
                call kof_sec_sha512_block
                leaq 256(%rsp), %rdi
                leaq 128(%rsp), %rsi
                call kof_sec_sha512_block
            .Ls512_final_done:
                addq $256, %rsp
            .Ls512_out:
                xorq %rcx, %rcx
            .Ls512_out_loop:
                cmpq $8, %rcx
                jge .Ls512_ret
                movq (%rsp,%rcx,8), %rax
                bswapq %rax
                movq %rax, (%r12,%rcx,8)
                incq %rcx
                jmp .Ls512_out_loop
            .Ls512_ret:
                addq $560, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .text

            # kof_sec_sha512(rdi=src_kstr) → hex string (128 chars)
            .globl kof_sec_sha512
            .type kof_sec_sha512, @function
            kof_sec_sha512:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                subq $64, %rsp
                movq %rsp, %rdi
                leaq 24(%rbx), %rsi
                movslq %r12d, %rdx
                call kof_sec_sha512_internal
                movl $153, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $128, 16(%r13)
                movl $0, 20(%r13)
                xorq %rcx, %rcx
            .Ls512_hex:
                cmpq $64, %rcx
                jge .Ls512_hex_done
                movzbl (%rsp,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r13,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r13,%rcx,2)
                incq %rcx
                jmp .Ls512_hex
            .Ls512_hex_done:
                movb $0, 152(%r13)
                movq %r13, %rax
                addq $64, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_hmac_sha256(rdi=key, rsi=data) → hex string
            # HMAC-SHA256: H((K^opad) || H((K^ipad) || data)) with K padded to 64
            .globl kof_sec_hmac_sha256
            .type kof_sec_hmac_sha256, @function
            kof_sec_hmac_sha256:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # key
                movq %rsi, %r12          # data
                subq $576, %rsp          # k64(64) + inner(64+datalen up to 448) + out(32) + scratch
                movq %rbx, %r13
                movl 16(%rbx), %r13d     # key len
                # build k64 in 0..63(%rsp): key bytes (or hash if keylen>64)
                cmpl $64, %r13d
                jg .Lsec_hmac_key_hash
                xorq %rcx, %rcx
            .Lsec_hmac_key_copy:
                cmpl %r13d, %ecx
                jge .Lsec_hmac_key_copy_done
                movb 24(%rbx,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_copy
            .Lsec_hmac_key_copy_done:
                movq %r13, %rcx
            .Lsec_hmac_key_zero:
                cmpq $64, %rcx
                jge .Lsec_hmac_key_done
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_zero
            .Lsec_hmac_key_done:
                jmp .Lsec_hmac_key_ready
            .Lsec_hmac_key_hash:
                leaq 512(%rsp), %rdi     # out
                movq %rbx, %rsi
                addq $24, %rsi
                movslq %r13d, %rdx
                call kof_sec_sha256_internal
                xorq %rcx, %rcx
            .Lsec_hmac_key_hash_copy:
                cmpq $32, %rcx
                jge .Lsec_hmac_key_hash_done
                movb 512(%rsp,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_hash_copy
            .Lsec_hmac_key_hash_done:
                movq $32, %rcx
            .Lsec_hmac_key_hash_zero:
                cmpq $64, %rcx
                jge .Lsec_hmac_key_ready
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lsec_hmac_key_hash_zero
            .Lsec_hmac_key_ready:
                # inner input: ipad(64) at 64(%rsp) + data at 128(%rsp)
                movq %r12, %r14
                movl 16(%r12), %r14d     # data len
                movl $63, %ecx
            .Lsec_hmac_ipad:
                movb (%rsp,%rcx), %al
                xorb $0x36, %al
                movb %al, 64(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_ipad
            .Lsec_hmac_ipad_done:
                movq %r14, %rcx
                decq %rcx
            .Lsec_hmac_data_copy:
                testq %rcx, %rcx
                js .Lsec_hmac_data_copy_done
                movb 24(%r12,%rcx), %al
                movb %al, 128(%rsp,%rcx)
                decq %rcx
                jmp .Lsec_hmac_data_copy
            .Lsec_hmac_data_copy_done:
                # inner = sha256(64+data at 64(%rsp)) → 544(%rsp)
                leaq 544(%rsp), %rdi
                leaq 64(%rsp), %rsi
                movq %r14, %rdx
                addq $64, %rdx
                call kof_sec_sha256_internal
                # outer input: opad(64) + inner(32) → 64(%rsp)
                movl $63, %ecx
            .Lsec_hmac_opad:
                movb (%rsp,%rcx), %al
                xorb $0x5c, %al
                movb %al, 64(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_opad
            .Lsec_hmac_opad_done:
                movl $31, %ecx
            .Lsec_hmac_outer_copy:
                movb 544(%rsp,%rcx), %al
                movb %al, 128(%rsp,%rcx)
                decq %rcx
                jns .Lsec_hmac_outer_copy
            .Lsec_hmac_outer_done:
                # mac = sha256(64+32 at 64(%rsp)) → 512(%rsp)
                leaq 512(%rsp), %rdi
                leaq 64(%rsp), %rsi
                movq $96, %rdx
                call kof_sec_sha256_internal
                # build hex string (24 + 64 + 1)
                movl $89, %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl $64, 16(%r15)
                movl $0, 20(%r15)
                xorq %rcx, %rcx
            .Lsec_hmac_hex:
                cmpq $32, %rcx
                jge .Lsec_hmac_hex_done
                movzbl 512(%rsp,%rcx), %eax
                movl %eax, %edx
                shrb $4, %al
                andb $0x0f, %dl
                leaq .Lsec_hex_chars(%rip), %r14
                movb (%r14,%rax), %al
                movb %al, 24(%r15,%rcx,2)
                movb (%r14,%rdx), %al
                movb %al, 25(%r15,%rcx,2)
                incq %rcx
                jmp .Lsec_hmac_hex
            .Lsec_hmac_hex_done:
                movb $0, 88(%r15)
                movq %r15, %rax
                addq $576, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── base64 (RFC 4648, com padding) ────────────────────────────
            # kof_b64_encode_internal(rdi=out, rsi=src, rdx=len)
            # escreve 4*((len+2)/3) bytes + \0 (padding '=')
            kof_b64_encode_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                xorq %r15, %r15          # i
                leaq .Lsec_b64_chars(%rip), %rbx
            .Lb64_loop:
                cmpq %r14, %r15
                jae .Lb64_pad
                leaq 1(%r15), %rdx
                cmpq %r14, %rdx
                jae .Lb64_one
                leaq 2(%r15), %rcx
                cmpq %r14, %rcx
                jae .Lb64_two
                # 3 bytes completos
                movzbl (%r13,%r15), %eax
                shll $16, %eax
                movzbl (%r13,%rdx), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl (%r13,%rcx), %edx
                orl %edx, %eax
                movl %eax, %edx
                shrl $18, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                movl %edx, %eax
                shrl $12, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 1(%r12)
                movl %edx, %eax
                shrl $6, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 2(%r12)
                andl $63, %edx
                movb (%rbx,%rdx), %al
                movb %al, 3(%r12)
                addq $4, %r12
                addq $3, %r15
                jmp .Lb64_loop
            .Lb64_two:
                # 2 bytes: 3 chars + '='
                movzbl (%r13,%r15), %eax
                shll $8, %eax
                movzbl (%r13,%rdx), %edx
                orl %edx, %eax
                movl %eax, %edx
                shrl $10, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                movl %edx, %eax
                shrl $4, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 1(%r12)
                movl %edx, %eax
                shll $2, %eax
                andl $63, %eax
                movb (%rbx,%rax), %al
                movb %al, 2(%r12)
                movb $61, 3(%r12)       # '='
                addq $4, %r12
                addq $2, %r15
                jmp .Lb64_loop
            .Lb64_one:
                # 1 byte: 2 chars + '=='
                movzbl (%r13,%r15), %eax
                movl %eax, %edx
                shrl $2, %eax
                movb (%rbx,%rax), %al
                movb %al, 0(%r12)
                andl $3, %edx
                shll $4, %edx
                movb (%rbx,%rdx), %al
                movb %al, 1(%r12)
                movb $61, 2(%r12)
                movb $61, 3(%r12)
                addq $4, %r12
                addq $1, %r15
                jmp .Lb64_loop
            .Lb64_pad:
                movb $0, 0(%r12)
                movq %r12, %rax
                subq %rdi, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_b64_decode_internal(rdi=out, rsi=src, rdx=len) → nbytes
            # decodifica base64 (ignora '=' e inválidos), escreve em out
            kof_b64_decode_internal:
                pushq %rbx
                pushq %r12
            """);
    }
}
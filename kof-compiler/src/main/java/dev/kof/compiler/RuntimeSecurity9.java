package dev.kof.compiler;

/**
 * Emissão do ASM de security9 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity9 {

    private RuntimeSecurity9() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                leaq 24(%rbx), %rcx
                addq %rcx, %rax
                subq %r9, %rax            # ct b64 len
                leaq 940(%rsp), %rdi
                movq %r9, %rsi
                movq %rax, %rdx
                call kof_b64_decode_internal
                movq %rax, %r13           # ct bytes (ct + tag)
                movl %r13d, %r14d
                subl $16, %r14d           # ct sem a tag
                cmpl $1, %r14d
                jl .Laesgcm_badct
                # J0 = iv || 1
                xorq %rcx, %rcx
            .Laesgcm_dj0:
                cmpq $12, %rcx
                jge .Laesgcm_dj0done
                movb 288(%rsp,%rcx), %al
                movb %al, 300(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dj0
            .Laesgcm_dj0done:
                movb $0, 312(%rsp)
                movb $0, 313(%rsp)
                movb $0, 314(%rsp)
                movb $1, 315(%rsp)
                # CTR decrypt (ct em 940, plain em 348)
                xorq %rcx, %rcx
            .Laesgcm_dctr_init:
                xorq %rcx, %rcx
            .Laesgcm_dctr_init_l:
                cmpq $16, %rcx
                jge .Laesgcm_dctr_ready
                movb 300(%rsp,%rcx), %al
                movb %al, 316(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dctr_init_l
            .Laesgcm_dctr_ready:
                xorq %r15, %r15
            .Laesgcm_dctr_loop:
                cmpl %r14d, %r15d
                
cmpl %r14d, %r15d
                jge .Laesgcm_dctr_done
                movl 328(%rsp), %eax
                bswapl %eax
                incl %eax
                bswapl %eax
                movl %eax, 328(%rsp)
                xorq %rcx, %rcx
            .Laesgcm_dkscopy:
                cmpq $16, %rcx
                jge .Laesgcm_dksenc
                movb 316(%rsp,%rcx), %al
                movb %al, 364(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dkscopy
            .Laesgcm_dksenc:
                leaq 364(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                movl %r14d, %eax
                subl %r15d, %eax
                cmpl $16, %eax
                jl .Laesgcm_dpartial
                movl $16, %eax
            .Laesgcm_dpartial:
                xorq %rcx, %rcx
            .Laesgcm_dxor:
                cmpl %eax, %ecx
                jge .Laesgcm_dctr_next
                movzbl 940(%rsp,%r15), %edx
                xorb 364(%rsp,%rcx), %dl
                movb %dl, 348(%rsp,%r15)
                incq %rcx
                incq %r15
                jmp .Laesgcm_dxor
            .Laesgcm_dctr_next:
                jmp .Laesgcm_dctr_loop
            .Laesgcm_dctr_done:
                # GHASH(C sem tag)
                leaq 860(%rsp), %rdi
                leaq 940(%rsp), %rsi
                movslq %r14d, %rdx
                leaq 272(%rsp), %rcx
                call kof_gcm_hash
                movl %r14d, %eax
                shll $3, %eax
                movslq %eax, %rdx
                xorl %esi, %esi
                leaq 272(%rsp), %rcx
                leaq 860(%rsp), %rdi
                call kof_gcm_finish
                leaq 300(%rsp), %rdi
                leaq 32(%rsp), %rsi
                call kof_aes256_encrypt_block
                # tag esperada = AES(J0) ^ S → 876
                xorq %rcx, %rcx
            .Laesgcm_dtag:
                cmpq $16, %rcx
                jge .Laesgcm_dtagcmp
                movzbl 860(%rsp,%rcx), %eax
                xorb 300(%rsp,%rcx), %al
                movb %al, 876(%rsp,%rcx)
                incq %rcx
                jmp .Laesgcm_dtag
            .Laesgcm_dtagcmp:
                # compare com a tag do input (940 + ct_len)
                movl %r14d, %eax
                leaq 940(%rsp,%rax), %rsi
                xorq %rcx, %rcx
                xorl %r11d, %r11d
            .Laesgcm_dcmp:
                cmpq $16, %rcx
                jge .Laesgcm_dcmpdone
                movzbl 876(%rsp,%rcx), %eax
                movzbl (%rsi,%rcx), %edx
                xorl %edx, %eax
                orl %eax, %r11d
                incq %rcx
                jmp .Laesgcm_dcmp
            .Laesgcm_dcmpdone:
                testl %r11d, %r11d
                jnz .Laesgcm_badct
                # resultado: plaintext (348, len r14d)
                movl %r14d, %eax
                leaq 25(%rax), %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r14d, 16(%rbx)
                movl $0, 20(%rbx)
                xorq %rcx, %rcx
            .Laesgcm_dout:
                cmpl %r14d, %ecx
                jge .Laesgcm_doutdone
                movb 348(%rsp,%rcx), %al
                movb %al, 24(%rbx,%rcx)
                incq %rcx
                jmp .Laesgcm_dout
            .Laesgcm_doutdone:
                movb $0, 24(%rbx,%r14)
                movq %rbx, %rax
                addq $1500, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Laesgcm_badct:
                leaq .Lstr_jwt_invalid(%rip), %rdi
                movq $13, %rsi
                call kof_jwt_fail

            # ── kof.validation (G4) ──────────────────────────────────────
            # kof_validation_required(rdi=str) -> 1/0
            """);
    }
}
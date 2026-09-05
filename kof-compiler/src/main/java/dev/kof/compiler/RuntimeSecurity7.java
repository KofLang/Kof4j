package dev.kof.compiler;

/**
 * Emissão do ASM de security7 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity7 {

    private RuntimeSecurity7() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                movl $11, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 3(%rsp), %edi
                movl $13, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movb %r13b, 1(%r10)
                # n2 = mult(0d,a0) ^ mult(09,a1) ^ mult(0e,a2) ^ mult(0b,a3)
                movzbl 0(%rsp), %edi
                movl $13, %esi
                call kof_aes_mult
                movzbl %al, %r13d
                movzbl 1(%rsp), %edi
                movl $9, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 2(%rsp), %edi
                movl $14, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 3(%rsp), %edi
                movl $11, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movb %r13b, 2(%r10)
                # n3 = mult(0b,a0) ^ mult(0d,a1) ^ mult(09,a2) ^ mult(0e,a3)
                movzbl 0(%rsp), %edi
                movl $11, %esi
                call kof_aes_mult
                movzbl %al, %r13d
                movzbl 1(%rsp), %edi
                movl $13, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 2(%rsp), %edi
                movl $9, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movzbl 3(%rsp), %edi
                movl $14, %esi
                call kof_aes_mult
                xorl %eax, %r13d
                movb %r13b, 3(%r10)
                addq $16, %rsp
                incq %r15
                jmp .Laes_imc_col
            .Laes_imc_done:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_aes256_encrypt_block(rdi=state16, rsi=expanded240)
            kof_aes256_encrypt_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                xorq %rcx, %rcx
            .Laes_en_ark0:
                cmpq $16, %rcx
                jge .Laes_en_round
                movzbl (%rbx,%rcx), %eax
                xorb (%r12,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_en_ark0
            .Laes_en_round:
                movq $1, %r13
            .Laes_en_round_loop:
                cmpq $14, %r13
                jge .Laes_en_last
                leaq .Laes_sbox(%rip), %r14
                xorq %rcx, %rcx
            .Laes_en_sub:
                cmpq $16, %rcx
                jge .Laes_en_shift
                movzbl (%rbx,%rcx), %eax
                movb (%r14,%rax), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_en_sub
            .Laes_en_shift:
                movq %rbx, %rdi
                call kof_aes_shiftrows
                movq %rbx, %rdi
                call kof_aes_mixcolumns
                movq %r13, %rax
                shlq $4, %rax
                leaq (%r12,%rax), %rsi
                xorq %rcx, %rcx
            .Laes_en_ark:
                cmpq $16, %rcx
                jge .Laes_en_next
                movzbl (%rbx,%rcx), %eax
                xorb (%rsi,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_en_ark
            .Laes_en_next:
                incq %r13
                jmp .Laes_en_round_loop
            .Laes_en_last:
                leaq .Laes_sbox(%rip), %r14
                xorq %rcx, %rcx
            .Laes_en_lsub:
                cmpq $16, %rcx
                jge .Laes_en_lshift
                movzbl (%rbx,%rcx), %eax
                movb (%r14,%rax), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_en_lsub
            .Laes_en_lshift:
                movq %rbx, %rdi
                call kof_aes_shiftrows
                leaq 224(%r12), %rsi
                xorq %rcx, %rcx
            .Laes_en_lark:
                cmpq $16, %rcx
                jge .Laes_en_done
                movzbl (%rbx,%rcx), %eax
                xorb (%rsi,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_en_lark
            .Laes_en_done:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_aes256_decrypt_block(rdi=state16, rsi=expanded240)
            kof_aes256_decrypt_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                leaq 224(%r12), %rsi
                xorq %rcx, %rcx
            .Laes_de_ark14:
                cmpq $16, %rcx
                jge .Laes_de_round
                movzbl (%rbx,%rcx), %eax
                xorb (%rsi,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_de_ark14
            .Laes_de_round:
                movq $13, %r13
            .Laes_de_round_loop:
                cmpq $0, %r13
                jle .Laes_de_last
                movq %rbx, %rdi
                call kof_aes_invshiftrows
                leaq .Laes_sbox_inv(%rip), %r14
                xorq %rcx, %rcx
            .Laes_de_isub:
                cmpq $16, %rcx
                jge .Laes_de_ark
                movzbl (%rbx,%rcx), %eax
                movb (%r14,%rax), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_de_isub
            .Laes_de_ark:
                movq %r13, %rax
                shlq $4, %rax
                leaq (%r12,%rax), %rsi
                xorq %rcx, %rcx
            .Laes_de_arkn:
                cmpq $16, %rcx
                jge .Laes_de_imix
                movzbl (%rbx,%rcx), %eax
                xorb (%rsi,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_de_arkn
            .Laes_de_imix:
                movq %rbx, %rdi
                call kof_aes_invmixcolumns
                decq %r13
                jmp .Laes_de_round_loop
            .Laes_de_last:
                movq %rbx, %rdi
                call kof_aes_invshiftrows
                leaq .Laes_sbox_inv(%rip), %r14
                xorq %rcx, %rcx
            .Laes_de_lsub:
                cmpq $16, %rcx
                jge .Laes_de_lark
                movzbl (%rbx,%rcx), %eax
                movb (%r14,%rax), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_de_lsub
            .Laes_de_lark:
                xorq %rcx, %rcx
            .Laes_de_larkn:
                cmpq $16, %rcx
                jge .Laes_de_done
                movzbl (%rbx,%rcx), %eax
                xorb (%r12,%rcx), %al
                movb %al, (%rbx,%rcx)
                incq %rcx
                jmp .Laes_de_larkn
            .Laes_de_done:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

# kof_sec_hex_decode(rdi=hex_kstr) → KofString* com os bytes (metade do len)
            kof_sec_hex_decode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                movl %r12d, %eax
                shrl $1, %eax
                movl %eax, %r13d
                leaq 25(%rax), %rdi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                xorq %rcx, %rcx          # i (hex chars)
                xorq %r15, %r15          # out bytes
            .Lhexd_loop:
                cmpl %r12d, %ecx
                jge .Lhexd_done
                movzbl 24(%rbx,%rcx), %eax
                cmpb $97, %al            # 'a'
                jb .Lhexd_upper
                subb $87, %al            # a-f -> 10-15
                jmp .Lhexd_got
            .Lhexd_upper:
                cmpb $65, %al
                jb .Lhexd_digit
                subb $55, %al            # A-F -> 10-15
                jmp .Lhexd_got
            .Lhexd_digit:
                subb $48, %al
            .Lhexd_got:
                shll $4, %eax
                movzbl 25(%rbx,%rcx), %edx
                cmpb $97, %dl
                jb .Lhexd_upper2
                subb $87, %dl
                jmp .Lhexd_got2
            .Lhexd_upper2:
                cmpb $65, %dl
                jb .Lhexd_digit2
                subb $55, %dl
                jmp .Lhexd_got2
            .Lhexd_digit2:
                subb $48, %dl
            .Lhexd_got2:
                orl %edx, %eax
                movb %al, 24(%r14,%r15)
                incq %r15
                addq $2, %rcx
                jmp .Lhexd_loop
            .Lhexd_done:
                movb $0, 24(%r14,%r15)
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── GCM (NIST SP 800-38D) ────────────────────────────────────
            # kof_gcm_mul(rdi=X[16], rsi=H[16], rdx=out[16])
            # multiplicacao em GF(2^128), polinomio x^128+x^7+x^2+x+1
            kof_gcm_mul:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # X
                movq %rsi, %r12          # H
                movq %rdx, %r13          # out
                subq $64, %rsp           # Z 0..15, V 16..31
                xorq %rcx, %rcx
            .Lgcm_z0:
                cmpq $16, %rcx
                jge .Lgcm_vcopy
                movb $0, (%rsp,%rcx)
                incq %rcx
                jmp .Lgcm_z0
            .Lgcm_vcopy:
                xorq %rcx, %rcx
            .Lgcm_vcopy_l:
                cmpq $16, %rcx
                jge .Lgcm_bit
                movb (%r12,%rcx), %al
                movb %al, 16(%rsp,%rcx)
                incq %rcx
                jmp .Lgcm_vcopy_l
            .Lgcm_bit:
                xorq %r14, %r14          # bit index (0..127)
            .Lgcm_bit_loop:
                cmpq $128, %r14
                jge .Lgcm_done
                # bit (127 - r14) de X: byte = r14/8, bit = 7 - (r14%8)
                movq %r14, %rax
                xorq %rdx, %rdx
                movq $8, %rcx
                divq %rcx                 # rax = byte, rdx = bit
                movzbl (%rbx,%rax), %ecx
                movq %rdx, %r15
                movb $0x80, %dl
                movq %r15, %r8
            .Lgcm_shl:
                testq %r8, %r8
                jz .Lgcm_shl_done
                shrb %dl
                decq %r8
                jmp .Lgcm_shl
            .Lgcm_shl_done:
                testb %dl, %cl
                jz .Lgcm_vshift
                # Z ^= V
                xorq %rcx, %rcx
            .Lgcm_zxor:
                cmpq $16, %rcx
                jge .Lgcm_vshift
                movb (%rsp,%rcx), %al
                xorb 16(%rsp,%rcx), %al
                movb %al, (%rsp,%rcx)
                incq %rcx
                jmp .Lgcm_zxor
            .Lgcm_vshift:
                # if V & 1: V = (V >> 1) ^ 0xE1 (byte 15)
                movb 31(%rsp), %al
                testb $1, %al
                jz .Lgcm_vshift_plain
                xorq %rcx, %rcx
            .Lgcm_vshift_x:
                movq $15, %rcx
            .Lgcm_vshift_x_loop:
                cmpq $1, %rcx
                jl .Lgcm_vshift_x0
                movzbl 16(%rsp,%rcx), %eax
                shrb %al
                movb 15(%rsp,%rcx), %dl
                testb $1, %dl
                jz .Lgcm_vshift_x_write
                orb $0x80, %al
            .Lgcm_vshift_x_write:
                movb %al, 16(%rsp,%rcx)
                decq %rcx
                jmp .Lgcm_vshift_x_loop
            .Lgcm_vshift_x0:
                movzbl 16(%rsp), %eax
                shrb %al
                movb %al, 16(%rsp)
            .Lgcm_vshift_reduce:
                movb 16(%rsp), %al
                xorb $0xe1, %al
                movb %al, 16(%rsp)
                jmp .Lgcm_vshift_done
            .Lgcm_vshift_plain:
                movq $15, %rcx
            .Lgcm_vshift_p:
                cmpq $1, %rcx
                jl .Lgcm_vshift_p0
                movzbl 16(%rsp,%rcx), %eax
                shrb %al
                movb 15(%rsp,%rcx), %dl
                testb $1, %dl
                jz .Lgcm_vshift_p_write
                orb $0x80, %al
            .Lgcm_vshift_p_write:
                movb %al, 16(%rsp,%rcx)
                decq %rcx
                jmp .Lgcm_vshift_p
            .Lgcm_vshift_p0:
                movzbl 16(%rsp), %eax
                shrb %al
                movb %al, 16(%rsp)
            .Lgcm_vshift_done:
                incq %r14
                jmp .Lgcm_bit_loop
            .Lgcm_done:
                # out = Z
                xorq %rcx, %rcx
            .Lgcm_out:
                cmpq $16, %rcx
                jge .Lgcm_ret
                movb (%rsp,%rcx), %al
                movb %al, (%r13,%rcx)
                incq %rcx
                jmp .Lgcm_out
            .Lgcm_ret:
            """);
    }
}
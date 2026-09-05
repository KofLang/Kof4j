package dev.kof.compiler;

/**
 * Emissão do ASM de security1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeSecurity1 {

    private RuntimeSecurity1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_sec_sha256_internal
            .type kof_sec_sha256_internal, @function
            kof_sec_sha256_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $296, %rsp          # w[64]=256 + h[8]=32 + len(8) -> use 272..287 for w? layout below
                movq %rdi, %r12          # out
                movq %rsi, %r13          # src
                movq %rdx, %r14          # len
                movl $0x6a09e667, 0(%rsp)
                movl $0xbb67ae85, 4(%rsp)
                movl $0x3c6ef372, 8(%rsp)
                movl $0xa54ff53a, 12(%rsp)
                movl $0x510e527f, 16(%rsp)
                movl $0x9b05688c, 20(%rsp)
                movl $0x1f83d9ab, 24(%rsp)
                movl $0x5be0cd19, 28(%rsp)
                # w starts at 32(%rsp) -- 256 bytes → ends at 288(%rsp)
                # process full 64-byte blocks from src
                xorq %r15, %r15          # offset
            .Lsec_sha256_full_block:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $64, %rax
                jl .Lsec_sha256_final_block
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                addq $64, %r15
                jmp .Lsec_sha256_full_block
            .Lsec_sha256_final_block:
                # rem = len - offset; build the final block(s) on the stack
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx          # rem
                subq $128, %rsp
                xorq %rdx, %rdx
            .Lsec_sha256_copy_rem:
                cmpq %rcx, %rdx
                jge .Lsec_sha256_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rsp,%rdx)
                incq %rdx
                jmp .Lsec_sha256_copy_rem
            .Lsec_sha256_copy_done:
                movb $0x80, (%rsp,%rcx)
                movq %rcx, %r15          # rem (offset no longer needed)
                movq %rcx, %rdx
                incq %rdx
            .Lsec_sha256_zero_pad:
                cmpq $128, %rdx
                jge .Lsec_sha256_zero_done
                movb $0, (%rsp,%rdx)
                incq %rdx
                jmp .Lsec_sha256_zero_pad
            .Lsec_sha256_zero_done:
                movq %r15, %rax
                addq $9, %rax
                cmpq $64, %rax
                jg .Lsec_sha256_len_in_second
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 56(%rsp)
                leaq 128(%rsp), %rdi
                movq %rsp, %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                jmp .Lsec_sha256_final_done
            .Lsec_sha256_len_in_second:
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 120(%rsp)
                leaq 128(%rsp), %rdi
                movq %rsp, %rsi
                movl $64, %edx
                call kof_sec_sha256_block
                leaq 64(%rsp), %rsi
                leaq 128(%rsp), %rdi
                movl $64, %edx
                call kof_sec_sha256_block
            .Lsec_sha256_final_done:
                addq $128, %rsp
                jmp .Lsec_sha256_finish
            .Lsec_sha256_finish:
                # write h0..h7 big-endian to out
                xorq %rcx, %rcx
            .Lsec_sha256_out:
                cmpq $8, %rcx
                jge .Lsec_sha256_ret
                movl (%rsp,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%r12,%rcx,4)
                incq %rcx
                jmp .Lsec_sha256_out
            .Lsec_sha256_ret:
                addq $296, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha256_block(rdi=h[8] uint32, rsi=block64)
            .globl kof_sec_sha256_block
            .type kof_sec_sha256_block, @function
            kof_sec_sha256_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $272, %rsp          # w[64] = 256 + scratch 8 + h 8
                movq %rdi, 264(%rsp)     # h saved on the stack
                movq %rdi, %r12          # h (also used as scratch; reloaded at the end)
                movq %rsi, %r13          # block
                xorq %rcx, %rcx
            .Lsec_w_load:
                cmpq $16, %rcx
                jge .Lsec_w_load_done
                movl (%r13,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsec_w_load
            .Lsec_w_load_done:
                movq $16, %rcx
            .Lsec_w_extend:
                cmpq $64, %rcx
                jge .Lsec_w_extend_done
                # s0 = ror7(w[i-15]) ^ ror18 ^ shr3
                movl -60(%rsp,%rcx,4), %eax
                movl %eax, %edx
                movl %eax, %ebx
                roll $25, %eax           # ror 7
                roll $14, %edx           # ror 18
                shrl $3, %ebx
                xorl %edx, %eax
                xorl %ebx, %eax
                # s1 = ror17(w[i-2]) ^ ror19 ^ shr10
                movl -8(%rsp,%rcx,4), %edx
                movl %edx, %ebx
                movl %edx, %r14d
                roll $15, %edx           # ror 17
                roll $13, %ebx           # ror 19
                shrl $10, %r14d
                xorl %ebx, %edx
                xorl %r14d, %edx
                movl -64(%rsp,%rcx,4), %r14d   # w[i-16]
                addl %eax, %r14d
                addl -28(%rsp,%rcx,4), %r14d   # + w[i-7]
                addl %edx, %r14d
                movl %r14d, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsec_w_extend
            .Lsec_w_extend_done:
                movl 0(%r12), %eax       # a
                movl 4(%r12), %ebx       # b
                movl 8(%r12), %ecx       # c
                movl 12(%r12), %edx      # d
                movl 16(%r12), %r8d      # e
                movl 20(%r12), %r9d      # f
                movl 24(%r12), %r10d     # g
                movl 28(%r12), %r11d     # h
                movq $0, %r14            # round index
            .Lsec_round:
                cmpq $64, %r14
                jge .Lsec_round_done
                # S1(e) -> 256(%rsp)
                movl %r8d, %r15d
                movl %r8d, %r12d
                roll $26, %r15d          # ror 6
                roll $21, %r12d          # ror 11
                xorl %r12d, %r15d
                movl %r8d, %r12d
                roll $7, %r12d           # ror 25
                xorl %r12d, %r15d
                movl %r15d, 256(%rsp)
                # ch = (e & f) ^ (~e & g) -> r15d
                movl %r8d, %r15d
                andl %r9d, %r15d
                movl %r8d, %r12d
                notl %r12d
                andl %r10d, %r12d
                xorl %r12d, %r15d
                # t1 = h + S1 + ch + K[i] + w[i] -> r13d
                leaq .Lsec_sha256_k(%rip), %r13
                movl (%r13,%r14,4), %r13d
                addl (%rsp,%r14,4), %r13d
                addl 256(%rsp), %r13d    # + S1
                addl %r15d, %r13d        # + ch
                addl %r11d, %r13d        # + h
                # S0(a) -> 256(%rsp)
                movl %eax, %r15d
                movl %eax, %r12d
                roll $30, %r15d          # ror 2
                roll $19, %r12d          # ror 13
                xorl %r12d, %r15d
                movl %eax, %r12d
                roll $10, %r12d          # ror 22
                xorl %r12d, %r15d
                movl %r15d, 256(%rsp)
                # maj = (a&b)^(a&c)^(b&c) -> r15d
                movl %eax, %r15d
                andl %ebx, %r15d
                movl %eax, %r12d
                andl %ecx, %r12d
                xorl %r12d, %r15d
                movl %ebx, %r12d
                andl %ecx, %r12d
                xorl %r12d, %r15d
                # t2 = S0 + maj -> 256(%rsp)
                addl 256(%rsp), %r15d
                movl %r15d, 256(%rsp)
                # shift: h=g, g=f, f=e, e=d+t1, d=c, c=b, b=a, a=t1+t2
                movl %r10d, %r11d
                movl %r9d, %r10d
                movl %r8d, %r9d
                movl %edx, %r8d
                addl %r13d, %r8d         # e = d + t1
                movl %ecx, %edx
                movl %ebx, %ecx
                movl %eax, %ebx
                movl %r13d, %eax
                addl 256(%rsp), %eax     # a = t1 + t2
                incq %r14
                jmp .Lsec_round
            .Lsec_round_done:
                movq 264(%rsp), %r12
                addl %eax, 0(%r12)
                addl %ebx, 4(%r12)
                addl %ecx, 8(%r12)
                addl %edx, 12(%r12)
                addl %r8d, 16(%r12)
                addl %r9d, 20(%r12)
                addl %r10d, 24(%r12)
                addl %r11d, 28(%r12)
                addq $272, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha256(rdi=string) → hex string
            .globl kof_sec_sha256
            .type kof_sec_sha256, @function
            kof_sec_sha256:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                subq $32, %rsp
                movq %rsp, %rdi
                movq %rbx, %rsi
                addq $24, %rsi           # payload
                movslq %r12d, %rdx
                call kof_sec_sha256_internal
                # build hex string: 24 + 64 + 1
                movl $89, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $64, 16(%r13)
                movl $0, 20(%r13)
                xorq %rcx, %rcx
            .Lsec_sha256_hex:
                cmpq $32, %rcx
                jge .Lsec_sha256_hex_done
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
                jmp .Lsec_sha256_hex
            .Lsec_sha256_hex_done:
                movb $0, 88(%r13)
                movq %r13, %rax
                addq $32, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── SHA-512 (FIPS 180-4) ──────────────────────────────────────
            # kof_sec_sha512_block(rdi=h[8] uint64, rsi=block128)
            kof_sec_sha512_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $680, %rsp          # w[80]*8=640 + h_save 8 + block_save 8
                movq %rdi, 640(%rsp)
                movq %rsi, 648(%rsp)
                movq %rsi, %r13          # block
                xorq %rcx, %rcx
            .Ls512_w_load:
                cmpq $16, %rcx
                jge .Ls512_w_load_done
                movq (%r13,%rcx,8), %rax
                bswapq %rax
                movq %rax, (%rsp,%rcx,8)
                incq %rcx
                jmp .Ls512_w_load
            .Ls512_w_load_done:
                movq $16, %rcx
            .Ls512_w_ext:
                cmpq $80, %rcx
                jge .Ls512_w_ext_done
                movq -120(%rsp,%rcx,8), %rax    # w[i-15]
                movq %rax, %rbx
                movq %rax, %rdx
                rorq $1, %rax
                rorq $8, %rbx
                shrq $7, %rdx
                xorq %rbx, %rax
                xorq %rdx, %rax
                movq -16(%rsp,%rcx,8), %rbx     # w[i-2]
                movq %rbx, %rdx
                movq %rbx, %r8
                rorq $19, %rbx
                rorq $61, %rdx
                shrq $6, %r8
                xorq %rdx, %rbx
                xorq %r8, %rbx
                movq -128(%rsp,%rcx,8), %rdx    # w[i-16]
                addq %rax, %rdx
                addq -56(%rsp,%rcx,8), %rdx     # w[i-7]
                addq %rbx, %rdx
                movq %rdx, (%rsp,%rcx,8)
                incq %rcx
                jmp .Ls512_w_ext
            .Ls512_w_ext_done:
                movq 640(%rsp), %rbx
                movq 0(%rbx), %r8        # a
                movq 8(%rbx), %r9         # b
                movq 16(%rbx), %r10       # c
                movq 24(%rbx), %r11       # d
                movq 32(%rbx), %r12       # e
                movq 40(%rbx), %r13       # f
                movq 48(%rbx), %r14       # g
                movq 56(%rbx), %r15       # h
                leaq .Lsec_sha512_k(%rip), %rsi
                xorq %rcx, %rcx
            .Ls512_round:
                cmpq $80, %rcx
                jge .Ls512_round_done
                movq %r12, %rax
                movq %r12, %rbx
                movq %r12, %rdx
                rorq $14, %rax
                rorq $18, %rbx
                rorq $41, %rdx
                xorq %rbx, %rax
                xorq %rdx, %rax          # S1(e)
                movq %r12, %rbx
                andq %r13, %rbx          # e&f
                movq %r12, %rdx
                notq %rdx
                andq %r14, %rdx          # ~e&g
                xorq %rdx, %rbx          # ch
                movq %r15, %rdi
                addq %rax, %rdi
                addq %rbx, %rdi          # T1 = h + S1 + ch
                movq (%rsi,%rcx,8), %rax
                addq %rax, %rdi
                addq (%rsp,%rcx,8), %rdi # T1 += K[i] + w[i]
                movq %r8, %rax
                movq %r8, %rbx
                movq %r8, %rdx
                rorq $28, %rax
                rorq $34, %rbx
                rorq $39, %rdx
                xorq %rbx, %rax
                xorq %rdx, %rax          # S0(a)
                movq %r8, %rbx
                andq %r9, %rbx           # a&b
                movq %r8, %rdx
                andq %r10, %rdx          # a&c
                xorq %rdx, %rbx
                movq %r9, %rdx
                andq %r10, %rdx          # b&c
                xorq %rdx, %rbx          # maj
                addq %rbx, %rax          # T2 = S0 + maj
                movq %r14, %r15          # h = g
                movq %r13, %r14          # g = f
                movq %r12, %r13          # f = e
                movq %r11, %r12          # e = d
                addq %rdi, %r12          # e += T1
                movq %r10, %r11          # d = c
                movq %r9, %r10           # c = b
                movq %r8, %r9            # b = a
                leaq (%rdi,%rax), %r8   # a = T1 + T2
                incq %rcx
                jmp .Ls512_round
            .Ls512_round_done:
                movq 640(%rsp), %rdx
                addq %r8, 0(%rdx)
                addq %r9, 8(%rdx)
                addq %r10, 16(%rdx)
                addq %r11, 24(%rdx)
            """);
    }
}
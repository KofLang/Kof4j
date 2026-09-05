package dev.kof.compiler;

/**
Emissão do ASM de db1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeDb1 {

    private RuntimeDb1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .Ldb_null: .asciz "null"
            # KofStrings (header 24B: 1@0, 0@4, 0@8, len@16, 0@20; dados em +24)
            # p/ transaction — kf_db_execute lê em leaq 24(%rsi).
            .Ldb_begin_str:
                .long 1
                .long 0
                .quad 0
                .long 5
                .long 0
                .asciz "begin"
            .Ldb_commit_str:
                .long 1
                .long 0
                .quad 0
                .long 6
                .long 0
                .asciz "commit"
            .Ldb_rollback_str:
                .long 1
                .long 0
                .quad 0
                .long 8
                .long 0
                .asciz "rollback"
            .section .bss
            .Ldb_slots: .zero 512
            .Ldb_types: .zero 64
            .Ldb_count: .quad 0
            # handle (KofString*) da conexão "default" — o que transaction {} usa.
            # 0 = sem conexão aberta.
            .Ldb_default_handle: .quad 0
            .Ldb_mysql_buf: .zero 16384
            .Ldb_mysql_names: .zero 1024
            .Ldb_mysql_seq: .zero 1
            # estado do reader de pacotes (query):
            #   .Ldb_mysql_fd    — fd atual
            #   .Ldb_mysql_ppos  — offset do payload atual (buf+4)
            #   .Ldb_mysql_pend  — fim do payload atual
            #   .Ldb_mysql_next  — 1 se o próximo pacote já está em buf
            .Ldb_mysql_fd: .quad 0
            .Ldb_mysql_ppos: .quad 0
            .Ldb_mysql_pend: .quad 0
            .Ldb_mysql_next: .long 0
            .section .data
            .Ldb_mysql_plugin: .asciz "mysql_native_password"
            .Ldb_mysql_empty: .asciz ""
            .Ldb_mysql_nullstr: .asciz "null"
            .section .text

            # ── SHA-1 (para o auth scramble do MySQL) ────────────────
            # kof_sec_sha1_block: (rdi=h[5] em LE na stack, rsi=bloco 64B)
            .globl kof_sec_sha1_block
            .type kof_sec_sha1_block, @function
            kof_sec_sha1_block:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $352, %rsp           # w[80]=320 + h[5]=20
                movq %rdi, %r12
                movq %rsi, %r13
                movl 0(%r12), %eax
                movl %eax, 320(%rsp)
                movl 4(%r12), %eax
                movl %eax, 324(%rsp)
                movl 8(%r12), %eax
                movl %eax, 328(%rsp)
                movl 12(%r12), %eax
                movl %eax, 332(%rsp)
                movl 16(%r12), %eax
                movl %eax, 336(%rsp)
                # w[0..15] = bloco em BE
                xorl %ecx, %ecx
            .Lsha1_load:
                cmpl $16, %ecx
                jge .Lsha1_load_done
                movl (%r13,%rcx,4), %eax
                bswapl %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_load
            .Lsha1_load_done:
                # w[16..79]
                movl $16, %ecx
            .Lsha1_w:
                cmpl $80, %ecx
                jge .Lsha1_w_done
                movl -12(%rsp,%rcx,4), %eax
                xorl -32(%rsp,%rcx,4), %eax
                xorl -56(%rsp,%rcx,4), %eax
                xorl -64(%rsp,%rcx,4), %eax
                roll $1, %eax
                movl %eax, (%rsp,%rcx,4)
                incq %rcx
                jmp .Lsha1_w
            .Lsha1_w_done:
                # a..e = h[0..4]
                movl 320(%rsp), %r8d
                movl 324(%rsp), %r9d
                movl 328(%rsp), %r10d
                movl 332(%rsp), %r11d
                movl 336(%rsp), %ebx
                xorl %r15d, %r15d
            .Lsha1_round:
                cmpl $80, %r15d
                jge .Lsha1_round_done
                # f/g/K por fase
                cmpl $20, %r15d
                jge .Lsha1_phase2
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                notl %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x5A827999, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase2:
                cmpl $40, %r15d
                jge .Lsha1_phase3
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0x6ED9EBA1, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase3:
                cmpl $60, %r15d
                jge .Lsha1_phase4
                movl %r9d, %eax
                andl %r10d, %eax
                movl %r9d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl %r10d, %edx
                andl %r11d, %edx
                orl %edx, %eax
                movl $0x8F1BBCDC, %r14d
                jmp .Lsha1_f_done
            .Lsha1_phase4:
                movl %r9d, %eax
                xorl %r10d, %eax
                xorl %r11d, %eax
                movl $0xCA62C1D6, %r14d
            .Lsha1_f_done:
                # temp = ROTL5(a) + f + e + K + W[i]
                movl %r8d, %edx
                roll $5, %edx
                addl %eax, %edx
                addl %ebx, %edx
                addl %r14d, %edx
                addl (%rsp,%r15,4), %edx
                movl %r9d, %eax
                movl %r11d, %ebx
                movl %r10d, %r11d
                roll $30, %eax
                movl %eax, %r10d
                movl %r8d, %r9d
                movl %edx, %r8d
                incq %r15
                jmp .Lsha1_round
            .Lsha1_round_done:
                addl %r8d, 320(%rsp)
                addl %r9d, 324(%rsp)
                addl %r10d, 328(%rsp)
                addl %r11d, 332(%rsp)
                addl %ebx, 336(%rsp)
                movl 320(%rsp), %eax
                movl %eax, 0(%r12)
                movl 324(%rsp), %eax
                movl %eax, 4(%r12)
                movl 328(%rsp), %eax
                movl %eax, 8(%r12)
                movl 332(%rsp), %eax
                movl %eax, 12(%r12)
                movl 336(%rsp), %eax
                movl %eax, 16(%r12)
                addq $352, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_sec_sha1_internal(rdi=out20, rsi=src, rdx=len)
            .globl kof_sec_sha1_internal
            .type kof_sec_sha1_internal, @function
            kof_sec_sha1_internal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp          # h[5]=20 + bloco 64 + pad 128
                movq %rdi, %r12
                movq %rsi, %r13
                movq %rdx, %r14
                movl $0x67452301, 0(%rsp)
                movl $0xEFCDAB89, 4(%rsp)
                movl $0x98BADCFE, 8(%rsp)
                movl $0x10325476, 12(%rsp)
                movl $0xC3D2E1F0, 16(%rsp)
                xorq %r15, %r15
            .Lsha1_full:
                movq %r14, %rax
                subq %r15, %rax
                cmpq $64, %rax
                jl .Lsha1_final
                movq %rsp, %rdi
                leaq (%r13,%r15), %rsi
                call kof_sec_sha1_block
                addq $64, %r15
                jmp .Lsha1_full
            .Lsha1_final:
                movq %r14, %rax
                subq %r15, %rax
                movq %rax, %rcx
                leaq 20(%rsp), %rdi
                xorq %rdx, %rdx
            .Lsha1_copy:
                cmpq %rcx, %rdx
                jge .Lsha1_copy_done
                leaq (%r13,%r15), %rsi
                movb (%rsi,%rdx), %al
                movb %al, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_copy
            .Lsha1_copy_done:
                movb $0x80, (%rdi,%rcx)
                movq %rcx, %r15
                movq %rcx, %rdx
                incq %rdx
            .Lsha1_pad:
                cmpq $128, %rdx
                jge .Lsha1_pad_done
                movb $0, (%rdi,%rdx)
                incq %rdx
                jmp .Lsha1_pad
            .Lsha1_pad_done:
                movq %r14, %rax
                shlq $3, %rax
                bswapq %rax
                movq %rax, 56(%rdi)
                # primeiro bloco do pad (sempre o final: 1 bloco p/ len<56)
                movq %rsp, %rdi
                leaq 20(%rsp), %rsi
                call kof_sec_sha1_block
                movq %r12, %rdi
                movq %rsp, %rsi
                # escreve o digest (BE) direto no out
                movl 0(%rsp), %eax
                bswapl %eax
                movl %eax, 0(%r12)
                movl 4(%rsp), %eax
                bswapl %eax
                movl %eax, 4(%r12)
                movl 8(%rsp), %eax
                bswapl %eax
                movl %eax, 8(%r12)
                movl 12(%rsp), %eax
                bswapl %eax
                movl %eax, 12(%r12)
                movl 16(%rsp), %eax
                bswapl %eax
                movl %eax, 16(%r12)
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_scramble(rdi=out20, rsi=seed, rdx=seedlen, rcx=pass KofString)
            .globl kof_db_mysql_scramble
            .type kof_db_mysql_scramble, @function
            kof_db_mysql_scramble:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $128, %rsp
                movq %rdi, %r12          # out
                movq %rsi, %r13          # seed
                movq %rdx, %r14          # seedlen
                movq %rcx, %r15          # pass
                # stage1 = SHA1(pass)
                leaq 96(%rsp), %rdi
                leaq 24(%r15), %rsi
                movslq 16(%r15), %rdx
                call kof_sec_sha1_internal
                # stage2 = SHA1(stage1)
                leaq 76(%rsp), %rdi
                leaq 96(%rsp), %rsi
                movl $20, %edx
                call kof_sec_sha1_internal
                # stage3 = SHA1(seed + stage2) → 56(%rsp)
                leaq 56(%rsp), %rdi
                leaq 36(%rsp), %rsi
                # copia seed para 36(%rsp)
                xorq %rcx, %rcx
            .Lscr_copy_seed:
                cmpq %r14, %rcx
                jge .Lscr_copy_seed_done
                movb (%r13,%rcx), %al
                movb %al, 36(%rsp,%rcx)
                incq %rcx
                jmp .Lscr_copy_seed
            .Lscr_copy_seed_done:
                movq %r14, %r8
                xorl %ecx, %ecx
            .Lscr_copy_st2:
                cmpl $20, %ecx
                jge .Lscr_copy_st2_done
                movb 76(%rsp,%rcx), %al
                movb %al, 36(%rsp,%r8)
                incq %rcx
                incq %r8
                jmp .Lscr_copy_st2
            .Lscr_copy_st2_done:
                leaq 36(%rsp), %rsi
                movq %r8, %rdx
                call kof_sec_sha1_internal
                # result = stage1 XOR stage3
                xorl %ecx, %ecx
            .Lscr_xor:
                cmpl $20, %ecx
                jge .Lscr_xor_done
                movb 96(%rsp,%rcx), %al
                xorb 56(%rsp,%rcx), %al
                movb %al, (%r12,%rcx)
                incq %rcx
                jmp .Lscr_xor
            .Lscr_xor_done:
                addq $128, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_db_mysql_lenenc: rsi=buf → eax=valor, rsi=proxima pos
            kof_db_mysql_lenenc:
                movzbl (%rsi), %eax
                cmpb $0xFC, %al
                je .Ldb_len_2
                cmpb $0xFD, %al
                je .Ldb_len_3
                incq %rsi
                ret
            .Ldb_len_2:
                movzwl 1(%rsi), %eax
                addq $3, %rsi
                ret
            .Ldb_len_3:
                movzbl 1(%rsi), %eax
                movzbl 2(%rsi), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 3(%rsi), %edx
                shll $16, %edx
                orl %edx, %eax
                addq $4, %rsi
                ret

            # kof_db_mysql_render(rdi=value) → rax: literal SQL p/ bind.
            # Int (rdx<0x1000000) -> decimais; String (rdi>=0x1000000) -> 'escaped'
            # (MySQL: ' -> '' e \\ -> \\\\).
            .globl kof_db_mysql_render
            .type kof_db_mysql_render, @function
            kof_db_mysql_render:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $4096, %rsp
                cmpq $0x1000000, %rdi
                jb .Ldb_rnd_int
            .Ldb_rnd_str:
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leaq 24(%rbx), %rsi
                movq %rsp, %r13               # scratch start
                movq %rsp, %r14              # out cursor
                xorl %ecx, %ecx
            .Ldb_rnd_esc:
                cmpl %r12d, %ecx
                jge .Ldb_rnd_esc_done
                movzbl (%rsi,%rcx), %eax
                cmpb $'\'', %al
                je .Ldb_rnd_dbl
                cmpb $'\\', %al
                je .Ldb_rnd_dbl
                movb %al, (%r14)
                incq %r14
            .Ldb_rnd_next:
                incl %ecx
                jmp .Ldb_rnd_esc
            .Ldb_rnd_dbl:
                movb %al, (%r14)
                incq %r14
                movb %al, (%r14)
                incq %r14
                jmp .Ldb_rnd_next
            .Ldb_rnd_esc_done:
                movq %r14, %r15
                subq %r13, %r15                  # escaped len
                leal 2(%r15), %r12d              # novo len (com aspas)
                leal 25(%r12d), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
            """);
    }
}
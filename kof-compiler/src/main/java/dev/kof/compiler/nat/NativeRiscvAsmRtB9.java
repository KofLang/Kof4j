package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2b.3): fatia 9 de RISCV_RUNTIME_ASM_B — kof.strings
// padLeft/padRight (alocam String; molde B7). pad = 1º byte do 3º arg.
public final class NativeRiscvAsmRtB9 {

    static final String RISCV_RUNTIME_ASM_B_9 = """

            # ── kof.strings (STDLIB S2b.3) — padLeft/padRight ─────────

            # kof_strings_padLeft(a0=v, a1=n, a2=pad) -> String
            # null=>0; pad null/"" ou len>=n => v; senao (n-len)×pad[0] + v.
            .section .text
            .globl kof_strings_padLeft
            kof_strings_padLeft:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0              # v
                mv   s1, a1              # n
                mv   s2, a2              # pad
                beqz s0, .Lv_str_pl_null
                beqz s2, .Lv_str_pl_orig
                lw   t0, 16(s2)
                blez t0, .Lv_str_pl_orig
                lbu  s3, 24(s2)          # pad[0]
                lw   s4, 16(s0)          # vlen
                bge  s4, s1, .Lv_str_pl_orig
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s1, 16(s5)
                sw   zero, 20(s5)
                # fill: i em [0, n-vlen)
                li   t1, 0
            .Lv_str_pl_fill:
                mv   t2, s1
                sub  t2, t2, s4
                bge  t1, t2, .Lv_str_pl_copy
                add  t3, s5, 24
                add  t3, t3, t1
                sb   s3, 0(t3)
                addi t1, t1, 1
                j    .Lv_str_pl_fill
            .Lv_str_pl_copy:
                blez s4, .Lv_str_pl_term
                addi a0, s5, 24
                add  a0, a0, t2          # + (n-vlen)
                addi a1, s0, 24
                mv   a2, s4
                call kof_memcpy
            .Lv_str_pl_term:
                li   t0, 0
                add  t1, s5, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s5
                j    .Lv_str_pl_done
            .Lv_str_pl_orig:
                mv   a0, s0
                j    .Lv_str_pl_done
            .Lv_str_pl_null:
                li   a0, 0
            .Lv_str_pl_done:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 64
                ret

            # kof_strings_padRight(a0=v, a1=n, a2=pad) -> String
            .globl kof_strings_padRight
            kof_strings_padRight:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Lv_str_pr_null
                beqz s2, .Lv_str_pr_orig
                lw   t0, 16(s2)
                blez t0, .Lv_str_pr_orig
                lbu  s3, 24(s2)
                lw   s4, 16(s0)
                bge  s4, s1, .Lv_str_pr_orig
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s1, 16(s5)
                sw   zero, 20(s5)
                # copia v primeiro
                addi a0, s5, 24
                addi a1, s0, 24
                mv   a2, s4
                call kof_memcpy
                # fill cauda: i em [vlen, n)
                mv   t1, s4
            .Lv_str_pr_fill:
                bge  t1, s1, .Lv_str_pr_term
                add  t3, s5, 24
                add  t3, t3, t1
                sb   s3, 0(t3)
                addi t1, t1, 1
                j    .Lv_str_pr_fill
            .Lv_str_pr_term:
                li   t0, 0
                add  t1, s5, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s5
                j    .Lv_str_pr_done
            .Lv_str_pr_orig:
                mv   a0, s0
                j    .Lv_str_pr_done
            .Lv_str_pr_null:
                li   a0, 0
            .Lv_str_pr_done:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 64
                ret

            """;
}

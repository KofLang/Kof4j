package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2b.3): fatia 9 de RISCV_RUNTIME_ASM_B — kof.strings
// padLeft/padRight (alocam String; molde B7). pad = 1º byte do 3º arg.
public final class NativeRiscvAsmRtB9 {

    static  String RISCV_RUNTIME_ASM_B_9 = """

            # ── kof.strings (STDLIB S2b.3) — padLeft/padRight ─────────

            # kof_strings_padLeft(a0=v, a1=n, a2=pad) -> String
            # null=>0; pad null/"" ou len>=n => v; senao (n-len)×pad[0] + v.
            .section .text
            .globl kof_strings_padLeft
            # §747 (D-FULL-PARITY-050 NAT-STR01): o pad e o PRIMEIRO CARACTERE
            # do pad repetido ate n CARACTERES (contrato JVM/JS/Script). O
            # helper antigo escrevia UM BYTE por slot e contava a vitima em
            # BYTES -> pad nao-ASCII corrompia a String e vitima multi-byte
            # recebia pads a menos.
            kof_strings_padLeft:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Lv_str_pl_null
                beqz s2, .Lv_str_pl_orig
                lw   t0, 16(s2)
                blez t0, .Lv_str_pl_orig
                lbu  t0, 24(s2)
                li   s3, 1
                andi t1, t0, 0xE0
                li   t2, 0xC0
                beq  t1, t2, .Lv_pl_l2
                andi t1, t0, 0xF0
                li   t2, 0xE0
                beq  t1, t2, .Lv_pl_l3
                andi t1, t0, 0xF8
                li   t2, 0xF0
                beq  t1, t2, .Lv_pl_l4
                j    .Lv_pl_plen
            .Lv_pl_l2:
                li   s3, 2
                j    .Lv_pl_plen
            .Lv_pl_l3:
                li   s3, 3
                j    .Lv_pl_plen
            .Lv_pl_l4:
                li   s3, 4
            .Lv_pl_plen:
                lw   s4, 16(s0)
                li   s6, 0
                li   t1, 0
            .Lv_pl_cnt:
                bge  t1, s4, .Lv_pl_cntd
                add  t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                andi t2, t2, 0xC0
                li   t3, 0x80
                beq  t2, t3, .Lv_pl_cntn
                addi s6, s6, 1
            .Lv_pl_cntn:
                addi t1, t1, 1
                j    .Lv_pl_cnt
            .Lv_pl_cntd:
                bge  s6, s1, .Lv_str_pl_orig
                sub  s7, s1, s6
                mul  s7, s7, s3
                add  s7, s7, s4
                addi a0, s7, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s7, 16(s5)
                sw   zero, 20(s5)
                li   t1, 0
                sub  t5, s7, s4
            .Lv_pl_pchk:
                bge  t1, t5, .Lv_pl_copy
                li   t2, 0
            .Lv_pl_pbyte:
                bge  t2, s3, .Lv_pl_pchk
                add  t3, s2, 24
                add  t3, t3, t2
                lbu  t3, 0(t3)
                add  t4, s5, 24
                add  t4, t4, t1
                sb   t3, 0(t4)
                addi t1, t1, 1
                addi t2, t2, 1
                j    .Lv_pl_pbyte
            .Lv_pl_copy:
                blez s4, .Lv_pl_term
                addi a0, s5, 24
                add  a0, a0, t1
                addi a1, s0, 24
                mv   a2, s4
                call kof_memcpy
            .Lv_pl_term:
                add  t0, s5, 24
                add  t0, t0, s7
                sb   zero, 0(t0)
                mv   a0, s5
                j    .Lv_str_pl_done
            .Lv_str_pl_orig:
                mv   a0, s0
                j    .Lv_str_pl_done
            .Lv_str_pl_null:
                li   a0, 0
            .Lv_str_pl_done:
                ld   ra, 72(sp)
                ld   s0, 64(sp)
                ld   s1, 56(sp)
                ld   s2, 48(sp)
                ld   s3, 40(sp)
                ld   s4, 32(sp)
                ld   s5, 24(sp)
                ld   s6, 16(sp)
                ld   s7, 8(sp)
                addi sp, sp, 80
                ret

            # kof_strings_padRight(a0=v, a1=n, a2=pad) -> String
            # §747: vitima PRIMEIRO, depois o pad (primeiro caractere repetido).
            .globl kof_strings_padRight
            kof_strings_padRight:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Lv_str_pr_null
                beqz s2, .Lv_str_pr_orig
                lw   t0, 16(s2)
                blez t0, .Lv_str_pr_orig
                lbu  t0, 24(s2)
                li   s3, 1
                andi t1, t0, 0xE0
                li   t2, 0xC0
                beq  t1, t2, .Lv_pr_l2
                andi t1, t0, 0xF0
                li   t2, 0xE0
                beq  t1, t2, .Lv_pr_l3
                andi t1, t0, 0xF8
                li   t2, 0xF0
                beq  t1, t2, .Lv_pr_l4
                j    .Lv_pr_plen
            .Lv_pr_l2:
                li   s3, 2
                j    .Lv_pr_plen
            .Lv_pr_l3:
                li   s3, 3
                j    .Lv_pr_plen
            .Lv_pr_l4:
                li   s3, 4
            .Lv_pr_plen:
                lw   s4, 16(s0)
                li   s6, 0
                li   t1, 0
            .Lv_pr_cnt:
                bge  t1, s4, .Lv_pr_cntd
                add  t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                andi t2, t2, 0xC0
                li   t3, 0x80
                beq  t2, t3, .Lv_pr_cntn
                addi s6, s6, 1
            .Lv_pr_cntn:
                addi t1, t1, 1
                j    .Lv_pr_cnt
            .Lv_pr_cntd:
                bge  s6, s1, .Lv_str_pr_orig
                sub  s7, s1, s6
                mul  s7, s7, s3
                add  s7, s7, s4
                addi a0, s7, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s7, 16(s5)
                sw   zero, 20(s5)
                blez s4, .Lv_pr_pad
                addi a0, s5, 24
                addi a1, s0, 24
                mv   a2, s4
                call kof_memcpy
            .Lv_pr_pad:
                mv   t1, s4              # preenche da cauda: [vBytes, newLen)
            .Lv_pr_pchk:
                bge  t1, s7, .Lv_pr_term
                li   t2, 0
            .Lv_pr_pbyte:
                bge  t2, s3, .Lv_pr_pchk
                add  t3, s2, 24
                add  t3, t3, t2
                lbu  t3, 0(t3)
                add  t4, s5, 24
                add  t4, t4, t1
                sb   t3, 0(t4)
                addi t1, t1, 1
                addi t2, t2, 1
                j    .Lv_pr_pbyte
            .Lv_pr_term:
                add  t0, s5, 24
                add  t0, t0, s7
                sb   zero, 0(t0)
                mv   a0, s5
                j    .Lv_str_pr_done
            .Lv_str_pr_orig:
                mv   a0, s0
                j    .Lv_str_pr_done
            .Lv_str_pr_null:
                li   a0, 0
            .Lv_str_pr_done:
                ld   ra, 72(sp)
                ld   s0, 64(sp)
                ld   s1, 56(sp)
                ld   s2, 48(sp)
                ld   s3, 40(sp)
                ld   s4, 32(sp)
                ld   s5, 24(sp)
                ld   s6, 16(sp)
                ld   s7, 8(sp)
                addi sp, sp, 80
                ret

            """;
}

package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_STRN002_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmStrn0 {

    private NativeRiscvAsmStrn0() {}

    static final String RISCV_STRN002_ASM_0 = """
            # ---- STRN002: String methods riscv64 (char-loops, sem FP) ----
            # kof_string_trim(str@a0) -> str
            .globl kof_string_trim
            kof_string_trim:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                li   s2, 0
            .Ltr_lead:
                bge  s2, s1, .Ltr_trail
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 32
                beq  t0, t1, .Ltr_lskip
                li   t1, 9
                beq  t0, t1, .Ltr_lskip
                li   t1, 10
                beq  t0, t1, .Ltr_lskip
                li   t1, 13
                beq  t0, t1, .Ltr_lskip
                j    .Ltr_trail
            .Ltr_lskip:
                addi s2, s2, 1
                j    .Ltr_lead
            .Ltr_trail:
                mv   s3, s1
            .Ltr_tloop:
                ble  s3, s2, .Ltr_build
                addi t0, s3, -1
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                li   t2, 32
                beq  t1, t2, .Ltr_tskip
                li   t2, 9
                beq  t1, t2, .Ltr_tskip
                li   t2, 10
                beq  t1, t2, .Ltr_tskip
                li   t2, 13
                beq  t1, t2, .Ltr_tskip
                j    .Ltr_build
            .Ltr_tskip:
                mv   s3, t0
                j    .Ltr_tloop
            .Ltr_build:
                sub  s4, s3, s2
                addi a0, s4, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s5, a0
                li   t0, 1
                sw   t0, 0(s5)
                sw   zero, 4(s5)
                sd   zero, 8(s5)
                sw   s4, 16(s5)
                sw   zero, 20(s5)
                addi a0, s5, 24
                addi a1, s0, 24
                add  a1, a1, s2
                mv   a2, s4
                call kof_memcpy
                li   t0, 0
                addi t1, s5, 24
                add  t1, t1, s4
                sb   t0, 0(t1)
                mv   a0, s5
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_string_to_upper(str@a0) -> str
            .globl kof_string_to_upper
            kof_string_to_upper:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                li   s3, 0
            .Ltu_loop:
                bge  s3, s1, .Ltu_done
                addi t0, s0, 24
                add  t0, t0, s3
                lbu  t0, 0(t0)
                li   t1, 97
                blt  t0, t1, .Ltu_store
                li   t1, 122
                bgt  t0, t1, .Ltu_store
                addi t0, t0, -32
            .Ltu_store:
                addi t1, s2, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                addi s3, s3, 1
                j    .Ltu_loop
            .Ltu_done:
                li   t0, 0
                addi t1, s2, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_to_lower(str@a0) -> str
            .globl kof_string_to_lower
            kof_string_to_lower:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                li   s3, 0
            .Ltl_loop:
                bge  s3, s1, .Ltl_done
                addi t0, s0, 24
                add  t0, t0, s3
                lbu  t0, 0(t0)
                li   t1, 65
                blt  t0, t1, .Ltl_store
                li   t1, 90
                bgt  t0, t1, .Ltl_store
                addi t0, t0, 32
            .Ltl_store:
                addi t1, s2, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                addi s3, s3, 1
                j    .Ltl_loop
            .Ltl_done:
                li   t0, 0
                addi t1, s2, 24
                add  t1, t1, s1
                sb   t0, 0(t1)
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_replace_char(str@a0, from@a1, to@a2) -> str
            .globl kof_string_replace_char
            kof_string_replace_char:
                addi sp, sp, -56
                sd   ra, 48(sp)
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   s3, 16(s0)
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   s3, 16(s4)
                sw   zero, 20(s4)
                li   t1, 0
            .Lrc_loop:
                bge  t1, s3, .Lrc_done
                addi t2, s0, 24
                add  t2, t2, t1
                lbu  t2, 0(t2)
                bne  t2, s1, .Lrc_store
                mv   t2, s2
            .Lrc_store:
                addi t3, s4, 24
                add  t3, t3, t1
                sb   t2, 0(t3)
                addi t1, t1, 1
                j    .Lrc_loop
            .Lrc_done:
                li   t0, 0
                addi t1, s4, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s4
                ld   s4, 8(sp)
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                ld   ra, 48(sp)
                addi sp, sp, 56
                ret

            # kof_string_replace(str@a0, from@a1, to@a2) -> str
            .globl kof_string_replace
            kof_string_replace:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                lw   s3, 16(s0)
                lw   s4, 16(s1)
                lw   s5, 16(s2)
                beqz s4, .Lrp_copy
                li   s6, 0
                li   s7, 0
            .Lrp_scan:
                bge  s7, s3, .Lrp_alloc
                li   s8, 0
            .Lrp_cmp:
                bge  s8, s4, .Lrp_match
                addi t0, s0, 24
                add  t1, s7, s8
                add  t0, t0, t1
                lbu  t0, 0(t0)
                addi t1, s1, 24
                add  t1, t1, s8
                lbu  t1, 0(t1)
                bne  t0, t1, .Lrp_nomatch
                addi s8, s8, 1
                j    .Lrp_cmp
            .Lrp_match:
                addi s6, s6, 1
                add  s7, s7, s4
                j    .Lrp_scan
            .Lrp_nomatch:
                addi s7, s7, 1
                j    .Lrp_scan
            .Lrp_alloc:
                sub  t0, s5, s4
                mul  t0, t0, s6
                add  t0, t0, s3
                mv   s7, t0
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   s7, 16(s8)
                sw   zero, 20(s8)
                li   s6, 0
                li   s5, 0
            .Lrp_build:
                bge  s6, s3, .Lrp_done
                li   t3, 0
            .Lrp_bcmp:
                bge  t3, s4, .Lrp_bmatch
                addi t0, s0, 24
                add  t1, s6, t3
                add  t0, t0, t1
                lbu  t0, 0(t0)
                addi t1, s1, 24
                add  t1, t1, t3
                lbu  t1, 0(t1)
                bne  t0, t1, .Lrp_bcopy
                addi t3, t3, 1
                j    .Lrp_bcmp
            .Lrp_bmatch:
                lw   t4, 16(s2)
                li   t3, 0
            .Lrp_bto:
                bge  t3, t4, .Lrp_bskip
                addi t0, s2, 24
                add  t0, t0, t3
                lbu  t0, 0(t0)
                addi t1, s8, 24
                add  t1, t1, s5
                sb   t0, 0(t1)
                addi s5, s5, 1
                addi t3, t3, 1
                j    .Lrp_bto
            .Lrp_bskip:
                add  s6, s6, s4
                j    .Lrp_build
            .Lrp_bcopy:
                addi t0, s0, 24
                add  t0, t0, s6
                lbu  t0, 0(t0)
                addi t1, s8, 24
                add  t1, t1, s5
                sb   t0, 0(t1)
                addi s5, s5, 1
                addi s6, s6, 1
                j    .Lrp_build
            .Lrp_done:
                li   t0, 0
                addi t1, s8, 24
                add  t1, t1, s7
                sb   t0, 0(t1)
                mv   a0, s8
                j    .Lrp_ret
            .Lrp_copy:
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s8, a0
                li   t0, 1
                sw   t0, 0(s8)
                sw   zero, 4(s8)
                sd   zero, 8(s8)
                sw   s3, 16(s8)
                sw   zero, 20(s8)
                addi a0, s8, 24
                addi a1, s0, 24
                mv   a2, s3
                call kof_memcpy
                li   t0, 0
                addi t1, s8, 24
                add  t1, t1, s3
                sb   t0, 0(t1)
                mv   a0, s8
            .Lrp_ret:
                ld   s8, 16(sp)
                ld   s7, 24(sp)
                ld   s6, 32(sp)
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # kof_string_last_index_of(str@a0, sub@a1) -> Int
            .globl kof_string_last_index_of
            kof_string_last_index_of:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                beqz s3, .Lli_end
                bgt  s3, s2, .Lli_no
                sub  s4, s2, s3
            .Lli_outer:
                bltz s4, .Lli_no
                li   t0, 0
            .Lli_inner:
                bge  t0, s3, .Lli_found
                addi t1, s0, 24
                add  t2, s4, t0
                add  t1, t1, t2
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                lbu  t2, 0(t2)
                bne  t1, t2, .Lli_next
                addi t0, t0, 1
                j    .Lli_inner
            .Lli_next:
                addi s4, s4, -1
                j    .Lli_outer
            .Lli_found:
                mv   a0, s4
                j    .Lli_ret
            .Lli_end:
                mv   a0, s2
                j    .Lli_ret
            .Lli_no:
                li   a0, -1
            .Lli_ret:
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_string_equals_ignore_case(a@a0, b@a1) -> Bool
            """;
}

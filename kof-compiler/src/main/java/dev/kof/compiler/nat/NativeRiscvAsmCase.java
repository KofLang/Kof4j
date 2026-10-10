package dev.kof.compiler.nat;

// D-STR-UNICODE (row 11): fold Unicode por CODE UNIT riscv64 — extraido de
// NativeRiscvAsmStrn0 por limite de 500/600 ln (responsabilidade propria).
public final class NativeRiscvAsmCase {

    private NativeRiscvAsmCase() {}

    static String RISCV_STRCASE_ASM = ("""
            # kof_string_to_upper(str@a0) -> str (fold Unicode por CODE UNIT)
            # Caminha os code points UTF-8; ASCII inline; BMP 0x80..0xFFFF na
            # tabela .Lkof_cu_up_tab (busca binaria); astral (4 bytes) intacto.
            .globl kof_string_to_upper
            kof_string_to_upper:
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
                lw   s1, 16(s0)
                slli t0, s1, 1
                add  t0, t0, s1
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   zero, 16(s2)
                sw   zero, 20(s2)
                la   s3, .Lkof_cu_up_tab
                li   s4, @UPN@
                li   s5, 0
                li   s6, 0
            .Lkof_cu_up_loop:
                bge  s5, s1, .Lkof_cu_up_done
                add  t0, s0, s5
                lbu  t1, 24(t0)
                li   t2, 128
                blt  t1, t2, .Lkof_cu_up_ascii
                li   t2, 224
                blt  t1, t2, .Lkof_cu_up_two
                li   t2, 240
                blt  t1, t2, .Lkof_cu_up_three
                lw   t1, 24(t0)
                add  t2, s2, s6
                sw   t1, 24(t2)
                addi s5, s5, 4
                addi s6, s6, 4
                j    .Lkof_cu_up_loop
            .Lkof_cu_up_two:
                andi t1, t1, 31
                slli t1, t1, 6
                lbu  t2, 25(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                li   t3, 2
                j    .Lkof_cu_up_fold
            .Lkof_cu_up_three:
                andi t1, t1, 15
                slli t1, t1, 12
                lbu  t2, 25(t0)
                andi t2, t2, 63
                slli t2, t2, 6
                or   t1, t1, t2
                lbu  t2, 26(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                li   t3, 3
                j    .Lkof_cu_up_fold
            .Lkof_cu_up_ascii:
                li   t2, 97
                blt  t1, t2, .Lkof_cu_up_emit
                li   t2, 122
                bgt  t1, t2, .Lkof_cu_up_emit
                addi t1, t1, -32
            .Lkof_cu_up_emit:
                add  t2, s2, s6
                sb   t1, 24(t2)
                addi s5, s5, 1
                addi s6, s6, 1
                j    .Lkof_cu_up_loop
            .Lkof_cu_up_fold:
                mv   s7, t1
                add  s5, s5, t3
                li   t0, 0
                addi t4, s4, -1
            .Lkof_cu_up_bs:
                bgt  t0, t4, .Lkof_cu_up_nf
                add  t5, t0, t4
                srli t5, t5, 1
                slli t6, t5, 2
                add  t6, s3, t6
                lhu  a4, 0(t6)
                beq  a4, s7, .Lkof_cu_up_found
                blt  s7, a4, .Lkof_cu_up_bs_hi
                addi t0, t5, 1
                j    .Lkof_cu_up_bs
            .Lkof_cu_up_bs_hi:
                addi t4, t5, -1
                j    .Lkof_cu_up_bs
            .Lkof_cu_up_found:
                lhu  s7, 2(t6)
            .Lkof_cu_up_nf:
                li   t2, 128
                blt  s7, t2, .Lkof_cu_up_e1
                li   t2, 2048
                blt  s7, t2, .Lkof_cu_up_e2
                srli t0, s7, 12
                ori  t0, t0, 224
                add  t1, s2, s6
                sb   t0, 24(t1)
                srli t0, s7, 6
                andi t0, t0, 63
                ori  t0, t0, 128
                sb   t0, 25(t1)
                andi t0, s7, 63
                ori  t0, t0, 128
                sb   t0, 26(t1)
                addi s6, s6, 3
                j    .Lkof_cu_up_loop
            .Lkof_cu_up_e2:
                srli t0, s7, 6
                ori  t0, t0, 192
                add  t1, s2, s6
                sb   t0, 24(t1)
                andi t0, s7, 63
                ori  t0, t0, 128
                sb   t0, 25(t1)
                addi s6, s6, 2
                j    .Lkof_cu_up_loop
            .Lkof_cu_up_e1:
                add  t1, s2, s6
                sb   s7, 24(t1)
                addi s6, s6, 1
                j    .Lkof_cu_up_loop
            .Lkof_cu_up_done:
                sw   s6, 16(s2)
                add  t1, s2, s6
                sb   zero, 24(t1)
                mv   a0, s2
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

            # kof_string_to_lower(str@a0) -> str (fold Unicode por CODE UNIT)
            .globl kof_string_to_lower
            kof_string_to_lower:
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
                lw   s1, 16(s0)
                slli t0, s1, 1
                add  t0, t0, s1
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   zero, 16(s2)
                sw   zero, 20(s2)
                la   s3, .Lkof_cu_lo_tab
                li   s4, @LON@
                li   s5, 0
                li   s6, 0
            .Lkof_cu_lo_loop:
                bge  s5, s1, .Lkof_cu_lo_done
                add  t0, s0, s5
                lbu  t1, 24(t0)
                li   t2, 128
                blt  t1, t2, .Lkof_cu_lo_ascii
                li   t2, 224
                blt  t1, t2, .Lkof_cu_lo_two
                li   t2, 240
                blt  t1, t2, .Lkof_cu_lo_three
                lw   t1, 24(t0)
                add  t2, s2, s6
                sw   t1, 24(t2)
                addi s5, s5, 4
                addi s6, s6, 4
                j    .Lkof_cu_lo_loop
            .Lkof_cu_lo_two:
                andi t1, t1, 31
                slli t1, t1, 6
                lbu  t2, 25(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                li   t3, 2
                j    .Lkof_cu_lo_fold
            .Lkof_cu_lo_three:
                andi t1, t1, 15
                slli t1, t1, 12
                lbu  t2, 25(t0)
                andi t2, t2, 63
                slli t2, t2, 6
                or   t1, t1, t2
                lbu  t2, 26(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                li   t3, 3
                j    .Lkof_cu_lo_fold
            .Lkof_cu_lo_ascii:
                li   t2, 65
                blt  t1, t2, .Lkof_cu_lo_emit
                li   t2, 90
                bgt  t1, t2, .Lkof_cu_lo_emit
                addi t1, t1, 32
            .Lkof_cu_lo_emit:
                add  t2, s2, s6
                sb   t1, 24(t2)
                addi s5, s5, 1
                addi s6, s6, 1
                j    .Lkof_cu_lo_loop
            .Lkof_cu_lo_fold:
                mv   s7, t1
                add  s5, s5, t3
                li   t0, 0
                addi t4, s4, -1
            .Lkof_cu_lo_bs:
                bgt  t0, t4, .Lkof_cu_lo_nf
                add  t5, t0, t4
                srli t5, t5, 1
                slli t6, t5, 2
                add  t6, s3, t6
                lhu  a4, 0(t6)
                beq  a4, s7, .Lkof_cu_lo_found
                blt  s7, a4, .Lkof_cu_lo_bs_hi
                addi t0, t5, 1
                j    .Lkof_cu_lo_bs
            .Lkof_cu_lo_bs_hi:
                addi t4, t5, -1
                j    .Lkof_cu_lo_bs
            .Lkof_cu_lo_found:
                lhu  s7, 2(t6)
            .Lkof_cu_lo_nf:
                li   t2, 128
                blt  s7, t2, .Lkof_cu_lo_e1
                li   t2, 2048
                blt  s7, t2, .Lkof_cu_lo_e2
                srli t0, s7, 12
                ori  t0, t0, 224
                add  t1, s2, s6
                sb   t0, 24(t1)
                srli t0, s7, 6
                andi t0, t0, 63
                ori  t0, t0, 128
                sb   t0, 25(t1)
                andi t0, s7, 63
                ori  t0, t0, 128
                sb   t0, 26(t1)
                addi s6, s6, 3
                j    .Lkof_cu_lo_loop
            .Lkof_cu_lo_e2:
                srli t0, s7, 6
                ori  t0, t0, 192
                add  t1, s2, s6
                sb   t0, 24(t1)
                andi t0, s7, 63
                ori  t0, t0, 128
                sb   t0, 25(t1)
                addi s6, s6, 2
                j    .Lkof_cu_lo_loop
            .Lkof_cu_lo_e1:
                add  t1, s2, s6
                sb   s7, 24(t1)
                addi s6, s6, 1
                j    .Lkof_cu_lo_loop
            .Lkof_cu_lo_done:
                sw   s6, 16(s2)
                add  t1, s2, s6
                sb   zero, 24(t1)
                mv   a0, s2
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
""").replace("@UPN@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(true)))
                .replace("@LON@", Integer.toString(dev.kof.compiler.runtime.RuntimeStringCase.count(false)))
                + dev.kof.compiler.runtime.RuntimeStringCase.data(true, ".Lkof_cu_up_tab")
                + dev.kof.compiler.runtime.RuntimeStringCase.data(false, ".Lkof_cu_lo_tab");
}

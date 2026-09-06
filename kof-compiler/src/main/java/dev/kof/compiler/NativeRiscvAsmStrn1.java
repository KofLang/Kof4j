package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 1 de RISCV_STRN002_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmStrn1 {

    private NativeRiscvAsmStrn1() {}

    static final String RISCV_STRN002_ASM_1 = """
            .globl kof_string_equals_ignore_case
            kof_string_equals_ignore_case:
                addi sp, sp, -40
                sd   ra, 32(sp)
                sd   s0, 24(sp)
                sd   s1, 16(sp)
                sd   s2, 8(sp)
                sd   s3, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
                lw   s3, 16(s1)
                bne  s2, s3, .Lec_no
                li   t0, 0
            .Lec_loop:
                bge  t0, s2, .Lec_yes
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t1, 0(t1)
                addi t2, s1, 24
                add  t2, t2, t0
                lbu  t2, 0(t2)
                li   t3, 65
                blt  t1, t3, .Lec_f2
                li   t3, 90
                bgt  t1, t3, .Lec_f2
                addi t1, t1, 32
            .Lec_f2:
                li   t3, 65
                blt  t2, t3, .Lec_cmp
                li   t3, 90
                bgt  t2, t3, .Lec_cmp
                addi t2, t2, 32
            .Lec_cmp:
                bne  t1, t2, .Lec_no
                addi t0, t0, 1
                j    .Lec_loop
            .Lec_yes:
                li   a0, 1
                j    .Lec_ret
            .Lec_no:
                li   a0, 0
            .Lec_ret:
                ld   s3, 0(sp)
                ld   s2, 8(sp)
                ld   s1, 16(sp)
                ld   s0, 24(sp)
                ld   ra, 32(sp)
                addi sp, sp, 40
                ret

            # kof_string_split(str@a0, sep@a1) -> String[] (array de KofString)
            .globl kof_string_split
            kof_string_split:
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
                lw   s2, 16(s0)
                lw   t0, 16(s1)
                li   s3, 0
                beqz t0, .Lsk_count
                addi s3, s1, 24
                lbu  s3, 0(s3)
            .Lsk_count:
                li   s4, 1
                li   s5, 0
            .Lsk_cloop:
                bge  s5, s2, .Lsk_alloc
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_cnext
                addi s4, s4, 1
            .Lsk_cnext:
                addi s5, s5, 1
                j    .Lsk_cloop
            .Lsk_alloc:
                mv   a0, s4
                li   a1, 8
                call kof_array_alloc
                mv   s6, a0
                li   s7, 0
                li   s5, 0
                li   s4, 0
            .Lsk_outer:
                bge  s5, s2, .Lsk_last
                addi t1, s0, 24
                add  t1, t1, s5
                lbu  t1, 0(t1)
                bne  t1, s3, .Lsk_onext
                call .Lsk_emit
                addi s4, s5, 1
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_onext:
                addi s5, s5, 1
                j    .Lsk_outer
            .Lsk_last:
                bge  s4, s2, .Lsk_done
                mv   s5, s2
                call .Lsk_emit
            .Lsk_done:
                mv   a0, s6
                j    .Lsk_ret
            .Lsk_emit:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sub  t0, s5, s4
                sd   t0, 8(sp)              # len (sobrevive ao memcpy)
                addi a0, t0, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                sd   a0, 0(sp)              # str nova
                ld   t0, 8(sp)              # recarrega len (alloc clobbera t0)
                li   t2, 1
                sw   t2, 0(a0)
                sw   zero, 4(a0)
                sd   zero, 8(a0)
                sw   t0, 16(a0)
                sw   zero, 20(a0)
                addi a0, a0, 24
                addi a1, s0, 24
                add  a1, a1, s4
                mv   a2, t0
                call kof_memcpy
                ld   t1, 0(sp)
                ld   t0, 8(sp)
                li   t2, 0
                addi t3, t1, 24
                add  t3, t3, t0
                sb   t2, 0(t3)
                addi t2, s6, 24             # array: dados inline em 24+i*8
                slli t3, s7, 3
                add  t2, t2, t3
                sd   t1, 0(t2)
                addi s7, s7, 1
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lsk_ret:
                ld   s7, 8(sp)
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;
}

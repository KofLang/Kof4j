package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 linha 4 FATIA 2A (26/09): scan MP4/MOV riscv64 — port
 * byte-fiel de {@code RuntimeMediaMp4} x86 (mesma paridade com o oraculo JVM
 * medido no #624: box-size be32 UNSIGNED, size==1 = largesize 64-bit,
 * size==0 = box ate o fim, varredura externa 64-bit, `pos += (int) boxSize`
 * TRUNCADO no mvhd, low do duration v1 SINALIZADO). Compartilha os labels
 * .Lmed_* de {@link NativeRiscvAsmMedia} (mesmo .s); funcao pura, sem .bss
 * (§503 nao toca).
 */
public final class NativeRiscvAsmMediaMp4 {

    private NativeRiscvAsmMediaMp4() {}

    static String RISCV_ASM_MEDIA_MP4 = """
            .section .text
            # ── MP4 (paridade exata do oraculo JVM, be32 unsigned) ──
            # .Lmed_mp4_dur(a0=KofStr data) -> a0=durationMs
            .Lmed_mp4_dur:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                lw   s1, 16(s0)
                addi s2, s0, 24
                li   s3, 0
            .Lmed_mp4_loop:
                addi t0, s3, 8
                bgt  t0, s1, .Lmed_mp4_end
                add  t1, s2, s3
                lbu  t2, 0(t1)
                slli t2, t2, 24
                lbu  t3, 1(t1)
                slli t3, t3, 16
                or   t2, t2, t3
                lbu  t3, 2(t1)
                slli t3, t3, 8
                or   t2, t2, t3
                lbu  t3, 3(t1)
                or   t2, t2, t3
                beqz t2, .Lmed_mp4_to0
                li   t3, 1
                beq  t2, t3, .Lmed_mp4_ext
            .Lmed_mp4_sz:
                li   t3, 8
                blt  t2, t3, .Lmed_mp4_end
                addi t1, t1, 4
                lbu  t4, 0(t1)
                slli t4, t4, 24
                lbu  t5, 1(t1)
                slli t5, t5, 16
                or   t4, t4, t5
                lbu  t5, 2(t1)
                slli t5, t5, 8
                or   t4, t4, t5
                lbu  t5, 3(t1)
                or   t4, t4, t5
                li   t6, 0x6D6F6F76
                bne  t4, t6, .Lmed_mp4_next
                mv   a1, t0
                mv   a2, s3
                add  a2, a2, t2
                mv   a0, s0
                call .Lmed_mvhd_dur
                j    .Lmed_mp4_ret
            .Lmed_mp4_next:
                add  s3, s3, t2
                j    .Lmed_mp4_loop
            .Lmed_mp4_to0:
                mv   t2, s1
                sub  t2, t2, s3
                j    .Lmed_mp4_sz
            .Lmed_mp4_ext:
                addi t0, s3, 16
                bgt  t0, s1, .Lmed_mp4_end
                addi t5, s2, 8
                add  t5, t5, s3
                lbu  t2, 0(t5)
                slli t2, t2, 56
                lbu  t3, 1(t5)
                slli t3, t3, 48
                or   t2, t2, t3
                lbu  t3, 2(t5)
                slli t3, t3, 40
                or   t2, t2, t3
                lbu  t3, 3(t5)
                slli t3, t3, 32
                or   t2, t2, t3
                lbu  t3, 4(t5)
                slli t3, t3, 24
                or   t2, t2, t3
                lbu  t3, 5(t5)
                slli t3, t3, 16
                or   t2, t2, t3
                lbu  t3, 6(t5)
                slli t3, t3, 8
                or   t2, t2, t3
                lbu  t3, 7(t5)
                or   t2, t2, t3
                j    .Lmed_mp4_sz
            .Lmed_mp4_end:
                mv   a0, zero
            .Lmed_mp4_ret:
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # .Lmed_mvhd_dur(a0=KofStr data,a1=from,a2=to) -> a0=ms
            .Lmed_mvhd_dur:
                addi sp, sp, -72
                sd   ra, 64(sp)
                sd   s0, 56(sp)
                sd   s1, 48(sp)
                sd   s2, 40(sp)
                sd   s3, 32(sp)
                sd   s4, 24(sp)
                sd   s5, 16(sp)
                sd   s6, 8(sp)
                mv   s0, a0
                addi s1, a0, 24
                mv   s2, a1
                mv   t0, a2
                slli t0, t0, 32
                srai t0, t0, 32
                mv   s6, t0
            .Lmed_mv_loop:
                mv   t0, s2
                slli t0, t0, 32
                srai t0, t0, 32
                addi t3, t0, 8
                bgt  t3, s6, .Lmed_mv_zero
                add  t1, s1, t0
                lbu  t2, 0(t1)
                slli t2, t2, 24
                lbu  t3, 1(t1)
                slli t3, t3, 16
                or   t2, t2, t3
                lbu  t3, 2(t1)
                slli t3, t3, 8
                or   t2, t2, t3
                lbu  t3, 3(t1)
                or   t2, t2, t3
                beqz t2, .Lmed_mv_to0
                li   t3, 1
                beq  t2, t3, .Lmed_mv_ext
            .Lmed_mv_sz:
                li   t3, 8
                blt  t2, t3, .Lmed_mv_zero
                addi t1, t1, 4
                lbu  t4, 0(t1)
                slli t4, t4, 24
                lbu  t5, 1(t1)
                slli t5, t5, 16
                or   t4, t4, t5
                lbu  t5, 2(t1)
                slli t5, t5, 8
                or   t4, t4, t5
                lbu  t5, 3(t1)
                or   t4, t4, t5
                li   t6, 0x6D766864
                bne  t4, t6, .Lmed_mv_next
                addi t1, t1, 4
                lbu  t4, 0(t1)
                li   t5, 1
                beq  t4, t5, .Lmed_mv_v1
            .Lmed_mv_v0:
                mv   t0, s2
                slli t0, t0, 32
                srai t0, t0, 32
                addi t0, t0, 28
                bgt  t0, s6, .Lmed_mv_zero
                addi t1, t1, 16
                lbu  t5, 0(t1)
                slli t5, t5, 24
                lbu  t3, 1(t1)
                slli t3, t3, 16
                or   t5, t5, t3
                lbu  t3, 2(t1)
                slli t3, t3, 8
                or   t5, t5, t3
                lbu  t3, 3(t1)
                or   t5, t5, t3
                slli t5, t5, 32
                srai s3, t5, 32
                addi t1, t1, -4
                lbu  t4, 0(t1)
                slli t4, t4, 24
                lbu  t5, 1(t1)
                slli t5, t5, 16
                or   t4, t4, t5
                lbu  t5, 2(t1)
                slli t5, t5, 8
                or   t4, t4, t5
                lbu  t5, 3(t1)
                or   t4, t4, t5
                slli t4, t4, 32
                srai s4, t4, 32
                j    .Lmed_mv_math
            .Lmed_mv_v1:
                mv   t0, s2
                slli t0, t0, 32
                srai t0, t0, 32
                addi t0, t0, 40
                bgt  t0, s6, .Lmed_mv_zero
                addi t1, t1, 24
                lbu  t5, 0(t1)
                slli t5, t5, 24
                lbu  t3, 1(t1)
                slli t3, t3, 16
                or   t5, t5, t3
                lbu  t3, 2(t1)
                slli t3, t3, 8
                or   t5, t5, t3
                lbu  t3, 3(t1)
                or   t5, t5, t3
                slli s3, t5, 32
                addi t1, t1, 4
                lbu  t4, 0(t1)
                slli t4, t4, 24
                lbu  t5, 1(t1)
                slli t5, t5, 16
                or   t4, t4, t5
                lbu  t5, 2(t1)
                slli t5, t5, 8
                or   t4, t4, t5
                lbu  t5, 3(t1)
                or   t4, t4, t5
                slli t4, t4, 32
                srai t4, t4, 32
                or   s3, s3, t4
                addi t1, t1, -8
                lbu  s4, 0(t1)
                slli s4, s4, 24
                lbu  t5, 1(t1)
                slli t5, t5, 16
                or   s4, s4, t5
                lbu  t5, 2(t1)
                slli t5, t5, 8
                or   s4, s4, t5
                lbu  t5, 3(t1)
                or   s4, s4, t5
                slli t4, s4, 32
                srai s4, t4, 32
            .Lmed_mv_math:
                bge  zero, s4, .Lmed_mv_zero
                li   t0, 1000
                mul  s3, s3, t0
                div  s3, s3, s4
                li   t1, 2147483647
                ble  s3, t1, .Lmed_mv_ret
                mv   s3, t1
            .Lmed_mv_ret:
                mv   a0, s3
                ld   s6, 8(sp)
                ld   s5, 16(sp)
                ld   s4, 24(sp)
                ld   s3, 32(sp)
                ld   s2, 40(sp)
                ld   s1, 48(sp)
                ld   s0, 56(sp)
                ld   ra, 64(sp)
                addi sp, sp, 72
                ret
            .Lmed_mv_next:
                add  s2, s2, t2
                slli s2, s2, 32
                srli s2, s2, 32
                j    .Lmed_mv_loop
            .Lmed_mv_to0:
                mv   t0, s2
                slli t0, t0, 32
                srai t0, t0, 32
                mv   t2, s6
                sub  t2, t2, t0
                j    .Lmed_mv_sz
            .Lmed_mv_ext:
                mv   t0, s2
                slli t0, t0, 32
                srai t0, t0, 32
                addi t0, t0, 16
                bgt  t0, s6, .Lmed_mv_zero
                add  t5, s1, t0
                addi t5, t5, 8
                lbu  t2, 0(t5)
                slli t2, t2, 56
                lbu  t3, 1(t5)
                slli t3, t3, 48
                or   t2, t2, t3
                lbu  t3, 2(t5)
                slli t3, t3, 40
                or   t2, t2, t3
                lbu  t3, 3(t5)
                slli t3, t3, 32
                or   t2, t2, t3
                lbu  t3, 4(t5)
                slli t3, t3, 24
                or   t2, t2, t3
                lbu  t3, 5(t5)
                slli t3, t3, 16
                or   t2, t2, t3
                lbu  t3, 6(t5)
                slli t3, t3, 8
                or   t2, t2, t3
                lbu  t3, 7(t5)
                or   t2, t2, t3
                j    .Lmed_mv_sz
            .Lmed_mv_zero:
                mv   a0, zero
                ld   s6, 8(sp)
                ld   s5, 16(sp)
                ld   s4, 24(sp)
                ld   s3, 32(sp)
                ld   s2, 40(sp)
                ld   s1, 48(sp)
                ld   s0, 56(sp)
                ld   ra, 64(sp)
                addi sp, sp, 72
                ret
            """;
}

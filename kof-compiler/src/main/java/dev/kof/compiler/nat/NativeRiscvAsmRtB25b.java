package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3b.2): fatia 25b — kof.uuid v7 (RFC 9562). Espelha o v4
// (B25): 16 bytes via getrandom(2) (syscall 278 — mesmo probe qemu; rc!=16 =>
// NULL, nunca uuid fraco silencioso R11) e SOBREPOE b0..b5 = unix-ts-ms 48
// bits big-endian (kof_time_now epoch-ms), b6 nibble-alto='7' (version), b8
// variante 10xx (mesma MASCARA do v4 — paridade JVM/JS, nao o forca '8').
// b7 = rand_a low, b9..b15 = rand_b (deixados aleatorios — o RFC so exige
// ver/variant fixos e ts em b0..b5; rand_a/rand_b = entropia). ts<0 (relogio
// quebrado) => NULL (R6); ts>2^48 trunca p/ low-48 (formato, ano ~10889).
//
// kof_time_now retorna epoch-ms em a0 (B0). Os shifts de extração usam slli/
// srli/andi (immediates >12 bits nao existem; sequencia abaixo materializa).
// Loop de formatacao hex e IDENTICO ao v4 (labels .Lu7_, sub .Lu7_hx t-only).
// Frame -80: buf sp+0..15, s-regs 24..72, ra 72 (mesmo B25 — alloc 16-align).
// ⚠ `.section .text` NO TOPO (licao 7be4fd0a).
public final class NativeRiscvAsmRtB25b {

    static final String RISCV_RUNTIME_ASM_B_25B = """

            .section .text
            .globl kof_uuid_v7
            kof_uuid_v7:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s5, 32(sp)
                # --- getrandom(sp+0, 16, 0) -> buf[0..15] ---
                addi a0, sp, 0
                li   a1, 16
                li   a2, 0
                li   a7, 278
                ecall
                li   t1, 16
                bne  a0, t1, .Lu7_null       # rc != 16 (falha/parcial) => null
                # --- ts = kof_time_now(); bltz => null (relogio quebrado) ---
                call kof_time_now
                bltz a0, .Lu7_null
                # b5 = ts & 0xff            (a0 intacto: shifts s64 em t0)
                andi t0, a0, 255
                sb   t0, 5(sp)
                # b4 = (ts>>8)&0xff
                srli t0, a0, 8
                andi t0, t0, 255
                sb   t0, 4(sp)
                # b3 = (ts>>16)&0xff
                srli t0, a0, 16
                andi t0, t0, 255
                sb   t0, 3(sp)
                # b2 = (ts>>24)&0xff
                srli t0, a0, 24
                andi t0, t0, 255
                sb   t0, 2(sp)
                # b1 = (ts>>32)&0xff
                srli t0, a0, 32
                andi t0, t0, 255
                sb   t0, 1(sp)
                # b0 = (ts>>40)&0xff
                srli t0, a0, 40
                andi t0, t0, 255
                sb   t0, 0(sp)
                # --- version em b6: (b6 & 0x0f) | 0x70 ---
                lbu  t0, 6(sp)
                andi t0, t0, 15
                ori  t0, t0, 112
                sb   t0, 6(sp)
                # --- variante em b8: (b8 & 0x3f) | 0x80 (mesma mascara v4) ---
                lbu  t0, 8(sp)
                andi t0, t0, 63
                ori  t0, t0, 128
                sb   t0, 8(sp)
                # --- alloc String 36: 61 -> 64 (igual v4) ---
                li   a0, 61
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s0, a0
                li   t0, 1
                sw   t0, 0(s0)
                sw   zero, 4(s0)
                sd   zero, 8(s0)
                li   t0, 36
                sw   t0, 16(s0)
                sw   zero, 20(s0)
                li   s1, 0
                li   s2, 0
            .Lu7_loop:
                li   t1, 16
                bge  s1, t1, .Lu7_term
                add  t2, sp, s1
                lbu  t3, 0(t2)
                srli a0, t3, 4
                call .Lu7_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                andi a0, t3, 15
                call .Lu7_hx
                add  t4, s0, s2
                addi t4, t4, 24
                sb   a0, 0(t4)
                addi s2, s2, 1
                li   t1, 3
                beq  s1, t1, .Lu7_dash
                li   t1, 5
                beq  s1, t1, .Lu7_dash
                li   t1, 7
                beq  s1, t1, .Lu7_dash
                li   t1, 9
                beq  s1, t1, .Lu7_dash
                j    .Lu7_next
            .Lu7_dash:
                add  t4, s0, s2
                addi t4, t4, 24
                li   t3, 45
                sb   t3, 0(t4)
                addi s2, s2, 1
            .Lu7_next:
                addi s1, s1, 1
                j    .Lu7_loop
            .Lu7_term:
                add  t0, s0, s2
                addi t0, t0, 24
                sb   zero, 0(t0)
                mv   a0, s0
                j    .Lu7_done
            .Lu7_null:
                li   a0, 0
            .Lu7_done:
                ld   s5, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # sub .Lu7_hx: a0 = nibble 0..15 -> char hex minusculo (t-only).
            .Lu7_hx:
                li   t0, 10
                bltu a0, t0, .Lu7_h9
                addi a0, a0, 87
                ret
            .Lu7_h9:
                addi a0, a0, 48
                ret
                        """;
}

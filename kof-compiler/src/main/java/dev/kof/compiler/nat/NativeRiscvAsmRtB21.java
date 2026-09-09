package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3.2): fatia 21 de RISCV_RUNTIME_ASM_B — kof.strings
// removeWhitespace/normalizeWhitespace. WS = {9..13,32}; >=128 NÃO é WS
// (paridade capitalize; mesma máquina JVM/JS/x86). Buffer len+25 (a saída
// nunca excede o input). null/vazia => original. Chama kof_alloc => salva
// ra+s0..s5; pós-call só s-regs (a/t caller-saved).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB21 {

    static final String RISCV_RUNTIME_ASM_B_21 = """

            .section .text
            .globl kof_strings_removeWhitespace
            kof_strings_removeWhitespace:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a0              # v
                beqz s0, .Lv_rw_o
                lw   s1, 16(s0)          # len
                blez s1, .Lv_rw_o
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # novo
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
            .Lv_rw_loop:
                bge  s3, s1, .Lv_rw_term
                add  t0, s0, s3
                lbu  t0, 24(t0)          # c
                addi s3, s3, 1
                li   t1, 32
                beq  t0, t1, .Lv_rw_loop
                li   t1, 9
                blt  t0, t1, .Lv_rw_put
                li   t1, 13
                ble  t0, t1, .Lv_rw_loop
            .Lv_rw_put:
                add  t2, s2, s4
                sb   t0, 24(t2)
                addi s4, s4, 1
                j    .Lv_rw_loop
            .Lv_rw_term:
                sw   s4, 16(s2)
                add  t0, s2, s4
                sb   zero, 24(t0)
                mv   a0, s2
                j    .Lv_rw_done
            .Lv_rw_o:
                mv   a0, s0
            .Lv_rw_done:
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            .globl kof_strings_normalizeWhitespace
            kof_strings_normalizeWhitespace:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0              # v
                beqz s0, .Lv_nw_o
                lw   s1, 16(s0)
                blez s1, .Lv_nw_o
                addi a0, s1, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
                li   s5, 0               # inws|started: bit0=inws bit1=started
            .Lv_nw_loop:
                bge  s3, s1, .Lv_nw_term
                add  t0, s0, s3
                lbu  t0, 24(t0)          # c
                addi s3, s3, 1
                li   t1, 32
                beq  t0, t1, .Lv_nw_ws
                li   t1, 9
                blt  t0, t1, .Lv_nw_nws
                li   t1, 13
                ble  t0, t1, .Lv_nw_ws
                j    .Lv_nw_nws
            .Lv_nw_ws:
                andi t1, s5, 2           # started?
                beqz t1, .Lv_nw_loop
                ori  s5, s5, 1           # inws=1
                j    .Lv_nw_loop
            .Lv_nw_nws:
                andi t1, s5, 1
                beqz t1, .Lv_nw_emit
                add  t2, s2, s4
                li   t1, 32
                sb   t1, 24(t2)          # espaço colapsado
                addi s4, s4, 1
                andi s5, s5, -2          # inws=0
            .Lv_nw_emit:
                add  t2, s2, s4
                sb   t0, 24(t2)
                addi s4, s4, 1
                ori  s5, s5, 2           # started=1
                j    .Lv_nw_loop
            .Lv_nw_term:
                sw   s4, 16(s2)
                add  t0, s2, s4
                sb   zero, 24(t0)
                mv   a0, s2
                j    .Lv_nw_done
            .Lv_nw_o:
                mv   a0, s0
            .Lv_nw_done:
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}

package dev.kof.compiler.nat;

// FASE 3 (STDLIB S3.1): fatia 20 de RISCV_RUNTIME_ASM_B — kof.strings
// escapeHtml. MESMA máquina de JVM/JS/x86 (oracle Python, 28 casos):
// 5 chars -> entidade (&amp; &lt; &gt; &quot; &#39;); demais bytes copiados
// (>=128 passa — paridade capitalize). Buffer 6*len; null/"" => original.
// Chama kof_alloc => salva ra + s0..s5; frame 64 (16-align p/ kof_alloc).
// Após o call, &v.bytes RECOMPUTADO (a-regs/t-regs são caller-saved).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB20 {

    static final String RISCV_RUNTIME_ASM_B_20 = """

            .section .text
            .globl kof_strings_escapeHtml
            kof_strings_escapeHtml:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0              # v
                beqz s0, .Lv_esc_o
                lw   s1, 16(s0)          # len
                blez s1, .Lv_esc_o
                li   t0, 6
                mul  t0, t0, s1          # 6*len
                addi t0, t0, 25
                addi a0, t0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s2, a0              # novo
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                li   s3, 0               # i
                li   s4, 0               # pos
            .Lv_esc_loop:
                bge  s3, s1, .Lv_esc_term
                add  a1, s0, s3
                lbu  t0, 24(a1)          # c = v.bytes[i]
                li   t1, 38
                beq  t0, t1, .Lv_esc_amp
                li   t1, 60
                beq  t0, t1, .Lv_esc_lt
                li   t1, 62
                beq  t0, t1, .Lv_esc_gt
                li   t1, 34
                beq  t0, t1, .Lv_esc_qu
                li   t1, 39
                beq  t0, t1, .Lv_esc_ap
                add  t2, s2, s4
                sb   t0, 24(t2)
                addi s4, s4, 1
                addi s3, s3, 1
                j    .Lv_esc_loop
            .Lv_esc_amp:
                add  t2, s2, s4
                li   t3, 38
                sb   t3, 24(t2)
                li   t3, 97
                sb   t3, 25(t2)
                li   t3, 109
                sb   t3, 26(t2)
                li   t3, 112
                sb   t3, 27(t2)
                li   t3, 59
                sb   t3, 28(t2)
                addi s4, s4, 5
                j    .Lv_esc_adv
            .Lv_esc_lt:
                add  t2, s2, s4
                li   t3, 38
                sb   t3, 24(t2)
                li   t3, 108
                sb   t3, 25(t2)
                li   t3, 116
                sb   t3, 26(t2)
                li   t3, 59
                sb   t3, 27(t2)
                addi s4, s4, 4
                j    .Lv_esc_adv
            .Lv_esc_gt:
                add  t2, s2, s4
                li   t3, 38
                sb   t3, 24(t2)
                li   t3, 103
                sb   t3, 25(t2)
                li   t3, 116
                sb   t3, 26(t2)
                li   t3, 59
                sb   t3, 27(t2)
                addi s4, s4, 4
                j    .Lv_esc_adv
            .Lv_esc_qu:
                add  t2, s2, s4
                li   t3, 38
                sb   t3, 24(t2)
                li   t3, 113
                sb   t3, 25(t2)
                li   t3, 117
                sb   t3, 26(t2)
                li   t3, 111
                sb   t3, 27(t2)
                li   t3, 116
                sb   t3, 28(t2)
                li   t3, 59
                sb   t3, 29(t2)
                addi s4, s4, 6
                j    .Lv_esc_adv
            .Lv_esc_ap:
                add  t2, s2, s4
                li   t3, 38
                sb   t3, 24(t2)
                li   t3, 35
                sb   t3, 25(t2)
                li   t3, 51
                sb   t3, 26(t2)
                li   t3, 57
                sb   t3, 27(t2)
                li   t3, 59
                sb   t3, 28(t2)
                addi s4, s4, 5
            .Lv_esc_adv:
                addi s3, s3, 1
                j    .Lv_esc_loop
            .Lv_esc_term:
                sw   s4, 16(s2)
                add  t0, s2, s4
                sb   zero, 24(t0)
                mv   a0, s2
                j    .Lv_esc_done
            .Lv_esc_o:
                mv   a0, s0
            .Lv_esc_done:
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

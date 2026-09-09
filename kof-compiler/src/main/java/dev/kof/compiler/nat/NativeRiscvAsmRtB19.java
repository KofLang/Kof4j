package dev.kof.compiler.nat;

// FASE 3 (STDLIB S6c): fatia 19 de RISCV_RUNTIME_ASM_B — kof.validation
// isDomain. MESMA máquina de RuntimeValidationNet (x86) / JVM / JS,
// validada em Python (oracle 28 casos) e no harness C (x86 28/28).
// Subconjunto RFC 1123 declarado (v1, idem isIpv6): labels [A-Za-z0-9-]
// 1..63 sem '-' nas pontas; >=2 labels; TLD >=2 só letras; total<=253;
// ponto final/duplo/inicial => false; sem underscore/IDN (punycode xn--
// é ASCII e passa). Sem call interna => só t/a-regs + s0..s4 (frame 48,
// 16-alinhado; classificação em faixas UNSIGNED bltu).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB19 {

    static final String RISCV_RUNTIME_ASM_B_19 = """

            .section .text
            .globl kof_validation_isDomain
            kof_validation_isDomain:
                addi sp, sp, -48
                sd   s0, 40(sp)
                sd   s1, 32(sp)
                sd   s2, 24(sp)
                sd   s3, 16(sp)
                sd   s4, 8(sp)
                mv   s0, a0              # str
                beqz s0, .Lv_dm_f
                lw   s2, 16(s0)          # len
                beqz s2, .Lv_dm_f
                li   t1, 253
                bgt  s2, t1, .Lv_dm_f
                li   s1, 0               # start
                li   s3, 0               # labels
                li   t0, 0               # i
            .Lv_dm_scan:
                bge  t0, s2, .Lv_dm_at
                add  t2, s0, t0
                lbu  t2, 24(t2)
                li   t3, 46
                beq  t2, t3, .Lv_dm_at
                addi t0, t0, 1
                j    .Lv_dm_scan
            .Lv_dm_at:
                mv   s4, s1              # último start (vira TLD se for o fim)
                sub  t4, t0, s1          # llen
                beqz t4, .Lv_dm_f        # label vazio
                li   t3, 63
                bgt  t4, t3, .Lv_dm_f
                add  t2, s0, s1
                lbu  t2, 24(t2)
                li   t3, 45
                beq  t2, t3, .Lv_dm_f    # '-' inicial
                addi t5, t0, -1
                add  t2, s0, t5
                lbu  t2, 24(t2)
                beq  t2, t3, .Lv_dm_f    # '-' final
                mv   t6, s1              # j
            .Lv_dm_chk:
                bge  t6, t0, .Lv_dm_lok
                add  a1, s0, t6
                lbu  a1, 24(a1)          # c
                li   t3, 45
                beq  a1, t3, .Lv_dm_nx   # '-' interno ok
                addi a2, a1, -48
                li   t3, 10
                bltu a2, t3, .Lv_dm_nx   # dígito
                addi a2, a1, -97
                li   t3, 26
                bltu a2, t3, .Lv_dm_nx   # a-f..z
                addi a2, a1, -65
                li   t3, 26
                bltu a2, t3, .Lv_dm_nx   # A-F..Z
                j    .Lv_dm_f
            .Lv_dm_nx:
                addi t6, t6, 1
                j    .Lv_dm_chk
            .Lv_dm_lok:
                addi s3, s3, 1
                addi t5, t0, 1
                mv   s1, t5              # start = i+1
                addi t0, t0, 1           # consome '.'
                blt  t0, s2, .Lv_dm_scan
                beq  t0, s2, .Lv_dm_scan # i==len: label virtual vazio pega fim? x86: jle scan
                j    .Lv_dm_final
            .Lv_dm_final:
                li   t3, 2
                blt  s3, t3, .Lv_dm_f
                sub  t4, s2, s4          # tlen
                li   t3, 2
                blt  t4, t3, .Lv_dm_f
                mv   t6, s4
            .Lv_dm_tld:
                bge  t6, s2, .Lv_dm_t
                add  a1, s0, t6
                lbu  a1, 24(a1)
                addi a2, a1, -97
                li   t3, 26
                bltu a2, t3, .Lv_dm_tnx
                addi a2, a1, -65
                li   t3, 26
                bltu a2, t3, .Lv_dm_tnx
                j    .Lv_dm_f
            .Lv_dm_tnx:
                addi t6, t6, 1
                j    .Lv_dm_tld
            .Lv_dm_t:
                li   a0, 1
                j    .Lv_dm_done
            .Lv_dm_f:
                li   a0, 0
            .Lv_dm_done:
                ld   s4, 8(sp)
                ld   s3, 16(sp)
                ld   s2, 24(sp)
                ld   s1, 32(sp)
                ld   s0, 40(sp)
                addi sp, sp, 48
                ret
            """;
}

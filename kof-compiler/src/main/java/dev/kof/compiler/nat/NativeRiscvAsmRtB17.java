package dev.kof.compiler.nat;

// FASE 3 (STDLIB S6b): fatia 17 de RISCV_RUNTIME_ASM_B — kof.validation
// isCreditCard (Luhn). Espelha RuntimeValidationNet (x86) / JVM / JS.
// Dígitos p/ buf[19] na pilha; >19 => false; <12 => false; dobra
// ímpares-contando-da-direita (v*2; v>9 => v-9); soma % 10 == 0.
// rem por 10 é seguro: soma <= 19*9 = 171 (positivo) — tradutor aarch sdiv+msub.
// Classificação de byte usa faixas UNSIGNED (bltu/bgeu) — bytes 0..255.
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB17 {

    static final String RISCV_RUNTIME_ASM_B_17 = """

            .section .text
            # kof_validation_isCreditCard(a0=str) -> a0 0/1
            .globl kof_validation_isCreditCard
            kof_validation_isCreditCard:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s0, a0              # str
                mv   s1, sp              # buf (0..18 em (s1))
                li   s2, 0               # n
                li   t0, 0               # i
                beqz s0, .Lv_cc_f
                lw   t1, 16(s0)          # len
            .Lv_cc_collect:
                bge  t0, t1, .Lv_cc_nchk
                add  a1, s0, t0
                lbu  a1, 24(a1)          # c
                addi t0, t0, 1
                li   a2, 48
                blt  a1, a2, .Lv_cc_collect
                li   a2, 57
                bgt  a1, a2, .Lv_cc_collect
                li   a2, 19
                bge  s2, a2, .Lv_cc_f    # >19 dígitos
                addi a1, a1, -48
                add  a2, s1, s2
                sb   a1, 0(a2)
                addi s2, s2, 1
                j    .Lv_cc_collect
            .Lv_cc_nchk:
                li   a2, 12
                blt  s2, a2, .Lv_cc_f    # <12
                li   s3, 0               # j
                li   t2, 0               # sum
            .Lv_cc_sum:
                bge  s3, s2, .Lv_cc_mod
                addi a3, s2, -1
                sub  a3, a3, s3          # n-1-j
                andi a3, a3, 1           # impar-contando-da-direita?
                add  a4, s1, s3
                lbu  t3, 0(a4)           # v (zero-extend via lbu; buf guarda 0..9)
                beqz a3, .Lv_cc_add
                add  t3, t3, t3          # v*2
                li   a5, 9
                ble  t3, a5, .Lv_cc_add
                addi t3, t3, -9
            .Lv_cc_add:
                add  t2, t2, t3
                addi s3, s3, 1
                j    .Lv_cc_sum
            .Lv_cc_mod:
                li   a2, 10
                rem  t4, t2, a2          # t2 >= 0 => rem == floor mod
                bnez t4, .Lv_cc_f
                li   a0, 1
                j    .Lv_cc_done
            .Lv_cc_f:
                li   a0, 0
            .Lv_cc_done:
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}

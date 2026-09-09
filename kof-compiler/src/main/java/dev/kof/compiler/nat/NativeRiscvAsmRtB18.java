package dev.kof.compiler.nat;

// FASE 3 (STDLIB S6b.3): fatia 18 de RISCV_RUNTIME_ASM_B — kof.validation
// isIpv6. MESMA máquina de RuntimeValidationNet (x86) / JVM / JS (validada
// em Python contra ipaddress, 30 casos; x86==JVM==JS confirmado no diff
// dos 30). Grupos 1..4 hex; ':' após grupo; '::' no MÁXIMO uma vez
// (sentinela dbl=-1 = ausente); sem '::' exige g==8; com '::' exige g<8.
// v1: sem forma mista (::ffff:1.2.3.4) nem zona (%eth0) — documentado.
// Classificação de byte em faixas UNSIGNED (bltu/bgeu) — como B15.
// Sem alocação nem call interna => usa só t-regs/a-regs (nenhum s-reg vivo).
//
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB18 {

    static final String RISCV_RUNTIME_ASM_B_18 = """

            .section .text
            .globl kof_validation_isIpv6
            kof_validation_isIpv6:
                beqz a0, .Lv_v6_f
                lw   t4, 16(a0)          # len
                beqz t4, .Lv_v6_f
                addi a1, a0, 24          # &bytes[0]
                li   t0, 0               # i
                li   t1, 0               # g
                li   t2, -1              # dbl
            .Lv_v6_loop:
                bge  t0, t4, .Lv_v6_end
                li   t3, 0               # h
            .Lv_v6_hex:
                bge  t0, t4, .Lv_v6_gend
                add  a2, a1, t0
                lbu  a2, 0(a2)           # c
                addi a3, a2, -48
                li   a4, 10
                bltu a3, a4, .Lv_v6_ishex   # '0'..'9'
                addi a3, a2, -97
                li   a4, 6
                bltu a3, a4, .Lv_v6_ishex   # 'a'..'f'
                addi a3, a2, -65
                li   a4, 6
                bltu a3, a4, .Lv_v6_ishex   # 'A'..'F'
                j    .Lv_v6_gend
            .Lv_v6_ishex:
                li   a4, 3
                blt  a4, t3, .Lv_v6_f       # 4ª+ hex (t3>=4) => h>4 ao somar
                addi t3, t3, 1
                addi t0, t0, 1
                j    .Lv_v6_hex
            .Lv_v6_gend:
                beqz t3, .Lv_v6_nohex
                addi t1, t1, 1
                li   a4, 8
                blt  a4, t1, .Lv_v6_f       # g>8
                bge  t0, t4, .Lv_v6_end
                add  a2, a1, t0
                lbu  a2, 0(a2)
                li   a4, 58
                bne  a2, a4, .Lv_v6_f       # precisa de ':'
                addi t0, t0, 1              # consome ':'
                bge  t0, t4, .Lv_v6_f       # ':' terminal ("1:")
                add  a2, a1, t0
                lbu  a2, 0(a2)
                li   a4, 58
                bne  a2, a4, .Lv_v6_loop    # ':' normal
                bgez t2, .Lv_v6_f           # segundo '::'
                mv   t2, t0
                addi t0, t0, 1              # consome 2º ':'
                j    .Lv_v6_loop
            .Lv_v6_nohex:
                addi a3, t0, 1
                bge  a3, t4, .Lv_v6_f
                add  a2, a1, t0
                lbu  a2, 0(a2)
                li   a4, 58
                bne  a2, a4, .Lv_v6_f
                add  a5, a1, a3
                lbu  a5, 0(a5)
                li   a4, 58
                bne  a5, a4, .Lv_v6_f
                bgez t2, .Lv_v6_f
                mv   t2, t0
                addi t0, t0, 2
                j    .Lv_v6_loop
            .Lv_v6_end:
                bgez t2, .Lv_v6_withd
                li   a4, 8
                bne  t1, a4, .Lv_v6_f       # sem '::': g==8
                j    .Lv_v6_t
            .Lv_v6_withd:
                li   a4, 8
                bge  t1, a4, .Lv_v6_f       # com '::': g<8
            .Lv_v6_t:
                li   a0, 1
                ret
            .Lv_v6_f:
                li   a0, 0
                ret
            """;
}

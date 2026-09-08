package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2a): fatia 6 de RISCV_RUNTIME_ASM_B — kof.strings predicados.
// Layout de string Kof: length em 16(a0), bytes ASCII em 24(a0).
// Convenção de paridade (matriz stdstrings): "" / null => false.
public final class NativeRiscvAsmRtB6 {

    static final String RISCV_RUNTIME_ASM_B_6 = """

            # ── kof.strings (STDLIB S2a) — predicados de char ─────────

            # kof_strings_isAlpha(a0=str) -> 1/0 (só [A-Za-z], não-vazio)
            .globl kof_strings_isAlpha
            kof_strings_isAlpha:
                beqz a0, .Lv_str_alpha_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_alpha_f
                addi t1, a0, 24
                li   t3, 0
            .Lv_str_alpha_loop:
                bge  t3, t0, .Lv_str_alpha_t
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   t4, 65
                blt  t2, t4, .Lv_str_alpha_f
                li   t4, 90
                ble  t2, t4, .Lv_str_alpha_next
                li   t4, 97
                blt  t2, t4, .Lv_str_alpha_f
                li   t4, 122
                bgt  t2, t4, .Lv_str_alpha_f
            .Lv_str_alpha_next:
                addi t3, t3, 1
                j    .Lv_str_alpha_loop
            .Lv_str_alpha_t:
                li   a0, 1
                ret
            .Lv_str_alpha_f:
                li   a0, 0
                ret

            # kof_strings_isNumeric(a0=str) -> 1/0 (só [0-9], não-vazio)
            .globl kof_strings_isNumeric
            kof_strings_isNumeric:
                beqz a0, .Lv_str_num_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_num_f
                addi t1, a0, 24
                li   t3, 0
            .Lv_str_num_loop:
                bge  t3, t0, .Lv_str_num_t
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   t4, 48
                blt  t2, t4, .Lv_str_num_f
                li   t4, 57
                bgt  t2, t4, .Lv_str_num_f
                addi t3, t3, 1
                j    .Lv_str_num_loop
            .Lv_str_num_t:
                li   a0, 1
                ret
            .Lv_str_num_f:
                li   a0, 0
                ret

            # kof_strings_isAlphaNumeric(a0=str) -> 1/0 (só [A-Za-z0-9], não-vazio)
            .globl kof_strings_isAlphaNumeric
            kof_strings_isAlphaNumeric:
                beqz a0, .Lv_str_an_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_an_f
                addi t1, a0, 24
                li   t3, 0
            .Lv_str_an_loop:
                bge  t3, t0, .Lv_str_an_t
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   t4, 48
                blt  t2, t4, .Lv_str_an_alpha
                li   t4, 57
                ble  t2, t4, .Lv_str_an_next
            .Lv_str_an_alpha:
                li   t4, 65
                blt  t2, t4, .Lv_str_an_f
                li   t4, 90
                ble  t2, t4, .Lv_str_an_next
                li   t4, 97
                blt  t2, t4, .Lv_str_an_f
                li   t4, 122
                bgt  t2, t4, .Lv_str_an_f
            .Lv_str_an_next:
                addi t3, t3, 1
                j    .Lv_str_an_loop
            .Lv_str_an_t:
                li   a0, 1
                ret
            .Lv_str_an_f:
                li   a0, 0
                ret

            # kof_strings_isAscii(a0=str) -> 1/0 (não-vazio, todos os bytes < 128)
            .globl kof_strings_isAscii
            kof_strings_isAscii:
                beqz a0, .Lv_str_asc_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_asc_f
                addi t1, a0, 24
                li   t3, 0
            .Lv_str_asc_loop:
                bge  t3, t0, .Lv_str_asc_t
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   t4, 128
                bge  t2, t4, .Lv_str_asc_f
                addi t3, t3, 1
                j    .Lv_str_asc_loop
            .Lv_str_asc_t:
                li   a0, 1
                ret
            .Lv_str_asc_f:
                li   a0, 0
                ret

            """;
}

package dev.kof.compiler.nat;

// FASE 3 (STDLIB S2a): fatia 6 de RISCV_RUNTIME_ASM_B — kof.strings predicados.
// Layout de string Kof: length em 16(a0), bytes ASCII em 24(a0).
// Convenção de paridade (matriz stdstrings): "" / null => false.
public final class NativeRiscvAsmRtB6 {

    static final String RISCV_RUNTIME_ASM_B_6 = """

            # ── kof.strings (STDLIB S2a) — predicados de char ─────────

            # kof_strings_isAlpha(a0=str) -> 1/0 (só [A-Za-z], não-vazio)
            .section .text
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

            # kof_strings_isUpperCase(a0=str) -> 1/0
            .globl kof_strings_isUpperCase
            kof_strings_isUpperCase:
                beqz a0, .Lv_str_uu_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_uu_f
                addi t1, a0, 24
                li   t3, 0
                li   t4, 0              # hasLetter
            .Lv_str_uu_loop:
                bge  t3, t0, .Lv_str_uu_chk
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   a1, 97
                bge  t2, a1, .Lv_str_uu_a
                li   a1, 65
                blt  t2, a1, .Lv_str_uu_nx
                li   a1, 90
                bgt  t2, a1, .Lv_str_uu_nx
                li   t4, 1
                j    .Lv_str_uu_nx
            .Lv_str_uu_a:
                li   a1, 122
                bgt  t2, a1, .Lv_str_uu_nx
                j    .Lv_str_uu_f       # [a-z] => false
            .Lv_str_uu_nx:
                addi t3, t3, 1
                j    .Lv_str_uu_loop
            .Lv_str_uu_chk:
                beqz t4, .Lv_str_uu_f
                li   a0, 1
                ret
            .Lv_str_uu_f:
                li   a0, 0
                ret

            # kof_strings_isLowerCase(a0=str) -> 1/0
            .globl kof_strings_isLowerCase
            kof_strings_isLowerCase:
                beqz a0, .Lv_str_ll_f
                lw   t0, 16(a0)
                blez t0, .Lv_str_ll_f
                addi t1, a0, 24
                li   t3, 0
                li   t4, 0
            .Lv_str_ll_loop:
                bge  t3, t0, .Lv_str_ll_chk
                add  t2, t1, t3
                lbu  t2, 0(t2)
                li   a1, 65
                blt  t2, a1, .Lv_str_ll_n
                li   a1, 90
                bgt  t2, a1, .Lv_str_ll_n
                j    .Lv_str_ll_f       # [A-Z] => false
            .Lv_str_ll_n:
                li   a1, 97
                blt  t2, a1, .Lv_str_ll_nx
                li   a1, 122
                bgt  t2, a1, .Lv_str_ll_nx
                li   t4, 1
                j    .Lv_str_ll_nx
            .Lv_str_ll_nx:
                addi t3, t3, 1
                j    .Lv_str_ll_loop
            .Lv_str_ll_chk:
                beqz t4, .Lv_str_ll_f
                li   a0, 1
                ret
            .Lv_str_ll_f:
                li   a0, 0
                ret

            # kof_strings_count(a0=s, a1=sub) -> Int (não-sobrepostas)
            .globl kof_strings_count
            kof_strings_count:
                addi sp, sp, -16
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                beqz a0, .Lv_str_cnt_f
                beqz a1, .Lv_str_cnt_f
                lw   s0, 16(a0)          # len_s
                blez s0, .Lv_str_cnt_f
                lw   t6, 16(a1)          # len_sub
                blez t6, .Lv_str_cnt_f
                bgt  t6, s0, .Lv_str_cnt_f
                addi t0, a0, 24          # s ptr
                addi t1, a1, 24          # sub ptr
                li   t2, 0               # count
                li   t3, 0               # i
            .Lv_str_cnt_outer:
                mv   t4, s0
                sub  t4, t4, t6          # último início
                bgt  t3, t4, .Lv_str_cnt_done
                li   t5, 0               # j
            .Lv_str_cnt_inner:
                bge  t5, t6, .Lv_str_cnt_m
                add  a1, t3, t5
                add  a1, t0, a1
                lbu  a1, 0(a1)           # s[i+j]
                add  a2, t1, t5
                lbu  a2, 0(a2)           # sub[j]
                bne  a1, a2, .Lv_str_cnt_nm
                addi t5, t5, 1
                j    .Lv_str_cnt_inner
            .Lv_str_cnt_m:
                addi t2, t2, 1
                add  t3, t3, t6
                j    .Lv_str_cnt_outer
            .Lv_str_cnt_nm:
                addi t3, t3, 1
                j    .Lv_str_cnt_outer
            .Lv_str_cnt_done:
                mv   a0, t2
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                addi sp, sp, 16
                ret
            .Lv_str_cnt_f:
                li   a0, 0
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                addi sp, sp, 16
                ret

            """;
}

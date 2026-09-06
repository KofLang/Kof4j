package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 3 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRtB3 {

    private NativeRiscvAsmRtB3() {}

    static final String RISCV_RUNTIME_ASM_B_3 = """
            .globl kof_validation_notBlank
            kof_validation_notBlank:
                beqz a0, .Lv_nb_false
                lw   t1, 16(a0)
                beqz t1, .Lv_nb_false
                addi t2, a0, 24
                li   t0, 0
            .Lv_nb_loop:
                bge  t0, t1, .Lv_nb_false
                add  t3, t2, t0
                lbu  t3, 0(t3)
                li   t4, 32
                beq  t3, t4, .Lv_nb_next
                li   t4, 9
                beq  t3, t4, .Lv_nb_next
                li   t4, 10
                beq  t3, t4, .Lv_nb_next
                li   t4, 13
                beq  t3, t4, .Lv_nb_next
                li   a0, 1
                ret
            .Lv_nb_next:
                addi t0, t0, 1
                j    .Lv_nb_loop
            .Lv_nb_false:
                li   a0, 0
                ret

            .globl kof_validation_minLength
            kof_validation_minLength:
                beqz a0, .Lv_min_false
                lw   t0, 16(a0)
                bge  t0, a1, .Lv_min_true
            .Lv_min_false:
                li   a0, 0
                ret
            .Lv_min_true:
                li   a0, 1
                ret

            .globl kof_validation_maxLength
            kof_validation_maxLength:
                beqz a0, .Lv_max_true
                lw   t0, 16(a0)
                ble  t0, a1, .Lv_max_true
                li   a0, 0
                ret
            .Lv_max_true:
                li   a0, 1
                ret

            .globl kof_validation_lengthBetween
            kof_validation_lengthBetween:
                beqz a0, .Lv_bet_false
                lw   t0, 16(a0)
                blt  t0, a1, .Lv_bet_false
                bgt  t0, a2, .Lv_bet_false
                li   a0, 1
                ret
            .Lv_bet_false:
                li   a0, 0
                ret

            .globl kof_validation_isEmail
            kof_validation_isEmail:
                beqz a0, .Lv_em_false
                lw   t1, 16(a0)
                li   t4, 3
                blt  t1, t4, .Lv_em_false
                addi t2, a0, 24
                li   t0, 0
                li   t3, 0
                li   t4, 0
                li   t5, 0
            .Lv_em_loop:
                bge  t0, t1, .Lv_em_check
                add  t6, t2, t0
                lbu  t6, 0(t6)
                li   a2, 32
                beq  t6, a2, .Lv_em_false
                li   a2, 9
                beq  t6, a2, .Lv_em_false
                li   a2, 64
                bne  t6, a2, .Lv_em_notat
                addi t3, t3, 1
                mv   t4, t0
                j    .Lv_em_next
            .Lv_em_notat:
                li   a2, 46
                bne  t6, a2, .Lv_em_next
                beqz t3, .Lv_em_next
                addi a2, t4, 1
                bgt  a2, t0, .Lv_em_next
                li   t5, 1
            .Lv_em_next:
                addi t0, t0, 1
                j    .Lv_em_loop
            .Lv_em_check:
                li   a2, 1
                bne  t3, a2, .Lv_em_false
                beqz t4, .Lv_em_false
                beqz t5, .Lv_em_false
                addi t6, t1, -1
                beq  t4, t6, .Lv_em_false
                add  t6, t2, t1
                addi t6, t6, -1
                lbu  t6, 0(t6)
                li   a2, 46
                beq  t6, a2, .Lv_em_false
                li   a0, 1
                ret
            .Lv_em_false:
                li   a0, 0
                ret

            .globl kof_validation_isUrl
            kof_validation_isUrl:
                beqz a0, .Lv_url_false
                lw   t1, 16(a0)
                li   t4, 7
                blt  t1, t4, .Lv_url_false
                addi t2, a0, 24
                lbu  t0, 0(t2)
                li   t3, 104
                bne  t0, t3, .Lv_url_false
                lbu  t0, 1(t2)
                li   t3, 116
                bne  t0, t3, .Lv_url_false
                lbu  t0, 2(t2)
                bne  t0, t3, .Lv_url_false
                lbu  t0, 3(t2)
                li   t3, 112
                bne  t0, t3, .Lv_url_false
                lbu  t0, 4(t2)
                li   t3, 58
                bne  t0, t3, .Lv_url_check_https
                lbu  t0, 5(t2)
                li   t3, 47
                bne  t0, t3, .Lv_url_false
                lbu  t0, 6(t2)
                bne  t0, t3, .Lv_url_false
                li   a0, 1
                ret
            .Lv_url_check_https:
                li   t4, 8
                blt  t1, t4, .Lv_url_false
                lbu  t0, 4(t2)
                li   t3, 115
                bne  t0, t3, .Lv_url_false
                lbu  t0, 5(t2)
                li   t3, 58
                bne  t0, t3, .Lv_url_false
                lbu  t0, 6(t2)
                li   t3, 47
                bne  t0, t3, .Lv_url_false
                lbu  t0, 7(t2)
                bne  t0, t3, .Lv_url_false
                li   a0, 1
                ret
            .Lv_url_false:
                li   a0, 0
                ret

            .globl kof_validation_matches
            kof_validation_matches:
                beqz a0, .Lv_mat_false
                beqz a1, .Lv_mat_false
                lw   t1, 16(a0)
                lw   t2, 16(a1)
                beqz t2, .Lv_mat_true
                bgt  t2, t1, .Lv_mat_false
                addi t3, a0, 24
                addi t4, a1, 24
                li   t0, 0
            .Lv_mat_outer:
                sub  t5, t1, t0
                blt  t5, t2, .Lv_mat_false
                li   t5, 0
            .Lv_mat_inner:
                bge  t5, t2, .Lv_mat_true
                add  t6, t3, t0
                add  t6, t6, t5
                lbu  t6, 0(t6)
                add  a2, t4, t5
                lbu  a2, 0(a2)
                bne  t6, a2, .Lv_mat_next
                addi t5, t5, 1
                j    .Lv_mat_inner
            .Lv_mat_next:
                addi t0, t0, 1
                j    .Lv_mat_outer
            .Lv_mat_true:
                li   a0, 1
                ret
            .Lv_mat_false:
                li   a0, 0
                ret

            .globl kof_validation_isInt
            kof_validation_isInt:
                beqz a0, .Lv_int_false
                lw   t1, 16(a0)
                beqz t1, .Lv_int_false
                addi t2, a0, 24
                lbu  t0, 0(t2)
                li   t3, 45
                beq  t0, t3, .Lv_int_sign
                li   t3, 43
                beq  t0, t3, .Lv_int_sign
                j    .Lv_int_digits
            .Lv_int_sign:
                li   t0, 1
                bge  t0, t1, .Lv_int_false
                j    .Lv_int_digits_start
            .Lv_int_digits:
                li   t0, 0
            .Lv_int_digits_start:
                li   t3, 0
            .Lv_int_loop:
                bge  t0, t1, .Lv_int_check
                add  t4, t2, t0
                lbu  t4, 0(t4)
                li   t5, 48
                blt  t4, t5, .Lv_int_false
                li   t5, 57
                bgt  t4, t5, .Lv_int_false
                addi t3, t3, 1
                addi t0, t0, 1
                j    .Lv_int_loop
            .Lv_int_check:
                beqz t3, .Lv_int_false
                li   a0, 1
                ret
            .Lv_int_false:
                li   a0, 0
                ret

            .globl kof_validation_isLong
            kof_validation_isLong:
                j kof_validation_isInt

            .globl kof_validation_inRange
            kof_validation_inRange:
                blt  a0, a1, .Lv_range_false
                bgt  a0, a2, .Lv_range_false
                li   a0, 1
                ret
            .Lv_range_false:
                li   a0, 0
                ret

            .globl kof_validation_min
            kof_validation_min:
                bge  a0, a1, .Lv_min2_true
                li   a0, 0
                ret
            .Lv_min2_true:
                li   a0, 1
                ret

            .globl kof_validation_max
            kof_validation_max:
                ble  a0, a1, .Lv_max2_true
                li   a0, 0
                ret
            .Lv_max2_true:
                li   a0, 1
                ret

            # ---- JSN002: kof_json_quote(s@a0) -> KofString* -------------
            # Escapa para JSON: aspas, backslash, \\n \\r \\t, control<32
            # como \\u00XX, null -> "null". Mesma semântica do x86_64.
            .globl kof_json_quote
            kof_json_quote:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                li   s0, 0
                li   s1, 0
                beqz a0, .Ljq_bound
                lw   s1, 16(a0)
                addi s0, a0, 24
            .Ljq_bound:
                li   t0, 6
                mul  a0, s1, t0
                addi a0, a0, 30
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                addi s3, s2, 24
                beqz s0, .Ljq_null
                li   t0, 34
                sb   t0, 0(s3)
                addi s3, s3, 1
                li   s4, 0
            .Ljq_loop:
                bge  s4, s1, .Ljq_close_str
                add  t1, s0, s4
                lbu  a0, 0(t1)
                li   t0, 34
                beq  a0, t0, .Ljq_e_q
                li   t0, 92
                beq  a0, t0, .Ljq_e_bs
                li   t0, 10
                beq  a0, t0, .Ljq_e_nl
                li   t0, 13
                beq  a0, t0, .Ljq_e_cr
                li   t0, 9
                beq  a0, t0, .Ljq_e_tb
                li   t0, 32
                bltu a0, t0, .Ljq_e_uni
                sb   a0, 0(s3)
                addi s3, s3, 1
                j    .Ljq_next
            .Ljq_e_q:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 34
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_bs:
                li   t0, 92
                sb   t0, 0(s3)
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_nl:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 110
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_cr:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 114
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_tb:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 116
                sb   t0, 1(s3)
                addi s3, s3, 2
                j    .Ljq_next
            .Ljq_e_uni:
                li   t0, 92
                sb   t0, 0(s3)
                li   t0, 117
                sb   t0, 1(s3)
                li   t0, 48
                sb   t0, 2(s3)
                sb   t0, 3(s3)
                srli t1, a0, 4
                andi t1, t1, 15
                li   t0, 10
                bltu t1, t0, .Ljq_uh1
                addi t1, t1, 39
                j    .Ljq_uh2
            .Ljq_uh1:
                addi t1, t1, 48
            .Ljq_uh2:
                sb   t1, 4(s3)
                andi t1, a0, 15
                li   t0, 10
                bltu t1, t0, .Ljq_ul1
                addi t1, t1, 39
                j    .Ljq_ul2
            .Ljq_ul1:
                addi t1, t1, 48
            .Ljq_ul2:
                sb   t1, 5(s3)
                addi s3, s3, 6
                j    .Ljq_next
            .Ljq_next:
                addi s4, s4, 1
                j    .Ljq_loop
            .Ljq_close_str:
                li   t0, 34
                sb   t0, 0(s3)
                addi s3, s3, 1
                j    .Ljq_close
            .Ljq_null:
                li   t0, 110
                sb   t0, 0(s3)
                li   t0, 117
                sb   t0, 1(s3)
                li   t0, 108
                sb   t0, 2(s3)
                sb   t0, 3(s3)
                addi s3, s3, 4
            .Ljq_close:
                sub  a0, s3, s2
                addi a0, a0, -24
                sw   a0, 16(s2)
                add  t1, s2, a0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # ---- JSN002: encode de listas/primitivas --------------------
            # kof_json_encode_int/bool: alias das conversões existentes.
            .globl kof_json_encode_int
            kof_json_encode_int:
                j    kof_int_to_string
            .globl kof_json_encode_bool
            kof_json_encode_bool:
                j    kof_bool_to_string
            # kof_json_encode_string: aspas + escape = quote (já portado).
            .globl kof_json_encode_string
            kof_json_encode_string:
                j    kof_json_quote

            # Helper: a0 = elemento (ptr), a1 = tag (0 int / 1 str / 2 bool)
            # -> a0 = json do elemento. (salva ra: dispatch chama funcoes
            # que sobrescrevem o return address)
            kof_json_enc_elem:
                addi sp, sp, -16
                sd   ra, 8(sp)
                li   t0, 1
                beq  a1, t0, .Lenc_el_str
                li   t0, 2
                beq  a1, t0, .Lenc_el_bool
                call kof_json_encode_int
                j    .Lenc_el_done
            .Lenc_el_str:
                call kof_json_encode_string
                j    .Lenc_el_done
            .Lenc_el_bool:
                call kof_json_encode_bool
            .Lenc_el_done:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_encode_list(list@a0, tag@a1) -> KofString*
            """;
}

package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 4 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRtB4 {

    private NativeRiscvAsmRtB4() {}

    static final String RISCV_RUNTIME_ASM_B_4 = """
            .globl kof_json_encode_list
            kof_json_encode_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                la   a0, .Lstr_open_json
                li   a1, 1
                call kof_string_from_literal
                mv   s2, a0            # acc = "["
                li   s3, 0
            .Lenc_list_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Lenc_list_close
                slli t0, s3, 3
                ld   t1, 24(s0)
                add  a0, t1, t0
                ld   a0, 0(a0)
                mv   a1, s1
                call kof_json_enc_elem
                mv   s4, a0            # elem encodado
                bnez s3, .Lenc_list_comma
                j    .Lenc_list_append
            .Lenc_list_comma:
                la   a0, .Lstr_comma_json
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0            # b = ","
                mv   a0, s2
                call kof_string_concat
                mv   s2, a0
            .Lenc_list_append:
                mv   a0, s2
                mv   a1, s4
                call kof_string_concat
                mv   s2, a0
                addi s3, s3, 1
                j    .Lenc_list_loop
            .Lenc_list_close:
                la   a0, .Lstr_close_json
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0            # b = "]"
                mv   a0, s2
                call kof_string_concat
                mv   a0, a0
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # ---- JSN002: decode de listas --------------------------------
            # Helper: a0 = json, a1 = cursor -> a0 = int, a2 = novo cursor.
            kof_json_dec_int:
                addi sp, sp, -24
                sd   ra, 16(sp)
                sd   s0, 8(sp)
                sd   s1, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t2, 16(s0)          # t2 = len
            .Ldj_skip:
                bge  s1, t2, .Ldj_err
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldj_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldj_skip_inc
                j    .Ldj_sign
            .Ldj_skip_inc:
                addi s1, s1, 1
                j    .Ldj_skip
            .Ldj_sign:
                li   t3, 1
                li   t1, 45
                bne  a0, t1, .Ldj_digits
                li   t3, -1
                addi s1, s1, 1
            .Ldj_digits:
                li   t4, 0
            .Ldj_loop:
                bge  s1, t2, .Ldj_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 48
                blt  a0, t1, .Ldj_done
                li   t1, 57
                bgt  a0, t1, .Ldj_done
                li   t5, 10
                mul  t4, t4, t5
                addi t5, a0, -48
                add  t4, t4, t5
                addi s1, s1, 1
                j    .Ldj_loop
            .Ldj_done:
                mul  t4, t4, t3
                mv   a0, t4
                mv   a2, s1
                ld   ra, 16(sp)
                ld   s0, 8(sp)
                ld   s1, 0(sp)
                addi sp, sp, 24
                ret
            .Ldj_err:
                li   a0, 0
                mv   a2, s1
                ld   ra, 16(sp)
                ld   s0, 8(sp)
                ld   s1, 0(sp)
                addi sp, sp, 24
                ret

            # Helper: a0 = json, a1 = cursor -> a0 = KofString, a2 = novo
            # cursor (após a aspa de fechamento). Backslash: proximo byte,
            # exceto n->10 e t->9.
            kof_json_dec_str:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s0)
            .Lds_skip:
                bge  s1, s2, .Lds_empty
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Lds_skip_inc
                li   t1, 10
                beq  a0, t1, .Lds_skip_inc
                li   t1, 13
                beq  a0, t1, .Lds_skip_inc
                li   t1, 9
                beq  a0, t1, .Lds_skip_inc
                j    .Lds_open
            .Lds_skip_inc:
                addi s1, s1, 1
                j    .Lds_skip
            .Lds_open:
                li   t1, 34
                bne  a0, t1, .Lds_empty
                addi s1, s1, 1
                li   t0, 16
                mul  t0, s2, t0
                addi t0, t0, 32
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   zero, 16(s4)
                sw   zero, 20(s4)
                addi s3, s4, 24
            .Lds_loop:
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 34
                beq  a0, t1, .Lds_close
                li   t1, 92
                bne  a0, t1, .Lds_plain
                addi s1, s1, 1
                bge  s1, s2, .Lds_fin
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 110
                beq  a0, t1, .Lds_nl
                li   t1, 116
                beq  a0, t1, .Lds_tab
                j    .Lds_plain
            .Lds_nl:
                li   a0, 10
                j    .Lds_plain
            .Lds_tab:
                li   a0, 9
            .Lds_plain:
                sb   a0, 0(s3)
                addi s3, s3, 1
                addi s1, s1, 1
                j    .Lds_loop
            .Lds_close:
                addi s1, s1, 1
            .Lds_fin:
                sub  t0, s3, s4
                addi t0, t0, -24
                sw   t0, 16(s4)
                add  t1, s4, t0
                addi t1, t1, 24
                sb   zero, 0(t1)
                mv   a0, s4
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret
            .Lds_empty:
                la   a0, .Lstr_empty
                li   a1, 0
                call kof_string_from_literal
                mv   a2, s1
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   s4, 0(sp)
                addi sp, sp, 48
                ret

            # kof_json_decode_int_list(json@a0) -> List<Int>
            .globl kof_json_decode_int_list
            kof_json_decode_int_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0            # json (antes de list_new sobrescrever a0)
                call kof_list_new
                mv   s4, a0
                lw   s5, 16(s0)
                li   s1, 0
            .Ldil_skip:
                bge  s1, s5, .Ldil_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldil_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldil_skip_inc
                j    .Ldil_open
            .Ldil_skip_inc:
                addi s1, s1, 1
                j    .Ldil_skip
            .Ldil_open:
                li   t1, 91
                bne  a0, t1, .Ldil_done
                addi s1, s1, 1
            .Ldil_loop:
                bge  s1, s5, .Ldil_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 93
                beq  a0, t1, .Ldil_done
                li   t1, 44
                beq  a0, t1, .Ldil_comma
                li   t1, 32
                beq  a0, t1, .Ldil_comma
                li   t1, 10
                beq  a0, t1, .Ldil_comma
                li   t1, 9
                beq  a0, t1, .Ldil_comma
                mv   a0, s0
                mv   a1, s1
                call kof_json_dec_int
                mv   s1, a2
                mv   a1, a0            # a1 = valor int
                mv   a0, s4            # a0 = lista
                call kof_list_add
                j    .Ldil_loop
            .Ldil_comma:
                addi s1, s1, 1
                j    .Ldil_loop
            .Ldil_done:
                mv   a0, s4
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 48
                ret

            # kof_json_decode_string_list(json@a0) -> List<String>
            .globl kof_json_decode_string_list
            kof_json_decode_string_list:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                mv   s0, a0            # json (antes de list_new sobrescrever a0)
                call kof_list_new
                mv   s4, a0
                lw   s5, 16(s0)
                li   s1, 0
            .Ldsl_skip:
                bge  s1, s5, .Ldsl_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 32
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 10
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 13
                beq  a0, t1, .Ldsl_skip_inc
                li   t1, 9
                beq  a0, t1, .Ldsl_skip_inc
                j    .Ldsl_open
            .Ldsl_skip_inc:
                addi s1, s1, 1
                j    .Ldsl_skip
            .Ldsl_open:
                li   t1, 91
                bne  a0, t1, .Ldsl_done
                addi s1, s1, 1
            .Ldsl_loop:
                bge  s1, s5, .Ldsl_done
                add  t0, s0, s1
                lbu  a0, 24(t0)
                li   t1, 93
                beq  a0, t1, .Ldsl_done
                li   t1, 44
                beq  a0, t1, .Ldsl_comma
                li   t1, 32
                beq  a0, t1, .Ldsl_comma
                li   t1, 10
                beq  a0, t1, .Ldsl_comma
                li   t1, 9
                beq  a0, t1, .Ldsl_comma
                mv   a0, s0
                mv   a1, s1
                call kof_json_dec_str
                mv   s1, a2
                mv   a1, a0            # a1 = KofString decodificada
                mv   a0, s4            # a0 = lista
                call kof_list_add
                j    .Ldsl_loop
            .Ldsl_comma:
                addi s1, s1, 1
                j    .Ldsl_loop
            .Ldsl_done:
                mv   a0, s4
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                addi sp, sp, 48
                ret

            .section .data
            kof_alloc_ptr: .quad _kof_heap
            .align 16
            kof_exc_chain: .quad 0
            .section .bss
            _kof_heap: .space 262144
            .Lmq_subs:    .space 1024
            .Lmq_queues:  .space 1024
            .Lmq_seq:     .space 8
            .Lcache_area: .space 1536
            kof_obs_counter_name: .quad 0
            kof_obs_counter_val: .word 0
            .align 3
            kof_obs_gauge_name: .quad 0
            kof_obs_gauge_val: .word 0
            .align 3
            kof_obs_hist_name: .quad 0
            kof_obs_hist_count: .word 0
            kof_obs_hist_sum: .word 0
            .align 3

            .section .rodata
            .Lnewline: .asciz "\\n"
            .Lstr_true: .asciz "true"
            .Lstr_false: .asciz "false"
            .Lstr_null: .asciz "null"
            .Lstr_null_err: .asciz "Runtime error: null pointer access"
            .Lstr_bounds_err: .asciz "Runtime error: array index out of bounds"
            .Lstr_empty: .asciz ""
            .Lstr_empty_interval: .asciz "interval-0"
            .Lstr_job_prefix: .asciz "job-"
            .Lstr_health: .asciz "UP"
            .Lstr_empty_json: .asciz "{}"
            .Lstr_open_json: .asciz "["
            .Lstr_close_json: .asciz "]"
            .Lstr_comma_json: .asciz ","
            .Lstr_trace: .asciz "00000000000000000000000000000000"
            .Lstr_span: .asciz "0000000000000000"
            .Lstr_span_handle: .asciz "000000000000000000000000000000000000000000000000"
            .Lstr_mq: .asciz "mq-0"
            .Lstr_mq_prefix: .asciz "mq-"
            .Lstr_obs_type: .asciz "# TYPE "
            .Lstr_obs_counter_nl: .asciz " counter\\n"
            .Lstr_obs_gauge_nl: .asciz " gauge\\n"
            .Lstr_space: .asciz " "
            .Lstr_nl: .asciz "\\n"
            .Lstr_count: .asciz "_count"
            .Lstr_sum: .asciz "_sum"
            .Llog_lbl_debug: .ascii "[DEBUG] "
            .Llog_lbl_info:  .ascii "[INFO ] "
            .Llog_lbl_warn:  .ascii "[WARN ] "
            .Llog_lbl_error: .ascii "[ERROR] "
            """;
}

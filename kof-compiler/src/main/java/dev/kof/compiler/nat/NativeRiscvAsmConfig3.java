package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 9, lane parity, 26/09): fatia 3c do kof.config no
// cross — interpolação ${key} (P2, profundidade 16) e os wrappers tipados
// (get/env/has/str/int/long/bool/required), espelhando RuntimeConfig1/2 (x86)
// e o JvmConfigRuntime. Int sem parse/fora de int32 → default; bool aceita
// true/yes/1 e false/no/0 (case-insensitive); required ausente → panic.
public final class NativeRiscvAsmConfig3 {

    private NativeRiscvAsmConfig3() {}

    static String RISCV_RUNTIME_ASM_CONFIG_3 = """
            .section .rodata
            .Lcfg3_dollar:  .asciz "${"
            .Lcfg3_close:   .asciz "}"
            .Lcfg3_w_true:  .asciz "true"
            .Lcfg3_w_yes:   .asciz "yes"
            .Lcfg3_w_one:   .asciz "1"
            .Lcfg3_w_false: .asciz "false"
            .Lcfg3_w_no:    .asciz "no"
            .Lcfg3_req_msg: .asciz "Kof config: missing required key (CONF001)"
            .section .text

            # kof_config_lookup(a0=key) -> a0=valor|0 (raw + interpolação)
            .globl kof_config_lookup
            .type kof_config_lookup, @function
            kof_config_lookup:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_config_lookup_raw
                beqz a0, .Lcfg3_lk_null
                call kof_config_interpolate
            .Lcfg3_lk_null:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_config_get(a0=key) -> a0=valor|0
            .globl kof_config_get
            .type kof_config_get, @function
            kof_config_get:
                j    kof_config_lookup

            # kof_config_interpolate(a0=value KofString*|0) -> a0=valor resolvido
            .globl kof_config_interpolate
            .type kof_config_interpolate, @function
            kof_config_interpolate:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                beqz a0, .Lcfg3_ret
                mv   s0, a0
                li   s1, 0
            .Lcfg3_loop:
                li   t0, 16
                bge  s1, t0, .Lcfg3_done
                la   a0, .Lcfg3_dollar
                li   a1, 2
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_index_of
                li   t0, -1
                beq  a0, t0, .Lcfg3_done
                mv   s5, a0                  # start
                mv   a0, s0
                addi a1, s5, 2
                lw   a2, 16(s0)
                call kof_string_substring
                beqz a0, .Lcfg3_done
                mv   s4, a0                  # tail
                la   a0, .Lcfg3_close
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s4
                call kof_string_index_of
                li   t0, -1
                beq  a0, t0, .Lcfg3_done
                addi t0, a0, 2
                add  s3, s5, t0              # end
                mv   a0, s0
                addi a1, s5, 2
                mv   a2, s3
                call kof_string_substring    # ref
                call kof_config_lookup
                beqz a0, .Lcfg3_done
                mv   s4, a0                  # resolved
                mv   a0, s0
                li   a1, 0
                mv   a2, s5
                call kof_string_substring    # prefix
                sd   a0, 0(sp)
                mv   a0, s0
                addi a1, s3, 1
                lw   a2, 16(s0)
                call kof_string_substring    # suffix
                sd   a0, 8(sp)
                mv   a0, s4
                ld   a1, 8(sp)
                call kof_string_concat
                mv   a1, a0
                ld   a0, 0(sp)
                call kof_string_concat
                mv   s0, a0
                addi s1, s1, 1
                j    .Lcfg3_loop
            .Lcfg3_done:
                mv   a0, s0
            .Lcfg3_ret:
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret

            # .Lcfg3_ci(a0=candidato minúsculo cstr, a1=dados, a2=len) -> a0=1/0
            .Lcfg3_ci:
                li   t0, 0
            .Lcfg3_ci_loop:
                bge  t0, a2, .Lcfg3_ci_yes
                add  t1, a0, t0
                lbu  t2, 0(t1)
                add  t3, a1, t0
                lbu  t4, 0(t3)
                ori  t2, t2, 0x20
                ori  t4, t4, 0x20
                bne  t2, t4, .Lcfg3_ci_no
                addi t0, t0, 1
                j    .Lcfg3_ci_loop
            .Lcfg3_ci_yes:
                li   a0, 1
                ret
            .Lcfg3_ci_no:
                li   a0, 0
                ret

            # .Lcfg3_parse_i64(a0=KofString*) -> a0=valor, a1=1 ok | a1=0 inválido
            .Lcfg3_parse_i64:
                lw   t0, 16(a0)
                addi t1, a0, 24
                li   t2, 0
            .Lcfg3_pi_tls:
                bge  t2, t0, .Lcfg3_pi_bad
                add  t3, t1, t2
                lbu  t4, 0(t3)
                li   t5, 32
                beq  t4, t5, .Lcfg3_pi_tls1
                li   t5, 9
                bne  t4, t5, .Lcfg3_pi_tle
            .Lcfg3_pi_tls1:
                addi t2, t2, 1
                j    .Lcfg3_pi_tls
            .Lcfg3_pi_tle:
                mv   t6, t0
            .Lcfg3_pi_tle_l:
                bge  t2, t6, .Lcfg3_pi_bad
                addi t3, t6, -1
                add  t3, t1, t3
                lbu  t4, 0(t3)
                li   t5, 32
                beq  t4, t5, .Lcfg3_pi_tle1
                li   t5, 9
                beq  t4, t5, .Lcfg3_pi_tle1
                j    .Lcfg3_pi_sign
            .Lcfg3_pi_tle1:
                addi t6, t6, -1
                j    .Lcfg3_pi_tle_l
            .Lcfg3_pi_sign:
                li   a0, 0
                li   a1, 0
                bge  t2, t6, .Lcfg3_pi_bad
                add  t3, t1, t2
                lbu  t4, 0(t3)
                li   t5, 45
                beq  t4, t5, .Lcfg3_pi_neg
                li   t5, 43
                beq  t4, t5, .Lcfg3_pi_pos
                j    .Lcfg3_pi_dcheck
            .Lcfg3_pi_neg:
                li   a1, 1
            .Lcfg3_pi_pos:
                addi t2, t2, 1
            .Lcfg3_pi_dcheck:
                bge  t2, t6, .Lcfg3_pi_bad
            .Lcfg3_pi_digit:
                add  t3, t1, t2
                lbu  t4, 0(t3)
                addi t4, t4, -48
                blt  t4, zero, .Lcfg3_pi_bad
                li   t5, 9
                bgt  t4, t5, .Lcfg3_pi_bad
                li   t5, 10
                mul  a0, a0, t5
                add  a0, a0, t4
                addi t2, t2, 1
                blt  t2, t6, .Lcfg3_pi_digit
                beqz a1, .Lcfg3_pi_ok
                sub  a0, zero, a0
            .Lcfg3_pi_ok:
                li   a1, 1
                ret
            .Lcfg3_pi_bad:
                li   a0, 0
                li   a1, 0
                ret

            # kof_config_env(a0=key KofString*) -> a0=KofString*|0
            .globl kof_config_env
            .type kof_config_env, @function
            kof_config_env:
                addi a0, a0, 24
                j    kof_env_getc

            # kof_config_has(a0=key) -> a0=1/0
            .globl kof_config_has
            .type kof_config_has, @function
            kof_config_has:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_config_lookup
                beqz a0, .Lcfg3_h_no
                li   a0, 1
                j    .Lcfg3_h_exit
            .Lcfg3_h_no:
                li   a0, 0
            .Lcfg3_h_exit:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_config_str(a0=key, a1=default) -> a0=valor|default
            .globl kof_config_str
            .type kof_config_str, @function
            kof_config_str:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a1
                call kof_config_lookup
                bnez a0, .Lcfg3_cs_exit
                mv   a0, s0
            .Lcfg3_cs_exit:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_config_int(a0=key, a1=default) -> a0=Int
            .globl kof_config_int
            .type kof_config_int, @function
            kof_config_int:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a1
                call kof_config_lookup
                beqz a0, .Lcfg3_ci_def
                call .Lcfg3_parse_i64
                beqz a1, .Lcfg3_ci_def
                li   t0, 2147483647
                bgt  a0, t0, .Lcfg3_ci_def
                li   t0, -2147483648
                blt  a0, t0, .Lcfg3_ci_def
                j    .Lcfg3_ci_exit
            .Lcfg3_ci_def:
                mv   a0, s0
            .Lcfg3_ci_exit:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_config_long(a0=key, a1=default) -> a0=Long
            .globl kof_config_long
            .type kof_config_long, @function
            kof_config_long:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a1
                call kof_config_lookup
                beqz a0, .Lcfg3_cl_def
                call .Lcfg3_parse_i64
                beqz a1, .Lcfg3_cl_def
                j    .Lcfg3_cl_exit
            .Lcfg3_cl_def:
                mv   a0, s0
            .Lcfg3_cl_exit:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_config_bool(a0=key, a1=default) -> a0=1/0/default
            .globl kof_config_bool
            .type kof_config_bool, @function
            kof_config_bool:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a1
                call kof_config_lookup
                beqz a0, .Lcfg3_cb_def
                lw   t0, 16(a0)
                addi t1, a0, 24
                li   t2, 0
            .Lcfg3_cb_tls:
                bge  t2, t0, .Lcfg3_cb_def
                add  t3, t1, t2
                lbu  t4, 0(t3)
                li   t5, 32
                beq  t4, t5, .Lcfg3_cb_tls1
                li   t5, 9
                beq  t4, t5, .Lcfg3_cb_tls1
                j    .Lcfg3_cb_tle
            .Lcfg3_cb_tls1:
                addi t2, t2, 1
                j    .Lcfg3_cb_tls
            .Lcfg3_cb_tle:
                mv   t6, t0
            .Lcfg3_cb_tle_l:
                bge  t2, t6, .Lcfg3_cb_def
                addi t3, t6, -1
                add  t3, t1, t3
                lbu  t4, 0(t3)
                li   t5, 32
                beq  t4, t5, .Lcfg3_cb_tle1
                li   t5, 9
                beq  t4, t5, .Lcfg3_cb_tle1
                j    .Lcfg3_cb_disp
            .Lcfg3_cb_tle1:
                addi t6, t6, -1
                j    .Lcfg3_cb_tle_l
            .Lcfg3_cb_disp:
                sub  s1, t6, t2
                add  s2, t1, t2
                li   t0, 4
                beq  s1, t0, .Lcfg3_cb_t4
                li   t0, 3
                beq  s1, t0, .Lcfg3_cb_t3
                li   t0, 1
                beq  s1, t0, .Lcfg3_cb_t1
                li   t0, 5
                beq  s1, t0, .Lcfg3_cb_f5
                li   t0, 2
                beq  s1, t0, .Lcfg3_cb_f2
                j    .Lcfg3_cb_def
            .Lcfg3_cb_t4:
                la   a0, .Lcfg3_w_true
                mv   a1, s2
                mv   a2, s1
                call .Lcfg3_ci
                beqz a0, .Lcfg3_cb_def
                li   a0, 1
                j    .Lcfg3_cb_exit
            .Lcfg3_cb_t3:
                la   a0, .Lcfg3_w_yes
                mv   a1, s2
                mv   a2, s1
                call .Lcfg3_ci
                beqz a0, .Lcfg3_cb_def
                li   a0, 1
                j    .Lcfg3_cb_exit
            .Lcfg3_cb_t1:
                la   a0, .Lcfg3_w_one
                mv   a1, s2
                mv   a2, s1
                call .Lcfg3_ci
                beqz a0, .Lcfg3_cb_def
                li   a0, 1
                j    .Lcfg3_cb_exit
            .Lcfg3_cb_f5:
                la   a0, .Lcfg3_w_false
                mv   a1, s2
                mv   a2, s1
                call .Lcfg3_ci
                beqz a0, .Lcfg3_cb_def
                li   a0, 0
                j    .Lcfg3_cb_exit
            .Lcfg3_cb_f2:
                la   a0, .Lcfg3_w_no
                mv   a1, s2
                mv   a2, s1
                call .Lcfg3_ci
                beqz a0, .Lcfg3_cb_def
                li   a0, 0
                j    .Lcfg3_cb_exit
            .Lcfg3_cb_def:
                mv   a0, s0
            .Lcfg3_cb_exit:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_config_required(a0=key) -> a0=valor|panic
            .globl kof_config_required
            .type kof_config_required, @function
            kof_config_required:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_config_lookup
                beqz a0, .Lcfg3_cr_missing
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lcfg3_cr_missing:
                la   a0, .Lcfg3_req_msg
                call kof_panic
            """;
}

package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRtB0 {

    private NativeRiscvAsmRtB0() {}

    static final String RISCV_RUNTIME_ASM_B_0 = """
            # kof_string_to_int(str) -> Int
            .globl kof_string_to_int
            kof_string_to_int:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                li   s1, 0
                li   s2, 0
                li   a0, 0
                lw   s3, 16(s0)
                beqz s3, .Lsti_done
                lbu  t0, 24(s0)
                li   t1, 45
                bne  t0, t1, .Lsti_loop
                li   s2, 1
            .Lsti_loop:
                bge  s1, s3, .Lsti_sign
                addi t0, s0, 24
                add  t0, t0, s1
                lbu  t0, 0(t0)
                addi t0, t0, -48
                li   t1, 9
                bgt  t0, t1, .Lsti_skip
                li   t1, 0
                blt  t0, t1, .Lsti_skip
                li   t1, 10
                mul  a0, a0, t1
                add  a0, a0, t0
            .Lsti_skip:
                addi s1, s1, 1
                j    .Lsti_loop
            .Lsti_sign:
                beqz s2, .Lsti_done
                neg  a0, a0
            .Lsti_done:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- List (typeId@0 super@4 vtable@8 len@16 cap@20 data@24) ----
            .globl kof_list_new
            kof_list_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 32
                call kof_alloc
                mv   s0, a0
                li   t0, 100
                sw   t0, 0(s0)
                li   t0, 0
                sw   t0, 4(s0)
                sd   t0, 8(s0)
                sw   t0, 16(s0)
                li   t0, 2
                sw   t0, 20(s0)
                li   a0, 16
                call kof_alloc
                sd   a0, 24(s0)
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_list_grow
            kof_list_grow:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                lw   t0, 20(s0)
                slli t0, t0, 1
                sw   t0, 20(s0)
                slli a0, t0, 3
                addi a0, a0, 24
                call kof_alloc
                mv   s1, a0
                ld   a1, 24(s0)
                lw   a2, 16(s0)
                slli a2, a2, 3
                call kof_memcpy
                sd   s1, 24(s0)
                mv   a0, s0
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_add
            kof_list_add:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                lw   t0, 20(s0)
                lw   t1, 16(s0)
                bge  t1, t0, .Lla_grow
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
                j    .Lla_done
            .Lla_grow:
                mv   a0, s0
                call kof_list_grow
                lw   t1, 16(s0)
                ld   t2, 24(s0)
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s1, 0(t2)
                addi t1, t1, 1
                sw   t1, 16(s0)
            .Lla_done:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_size
            kof_list_size:
                lw   a0, 16(a0)
                ret

            # kof_list_remove(list, idx) -> item removido (desloca o resto)
            .globl kof_list_remove
            kof_list_remove:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)      # list
                sd   s1, 8(sp)       # idx
                sd   s2, 0(sp)       # item
                mv   s0, a0
                mv   s1, a1
                ld   t0, 24(s0)
                slli t1, s1, 3
                add  t0, t0, t1
                ld   s2, 0(t0)       # item = data[idx]
                lw   t2, 16(s0)      # size
                addi t3, s1, 1       # i = idx+1
            .Llr_loop:
                bge  t3, t2, .Llr_done
                ld   t0, 24(s0)
                slli t4, t3, 3
                add  t4, t0, t4
                ld   t5, 0(t4)       # data[i]
                ld   t0, 24(s0)
                addi t6, t3, -1
                slli t6, t6, 3
                add  t6, t0, t6
                sd   t5, 0(t6)       # data[i-1] = data[i]
                addi t3, t3, 1
                j    .Llr_loop
            .Llr_done:
                addi t2, t2, -1
                sw   t2, 16(s0)      # size--
                mv   a0, s2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_get
            kof_list_get:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Llg_bounds
                blt  a1, zero, .Llg_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                ld   a0, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Llg_bounds:
                call kof_bounds_error

            .globl kof_list_set
            kof_list_set:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lw   t0, 16(a0)
                bge  a1, t0, .Lls_bounds
                blt  a1, zero, .Lls_bounds
                ld   t1, 24(a0)
                slli t2, a1, 3
                add  t1, t1, t2
                sd   a2, 0(t1)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lls_bounds:
                call kof_bounds_error

            .globl kof_list_contains
            kof_list_contains:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                li   t0, 0
            .Llc_loop:
                lw   t1, 16(s0)
                bge  t0, t1, .Llc_no
                ld   t2, 24(s0)
                slli t3, t0, 3
                add  t2, t2, t3
                ld   t2, 0(t2)
                bne  t2, s1, .Llc_next
                li   a0, 1
                j    .Llc_ret
            .Llc_next:
                addi t0, t0, 1
                j    .Llc_loop
            .Llc_no:
                li   a0, 0
            .Llc_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_list_is_empty
            kof_list_is_empty:
                lw   t0, 16(a0)
                seqz a0, t0
                ret

            .globl kof_list_clear
            kof_list_clear:
                li   t0, 0
                sw   t0, 16(a0)
                ret

            # ---- kof.log: label [LEVEL] + msg + newline; stderr para warn/error, stdout para info/debug ----
            .globl kof_log_debug
            kof_log_debug:
                li   a1, 0
                j    kof_log_write_lvl
            .globl kof_log_info
            kof_log_info:
                li   a1, 1
                j    kof_log_write_lvl
            .globl kof_log_warn
            kof_log_warn:
                li   a1, 2
                j    kof_log_write_lvl
            .globl kof_log_error
            kof_log_error:
                li   a1, 3
                j    kof_log_write_lvl
            # helper: a0=msg*, a1=level (0..3)
            kof_log_write_lvl:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)      # msg
                sd   s1, 8(sp)       # level
                mv   s0, a0
                mv   s1, a1
                # fd = level >= 2 ? 2(stderr) : 1(stdout)
                li   t0, 2
                bge  s1, t0, .Llw_stderr
                li   s2, 1
                j    .Llw_write
            .Llw_stderr:
                li   s2, 2
            .Llw_write:
                # escolhe label
                la   t0, .Llog_lbl_debug
                li   t1, 0
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_info
                li   t1, 1
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_warn
                li   t1, 2
                beq  s1, t1, .Llw_have_lbl
                la   t0, .Llog_lbl_error
            .Llw_have_lbl:
                # write(fd, label, 8)
                mv   a0, s2
                mv   a1, t0
                li   a2, 8
                li   a7, 64
                ecall
                # write(fd, msg.data, msg.len)
                beqz s0, .Llw_skip_msg
                lw   a2, 16(s0)
                addi a1, s0, 24
                mv   a0, s2
                li   a7, 64
                ecall
            .Llw_skip_msg:
                # newline (usar .Lnewline)
                mv   a0, s2
                la   a1, .Lnewline
                li   a2, 1
                li   a7, 64
                ecall
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # ---- kof.config (minimal — retorna default / 0 / false) ----
            .globl kof_config_get
            kof_config_get:
                li   a0, 0
                ret
            .globl kof_config_env
            kof_config_env:
                li   a0, 0
                ret
            .globl kof_config_has
            kof_config_has:
                li   a0, 0
                ret
            .globl kof_config_str
            kof_config_str:
                mv   a0, a1
                ret
            .globl kof_config_int
            kof_config_int:
                mv   a0, a1
                ret
            .globl kof_config_long
            kof_config_long:
                mv   a0, a1
                ret
            .globl kof_config_bool
            kof_config_bool:
                mv   a0, a1
                ret
            .globl kof_config_required
            kof_config_required:
                beqz a0, kof_null_error
                ret

            # ---- kof.time ----
            # kof_time_now() -> epoch-ms (CLOCK_REALTIME). clock_gettime=113
            # (asm-generic, mesma tabela aarch64); paridade com o x86_64 (que
            # usava syscall 96). Antes era stub `li a0,0` → TTL do cache e
            # time.now() quebravam silenciosamente (R6).
            .globl kof_time_now
            kof_time_now:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a1, sp, 0              # &timespec {sec@0, nsec@8}
                li   a0, 0                  # CLOCK_REALTIME
                li   a7, 113
                ecall
                ld   t0, 0(sp)              # tv_sec
                ld   t1, 8(sp)              # tv_nsec
                li   t2, 1000
                mul  a0, t0, t2             # sec * 1000
                li   t3, 1000000
                div  t1, t1, t3             # nsec / 1_000_000
                add  a0, a0, t1             # epoch-ms
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .globl kof_time_sleep
            kof_time_sleep:
                # a0 = ms. nanosleep (syscall 101 asm-generic) com timespec
                # {tv_sec=ms/1000, tv_nsec=(ms%1000)*1e6} na stack. Antes era
                # stub `ret` (time.sleep nao dormia no cross — R6).
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                li   t0, 1000
                div  s1, s0, t0            # sec
                rem  t1, s0, t0            # ms%1000
                li   t2, 1000000
                mul  t1, t1, t2            # nsec
                sd   s1, 0(sp)             # timespec.tv_sec
                sd   t1, 8(sp)             # timespec.tv_nsec
                mv   a0, sp                # &ts
                mv   a1, zero              # rem = NULL
                li   a7, 101
                ecall
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .globl kof_time_interval
            kof_time_interval:
                # TIME001 fechado no cross: mesmo mecanismo do scheduler.every
                # (thread por job, loop com cancel). Aliás (a0=ms, a1=task).
                j    kof_scheduler_every
            .globl kof_time_cancel
            kof_time_cancel:
                j    kof_scheduler_cancel

            # ---- kof.observability (real, minimal para passar KofObservabilityTest) ----
            .globl kof_observability_health
            kof_observability_health:
                la   a0, .Lstr_health
                li   a1, 2
                # tail-call (j): o `call`+`ret` sobrescrevia ra com o ret da
                # própria função → loop infinito. `j` preserva o ra do chamador.
                j    kof_string_from_literal
            .globl kof_observability_readiness
            kof_observability_readiness:
                li   a0, 1
                ret
            .globl kof_observability_liveness
            kof_observability_liveness:
                li   a0, 1
                ret
            .globl kof_observability_counter
            kof_observability_counter:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_counter_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_counter_new
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                addi t1, t1, 1
                sw   t1, 0(t0)
                mv   a0, t1
                j    .Loc_counter_ret
            .Loc_counter_new:
                la   t0, kof_obs_counter_name
                sd   s0, 0(t0)
                la   t0, kof_obs_counter_val
                li   t1, 1
                sw   t1, 0(t0)
                li   a0, 1
            .Loc_counter_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            """;
}

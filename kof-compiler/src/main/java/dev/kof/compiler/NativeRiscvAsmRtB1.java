package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 1 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRtB1 {

    private NativeRiscvAsmRtB1() {}

    static final String RISCV_RUNTIME_ASM_B_1 = """
            .globl kof_observability_increment
            kof_observability_increment:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_inc_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_inc_new
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                add  t1, t1, s1
                sw   t1, 0(t0)
                mv   a0, t1
                j    .Loc_inc_ret
            .Loc_inc_new:
                la   t0, kof_obs_counter_name
                sd   s0, 0(t0)
                la   t0, kof_obs_counter_val
                sw   s1, 0(t0)
                mv   a0, s1
            .Loc_inc_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_observability_gauge
            kof_observability_gauge:
                la   t0, kof_obs_gauge_name
                sd   a0, 0(t0)
                la   t0, kof_obs_gauge_val
                sw   a1, 0(t0)
                ret

            .globl kof_observability_histogram
            kof_observability_histogram:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0
                mv   s1, a1
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                beqz t1, .Loc_hist_new
                mv   a0, t1
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Loc_hist_new
                la   t0, kof_obs_hist_count
                lw   t1, 0(t0)
                addi t1, t1, 1
                sw   t1, 0(t0)
                la   t0, kof_obs_hist_sum
                lw   t1, 0(t0)
                add  t1, t1, s1
                sw   t1, 0(t0)
                j    .Loc_hist_ret
            .Loc_hist_new:
                la   t0, kof_obs_hist_name
                sd   s0, 0(t0)
                la   t0, kof_obs_hist_count
                li   t1, 1
                sw   t1, 0(t0)
                la   t0, kof_obs_hist_sum
                sw   s1, 0(t0)
            .Loc_hist_ret:
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .globl kof_observability_metrics
            kof_observability_metrics:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                # start with empty string
                la   a0, .Lstr_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s0, a0
                # counter part
                la   t0, kof_obs_counter_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_gauge
                la   t0, kof_obs_counter_val
                lw   t1, 0(t0)
                # counter string: name + space + val + newline
                mv   a0, s0
                la   t0, kof_obs_gauge_name
                ld   a1, 0(t0)
                # we need to convert counter name + space + val
                # s0 = s0 + counter_name
                mv   s1, s0
                mv   a0, s1
                la   t0, kof_obs_gauge_name
                ld   a1, 0(t0)
                # actually we will build step by step via kof_string_concat and kof_int_to_string
                # "# TYPE " + name + " counter" (paridade JVM/x86_64)
                la   a0, .Lstr_obs_type
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t2, kof_obs_counter_name
                ld   a1, 0(t2)
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_obs_counter_nl
                li   a1, 9
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t2, kof_obs_counter_name
                ld   a1, 0(t2)
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_counter_val
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_gauge:
                la   t0, kof_obs_gauge_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_hist
                la   t0, kof_obs_gauge_val
                lw   t2, 0(t0)
                # "# TYPE " + name + " gauge"
                la   a0, .Lstr_obs_type
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_gauge_name
                ld   a1, 0(t0)
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_obs_gauge_nl
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s0
                la   t0, kof_obs_gauge_name
                ld   a1, 0(t0)
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_gauge_val
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_hist:
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                beqz t1, .Loc_met_done
                # "# TYPE " + name + _count + " counter"
                la   a0, .Lstr_obs_type
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s0
                la   t0, kof_obs_hist_name
                ld   a1, 0(t0)
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_count
                li   a1, 6
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_obs_counter_nl
                li   a1, 9
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                # hist_count line: name + _count + space + count + newline
                mv   a0, s0
                la   t0, kof_obs_hist_name
                ld   a1, 0(t0)
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_count
                li   a1, 6
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_hist_count
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                # "# TYPE " + name + _sum + " gauge" + hist_sum line
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                la   a0, .Lstr_obs_type
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s0
                la   t0, kof_obs_hist_name
                ld   a1, 0(t0)
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_sum
                li   a1, 4
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_obs_gauge_nl
                li   a1, 7
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_hist_name
                ld   t1, 0(t0)
                mv   a0, s0
                la   t0, kof_obs_hist_name
                ld   a1, 0(t0)
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_sum
                li   a1, 4
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_space
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   t0, kof_obs_hist_sum
                lw   a0, 0(t0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lstr_nl
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
            .Loc_met_done:
                mv   a0, s0
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            .globl kof_observability_request_id
            kof_observability_request_id:
                la   a0, .Lstr_trace
                li   a1, 16
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal
            .globl kof_observability_correlation_id
            kof_observability_correlation_id:
                la   a0, .Lstr_span
                li   a1, 16
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal
            .globl kof_observability_trace_id
            kof_observability_trace_id:
                la   a0, .Lstr_trace
                li   a1, 32
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal
            .globl kof_observability_span_id
            kof_observability_span_id:
                la   a0, .Lstr_span
                li   a1, 16
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal
            .globl kof_observability_span_start
            kof_observability_span_start:
                la   a0, .Lstr_span_handle
                li   a1, 48
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal
            .globl kof_observability_span_end
            kof_observability_span_end:
                la   a0, .Lstr_empty_json
                li   a1, 2
                # tail-call (j): preserva o ra do chamador — call+ret dava loop
                j    kof_string_from_literal

            # ---- kof.cache riscv64/aarch64: 64 slots de 24B em .bss ([key*][val*][expira_ms]) ----
            # set(key, val): grava; get(key): lê; set_ttl(key,val,ttl): guarda com expiração real
            .globl kof_cache_set
            kof_cache_set:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # key
                sd   s1, 24(sp)      # val
                sd   s2, 16(sp)      # ponteiro slot
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lcache_area
                li   t0, 1536
                add  t0, s2, t0
                sd   t0, 8(sp)       # fim (recarregado a cada iteração — t-regs
                                     # são clobberados pelo kof_string_equals)
            .Lcs_scan:
                ld   t5, 8(sp)
                bgeu s2, t5, .Lcs_write
                ld   t4, 0(s2)
                beqz t4, .Lcs_write       # vazio
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcs_write       # mesma key, overwrite
                addi s2, s2, 24
                j    .Lcs_scan
            .Lcs_write:
                sd   s0, 0(s2)
                sd   s1, 8(s2)
                li   t4, 0
                sd   t4, 16(s2)           # sem ttl
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_set_ttl(key, val, ttl_seconds)
            .globl kof_cache_set_ttl
            kof_cache_set_ttl:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)      # key
                sd   s1, 40(sp)      # val
                sd   s2, 32(sp)      # ttl seconds
                sd   s3, 24(sp)      # slot ptr
                sd   s4, 16(sp)      # expira em ms
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                # expira = now + ttl*1000
                call kof_time_now
                li   t4, 1000
                mul  t4, s2, t4
                add  s4, a0, t4
                # grava
                la   s3, .Lcache_area
                li   t0, 1536
                add  t0, s3, t0
                sd   t0, 8(sp)       # fim (recarregado — t-regs clobberados)
            .Lcst_scan:
                ld   t6, 8(sp)
                bgeu s3, t6, .Lcst_write
                ld   t5, 0(s3)
                beqz t5, .Lcst_write
                mv   a0, t5
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcst_write
                addi s3, s3, 24
                j    .Lcst_scan
            .Lcst_write:
                sd   s0, 0(s3)
                sd   s1, 8(s3)
                sd   s4, 16(s3)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_cache_get(key) -> val* ou 0
            """;
}

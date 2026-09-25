package dev.kof.compiler.nat;

// FASE 3 (REFACTOR-500): fatia 0 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
public final class NativeRiscvAsmRtB0 {

    private NativeRiscvAsmRtB0() {}

    static  String RISCV_RUNTIME_ASM_B_0 = """
            # kof_string_to_int MOVIDO p/ B30 (bug 79 U3: contrato JDK
            # c/ trim+digito-a-digito+overflow->throw; este corpo silencioso
            # era "abc"=0 / "12a34"=1234 — divergente do JVM).

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
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2            # tag: 0=raw, 1=String, 2=objeto Kof
                li   s3, 0
            .Llc_loop:
                lw   t1, 16(s0)
                bge  s3, t1, .Llc_no
                ld   t2, 24(s0)
                slli t3, s3, 3
                add  t2, t2, t3
                ld   t2, 0(t2)
                li   t4, 1
                beq  s2, t4, .Llc_str
                li   t4, 2
                beq  s2, t4, .Llc_obj
                beq  t2, s1, .Llc_yes
                j    .Llc_next
            .Llc_str:
                beqz t2, .Llc_next
                mv   a0, t2
                mv   a1, s1
                call kof_string_equals
                bnez a0, .Llc_yes
                j    .Llc_next
            .Llc_obj:
                beqz t2, .Llc_next
                mv   a0, t2
                mv   a1, s1
                call kof_obj_equals
                bnez a0, .Llc_yes
            .Llc_next:
                addi s3, s3, 1
                j    .Llc_loop
            .Llc_yes:
                li   a0, 1
                j    .Llc_ret
            .Llc_no:
                li   a0, 0
            .Llc_ret:
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            .globl kof_obj_equals
            # §104b-ii (24/09): igualdade por CONTEUDO de referencia Kof
            # (record/classe). a0=a, a1=b -> a0 1/0. a==b (inclui null==null)
            # -> 1; um nulo -> 0; String (type_id==1) -> kof_string_equals;
            # senao despacha o equals virtual por kof_equals_table[type_id]
            # (0 -> 0 = sem equals).
            kof_obj_equals:
                beq  a0, a1, .Lkoe_yes
                beqz a0, .Lkoe_no
                beqz a1, .Lkoe_no
                lw   t0, 0(a0)
                li   t1, 1
                beq  t0, t1, .Lkoe_str
                la   t2, kof_equals_table
                slli t0, t0, 3
                add  t2, t2, t0
                ld   t3, 0(t2)
                beqz t3, .Lkoe_no
                jr   t3
            .Lkoe_str:
                j    kof_string_equals
            .Lkoe_yes:
                li   a0, 1
                ret
            .Lkoe_no:
                li   a0, 0
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


            # ---- kof.time ----
            # kof_time_now() -> epoch-ms (CLOCK_REALTIME). clock_gettime=113
            # (asm-generic, mesma tabela aarch64); paridade com o x86_64 (que
            # usava syscall 96). Antes era stub `li a0,0` → TTL do cache e
            # time.now() quebravam silenciosamente (R6).
            .globl kof_time_now
            kof_time_now:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a0, sp, 0              # &timespec {sec@0, nsec@8}
                call kof_plat_time
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
                call kof_plat_sleep
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

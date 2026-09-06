package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 1 de RISCV_MAPSET_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmMapset1 {

    private NativeRiscvAsmMapset1() {}

    static final String RISCV_MAPSET_ASM_1 = """
            .globl kof_json_decode_long
            kof_json_decode_long:
                j    kof_json_decode_int

            # kof_json_decode_bool(json) -> Bool (skip ws; "true"→1, else 0)
            .globl kof_json_decode_bool
            kof_json_decode_bool:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)          # json
                sd   s1, 8(sp)           # len
                mv   s0, a0
                lw   s1, 16(s0)
                li   t0, 0               # pos
            .Lkdb_skip:
                bge  t0, s1, .Lkdb_false
                addi t1, s0, 24
                add  t1, t1, t0
                lbu  t2, 0(t1)
                li   t3, 32
                beq  t2, t3, .Lkdb_skipinc
                li   t3, 10
                beq  t2, t3, .Lkdb_skipinc
                li   t3, 13
                beq  t2, t3, .Lkdb_skipinc
                li   t3, 9
                beq  t2, t3, .Lkdb_skipinc
                li   t3, 116             # 't' de "true"
                beq  t2, t3, .Lkdb_true
                j    .Lkdb_false
            .Lkdb_skipinc:
                addi t0, t0, 1
                j    .Lkdb_skip
            .Lkdb_true:
                li   a0, 1
                j    .Lkdb_ret
            .Lkdb_false:
                li   a0, 0
            .Lkdb_ret:
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_decode_string(json) -> String (extrai conteúdo entre
            # aspas, sem processar escapes — paridade com o x86_64 p/ casos
            # simples; escapes ficam p/ degrau com builder).
            .globl kof_json_decode_string
            kof_json_decode_string:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # json
                sd   s1, 24(sp)          # len
                sd   s2, 16(sp)          # pos
                sd   s3, 8(sp)           # start
                mv   s0, a0
                lw   s1, 16(s0)
                li   s2, 0
            .Lkds_open:
                bge  s2, s1, .Lkds_empty
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 34              # '"'
                beq  t1, t2, .Lkds_found
                addi s2, s2, 1
                j    .Lkds_open
            .Lkds_found:
                addi s3, s2, 1           # start = após a aspa
                mv   t3, s2              # pos da aspa de abertura
            .Lkds_close:
                addi t3, t3, 1
                bge  t3, s1, .Lkds_empty
                addi t0, s0, 24
                add  t0, t0, t3
                lbu  t1, 0(t0)
                li   t2, 34
                bne  t1, t2, .Lkds_close
                # substring [s3, t3)
                mv   a0, s0
                mv   a1, s3
                mv   a2, t3
                call kof_string_substring
                j    .Lkds_ret
            .Lkds_empty:
                li   a0, 0
            .Lkds_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- higher-order (map/filter/reduce) — closure ABI igual mq ----
            # kof_json_decode_int(json) -> Int (escalar; port do x86_64
            # RuntimeJsonDecode: skip ws, sinal, dígitos). Autocontido (sem
            # helpers). bool/string/long exigem kof_json_starts_with/builder.
            .globl kof_json_decode_int
            kof_json_decode_int:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # json str
                sd   s1, 24(sp)          # len
                sd   s2, 16(sp)          # pos
                sd   s3, 8(sp)           # sign
                mv   s0, a0
                lw   s1, 16(s0)
                li   s2, 0
                li   s3, 1
            .Lkdi_skip:
                bge  s2, s1, .Lkdi_done
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 32
                beq  t1, t2, .Lkdi_skipinc
                li   t2, 10
                beq  t1, t2, .Lkdi_skipinc
                li   t2, 13
                beq  t1, t2, .Lkdi_skipinc
                li   t2, 9
                beq  t1, t2, .Lkdi_skipinc
                j    .Lkdi_sign
            .Lkdi_skipinc:
                addi s2, s2, 1
                j    .Lkdi_skip
            .Lkdi_sign:
                li   t2, 45              # '-'
                bne  t1, t2, .Lkdi_digits
                li   s3, -1
                addi s2, s2, 1
            .Lkdi_digits:
                li   a0, 0               # acc
            .Lkdi_loop:
                bge  s2, s1, .Lkdi_done
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 48              # '0'
                blt  t1, t2, .Lkdi_done
                li   t2, 57              # '9'
                bgt  t1, t2, .Lkdi_done
                li   t3, 10
                mul  a0, a0, t3
                addi t1, t1, -48
                add  a0, a0, t1
                addi s2, s2, 1
                j    .Lkdi_loop
            .Lkdi_done:
                mul  a0, a0, s3
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # invoke: a0=fn, a1..=args → ld t0,8(a0) ld t0,0(t0) jalr t0
            # kof_list_map(list, fn) -> List (fn(item))
            .globl kof_list_map
            kof_list_map:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # src list
                sd   s1, 24(sp)          # fn
                sd   s2, 16(sp)          # dst list
                sd   s3, 8(sp)           # i
                mv   s0, a0
                mv   s1, a1
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Llmap_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llmap_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)           # item
                mv   a0, s1              # fn
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3                  # a0 = fn(item)
                mv   a1, a0
                mv   a0, s2
                call kof_list_add
                addi s3, s3, 1
                j    .Llmap_loop
            .Llmap_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_filter(list, fn) -> List (mantém onde fn(item) é true)
            .globl kof_list_filter
            kof_list_filter:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                call kof_list_new
                mv   s2, a0
                li   s3, 0
            .Llfilt_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llfilt_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)           # item
                mv   a0, s1
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3                  # a0 = fn(item)
                beqz a0, .Llfilt_next
                # recarrega item (a1 pode ter sido clobberado pelo callee)
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a1, 0(t1)
                mv   a0, s2
                call kof_list_add
            .Llfilt_next:
                addi s3, s3, 1
                j    .Llfilt_loop
            .Llfilt_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_list_reduce(list, init, fn) -> acc (fn(acc, item))
            .globl kof_list_reduce
            kof_list_reduce:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # list
                sd   s1, 24(sp)          # fn
                sd   s2, 16(sp)          # acc
                sd   s3, 8(sp)           # i
                mv   s0, a0
                mv   s2, a1              # init = acc
                mv   s1, a2              # fn
                li   s3, 0
            .Llred_loop:
                lw   t0, 16(s0)
                bge  s3, t0, .Llred_done
                ld   t1, 24(s0)
                slli t2, s3, 3
                add  t1, t1, t2
                ld   a2, 0(t1)           # item
                mv   a0, s1              # fn
                mv   a1, s2              # acc
                ld   t3, 8(a0)
                ld   t3, 0(t3)
                jalr t3                  # a0 = fn(acc, item)
                mv   s2, a0
                addi s3, s3, 1
                j    .Llred_loop
            .Llred_done:
                mv   a0, s2
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- scheduler/time.interval riscv64 (SCHED001/TIME001) ----
            # thread por job (clone 220, mesmo mecanismo do spawn): loop
            # lock→read active→unlock→nanosleep(ms)→re-check→invoke(task).
            # cancel(id) marca active=0; a thread sai sozinha no próximo tick.
            # job 48B: next@0 task@8 ms@16(i32) active@20(i32) id@24 stack@40
            # lock: spinlock amoswap.w (amoswap.w t0, t1, (s2): t0=old).
            .section .data
            .align 3
            kof_sched_head: .quad 0
            kof_sched_seq:  .quad 0
            kof_sched_lock: .word 0
            .section .text

            # kof_scheduler_every(ms@a0, task@a1) -> id String@a0
            .globl kof_scheduler_every
            kof_scheduler_every:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)          # ms
                sd   s1, 40(sp)          # task
                sd   s2, 32(sp)          # seq
                sd   s3, 24(sp)          # id
                sd   s4, 16(sp)          # job
                sd   s5, 8(sp)           # stack top
                mv   s0, a0
                mv   s1, a1
                # seq++ sob lock
                la   s2, kof_sched_lock
            .Lse_lk1:
                li   t1, 1
                amoswap.w t0, t1, (s2)
                bnez t0, .Lse_lk1
                la   t0, kof_sched_seq
                ld   t1, 0(t0)
                addi t1, t1, 1
                sd   t1, 0(t0)
                sw   zero, 0(s2)
                # id = "job-" + int_to_string(seq)
                la   a0, .Lstr_job_prefix
                li   a1, 4
                call kof_string_from_literal
                mv   s3, a0
                mv   a0, t1
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat
                mv   s3, a0
                # job = alloc(48)
                li   a0, 48
                call kof_alloc
                mv   s4, a0
                sd   zero, 0(s4)         # next
                sd   s1, 8(s4)           # task
                sw   s0, 16(s4)          # ms
                li   t0, 1
                sw   t0, 20(s4)          # active
                sd   s3, 24(s4)          # id
                # stack dedicada 1MB (mmap 222)
                li   a0, 0
                li   a1, 1048576
                li   a2, 3
                li   a3, 0x22
                li   a4, -1
                li   a5, 0
                li   a7, 222
                ecall
                li   t1, 1048576
                add  s5, a0, t1          # stack TOP
                sd   s5, 40(s4)
                # clone(flags, stack_top, ...) — filho herda s0-s5
                li   a0, 0x3D0F00
                mv   a1, s5
                li   a2, 0
                li   a3, 0
                li   a4, 0
                li   a7, 220
                ecall
                bltz a0, .Lse_fail
                bnez a0, .Lse_reg
                # ---- filho: sp dedicado, roda o loop do job ----
                mv   sp, s5
                mv   a0, s4              # job (trampoline espera a0=job)
                call kof_sched_trampoline
                li   a0, 0
                li   a7, 93              # exit (só a thread)
                ecall
            .Lse_fail:
                # clone falhou: sem thread — job nunca dispara (degradação
                # segura; sem scheduler não há como rodar o loop inline sem
                # travar o main). Retorna o id mesmo assim.
                j    .Lse_ret
            .Lse_reg:
                # push na lista sob lock
                la   s2, kof_sched_lock
            .Lse_lk2:
                li   t1, 1
                amoswap.w t0, t1, (s2)
                bnez t0, .Lse_lk2
                la   t0, kof_sched_head
                ld   t1, 0(t0)
                sd   t1, 0(s4)
                sd   s4, 0(t0)
                sw   zero, 0(s2)
            .Lse_ret:
                mv   a0, s3
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_sched_trampoline(job@a0): loop sleep→invoke até active=0
            kof_sched_trampoline:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # job
                sd   s1, 24(sp)          # task
                sd   s2, 16(sp)          # &lock
                mv   s0, a0
                ld   s1, 8(s0)           # task
                la   s2, kof_sched_lock
            .Lst_loop:
                # lock; active/ms; unlock
            .Lst_lk1:
                li   t1, 1
                amoswap.w t0, t1, (s2)
                bnez t0, .Lst_lk1
                lw   t0, 20(s0)          # active
                lw   t1, 16(s0)          # ms
                sw   zero, 0(s2)
                beqz t0, .Lst_done
                mv   a0, t1
                call kof_time_sleep      # nanosleep (preserva s-regs)
                # re-check active (cancel pode ter chegado durante o sono)
            .Lst_lk2:
                li   t1, 1
                amoswap.w t0, t1, (s2)
                bnez t0, .Lst_lk2
                lw   t0, 20(s0)
                sw   zero, 0(s2)
                beqz t0, .Lst_done
                # invoke task (vtable[0], a0=task — closure ABI do mq)
                ld   t0, 8(s1)
                ld   t0, 0(t0)
                mv   a0, s1
                jalr t0
                j    .Lst_loop
            .Lst_done:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_scheduler_at(cron@a0, task@a1) -> id (MVP: roda a cada 60s)
            .globl kof_scheduler_at
            kof_scheduler_at:
                mv   t0, a1
                li   a0, 60000
                mv   a1, t0
                j    kof_scheduler_every

            # kof_scheduler_cancel(id@a0): acha o job e marca active=0
            """;
}

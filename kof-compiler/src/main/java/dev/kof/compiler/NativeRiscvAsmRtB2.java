package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 2 de RISCV_RUNTIME_ASM_B — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmRtB2 {

    private NativeRiscvAsmRtB2() {}

    static final String RISCV_RUNTIME_ASM_B_2 = """
            .globl kof_cache_get
            kof_cache_get:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lcg_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lcg_miss
                ld   t4, 0(s1)
                beqz t4, .Lcg_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcg_hit
            .Lcg_next:
                addi s1, s1, 24
                j    .Lcg_scan
            .Lcg_hit:
                # checa expiração: slot = [key@0 val@8 expira@16]
                ld   t4, 16(s1)
                beqz t4, .Lcg_hit_val
                sd   s1, 8(sp)
                sd   t4, 0(sp)
                call kof_time_now
                ld   s1, 8(sp)
                ld   t4, 0(sp)
                # se now >= expira → miss
                bgeu a0, t4, .Lcg_miss
                # devolve val
                ld   a0, 8(s1)
                j    .Lcg_ret
            .Lcg_hit_val:
                ld   a0, 8(s1)
                j    .Lcg_ret
            .Lcg_miss:
                li   a0, 0
            .Lcg_ret:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_ttl(key) -> segundos restantes, 0 default
            .globl kof_cache_ttl
            kof_cache_ttl:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lct_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lct_miss
                ld   t4, 0(s1)
                beqz t4, .Lct_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lct_found
            .Lct_next:
                addi s1, s1, 24
                j    .Lct_scan
            .Lct_found:
                ld   t4, 16(s1)
                beqz t4, .Lct_miss     # sem ttl (expira=0) → 0
                sd   t4, 0(sp)
                sd   s1, 8(sp)
                call kof_time_now
                ld   t4, 0(sp)         # expira_ms
                # resta_ms = expira - now (ms)
                sub  t4, t4, a0
                blez t4, .Lct_miss
                li   t5, 1000
                div  a0, t4, t5
                j    .Lct_ret
            .Lct_miss:
                li   a0, 0
            .Lct_ret:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_delete(key)
            .globl kof_cache_delete
            kof_cache_delete:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                la   s1, .Lcache_area
                li   t0, 1536
                add  t0, s1, t0
                sd   t0, 16(sp)      # fim (recarregado — t-regs clobberados)
            .Lcd_scan:
                ld   t5, 16(sp)
                bgeu s1, t5, .Lcd_done
                ld   t4, 0(s1)
                beqz t4, .Lcd_next
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lcd_clear
            .Lcd_next:
                addi s1, s1, 24
                j    .Lcd_scan
            .Lcd_clear:
                li   t0, 0
                sd   t0, 0(s1)
                sd   t0, 8(s1)
                sd   t0, 16(s1)
            .Lcd_done:
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_cache_clear()
            .globl kof_cache_clear
            kof_cache_clear:
                la   t0, .Lcache_area
                li   t1, 0
                li   t2, 64
            .Lclr_loop:
                bge  t1, t2, .Lclr_done
                li   t3, 0
                sd   t3, 0(t0)
                sd   t3, 8(t0)
                sd   t3, 16(t0)
                addi t1, t1, 1
                addi t0, t0, 24
                j    .Lclr_loop
            .Lclr_done:
                ret
            # ---- kof.mq riscv64/aarch64: pub/sub + filas em .bss (64 slots de 16B) ----
            # Reescrita 05/09 (port completo): entry = [topic@0, list@8].
            # (1) scan loops usam PONTEIRO-FIM salvo no frame (recarregado a
            #     cada iteração) — t2/t3/t4 são caller-saved e o
            #     kof_string_equals os clobber (segfault nos loops antigos);
            # (2) todos os s-regs usados são salvos no frame (os antigos
            #     usavam s2/s3/s4 sem salvar — clobber sob kof_string_equals,
            #     kof_list_* e o jalr dos handlers);
            # (3) invoke de handler: a0=fn a1=msg → ld t0,8(a0) ld t0,0(t0)
            #     jalr t0 (mesmo padrão do codegen de dispatch virtual);
            # (4) pop/remove/queue_size por handle — kof_list_remove (novo).
            # Paridade de output com o x86_64 (MQ001).
            .globl kof_mq_subscribe
            kof_mq_subscribe:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)      # topic
                sd   s1, 40(sp)      # fn
                sd   s2, 32(sp)      # slot ptr
                sd   s3, 24(sp)      # end ptr
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_sub_find:
                bgeu s2, s3, .Lmq_sub_new
                ld   t4, 0(s2)
                beqz t4, .Lmq_sub_new
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                bnez a0, .Lmq_sub_addfn
                addi s2, s2, 16
                j    .Lmq_sub_find
            .Lmq_sub_new:
                sd   s0, 0(s2)
                mv   a0, zero
                call kof_list_new
                sd   a0, 8(s2)
            .Lmq_sub_addfn:
                ld   a0, 8(s2)
                mv   a1, s1
                call kof_list_add
            .Lmq_sub_done:
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_mq_unsubscribe(topic, fn) — remove por identidade do objeto fn
            .globl kof_mq_unsubscribe
            kof_mq_unsubscribe:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)      # topic
                sd   s1, 56(sp)      # fn
                sd   s2, 48(sp)      # slot ptr
                sd   s3, 40(sp)      # end ptr
                sd   s4, 32(sp)      # list
                sd   s5, 24(sp)      # idx
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_unsub_find:
                bgeu s2, s3, .Lmq_unsub_done
                ld   t4, 0(s2)
                beqz t4, .Lmq_unsub_done
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_unsub_next
                ld   s4, 8(s2)
                li   s5, 0
            .Lmq_unsub_loop:
                mv   a0, s4
                call kof_list_size
                bge  s5, a0, .Lmq_unsub_done
                mv   a0, s4
                mv   a1, s5
                call kof_list_get
                beq  a0, s1, .Lmq_unsub_rm
                addi s5, s5, 1
                j    .Lmq_unsub_loop
            .Lmq_unsub_rm:
                mv   a0, s4
                mv   a1, s5
                call kof_list_remove
                j    .Lmq_unsub_done
            .Lmq_unsub_next:
                addi s2, s2, 16
                j    .Lmq_unsub_find
            .Lmq_unsub_done:
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_mq_publish(topic, msg) — dispara cada fn com a0=fn a1=msg
            .globl kof_mq_publish
            kof_mq_publish:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)      # topic
                sd   s1, 56(sp)      # msg
                sd   s2, 48(sp)      # slot ptr
                sd   s3, 40(sp)      # end ptr
                sd   s4, 32(sp)      # list
                sd   s5, 24(sp)      # idx
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_subs
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_pub_find:
                bgeu s2, s3, .Lmq_pub_done
                ld   t4, 0(s2)
                beqz t4, .Lmq_pub_done
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_pub_next
                ld   s4, 8(s2)
                li   s5, 0
            .Lmq_pub_iter:
                mv   a0, s4
                call kof_list_size
                bge  s5, a0, .Lmq_pub_done
                mv   a0, s4
                mv   a1, s5
                call kof_list_get
                mv   t5, a0          # fn
                mv   a0, t5          # invoke: a0=fn a1=msg
                # (t5 é caller-saved, mas a1 já foi movido antes de clobber)
                mv   a1, s1
                ld   t0, 8(a0)       # fn->vtable
                ld   t0, 0(t0)       # vtable[0] = invoke
                jalr t0
                addi s5, s5, 1
                j    .Lmq_pub_iter
            .Lmq_pub_next:
                addi s2, s2, 16
                j    .Lmq_pub_find
            .Lmq_pub_done:
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_mq_queue() -> String "mq-<n>" (handle único por queue)
            .globl kof_mq_queue
            kof_mq_queue:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                la   s0, .Lmq_seq
                lw   t0, 0(s0)
                addi t0, t0, 1
                sw   t0, 0(s0)
                la   a0, .Lstr_mq_prefix
                li   a1, 3
                call kof_string_from_literal
                sd   a0, 0(sp)
                mv   a0, t0
                call kof_int_to_string
                ld   a1, 0(sp)
                call kof_string_concat
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_mq_push(handle, item) — add na fila do handle
            .globl kof_mq_push
            kof_mq_push:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # item
                sd   s2, 16(sp)      # slot ptr
                sd   s3, 8(sp)       # end ptr
                mv   s0, a0
                mv   s1, a1
                la   s2, .Lmq_queues
                li   t0, 1024
                add  s3, s2, t0
            .Lmq_push_find:
                bgeu s2, s3, .Lmq_push_new
                ld   t4, 0(s2)
                beqz t4, .Lmq_push_new
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_push_next
                ld   a0, 8(s2)
                mv   a1, s1
                call kof_list_add
                j    .Lmq_push_done
            .Lmq_push_next:
                addi s2, s2, 16
                j    .Lmq_push_find
            .Lmq_push_new:
                sd   s0, 0(s2)
                mv   a0, zero
                call kof_list_new
                sd   a0, 8(s2)
                mv   a1, s1
                call kof_list_add      # a0 = list (ainda em a0)
            .Lmq_push_done:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_mq_pop(name) -> KofString* ou 0 — lê primeiro suspeito
                        # kof_mq_pop(handle) -> item ou 0 — remove o primeiro (FIFO)
            .globl kof_mq_pop
            kof_mq_pop:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # slot ptr
                sd   s2, 16(sp)      # end ptr
                sd   s3, 8(sp)       # list
                mv   s0, a0
                la   s1, .Lmq_queues
                li   t0, 1024
                add  s2, s1, t0
            .Lmq_pop_find:
                bgeu s1, s2, .Lmq_pop_null
                ld   t4, 0(s1)
                beqz t4, .Lmq_pop_null
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_pop_next
                ld   s3, 8(s1)
                mv   a0, s3
                call kof_list_size
                beqz a0, .Lmq_pop_null
                mv   a0, s3
                li   a1, 0
                call kof_list_remove
                j    .Lmq_pop_ret
            .Lmq_pop_next:
                addi s1, s1, 16
                j    .Lmq_pop_find
            .Lmq_pop_null:
                li   a0, 0
            .Lmq_pop_ret:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_mq_queue_size(handle) -> Int
            .globl kof_mq_queue_size
            kof_mq_queue_size:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)      # handle
                sd   s1, 24(sp)      # slot ptr
                sd   s2, 16(sp)      # end ptr
                mv   s0, a0
                la   s1, .Lmq_queues
                li   t0, 1024
                add  s2, s1, t0
            .Lmq_qs_find:
                bgeu s1, s2, .Lmq_qs_zero
                ld   t4, 0(s1)
                beqz t4, .Lmq_qs_zero
                mv   a0, t4
                mv   a1, s0
                call kof_string_equals
                beqz a0, .Lmq_qs_next
                ld   a0, 8(s1)
                call kof_list_size
                j    .Lmq_qs_ret
            .Lmq_qs_next:
                addi s1, s1, 16
                j    .Lmq_qs_find
            .Lmq_qs_zero:
                li   a0, 0
            .Lmq_qs_ret:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # ---- kof.validation (13 predicados) ----
            .globl kof_validation_required
            kof_validation_required:
                beqz a0, .Lv_req_false
                lw   t0, 16(a0)
                beqz t0, .Lv_req_false
                li   a0, 1
                ret
            .Lv_req_false:
                li   a0, 0
                ret

            """;
}

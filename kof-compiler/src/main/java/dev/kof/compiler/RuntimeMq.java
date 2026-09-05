package dev.kof.compiler;

/**
 * Emissão do ASM de message queue (kof_mq_*) do runtime nativo. Domínio isolado do
 * NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeMq {

    private RuntimeMq() {}

    static void emitMq(StringBuilder sb) {
        sb.append("""
            .section .bss
            .Lmq_topics: .quad 0
            .Lmq_queues: .quad 0
            .Lmq_seq: .quad 0
            .section .data
            .Lstr_mq_prefix: .asciz "mq-"
            .section .text

            # kof_mq_find_topic(rdi=topic) -> rax node | 0
            # (kf_string_equals clobbra rdi/rsi/rax -- usa rbx/r12 callee-saved)
            kof_mq_find_topic:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq .Lmq_topics(%rip), %r12
            .Lmq_ft_loop:
                testq %r12, %r12
                jz .Lmq_ft_done
                movq %rbx, %rdi
                movq 8(%r12), %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lmq_ft_done
                movq 0(%r12), %r12
                jmp .Lmq_ft_loop
            .Lmq_ft_done:
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret

            # kof_mq_find_queue(rdi=name) -> rax node | 0
            kof_mq_find_queue:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq .Lmq_queues(%rip), %r12
            .Lmq_fq_loop:
                testq %r12, %r12
                jz .Lmq_fq_done
                movq %rbx, %rdi
                movq 8(%r12), %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lmq_fq_done
                movq 0(%r12), %r12
                jmp .Lmq_fq_loop
            .Lmq_fq_done:
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret

            # kof_mq_subscribe(rdi=topic, rsi=fn) -> void
            .globl kof_mq_subscribe
            .type kof_mq_subscribe, @function
            kof_mq_subscribe:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                call kof_mq_find_topic
                testq %rax, %rax
                jnz .Lmq_sub_have
                movl $40, %edi
                call kof_alloc
                movq %rax, %r13
                movq .Lmq_topics(%rip), %rax
                movq %rax, 0(%r13)
                movq %rbx, 8(%r13)
                call kof_list_new
                movq %rax, 16(%r13)
                movq %r13, .Lmq_topics(%rip)
                movq %r13, %rax
            .Lmq_sub_have:
                movq %rax, %rdi
                movq 16(%rax), %rdi
                movq %r12, %rsi
                call kof_list_add
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_mq_unsubscribe(rdi=topic, rsi=fn) -> void
            .globl kof_mq_unsubscribe
            .type kof_mq_unsubscribe, @function
            kof_mq_unsubscribe:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                call kof_mq_find_topic
                testq %rax, %rax
                jz .Lmq_unsub_done
                movq %rax, %r13
                movq 16(%r13), %r14
                xorl %r15d, %r15d
            .Lmq_unsub_loop:
                movq %r14, %rdi
                call kof_list_size
                cmpq %rax, %r15
                jge .Lmq_unsub_done
                movq %r14, %rdi
                movq %r15, %rsi
                call kof_list_get
                cmpq %r12, %rax                  # identidade do objeto fn
                je .Lmq_unsub_rm
                incq %r15
                jmp .Lmq_unsub_loop
            .Lmq_unsub_rm:
                movq %r14, %rdi
                movq %r15, %rsi
                call kof_list_remove
            .Lmq_unsub_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_mq_publish(rdi=topic, rsi=msg) -> void
            .globl kof_mq_publish
            .type kof_mq_publish, @function
            kof_mq_publish:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                call kof_mq_find_topic
                testq %rax, %rax
                jz .Lmq_pub_done
                movq %rax, %r13
                movq 16(%r13), %r14
                xorl %r15d, %r15d
            .Lmq_pub_loop:
                movq %r14, %rdi
                call kof_list_size
                cmpq %rax, %r15
                jge .Lmq_pub_done
                movq %r14, %rdi
                movq %r15, %rsi
                call kof_list_get
                movq %rax, %rdi
                movq %r12, %rsi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                incq %r15
                jmp .Lmq_pub_loop
            .Lmq_pub_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_mq_queue() -> String "mq-<n>"
            .globl kof_mq_queue
            .type kof_mq_queue, @function
            kof_mq_queue:
                pushq %rbx
                pushq %r12
                incq .Lmq_seq(%rip)
                movq .Lmq_seq(%rip), %r12
                leaq .Lstr_mq_prefix(%rip), %rdi
                movl $3, %esi
                call kof_string_from_literal
                pushq %rax
                movq %r12, %rdi
                call kof_int_to_string
                movq %rax, %rsi
                popq %rdi
                call kof_string_concat
                popq %r12
                popq %rbx
                ret

            # kof_mq_push(rdi=q, rsi=item) -> void
            .globl kof_mq_push
            .type kof_mq_push, @function
            kof_mq_push:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                call kof_mq_find_queue
                testq %rax, %rax
                jnz .Lmq_push_have
                movl $40, %edi
                call kof_alloc
                movq %rax, %r13
                movq .Lmq_queues(%rip), %rax
                movq %rax, 0(%r13)
                movq %rbx, 8(%r13)
                call kof_list_new
                movq %rax, 16(%r13)
                movq %r13, .Lmq_queues(%rip)
                movq %r13, %rax
            .Lmq_push_have:
                movq 16(%rax), %rdi
                movq %r12, %rsi
                call kof_list_add
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_mq_pop(rdi=q) -> Object | null
            .globl kof_mq_pop
            .type kof_mq_pop, @function
            kof_mq_pop:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                call kof_mq_find_queue
                testq %rax, %rax
                jz .Lmq_pop_null
                movq %rax, %r12
                movq 16(%r12), %rdi
                call kof_list_size
                testq %rax, %rax
                jz .Lmq_pop_null
                movq 16(%r12), %rdi
                xorl %esi, %esi
                call kof_list_remove
                popq %r12
                popq %rbx
                ret
            .Lmq_pop_null:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            # kof_mq_queue_size(rdi=q) -> Int
            .globl kof_mq_queue_size
            .type kof_mq_queue_size, @function
            kof_mq_queue_size:
                pushq %rbx
                movq %rdi, %rbx
                call kof_mq_find_queue
                testq %rax, %rax
                jz .Lmq_qs_zero
                movq 16(%rax), %rdi
                call kof_list_size
                popq %rbx
                ret
            .Lmq_qs_zero:
                xorl %eax, %eax
                popq %rbx
                ret
            """);
    }

}
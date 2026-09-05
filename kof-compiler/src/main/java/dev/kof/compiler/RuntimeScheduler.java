package dev.kof.compiler;

/**
Emissão do ASM do scheduler (kof_scheduler/every/interval) do runtime nativo.
 * Domínio isolado do NativeRuntime — refactor preserva semântica.
 */
final class RuntimeScheduler {

    private RuntimeScheduler() {}

    static void emitScheduler(StringBuilder sb) {
        sb.append("""
            # SCHED001 fechado: scheduler.every/at/cancel no Native.
            # Job (48B): 0=next 8=task 16=ms(int) 20=active(int) 24=id(string) 32=pthread_t
            # Thread por job: usleep(ms) + invoke(task) enquanto active.
            # cancel(id): marca active=0 (a thread sai na proxima checagem).
            # at(cron, fn): MVP igual JVM — ignora o cron, roda a cada 60s.
            .section .bss
            .balign 8
            kof_sched_head: .quad 0
            kof_sched_seq: .long 0
            kof_sched_lock: .long 0
            .section .rodata
            .Lstr_job_prefix: .ascii "job-"
            .section .text

            # macro-ish: lock/unlock inline (tecnica kof_alloc: futex spin)
            .globl kof_sched_trampoline
            .type kof_sched_trampoline, @function
            kof_sched_trampoline:
                # rdi = job. 3 pushes -> ≡0 no call (ABI).
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
            .Lsched_loop:
                leaq kof_sched_lock(%rip), %rsi
                xorl %eax, %eax
            .Lsched_lk:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lsched_lkd
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lsched_lk
            .Lsched_lkd:
                movl 20(%rbx), %r12d              # active
                movq 8(%rbx), %r13                # task
                movl $0, (%rsi)                   # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                testl %r12d, %r12d
                jz .Lsched_done
                movl 16(%rbx), %edi               # ms -> us (clamp evita overflow)
                cmpl $2147483, %edi
                jle .Lsched_us_ok
                movl $2147483000, %edi
            .Lsched_us_ok:
                imull $1000, %edi
                call usleep
                # re-checa active apos dormir (cancel pode ter chegado)
                leaq kof_sched_lock(%rip), %rsi
                xorl %eax, %eax
            .Lsched_lk2:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lsched_lkd2
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lsched_lk2
            .Lsched_lkd2:
                movl 20(%rbx), %r12d
                movl $0, (%rsi)                   # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                testl %r12d, %r12d
                jz .Lsched_done
                movq %r13, %rdi                   # task
                movq 8(%rdi), %rax                # vtable
                movq (%rax), %rax                 # invoke
                call *%rax
                jmp .Lsched_loop
            .Lsched_done:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_scheduler_every
            .type kof_scheduler_every, @function
            kof_scheduler_every:
                # edi = ms, rsi = task -> id String. 4 push + subq 8 -> ≡0 no call.
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %rbp
                subq $8, %rsp
                movl %edi, %r13d                   # ms
                movq %rsi, %r12                    # task
                # seq++ (lock)
                leaq kof_sched_lock(%rip), %rsi
                xorl %eax, %eax
            .Lsched_new_lk:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lsched_new_lkd
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lsched_new_lk
            .Lsched_new_lkd:
                incl kof_sched_seq(%rip)
                movl kof_sched_seq(%rip), %ebp     # seq
                movl $0, (%rsi)                    # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                # id = "job-" + int_to_string(seq)
                leaq .Lstr_job_prefix(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                pushq %rax
                movl %ebp, %edi
                call kof_int_to_string
                movq %rax, %rsi
                popq %rdi
                call kof_string_concat
                movq %rax, %rbp                    # id
                # job = alloc(48)
                movl $48, %edi
                call kof_alloc
                movq %rax, %rbx
                xorq %rdx, %rdx
                movq %rdx, 0(%rbx)                 # next = 0
                movq %r12, 8(%rbx)                 # task
                movl %r13d, 16(%rbx)               # ms
                movl $1, 20(%rbx)                  # active
                movq %rbp, 24(%rbx)                # id
                movq %rdx, 32(%rbx)                # tid
                # push na lista (lock)
                leaq kof_sched_lock(%rip), %rsi
                xorl %eax, %eax
            .Lsched_push_lk:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lsched_push_lkd
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lsched_push_lk
            .Lsched_push_lkd:
                movq kof_sched_head(%rip), %rax
                movq %rax, 0(%rbx)                 # job->next = head
                movq %rbx, kof_sched_head(%rip)    # head = job
                movl $0, (%rsi)                    # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                # pthread_create(&job->tid, 0, trampoline, job)
                leaq 32(%rbx), %rdi
                xorl %esi, %esi
                leaq kof_sched_trampoline(%rip), %rdx
                movq %rbx, %rcx
                call pthread_create
                movq %rbp, %rax                    # id
                addq $8, %rsp
                popq %rbp
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_scheduler_at
            .type kof_scheduler_at, @function
            kof_scheduler_at:
                # rdi = cron (ignorado no MVP), rsi = task -> roda a cada 60s
                movq %rsi, %rax
                movl $60000, %edi
                movq %rax, %rsi
                jmp kof_scheduler_every

            # TIME001 (01/09): time.interval/cancel no Native. Mesmo mecanismo do
            # scheduler.every/cancel (thread por job, loop com cancel; captura por
            # referência — mesma lambda). Aliás p/ o símbolo do scheduler.
            # rdi=ms, rsi=task -> id String (igual a kof_scheduler_every).
            .globl kof_time_interval
            .type kof_time_interval, @function
            kof_time_interval:
                jmp kof_scheduler_every
            # rdi=id String -> void (igual a kof_scheduler_cancel).
            .globl kof_time_cancel
            .type kof_time_cancel, @function
            kof_time_cancel:
                jmp kof_scheduler_cancel

            .globl kof_scheduler_cancel
            .type kof_scheduler_cancel, @function
            kof_scheduler_cancel:
                # rdi = id String: acha o job na lista e marca active=0
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %r12                    # id
                leaq kof_sched_lock(%rip), %rsi
                xorl %eax, %eax
            .Lsched_can_lk:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lsched_can_lkd
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lsched_can_lk
            .Lsched_can_lkd:
                movq kof_sched_head(%rip), %rbx
            .Lsched_can_walk:
                testq %rbx, %rbx
                jz .Lsched_can_unlock
                movq %r12, %rdi
                movq 24(%rbx), %rsi
                call kof_string_equals             # rax=1 se igual
                testl %eax, %eax
                jnz .Lsched_can_found
                movq 0(%rbx), %rbx
                jmp .Lsched_can_walk
            .Lsched_can_found:
                movl $0, 20(%rbx)                  # active = 0
            .Lsched_can_unlock:
                leaq kof_sched_lock(%rip), %rsi
                movl $0, (%rsi)                    # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

}
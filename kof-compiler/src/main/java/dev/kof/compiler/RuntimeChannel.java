package dev.kof.compiler;

/**
 * Emissão do ASM de canais (kof_channel_*) do runtime nativo. Domínio isolado do
 * NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeChannel {

    private RuntimeChannel() {}

    static void emitChannel(StringBuilder sb) {
        sb.append("""
            # Canais tipados: FIFO de lista ligada + mutex futex + polling.
            # Struct (56B): 0=head  8=tail  16=count(int)  20=lock(4B)
            # No (16B): 0=value  8=next.  head=frente (de onde recebe),
            # tail=tras (onde envia). lock/unlock inline (tecnica de kof_alloc).
            # receive vazio: libera o lock e dorme 1ms (usleep) -- sem perder wake.
            .globl kof_channel_new
            .type kof_channel_new, @function
            kof_channel_new:
                pushq %rbx
                movl $56, %edi
                call kof_alloc
                movq %rax, %rbx
                xorl %eax, %eax
                movq %rax, 0(%rbx)               # head = 0 (vazio; sentinel NULL)
                movq %rax, 8(%rbx)               # tail = 0
                movl $0, 16(%rbx)                # count
                movl $0, 20(%rbx)                # lock
                movq %rbx, %rax                  # retorna o canal (rax foi zerado!)
                popq %rbx
                ret

            .globl kof_channel_send
            .type kof_channel_send, @function
            kof_channel_send:
                # rdi = chan, rsi = value. chan em r13 (movl $16,%edi clobbera rdi!).
                # entry rsp≡8; 4 push + subq 8 = ≡0 no call (ABI).
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %rbp
                subq $8, %rsp
                movq %rdi, %r13                  # r13 = chan
                movq %rsi, %rbp                  # rbp = value
                leaq 20(%r13), %rsi             # &lock
                xorl %eax, %eax
            .Lchan_send_lock:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lchan_send_locked
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lchan_send_lock
            .Lchan_send_locked:
                movl $16, %edi
                call kof_alloc                   # no em rax
                movq %rbp, 0(%rax)               # no.value
                xorq %rdx, %rdx
                movq %rdx, 8(%rax)               # no.next = 0
                movq 8(%r13), %r12               # r12 = tail
                testq %r12, %r12
                je .Lchan_send_empty             # vazio: head=tail=no
                movq %rax, 8(%r12)               # tail->next = no
                movq %rax, 8(%r13)               # tail = no
                jmp .Lchan_send_count
            .Lchan_send_empty:
                movq %rax, 0(%r13)               # head = no
                movq %rax, 8(%r13)               # tail = no
            .Lchan_send_count:
                addl $1, 16(%r13)                # count++
                leaq 20(%r13), %rdi
                movl $0, (%rdi)                   # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                xorl %eax, %eax
                addq $8, %rsp
                popq %rbp
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_channel_receive
            .type kof_channel_receive, @function
            kof_channel_receive:
                # rdi = chan -> value (polling 1ms se vazio). chan em r13.
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %r13                  # r13 = chan
                leaq 20(%r13), %rsi             # &lock
                xorl %eax, %eax
            .Lchan_recv_lock:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)
                testl %eax, %eax
                jz .Lchan_recv_locked
                movl $1, %edx
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                movq $202, %rax
                syscall
                jmp .Lchan_recv_lock
            .Lchan_recv_locked:
                cmpl $0, 16(%r13)                # count?
                je .Lchan_recv_empty
                movq 0(%r13), %rax               # no = head
                movq 0(%rax), %r12               # value
                movq 8(%rax), %rbx               # next
                movq %rbx, 0(%r13)               # head = next
                decl 16(%r13)                    # count--
                movq %rax, %rdi
                call kof_free                    # libera o no
                leaq 20(%r13), %rdi
                movl $0, (%rdi)                  # unlock
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall                          # clobbera rax -- resultado depois!
                movq %r12, %rax                  # resultado (apos o syscall)
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lchan_recv_empty:
                leaq 20(%r13), %rdi
                movl $0, (%rdi)                  # libera o lock antes de dormir
                movl $1, %esi
                movq $202, %rax
                xorl %edx, %edx
                xorq %r10, %r10
                xorq %r9, %r9
                syscall
                movl $1000, %edi
                call usleep
                jmp .Lchan_recv_lock
            """);
    }

}
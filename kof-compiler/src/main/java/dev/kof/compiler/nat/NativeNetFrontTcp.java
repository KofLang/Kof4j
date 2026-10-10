package dev.kof.compiler.nat;

/**
 * `kof.net` x86-64 (D-KOF-NET, fatia 3): verbos de fluxo TCP do front —
 * `conn.send(Byte[]) -> Int` e `conn.receive(maxBytes) -> Byte[]`.
 *
 * <p>Semântica espelhada de {@code JvmRuntimeSockets}: `send` devolve o total
 * escrito; `receive` volta assim que HÁ dado (semântica de `read`, NUNCA enche
 * `maxBytes` — o deadlock medido na fatia 2) e devolve `[]` no EOF. O array
 * devolvido tem o comprimento REAL lido, não a capacidade pedida.
 */
public final class NativeNetFrontTcp {

    private NativeNetFrontTcp() {}

    /** conn.send(Byte[]) -> Int (bytes escritos). */
    public static void emitNetSend(StringBuilder sb) {
        sb.append("""
            .globl kof_net_send
            .type kof_net_send, @function
            kof_net_send:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                testq %rbx, %rbx
                jz .Lnet_send_bad
                cmpl $2, 0(%rbx)
                jne .Lnet_send_bad
                testq %r12, %r12
                jz .Lnet_send_zero
                movl 16(%r12), %r13d
                testl %r13d, %r13d
                jle .Lnet_send_zero
                movq 8(%rbx), %rdi
                leaq 24(%r12), %rsi
                movl %r13d, %edx
                call kof_net_write
                testq %rax, %rax
                js .Lnet_send_fail
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_send_zero:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_send_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            .Lnet_send_fail:
                leaq .Lnet_msg_send(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** conn.receive(maxBytes) -> Byte[] (uma leitura; [] no EOF). */
    public static void emitNetReceive(StringBuilder sb) {
        sb.append("""
            .globl kof_net_receive
            .type kof_net_receive, @function
            kof_net_receive:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lnet_recv_bad
                cmpl $2, 0(%rbx)
                jne .Lnet_recv_bad
                testl %r12d, %r12d
                jle .Lnet_recv_empty
                movl %r12d, %edi
                movl $1, %esi
                call kof_array_alloc
                movq %rax, %r13
                movq 8(%rbx), %rdi
                leaq 24(%r13), %rsi
                movl %r12d, %edx
                call kof_plat_read
                testq %rax, %rax
                jle .Lnet_recv_empty
                movl %eax, %r14d
                cmpl %r12d, %r14d
                jge .Lnet_recv_full
                movl %r14d, 16(%r13)
            .Lnet_recv_full:
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_recv_empty:
                xorl %edi, %edi
                movl $1, %esi
                call kof_array_alloc
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_recv_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            """);
    }
}

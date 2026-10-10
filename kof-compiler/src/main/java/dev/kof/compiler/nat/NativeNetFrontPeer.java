package dev.kof.compiler.nat;

/**
 * `kof.net` x86-64 (D-KOF-NET, fatia 3): `endpoint.peer() -> String`.
 *
 * <p>Devolve o endereço de origem do ÚLTIMO `receive`, na forma `"a.b.c.d:port"`
 * que `sendTo` aceita (mesmo formato, ida e volta). Antes de qualquer
 * `receive` devolve a String vazia (nunca null), espelhando
 * {@code JvmRuntimeSockets.kof_net_peer}.
 */
public final class NativeNetFrontPeer {

    private NativeNetFrontPeer() {}

    public static void emitNetPeer(StringBuilder sb) {
        sb.append("""
            .globl kof_net_peer
            .type kof_net_peer, @function
            kof_net_peer:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lnet_peer_empty
                cmpl $3, 0(%rbx)
                jne .Lnet_peer_bad
                cmpl $0, 16(%rbx)
                je .Lnet_peer_empty
                movl 24(%rbx), %edi
                call kof_int_to_string
                movq %rax, %r12
                movl $1, %r13d
            .Lnet_peer_octet:
                leaq .Lnet_dot(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r12
                cmpl $1, %r13d
                jne .Lnet_peer_o2
                movl 28(%rbx), %edi
                jmp .Lnet_peer_oi
            .Lnet_peer_o2:
                cmpl $2, %r13d
                jne .Lnet_peer_o3
                movl 32(%rbx), %edi
                jmp .Lnet_peer_oi
            .Lnet_peer_o3:
                movl 36(%rbx), %edi
            .Lnet_peer_oi:
                call kof_int_to_string
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r12
                incl %r13d
                cmpl $3, %r13d
                jle .Lnet_peer_octet
                leaq .Lnet_colon(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %r12
                movl 40(%rbx), %edi
                call kof_int_to_string
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_string_concat
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_peer_empty:
                leaq .Lnet_empty(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_peer_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            """);
    }
}

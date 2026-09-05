package dev.kof.compiler;

/**
Emissão do ASM de rede (kof_net_socket/bind/listen/accept/read/write/close) do
 * runtime nativo. Domínio isolado do NativeRuntime — refactor preserva semântica.
 */
final class RuntimeNet {

    private RuntimeNet() {}

    static void emitNetSocket(StringBuilder sb) {
        sb.append("""
            .globl kof_net_socket
            .type kof_net_socket, @function
            kof_net_socket:
                movq $41, %rax
                syscall
                ret
            """);
    }

    static void emitNetBind(StringBuilder sb) {
        sb.append("""
            .globl kof_net_bind
            .type kof_net_bind, @function
            kof_net_bind:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %esi, %r12d
                movq %rdx, %r13
                subq $16, %rsp
                movw $2, (%rsp)
                movl %r12d, %eax
                xchgb %al, %ah
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)
                movq %r13, %rdx
                testq %rdx, %rdx
                jnz .Lkof_net_bind_custom
                leaq 4(%rsp), %rdx
            .Lkof_net_bind_custom:
                movl %ebx, %edi
                movq %rdx, %rsi
                movq $16, %rdx
                movq $49, %rax
                syscall
                addq $16, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitNetListen(StringBuilder sb) {
        sb.append("""
            .globl kof_net_listen
            .type kof_net_listen, @function
            kof_net_listen:
                movq $50, %rax
                syscall
                ret
            """);
    }

    static void emitNetAccept(StringBuilder sb) {
        sb.append("""
            .globl kof_net_accept
            .type kof_net_accept, @function
            kof_net_accept:
                subq $16, %rsp
                movq $0, (%rsp)
                movq $0, 8(%rsp)
                movq %rsp, %rsi
                leaq 8(%rsp), %rdx
                movq $43, %rax
                syscall
                addq $16, %rsp
                ret
            """);
    }

    static void emitNetRead(StringBuilder sb) {
        sb.append("""
            .globl kof_net_read
            .type kof_net_read, @function
            kof_net_read:
                movq $0, %rax
                syscall
                ret
            """);
    }

    static void emitNetWrite(StringBuilder sb) {
        sb.append("""
            .globl kof_net_write
            .type kof_net_write, @function
            kof_net_write:
                movq $44, %rax
                movq $0x4000, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                syscall
                ret
            """);
    }

    static void emitNetClose(StringBuilder sb) {
        sb.append("""
            .globl kof_net_close
            .type kof_net_close, @function
            kof_net_close:
                movq $3, %rax
                syscall
                ret
            """);
    }

}
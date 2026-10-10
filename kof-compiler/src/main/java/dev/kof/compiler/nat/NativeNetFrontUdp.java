package dev.kof.compiler.nat;

/**
 * `kof.net` x86-64 (D-KOF-NET, fatia 3): frente UDP do front —
 * `net.bind(port) -> Endpoint`, `endpoint.sendTo("host:port", Byte[]) -> Int`,
 * `endpoint.receive(maxBytes) -> Byte[]` (registra a origem) e
 * `endpoint.peer() -> String`.
 *
 * <p>UDP preserva a fronteira da mensagem: `receive` lê UM datagrama e o
 * `peer()` devolve o endereço de origem do último recebido, na forma
 * `"host:port"` que `sendTo` aceita (ida e volta com o mesmo formato).
 * Payload limitado a 65507 (IPv4: 65535 − 20 − 8) — recusa nomeada `NET003`
 * ANTES do syscall. Endereço `"host:port"` malformado = `NET004`.
 */
public final class NativeNetFrontUdp {

    private NativeNetFrontUdp() {}

    /** Helpers UDP: parser `"host:port"` (IPv4 dotted-quad) + guarda de 64 KiB. */
    public static void emitNetUdpHelpers(StringBuilder sb) {
        sb.append("""
            # kof_net_parse_host(rdi=String, rsi=out4) -> rax=port(host), rdx=1 ok/0 bad
            .globl kof_net_parse_host
            .type kof_net_parse_host, @function
            kof_net_parse_host:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                testq %rbx, %rbx
                jz .Lnet_ph_bad
                movl 16(%rbx), %r13d
                testl %r13d, %r13d
                jz .Lnet_ph_bad
                leaq 24(%rbx), %r14
                xorl %r8d, %r8d
                xorl %r9d, %r9d
                xorl %r15d, %r15d
            .Lnet_ph_loop:
                testl %r13d, %r13d
                jz .Lnet_ph_end
                movzbl (%r14), %eax
                cmpb $':', %al
                je .Lnet_ph_colon
                testl %r15d, %r15d
                jnz .Lnet_ph_port
                cmpb $'.', %al
                je .Lnet_ph_dot
                cmpb $'0', %al
                jb .Lnet_ph_bad
                cmpb $'9', %al
                ja .Lnet_ph_bad
                imull $10, %r9d, %r9d
                subl $'0', %eax
                addl %eax, %r9d
                incq %r14
                decl %r13d
                jmp .Lnet_ph_loop
            .Lnet_ph_dot:
                cmpl $3, %r8d
                jae .Lnet_ph_bad
                movb %r9b, (%r12,%r8)
                xorl %r9d, %r9d
                incl %r8d
                incq %r14
                decl %r13d
                jmp .Lnet_ph_loop
            .Lnet_ph_colon:
                cmpl $3, %r8d
                jne .Lnet_ph_bad
                movb %r9b, (%r12,%r8)
                movl $1, %r15d
                xorl %r9d, %r9d
                incq %r14
                decl %r13d
                jmp .Lnet_ph_loop
            .Lnet_ph_port:
                cmpb $'0', %al
                jb .Lnet_ph_bad
                cmpb $'9', %al
                ja .Lnet_ph_bad
                imull $10, %r9d, %r9d
                subl $'0', %eax
                addl %eax, %r9d
                incq %r14
                decl %r13d
                jmp .Lnet_ph_loop
            .Lnet_ph_end:
                testl %r15d, %r15d
                jz .Lnet_ph_bad
                movq %r9, %rax
                movl $1, %edx
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_ph_bad:
                xorl %eax, %eax
                xorl %edx, %edx
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_net_udp_check(rdi=Byte[]): >65507 => NET003
            .globl kof_net_udp_check
            .type kof_net_udp_check, @function
            kof_net_udp_check:
                testq %rdi, %rdi
                jz .Lnet_uc_ok
                cmpl $65507, 16(%rdi)
                jle .Lnet_uc_ok
                leaq .Lnet_msg_toobig(%rip), %rdi
                call kof_net_throw
            .Lnet_uc_ok:
                ret
            """);
    }

    /** net.bind(port) -> Endpoint (SOCK_DGRAM). */
    public static void emitNetBind(StringBuilder sb) {
        sb.append("""
            .globl kof_net_bind
            .type kof_net_bind, @function
            kof_net_bind:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %r12d
                movl $2, %edi
                movl $2, %esi
                xorl %edx, %edx
                call kof_plat_net_socket
                movq %rax, %rbx
                testq %rbx, %rbx
                js .Lnet_bind_fail
                subq $16, %rsp
                movw $2, (%rsp)
                movl %r12d, %eax
                xchgb %al, %ah
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)
                movq $0, 8(%rsp)
                movl %ebx, %edi
                movq %rsp, %rsi
                movl $16, %edx
                call kof_plat_net_bind
                testq %rax, %rax
                js .Lnet_bind_fail_sp
                addq $16, %rsp
                movl $3, %edi
                movl %ebx, %esi
                call kof_net_new_handle
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_bind_fail_sp:
                addq $16, %rsp
            .Lnet_bind_fail:
                movl %ebx, %edi
                call kof_plat_close
                leaq .Lnet_msg_bind(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** endpoint.sendTo("host:port", Byte[]) -> Int. */
    public static void emitNetSendTo(StringBuilder sb) {
        sb.append("""
            .globl kof_net_sendTo
            .type kof_net_sendTo, @function
            kof_net_sendTo:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                testq %rbx, %rbx
                jz .Lnet_sendto_bad
                cmpl $3, 0(%rbx)
                jne .Lnet_sendto_bad
                movq %r13, %rdi
                call kof_net_udp_check
                subq $32, %rsp
                movw $2, (%rsp)
                movq %r12, %rdi
                leaq 4(%rsp), %rsi
                call kof_net_parse_host
                testl %edx, %edx
                jz .Lnet_sendto_bad_sp
                xchgb %al, %ah
                movw %ax, 2(%rsp)
                movq $0, 8(%rsp)
                testq %r13, %r13
                jz .Lnet_sendto_zero
                movl 16(%r13), %r14d
                testl %r14d, %r14d
                jle .Lnet_sendto_zero
                movq 8(%rbx), %rdi
                leaq 24(%r13), %rsi
                movl %r14d, %edx
                xorl %r10d, %r10d
                movq %rsp, %r8
                movl $16, %r9d
                movl $44, %eax
                syscall
                addq $32, %rsp
                testq %rax, %rax
                js .Lnet_sendto_fail
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_sendto_zero:
                addq $32, %rsp
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_sendto_bad_sp:
                addq $32, %rsp
            .Lnet_sendto_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            .Lnet_sendto_fail:
                leaq .Lnet_msg_send(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** endpoint.receive(maxBytes) -> Byte[] (um datagrama + origem gravada). */
    public static void emitNetReceiveFrom(StringBuilder sb) {
        sb.append("""
            .globl kof_net_receiveFrom
            .type kof_net_receiveFrom, @function
            kof_net_receiveFrom:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                testq %rbx, %rbx
                jz .Lnet_recvfrom_bad
                cmpl $3, 0(%rbx)
                jne .Lnet_recvfrom_bad
                testl %r12d, %r12d
                jle .Lnet_recvfrom_empty
                movl %r12d, %edi
                movl $1, %esi
                call kof_array_alloc
                movq %rax, %r13
                subq $32, %rsp
                movl $16, 0(%rsp)
                movq 8(%rbx), %rdi
                leaq 24(%r13), %rsi
                movl %r12d, %edx
                xorl %r10d, %r10d
                leaq 16(%rsp), %r8
                movq %rsp, %r9
                movl $45, %eax
                syscall
                testq %rax, %rax
                jle .Lnet_recvfrom_fail_sp
                movzbl 20(%rsp), %ecx
                movl %ecx, 24(%rbx)
                movzbl 21(%rsp), %ecx
                movl %ecx, 28(%rbx)
                movzbl 22(%rsp), %ecx
                movl %ecx, 32(%rbx)
                movzbl 23(%rsp), %ecx
                movl %ecx, 36(%rbx)
                movzwl 18(%rsp), %ecx
                rolw $8, %cx
                movzwl %cx, %ecx
                movl %ecx, 40(%rbx)
                movl $1, 16(%rbx)
                movl %eax, %r14d
                cmpl %r12d, %r14d
                jge .Lnet_recvfrom_full
                movl %r14d, 16(%r13)
            .Lnet_recvfrom_full:
                addq $32, %rsp
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_recvfrom_empty:
                xorl %edi, %edi
                movl $1, %esi
                call kof_array_alloc
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_recvfrom_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            .Lnet_recvfrom_fail_sp:
                addq $32, %rsp
                leaq .Lnet_msg_recv(%rip), %rdi
                call kof_net_throw
            """);
    }
}

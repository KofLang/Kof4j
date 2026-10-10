package dev.kof.compiler.nat;

/**
 * `kof.net` x86-64 (D-KOF-NET, plan {@code docs/stdlib/network-kofnet-plan.md},
 * fatia 3): TCP core do front — helpers de erro/handle + listen/accept/connect/close.
 *
 * <p>Diferente da costura CRUA {@code kof_net_socket/read/write} (fd puro,
 * consumida pelo MySQL/HTTP), o front opera sobre HANDLES opacos (um bloco do
 * heap com tag): `kof_net_listen` devolve o handle do listener, `accept` o da
 * conexão, `net.bind` o do endpoint. O `close` é o único verbo polimórfico —
 * checa a tag ANTES de fechar (nunca no objeto errado, espelho do
 * {@code JvmRuntimeSockets.kof_net_close}).
 *
 * <p>Layout do handle (payload de 40 bytes devolvido por {@code kof_alloc}):
 * <pre>
 *   0(%h)  tag      1=Listener 2=Conn 3=Endpoint
 *   8(%h)  fd       fd do socket
 *   16(%h) hasPeer  0/1 (só endpoint)
 *   24(%h) ip[4]    último IP de origem (bytes a.b.c.d)
 *   28(%h) port     última porta de origem (host order)
 * </pre>
 *
 * <p>Limite honesto v1: `connect` resolve apenas IPv4 dotted-quad (mesma
 * escolha do HTTP nativo e do MySQL nativo, que caem em 127.0.0.1 quando o
 * host não é numérico) — hostname é recusado com mensagem nomeada, nunca
 * resolvido em silêncio. Payload UDP limitado a 65507 (NET003). Unicast-only.
 */
public final class NativeNetFront {

    private NativeNetFront() {}

    /** Helpers compartilhados: mensagens, throw, alocação de handle, socket TCP
     *  com SO_REUSEADDR. Emitido uma única vez; as fatias de verbo o referenciam. */
    public static void emitNetHelpers(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lnet_msg_listen:   .asciz "kof.net: cannot listen (NET002)"
            .Lnet_msg_accept:   .asciz "kof.net: accept failed (NET002)"
            .Lnet_msg_connect:  .asciz "kof.net: cannot connect to IPv4 literal (NET002)"
            .Lnet_msg_bind:     .asciz "kof.net: cannot bind (NET002)"
            .Lnet_msg_handle:   .asciz "kof.net: not a net handle (NET005)"
            .Lnet_msg_addr:     .asciz "kof.net: address needs host:port (NET004)"
            .Lnet_msg_vetted:   .asciz "kof.net: connect to a vetted address needs a non-empty address (NET004)"
            .Lnet_msg_resolve:  .asciz "kof.net: cannot resolve host (NET004)"
            .Lnet_msg_toobig:   .asciz "kof.net: datagram exceeds 65507 byte UDP payload bound (NET003)"
            .Lnet_msg_send:     .asciz "kof.net: send failed (NET002)"
            .Lnet_msg_recv:     .asciz "kof.net: receive failed (NET002)"
            .Lnet_empty:        .asciz ""
            .Lnet_dot:          .asciz "."
            .Lnet_colon:        .asciz ":"
            .section .text

            # kof_net_throw(rdi=cstr): monta um KofString e lança (try/catch Kof).
            .globl kof_net_throw
            .type kof_net_throw, @function
            kof_net_throw:
                pushq %rbx
                movq %rdi, %rbx
                xorq %rcx, %rcx
            .Lnet_throw_len:
                cmpb $0, (%rbx,%rcx)
                je .Lnet_throw_len_done
                incq %rcx
                jmp .Lnet_throw_len
            .Lnet_throw_len_done:
                movq %rbx, %rdi
                movl %ecx, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
                popq %rbx
                ret

            # kof_net_new_handle(edi=tag, esi=fd) -> rax=handle
            .globl kof_net_new_handle
            .type kof_net_new_handle, @function
            kof_net_new_handle:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %ebx
                movl %esi, %r12d
                movl $48, %edi
                call kof_alloc
                movq %rax, %r13
                movl %ebx, 0(%r13)
                movq %r12, 8(%r13)
                movq $0, 16(%r13)
                movq $0, 24(%r13)
                movq $0, 32(%r13)
                movl $0, 40(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_net_tcp_socket() -> rax=fd (SOCK_STREAM + SO_REUSEADDR)
            .globl kof_net_tcp_socket
            .type kof_net_tcp_socket, @function
            kof_net_tcp_socket:
                pushq %rbx
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                call kof_plat_net_socket
                movq %rax, %rbx
                testq %rbx, %rbx
                js .Lnet_tcp_sock_done
                subq $16, %rsp
                movl $1, (%rsp)
                movl %ebx, %edi
                movl $1, %esi
                movl $2, %edx
                movq %rsp, %r10
                movl $4, %r8d
                movl $54, %eax
                syscall
                addq $16, %rsp
            .Lnet_tcp_sock_done:
                movq %rbx, %rax
                popq %rbx
                ret
            """);
    }

    /** net.listen(port) -> Listener. */
    public static void emitNetListen(StringBuilder sb) {
        sb.append("""
            .globl kof_net_listen
            .type kof_net_listen, @function
            kof_net_listen:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %r12d
                call kof_net_tcp_socket
                movq %rax, %rbx
                testq %rbx, %rbx
                js .Lnet_listen_fail
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
                js .Lnet_listen_fail_sp
                movl %ebx, %edi
                movl $64, %esi
                call kof_plat_net_listen
                testq %rax, %rax
                js .Lnet_listen_fail_sp
                addq $16, %rsp
                movl $1, %edi
                movl %ebx, %esi
                call kof_net_new_handle
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_listen_fail_sp:
                addq $16, %rsp
            .Lnet_listen_fail:
                movl %ebx, %edi
                call kof_plat_close
                leaq .Lnet_msg_listen(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** listener.accept() -> Conn. */
    public static void emitNetAccept(StringBuilder sb) {
        sb.append("""
            .globl kof_net_accept
            .type kof_net_accept, @function
            kof_net_accept:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lnet_accept_bad
                cmpl $1, 0(%rbx)
                jne .Lnet_accept_bad
                movq 8(%rbx), %rdi
                subq $32, %rsp
                movq %rsp, %rsi
                leaq 16(%rsp), %rdx
                movq $16, 16(%rsp)
                call kof_plat_net_accept
                addq $32, %rsp
                testq %rax, %rax
                js .Lnet_accept_fail
                movl $2, %edi
                movl %eax, %esi
                call kof_net_new_handle
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_accept_bad:
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            .Lnet_accept_fail:
                leaq .Lnet_msg_accept(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** net.connect(host, port) -> Conn. IPv4 dotted-quad only (v1). */
    public static void emitNetConnect(StringBuilder sb) {
        sb.append("""
            .globl kof_net_connect
            .type kof_net_connect, @function
            kof_net_connect:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                call kof_net_tcp_socket
                movq %rax, %r13
                testq %r13, %r13
                js .Lnet_connect_fail
                subq $32, %rsp
                movw $2, (%rsp)
                movl %r12d, %eax
                xchgb %al, %ah
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)
                movq $0, 8(%rsp)
                testq %rbx, %rbx
                jz .Lnet_connect_bad_sp
                movl 16(%rbx), %ecx
                testl %ecx, %ecx
                jz .Lnet_connect_bad_sp
                leaq 24(%rbx), %rsi
                xorl %r8d, %r8d
                xorl %r9d, %r9d
            .Lnet_connect_parse:
                testl %ecx, %ecx
                jz .Lnet_connect_parse_end
                movzbl (%rsi), %eax
                cmpb $'.', %al
                je .Lnet_connect_dot
                cmpb $'0', %al
                jb .Lnet_connect_bad_sp
                cmpb $'9', %al
                ja .Lnet_connect_bad_sp
                imull $10, %r9d, %r9d
                subl $'0', %eax
                addl %eax, %r9d
                incq %rsi
                decl %ecx
                jmp .Lnet_connect_parse
            .Lnet_connect_dot:
                cmpl $3, %r8d
                jae .Lnet_connect_bad_sp
                movb %r9b, 4(%rsp,%r8)
                xorl %r9d, %r9d
                incl %r8d
                incq %rsi
                decl %ecx
                jmp .Lnet_connect_parse
            .Lnet_connect_parse_end:
                cmpl $3, %r8d
                jne .Lnet_connect_bad_sp
                movb %r9b, 4(%rsp,%r8)
                movl %r13d, %edi
                movq %rsp, %rsi
                movl $16, %edx
                call kof_plat_net_connect
                testq %rax, %rax
                js .Lnet_connect_bad_sp
                addq $32, %rsp
                movl $2, %edi
                movl %r13d, %esi
                call kof_net_new_handle
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lnet_connect_bad_sp:
                addq $32, %rsp
            .Lnet_connect_fail:
                movl %r13d, %edi
                call kof_plat_close
                leaq .Lnet_msg_connect(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** #759 / NET1: net.resolve(host) -> List&lt;String&gt; (todos os A/AAAA, ordem do
     *  SO) via libc {@code getaddrinfo}/{@code inet_ntop}. Um host desconhecido
     *  lança String catchável (NET004). */
    public static void emitNetResolve(StringBuilder sb) {
        sb.append("""
            .globl kof_net_resolve
            .type kof_net_resolve, @function
            kof_net_resolve:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $176, %rsp
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lresolve_fail
                movl 16(%rbx), %eax
                testl %eax, %eax
                jz .Lresolve_fail
                movq $0, 64(%rsp)
                movq $0, 72(%rsp)
                movq $0, 80(%rsp)
                movq $0, 88(%rsp)
                movq $0, 96(%rsp)
                movq $0, 104(%rsp)
                movl $1, 72(%rsp)
                leaq 24(%rbx), %rdi
                xorq %rsi, %rsi
                leaq 64(%rsp), %rdx
                leaq 112(%rsp), %rcx
                call getaddrinfo
                testl %eax, %eax
                jne .Lresolve_fail
                movq 112(%rsp), %r12
                call kof_list_new
                movq %rax, %r13
            .Lresolve_loop:
                testq %r12, %r12
                jz .Lresolve_done
                cmpl $2, 4(%r12)
                jne .Lresolve_next
                movl $2, %edi
                movq 24(%r12), %rsi
                addq $4, %rsi
                movq %rsp, %rdx
                movl $64, %ecx
                call inet_ntop
                movq %rsp, %r14
                xorq %rcx, %rcx
            .Lresolve_len:
                cmpb $0, (%r14,%rcx)
                je .Lresolve_len_done
                incq %rcx
                jmp .Lresolve_len
            .Lresolve_len_done:
                movq %r14, %rdi
                movl %ecx, %esi
                call kof_string_from_literal
                movq %r13, %rdi
                movq %rax, %rsi
                call kof_list_add
            .Lresolve_next:
                movq 40(%r12), %r12
                jmp .Lresolve_loop
            .Lresolve_done:
                movq 112(%rsp), %rdi
                call freeaddrinfo
                movq %r13, %rax
                addq $176, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lresolve_fail:
                leaq .Lnet_msg_resolve(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** #759 / NET1: net.connect(host, port, address) -> Conn. O `address` já
     *  validado é o destino real (dotted-quad v1, mesma rota do connect);
     *  `host` fica para o Host/SNI/cert do chamador. Endereço em branco recusa
     *  NET004, nunca uma re-resolução silenciosa. */
    public static void emitNetConnectAddr(StringBuilder sb) {
        sb.append("""
            .globl kof_net_connect_addr
            .type kof_net_connect_addr, @function
            kof_net_connect_addr:
                testq %rdx, %rdx
                jz .Lca_fail
                movl 16(%rdx), %eax
                testl %eax, %eax
                jz .Lca_fail
                movq %rdx, %rdi
                jmp kof_net_connect
            .Lca_fail:
                leaq .Lnet_msg_vetted(%rip), %rdi
                call kof_net_throw
            """);
    }

    /** handle.close() — polimórfico por tag; tag desconhecida = NET005. */
    public static void emitNetClose(StringBuilder sb) {
        sb.append("""
            .globl kof_net_close
            .type kof_net_close, @function
            kof_net_close:
                pushq %rbx
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lnet_close_done
                movl 0(%rbx), %eax
                cmpl $1, %eax
                je .Lnet_close_fd
                cmpl $2, %eax
                je .Lnet_close_fd
                cmpl $3, %eax
                je .Lnet_close_fd
                leaq .Lnet_msg_handle(%rip), %rdi
                call kof_net_throw
            .Lnet_close_fd:
                movq 8(%rbx), %rdi
                call kof_plat_close
            .Lnet_close_done:
                popq %rbx
                ret
            """);
    }
}

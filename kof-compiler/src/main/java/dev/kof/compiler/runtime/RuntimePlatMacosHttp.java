package dev.kof.compiler.runtime;

/**
 * M-6 (#1135, D-NATIVE-MACOS): a família HTTP/SOCKETS da costura sobre a
 * libSystem — as chamadas que o core do {@code kof.http} fazia direto.
 *
 * <p><b>O QUE MUDA NO DARWIN (medido nos headers do SDK, não suposto):</b>
 *
 * <pre>
 *                 Linux            Darwin
 *   O_NONBLOCK    0x800 (2048)     0x4
 *   F_SETFL       4                4
 *   SOL_SOCKET    1                0xffff
 *   SO_RCVTIMEO   20               0x1006
 *   SO_SNDTIMEO   21               0x1005
 *   SO_ERROR      4                0x1007
 * </pre>
 *
 * <p>E o <b>ERRO</b>: o syscall devolve {@code -errno} (o core compara o
 * valor — o {@code EINPROGRESS} = 115 no Linux — para saber que o connect
 * não-bloqueante está a caminho); a libc devolve {@code -1} e põe o errno num
 * slot. O corpo do macOS <b>NORMALIZA</b>: lê o errno ({@code int
 * *__error(void)} — declarado no errno.h do SDK) e devolve {@code -errno},
 * como o kernel.
 *
 * <p><b>O REGISTO DA 4ª ARGUMENTA:</b> o syscall põe a 4ª em {@code %r10};
 * a ABI SysV da libc põe-a em {@code %rcx} — o mesmo desvio do {@code mmap}
 * (medido na fatia M-3).
 */
public final class RuntimePlatMacosHttp {

    private RuntimePlatMacosHttp() {}

    /**
     * Os corpos macOS da família HTTP/sockets, com o retorno normalizado
     * ({@code -errno} nos erros).
     */
    public static void emitHttp(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_connect
            .type kof_plat_connect, @function
            kof_plat_connect:
                # rdi=fd, rsi=addr, rdx=len -> 0 | -errno
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp          # a libSystem usa SSE
                call connect
                testl %eax, %eax
                jne .Lpc_err
                xorl %eax, %eax
                jmp .Lpc_ret
            .Lpc_err:
                call __error              # int *__error(void) — o errno do Darwin
                movl (%rax), %eax
                negl %eax                 # -errno (a convencao do kernel)
                cmpq $-36, %rax           # EINPROGRESS no DARWIN e' 36 (115 no Linux)
                jne .Lpc_ret
                movq $-115, %rax          # normaliza p/ o valor do contrato da costura
            .Lpc_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_nonblock
            .type kof_plat_nonblock, @function
            kof_plat_nonblock:
                # rdi=fd, rsi=1 liga / 0 desliga (F_SETFL + O_NONBLOCK)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                pushq %rdi               # o fd sobrevive ao teste do seletor
                testq %rsi, %rsi
                jz .Lpn_off
                movl $4, %edx            # O_NONBLOCK = 0x4 no DARWIN (0x800 no Linux)
                jmp .Lpn_go
            .Lpn_off:
                xorl %edx, %edx
            .Lpn_go:
                popq %rdi
                movl $4, %esi            # F_SETFL
                xorl %eax, %eax          # fcntl e' variadico: AL = nº de XMM usados
                call fcntl
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_poll
            .type kof_plat_poll, @function
            kof_plat_poll:
                # rdi=fds, rsi=n, rdx=timeout_ms
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call poll
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_sock_timeout
            .type kof_plat_sock_timeout, @function
            kof_plat_sock_timeout:
                # rdi=fd, rsi=0 recv/1 send, rdx=segundos
                pushq %rbx
                movq %rdi, %rbx
                pushq %r12
                movq %rsi, %r12
                pushq %r13
                movq %rdx, %r13
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                subq $16, %rsp           # o struct timeval vive no topo alinhado
                movq %r13, (%rsp)
                movq $0, 8(%rsp)
                movq %rbx, %rdi
                movl $0xffff, %esi        # SOL_SOCKET no DARWIN (1 no Linux)
                testq %r12, %r12         # 0 = receive, 1 = send
                jnz .Lst_snd
                movl $0x1006, %edx        # SO_RCVTIMEO no DARWIN (20 no Linux)
                jmp .Lst_go
            .Lst_snd:
                movl $0x1005, %edx        # SO_SNDTIMEO no DARWIN (21 no Linux)
            .Lst_go:
                movq %rsp, %rcx          # a 4a arg da libSystem vai em %rcx
                movl $16, %r8d
                call setsockopt
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_plat_sock_error
            .type kof_plat_sock_error, @function
            kof_plat_sock_error:
                # rdi=fd -> rax = o valor de SO_ERROR (0 = sem erro)
                pushq %rbx
                movq %rdi, %rbx
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                subq $16, %rsp
                movl $0, (%rsp)
                movl $4, 4(%rsp)
                movq %rbx, %rdi
                movl $0xffff, %esi        # SOL_SOCKET no DARWIN
                movl $0x1007, %edx        # SO_ERROR no DARWIN (4 no Linux)
                leaq (%rsp), %rcx        # o valor
                leaq 4(%rsp), %r8        # o socklen* (a libc quer o PONTEIRO)
                call getsockopt
                movl (%rsp), %eax
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %rbx
                ret
            """);
    }
}

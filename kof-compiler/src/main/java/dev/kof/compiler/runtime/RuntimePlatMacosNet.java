package dev.kof.compiler.runtime;

/**
 * M-6 (#1135, D-NATIVE-MACOS): a família de REDE da costura
 * {@code kof_plat_net_*} sobre a libSystem — separada de
 * {@link RuntimePlatMacos} pelo gate de 500 linhas (responsabilidade
 * própria: os sockets).
 */
public final class RuntimePlatMacosNet {

    private RuntimePlatMacosNet() {}

    /**
     * A FAMÍLIA rede (M-6): as chamadas de socket pelo libSystem.
     *
     * <p>A ABI dos sockets do BSD é a MESMA da libc Linux
     * ({@code socket(domain, type, protocol)}, {@code connect(fd, addr, len)},
     * {@code bind}, {@code listen(fd, backlog)}, {@code accept(fd, addr,
     * lenp)} — os mesmos registradores SysV. A ÚNICA diferença é a 4ª flag do
     * {@code sendto}: o syscall Linux a põe em {@code %r10}; a ABI SysV, em
     * {@code %rcx} — o corpo move antes de chamar.
     *
     * <p><b>TRANSPARENTE:</b> o corpo preserva os caller-saved (só o
     * {@code %rax} — o retorno — muda), então os chamadores do runtime (que
     * foram escritos contra o syscall) continuam certos.
     */
    public static void emitNet(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_net_socket
.type kof_plat_net_socket, @function
kof_plat_net_socket:
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call socket
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret

.globl kof_plat_net_connect
.type kof_plat_net_connect, @function
kof_plat_net_connect:
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call connect
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret

.globl kof_plat_net_bind
.type kof_plat_net_bind, @function
kof_plat_net_bind:
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call bind
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret

.globl kof_plat_net_listen
.type kof_plat_net_listen, @function
kof_plat_net_listen:
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call listen
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret

.globl kof_plat_net_accept
.type kof_plat_net_accept, @function
kof_plat_net_accept:
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call accept
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret

.globl kof_plat_net_send
.type kof_plat_net_send, @function
kof_plat_net_send:
    movq %r10, %rcx          # a 4a flag: o syscall Linux usa %r10, a ABI SysV usa %rcx
    pushq %rcx
    pushq %rdx
    pushq %rsi
    pushq %rdi
    pushq %r8
    pushq %r9
    pushq %r10
    pushq %r11
    pushq %r15
    movq %rsp, %r15
    andq $-16, %rsp          # a libSystem usa SSE: rsp ≡ 0 no call
    call sendto
    subq %rsp, %r15
    addq %r15, %rsp
    popq %r15
    popq %r11
    popq %r10
    popq %r9
    popq %r8
    popq %rdi
    popq %rsi
    popq %rdx
    popq %rcx
    ret
            """);
    }

}


package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM da costura CRUA de rede (kof_net_socket/read/write) do runtime
 * nativo — consumida pelo cliente MySQL/HTTP nativo (fd puro). Domínio isolado
 * do NativeRuntime.
 *
 * <p>Os nomes de superfície `kof.net` (listen/accept/connect/bind/send/receive/
 * sendTo/receiveFrom/peer/close) NÃO vivem aqui: são emitidos por
 * {@link NativeNetFront} e operam sobre HANDLES opacos, não fds crus. As
 * definições cruas de bind/listen/accept/close foram REMOVIDAS na fatia 3
 * (`D-KOF-NET`) para liberar os símbolos — nenhum consumidor cru as usava
 * (medido por grep) e os dois chamadores de `kof_net_close` (MySQL/HTTP)
 * passaram a `kof_plat_close`, que é o close de fd real.
 */
public final class RuntimeNet {

    private RuntimeNet() {}

    public static void emitNetSocket(StringBuilder sb) {
        sb.append("""
            .globl kof_net_socket
            .type kof_net_socket, @function
            kof_net_socket:
                jmp kof_plat_net_socket
            """);
    }

    public static void emitNetRead(StringBuilder sb) {
        sb.append("""
            .globl kof_net_read
            .type kof_net_read, @function
            kof_net_read:
                jmp kof_plat_read
            """);
    }

    public static void emitNetWrite(StringBuilder sb) {
        sb.append("""
            .globl kof_net_write
            .type kof_net_write, @function
            kof_net_write:
                movq $0x4000, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                call kof_plat_net_send
                ret
            """);
    }

}
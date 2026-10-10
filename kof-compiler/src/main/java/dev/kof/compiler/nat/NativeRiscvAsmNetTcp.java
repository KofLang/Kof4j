package dev.kof.compiler.nat;

// D-KOF-NET fatia 4b: verbos de fluxo TCP do front `kof.net` no riscv64 —
// conn.send(Byte[]) -> Int e conn.receive(maxBytes) -> Byte[]. Semantica
// espelhada de JvmRuntimeSockets/NativeNetFrontTcp: `send` devolve o total
// escrito; `receive` volta assim que HA dado (semantica de `read`, nunca enche
// maxBytes) e devolve `[]` no EOF. O array devolvido tem o comprimento REAL.
public final class NativeRiscvAsmNetTcp {

    private NativeRiscvAsmNetTcp() {}

    static String RISCV_RUNTIME_ASM_NET_TCP = """
            .section .text
            # conn.send(a0=handle, a1=Byte[]) -> a0=Int (bytes escritos)
            .globl kof_net_send
            .type kof_net_send, @function
            kof_net_send:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lnet_send_bad
                lw   t0, 0(s0)
                li   t1, 2
                bne  t0, t1, .Lnet_send_bad
                beqz s1, .Lnet_send_zero
                lw   s2, 16(s1)
                blez s2, .Lnet_send_zero
                ld   a0, 8(s0)
                addi a1, s1, 24
                mv   a2, s2
                li   a3, 16384           # MSG_NOSIGNAL
                li   a4, 0               # sendto dest=NULL
                li   a5, 0
                call kof_plat_net_send
                bltz a0, .Lnet_send_fail
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_send_zero:
                li   a0, 0
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_send_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            .Lnet_send_fail:
                la   a0, .Lnet_msg_send
                call kof_net_throw

            # conn.receive(a0=handle, a1=maxBytes) -> a0=Byte[] ([] no EOF)
            .globl kof_net_receive
            .type kof_net_receive, @function
            kof_net_receive:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lnet_recv_bad
                lw   t0, 0(s0)
                li   t1, 2
                bne  t0, t1, .Lnet_recv_bad
                blez s1, .Lnet_recv_empty
                mv   a0, s1
                li   a1, 1
                call kof_array_alloc
                mv   s2, a0
                ld   a0, 8(s0)
                addi a1, s2, 24
                mv   a2, s1
                call kof_plat_read
                blez a0, .Lnet_recv_empty
                mv   s3, a0
                bge  s3, s1, .Lnet_recv_full
                sw   s3, 16(s2)
            .Lnet_recv_full:
                mv   a0, s2
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            .Lnet_recv_empty:
                li   a0, 0
                li   a1, 1
                call kof_array_alloc
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            .Lnet_recv_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            """;
}

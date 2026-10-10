package dev.kof.compiler.nat;

// D-KOF-NET fatia 4b: frente UDP do front `kof.net` no riscv64 — net.bind ->
// Endpoint, endpoint.sendTo("host:port", Byte[]) -> Int, endpoint.receive
// (um datagrama, registra a origem) e endpoint.peer() -> String. UDP preserva a
// fronteira da mensagem; payload limitado a 65507 (NET003) e endereco
// malformado = NET004. Syscalls: sendto=206 recvfrom=207.
public final class NativeRiscvAsmNetUdp {

    private NativeRiscvAsmNetUdp() {}

    static String RISCV_RUNTIME_ASM_NET_UDP = """
            .section .text
            # kof_net_parse_host(a0=String, a1=out4) -> a0=port, a1=1 ok/0 bad
            .globl kof_net_parse_host
            .type kof_net_parse_host, @function
            kof_net_parse_host:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lph_bad
                lw   s2, 16(s0)
                beqz s2, .Lph_bad
                addi s3, s0, 24
                li   s4, 0
                li   s5, 0
                li   s6, 0
            .Lph_loop:
                beqz s2, .Lph_end
                lbu  t0, 0(s3)
                li   t1, 58
                beq  t0, t1, .Lph_colon
                bnez s6, .Lph_port
                li   t1, 46
                beq  t0, t1, .Lph_dot
                li   t1, 48
                bltu t0, t1, .Lph_bad
                li   t1, 57
                bltu t1, t0, .Lph_bad
                li   t1, 10
                mul  s5, s5, t1
                addi t0, t0, -48
                add  s5, s5, t0
                addi s3, s3, 1
                addi s2, s2, -1
                j    .Lph_loop
            .Lph_dot:
                li   t1, 3
                bgeu s4, t1, .Lph_bad
                add  t1, s1, s4
                sb   s5, 0(t1)
                li   s5, 0
                addi s4, s4, 1
                addi s3, s3, 1
                addi s2, s2, -1
                j    .Lph_loop
            .Lph_colon:
                li   t1, 3
                bne  s4, t1, .Lph_bad
                add  t1, s1, s4
                sb   s5, 0(t1)
                li   s6, 1
                li   s5, 0
                addi s3, s3, 1
                addi s2, s2, -1
                j    .Lph_loop
            .Lph_port:
                li   t1, 48
                bltu t0, t1, .Lph_bad
                li   t1, 57
                bltu t1, t0, .Lph_bad
                li   t1, 10
                mul  s5, s5, t1
                addi t0, t0, -48
                add  s5, s5, t0
                addi s3, s3, 1
                addi s2, s2, -1
                j    .Lph_loop
            .Lph_end:
                beqz s6, .Lph_bad
                mv   a0, s5
                li   a1, 1
                j    .Lph_ret
            .Lph_bad:
                li   a0, 0
                li   a1, 0
            .Lph_ret:
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_net_udp_check(a0=Byte[]): >65507 => NET003
            .globl kof_net_udp_check
            .type kof_net_udp_check, @function
            kof_net_udp_check:
                addi sp, sp, -32
                sd   ra, 24(sp)
                beqz a0, .Lnet_uc_ok
                lw   t0, 16(a0)
                li   t1, 65507
                ble  t0, t1, .Lnet_uc_ok
                la   a0, .Lnet_msg_toobig
                call kof_net_throw
            .Lnet_uc_ok:
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # net.bind(a0=port) -> Endpoint (SOCK_DGRAM)
            .globl kof_net_bind
            .type kof_net_bind, @function
            kof_net_bind:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s1, a0
                li   a0, 2
                li   a1, 2
                li   a2, 0
                call kof_plat_net_socket
                mv   s0, a0
                bltz s0, .Lnet_bind_fail
                li   t0, 2
                sh   t0, 16(sp)
                addi t0, sp, 18
                slli t1, s1, 8
                srli t2, s1, 8
                andi t2, t2, 255
                or   t1, t1, t2
                sh   t1, 0(t0)
                sw   zero, 20(sp)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                mv   a0, s0
                addi a1, sp, 16
                li   a2, 16
                call kof_plat_net_bind
                bltz a0, .Lnet_bind_fail
                li   a0, 3
                mv   a1, s0
                call kof_net_new_handle
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_bind_fail:
                mv   a0, s0
                call kof_plat_close
                la   a0, .Lnet_msg_bind
                call kof_net_throw

            # endpoint.sendTo(a0=handle, a1="host:port", a2=Byte[]) -> Int
            .globl kof_net_sendTo
            .type kof_net_sendTo, @function
            kof_net_sendTo:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                beqz s0, .Lnet_sendto_bad
                lw   t0, 0(s0)
                li   t1, 3
                bne  t0, t1, .Lnet_sendto_bad
                mv   a0, s2
                call kof_net_udp_check
                li   t0, 2
                sh   t0, 16(sp)
                mv   a0, s1
                addi a1, sp, 20
                call kof_net_parse_host
                beqz a1, .Lnet_sendto_bad
                addi t0, sp, 18
                slli t1, a0, 8
                srli t2, a0, 8
                andi t2, t2, 255
                or   t1, t1, t2
                sh   t1, 0(t0)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                beqz s2, .Lnet_sendto_zero
                lw   s3, 16(s2)
                blez s3, .Lnet_sendto_zero
                ld   a0, 8(s0)
                addi a1, s2, 24
                mv   a2, s3
                li   a3, 0
                addi a4, sp, 16
                li   a5, 16
                li   a7, 206
                ecall
                bltz a0, .Lnet_sendto_fail
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            .Lnet_sendto_zero:
                li   a0, 0
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            .Lnet_sendto_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            .Lnet_sendto_fail:
                la   a0, .Lnet_msg_send
                call kof_net_throw

            # endpoint.receive(a0=handle, a1=maxBytes) -> Byte[] (origem gravada)
            .globl kof_net_receiveFrom
            .type kof_net_receiveFrom, @function
            kof_net_receiveFrom:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s0, 112(sp)
                sd   s1, 104(sp)
                sd   s2, 96(sp)
                sd   s3, 88(sp)
                sd   s4, 80(sp)
                sd   s5, 72(sp)
                mv   s0, a0
                mv   s1, a1
                beqz s0, .Lnet_recvfrom_bad
                lw   t0, 0(s0)
                li   t1, 3
                bne  t0, t1, .Lnet_recvfrom_bad
                blez s1, .Lnet_recvfrom_empty
                mv   a0, s1
                li   a1, 1
                call kof_array_alloc
                mv   s2, a0
                li   t0, 16
                sw   t0, 0(sp)
                ld   a0, 8(s0)
                addi a1, s2, 24
                mv   a2, s1
                li   a3, 0
                addi a4, sp, 16
                mv   a5, sp
                li   a7, 207
                ecall
                blez a0, .Lnet_recvfrom_fail
                lbu  t0, 20(sp)
                sw   t0, 24(s0)
                lbu  t0, 21(sp)
                sw   t0, 28(s0)
                lbu  t0, 22(sp)
                sw   t0, 32(s0)
                lbu  t0, 23(sp)
                sw   t0, 36(s0)
                lbu  t1, 18(sp)
                lbu  t2, 19(sp)
                slli t1, t1, 8
                or   t0, t1, t2
                sw   t0, 40(s0)
                li   t0, 1
                sw   t0, 16(s0)
                mv   s3, a0
                bge  s3, s1, .Lnet_recvfrom_full
                sw   s3, 16(s2)
            .Lnet_recvfrom_full:
                mv   a0, s2
                ld   s5, 72(sp)
                ld   s4, 80(sp)
                ld   s3, 88(sp)
                ld   s2, 96(sp)
                ld   s1, 104(sp)
                ld   s0, 112(sp)
                ld   ra, 120(sp)
                addi sp, sp, 128
                ret
            .Lnet_recvfrom_empty:
                li   a0, 0
                li   a1, 1
                call kof_array_alloc
                ld   s5, 72(sp)
                ld   s4, 80(sp)
                ld   s3, 88(sp)
                ld   s2, 96(sp)
                ld   s1, 104(sp)
                ld   s0, 112(sp)
                ld   ra, 120(sp)
                addi sp, sp, 128
                ret
            .Lnet_recvfrom_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            .Lnet_recvfrom_fail:
                la   a0, .Lnet_msg_recv
                call kof_net_throw

            # endpoint.peer(a0=handle) -> String ("a.b.c.d:port" | "")
            .globl kof_net_peer
            .type kof_net_peer, @function
            kof_net_peer:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s0, a0
                beqz s0, .Lnet_peer_empty
                lw   t0, 0(s0)
                li   t1, 3
                bne  t0, t1, .Lnet_peer_bad
                lw   t0, 16(s0)
                beqz t0, .Lnet_peer_empty
                lw   a0, 24(s0)
                call kof_int_to_string
                mv   s1, a0
                li   s2, 1
            .Lnet_peer_octet:
                la   a0, .Lnet_dot
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
                li   t0, 1
                beq  s2, t0, .Lnet_peer_o1
                li   t0, 2
                beq  s2, t0, .Lnet_peer_o2
                lw   a0, 36(s0)
                j    .Lnet_peer_oi
            .Lnet_peer_o1:
                lw   a0, 28(s0)
                j    .Lnet_peer_oi
            .Lnet_peer_o2:
                lw   a0, 32(s0)
            .Lnet_peer_oi:
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
                addi s2, s2, 1
                li   t0, 3
                ble  s2, t0, .Lnet_peer_octet
                la   a0, .Lnet_colon
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
                lw   a0, 40(s0)
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_peer_empty:
                la   a0, .Lnet_empty
                li   a1, 0
                call kof_string_from_literal
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_peer_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            """;
}

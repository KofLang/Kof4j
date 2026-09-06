package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): helpers HTTP riscv64 (cstrlen/append/parse_url).
 * Extraído verbatim de NativeBackend.emitRiscvHttp (parte A) — asm
 * byte-idêntico (prova: diff do .s nos 3 targets).
 */
final class NativeRiscvHttpSupport {

    private NativeRiscvHttpSupport() {}

    static void emit(StringBuilder sb) {
        sb.append("""

            # ---- HTTP riscv64 (NATIVE002-stdlib) ----
            .section .text
            # cstrlen: a0=cstr -> a0=len
            .globl kof_http_cstrlen
            kof_http_cstrlen:
                mv   t1, a0
                li   a0, 0
            .Lhcl0:
                lbu  t0, 0(t1)
                beqz t0, .Lhcl1
                addi a0, a0, 1
                addi t1, t1, 1
                j    .Lhcl0
            .Lhcl1:
                ret
            # append cstr: a0=cursor, a1=cstr -> a0=novo cursor
            .globl kof_http_append_cstr
            kof_http_append_cstr:
            .Lhac0:
                lbu  t0, 0(a1)
                beqz t0, .Lhac1
                sb   t0, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                j    .Lhac0
            .Lhac1:
                ret
            # append n bytes: a0=cursor, a1=src, a2=len -> a0=cursor
            .globl kof_http_append_n
            kof_http_append_n:
            .Lhan0:
                beqz a2, .Lhan1
                lbu  t0, 0(a1)
                sb   t0, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 1
                addi a2, a2, -1
                j    .Lhan0
            .Lhan1:
                ret
            # append decimal: a0=cursor, a1=int64(>=0) -> a0=cursor
            .globl kof_http_append_dec
            kof_http_append_dec:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s0, a0
                mv   s1, a1
                addi s2, sp, 0
                mv   s3, s2
            .Lhad0:
                li   t1, 10
                div  t0, s1, t1
                rem  a1, s1, t1
                li   t1, 48
                add  a1, a1, t1
                sb   a1, 0(s2)
                addi s2, s2, 1
                mv   s1, t0
                bnez s1, .Lhad0
            .Lhad1:
                addi s2, s2, -1
                bltu s2, s3, .Lhad_end
                lbu  t0, 0(s2)
                sb   t0, 0(s0)
                addi s0, s0, 1
                j    .Lhad1
            .Lhad_end:
                mv   a0, s0
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            # parse URL. a0=KofString (len@16, chars@24)
            # saida: .Lhttp_hostbuf (cstr), .Lhttp_pathbuf (cstr),
            #        .Lhttp_portbin (2B BE), .Lhttp_ipbin (4B)
            .globl kof_http_parse_url
            kof_http_parse_url:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                addi s1, a0, 24
                lbu  a0, 0(s1)
                li   t0, 104
                bne  a0, t0, .Lpu_bad
                lbu  a0, 1(s1)
                li   t0, 116
                bne  a0, t0, .Lpu_bad
                lbu  a0, 2(s1)
                bne  a0, t0, .Lpu_bad
                lbu  a0, 3(s1)
                li   t0, 112
                bne  a0, t0, .Lpu_bad
                lbu  a0, 4(s1)
                li   t0, 115
                beq  a0, t0, .Lpu_https
                li   t0, 58
                bne  a0, t0, .Lpu_bad
                lbu  a0, 5(s1)
                li   t0, 47
                bne  a0, t0, .Lpu_bad
                lbu  a0, 6(s1)
                bne  a0, t0, .Lpu_bad
                addi s1, s1, 7
                la   s2, .Lhttp_hostbuf
            .Lpu_h0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_h1
                li   t0, 58
                beq  a0, t0, .Lpu_h1
                li   t0, 47
                beq  a0, t0, .Lpu_h1
                sb   a0, 0(s2)
                addi s2, s2, 1
                addi s1, s1, 1
                j    .Lpu_h0
            .Lpu_h1:
                li   t0, 0
                sb   t0, 0(s2)
                li   t0, 80
                lbu  a0, 0(s1)
                li   t1, 58
                bne  a0, t1, .Lpu_p1
                addi s1, s1, 1
                li   t0, 0
            .Lpu_p0:
                lbu  a0, 0(s1)
                li   t1, 47
                beq  a0, t1, .Lpu_p1
                beqz a0, .Lpu_p1
                li   t1, 48
                sub  a0, a0, t1
                li   t1, 9
                bltu t1, a0, .Lpu_p1
                li   t1, 10
                mul  t0, t0, t1
                add  t0, t0, a0
                addi s1, s1, 1
                j    .Lpu_p0
            .Lpu_p1:
                la   t1, .Lhttp_port_host
                sd   t0, 0(t1)
                andi t1, t0, 255
                slli t1, t1, 8
                srli t0, t0, 8
                or   t0, t0, t1
                la   t1, .Lhttp_portbin
                sh   t0, 0(t1)
                la   s2, .Lhttp_pathbuf
                lbu  a0, 0(s1)
                li   t0, 47
                beq  a0, t0, .Lpu_pa0
                sb   t0, 0(s2)
                li   t1, 0
                sb   t1, 1(s2)
                j    .Lpu_ip
            .Lpu_pa0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_pa1
                sb   a0, 0(s2)
                addi s2, s2, 1
                addi s1, s1, 1
                j    .Lpu_pa0
            .Lpu_pa1:
                li   t0, 0
                sb   t0, 0(s2)
            .Lpu_ip:
                la   s1, .Lhttp_hostbuf
                lbu  a0, 0(s1)
                li   t0, 48
                bltu a0, t0, .Lpu_fall
                li   t0, 57
                bltu t0, a0, .Lpu_fall
                la   s2, .Lhttp_ipbin
                li   t1, 0
                li   t2, 0
            .Lpu_ip0:
                lbu  a0, 0(s1)
                beqz a0, .Lpu_ip3
                li   t3, 46
                beq  a0, t3, .Lpu_ip1
                li   t3, 48
                sub  a0, a0, t3
                li   t3, 9
                bltu t3, a0, .Lpu_fall
                li   t3, 10
                mul  t2, t2, t3
                add  t2, t2, a0
                addi s1, s1, 1
                j    .Lpu_ip0
            .Lpu_ip1:
                add  t3, s2, t1
                sb   t2, 0(t3)
                li   t2, 0
                addi t1, t1, 1
                addi s1, s1, 1
                j    .Lpu_ip0
            .Lpu_ip3:
                add  t3, s2, t1
                sb   t2, 0(t3)
                j    .Lpu_done
            .Lpu_fall:
                la   s2, .Lhttp_ipbin
                li   t0, 127
                sb   t0, 0(s2)
                li   t0, 0
                sb   t0, 1(s2)
                sb   t0, 2(s2)
                li   t0, 1
                sb   t0, 3(s2)
            .Lpu_done:
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lpu_https:
                la   a0, .Lhttps_err
                call kof_http_cstrlen
                mv   a1, a0
                la   a0, .Lhttps_err
                call kof_string_from_literal
                call kof_throw_string
            .Lpu_bad:
                la   s0, .Lhttp_fallback
                la   s1, .Lhttp_hostbuf
                li   s2, 9
            .Lpu_bc:
                beqz s2, .Lpu_bc1
                lbu  t0, 0(s0)
                sb   t0, 0(s1)
                addi s0, s0, 1
                addi s1, s1, 1
                addi s2, s2, -1
                j    .Lpu_bc
            .Lpu_bc1:
                li   t0, 0
                sb   t0, 0(s1)
                la   s1, .Lhttp_pathbuf
                li   t0, 47
                sb   t0, 0(s1)
                sb   t0, 1(s1)
                li   t0, 20480
                la   t1, .Lhttp_portbin
                sh   t0, 0(t1)
                li   t0, 80
                la   t1, .Lhttp_port_host
                sd   t0, 0(t1)
                j    .Lpu_ip

            # ── request core ────────────────────────────────────────────
            # a0=url, a1=method cstr, a2=body cstr|0, a4=headers cstr|0
            # retorna a0=KofString body (status em .Lhttp_last_status)
            """);
    }
}

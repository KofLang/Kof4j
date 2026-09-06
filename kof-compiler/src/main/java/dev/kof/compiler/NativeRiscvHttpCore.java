package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): request core HTTP riscv64 + verbos (get/post/put/
 * patch/delete/options/status) e strings de wire-format. Extraído verbatim
 * de NativeBackend.emitRiscvHttp (parte B) — asm byte-idêntico.
 */
final class NativeRiscvHttpCore {

    private NativeRiscvHttpCore() {}

    static void emit(StringBuilder sb) {
        sb.append("""

            .globl kof_http_core
            kof_http_core:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s4, a4
                mv   a0, s0
                call kof_http_parse_url
                li   a0, 2
                li   a1, 1
                li   a2, 0
                li   a7, 198
                ecall
                bltz a0, .Lhr_fail
                mv   s5, a0
                la   t0, .Lhttp_sock
                li   t1, 2
                sw   t1, 0(t0)
                la   t1, .Lhttp_portbin
                lh   t1, 0(t1)
                sh   t1, 2(t0)
                la   t1, .Lhttp_ipbin
                lw   t1, 0(t1)
                sw   t1, 4(t0)
                li   t1, 0
                sd   t1, 8(t0)
                la   a1, .Lhttp_sock
                mv   a0, s5
                li   a2, 16
                li   a7, 203
                ecall
                bltz a0, .Lhr_fail_cl
                la   t0, .Lhttp_reqbuf
                mv   a0, t0
                mv   a1, s1
                call kof_http_append_cstr
                li   t0, 32
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   a1, .Lhttp_pathbuf
                call kof_http_append_cstr
                la   a1, .Lhttp_str_ver
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_str_host
                call kof_http_append_cstr
                la   a1, .Lhttp_hostbuf
                call kof_http_append_cstr
                la   t0, .Lhttp_port_host
                ld   t0, 0(t0)
                li   t1, 80
                beq  t0, t1, .Lhr_nohostport
                li   t0, 58
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   t0, .Lhttp_port_host
                ld   a1, 0(t0)
                call kof_http_append_dec
            .Lhr_nohostport:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s4, .Lhr_body_hdrs
                lw   t0, 16(s4)
                addi a1, s4, 24
                li   t1, 0
            .Lhr_hl:
                bltu t0, t1, .Lhr_hl_done
                lbu  t2, 0(a1)
                li   t3, 10
                beq  t2, t3, .Lhr_hc
                sb   t2, 0(a0)
                addi a0, a0, 1
                j    .Lhr_hn
            .Lhr_hc:
                li   t2, 13
                sb   t2, 0(a0)
                addi a0, a0, 1
                sb   t3, 0(a0)
                addi a0, a0, 1
            .Lhr_hn:
                addi t1, t1, 1
                addi a1, a1, 1
                j    .Lhr_hl
            .Lhr_hl_done:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_body_hdrs:
                beqz s2, .Lhr_closing
                la   a1, .Lhttp_str_clen
                call kof_http_append_cstr
                lw   t0, 16(s2)
                mv   a1, t0
                call kof_http_append_dec
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_closing:
                la   a1, .Lhttp_str_conn
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s2, .Lhr_send
                addi a1, s2, 24
                lw   a2, 16(s2)
                call kof_http_append_n
            .Lhr_send:
                la   t0, .Lhttp_reqbuf
                sub  a2, a0, t0
                mv   a1, t0
                mv   a0, s5
                li   a7, 64
                ecall
                li   s3, 0
            .Lhr_rd:
                la   t0, .Lhttp_respbuf
                add  a1, t0, s3
                li   t0, 262144
                sub  a2, t0, s3
                mv   a0, s5
                li   a7, 63
                ecall
                bltz a0, .Lhr_rd_done
                beqz a0, .Lhr_rd_done
                add  s3, s3, a0
                li   t0, 262144
                bltu s3, t0, .Lhr_rd
            .Lhr_rd_done:
                la   s1, .Lhttp_respbuf
                li   t0, 0
            .Lhr_st_space:
                lbu  a0, 0(s1)
                li   t1, 32
                beq  a0, t1, .Lhr_st_sp_hit
                addi s1, s1, 1
                j    .Lhr_st_space
            .Lhr_st_sp_hit:
                addi s1, s1, 1
            .Lhr_st_loop:
                lbu  a0, 0(s1)
                li   t1, 48
                sub  a0, a0, t1
                li   t2, 9
                bltu t2, a0, .Lhr_st_ok
                li   t1, 10
                mul  t0, t0, t1
                add  t0, t0, a0
                addi s1, s1, 1
                j    .Lhr_st_loop
            .Lhr_st_ok:
                la   t1, .Lhttp_last_status
                sd   t0, 0(t1)
                la   s1, .Lhttp_respbuf
                mv   s6, s3
            .Lhr_bscan:
                li   t0, 4
                bltu s6, t0, .Lhr_bnone
                lbu  a0, 0(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 1(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                lbu  a0, 2(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 3(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                addi s1, s1, 4
                addi s6, s6, -4
                mv   a0, s1
                mv   a1, s6
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                li   a7, 57
                ecall
                mv   a0, s0
                j    .Lhr_out
            .Lhr_bn:
                addi s1, s1, 1
                addi s6, s6, -1
                j    .Lhr_bscan
            .Lhr_bnone:
                la   a0, .Lhttp_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                li   a7, 57
                ecall
                mv   a0, s0
            .Lhr_fail_cl:
                mv   a0, s5
                li   a7, 57
                ecall
            .Lhr_fail:
                la   a0, .Lhttp_err_conn
                call kof_http_cstrlen
                mv   a1, a0
                la   a0, .Lhttp_err_conn
                call kof_string_from_literal
                call kof_throw_string
            .Lhr_out:
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # wrappers: a0=url [a1=body|headers] -> a0=KofString (status em .Lhttp_last_status)
            .globl kof_http_get
            kof_http_get:
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_get_headers
            kof_http_get_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_get
                li   a2, 0
                j    kof_http_core
            .globl kof_http_post
            kof_http_post:
                mv   a2, a1
                la   a1, .Lhttp_m_post
                li   a4, 0
                j    kof_http_core
            .globl kof_http_post_headers
            kof_http_post_headers:
                mv   a4, a2
                mv   a2, a1
                la   a1, .Lhttp_m_post
                j    kof_http_core
            .globl kof_http_delete
            kof_http_delete:
                la   a1, .Lhttp_m_delete
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_put
            kof_http_put:
                mv   a2, a1
                la   a1, .Lhttp_m_put
                li   a4, 0
                j    kof_http_core
            .globl kof_http_patch
            kof_http_patch:
                mv   a2, a1
                la   a1, .Lhttp_m_patch
                li   a4, 0
                j    kof_http_core
            .globl kof_http_status
            kof_http_status:
                addi sp, sp, -16
                sd   ra, 8(sp)
                la   a1, .Lhttp_m_get
                li   a2, 0
                li   a4, 0
                call kof_http_core
                la   a0, .Lhttp_last_status
                ld   a0, 0(a0)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .globl kof_http_options
            kof_http_options:
                la   a1, .Lhttp_m_options
                li   a2, 0
                li   a4, 0
                j    kof_http_core
            .globl kof_http_options_headers
            kof_http_options_headers:
                mv   a4, a1
                la   a1, .Lhttp_m_options
                li   a2, 0
                j    kof_http_core
            # configurators (no-op nativo, preservam API; paridade futura)
            .globl kof_http_timeout_set
            kof_http_timeout_set:
                ret
            .globl kof_http_retry_set
            kof_http_retry_set:
                ret
            .globl kof_http_circuit_set
            kof_http_circuit_set:
                ret
            .section .data
            .Lhttp_hostbuf:   .space 256
            .Lhttp_pathbuf:   .space 1024
            .Lhttp_portbin:   .space 2
            .Lhttp_port_host: .quad 0
            .Lhttp_ipbin:     .space 4
            .Lhttp_reqbuf:    .space 16384
            .Lhttp_respbuf:   .space 262144
            .Lhttp_sock:      .space 16
            .Lhttp_last_status: .quad 0
            .Lhttps_err:   .asciz "kof.http: https nao suportado no Native (TLS pendente); use http://"
            .Lhttp_fallback: .asciz "127.0.0.1"
            .Lhttp_str_host: .asciz "Host: "
            .Lhttp_str_clen: .asciz "Content-Length: "
            .Lhttp_str_conn: .asciz "Connection: close"
            .Lhttp_str_ver:  .asciz " HTTP/1.1"
            .Lhttp_crlfb:    .byte 13, 10
            .Lhttp_err_conn: .asciz "kof.http: connect falhou"
            .Lhttp_empty: .space 1
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .section .text
            """);
    }
}

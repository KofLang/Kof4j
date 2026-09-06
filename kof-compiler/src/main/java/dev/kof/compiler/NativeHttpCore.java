package dev.kof.compiler;

/**
 * HTTP client x86-64 — request core (connect/build/read/status) + wrappers
 * por verbo (GET/POST/PUT/...), status e configurators no-op.
 * Extraído de NativeHttpRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeHttpRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeHttpCore {

    private NativeHttpCore() {}

    static String source() {
        return """

            # ── request core ─────────────────────────────────────────────
            # rdi=url, rsi=method cstr, rdx=body cstr|0, rcx=headers cstr|0
            # retorna rax=KofString body (status em .Lhttp_last_status)
            .globl kof_http_core
            .type kof_http_core, @function
            kof_http_core:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsi, .Lhttp_method_ptr(%rip)  # method
                movq %rdx, %r13            # body
                movq %rcx, %r14            # headers
                call kof_http_parse_url    # rdi ja' tem url
                jmp .Lhr_retry_start
            .Lhr_retry_start:
                # circuit aberto? (check só no início — paridade JVM)
                movq .Lhttp_circuit_open_until(%rip), %rax
                testq %rax, %rax
                jz .Lhr_attempt_setup
                call kof_time_now
                cmpq %rax, .Lhttp_circuit_open_until(%rip)
                jle .Lhr_circuit_expired
                # ainda aberto -> fail fast
                leaq .Lhttp_err_circuit(%rip), %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                leaq .Lhttp_err_circuit(%rip), %rdi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            .Lhr_circuit_expired:
                movq $0, .Lhttp_circuit_open_until(%rip)
            .Lhr_attempt_setup:
                movq .Lhttp_retries(%rip), %rax
                movq %rax, .Lhttp_retries_left(%rip)
            .Lhr_attempt:
                # socket
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                call kof_net_socket
                testq %rax, %rax
                js .Lhr_fail
                movq %rax, %r15            # fd
                # sockaddr_in: family=2, port BE, ip BE
                subq $16, %rsp
                movw $2, (%rsp)
                movw .Lhttp_portbin(%rip), %ax
                movw %ax, 2(%rsp)
                movl .Lhttp_ipbin(%rip), %eax
                movl %eax, 4(%rsp)
                movq $0, 8(%rsp)
                movl %r15d, %edi
                movq %rsp, %rsi
                movl $16, %edx
                movq $42, %rax             # connect
                syscall
                addq $16, %rsp
                testq %rax, %rax
                js .Lhr_fail_cl
                # SO_RCVTIMEO quando http.timeout(n) foi chamado
                movq .Lhttp_timeout(%rip), %rax
                testq %rax, %rax
                jz .Lhr_no_timeout
                subq $16, %rsp
                movq %rax, 0(%rsp)            # tv_sec
                movq $0, 8(%rsp)              # tv_usec
                movl %r15d, %edi              # fd
                movl $1, %esi                 # SOL_SOCKET
                movl $20, %edx                # SO_RCVTIMEO
                movq %rsp, %r10               # optval
                movl $16, %r8d                # optlen
                movl $54, %eax                # SYS_setsockopt
                syscall
                addq $16, %rsp
            .Lhr_no_timeout:
                # build: METHOD SP path SP HTTP/1.1 CRLF Host: host CRLF
                leaq .Lhttp_reqbuf(%rip), %rbx
                movq %rbx, %rdi
                movq .Lhttp_method_ptr(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                movb $' ', (%rdi)
                incq %rdi
                leaq .Lhttp_pathbuf(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                leaq .Lhttp_str_ver(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
                leaq .Lhttp_str_host(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                leaq .Lhttp_hostbuf(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                # porta no Host quando nao for 80
                movw .Lhttp_portbin(%rip), %ax
                xchgb %al, %ah
                movzwl %ax, %eax
                cmpl $80, %eax
                je .Lhr_nohostport
                movb $':', (%rdi)
                incq %rdi
                movw .Lhttp_portbin(%rip), %ax
                xchgb %al, %ah
                movzwl %ax, %eax
                movslq %eax, %rsi
                call kof_http_append_dec
                movq %rax, %rdi
            .Lhr_nohostport:
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
                # headers custom: 1 por linha (LF -> CRLF)
                testq %r14, %r14
                jz .Lhr_body_hdrs
                movq %r14, %rsi
                movl 16(%r14), %r10d       # len
                leaq 24(%r14), %rsi
                xorq %r9, %r9              # idx
            .Lhr_hl:
                cmpl %r9d, %r10d
                jle .Lhr_hl_done
                movzbl (%rsi,%r9), %eax
                cmpb $10, %al
                jne .Lhr_hc
                # LF -> CRLF
                movb $13, (%rdi)
                incq %rdi
                movb $10, (%rdi)
                incq %rdi
                jmp .Lhr_hn
            .Lhr_hc:
                movb %al, (%rdi)
                incq %rdi
            .Lhr_hn:
                incq %r9
                jmp .Lhr_hl
            .Lhr_hl_done:
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
            .Lhr_body_hdrs:
                # body: Content-Length + CRLF + CRLF + body
                testq %r13, %r13
                jz .Lhr_closing
                leaq .Lhttp_str_clen(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                movl 16(%r13), %eax
                movslq %eax, %rsi
                call kof_http_append_dec
                movq %rax, %rdi
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
            .Lhr_closing:
                leaq .Lhttp_str_conn(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
                leaq .Lhttp_crlfb(%rip), %rsi
                movl $2, %edx
                call kof_http_append_n
                movq %rax, %rdi
                # body
                testq %r13, %r13
                jz .Lhr_send
                leaq 24(%r13), %rsi
                movl 16(%r13), %edx
                call kof_http_append_n
                movq %rax, %rdi
            .Lhr_send:
                leaq .Lhttp_reqbuf(%rip), %rcx
                movq %rdi, %rdx
                subq %rcx, %rdx            # total
                # kof_net_write: rdi=fd, rsi=buf, rdx=len
                movl %r15d, %edi
                movq %rcx, %rsi
                call kof_net_write
                # read all
                xorq %r12, %r12            # total
            .Lhr_rd:
                movl %r15d, %edi
                leaq .Lhttp_respbuf(%rip), %rsi
                addq %r12, %rsi
                movq $262144, %rdx
                subq %r12, %rdx
                call kof_net_read
                cmpq $-11, %rax               # EAGAIN => timeout
                je .Lhr_timeout
                testq %rax, %rax
                jle .Lhr_rd_done
                addq %rax, %r12
                cmpq $262144, %r12
                jl .Lhr_rd
            .Lhr_rd_done:
                # parse status: "HTTP/1.x NNN ..."
                leaq .Lhttp_respbuf(%rip), %rsi
                xorl %eax, %eax
            .Lhr_st_space:
                cmpb $' ', (%rsi)
                je .Lhr_st_d
                incq %rsi
                jmp .Lhr_st_space
            .Lhr_st_d:
                incq %rsi
            .Lhr_st_loop:
                movzbl (%rsi), %ecx
                subb $'0', %cl
                cmpb $9, %cl
                ja .Lhr_st_ok
                imull $10, %eax
                addl %ecx, %eax
                incq %rsi
                jmp .Lhr_st_loop
            .Lhr_st_ok:
                movq %rax, .Lhttp_last_status(%rip)
                cmpq $499, %rax
                jg .Lhr_5xx
                # sucesso: zera falhas do circuit breaker
                movq $0, .Lhttp_circuit_failures(%rip)
                movq $0, .Lhttp_circuit_open_until(%rip)
                # body = depois de \r\n\r\n
                leaq .Lhttp_respbuf(%rip), %rsi
                movq %r12, %r9             # total
            .Lhr_bscan:
                cmpq $4, %r9
                jl .Lhr_bnone
                cmpb $13, (%rsi)
                jne .Lhr_bn
                cmpb $10, 1(%rsi)
                jne .Lhr_bn
                cmpb $13, 2(%rsi)
                jne .Lhr_bn
                cmpb $10, 3(%rsi)
                jne .Lhr_bn
                addq $4, %rsi
                subq $4, %r9
                # r9 = body len; rsi = body start
                # monta KofString do body
                movq %rsi, %rdi
                movl %r9d, %esi
                call kof_string_from_literal
                # fecha fd
                pushq %rax
                movl %r15d, %edi
                call kof_net_close
                popq %rax
                jmp .Lhr_out
            .Lhr_bn:
                incq %rsi
                decq %r9
                jmp .Lhr_bscan
            .Lhr_bnone:
                # sem header terminator: body vazio
                movl $0, %edx
                leaq .Lhttp_empty(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                pushq %rax
                movl %r15d, %edi
                call kof_net_close
                popq %rax
                jmp .Lhr_out
            .Lhr_5xx:
                movl %r15d, %edi
                call kof_net_close
                leaq .Lhttp_err_failed(%rip), %rbx
                jmp .Lhr_record_retry
            .Lhr_fail_cl:
                movl %r15d, %edi
                call kof_net_close
                leaq .Lhttp_err_conn(%rip), %rbx
                jmp .Lhr_record_retry
            .Lhr_timeout:
                movl %r15d, %edi
                call kof_net_close
                leaq .Lhttp_err_timeout(%rip), %rbx
                jmp .Lhr_record_retry
            .Lhr_fail:
                leaq .Lhttp_err_conn(%rip), %rbx
            .Lhr_record_retry:
                # rbx = cstr do erro. Registra falha no circuit; retry ou throw.
                movq .Lhttp_circuit_trips(%rip), %rax
                testq %rax, %rax
                jz .Lhr_rr_nocirc
                movq .Lhttp_circuit_failures(%rip), %rcx
                incq %rcx
                movq %rcx, .Lhttp_circuit_failures(%rip)
                cmpq %rax, %rcx
                jl .Lhr_rr_nocirc
                call kof_time_now
                addq $30000, %rax
                movq %rax, .Lhttp_circuit_open_until(%rip)
            .Lhr_rr_nocirc:
                movq .Lhttp_retries_left(%rip), %rax
                testq %rax, %rax
                jz .Lhr_rr_throw
                decq %rax
                movq %rax, .Lhttp_retries_left(%rip)
                jmp .Lhr_attempt
            .Lhr_rr_throw:
                movq %rbx, %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                movq %rbx, %rdi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            .Lhr_out:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .data
            .Lhttp_err_conn: .asciz "kof.http: connect falhou"
            .Lhttp_err_timeout: .asciz "kof.http: timeout"
            .Lhttp_err_circuit: .asciz "kof.http circuit open"
            .Lhttp_err_failed: .asciz "request failed"
            .Lhttp_timeout: .quad 0
            .Lhttp_retries: .quad 0
            .Lhttp_retries_left: .quad 0
            .Lhttp_circuit_trips: .quad 0
            .Lhttp_circuit_failures: .quad 0
            .Lhttp_circuit_open_until: .quad 0
            .Lhttp_method_ptr: .quad 0
            .Lhttp_empty: .space 1
            .section .text

            # wrappers ----------------------------------------------------
            .globl kof_http_get
            .type kof_http_get, @function
            kof_http_get:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_get_headers
            .type kof_http_get_headers, @function
            kof_http_get_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_delete
            .type kof_http_delete, @function
            kof_http_delete:
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_delete_headers
            .type kof_http_delete_headers, @function
            kof_http_delete_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_delete(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_options
            .type kof_http_options, @function
            kof_http_options:
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_options_headers
            .type kof_http_options_headers, @function
            kof_http_options_headers:
                movq %rsi, %rcx
                leaq .Lhttp_m_options(%rip), %rsi
                xorq %rdx, %rdx
                jmp kof_http_core
            .globl kof_http_post
            .type kof_http_post, @function
            kof_http_post:
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers
            .type kof_http_post_headers, @function
            kof_http_post_headers:
                movq %rdx, %rcx            # headers
                movq %rsi, %rdx            # body
                leaq .Lhttp_m_post(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_put
            .type kof_http_put, @function
            kof_http_put:
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_post_headers_alias_put
            .globl kof_http_put_headers
            .type kof_http_put_headers, @function
            kof_http_put_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_put(%rip), %rsi
                jmp kof_http_core
            .globl kof_http_patch
            .type kof_http_patch, @function
            kof_http_patch:
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                xorq %rcx, %rcx
                jmp kof_http_core
            .globl kof_http_patch_headers
            .type kof_http_patch_headers, @function
            kof_http_patch_headers:
                movq %rdx, %rcx
                movq %rsi, %rdx
                leaq .Lhttp_m_patch(%rip), %rsi
                jmp kof_http_core
            # kof_http_status(url) -> int
            .globl kof_http_status
            .type kof_http_status, @function
            kof_http_status:
                leaq .Lhttp_m_get(%rip), %rsi
                xorq %rdx, %rdx
                xorq %rcx, %rcx
                call kof_http_core
                movq .Lhttp_last_status(%rip), %rax
                ret
            # configurators (paridade com o JVM; timeout/retry/circuit reais)
            .globl kof_http_timeout_set
            .type kof_http_timeout_set, @function
            kof_http_timeout_set:
                movslq %edi, %rax
                movq %rax, .Lhttp_timeout(%rip)
                ret
            .globl kof_http_retry_set
            .type kof_http_retry_set, @function
            kof_http_retry_set:
                movslq %edi, %rax
                testq %rax, %rax
                js .Lhrs_done            # negativo -> 0 (clamp)
                movq %rax, .Lhttp_retries(%rip)
            .Lhrs_done:
                ret
            .globl kof_http_circuit_set
            .type kof_http_circuit_set, @function
            kof_http_circuit_set:
                movslq %edi, %rax
                testq %rax, %rax
                jle .Lhcs_reset           # <= 0 -> desliga+reset
                movq %rax, .Lhttp_circuit_trips(%rip)
                ret
            .Lhcs_reset:
                movq $0, .Lhttp_circuit_trips(%rip)
                movq $0, .Lhttp_circuit_failures(%rip)
                movq $0, .Lhttp_circuit_open_until(%rip)
                ret

            .section .data
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .section .text
            """;
    }
}

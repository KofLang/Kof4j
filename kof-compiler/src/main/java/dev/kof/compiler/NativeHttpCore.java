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
                movq %rsi, %r12            # method
                movq %rdx, %r13            # body
                movq %rcx, %r14            # headers
                call kof_http_parse_url    # rdi ja' tem url
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
                # build: METHOD SP path SP HTTP/1.1 CRLF Host: host CRLF
                leaq .Lhttp_reqbuf(%rip), %rbx
                movq %rbx, %rdi
                movq %r12, %rsi
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
            .Lhr_fail_cl:
                movl %r15d, %edi
                call kof_net_close
            .Lhr_fail:
                leaq .Lhttp_err_conn(%rip), %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                leaq .Lhttp_err_conn(%rip), %rdi
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
            # configurators (no-op nativo, preservam API; paridade futura)
            .globl kof_http_timeout_set
            .type kof_http_timeout_set, @function
            kof_http_timeout_set:
                ret
            .globl kof_http_retry_set
            .type kof_http_retry_set, @function
            kof_http_retry_set:
                ret
            .globl kof_http_circuit_set
            .type kof_http_circuit_set, @function
            kof_http_circuit_set:
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

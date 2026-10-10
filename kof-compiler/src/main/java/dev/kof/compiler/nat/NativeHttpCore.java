package dev.kof.compiler.nat;

/**
 * HTTP client x86-64 — request core (connect/build/read/status) + wrappers
 * por verbo (GET/POST/PUT/...), status e configurators no-op.
 * Extraído de NativeHttpRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeHttpRuntime preserva o assembly injetado byte-a-byte.
 */
public final class NativeHttpCore {

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
                movq %rsi, .Lhttp_methodp(%rip)  # §259: r12 vira contador de read —
                movq %rsi, %r12            # method   o retry precisa restaurar por tentativa
                movq %rdx, %r13            # body
                movq %rcx, %r14            # headers
                movq %rdi, .Lhttp_urlptr(%rip)   # §259: url p/ msg "HTTP nnn from"
                pushq %rbp                 # §259: tentativas restantes (rbp = N)
                movq .Lhttp_retry_n(%rip), %rbp
                # §259 fatia 3: circuito aberto -> fail-fast SEM conectar (JVM:
                # kof_http_circuit_open() antes do loop de tentativas)
                call kof_http_circuit_open
                testq %rax, %rax
                jz .Lhr_parse
                leaq .Lhttp_errbuf(%rip), %rdi
                leaq .Lhttp_str_copen(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                movq .Lhttp_urlptr(%rip), %rsi
                movl 16(%rsi), %edx
                addq $24, %rsi
                call kof_http_append_n
                movb $0, (%rax)
                leaq .Lhttp_errbuf(%rip), %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                leaq .Lhttp_errbuf(%rip), %rdi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
                jmp .Lhr_out
            .Lhr_parse:
                call kof_http_parse_url    # rdi ja' tem url (parse 1x: buffers globais)
            .Lhr_attempt:
                movq .Lhttp_methodp(%rip), %r12   # restaura method (clobberado p/ total)
                # socket
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                call kof_net_socket
                testq %rax, %rax
                js .Lhr_fail
                movq %rax, %r15            # fd
                # §259: connect nao-bloqueante p/ aplicar deadline (medido:
                # loopback responde na hora; host morto fica em EINPROGRESS)
                movl %r15d, %edi
                movl $72, %eax             # fcntl
                movl $4, %esi              # F_SETFL
                movl $2048, %edx           # O_NONBLOCK
                syscall
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
                jge .Lhr_conn_done
                negq %rax
                cmpq $115, %rax            # EINPROGRESS (medido, probe tmo2)
                jne .Lhr_fail_cl
                # poll(fd, POLLOUT, timeout_s*1000; 0 = infinito -> -1)
                movl %r15d, .Lhttp_pf(%rip)
                movw $4, .Lhttp_pf+4(%rip) # struct pollfd.events @+4 (medido: tmo3)
                movq .Lhttp_timeout_s(%rip), %rcx
                testq %rcx, %rcx
                jz .Lhr_poll_inf
                 imulq $1000, %rcx
                 cmpq $0x7fffffff, %rcx     # clamp: ms > INT_MAX viraria negativo
                 jbe .Lhr_poll_go           # no as = espera infinita (pior que nao ter)
                 movq $0x7fffffff, %rcx     # ~24 dias de deadline
                 jmp .Lhr_poll_go
             .Lhr_poll_inf:
                 movq $-1, %rcx
             .Lhr_poll_go:
                leaq .Lhttp_pf(%rip), %rdi
                movl $1, %esi
                movl %ecx, %edx
                movl $7, %eax              # poll (medido: bloqueou o deadline exato)
                syscall
                testq %rax, %rax
                js .Lhr_fail_cl
                jz .Lhr_tmo_cl
                # getsockopt(fd, SOL_SOCKET=1, SO_ERROR=4, &err, &len)
                movl $0, .Lhttp_serr(%rip)
                movl $4, .Lhttp_serr+4(%rip)
                movl %r15d, %edi
                movl $1, %esi
                movl $4, %edx
                leaq .Lhttp_serr(%rip), %r10
                leaq .Lhttp_serr+4(%rip), %r8
                movl $55, %eax
                syscall
                cmpl $0, .Lhttp_serr(%rip)
                jne .Lhr_fail_cl           # ECONNREFUSED etc -> connect falhou (rapido)
            .Lhr_conn_done:
                # restaura blocking (F_SETFL=0 ignora access mode) e poe os
                # timeouts de leitura/escrita (medido: read volta -EAGAIN so'
                # depois de tv_sec; sem restaurar, EAGAIN seria instantaneo)
                movl %r15d, %edi
                movl $72, %eax
                movl $4, %esi
                xorl %edx, %edx
                syscall
                movq .Lhttp_timeout_s(%rip), %rcx
                testq %rcx, %rcx
                jz .Lhr_build
                movq %rcx, .Lhttp_tv(%rip)
                movq $0, .Lhttp_tv+8(%rip)
                movl %r15d, %edi
                movl $1, %esi
                movl $20, %edx             # SO_RCVTIMEO
                leaq .Lhttp_tv(%rip), %r10
                movl $16, %r8d
                movl $54, %eax             # setsockopt
                syscall
                movl %r15d, %edi
                movl $1, %esi
                movl $21, %edx             # SO_SNDTIMEO
                leaq .Lhttp_tv(%rip), %r10
                movl $16, %r8d
                movl $54, %eax
                syscall
            .Lhr_build:
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
                jg .Lhr_rd_more
                cmpq $-11, %rax            # EAGAIN: SO_RCVTIMEO expirou (medido)
                je .Lhr_tmo_cl
                jmp .Lhr_rd_done           # 0 = EOF (Connection: close), outro erro idem
            .Lhr_rd_more:
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
                # §259: status >= 500 = falha retryavel (paridade JVM "HTTP n from url")
                cmpl $500, %eax
                jl .Lhr_body
                movl %eax, .Lhttp_serr(%rip)       # stash (append_* clobbem eax)
                leaq .Lhttp_errbuf(%rip), %rdi
                leaq .Lhttp_str_5xx(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                movslq .Lhttp_serr(%rip), %rsi
                call kof_http_append_dec
                movq %rax, %rdi
                leaq .Lhttp_str_from(%rip), %rsi
                call kof_http_append_cstr
                movq %rax, %rdi
                movq .Lhttp_urlptr(%rip), %rsi
                movl 16(%rsi), %edx
                addq $24, %rsi
                call kof_http_append_n
                movb $0, (%rax)
                leaq .Lhttp_errbuf(%rip), %rax
                jmp .Lhr_rtry_cl
            .Lhr_rtry_cl:                  # 5xx: salva msg (close clobbera rax) e fecha fd
                movq %rax, .Lhttp_last_err(%rip)
                movl %r15d, %edi
                call kof_plat_close
                call kof_http_circuit_record_fail   # paridade JVM: 5xx tambem registra falha (Q4-me) (Q4-me)
                jmp .Lhr_rtry_chk
            .Lhr_body:
                call kof_http_circuit_record_success  # §259 fatia 3 (JVM: record_success p/ <500)
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
                call kof_plat_close
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
                call kof_plat_close
                popq %rax
                jmp .Lhr_out
            .Lhr_fail_cl:                  # erro com fd aberto -> fecha
                movl %r15d, %edi
                call kof_plat_close
                leaq .Lhttp_err_conn(%rip), %rax
                jmp .Lhr_rtry
            .Lhr_tmo_cl:
                movl %r15d, %edi
                call kof_plat_close
                leaq .Lhttp_err_tmo(%rip), %rax
                jmp .Lhr_rtry
            .Lhr_rtry:                     # §259: paridade JVM — excecao OU 5xx tentam de novo
                movq %rax, .Lhttp_last_err(%rip)
                call kof_http_circuit_record_fail   # §259 fatia 3 (JVM: record_failure por tentativa)
            .Lhr_rtry_chk:
                testq %rbp, %rbp
                jz .Lhr_throw_last
                decq %rbp
                jmp .Lhr_attempt
            .Lhr_throw_last:
                movq .Lhttp_last_err(%rip), %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                movq .Lhttp_last_err(%rip), %rdi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
                jmp .Lhr_out
            .Lhr_fail:                     # socket() sem fd: nao ha o que fechar
                leaq .Lhttp_err_conn(%rip), %rax
                jmp .Lhr_rtry
            .Lhr_out:
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .data
            .Lhttp_err_conn: .asciz "kof.http: connect failed"
            .Lhttp_err_tmo: .asciz "kof.http: timeout"
            .Lhttp_timeout_s: .quad 15
            .Lhttp_retry_n: .quad 0
            .Lhttp_last_err: .quad 0
            .Lhttp_urlptr: .quad 0
            .Lhttp_methodp: .quad 0
            .Lhttp_errbuf: .space 512
            .Lhttp_str_5xx: .asciz "HTTP "
            .Lhttp_str_from: .asciz " from "
            .Lhttp_empty2_unused: .quad 0
            .Lhttp_tv: .quad 0
            .Lhttp_serr: .quad 0
            .Lhttp_pf: .space 8
            .Lhttp_empty: .space 1
            .section .text
            """;
    }
}

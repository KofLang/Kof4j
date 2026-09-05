package dev.kof.compiler;

/**
 * HTTP client x86-64 — data section + primitivas de buffer (cstrlen/append_cstr/n/dec).
 * Extraído de NativeHttpRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeHttpRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeHttpPrimitives {

    private NativeHttpPrimitives() {}

    static String source() {
        return """
            .section .data
            .Lhttp_hostbuf:   .space 256
            .Lhttp_pathbuf:   .space 1024
            .Lhttp_portbin:   .space 2
            .Lhttp_ipbin:     .space 4
            .Lhttp_reqbuf:    .space 16384
            .Lhttp_respbuf:   .space 262144
            .Lhttp_last_status: .quad 0
            .Lhttps_err:   .asciz "kof.http: https nao suportado no Native (TLS pendente); use http://"
            .Lhttp_fallback: .asciz "127.0.0.1"
            .Lhttp_str_host: .asciz "Host: "
            .Lhttp_str_clen: .asciz "Content-Length: "
            .Lhttp_str_conn: .asciz "Connection: close"
            .Lhttp_str_ver:  .asciz " HTTP/1.1"
            .Lhttp_crlfb:    .byte 13, 10
            .section .text

            # cstrlen: rdi=cstr -> rax=len
            .globl kof_http_cstrlen
            .type kof_http_cstrlen, @function
            kof_http_cstrlen:
                xorq %rax, %rax
            .Lhc0:
                cmpb $0, (%rdi,%rax)
                je .Lhc1
                incq %rax
                jmp .Lhc0
            .Lhc1:
                ret

            # append cstr: rdi=cursor, rsi=cstr -> rax=novo cursor
            .globl kof_http_append_cstr
            .type kof_http_append_cstr, @function
            kof_http_append_cstr:
            .Lha0:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Lha1
                movb %al, (%rdi)
                incq %rdi
                incq %rsi
                jmp .Lha0
            .Lha1:
                movq %rdi, %rax
                ret

            # append n bytes: rdi=cursor, rsi=src, edx=len -> rax=cursor
            .globl kof_http_append_n
            .type kof_http_append_n, @function
            kof_http_append_n:
            .Lan0:
                testq %rdx, %rdx
                jz .Lan1
                movzbl (%rsi), %eax
                movb %al, (%rdi)
                incq %rdi
                incq %rsi
                decq %rdx
                jmp .Lan0
            .Lan1:
                movq %rdi, %rax
                ret

            # append decimal: rdi=cursor, rsi=int64(>=0) -> rax=cursor
            .globl kof_http_append_dec
            .type kof_http_append_dec, @function
            kof_http_append_dec:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rsi, %rax
                subq $24, %rsp
                leaq 16(%rsp), %r12
                xorq %rcx, %rcx
            .Lhd0:
                xorq %rdx, %rdx
                movq $10, %r9
                divq %r9
                addb $'0', %dl
                movb %dl, (%r12,%rcx)
                incq %rcx
                testq %rax, %rax
                jnz .Lhd0
            # escreve de tras p/ frente
            decq %rcx
            .Lhd1:
                movzbl (%r12,%rcx), %eax
                movb %al, (%rbx)
                incq %rbx
                subq $1, %rcx
                jns .Lhd1
                movq %rbx, %rax
                addq $24, %rsp
                popq %r12
                popq %rbx
                ret

            """;
    }
}

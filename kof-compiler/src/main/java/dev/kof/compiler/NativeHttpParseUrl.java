package dev.kof.compiler;

/**
 * HTTP client x86-64 — parse de URL (host/porta/path/IPv4) + erro https.
 * Extraído de NativeHttpRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeHttpRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeHttpParseUrl {

    private NativeHttpParseUrl() {}

    static String source() {
        return """
            # parse URL. rdi=KofString (len@16, chars@24)
            # saida: .Lhttp_hostbuf (cstr), .Lhttp_pathbuf (cstr),
            #        .Lhttp_portbin (2 bytes, ja BE), .Lhttp_ipbin (4 bytes IP)
            .globl kof_http_parse_url
            .type kof_http_parse_url, @function
            kof_http_parse_url:
                pushq %rbx
                pushq %r12
                movq %rdi, %r12
                leaq 24(%r12), %rbx          # chars
                # precisa comecar com http:// ; https -> throw
                cmpb $'h', 0(%rbx)
                jne .Lpu_bad
                cmpb $'t', 1(%rbx)
                jne .Lpu_bad
                cmpb $'t', 2(%rbx)
                jne .Lpu_bad
                cmpb $'p', 3(%rbx)
                jne .Lpu_bad
                cmpb $'s', 4(%rbx)
                je .Lpu_https
                cmpb $':', 4(%rbx)
                jne .Lpu_bad
                cmpb $'/', 5(%rbx)
                jne .Lpu_bad
                cmpb $'/', 6(%rbx)
                jne .Lpu_bad
                addq $7, %rbx
                # host
                leaq .Lhttp_hostbuf(%rip), %r12
            .Lpu_h0:
                movzbl (%rbx), %eax
                testb %al, %al
                jz .Lpu_h1
                cmpb $':', %al
                je .Lpu_h1
                cmpb $'/', %al
                je .Lpu_h1
                movb %al, (%r12)
                incq %r12
                incq %rbx
                jmp .Lpu_h0
            .Lpu_h1:
                movb $0, (%r12)
                # porta (default 80)
                movl $80, %eax
                cmpb $':', (%rbx)
                jne .Lpu_p1
                incq %rbx
                xorq %rax, %rax
            .Lpu_p0:
                movzbl (%rbx), %ecx
                cmpb $'/', %cl
                je .Lpu_p1
                testb %cl, %cl
                jz .Lpu_p1
                subb $'0', %cl
                cmpb $9, %cl
                ja .Lpu_p1
                imull $10, %eax
                addl %ecx, %eax
                incq %rbx
                jmp .Lpu_p0
            .Lpu_p1:
                # htons
                xchgb %al, %ah
                movw %ax, .Lhttp_portbin(%rip)
                # path (default "/")
                leaq .Lhttp_pathbuf(%rip), %r12
                cmpb $'/', (%rbx)
                je .Lpu_pa0
                movb $'/', (%r12)
                movb $0, 1(%r12)
                jmp .Lpu_ip
            .Lpu_pa0:
                movzbl (%rbx), %eax
                testb %al, %al
                jz .Lpu_pa1
                movb %al, (%r12)
                incq %r12
                incq %rbx
                jmp .Lpu_pa0
            .Lpu_pa1:
                movb $0, (%r12)
                jmp .Lpu_ip
            .Lpu_bad:
                # sem esquema: host = "127.0.0.1", port 80, path = "/"
                leaq .Lhttp_fallback(%rip), %rsi
                leaq .Lhttp_hostbuf(%rip), %r12
                movq $10, %rcx
            .Lpu_bc:
                testq %rcx, %rcx
                jz .Lpu_bc1
                movzbl (%rsi), %eax
                movb %al, (%r12)
                incq %rsi
                incq %r12
                decq %rcx
                jmp .Lpu_bc
            .Lpu_bc1:
                movb $0, (%r12)
                leaq .Lhttp_pathbuf(%rip), %r12
                movb $'/', (%r12)
                movb $0, 1(%r12)
                movw $20480, .Lhttp_portbin(%rip)   # htons(80)
            .Lpu_ip:
                # host buf -> ipbin (dotted quad). primeiro char nao numerico => fallback
                leaq .Lhttp_hostbuf(%rip), %rbx
                movzbl (%rbx), %eax
                cmpb $'0', %al
                jb .Lpu_fall
                cmpb $'9', %al
                ja .Lpu_fall
                leaq .Lhttp_ipbin(%rip), %r12
                xorq %r8, %r8              # idx octet
                xorq %r9, %r9              # acumulador
            .Lpu_ip0:
                movzbl (%rbx), %eax
                testb %al, %al
                jz .Lpu_ip3
                cmpb $'.', %al
                je .Lpu_ip1
                subb $'0', %al
                cmpb $9, %al
                ja .Lpu_fall
                imull $10, %r9d
                addl %eax, %r9d
                incq %rbx
                jmp .Lpu_ip0
            .Lpu_ip1:
                movb %r9b, (%r12,%r8)
                xorq %r9, %r9
                incq %r8
                incq %rbx
                jmp .Lpu_ip0
            .Lpu_ip3:
                movb %r9b, (%r12,%r8)
                jmp .Lpu_done
            .Lpu_fall:
                leaq .Lhttp_ipbin(%rip), %r12
                movb $127, (%r12)
                movb $0, 1(%r12)
                movb $0, 2(%r12)
                movb $1, 3(%r12)
            .Lpu_done:
                popq %r12
                popq %rbx
                ret
            .Lpu_https:
                leaq .Lhttps_err(%rip), %rdi
                call kof_http_cstrlen
                movl %eax, %esi
                leaq .Lhttps_err(%rip), %rdi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            """;
    }
}

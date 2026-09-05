package dev.kof.compiler;

/**
 * kof.web server x86-64 (WEB002) — helpers de resposta: crlf, append_cstr,
 * 200 route-match, 404 Not Found + literais finais.
 * Extraído de NativeWebRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeWebRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeWebResponses {

    private NativeWebResponses() {}

    static String source() {
        return """
            # ------------------------------------------------------------------
            # helpers send (rdi = client_fd)
            # ------------------------------------------------------------------
            # grava CRLF e retorna cursor+2 (além de rdi)
            _kof_web_crlf:
                movb $13, (%rdi)
                incq %rdi
                movb $10, (%rdi)
                incq %rdi
                ret

            # copia cstr (rsi) para dst (rdi) byte a byte; retorna fim do dst
            kof_web_append_cstr:
            .Lwa:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Lwadone
                movb %al, (%rdi)
                incq %rdi
                incq %rsi
                jmp .Lwa
            .Lwadone:
                movq %rdi, %rax
                ret

            # 200 route-match: escreve resposta com "route-match"
            kof_web_send_match:
                pushq %rbx
                movq %rdi, %rbx
                leaq .Lweb_skb(%rip), %rdi
                leaq .Lweb_h1(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_ok(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_hct(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_hcc(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_hnl(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                # Content-Length: 11
                movb $'1', (%rdi)
                incq %rdi
                movb $'1', (%rdi)
                incq %rdi
                call _kof_web_crlf
                call _kof_web_crlf      # header-end LF+LF
                # body
                leaq .Lweb_body_ok(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                # write(f, skb, cursor - skb)
                leaq .Lweb_skb(%rip), %rsi
                movq %rdi, %rdx
                subq %rsi, %rdx
                movq %rbx, %rdi
                movl $1, %eax                    # SYS_write
                syscall
                popq %rbx
                ret

            # 404 Not Found (corpo curto)
            kof_web_send_404:
                pushq %rbx
                movq %rdi, %rbx
                leaq .Lweb_skb(%rip), %rdi
                leaq .Lweb_h1(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_nf(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_hcc(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_hnl(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                # Content-Length: 9
                movb $'9', (%rdi)
                incq %rdi
                call _kof_web_crlf
                call _kof_web_crlf
                # body "Not Found"
                leaq .Lweb_nfbody(%rip), %rsi
                call kof_web_append_cstr
                movq %rax, %rdi
                leaq .Lweb_skb(%rip), %rsi
                movq %rdi, %rdx
                subq %rsi, %rdx
                movq %rbx, %rdi
                movl $1, %eax
                syscall
                popq %rbx
                ret

            .section .data
            .Lweb_nfbody: .asciz "Not Found"
            .Lweb_body_hello_lit: .asciz "hello"
            .section .text
            """;
    }
}

package dev.kof.compiler;

/**
 * kof.web server x86-64 (WEB002) — dados/estruturas + primitivas:
 * strlen, i32str, app_new, body ctx, registro de rotas.
 * Extraído de NativeWebRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeWebRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeWebCore {

    private NativeWebCore() {}

    static String source() {
        return """
            # ================= Web Runtime (WEB002) =================
            # Layout das entradas de rotas:
            #   .Lweb_routes[i] = { method_ptr(8), path_ptr(8), handler_ptr(8), pad(8) }
            # method/path vivem no heap (kof strings); o handler é o
            # objeto Lambda* Kof (ainda não invocado aqui).

            .section .data
            .Lweb_nroutes:     .quad 0
            .Lweb_routes:      .space 16384   # 512 rotas de 32B
            # resposta fixos
            .Lweb_h1:  .asciz "HTTP/1.1 "
            .Lweb_ok:  .asciz "200 OK\\r\\n"
            .Lweb_nf:  .asciz "404 Not Found\\r\\n"
            .Lweb_hct: .asciz "Content-Type: text/plain\\r\\n"
            .Lweb_hcc: .asciz "Connection: close\\r\\n"
            .Lweb_hnl: .asciz "Content-Length: "
            .Lweb_body_ok: .asciz "route-match"
            .Lweb_crlfx2: .asciz "\\r\\n\\r\\n"

            .section .bss
            .Lweb_reqbuf:     .space 16384
            .Lweb_skb:        .space 8192
            .Lweb_last_body:  .space 8192     # body extraído da última request
            .Lweb_last_blen:  .quad 0
            .Lweb_last_path:  .space 512

            .section .text

            # ------------------------------------------------------------------
            # strlen c-string: rdi → rax
            # ------------------------------------------------------------------
            kof_web_strlen:
                xorq %rax, %rax
            .Lwsloop:
                cmpb $0, (%rdi,%rax)
                je .Lwsdone
                incq %rax
                jmp .Lwsloop
            .Lwsdone:
                ret

            # ------------------------------------------------------------------
            # int→cstr: rdi=dst, rsi=val → rax=novo_cursor
            # (mínimo 4 dígitos significativos, sem sinal)
            # ------------------------------------------------------------------
            kof_web_i32str:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                # como int32 non-negative
                movl %r12d, %eax
                # 10k divisor
                movl $10000, %ecx
                # gera dígitos por divisão
                xorl %edx, %edx
                divl %ecx                # eax=high, edx=low
                movl %eax, %r13d         # saída hi
                # primeiro (hi)
                movl %r13d, %eax
                addl $'0', %eax
                movb %al, (%rbx)
                incq %rbx
                # segundo (lo)
                movl %edx, %eax
                addl $'0', %eax
                movb %al, (%rbx)
                incq %rbx
                # terminar
                movq %rbx, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # app_new sentinel — único "objeto" real
            # ------------------------------------------------------------------
            .globl kof_web_app_new
            .type kof_web_app_new, @function
            kof_web_app_new:
                movq $1, %rax
                ret

            # ------------------------------------------------------------------
            # kof_web_body(): body da última request (vazio se não há)
            # ------------------------------------------------------------------
            .globl kof_web_body
            .type kof_web_body, @function
            kof_web_body:
                leaq .Lweb_last_body(%rip), %rdi
                movq .Lweb_last_blen(%rip), %rsi
                call kof_string_from_literal
                ret

            # ------------------------------------------------------------------
            # kof_web_set_body_ctx(rdi=ptr, rsi=len) — chamado pelo handle_client
            # ------------------------------------------------------------------
            .globl kof_web_set_body_ctx
            .type kof_web_set_body_ctx, @function
            kof_web_set_body_ctx:
                cmpq $8191, %rsi
                jle .Lsbc_ok
                movl $8191, %esi
            .Lsbc_ok:
                movq %rsi, .Lweb_last_blen(%rip)
                leaq .Lweb_last_body(%rip), %rdx
                xorq %rcx, %rcx
            .Lsbc_cp:
                cmpq %rsi, %rcx
                jae .Lsbc_done
                movb (%rdi,%rcx), %al
                movb %al, (%rdx,%rcx)
                incq %rcx
                jmp .Lsbc_cp
            .Lsbc_done:
                movb $0, (%rdx,%rcx)
                ret

            # ------------------------------------------------------------------
            # route(app_ign rdi, method_string rsi, path_string rdx, handler rcx)
            # Registra 1 slot
            # ------------------------------------------------------------------
            .globl kof_web_route
            .type kof_web_route, @function
            kof_web_route:
                movq .Lweb_nroutes(%rip), %r8
                imulq $32, %r8, %r9
                leaq .Lweb_routes(%rip), %r10
                addq %r9, %r10
                movq %rsi, 0(%r10)
                movq %rdx, 8(%r10)
                movq %rcx, 16(%r10)
                movq $0, 24(%r10)
                incq %r8
                movq %r8, .Lweb_nroutes(%rip)
                ret

            """;
    }
}

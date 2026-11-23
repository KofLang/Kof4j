package dev.kof.compiler.nat;

/**
 * known-bugs §1198 — o `http.send(...).status()/.body()` do nativo x86-64 não
 * tinha runtime: o backend emitia os verbos (`kof_http_get/.../status`) mas os
 * símbolos do tipo RESPOSTA — que o JVM tem como `KofRuntime.HttpResponse` —
 * nunca eram emitidos, e o `ld` recusava com "referência não definida" ao usar a
 * forma idiomática.
 *
 * <p>A correção REUSA o cliente HTTP que já existe (nada de um segundo
 * cliente): o handle é <b>duas palavras</b> no heap — {status, corpo} —, o corpo
 * sai de `kof_http_get_headers(url, headers)` (devolve um `KofString` do corpo)
 * e o status de `.Lhttp_last_status`, que o MESMO request acabou de gravar.
 * (Chamar `kof_http_status(url)` outra vez seria um SEGUNDO request cuja
 * resposta — 403 de origem, por exemplo — poderia nem ser a mesma.) O cabeçalho não é exposto pelo núcleo atual, então `header()` devolve
 * a string vazia — e isso fica declarado no ledger, nunca escondido.
 *
 * <p>O primeiro byte do método decide o verbo: 'G' (GET) e 'H' (HEAD) têm
 * verbo próprio; os outros são RECUSADOS com a mensagem nomeada HTTP002 em vez
 * de um handle vazio.
 */
public final class NativeHttpResponse {

    private NativeHttpResponse() {}

    static String source() {
        return """
            # ── §1198: http.send(...) no nativo — handle {status, body} ──────
            .globl kof_http_send_response
            .type kof_http_send_response, @function
            kof_http_send_response:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx          # method (KofString*)
                movq %rsi, %r12          # url (KofString*)
                movq %rcx, %r13          # headers (KofString* | 0)
                movzbl 24(%rbx), %eax    # o primeiro byte do texto do metodo
                cmpb $'G', %al
                je .Lhrs_get
                cmpb $'H', %al
                je .Lhrs_get
                leaq .Lhrs_msg_method(%rip), %rdi
                movl $62, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            .Lhrs_get:
                movq %r12, %rdi
                movq %r13, %rsi
                call kof_http_get_headers
                movq %rax, %r13          # corpo (KofString*)
                # o status da MESMA request: o core guarda em .Lhttp_last_status e
                # e' ele que vale aqui. Chamar `kof_http_status(url)` de novo faria
                # OUTRA request e devolveria o status dela (MEDIDO 23/11: corpo 200
                # do /me e status 403 da chamada anterior).
                movq .Lhttp_last_status(%rip), %r12
                movq $16, %rdi
                call kof_alloc
                movq %r12, (%rax)
                movq %r13, 8(%rax)
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_http_response_status
            .type kof_http_response_status, @function
            kof_http_response_status:
                testq %rdi, %rdi
                je .Lhrs_status_zero
                movq (%rdi), %rax
                ret
            .Lhrs_status_zero:
                xorl %eax, %eax
                ret

            .globl kof_http_response_body
            .type kof_http_response_body, @function
            kof_http_response_body:
                testq %rdi, %rdi
                je .Lhrs_body_empty
                movq 8(%rdi), %rax
                ret
            .Lhrs_body_empty:
                leaq .Lhrs_empty_kstr(%rip), %rax
                ret

            .globl kof_http_response_header
            .type kof_http_response_header, @function
            kof_http_response_header:
                # o núcleo atual não guarda o cabeçalho: string VAZIA, declarada
                leaq .Lhrs_empty_kstr(%rip), %rax
                ret

            .section .rodata
            .Lhrs_msg_method: .asciz "kof.http: no nativo, http.send aceita so' GET e HEAD (HTTP002) — o handle de resposta dos outros verbos nao existe ainda"
            .align 8
            .Lhrs_empty_kstr: .quad 0, 0, 0
            .section .text
            """;
    }
}

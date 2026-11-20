package dev.kof.compiler.nat;
import dev.kof.compiler.Type;

/**
 * kof.web server x86-64 (WEB002) — dados/estruturas + primitivas:
 * strlen, i32str, app_new, body ctx, registro de rotas.
 * Extraído de NativeWebRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeWebRuntime preserva o assembly injetado byte-a-byte.
 */
public final class NativeWebCore {

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
            # §1159: a tabela código→texto do status (mesmo conjunto do JVM)
            .Lweb_status_texts:
                .quad 200
                .quad .Lweb_st_ok
                .quad 201
                .quad .Lweb_st_created
                .quad 202
                .quad .Lweb_st_accepted
                .quad 204
                .quad .Lweb_st_no_content
                .quad 301
                .quad .Lweb_st_moved
                .quad 302
                .quad .Lweb_st_found
                .quad 304
                .quad .Lweb_st_not_modified
                .quad 400
                .quad .Lweb_st_bad_request
                .quad 401
                .quad .Lweb_st_unauthorized
                .quad 403
                .quad .Lweb_st_forbidden
                .quad 404
                .quad .Lweb_st_not_found
                .quad 409
                .quad .Lweb_st_conflict
                .quad 422
                .quad .Lweb_st_unprocessable
                .quad 429
                .quad .Lweb_st_too_many
                .quad 500
                .quad .Lweb_st_internal
                .quad 502
                .quad .Lweb_st_bad_gateway
                .quad 503
                .quad .Lweb_st_unavailable
                .quad 0
                .quad 0
            .Lweb_st_ok: .asciz "OK"
            .Lweb_st_created: .asciz "Created"
            .Lweb_st_accepted: .asciz "Accepted"
            .Lweb_st_no_content: .asciz "No Content"
            .Lweb_st_moved: .asciz "Moved Permanently"
            .Lweb_st_found: .asciz "Found"
            .Lweb_st_not_modified: .asciz "Not Modified"
            .Lweb_st_bad_request: .asciz "Bad Request"
            .Lweb_st_unauthorized: .asciz "Unauthorized"
            .Lweb_st_forbidden: .asciz "Forbidden"
            .Lweb_st_not_found: .asciz "Not Found"
            .Lweb_st_conflict: .asciz "Conflict"
            .Lweb_st_unprocessable: .asciz "Unprocessable Entity"
            .Lweb_st_too_many: .asciz "Too Many Requests"
            .Lweb_st_internal: .asciz "Internal Server Error"
            .Lweb_st_bad_gateway: .asciz "Bad Gateway"
            .Lweb_st_unavailable: .asciz "Service Unavailable"

            .section .bss
            .Lweb_reqbuf:     .space 16384
            .Lweb_skb:        .space 8192
            .Lweb_last_body:  .space 8192     # body extraído da última request
            .Lweb_last_blen:  .quad 0
            .Lweb_last_path:  .space 512
            # §1159 (20/11): o CONTEXTO da request corrente — o path e o
            # método são guardados pelo handle_client quando uma rota casa;
            # kof_web_path()/kof_web_method() os devolvem como String.
            .Lweb_last_pathlen: .quad 0
            .Lweb_last_method: .space 16
            .Lweb_last_methodlen: .quad 0
            # §1159 (20/11): o STATUS da resposta — kof_web_status(code, body)
            # guarda o código (0 = nenhum status setado -> 200) e o texto
            # (ponteiro; 0 = "OK"). A tabela código→texto espelha o
            # `kof_web_status_text` do runtime JVM (mesmo contrato).
            .Lweb_status_code: .quad 0
            .Lweb_status_text: .quad 0
            # §1159 (20/11): a fila de headers — 16 slots de 32B:
            # name_ptr(0)/value_ptr(8)/name_len(16)/value_len(24).
            .Lweb_hdrq: .space 512
            .Lweb_hdrq_n: .quad 0
            # §1159 (20/11): os PATH PARAMS da rota que casou — 16 slots de
            # 32B: name_ptr(0)/name_len(8)/value_ptr(16)/value_len(24). Os
            # ponteiros vivem no padrão da rota (heap) e no buffer da request.
            .Lweb_params: .space 512
            .Lweb_params_n: .quad 0

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
            # §1159 (20/11): o CONTEXTO da request corrente.
            # kof_web_set_req_ctx(rdi=method_chars, rsi=method_len,
            #                     rdx=path_chars, rcx=path_len)
            # — chamado pelo handle_client quando uma rota casa. Preserva
            # rcx (o índice da rota que o chamador reusa) e os callee-saved.
            # ------------------------------------------------------------------
            .globl kof_web_set_req_ctx
            .type kof_web_set_req_ctx, @function
            kof_web_set_req_ctx:
                pushq %rcx
                pushq %rdx
                pushq %rbx
                # o status é por request: zera antes do handler
                movq $0, .Lweb_status_code(%rip)
                movq $0, .Lweb_status_text(%rip)
                movq $0, .Lweb_hdrq_n(%rip)
                # NÃO zera .Lweb_params_n aqui: o kof_web_match_path já o zera
                # a cada tentativa, e este ponto roda DEPOIS do match (zerar
                # aqui apagaria os params que o handler vai ler).
                # método: copia rsi bytes de rdi para .Lweb_last_method (cap 15)
                cmpq $15, %rsi
                jbe .Lsrc_m_len
                movl $15, %esi
            .Lsrc_m_len:
                movq %rsi, .Lweb_last_methodlen(%rip)
                leaq .Lweb_last_method(%rip), %rdx
                xorq %r8, %r8
            .Lsrc_m_loop:
                cmpq %rsi, %r8
                jae .Lsrc_m_done
                movb (%rdi,%r8), %al
                movb %al, (%rdx,%r8)
                incq %r8
                jmp .Lsrc_m_loop
            .Lsrc_m_done:
                movb $0, (%rdx,%r8)
                # path: copia rcx bytes de rdx (que está em 8(%rsp)) para
                # .Lweb_last_path (cap 511)
                movq 8(%rsp), %rdx
                movq 16(%rsp), %rcx
                cmpq $511, %rcx
                jbe .Lsrc_p_len
                movl $511, %ecx
            .Lsrc_p_len:
                movq %rcx, .Lweb_last_pathlen(%rip)
                leaq .Lweb_last_path(%rip), %rdi
                xorq %r8, %r8
            .Lsrc_p_loop:
                cmpq %rcx, %r8
                jae .Lsrc_p_done
                movb (%rdx,%r8), %al
                movb %al, (%rdi,%r8)
                incq %r8
                jmp .Lsrc_p_loop
            .Lsrc_p_done:
                movb $0, (%rdi,%r8)
                popq %rbx
                popq %rdx
                popq %rcx
                ret

            # kof_web_status(rdi=code, rsi=body) -> String
            # §1159 (20/11): guarda o status da resposta e DEVOLVE o body
            # (o contrato do JVM: `status(404, "gone")` responde "gone").
            .globl kof_web_status
            .type kof_web_status, @function
            kof_web_status:
                pushq %rbx
                movl %edi, %ebx              # code
                movq %rbx, .Lweb_status_code(%rip)
                # procura o texto na tabela (pares code/ptr, sentinela 0/0)
                leaq .Lweb_status_texts(%rip), %r8
            .Lst_lookup:
                movq 0(%r8), %rax
                testq %rax, %rax
                jz .Lst_default
                cmpq %rbx, %rax
                je .Lst_found
                addq $16, %r8
                jmp .Lst_lookup
            .Lst_found:
                movq 8(%r8), %rax
                jmp .Lst_store
            .Lst_default:
                leaq .Lweb_st_ok(%rip), %rax
            .Lst_store:
                movq %rax, .Lweb_status_text(%rip)
                popq %rbx
                movq %rsi, %rax                # devolve o body
                ret

            # kof_web_header_set(rdi=name kofstr, rsi=value kofstr) -> String
            # §1159 (20/11): a FILA de headers. Contrato do JVM: CR/LF no nome
            # ou no valor NUNCA chega ao fio (recusa = rax null); Set-Cookie
            # REPETE; todo outro header last-wins (slot pelo nome).
            .globl kof_web_header_set
            .type kof_web_header_set, @function
            kof_web_header_set:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # name kofstr
                movq %rsi, %r12          # value kofstr
                # guarda anti-CRLF: nome
                xorq %r8, %r8
            .Lhs_n_scan:
                movl 16(%rbx), %eax
                cmpq %rax, %r8
                jae .Lhs_n_ok
                movzbl 24(%rbx,%r8), %eax
                cmpb $13, %al
                je .Lhs_reject
                cmpb $10, %al
                je .Lhs_reject
                incq %r8
                jmp .Lhs_n_scan
            .Lhs_n_ok:
                # guarda anti-CRLF: valor
                xorq %r8, %r8
            .Lhs_v_scan:
                movl 16(%r12), %eax
                cmpq %rax, %r8
                jae .Lhs_v_ok
                movzbl 24(%r12,%r8), %eax
                cmpb $13, %al
                je .Lhs_reject
                cmpb $10, %al
                je .Lhs_reject
                incq %r8
                jmp .Lhs_v_scan
            .Lhs_v_ok:
                # Set-Cookie (case-insensitive) repete; o resto last-wins
                # a comparação é entre KofStrings: constrói "Set-Cookie"
                leaq .Lweb_st_cookie(%rip), %rdi
                movl $10, %esi
                call kof_string_from_literal
                movq %rax, %r13
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_string_equals_ignore_case
                testl %eax, %eax
                jnz .Lhs_new
                # procura o slot com o mesmo nome
                xorq %r13, %r13
            .Lhs_lw:
                cmpq .Lweb_hdrq_n(%rip), %r13
                jae .Lhs_new
                movq %r13, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_hdrq(%rip), %r9
                addq %rax, %r9
                movq 0(%r9), %rdi
                movq %rbx, %rsi
                call kof_string_equals_ignore_case
                testl %eax, %eax
                jnz .Lhs_put
                incq %r13
                jmp .Lhs_lw
            .Lhs_new:
                movq .Lweb_hdrq_n(%rip), %r13
                cmpq $15, %r13
                ja .Lhs_reject
                movq %r13, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_hdrq(%rip), %r9
                addq %rax, %r9
                leaq 1(%r13), %rax
                movq %rax, .Lweb_hdrq_n(%rip)
            .Lhs_put:
                movq %rbx, 0(%r9)
                movq %r12, 8(%r9)
                movl 16(%rbx), %eax
                movl %eax, 16(%r9)
                movl 16(%r12), %eax
                movl %eax, 24(%r9)
                movq %r12, %rax          # devolve o value
                jmp .Lhs_ret
            .Lhs_reject:
                xorq %rax, %rax
            .Lhs_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_web_match_path(rdi=pattern kofstr, rsi=path_chars, rdx=path_len)
            #   -> eax 1 = casou (e preencheu .Lweb_params), 0 = não.
            # §1159 (20/11): o match por SEGMENTO — um segmento do padrão que
            # começa com ':' casa qualquer segmento do path e CAPTURA o valor
            # (nome = o texto após o ':' até o próximo '/' ou o fim).
            .globl kof_web_match_path
            .type kof_web_match_path, @function
            kof_web_match_path:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # pattern kofstr
                movq %rsi, %r12          # path chars
                movq %rdx, %r13          # path len
                leaq 24(%rbx), %r14      # pattern chars
                movl 16(%rbx), %r15d     # pattern len (zero-extends)
                xorq %r8, %r8            # pi (pattern index)
                xorq %r9, %r9            # qi (path index)
                movq $0, .Lweb_params_n(%rip)
            .Lmp_loop:
                cmpl %r15d, %r8d
                jb .Lmp_p_left
                # pattern exhausted: the path must be too
                cmpq %r13, %r9
                jae .Lmp_ok
                jmp .Lmp_fail
            .Lmp_p_left:
                cmpq %r13, %r9
                jae .Lmp_fail            # path exhausted first
                movzbl (%r14,%r8), %eax
                cmpb $58, %al            # ':'
                je .Lmp_param
                movzbl (%r12,%r9), %ecx
                cmpb %cl, %al
                jne .Lmp_fail
                incq %r8
                incq %r9
                jmp .Lmp_loop
            .Lmp_param:
                # name = pattern[pi+1 ..) até '/' ou fim
                leaq 1(%r8), %r10        # name_start
                movq %r10, %r11          # name_end
            .Lmp_name_scan:
                cmpl %r15d, %r11d
                jae .Lmp_name_done
                movzbl (%r14,%r11), %eax
                cmpb $47, %al
                je .Lmp_name_done
                incq %r11
                jmp .Lmp_name_scan
            .Lmp_name_done:
                # value = path[qi ..) até '/' ou fim
                movq %r9, %rdx           # value_start
                movq %r9, %rsi           # value_end
            .Lmp_val_scan:
                cmpq %r13, %rsi
                jae .Lmp_val_done
                movzbl (%r12,%rsi), %eax
                cmpb $47, %al
                je .Lmp_val_done
                incq %rsi
                jmp .Lmp_val_scan
            .Lmp_val_done:
                movq .Lweb_params_n(%rip), %rax
                cmpq $15, %rax
                ja .Lmp_fail
                imulq $32, %rax, %rax
                leaq .Lweb_params(%rip), %rcx
                addq %rax, %rcx
                leaq (%r14,%r10), %rax
                movq %rax, 0(%rcx)       # name ptr
                movq %r11, %rax
                subq %r10, %rax
                movq %rax, 8(%rcx)       # name len
                leaq (%r12,%rdx), %rax
                movq %rax, 16(%rcx)      # value ptr
                movq %rsi, %rax
                subq %rdx, %rax
                movq %rax, 24(%rcx)      # value len
                incq .Lweb_params_n(%rip)
                movq %r11, %r8
                movq %rsi, %r9
                jmp .Lmp_loop
            .Lmp_ok:
                movl $1, %eax
                jmp .Lmp_ret
            .Lmp_fail:
                xorl %eax, %eax
            .Lmp_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_web_param(rdi=name kofstr) -> String (o valor do path param;
            # string vazia quando não casa — o JVM devolve null, o contrato
            # nativo usa "" para não propagar null aos consumidores).
            .globl kof_web_param
            .type kof_web_param, @function
            kof_web_param:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx          # name kofstr
                leaq 24(%rbx), %r12      # name chars
                movl 16(%rbx), %r13d     # name len
                xorq %r14, %r14          # slot
            .Lwp_loop:
                cmpq .Lweb_params_n(%rip), %r14
                jae .Lwp_miss
                movq %r14, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_params(%rip), %r8
                addq %rax, %r8
                movq 8(%r8), %rax        # name len do slot
                cmpq %r13, %rax
                jne .Lwp_next
                movq 0(%r8), %rsi        # slot name ptr
                xorq %rcx, %rcx
            .Lwp_cmp:
                cmpq %r13, %rcx
                jae .Lwp_hit
                movb (%rsi,%rcx), %al
                cmpb (%r12,%rcx), %al
                jne .Lwp_next
                incq %rcx
                jmp .Lwp_cmp
            .Lwp_hit:
                movq 16(%r8), %rdi       # value ptr
                movq 24(%r8), %rsi       # value len
                call kof_string_from_literal
                jmp .Lwp_ret
            .Lwp_next:
                incq %r14
                jmp .Lwp_loop
            .Lwp_miss:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
            .Lwp_ret:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .data
            .Lweb_empty: .asciz ""
            # §1159 (20/11): o nome do header que REPETE (RFC 6265 §3)
            .Lweb_st_cookie: .asciz "Set-Cookie"
            .section .text

            # kof_web_path() -> String (o path da request corrente)
            .globl kof_web_path
            .type kof_web_path, @function
            kof_web_path:
                leaq .Lweb_last_path(%rip), %rdi
                movq .Lweb_last_pathlen(%rip), %rsi
                call kof_string_from_literal
                ret

            # kof_web_method() -> String
            .globl kof_web_method
            .type kof_web_method, @function
            kof_web_method:
                leaq .Lweb_last_method(%rip), %rdi
                movq .Lweb_last_methodlen(%rip), %rsi
                call kof_string_from_literal
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

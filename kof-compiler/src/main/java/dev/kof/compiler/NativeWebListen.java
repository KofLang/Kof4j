package dev.kof.compiler;

/**
 * kof.web server x86-64 (WEB002) — listen/bind/accept loop + handle_client
 * (parse method/path, match de rotas, despacho via trampolim, write do body).
 * Extraído de NativeWebRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeWebRuntime preserva o assembly injetado byte-a-byte.
 */
final class NativeWebListen {

    private NativeWebListen() {}

    static String source() {
        return """
            # ------------------------------------------------------------------
            # my_strlen vs Kof-String (Kof Expands tipo String em (char*, len))
            # Calcula uma string C em rdi e devolve rax=ptr; len em rdx.
            # ------------------------------------------------------------------
            # Não sei — uso o que existe: kof_string_to_cstring? Não sei se existe.
            # Build-to-order: apenas retorno o body nochamlot.

            # ------------------------------------------------------------------
            # listen(row_ptr rdi, port_int rsi)
            # Estratégia: criar socket direto; só sem raw syscalls.
            # ------------------------------------------------------------------
            .globl kof_web_listen
            .type kof_web_listen, @function
            kof_web_listen:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rsi, %r14                  # port (rbx = align16 lixo)
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                movl $41, %eax                   # SYS_socket
                syscall
                testq %rax, %rax
                js .Lwl_fail
                movq %rax, %rbx                  # server_fd
                # bind
                subq $16, %rsp
                movw $2, (%rsp)
                movq %r14, %rax
                movzx %ax, %eax
                xchgb %al, %ah                   # htons
                movw %ax, 2(%rsp)
                movl $0, 4(%rsp)                 # 0.0.0.0
                movq $0, 8(%rsp)
                movq %rbx, %rdi
                movq %rsp, %rsi
                movl $16, %edx
                movl $49, %eax                   # SYS_bind
                syscall
                testq %rax, %rax
                js .Lwl_bf
                addq $16, %rsp
                # listen
                movq %rbx, %rdi
                movl $64, %esi
                movl $50, %eax                   # SYS_listen
                syscall
            # ---- accept loop ----
            .Lwl_accept:
                movq %rbx, %rdi
                xorl %esi, %esi
                xorl %edx, %edx
                xorl %r10d, %r10d
                movl $43, %eax                   # SYS_accept
                syscall
                testq %rax, %rax
                js .Lwl_accept
                movq %rax, %r12                  # client_fd
                movq %r12, %rdi                  # passa para handle_client
                call kof_web_handle_client
                movq %r12, %rdi
                movl $3, %eax                    # SYS_close
                syscall
                jmp .Lwl_accept
            .Lwl_bf:
                addq $16, %rsp
            .Lwl_fail:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # handle_client(rdi = client_fd)
            # Fluxo: lê request → parseia method+path → pesquisa em routes
            # → responde (200 "route-match" se achou, 404 senão).
            # ------------------------------------------------------------------
            .globl kof_web_handle_client
            .type kof_web_handle_client, @function
            kof_web_handle_client:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx                  # fd

                # read
                movq %rbx, %rdi
                leaq .Lweb_reqbuf(%rip), %rsi
                movl $16384, %edx
                xorl %eax, %eax                  # SYS_read
                syscall
                testq %rax, %rax
                jle .Lwh_404
                movq %rax, %r15                  # len total
                movq %rax, .Lweb_reqlen(%rip)
                movq $200, .Lweb_status(%rip)    # reset resposta da request anterior
                movq $0, .Lweb_nresphdr(%rip)

                # ------- T4: detecta body (apos CRLF CRLF) -------
                leaq .Lweb_reqbuf(%rip), %r8     # cursor
                leaq (%r8,%r15), %r9             # end
                leaq .Lweb_reqbuf(%rip), %rsi    # scan todas: \r\n\r\n
            .Lwh_bodyseek:
                cmpq %r9, %rsi
                jae .Lwh_nobody
                movb (%rsi), %al
                cmpb $13, %al
                jne .Lwh_seeknext
                movb 1(%rsi), %al
                cmpb $10, %al
                jne .Lwh_seeknext
                movb 2(%rsi), %al
                cmpb $13, %al
                jne .Lwh_seeknext
                movb 3(%rsi), %al
                cmpb $10, %al
                jne .Lwh_seeknext
                # achou body separator: body comeca em rsi+4
                leaq 4(%rsi), %rdi               # body ptr
                movq %r9, %rsi                   # end
                subq %rdi, %rsi                  # body len
                call kof_web_set_body_ctx
                jmp .Lwh_parsedone
            .Lwh_seeknext:
                incq %rsi
                jmp .Lwh_bodyseek
            .Lwh_nobody:
                leaq .Lweb_reqbuf(%rip), %rdi
                xorq %rsi, %rsi
                call kof_web_set_body_ctx
            .Lwh_parsedone:

                # ------- parse METHOD -------
                leaq .Lweb_reqbuf(%rip), %r8     # cursor
                movq %r8, %r9                    # method_start
            .Lwh_m:
                movb (%r8), %al
                cmpb $32, %al                    # ' '
                je .Lwh_m_done
                incq %r8
                jmp .Lwh_m
            .Lwh_m_done:
                movq %r8, %r10                   # method_end
                incq %r8                         # skip space
                # ------- parse PATH -------
                movq %r8, %r11                   # path_start
            .Lwh_p:
                movb (%r8), %al
                cmpb $32, %al
                je .Lwh_p_done
                cmpb $'\\r', %al
                je .Lwh_p_done
                incq %r8
                jmp .Lwh_p
            .Lwh_p_done:
                movq %r8, %r12                   # path_end

                # r9=method_start r10=method_end r11=path_start r12=path_end

                # guarda method/path/query/headers p/ method()/path()/query()/header()
                movq %r9, %rdi
                movq %r10, %rsi
                movq %r11, %rdx
                movq %r12, %rcx
                call kof_web_store_ctx

                # ------- lookup -------
                movq .Lweb_nroutes(%rip), %r13   # count
                leaq .Lweb_routes(%rip), %r14    # base
                xorq %rcx, %rcx                  # idx
            .Lwh_loop:
                cmpq %r13, %rcx
                jae .Lwh_404
                # r14 + rcx*32
                movq %rcx, %rax
                imulq $32, %rax, %rax
                leaq (%r14,%rax), %r8            # route[i] (entry)
                # kof method
                movq 0(%r8), %rdi                # method ptr (kof string)
                # method string é (len14 %rdi, chars @ 24(%rdi))
                movl 16(%rdi), %eax              # kof len
                movq %r10, %rdx                  # method_end
                subq %r9, %rdx                   # method_req_len
                cmpl %eax, %edx
                jne .Lwh_next
                # compara chars
                leaq 24(%rdi), %rsi              # src chars
                leaq 0(%r9), %rdi                # req method chars
                # loop
                xorl %eax, %eax
            .Lwh_cm:
                cmpl %edx, %eax
                jae .Lwh_cmdone
                movb (%rsi,%rax), %r8b
                cmpb (%rdi,%rax), %r8b
                jne .Lwh_next
                incl %eax
                jmp .Lwh_cm
            .Lwh_cmdone:
                # Se matchou method, compara path por segmentos (+ params ':id')
                movq %rcx, %r12                  # salva idx (helper clobbra rcx)
                movq %r12, %rax
                imulq $32, %rax, %rax
                leaq (%r14,%rax), %r8
                movq 8(%r8), %rdi                # route path KofString*
                movq .Lweb_path_ptr(%rip), %rsi  # req path ptr
                movq .Lweb_path_len(%rip), %rdx  # req path len
                call kof_web_path_match
                testq %rax, %rax
                jz .Lwh_next_saved
                # ------- MATCH: invoca handler via trampolim -------
                movq %r12, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_routes(%rip), %r11
                addq %rax, %r11
                movq 16(%r11), %rdi              # handler object
                testq %rdi, %rdi
                jz .Lwh_hello
                movq 8(%rdi), %rax               # vtable
                movq (%rax), %rax                # invoke
                subq $8, %rsp
                call *%rax
                addq $8, %rsp
                # rax = KofString (body)
                testq %rax, %rax
                jz .Lwh_hello                    # null → hello
                movq %rbx, %rdi
                movq %rax, %rsi                  # body ptr (String)
                call kof_web_write_body_response
                jmp .Lwh_done

            # ------- 404 -------
            .Lwh_next_saved:
                movq %r12, %rcx
                incq %rcx
                jmp .Lwh_loop
            .Lwh_next:
                incq %rcx
                jmp .Lwh_loop

            # ------- MATCH fallback: responde "hello" se handler null -------
            .Lwh_hello:
                movq %rbx, %rdi
                leaq .Lweb_body_hello_lit(%rip), %rsi
                call kof_web_write_body_response
                jmp .Lwh_done

            # ---------------------------------------
            # write_body_response: rdi=client_fd, rsi=KofString(body)
            # Escreve header 200 + Content-Length + CRLF + body
            # ---------------------------------------
            kof_web_write_body_response:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx                  # fd
                movq %rsi, %r12                  # body String
                leaq .Lweb_skb(%rip), %r13
                # status line: HTTP/1.1 <code> <text> + CRLF
                leaq .Lweb_h1(%rip), %rsi
                movq %r13, %rdi
                call kof_web_append_cstr
                movq %rax, %r13
                movl .Lweb_status(%rip), %edi    # code
                call kof_int_to_string
                movl 16(%rax), %edx
                leaq 24(%rax), %rsi
            .Lwb_status_code:
                movb (%rsi), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                decl %edx
                jnz .Lwb_status_code
                movb $' ', (%r13); incq %r13
                movl .Lweb_status(%rip), %edi
                call kof_web_status_text          # rax = cstr
                movq %rax, %rsi
                movq %r13, %rdi
                call kof_web_append_cstr
                movq %rax, %r13
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                # custom response headers (name: value)
                movq $0, %r14                    # idx (sem calls nas cópias)
            .Lwb_hdr_loop:
                cmpq .Lweb_nresphdr(%rip), %r14
                jae .Lwb_hdr_done
                movq %r14, %rax
                imulq $16, %rax, %rax
                leaq .Lweb_resp_hdrs(%rip), %r8
                addq %rax, %r8                  # slot
                movq 0(%r8), %rsi               # name String
                movl 16(%rsi), %edx
                leaq 24(%rsi), %rsi
            .Lwb_hdr_name:
                movb (%rsi), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                decl %edx
                jnz .Lwb_hdr_name
                movb $':', (%r13); incq %r13
                movb $' ', (%r13); incq %r13
                movq 8(%r8), %rsi               # value String
                movl 16(%rsi), %edx
                leaq 24(%rsi), %rsi
            .Lwb_hdr_val:
                movb (%rsi), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                decl %edx
                jnz .Lwb_hdr_val
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                incq %r14
                jmp .Lwb_hdr_loop
            .Lwb_hdr_done:
                leaq .Lweb_hct(%rip), %rsi
                movq %r13, %rdi
                call kof_web_append_cstr
                movq %rax, %r13
                leaq .Lweb_hnl(%rip), %rsi
                movq %r13, %rdi
                call kof_web_append_cstr
                movq %rax, %r13
                # Content-Length value (kof_int_to_string)
                movl 16(%r12), %edi              # body len (Int32)
                call kof_int_to_string
                # rax=String; append
                movl 16(%rax), %edx              # len deste String
                leaq 24(%rax), %rsi              # chars
            .Lwb_resp_cl:
                movb (%rsi), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                decl %edx
                jnz .Lwb_resp_cl
                # CRLF CRLF
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                # Body bytes (len da KofString)
                movl 16(%r12), %r14d             # len
                leaq 24(%r12), %rsi              # chars
                xorq %rdx, %rdx
            .Lwb_body:
                cmpl %edx, %r14d
                jle .Lwb_body_done
                movb (%rsi,%rdx), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rdx
                jmp .Lwb_body
            .Lwb_body_done:
                # write(f, skb, cursor - skb)
                leaq .Lweb_skb(%rip), %rsi
                movq %r13, %rdx
                subq %rsi, %rdx
                movq %rbx, %rdi
                movl $1, %eax                    # SYS_write
                syscall
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret


            # ------- 404 -------
            .Lwh_404:
                movq %rbx, %rdi
                call kof_web_send_404
            .Lwh_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """;
    }
}

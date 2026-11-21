package dev.kof.compiler.nat;

/**
 * kof.web server x86-64 (WEB002) — listen/bind/accept loop + handle_client
 * (parse method/path, match de rotas, despacho via trampolim, write do body).
 * Extraído de NativeWebRuntime (REFACTOR-500 Fase 8); a concatenação em
 * NativeWebRuntime preserva o assembly injetado byte-a-byte.
 */
public final class NativeWebListen {

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
                # §1159 (21/11): o PATH é separado do QUERY STRING — o
                # roteamento casa o path SEM o "?..." (antes, o request
                # "/list?page=2" casava "/list?page=2" e dava 404) e o
                # path() devolve o path sem o query (o contrato do
                # readRequest do JVM: path = fullPath.substring(0, q)).
                # O helper devolve o comprimento do path SEM o query.
                movq %r11, %rdi                  # path chars
                movq %r8, %rsi
                subq %r11, %rsi                  # path len (com o query)
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                call kof_web_split_query
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                addq %r11, %rax                  # path_end = path_start + len
                movq %rax, %r12

                # r9=method_start r10=method_end r11=path_start r12=path_end

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
                # §1159 (20/11): o match do PATH agora é por SEGMENTO (aceita
                # `:name` e captura o valor) — o helper preenche .Lweb_params.
                # r9/r10/r11 (os limites do method/path) e rcx (o índice da
                # rota) sobrevivem ao call: o helper usa-os como scratch.
                movq %rcx, %rax
                imulq $32, %rax, %rax
                leaq (%r14,%rax), %r8
                movq 8(%r8), %rdi                # path pattern kofstr
                movq %r11, %rsi                  # req path chars
                movq %r12, %rdx
                subq %r11, %rdx                  # req path len
                pushq %r9
                pushq %r10
                pushq %r11
                pushq %rcx
                call kof_web_match_path
                popq %rcx
                popq %r11
                popq %r10
                popq %r9
                testl %eax, %eax
                jne .Lwh_cpdone
                jmp .Lwh_next
            .Lwh_cpdone:
                # ------- §1159 (20/11): o CONTEXTO é guardado ANTES do
                # handler rodar: método (r9..r10) e path (r11..r12) para
                # kof_web_method()/kof_web_path(). (Os r9..r12 estão vivos
                # aqui; r13/r14 são os dados do lookup.)
                # o índice da rota (rcx) é REUSADO pelo código abaixo — o
                # pushq o preserva e ALINHA o rsp para o call (SysV).
                pushq %rcx
                movq %r9, %rdi                 # method chars
                movq %r10, %rsi
                subq %r9, %rsi                 # method len
                movq %r11, %rdx                # path chars
                movq %r12, %rcx
                subq %r11, %rcx                # path len
                call kof_web_set_req_ctx
                popq %rcx
                # ------- MATCH: invoca handler via trampolim -------
                # handler obj está em 16(%r14,%rax-slot-atual) — reaponto:
                movq %rcx, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_routes(%rip), %r11
                addq %rax, %r11
                movq 16(%r11), %rdi              # handler object
                testq %rdi, %rdi
                jz .Lwh_hello
                movq 8(%rdi), %rax               # vtable
                movq (%rax), %rax                # invoke
                # ABI SysV: call *%rax — precisa rsp alinhado; estamos após 5 pushes+1
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
                # cabecalho 200 OK (sem literais CRLF aqui no asm-comment)
                leaq .Lweb_h1(%rip), %rsi
                movq %r13, %rdi
                call kof_web_append_cstr
                movq %rax, %r13
                # §1159 (20/11): o status guardado (default 200) — o número
                # vai pelo kof_int_to_string e o texto pelo ponteiro guardado
                # pelo kof_web_status (0 = "OK"/200).
                movq .Lweb_status_code(%rip), %rdi
                testq %rdi, %rdi
                jne .Lwb_st_num
                movl $200, %edi
            .Lwb_st_num:
                call kof_int_to_string
                movl 16(%rax), %edx
                leaq 24(%rax), %rsi
            .Lwb_st_cl:
                movb (%rsi), %cl
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                decl %edx
                jnz .Lwb_st_cl
                movb $32, (%r13); incq %r13
                movq .Lweb_status_text(%rip), %rsi
                testq %rsi, %rsi
                jne .Lwb_st_txt
                leaq .Lweb_st_ok(%rip), %rsi
            .Lwb_st_txt:
                movb (%rsi), %cl
                testb %cl, %cl
                je .Lwb_st_done
                movb %cl, (%r13)
                incq %r13
                incq %rsi
                jmp .Lwb_st_txt
            .Lwb_st_done:
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                # §1159 (20/11): a FILA de headers (setados pelo handler).
                # Sem chamadas dentro do laço: r10/r11 são scratch seguros.
                xorq %r14, %r14
            .Lwb_hdrs:
                cmpq .Lweb_hdrq_n(%rip), %r14
                jae .Lwb_hdrs_done
                movq %r14, %rax
                imulq $32, %rax, %rax
                leaq .Lweb_hdrq(%rip), %r10
                addq %rax, %r10
                # nome (kofstr ptr @0): len @16, chars @24
                movq 0(%r10), %rsi
                movl 16(%rsi), %r11d
                addq $24, %rsi
                xorq %r8, %r8
            .Lwb_hn:
                cmpl %r11d, %r8d
                jae .Lwb_hn_done
                movzbl (%rsi,%r8), %eax
                movb %al, (%r13)
                incq %r13
                incq %r8
                jmp .Lwb_hn
            .Lwb_hn_done:
                movb $58, (%r13); incq %r13
                movb $32, (%r13); incq %r13
                # valor (kofstr ptr @8)
                movq 8(%r10), %rsi
                movl 16(%rsi), %r11d
                addq $24, %rsi
                xorq %r8, %r8
            .Lwb_hv:
                cmpl %r11d, %r8d
                jae .Lwb_hv_done
                movzbl (%rsi,%r8), %eax
                movb %al, (%r13)
                incq %r13
                incq %r8
                jmp .Lwb_hv
            .Lwb_hv_done:
                movb $13, (%r13); incq %r13
                movb $10, (%r13); incq %r13
                incq %r14
                jmp .Lwb_hdrs
            .Lwb_hdrs_done:
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

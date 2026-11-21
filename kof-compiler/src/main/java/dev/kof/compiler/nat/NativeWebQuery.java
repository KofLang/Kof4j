package dev.kof.compiler.nat;

/**
 * kof.web server x86-64 (WEB002) — a QUERY STRING da request.
 *
 * <p>O contrato é o do JVM ({@code JvmWebCoreRuntime.query}) e o do JS
 * ({@code JsRuntimeUiWeb.kofWebQuery}): o query string é dividido por
 * {@code &}; só um par com {@code =} casa; a CHAVE é comparada CRUA e o
 * VALOR é percent-decodificado. Ausente = null (o mesmo null do JVM — o
 * helper de paginação distingue "ausente" de "vazio").
 *
 * <p>§1159 (21/11): o path da request é separado do query — o roteamento casa
 * o path SEM o {@code ?...} (antes, {@code GET /list?page=2} dava 404) e
 * {@code path()} devolve o path sem o query (o mesmo contrato do
 * {@code readRequest} do JVM, que faz {@code path = fullPath.substring(0, q)}).
 */
public final class NativeWebQuery {

    private NativeWebQuery() {}

    static String source() {
        return """
            # ------------------------------------------------------------------
            # §1159 (21/11): a QUERY STRING da request corrente.
            # .Lweb_last_query = os BYTES do query (o path NÃO o inclui);
            # .Lweb_last_querylen = o comprimento (0 = sem query).
            # ------------------------------------------------------------------
            .section .bss
            .Lweb_last_query: .space 512
            .Lweb_last_querylen: .quad 0

            .section .text

            # ------------------------------------------------------------------
            # kof_web_split_query(rdi = path_chars, rsi = path_len)
            #   -> rax = o comprimento do PATH SEM o query string.
            # O query da request corrente é copiado para .Lweb_last_query
            # (cap 511). Sem '?' no path -> o len do query vai a 0 (reset por
            # request: a próxima request sem query NÃO herda a anterior).
            # ------------------------------------------------------------------
            .globl kof_web_split_query
            .type kof_web_split_query, @function
            kof_web_split_query:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx            # path chars
                movq %rsi, %r12            # path len (com o query)
                xorq %r13, %r13            # i
                movq %r12, %r14            # path len SEM o query
            .Lwsq_scan:
                cmpq %r12, %r13
                jae .Lwsq_none             # sem '?' no path
                movzbl (%rbx,%r13), %eax
                cmpb $63, %al              # '?'
                je .Lwsq_found
                incq %r13
                jmp .Lwsq_scan
            .Lwsq_none:
                movq $0, .Lweb_last_querylen(%rip)
                jmp .Lwsq_ret
            .Lwsq_found:
                movq %r13, %r14            # path len SEM o query
                # copia o query (depois do '?' até o fim do path)
                leaq 1(%rbx,%r13), %rsi   # src
                movq %r12, %r15
                subq %r13, %r15
                decq %r15                  # qlen = pathlen - (i + 1)
                cmpq $511, %r15
                jbe .Lwsq_copy
                movl $511, %r15d
            .Lwsq_copy:
                movq %r15, .Lweb_last_querylen(%rip)
                leaq .Lweb_last_query(%rip), %rdi
                xorq %rcx, %rcx
            .Lwsq_copy_loop:
                cmpq %r15, %rcx
                jae .Lwsq_copy_done
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Lwsq_copy_loop
            .Lwsq_copy_done:
                movb $0, (%rdi,%rcx)
            .Lwsq_ret:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # kof_web_query(rdi = name kofstr) -> String? (0 = ausente)
            # §1159 (21/11): o par só casa se tiver '=' (eq > 0); a CHAVE é
            # comparada CRUA (o JVM compara `kv.substring(0, eq).equals(key)`);
            # o VALOR é percent-decodificado pelo MESMO runtime de
            # kof.encoding (kof_encoding_urlDecode) — o '%' sem 2 dígitos
            # fica literal (o JVM lança; divergência documentada).
            # ------------------------------------------------------------------
            .globl kof_web_query
            .type kof_web_query, @function
            kof_web_query:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx            # name kofstr
                movl 16(%rbx), %r15d       # name len
                leaq 24(%rbx), %r14        # name chars
                movq .Lweb_last_querylen(%rip), %r13
                testq %r13, %r13
                jz .Lwqv_null              # sem query
                leaq .Lweb_last_query(%rip), %r12
                xorq %r8, %r8              # i (offset no query)
            .Lwqv_pair:
                cmpq %r13, %r8
                jae .Lwqv_null             # o query acabou
                movq %r8, %r9              # fim do par: '&' ou o fim
            .Lwqv_sep:
                cmpq %r13, %r9
                jae .Lwqv_pair_end
                movzbl (%r12,%r9), %eax
                cmpb $38, %al              # '&'
                je .Lwqv_pair_end
                incq %r9
                jmp .Lwqv_sep
            .Lwqv_pair_end:
                # procura o '=' dentro do par [r8, r9)
                movq %r8, %r10
            .Lwqv_eq:
                cmpq %r9, %r10
                jae .Lwqv_next             # par sem '='
                movzbl (%r12,%r10), %eax
                cmpb $61, %al              # '='
                je .Lwqv_eq_found
                incq %r10
                jmp .Lwqv_eq
            .Lwqv_eq_found:
                # a chave precisa ter >= 1 caractere (eq > 0 no JVM)
                movq %r10, %r11
                subq %r8, %r11
                testq %r11, %r11
                jz .Lwqv_next
                cmpl %r15d, %r11d
                jne .Lwqv_next
                leaq (%r12,%r8), %rdx      # chave (CRUA)
                xorq %rcx, %rcx
            .Lwqv_cmp:
                cmpl %r15d, %ecx
                jae .Lwqv_hit
                movzbl (%r14,%rcx), %eax
                cmpb (%rdx,%rcx), %al
                jne .Lwqv_next
                incq %rcx
                jmp .Lwqv_cmp
            .Lwqv_hit:
                # valor = [eq+1, fim do par)
                leaq 1(%r12,%r10), %rdi
                movq %r9, %rsi
                subq %r10, %rsi
                decq %rsi
                call kof_string_from_literal
                # §1159 (21/11): o percent-decode é INLINE — chamar
                # kof_encoding_urlDecode daqui puxava a fatia de kof.encoding
                # (base64/sec) para TODO binário nativo, porque o runtime web
                # é emitido sempre e a poda de fatias lê o texto do programa
                # (medido: ArtifactSizeTest 176 syms no hello, teto 110). O
                # contrato é o MESMO: '%XX' -> byte; '%' sem 2 dígitos hex
                # fica literal (o JVM lança; divergência documentada).
                movq %rax, %rbx            # KofString do valor (in-place)
                movl 16(%rbx), %ecx        # inLen
                leaq 24(%rbx), %rsi        # src
                movq %rsi, %rdi            # dst (in-place: dst <= src)
                xorl %r8d, %r8d            # i
                xorl %r9d, %r9d            # out
            .Lwqv_dec:
                cmpl %ecx, %r8d
                jge .Lwqv_dec_done
                movzbl (%rsi,%r8), %eax
                cmpb $37, %al              # '%'
                jne .Lwqv_dec_lit
                movl %r8d, %edx
                addl $3, %edx
                cmpl %ecx, %edx
                jg .Lwqv_dec_lit           # faltam dígitos -> literal
                movzbl 1(%rsi,%r8), %r11d  # hi
                cmpl $48, %r11d
                jb .Lwqv_dec_lit
                cmpl $57, %r11d
                jbe .Lwqv_hi_dig
                orl $32, %r11d             # minúsculas
                cmpl $97, %r11d
                jb .Lwqv_dec_lit
                cmpl $102, %r11d
                ja .Lwqv_dec_lit
                subl $87, %r11d            # 'a' -> 10
                jmp .Lwqv_hi_ok
            .Lwqv_hi_dig:
                subl $48, %r11d
            .Lwqv_hi_ok:
                shll $4, %r11d
                movzbl 2(%rsi,%r8), %edx   # lo
                cmpl $48, %edx
                jb .Lwqv_dec_lit
                cmpl $57, %edx
                jbe .Lwqv_lo_dig
                orl $32, %edx
                cmpl $97, %edx
                jb .Lwqv_dec_lit
                cmpl $102, %edx
                ja .Lwqv_dec_lit
                subl $87, %edx
                jmp .Lwqv_lo_ok
            .Lwqv_lo_dig:
                subl $48, %edx
            .Lwqv_lo_ok:
                orl %edx, %r11d
                movb %r11b, (%rdi,%r9)
                incl %r9d
                addl $3, %r8d
                jmp .Lwqv_dec
            .Lwqv_dec_lit:
                movzbl (%rsi,%r8), %eax
                movb %al, (%rdi,%r9)
                incl %r9d
                incl %r8d
                jmp .Lwqv_dec
            .Lwqv_dec_done:
                movl %r9d, 16(%rbx)        # o novo comprimento
                movb $0, 24(%rbx,%r9)
                movq %rbx, %rax
                jmp .Lwqv_ret
            .Lwqv_next:
                leaq 1(%r9), %r8           # próximo par (depois do '&')
                jmp .Lwqv_pair
            .Lwqv_null:
                xorq %rax, %rax
            .Lwqv_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """;
    }
}

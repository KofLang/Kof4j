package dev.kof.compiler;

/**
 * kof.web — contexto da request (method/path/query/header/param) + match por segmentos
 * Extraído do NativeWebRuntime (merge planning-future \u2190 beta-0.3.0, WEB002 residual).
 */
final class NativeWebRequestContext {

    private NativeWebRequestContext() {}

    static String source() {
        return """
            .section .data

            .section .bss
            .Lweb_method_ptr: .quad 0
            .Lweb_method_len: .quad 0
            .Lweb_path_ptr:   .quad 0
            .Lweb_path_len:   .quad 0
            .Lweb_query_ptr:  .quad 0
            .Lweb_query_len:  .quad 0
            .Lweb_hdr_ptr:    .quad 0
            .Lweb_hdr_len:    .quad 0
            .Lweb_reqlen:     .quad 0
            .Lweb_params:     .space 512    # 16 slots * 32B {name_ptr,name_len,value_ptr,value_len}
            .Lweb_nparams:    .quad 0
            .Lweb_empty:      .byte 0

            .section .text
            # ------------------------------------------------------------------
            # kof_web_store_ctx(rdi=method_start, rsi=method_end, rdx=path_start,
            #                   rcx=path_end)
            # Guarda ponteiros/len de method/path/query/headers no reqbuf.
            # Preserva r9/r10 (handle_client reusa como method_start/end).
            # ------------------------------------------------------------------
            kof_web_store_ctx:
                pushq %r9
                pushq %r10
                movq %rdi, .Lweb_method_ptr(%rip)
                movq %rsi, %rax
                subq %rdi, %rax
                movq %rax, .Lweb_method_len(%rip)
                movq %rdx, .Lweb_path_ptr(%rip)
                # procura '?' em [rdx, rcx)
                movq %rdx, %r8
            .Lctx_q:
                cmpq %rcx, %r8
                jae .Lctx_none
                cmpb $'?', (%r8)
                je .Lctx_found
                incq %r8
                jmp .Lctx_q
            .Lctx_found:
                # path = [rdx, r8); query = [r8+1, rcx)
                movq %r8, %rax
                subq %rdx, %rax
                movq %rax, .Lweb_path_len(%rip)
                leaq 1(%r8), %rax
                movq %rax, .Lweb_query_ptr(%rip)
                movq %rcx, %rax
                subq %r8, %rax
                decq %rax
                movq %rax, .Lweb_query_len(%rip)
                jmp .Lctx_headers
            .Lctx_none:
                movq %rcx, %rax
                subq %rdx, %rax
                movq %rax, .Lweb_path_len(%rip)
                movq %rdx, .Lweb_query_ptr(%rip)
                movq $0, .Lweb_query_len(%rip)
            .Lctx_headers:
                # rcx = path_end. Acha o fim da request-line (CRLF) e depois o
                # separador de body (CRLFCRLF); bloco de headers fica no meio.
                movq .Lweb_reqlen(%rip), %r8
                leaq .Lweb_reqbuf(%rip), %r9
                addq %r9, %r8                    # req_end
                movq %rcx, %r9                   # cursor
            .Lctx_h1:
                cmpq %r8, %r9
                jae .Lctx_nohdr
                cmpb $13, (%r9)
                jne .Lctx_h1n
                cmpb $10, 1(%r9)
                je .Lctx_hstart
            .Lctx_h1n:
                incq %r9
                jmp .Lctx_h1
            .Lctx_hstart:
                leaq 2(%r9), %r9                 # inicio dos headers
                movq %r9, .Lweb_hdr_ptr(%rip)
                movq %r9, %r10
            .Lctx_h2:
                cmpq %r8, %r10
                jae .Lctx_hend
                cmpb $13, (%r10)
                jne .Lctx_h2n
                cmpb $10, 1(%r10)
                jne .Lctx_h2n
                cmpb $13, 2(%r10)
                jne .Lctx_h2n
                cmpb $10, 3(%r10)
                je .Lctx_hsep
            .Lctx_h2n:
                incq %r10
                jmp .Lctx_h2
            .Lctx_hsep:
                movq %r10, %rax
                subq %r9, %rax
                movq %rax, .Lweb_hdr_len(%rip)
                jmp .Lctx_ret
            .Lctx_hend:
                movq %r8, %rax
                subq %r9, %rax
                movq %rax, .Lweb_hdr_len(%rip)
                jmp .Lctx_ret
            .Lctx_nohdr:
                movq $0, .Lweb_hdr_ptr(%rip)
                movq $0, .Lweb_hdr_len(%rip)
            .Lctx_ret:
                popq %r10
                popq %r9
                ret

            # ------------------------------------------------------------------
            # kof_web_method() -> KofString (método HTTP da última request)
            # ------------------------------------------------------------------
            .globl kof_web_method
            .type kof_web_method, @function
            kof_web_method:
                movq .Lweb_method_ptr(%rip), %rdi
                movq .Lweb_method_len(%rip), %rsi
                call kof_string_from_literal
                ret

            # ------------------------------------------------------------------
            # kof_web_path() -> KofString (path da última request, sem query)
            # ------------------------------------------------------------------
            .globl kof_web_path
            .type kof_web_path, @function
            kof_web_path:
                movq .Lweb_path_ptr(%rip), %rdi
                movq .Lweb_path_len(%rip), %rsi
                call kof_string_from_literal
                ret

            # ------------------------------------------------------------------
            # kof_web_query(name KofString*) -> KofString (valor do par?name=v)
            # Retorna "" se o par não existir.
            # ------------------------------------------------------------------
            .globl kof_web_query
            .type kof_web_query, @function
            kof_web_query:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl 16(%rdi), %r12d         # name_len
                leaq 24(%rdi), %r13          # name_chars
                movq .Lweb_query_ptr(%rip), %rbx   # cur
                movq .Lweb_query_len(%rip), %r14
                addq %rbx, %r14              # end
            .Lq_loop:
                cmpq %r14, %rbx
                jae .Lq_miss
                movq %rbx, %r8               # scan pos
                movq $0, %r9                 # eq = 0 (não achado)
            .Lq_scan:
                cmpq %r14, %r8
                jae .Lq_part
                movb (%r8), %al
                cmpb $'&', %al
                je .Lq_part
                cmpb $'=', %al
                jne .Lq_scan_next
                cmpq $0, %r9
                jne .Lq_scan_next
                movq %r8, %r9                 # eq = r8 (primeiro '=')
            .Lq_scan_next:
                incq %r8
                jmp .Lq_scan
            .Lq_part:
                movq %r8, %r15                # pair_end
                cmpq $0, %r9
                je .Lq_noeq
                # com '=': key=[rbx,r9), value=[r9+1,r15)
                movq %r9, %rax
                subq %rbx, %rax               # key_len
                cmpl %eax, %r12d
                jne .Lq_advance
                xorq %r10, %r10
            .Lq_cmp_eq:
                cmpl %r10d, %r12d
                jae .Lq_eq_match
                movb (%r13,%r10), %al
                cmpb (%rbx,%r10), %al
                jne .Lq_advance
                incq %r10
                jmp .Lq_cmp_eq
            .Lq_eq_match:
                leaq 1(%r9), %rdi             # value_start
                movq %r15, %rax
                subq %rdi, %rax               # value_len
                movq %rax, %rsi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lq_noeq:
                # sem '=': key=[rbx,r15), value vazio
                movq %r15, %rax
                subq %rbx, %rax               # key_len
                cmpl %eax, %r12d
                jne .Lq_advance
                xorq %r10, %r10
            .Lq_cmp_noeq:
                cmpl %r10d, %r12d
                jae .Lq_empty_match
                movb (%r13,%r10), %al
                cmpb (%rbx,%r10), %al
                jne .Lq_advance
                incq %r10
                jmp .Lq_cmp_noeq
            .Lq_empty_match:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lq_advance:
                cmpq %r14, %r15
                jae .Lq_miss
                leaq 1(%r15), %rbx            # passa o '&'
                jmp .Lq_loop
            .Lq_miss:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # kof_web_header(name KofString*) -> KofString (valor do header; case-insensitive)
            # Retorna "" se o header não existir.
            # ------------------------------------------------------------------
            .globl kof_web_header
            .type kof_web_header, @function
            kof_web_header:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl 16(%rdi), %r12d         # name_len
                leaq 24(%rdi), %r13          # name_chars
                movq .Lweb_hdr_ptr(%rip), %rbx
                movq .Lweb_hdr_len(%rip), %r14
                addq %rbx, %r14              # hdr_end
            .Lhdr_loop:
                cmpq %r14, %rbx
                jae .Lhdr_miss
                movq %rbx, %r15              # scan do ':'
            .Lhdr_colon:
                cmpq %r14, %r15
                jae .Lhdr_miss
                cmpb $':', (%r15)
                je .Lhdr_colon_found
                cmpb $13, (%r15)             # CR antes de ':' -> linha sem colon
                je .Lhdr_next_line
                incq %r15
                jmp .Lhdr_colon
            .Lhdr_colon_found:
                # header name = [rbx, r15)
                movq %r15, %rax
                subq %rbx, %rax              # hname_len
                cmpl %eax, %r12d
                jne .Lhdr_next_line
                xorq %r9, %r9
            .Lhdr_cmp:
                cmpl %r12d, %r9d
                jae .Lhdr_match
                movb (%r13,%r9), %al         # name char -> lower
                cmpb $'A', %al
                jb .Lhdr_c1
                cmpb $'Z', %al
                ja .Lhdr_c1
                addb $32, %al
            .Lhdr_c1:
                movb %al, %r10b
                movb (%rbx,%r9), %al         # header char -> lower
                cmpb $'A', %al
                jb .Lhdr_c2
                cmpb $'Z', %al
                ja .Lhdr_c2
                addb $32, %al
            .Lhdr_c2:
                cmpb %r10b, %al
                jne .Lhdr_next_line
                incq %r9
                jmp .Lhdr_cmp
            .Lhdr_match:
                leaq 1(%r15), %r9            # cursor do valor
            .Lhdr_trim_l:
                cmpq %r14, %r9
                jae .Lhdr_empty
                cmpb $' ', (%r9)
                jne .Lhdr_vstart
                incq %r9
                jmp .Lhdr_trim_l
            .Lhdr_vstart:
                movq %r9, %rdi               # value_start
                movq %r9, %rsi
.Lhdr_vend:
                cmpq %r14, %rsi
                jae .Lhdr_vdone
                cmpb $13, (%rsi)
                je .Lhdr_vdone
                incq %rsi
                jmp .Lhdr_vend
            .Lhdr_vdone:
            .Lhdr_trim_r:
                cmpq %rdi, %rsi
                jbe .Lhdr_vfinal
                cmpb $' ', -1(%rsi)
                jne .Lhdr_vfinal
                decq %rsi
                jmp .Lhdr_trim_r
            .Lhdr_vfinal:
                subq %rdi, %rsi              # value_len
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lhdr_empty:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lhdr_next_line:
                movq %rbx, %r15
            .Lhdr_nl:
                cmpq %r14, %r15
                jae .Lhdr_miss
                cmpb $10, (%r15)
                je .Lhdr_nldone
                incq %r15
                jmp .Lhdr_nl
            .Lhdr_nldone:
                leaq 1(%r15), %rbx
                jmp .Lhdr_loop
            .Lhdr_miss:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # kof_web_path_match(rdi=route_path KofString*, rsi=req_path ptr,
            #                    rdx=req_path len) -> rax (1 match / 0 não)
            # Match por segmentos; extrai parâmetros ':name' para .Lweb_params.
            # ------------------------------------------------------------------
            kof_web_path_match:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq $0, .Lweb_nparams(%rip)
                movl 16(%rdi), %eax
                leaq 24(%rdi), %rbx           # rcur
                leaq (%rbx,%rax), %r12        # route_end
                movq %rsi, %r13               # pcur
                leaq (%r13,%rdx), %r14        # req_end
            .Lpm_seg:
                cmpq %r12, %rbx
                jae .Lpm_route_done
                movq %rbx, %r15               # rseg scan
            .Lpm_rscan:
                cmpq %r12, %r15
                jae .Lpm_rseg_done
                cmpb $'/', (%r15)
                je .Lpm_rseg_done
                incq %r15
                jmp .Lpm_rscan
            .Lpm_rseg_done:
                movb (%rbx), %al
                cmpb $':', %al
                je .Lpm_seg_param
                # literal: requisição segmento [r13, r8)
                movq %r13, %r8
            .Lpm_pscan:
                cmpq %r14, %r8
                jae .Lpm_pseg_done
                cmpb $'/', (%r8)
                je .Lpm_pseg_done
                incq %r8
                jmp .Lpm_pscan
            .Lpm_pseg_done:
                movq %r15, %rax
                subq %rbx, %rax               # rseg_len
                movq %r8, %rcx
                subq %r13, %rcx               # pseg_len
                cmpq %rax, %rcx
                jne .Lpm_miss
                xorq %r9, %r9
            .Lpm_cmp:
                cmpq %rax, %r9
                jae .Lpm_advance
                movb (%rbx,%r9), %dl
                cmpb (%r13,%r9), %dl
                jne .Lpm_miss
                incq %r9
                jmp .Lpm_cmp
            .Lpm_seg_param:
                movq %r13, %r8
            .Lpm_pscan2:
                cmpq %r14, %r8
                jae .Lpm_pseg2_done
                cmpb $'/', (%r8)
                je .Lpm_pseg2_done
                incq %r8
                jmp .Lpm_pscan2
            .Lpm_pseg2_done:
                # store: name=[rbx+1,r15), value=[r13,r8)
                movq .Lweb_nparams(%rip), %r9
                cmpq $16, %r9
                jae .Lpm_store_skip
                imulq $32, %r9, %r9
                leaq .Lweb_params(%rip), %r10
                addq %r9, %r10
                leaq 1(%rbx), %rax
                movq %rax, 0(%r10)            # name ptr
                movq %r15, %rax
                subq %rbx, %rax
                decq %rax                     # name_len
                movq %rax, 8(%r10)
                movq %r13, 16(%r10)           # value ptr
                movq %r8, %rax
                subq %r13, %rax               # value_len
                movq %rax, 24(%r10)
                movq .Lweb_nparams(%rip), %r9
                incq %r9
                movq %r9, .Lweb_nparams(%rip)
            .Lpm_store_skip:
                movq %r8, %r13
                jmp .Lpm_advance_seg
            .Lpm_advance:
                movq %r8, %r13
            .Lpm_advance_seg:
                movq %r15, %rbx
                cmpq %r12, %rbx
                jae .Lpm_r_adv_done
                incq %rbx                     # pula '/'
            .Lpm_r_adv_done:
                cmpq %r14, %r13
                jae .Lpm_p_adv_done
                incq %r13                     # pula '/'
            .Lpm_p_adv_done:
                jmp .Lpm_seg
            .Lpm_route_done:
                cmpq %r14, %r13
                jne .Lpm_miss
                movq $1, %rax
                jmp .Lpm_ret
            .Lpm_miss:
                xorq %rax, %rax
            .Lpm_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ------------------------------------------------------------------
            # kof_web_param(name KofString*) -> KofString (valor do path param ':name')
            # Retorna "" se o parâmetro não existir.
            # ------------------------------------------------------------------
            .globl kof_web_param
            .type kof_web_param, @function
            kof_web_param:
                movl 16(%rdi), %r9d           # name_len
                leaq 24(%rdi), %r10           # name_chars
                movq .Lweb_nparams(%rip), %r11
                xorq %rcx, %rcx
            .Lparam_lookup:
                cmpq %r11, %rcx
                jae .Lparam_miss
                imulq $32, %rcx, %rdx
                leaq .Lweb_params(%rip), %rax
                addq %rdx, %rax
                cmpq %r9, 8(%rax)             # compara name_len
                jne .Lparam_next
                movq 0(%rax), %rsi            # slot name ptr
                xorq %r8, %r8
            .Lparam_cmp:
                cmpq %r9, %r8
                jae .Lparam_match
                movb (%rsi,%r8), %dl
                cmpb (%r10,%r8), %dl
                jne .Lparam_next
                incq %r8
                jmp .Lparam_cmp
            .Lparam_match:
                movq 16(%rax), %rdi
                movq 24(%rax), %rsi
                call kof_string_from_literal
                ret
            .Lparam_next:
                incq %rcx
                jmp .Lparam_lookup
            .Lparam_miss:
                leaq .Lweb_empty(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                ret

            # ------------------------------------------------------------------
            """;
    }
}

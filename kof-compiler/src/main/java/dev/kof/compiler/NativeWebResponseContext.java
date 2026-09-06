package dev.kof.compiler;

/**
 * kof.web — resposta customizável (status/headerSet) + status text
 */
final class NativeWebResponseContext {

    private NativeWebResponseContext() {}

    static String source() {
        return """
            .section .data
            .Lweb_st_ok: .asciz "OK"
            .Lweb_st_created: .asciz "Created"
            .Lweb_st_accepted: .asciz "Accepted"
            .Lweb_st_nocontent: .asciz "No Content"
            .Lweb_st_moved: .asciz "Moved Permanently"
            .Lweb_st_found: .asciz "Found"
            .Lweb_st_notmodified: .asciz "Not Modified"
            .Lweb_st_badreq: .asciz "Bad Request"
            .Lweb_st_unauth: .asciz "Unauthorized"
            .Lweb_st_forbidden: .asciz "Forbidden"
            .Lweb_st_notfound: .asciz "Not Found"
            .Lweb_st_conflict: .asciz "Conflict"
            .Lweb_st_iserr: .asciz "Internal Server Error"
            .Lweb_st_badgw: .asciz "Bad Gateway"
            .Lweb_st_unavail: .asciz "Service Unavailable"

            .section .bss
            .Lweb_status:     .quad 0     # status da resposta (reset p/ 200 a cada request)
            .Lweb_resp_hdrs:  .space 64    # 4 slots * 16B {name_ptr,value_ptr}
            .Lweb_nresphdr:   .quad 0

            .section .text
            # ------------------------------------------------------------------
            # kof_web_status(rdi=code Int, rsi=body String) -> String (o body)
            # Define o status da resposta; o writer usa .Lweb_status.
            # ------------------------------------------------------------------
            .globl kof_web_status
            .type kof_web_status, @function
            kof_web_status:
                movl %edi, .Lweb_status(%rip)
                movq %rsi, %rax
                ret

            # ------------------------------------------------------------------
            # kof_web_header_set(rdi=name String, rsi=value String) -> String
            # Adiciona um header de resposta; reusado pelo writer.
            # ------------------------------------------------------------------
            .globl kof_web_header_set
            .type kof_web_header_set, @function
            kof_web_header_set:
                movq .Lweb_nresphdr(%rip), %rax
                cmpq $4, %rax
                jae .Lkhs_done
                imulq $16, %rax, %rax
                leaq .Lweb_resp_hdrs(%rip), %rcx
                addq %rax, %rcx
                movq %rdi, 0(%rcx)
                movq %rsi, 8(%rcx)
                movq .Lweb_nresphdr(%rip), %rax
                incq %rax
                movq %rax, .Lweb_nresphdr(%rip)
            .Lkhs_done:
                movq %rsi, %rax
                ret

            # ------------------------------------------------------------------
            # kof_web_status_text(rdi=code Int) -> cstr (texto de status, NUL)
            # ------------------------------------------------------------------
            kof_web_status_text:
                movl %edi, %eax
                cmpl $200, %eax
                je .Lst_ok
                cmpl $201, %eax
                je .Lst_created
                cmpl $202, %eax
                je .Lst_accepted
                cmpl $204, %eax
                je .Lst_nocontent
                cmpl $301, %eax
                je .Lst_moved
                cmpl $302, %eax
                je .Lst_found
                cmpl $304, %eax
                je .Lst_notmodified
                cmpl $400, %eax
                je .Lst_badreq
                cmpl $401, %eax
                je .Lst_unauth
                cmpl $403, %eax
                je .Lst_forbidden
                cmpl $404, %eax
                je .Lst_notfound
                cmpl $409, %eax
                je .Lst_conflict
                cmpl $500, %eax
                je .Lst_iserr
                cmpl $502, %eax
                je .Lst_badgw
                cmpl $503, %eax
                je .Lst_unavail
                leaq .Lweb_st_ok(%rip), %rax
                ret
            .Lst_ok:         leaq .Lweb_st_ok(%rip), %rax; ret
            .Lst_created:    leaq .Lweb_st_created(%rip), %rax; ret
            .Lst_accepted:   leaq .Lweb_st_accepted(%rip), %rax; ret
            .Lst_nocontent:  leaq .Lweb_st_nocontent(%rip), %rax; ret
            .Lst_moved:      leaq .Lweb_st_moved(%rip), %rax; ret
            .Lst_found:      leaq .Lweb_st_found(%rip), %rax; ret
            .Lst_notmodified: leaq .Lweb_st_notmodified(%rip), %rax; ret
            .Lst_badreq:     leaq .Lweb_st_badreq(%rip), %rax; ret
            .Lst_unauth:     leaq .Lweb_st_unauth(%rip), %rax; ret
            .Lst_forbidden:  leaq .Lweb_st_forbidden(%rip), %rax; ret
            .Lst_notfound:   leaq .Lweb_st_notfound(%rip), %rax; ret
            .Lst_conflict:   leaq .Lweb_st_conflict(%rip), %rax; ret
            .Lst_iserr:      leaq .Lweb_st_iserr(%rip), %rax; ret
            .Lst_badgw:      leaq .Lweb_st_badgw(%rip), %rax; ret
            .Lst_unavail:    leaq .Lweb_st_unavail(%rip), %rax; ret

            # ------------------------------------------------------------------
            """;
    }
}

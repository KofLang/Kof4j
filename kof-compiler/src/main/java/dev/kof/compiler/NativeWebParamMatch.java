package dev.kof.compiler;

/**
 * kof.web server x86-64 — match de path por segmentos (`:id`) + extração de
 * parâmetros (param(name)). Módulo separado (regra <=500).
 */
final class NativeWebParamMatch {

    private NativeWebParamMatch() {}

    static String source() {
        return """
            .section .text
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

            """;
    }
}

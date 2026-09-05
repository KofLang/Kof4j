package dev.kof.compiler;

/**
 * Emissão do ASM de utilidades JSON (kof_json_quote/kof_json_find_value) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeJsonUtils {

    private RuntimeJsonUtils() {}

    static void emitJsonQuote(StringBuilder sb) {
        sb.append("""
            .globl kof_json_quote
            .type kof_json_quote, @function
            kof_json_quote:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                xorl %r15d, %r15d           # src data ptr (0 = entrada nula)
                xorl %r14d, %r14d           # srclen
                testq %rdi, %rdi
                jz .Ljq_bound
                movl 16(%rdi), %r14d        # srclen
                leaq 24(%rdi), %r15         # src data
            .Ljq_bound:
                imulq $6, %r14, %rax        # pior caso: tudo unicode-escapado
                addq $30, %rax
                movq %rax, %rdi
                call kof_alloc              # r15/r14 sobrevivem (callee-saved)
                movq %rax, %r12             # bloco destino
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                leaq 24(%r12), %r13         # cursor
                testq %r15, %r15
                jnz .Ljq_have_src
                movb $110, (%r13)           # null
                movb $117, 1(%r13)
                movb $108, 2(%r13)
                movb $108, 3(%r13)
                addq $4, %r13
                jmp .Ljq_close
            .Ljq_have_src:
                movb $34, (%r13)            # abre aspas
                incq %r13
                xorq %rbx, %rbx             # i
            .Ljq_loop:
                cmpq %r14, %rbx
                jge .Ljq_close_str
                movzbl (%r15,%rbx), %eax
                cmpb $34, %al               # aspa dupla
                je .Ljq_e_q
                cmpb $92, %al               # barra invertida
                je .Ljq_e_bs
                cmpb $10, %al               # LF
                je .Ljq_e_nl
                cmpb $13, %al               # CR
                je .Ljq_e_cr
                cmpb $9, %al                # TAB
                je .Ljq_e_tb
                cmpb $32, %al               # < 32 -> unicode escape
                jb .Ljq_e_uni
                movb %al, (%r13)
                incq %r13
                jmp .Ljq_next
            .Ljq_e_q:
                movw $8796, (%r13)          # backslash+aspa: LE -> 5C 22
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_bs:
                movw $23644, (%r13)         # 2x backslash: LE -> 5C 5C
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_nl:
                movw $28252, (%r13)         # backslash+n: LE -> 5C 6E
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_cr:
                movw $29276, (%r13)         # backslash+r: LE -> 5C 72
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_tb:
                movw $29788, (%r13)         # backslash+t: LE -> 5C 74
                addq $2, %r13
                jmp .Ljq_next
            .Ljq_e_uni:
                movb $92, (%r13)            # backslash
                movb $117, 1(%r13)          # u
                movb $48, 2(%r13)           # 0
                movb $48, 3(%r13)           # 0
                movzbl (%r15,%rbx), %edx
                shrl $4, %edx
                andl $15, %edx
                cmpb $10, %dl
                jb .Ljq_uh1
                addb $39, %dl
                jmp .Ljq_uh2
            .Ljq_uh1:
                addb $48, %dl
            .Ljq_uh2:
                movb %dl, 4(%r13)
                movzbl (%r15,%rbx), %edx
                andl $15, %edx
                cmpb $10, %dl
                jb .Ljq_ul1
                addb $39, %dl
                jmp .Ljq_ul2
            .Ljq_ul1:
                addb $48, %dl
            .Ljq_ul2:
                movb %dl, 5(%r13)
                addq $6, %r13
                jmp .Ljq_next
            .Ljq_next:
                incq %rbx
                jmp .Ljq_loop
            .Ljq_close_str:
                movb $34, (%r13)
                incq %r13
            .Ljq_close:
                movq %r13, %rax
                subq %r12, %rax
                subq $24, %rax
                movl %eax, 16(%r12)
                movb $0, 24(%r12,%rax)
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    static void emitJsonFindValue(StringBuilder sb) {
        sb.append("""
            # ---- JSN002: find_value(json kstr@rbx, key kstr@r15) -------
            # Retorna slice bruto do valor ou string vazia.
            # Registradores: rbx=json base(fixo), r13=len(fixo),
            #   r14=scan ptr, r15=key data ptr(fixo),
            #   rsi=key len, rax/rdx/rcx/rdi=r8-r11 scratch.
            .globl kof_json_find_value
            .type kof_json_find_value, @function
            kof_json_find_value:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # json
                movl 16(%rbx), %r13d     # json len
                leaq 24(%rbx), %r12      # json data
                movl 16(%rsi), %r15d     # key len
                testl %r15d, %r15d
                jz .Jfv_empty
                addq $24, %rsi           # key data
                leaq (%r12,%r13), %rbx   # json end
                movq %r12, %r14          # i (offset)
            .Jfv_scan:
                cmpq %rbx, %r14
                jae .Jfv_empty
                cmpb $34, (%r14)
                jne .Jfv_adv
                leaq 1(%r14), %rdi       # inicio da string candidata
                movq %rdi, %r8
                xorq %r9, %r9            # klen
            .Jfv_strwalk:
                cmpq %rbx, %rdi
                jae .Jfv_adv
                movzbl (%rdi), %eax
                cmpb $92, %al            # escape
                je .Jfv_esc
                cmpb $34, %al
                je .Jfv_strdone
                incq %rdi
                incq %r9
                jmp .Jfv_strwalk
            .Jfv_esc:
                addq $2, %rdi
                addq $2, %r9
                jmp .Jfv_strwalk
            .Jfv_strdone:
                cmpq %r15, %r9
                jne .Jfv_nomatch
                xorq %rcx, %rcx
            .Jfv_kcmp:
                cmpq %r15, %rcx
                jge .Jfv_match
                movzbl (%r8,%rcx), %eax
                cmpb (%rsi,%rcx), %al
                jne .Jfv_nomatch
                incq %rcx
                jmp .Jfv_kcmp
            .Jfv_nomatch:
                incq %r14
                jmp .Jfv_scan
            .Jfv_adv:
                incq %r14
                jmp .Jfv_scan
            .Jfv_match:
                incq %rdi                # apos a aspa de fechamento
                movq %rdi, %r8
            .Jfv_ws1:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $32, %al
                je .Jfv_ws1n
                cmpb $9, %al
                je .Jfv_ws1n
                cmpb $10, %al
                je .Jfv_ws1n
                cmpb $13, %al
                jne .Jfv_colon
            .Jfv_ws1n:
                incq %r8
                jmp .Jfv_ws1
            .Jfv_colon:
                cmpb $58, (%r8)
                jne .Jfv_nomatch
                incq %r8
            .Jfv_ws2:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $32, %al
                je .Jfv_ws2n
                cmpb $9, %al
                je .Jfv_ws2n
                cmpb $10, %al
                je .Jfv_ws2n
                cmpb $13, %al
                jne .Jfv_value
            .Jfv_ws2n:
                incq %r8
                jmp .Jfv_ws2
            .Jfv_value:
                cmpb $34, (%r8)
                je .Jfv_vstr
                movq %r8, %rdi
            .Jfv_prim:
                cmpq %rbx, %r8
                jae .Jfv_primdone
                movzbl (%r8), %eax
                cmpb $44, %al
                je .Jfv_primdone
                cmpb $125, %al
                je .Jfv_primdone
                cmpb $93, %al
                je .Jfv_primdone
                incq %r8
                jmp .Jfv_prim
            .Jfv_primdone:
                movq %rdi, %rcx
                movq %r8, %rsi
                subq %rcx, %rsi
                jmp .Jfv_mk
            .Jfv_vstr:
                incq %r8
                movq %r8, %rdi
            .Jfv_vstrwalk:
                cmpq %rbx, %r8
                jae .Jfv_empty
                movzbl (%r8), %eax
                cmpb $92, %al
                je .Jfv_vscesc
                cmpb $34, %al
                je .Jfv_vstrdone
                incq %r8
                jmp .Jfv_vstrwalk
            .Jfv_vscesc:
                addq $2, %r8
                jmp .Jfv_vstrwalk
            .Jfv_vstrdone:
                movq %r8, %rsi
                subq %rdi, %rsi
            .Jfv_mk:
                call .Ljf_mkstr
                jmp .Jfv_exit
            .Jfv_empty:
                movl $25, %edi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl $0, 16(%rcx)
                movl $0, 20(%rcx)
                movb $0, 24(%rcx)
                movq %rcx, %rax
            .Jfv_exit:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .Ljf_mkstr:
                # rdi=src, rsi=len -> rax=KofString*
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx             # src
                movq %rsi, %r12             # len (r12 preservado pelo kof_alloc)
                leaq 25(%r12), %rdi
                call kof_alloc
                movq %rax, %rcx
                movl $1, 0(%rcx)
                movl $0, 4(%rcx)
                movq $0, 8(%rcx)
                movl %r12d, 16(%rcx)
                movl $0, 20(%rcx)
                xorq %rdx, %rdx
            .Ljf_mk_cpy:
                cmpq %r12, %rdx
                jge .Ljf_mk_nul
                movzbl (%rbx,%rdx), %eax
                movb %al, 24(%rcx,%rdx)
                incq %rdx
                jmp .Ljf_mk_cpy
            .Ljf_mk_nul:
                movb $0, 24(%rcx,%r12)
                movq %rcx, %rax
                popq %r12
                popq %rbx
                ret
            .Ljf_exit:
                addq $16, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

}
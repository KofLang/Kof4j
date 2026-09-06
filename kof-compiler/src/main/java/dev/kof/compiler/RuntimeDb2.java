package dev.kof.compiler;

/**
Emissão do ASM de db2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeDb2 {

    private RuntimeDb2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r12d, 16(%rbx)
                movl $0, 20(%rbx)
                movb $39, 24(%rbx)               # ' (abre)
                leaq 25(%rbx), %rdi
                movq %r13, %rsi
                movq %r15, %rdx
                call kof_memcpy
                leaq 23(%rbx,%r12), %rdi         # fecha em data[len-1] = 24+len-1
                movb $39, (%rdi)                 # ' (fecha)
                incq %rdi
                movb $0, (%rdi)                  # NUL em 24+len
                movq %rbx, %rax
                jmp .Ldb_rnd_done
            .Ldb_rnd_int:
                movl %edi, %edi
                call kof_int_to_string
                movq %rax, %rbx
            .Ldb_rnd_done:
                addq $4096, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_mysql_replace_q(rdi=sql, rsi=literal) → rax: troca o 1º '?'
            # por literal (buffer bruto). Sem '?': devolve sql inalterado.
            .globl kof_db_mysql_replace_q
            .type kof_db_mysql_replace_q, @function
            kof_db_mysql_replace_q:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $4096, %rsp
                movq %rdi, %rbx                 # sql
                movq %rsi, %r12                 # literal
                movl 16(%rbx), %r13d            # sql len
                leaq 24(%rbx), %r14             # sql data
                # acha o 1º '?'
                xorl %ecx, %ecx
            .Ldb_rq_scan:
                cmpl %r13d, %ecx
                jge .Ldb_rq_none
                cmpb $'?', (%r14,%rcx)
                jne .Ldb_rq_adv
                jmp .Ldb_rq_found
            .Ldb_rq_adv:
                incl %ecx
                jmp .Ldb_rq_scan
            .Ldb_rq_none:
                movq %rbx, %rax
                jmp .Ldb_rq_done
            .Ldb_rq_found:
                movl %ecx, %r15d                # idx
                movq %rsp, %r8                   # out
                # copia sql[0..idx)
                xorl %esi, %esi
            .Ldb_rq_c1:
                cmpl %r15d, %esi
                jge .Ldb_rq_c1_done
                movzbl (%r14,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c1
            .Ldb_rq_c1_done:
                # copia literal (forward)
                xorl %esi, %esi
            .Ldb_rq_c2:
                cmpl 16(%r12), %esi
                jge .Ldb_rq_c2_done
                movzbl 24(%r12,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c2
            .Ldb_rq_c2_done:
                # copia sql[idx+1..end)
                leal 1(%r15d), %esi
            .Ldb_rq_c3:
                cmpl %r13d, %esi
                jge .Ldb_rq_c3_done
                movzbl (%r14,%rsi), %eax
                movb %al, (%r8)
                incq %r8
                incl %esi
                jmp .Ldb_rq_c3
            .Ldb_rq_c3_done:
                movq %r8, %r13
                movq %rsp, %r14
                subq %r14, %r13                 # novo len
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl %r13d, 16(%rbx)
                movl $0, 20(%rbx)
                leaq 24(%rbx), %rdi
                movq %rsp, %rsi
                movq %r13, %rdx
                call kof_memcpy
                movq %rbx, %rax
            .Ldb_rq_done:
                addq $4096, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_mysql_reset(rdi=fd): zera o estado do reader de pacotes.
            .globl kof_db_mysql_reset
            .type kof_db_mysql_reset, @function
            kof_db_mysql_reset:
                movq %rdi, .Ldb_mysql_fd(%rip)
                leaq .Ldb_mysql_buf(%rip), %rax
                movq %rax, .Ldb_mysql_ppos(%rip)
                movq %rax, .Ldb_mysql_pend(%rip)
                ret

            # kof_db_mysql_next: lê o PRÓXIMO pacote do stream (buf interno).
            # rsi = ponteiro do payload (buf+4 do pacote), rax = len do payload.
            # rax = 0 em fim de stream / erro. Clobbers rax rsi rdx rcx r8 r9
            # r10 r11 (leaf: sem call interno além de kof_net_read).
            .globl kof_db_mysql_next
            .type kof_db_mysql_next, @function
            kof_db_mysql_next:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq .Ldb_mysql_ppos(%rip), %r12   # start do pacote não lido
                movq .Ldb_mysql_pend(%rip), %rbx   # fim dos dados válidos
                cmpq %rbx, %r12
                jb .Ldb_mynxt_extract
            .Ldb_mynxt_read:
                movq .Ldb_mysql_fd(%rip), %rdi
                leaq .Ldb_mysql_buf(%rip), %rsi
                movl $16384, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_mynxt_fail
                leaq .Ldb_mysql_buf(%rip), %r12            # ppos = inicio do buf
                leaq .Ldb_mysql_buf(%rip), %rbx
                addq %rax, %rbx                             # pend = buf + rlen
                movq %rbx, .Ldb_mysql_pend(%rip)
                jmp .Ldb_mynxt_extract
            .Ldb_mynxt_extract:
                movzbl (%r12), %eax
                movzbl 1(%r12), %ecx
                shll $8, %ecx
                orl %ecx, %eax
                movzbl 2(%r12), %ecx
                shll $16, %ecx
                orl %ecx, %eax
                cmpl $0xFFFFFF, %eax
                je .Ldb_mynxt_fail                 # chunk de 16MB — fora de escopo
                leaq 4(%r12), %rsi                 # payload
                leaq .Ldb_mysql_buf(%rip), %r13
                addq $16384, %r13                  # buf end
                addq $4, %r12                      # pula o header
                addq %rax, %r12                    # + payload
                cmpq %r13, %r12
                ja .Ldb_mynxt_fail
                movq %r12, .Ldb_mysql_ppos(%rip)   # próximo não lido
                # return: rsi=payload, rax=len
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ldb_mynxt_fail:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # resolve "db<N>" → sqlite3* em rax (leaf: clobbera rsi/rdx/rax/rcx)
            kof_db_resolve:
                testq %rdi, %rdi
                jz .Ldb_res_null
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_res_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_res_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_res_parse
            .Ldb_res_done:
                decl %ecx
                movq .Ldb_slots(,%rcx,8), %rax
                ret
            .Ldb_res_null:
                xorl %eax, %eax
                ret

            # kof_db_type(id) → eax: 1=sqlite 2=mysql 3=oracle 4=mongo
            kof_db_type:
                testq %rdi, %rdi
                jz .Ldb_typ_null
                leaq 26(%rdi), %rsi
                xorl %ecx, %ecx
            .Ldb_typ_parse:
                movzbl (%rsi), %edx
                testb %dl, %dl
                je .Ldb_typ_done
                subl $'0', %edx
                imull $10, %ecx, %ecx
                addl %edx, %ecx
                incq %rsi
                jmp .Ldb_typ_parse
            .Ldb_typ_done:
                decl %ecx
                movzbl .Ldb_types(,%rcx,1), %eax
                ret
            .Ldb_typ_null:
                xorl %eax, %eax
                ret

            # kof_db_connect(url) — sqlite: ou mysql:// (user/pass = NULL)
            .globl kof_db_connect
            .type kof_db_connect, @function
            kof_db_connect:
                xorq %r14, %r14
                xorq %r15, %r15
                jmp kof_db_connect_inner

            # kof_db_connect2(url, user, pass)
            .globl kof_db_connect2
            .type kof_db_connect2, @function
            kof_db_connect2:
                movq %rsi, %r14
                movq %rdx, %r15

            kof_db_connect_inner:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                cmpb $'s', 24(%rbx)
                jne .Ldb_conn_maybe_mysql
                cmpb $'q', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'i', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'t', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $'e', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 30(%rbx)
                jne .Ldb_connect_bad
                leaq 31(%rbx), %rdi
                subq $40, %rsp
                movq %rsp, %rsi
                call sqlite3_open
                testl %eax, %eax
                jne .Ldb_connect_fail
                movq (%rsp), %r12
                addq $40, %rsp
                movl $1, %eax
                jmp .Ldb_connect_register
            .Ldb_conn_maybe_mysql:
                cmpb $'m', 24(%rbx)
                jne .Ldb_connect_bad
                cmpb $'y', 25(%rbx)
                jne .Ldb_connect_bad
                cmpb $'s', 26(%rbx)
                jne .Ldb_connect_bad
                cmpb $'q', 27(%rbx)
                jne .Ldb_connect_bad
                cmpb $'l', 28(%rbx)
                jne .Ldb_connect_bad
                cmpb $':', 29(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 30(%rbx)
                jne .Ldb_connect_bad
                cmpb $'/', 31(%rbx)
                jne .Ldb_connect_bad
                # mysql:// — parse [user[:pass]@]host[:port][/db] (also supports mysql://host:port/db)
                # Simple host:port/db parsing from after "mysql://"
                # If URL contains '@', treat host as after last '@' (fallback)
                leaq 24(%rbx), %r12
                leaq 8(%r12), %rsi
                movq %rsi, %r10
                xorq %r11, %r11
                movq %rsi, %rdx
            .Ldb_find_at_simple:
                movzbl (%rdx), %eax
                testb %al, %al
                jz .Ldb_at_simple_done
                cmpb $'@', %al
                jne .Ldb_at_simple_next
                movq %rdx, %r11
            .Ldb_at_simple_next:
                cmpb $'/', %al
                je .Ldb_at_simple_done
                incq %rdx
                jmp .Ldb_find_at_simple
            .Ldb_at_simple_done:
                testq %r11, %r11
                jz .Ldb_no_at_simple
                # user:pass@ — extrai userinfo e monta KofStrings (r14=user, r15=pass)
                leaq 8(%r12), %rdi
                subq $32, %rsp
                movq %rdi, 0(%rsp)       # u0
                leaq (%r11), %rdx        # len userinfo = @ - u0
                subq %rdi, %rdx
                movq %rdx, 24(%rsp)
                xorl %r8d, %r8d          # colon (0 se ausente)
            .Ldb_up_find_colon:
                cmpq %rdx, %r8
                jge .Ldb_up_find_colon_done
                cmpb $':', 0(%rdi,%r8)
                je .Ldb_up_find_colon_done
                incq %r8
                jmp .Ldb_up_find_colon
            .Ldb_up_find_colon_done:
                movq %r8, 8(%rsp)        # colon offset (0 se ausente)
                movq %r11, 16(%rsp)      # '@' (kof_alloc faz syscall: recarregar depois)
                # NUL no fim do user (muta o buffer da URL in-place)
                testq %r8, %r8
                jz .Ldb_up_term_at
                movb $0, (%rdi,%r8)
                jmp .Ldb_up_term_done
            .Ldb_up_term_at:
                movb $0, (%r11)
            .Ldb_up_term_done:
                # pass = [colon+1, @) -> r15
                testq %r8, %r8
                jz .Ldb_up_nopass
                leaq 1(%rdi,%r8), %rsi   # colon+1 (ptr real)
                movq %r11, %rdx
                subq %rsi, %rdx
                jle .Ldb_up_nopass
                leal 25(%rdx), %edi
                call kof_alloc
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movq 0(%rsp), %rsi
                movq 8(%rsp), %r8
                leaq 1(%rsi,%r8), %rsi   # colon+1
                movq 16(%rsp), %rdx
                subq %rsi, %rdx
                movl %edx, 16(%rax)
                movl $0, 20(%rax)
                xorl %ecx, %ecx
            .Ldb_up_pcopy:
                cmpl %edx, %ecx
                jge .Ldb_up_pcopy_done
                movzbl (%rsi,%rcx), %edi
                movb %dil, 24(%rax,%rcx)
                incl %ecx
                jmp .Ldb_up_pcopy
            .Ldb_up_pcopy_done:
                movb $0, 24(%rax,%rdx)
                movq %rax, %r15
            .Ldb_up_nopass:
                # user = [u0, colon ou @) -> r14
                movq 0(%rsp), %rsi
                movq 8(%rsp), %rdx
                testq %rdx, %rdx
                jz .Ldb_up_nocolon
                # com colon: len = colon; rdx precisa ser u0+colon p/ o subq abaixo
                addq %rsi, %rdx
                jmp .Ldb_up_ulen
            .Ldb_up_nocolon:
                movq %r11, %rdx            # '@' absoluto
            .Ldb_up_ulen:
                subq %rsi, %rdx
                jle .Ldb_up_nouser
                leal 25(%rdx), %edi
                movq %rdx, 24(%rsp)      # salva len (kof_alloc clobbera edx)
                call kof_alloc
                movq 24(%rsp), %rdx
                movl $1, 0(%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movq 0(%rsp), %rsi
                movl %edx, 16(%rax)
                movl $0, 20(%rax)
                xorl %ecx, %ecx
            .Ldb_up_ucopy:
                cmpl %edx, %ecx
                jge .Ldb_up_ucopy_done
                movzbl (%rsi,%rcx), %edi
                movb %dil, 24(%rax,%rcx)
                incl %ecx
                jmp .Ldb_up_ucopy
            .Ldb_up_ucopy_done:
                movb $0, 24(%rax,%rdx)
                movq %rax, %r14
            .Ldb_up_nouser:
                movq 16(%rsp), %r11   # syscall clobberou r11
                addq $32, %rsp
            """);
    }
}
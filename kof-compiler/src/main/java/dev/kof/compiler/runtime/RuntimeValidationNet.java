package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.validation rede (STDLIB S6a):
 * isIpv4 (dotted-quad, sem zero à esquerda, 0..255), isMac (6 hex, ':' ou
 * '-' consistente), isPort (1..65535). Só byte-scan, sem alocação.
 * Paridade byte-a-byte com JVM/JS/riscv (vetores Python-derivados).
 * Classificação usa subl + cmp UNSIGNED (ja/jbe) — mesmo idiom de BR digits.
 */
public final class RuntimeValidationNet {

    private RuntimeValidationNet() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.validation (STDLIB S6a) — rede ───────────────────────
            # kof_validation_isIpv4(rdi=str) -> Bool (0/1)
            .globl kof_validation_isIpv4
            .type kof_validation_isIpv4, @function
            kof_validation_isIpv4:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                testq %rdi, %rdi
                jz .Lv_net4_false
                movq %rdi, %rbx
                movl 16(%rbx), %r13d     # len
                xorl %r12d, %r12d        # i
                xorl %r14d, %r14d        # octets
                xorl %r15d, %r15d        # val
                movl $0, %ecx            # digits (volatile — chamadas? nenhuma aqui)
            .Lv_net4_loop:
                cmpl %r13d, %r12d
                jg .Lv_net4_false
                movl %r12d, %eax         # c: se i==len => '.'
                cmpl %r13d, %eax
                je .Lv_net4_dot
                movzbl 24(%rbx,%r12), %eax
                cmpl $57, %eax
                ja .Lv_net4_maybe
                cmpl $48, %eax
                jb .Lv_net4_maybe
                jmp .Lv_net4_digit
            .Lv_net4_maybe:
                cmpl $46, %eax
                je .Lv_net4_dot
                jmp .Lv_net4_false
            .Lv_net4_digit:
                # leading zero: digits==0 && c=='0' && i+1<len && s[i+1]!='.' => false
                testl %ecx, %ecx
                jnz .Lv_net4_val
                cmpl $48, %eax
                jne .Lv_net4_val
                leal 1(%r12), %edx
                cmpl %r13d, %edx
                jge .Lv_net4_val
                movzbl 24(%rbx,%rdx), %edx
                cmpl $46, %edx
                je .Lv_net4_val
                jmp .Lv_net4_false
            .Lv_net4_val:
                imull $10, %r15d, %r15d
                subl $48, %eax
                addl %eax, %r15d
                incl %ecx
                cmpl $3, %ecx
                ja .Lv_net4_false
                incl %r12d
                jmp .Lv_net4_loop
            .Lv_net4_dot:
                testl %ecx, %ecx
                jz .Lv_net4_false
                cmpl $255, %r15d
                ja .Lv_net4_false
                incl %r14d
                xorl %r15d, %r15d
                xorl %ecx, %ecx
                cmpl %r13d, %r12d       # dot virtual (i==len)? => finaliza
                je .Lv_net4_end
                incl %r12d
                jmp .Lv_net4_loop
            .Lv_net4_end:
                cmpl $4, %r14d
                jne .Lv_net4_false
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lv_net4_false:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isMac(rdi=str) -> Bool (0/1). len==17, sep : ou -.
            .globl kof_validation_isMac
            .type kof_validation_isMac, @function
            kof_validation_isMac:
                pushq %rbx
                pushq %r12
                testq %rdi, %rdi
                jz .Lv_mac_false
                movq %rdi, %rbx
                cmpl $17, 16(%rbx)
                jne .Lv_mac_false
                movzbl 26(%rbx), %r12d   # sep = bytes[2]
                cmpl $58, %r12d          # ':'
                je .Lv_mac_scan
                cmpl $45, %r12d          # '-'
                jne .Lv_mac_false
            .Lv_mac_scan:
                xorl %ecx, %ecx
            .Lv_mac_loop:
                cmpl $17, %ecx
                jge .Lv_mac_true
                movl %ecx, %eax
                incl %eax
                cltd
                movl $3, %r14d
                idivl %r14d              # (i+1)/3 ; edx=(i+1)%3
                movzbl 24(%rbx,%rcx), %eax
                testl %edx, %edx
                jnz .Lv_mac_hex
                cmpl %r12d, %eax         # separador
                jne .Lv_mac_false
                jmp .Lv_mac_next
            .Lv_mac_hex:
                cmpl $48, %eax
                jb .Lv_mac_false
                cmpl $57, %eax
                jbe .Lv_mac_next
                orl $32, %eax
                cmpl $97, %eax
                jb .Lv_mac_false
                cmpl $102, %eax
                ja .Lv_mac_false
            .Lv_mac_next:
                incl %ecx
                jmp .Lv_mac_loop
            .Lv_mac_true:
                movl $1, %eax
                jmp .Lv_mac_done
            .Lv_mac_false:
                xorl %eax, %eax
            .Lv_mac_done:
                popq %r12
                popq %rbx
                ret

            # kof_validation_isCreditCard(rdi=str) -> Bool. Luhn: extrai
            # dígitos p/ buf[19] (pilha); >19 => false; <12 => false; dobra
            # posições ímpares-contando-da-direita (v*2; v>9 => v-9); soma%10.
            .globl kof_validation_isCreditCard
            .type kof_validation_isCreditCard, @function
            kof_validation_isCreditCard:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $24, %rsp           # buf[19] em (%rsp)
                movq %rdi, %rbx
                movq %rsp, %r12          # buf
                xorl %r13d, %r13d        # n
                testq %rbx, %rbx
                jz .Lv_cc_false
                movl 16(%rbx), %ecx      # len
                xorl %edx, %edx          # i
            .Lv_cc_collect:
                cmpl %ecx, %edx
                jge .Lv_cc_ncheck
                movzbl 24(%rbx,%rdx), %eax
                incl %edx
                subl $48, %eax
                cmpl $9, %eax
                ja .Lv_cc_collect
                cmpl $19, %r13d
                jae .Lv_cc_false         # >19 dígitos
                movb %al, 0(%r12,%r13)
                incl %r13d
                jmp .Lv_cc_collect
            .Lv_cc_ncheck:
                cmpl $12, %r13d
                jb .Lv_cc_false
                # soma: j da esquerda; ímpar-contando-da-direita = (n-1-j)&1
                xorl %r8d, %r8d          # j
                xorl %r9d, %r9d          # sum
            .Lv_cc_sum:
                cmpl %r13d, %r8d
                jge .Lv_cc_mod
                movl %r13d, %eax
                subl %r8d, %eax
                decl %eax
                andl $1, %eax
                movzbl 0(%r12,%r8), %r10d   # v
                testl %eax, %eax
                jz .Lv_cc_add
                addl %r10d, %r10d        # v*2
                cmpl $9, %r10d
                jbe .Lv_cc_add
                subl $9, %r10d
            .Lv_cc_add:
                addl %r10d, %r9d
                incl %r8d
                jmp .Lv_cc_sum
            .Lv_cc_mod:
                movl %r9d, %eax
                xorl %edx, %edx
                movl $10, %ecx
                divl %ecx
                testl %edx, %edx
                jnz .Lv_cc_false
                movl $1, %eax
                jmp .Lv_cc_done
            .Lv_cc_false:
                xorl %eax, %eax
            .Lv_cc_done:
                addq $24, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isIpv6(rdi=str) -> Bool. Máquina (validada em
            # Python contra ipaddress, 30 casos): grupos 1..4 hex; ':' após
            # grupo; '::' no MÁXIMO uma vez (sentinela dbl=-1 = ausente);
            # sem '::' exige g==8; com '::' exige g<8. Forma MISTA
            # (::ffff:1.2.3.4) e zona (%eth0) NÃO aceitas na v1 — subconjunto
            # honesto de RFC 4291/5952, documentado em learn/39-stdlib.
            .globl kof_validation_isIpv6
            .type kof_validation_isIpv6, @function
            kof_validation_isIpv6:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                testq %rdi, %rdi
                jz .Lv_v6_false
                movq %rdi, %rbx
                movl 16(%rbx), %r13d     # len
                xorl %r12d, %r12d        # i
                xorl %r14d, %r14d        # g
                movl $-1, %r15d          # dbl
            .Lv_v6_loop:
                cmpl %r13d, %r12d
                jae .Lv_v6_end
                xorl %ecx, %ecx          # h
            .Lv_v6_hex:
                cmpl %r13d, %r12d
                jae .Lv_v6_gend
                movzbl 24(%rbx,%r12), %eax
                movl %eax, %r8d
                subl $48, %r8d
                cmpl $9, %r8d
                jbe .Lv_v6_ishex
                movl %eax, %r8d
                orl $32, %r8d
                subl $97, %r8d
                cmpl $5, %r8d
                ja .Lv_v6_gend
            .Lv_v6_ishex:
                cmpl $4, %ecx
                jae .Lv_v6_false         # 5º hex => grupo >4
                incl %ecx
                incl %r12d
                jmp .Lv_v6_hex
            .Lv_v6_gend:
                testl %ecx, %ecx
                jnz .Lv_v6_group
                # h==0: precisa de '::' exatamente aqui
                cmpl %r13d, %r12d
                jae .Lv_v6_false
                cmpb $58, 24(%rbx,%r12)
                jne .Lv_v6_false
                leal 1(%r12), %eax
                cmpl %r13d, %eax
                jge .Lv_v6_false
                cmpb $58, 24(%rbx,%rax)
                jne .Lv_v6_false
                cmpl $0, %r15d
                jge .Lv_v6_false         # segundo '::'
                movl %r12d, %r15d
                addl $2, %r12d
                jmp .Lv_v6_loop
            .Lv_v6_group:
                incl %r14d
                cmpl $8, %r14d
                ja .Lv_v6_false
                cmpl %r13d, %r12d
                jae .Lv_v6_end
                cmpb $58, 24(%rbx,%r12)
                jne .Lv_v6_false
                incl %r12d               # consome um ':'
                cmpl %r13d, %r12d
                jae .Lv_v6_false         # ':' terminal ("1:")
                cmpb $58, 24(%rbx,%r12)
                jne .Lv_v6_loop
                cmpl $0, %r15d
                jge .Lv_v6_false
                movl %r12d, %r15d
                incl %r12d               # consome o 2º ':'
                jmp .Lv_v6_loop
            .Lv_v6_end:
                cmpl $0, %r15d
                jl .Lv_v6_nodbl
                cmpl $8, %r14d
                jb .Lv_v6_true           # g < 8 com '::'
                jmp .Lv_v6_false
            .Lv_v6_nodbl:
                cmpl $8, %r14d
                je .Lv_v6_true
                jmp .Lv_v6_false
            .Lv_v6_true:
                movl $1, %eax
                jmp .Lv_v6_done
            .Lv_v6_false:
                xorl %eax, %eax
            .Lv_v6_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isDomain(rdi=str) -> Bool. MESMA máquina do
            # JVM/JS/riscv (oracle Python, 28 casos): varre labels; cada um
            # 1..63 [A-Za-z0-9-] sem '-' nas pontas; >=2 labels; TLD>=2 só
            # letras; total<=253; ponto final (label vazio) => false.
            .globl kof_validation_isDomain
            .type kof_validation_isDomain, @function
            kof_validation_isDomain:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                testq %rdi, %rdi
                jz .Lv_dm_false
                movl 16(%rdi), %r13d     # len
                testl %r13d, %r13d
                jz .Lv_dm_false
                cmpl $253, %r13d
                ja .Lv_dm_false
                movq %rdi, %rbx
                xorl %r12d, %r12d        # start
                xorl %r14d, %r14d        # labels
                xorl %ecx, %ecx          # i
            .Lv_dm_scan:
                cmpl %r13d, %ecx
                jge .Lv_dm_at
                cmpb $46, 24(%rbx,%rcx)  # '.'
                je .Lv_dm_at
                incl %ecx
                jmp .Lv_dm_scan
            .Lv_dm_at:
                movl %r12d, %r10d         # salva início deste label (vira o TLD se for o último)
                # label = [start, i)
                movl %ecx, %eax
                subl %r12d, %eax         # len do label
                testl %eax, %eax
                jz .Lv_dm_false          # vazio (ponto duplo/final/inicial)
                cmpl $63, %eax
                ja .Lv_dm_false
                movzbl 24(%rbx,%r12), %edx
                cmpl $45, %edx
                je .Lv_dm_false          # começa com '-'
                movl %ecx, %r8d
                decl %r8d
                movzbl 24(%rbx,%r8), %edx
                cmpl $45, %edx
                je .Lv_dm_false          # termina com '-'
                movl %r12d, %r9d
            .Lv_dm_chk:
                cmpl %ecx, %r9d
                jae .Lv_dm_labelok
                movzbl 24(%rbx,%r9), %edx
                cmpl $45, %edx
                je .Lv_dm_nx
                movl %edx, %eax
                subl $48, %eax
                cmpl $9, %eax
                jbe .Lv_dm_nx            # dígito
                movl %edx, %eax
                subl $97, %eax
                cmpl $25, %eax
                jbe .Lv_dm_nx            # minúscula
                movl %edx, %eax
                subl $65, %eax
                cmpl $25, %eax
                ja .Lv_dm_false          # não é maiúscula
            .Lv_dm_nx:
                incl %r9d
                jmp .Lv_dm_chk
            .Lv_dm_labelok:
                incl %r14d
                leal 1(%rcx), %r12d      # start = i+1
                incl %ecx                # pula o '.' (ou avança além do fim)
                cmpl %r13d, %ecx
                jle .Lv_dm_scan          # i<=len: virtual fim pega o último label
                jmp .Lv_dm_final
            .Lv_dm_final:
                cmpl $2, %r14d
                jb .Lv_dm_false
                # TLD = último label [dot+1, len): >=2 só letras
                movl %r13d, %r8d
                subl %r10d, %r8d
                cmpl $2, %r8d
                jb .Lv_dm_false          # tlen < 2
                movl %r10d, %r9d
            .Lv_dm_tld:
                cmpl %r13d, %r9d
                jae .Lv_dm_true
                movzbl 24(%rbx,%r9), %edx
                movl %edx, %eax
                subl $97, %eax
                cmpl $25, %eax
                jbe .Lv_dm_tldnx
                movl %edx, %eax
                subl $65, %eax
                cmpl $25, %eax
                ja .Lv_dm_false
            .Lv_dm_tldnx:
                incl %r9d
                jmp .Lv_dm_tld
            .Lv_dm_true:
                movl $1, %eax
                jmp .Lv_dm_done
            .Lv_dm_false:
                xorl %eax, %eax
            .Lv_dm_done:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isPort(edi=port) -> Bool (1..65535)
            .globl kof_validation_isPort
            .type kof_validation_isPort, @function
            kof_validation_isPort:
                movl %edi, %eax
                decl %eax                # port-1
                cmpl $65534, %eax        # (port-1) <= 65534 unsigned => 1..65535
                setbe %al
                movzbl %al, %eax
                ret
        """);
    }
}

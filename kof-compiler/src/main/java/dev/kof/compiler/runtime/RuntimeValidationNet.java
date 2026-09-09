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

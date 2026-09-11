package dev.kof.compiler.runtime;

/**
 * Emissao do ASM x86 de conversao String -> double/float
 * (kof_string_to_double/float) — bug 82: parser no contrato do JDK
 * (Double/Float.parseFloat(s.trim())): trim, +/-, digitos, um '.', expoente,
 * literais NaN/Infinity, falha -> excecao String (kof_throw_string). Mantissa
 * em int64 + uma divisao por 10^nfrac (rounding unico; "0.3"==0.3). LIMITE
 * documentado em docs/development/known-bugs.md §82: mantissa >19 digitos
 * LANCA (JVM parsearia); hex-float nao parseia. Split de RuntimeStringParse
 * (gate ≤500; REFACTOR-500) — mesma familia, corpos separados por alvo de
 * paridade (Int/Long inteiro x FP).
 */
public final class RuntimeStringParseFp {

    private RuntimeStringParseFp() {}

    public static void emitStringToDouble(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_double
            .type kof_string_to_double, @function
kof_string_to_double:
                call .Lkfs_pd
                ret
            .globl kof_string_to_float
            .type kof_string_to_float, @function
kof_string_to_float:
                call .Lkfs_pd
                cvtsd2ss %xmm0, %xmm0
                ret
            # .Lkfs_pd: String* (rdi) -> xmm0. Contrato = Double.parseDouble(s.trim())
            # do JDK (bug 82): trim (<=32), +/-, digitos, um '.', expoente e/E[+-]?digitos,
            # literais NaN/Infinity (case-sensitive), falha -> kof_throw_string. Mantissa
            # em int64 + UMA divisao por 10^nfrac (rounding unico -> "0.3"==0.3).
            # LIMITE (doc §82): >19 digitos de mantissa LANCA (JVM parseia); hex-float
            # (0x1p3) NAO parseia; exp |e|>320 -> 0/Infinity. Float: cvt do double.
.Lkfs_pd:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                testq %rdi, %rdi
                jz .Lpdd_throw
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorl %r14d, %r14d
                movl %r12d, %r15d
           .Lpdd_tl:
                cmpl %r15d, %r14d
                jae .Lpdd_vazio
                movzbl 24(%rbx,%r14), %eax
                cmpl $32, %eax
                ja .Lpdd_th0
                incl %r14d
                jmp .Lpdd_tl
           .Lpdd_th0:
                cmpl %r14d, %r15d
                jbe .Lpdd_vazio
                movl %r15d, %eax
                decl %eax
                movzbl 24(%rbx,%rax), %eax
                cmpl $32, %eax
                ja .Lpdd_lit
                decl %r15d
                jmp .Lpdd_th0
           .Lpdd_vazio:
                xorpd %xmm0, %xmm0
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
           .Lpdd_lit:
                # literais: NaN (3) / [+/-]Infinity (8/9/10). NaN: bit qNaN;
                # Infinity: +inf (NaN nao tem sinal).
                movl %r15d, %eax
                subl %r14d, %eax
                cmpl $3, %eax
                jne .Lpdd_lit_inf
                movzbl 24(%rbx,%r14), %eax
                cmpb $78, %al
                jne .Lpdd_num
                movzbl 25(%rbx,%r14), %eax
                cmpb $97, %al
                jne .Lpdd_num
                movzbl 26(%rbx,%r14), %eax
                cmpb $78, %al
                jne .Lpdd_num
                movsd .Lpdd_nan(%rip), %xmm0
                jmp .Lpdd_ret
           .Lpdd_lit_inf:
                movl %r15d, %eax
                subl %r14d, %eax
                movl $0, %r13d              # neg flag (so inf)
                cmpl $8, %eax
                je .Lpdd_inf_chk0
                cmpl $9, %eax
                je .Lpdd_inf_s1
                cmpl $10, %eax
                jne .Lpdd_num
                movzbl 24(%rbx,%r14), %eax
                cmpb $43, %al
                jne .Lpdd_throw
                incl %r14d
                jmp .Lpdd_inf_chk0
           .Lpdd_inf_s1:
                movzbl 24(%rbx,%r14), %eax
                cmpb $45, %al
                jne .Lpdd_throw
                movl $1, %r13d
                incl %r14d
           .Lpdd_inf_chk0:
                movzbl 24(%rbx,%r14), %eax
                cmpb $73, %al
                jne .Lpdd_throw
                movzbl 25(%rbx,%r14), %eax
                cmpb $110, %al
                jne .Lpdd_throw
                movzbl 26(%rbx,%r14), %eax
                cmpb $102, %al
                jne .Lpdd_throw
                movzbl 27(%rbx,%r14), %eax
                cmpb $105, %al
                jne .Lpdd_throw
                movzbl 28(%rbx,%r14), %eax
                cmpb $110, %al
                jne .Lpdd_throw
                movzbl 29(%rbx,%r14), %eax
                cmpb $105, %al
                jne .Lpdd_throw
                movzbl 30(%rbx,%r14), %eax
                cmpb $116, %al
                jne .Lpdd_throw
                movzbl 31(%rbx,%r14), %eax
                cmpb $121, %al
                jne .Lpdd_throw
                movsd .Lpdd_inf(%rip), %xmm0
                testl %r13d, %r13d
                jz .Lpdd_ret
                movsd .Lpdd_ninf(%rip), %xmm0
                jmp .Lpdd_ret
           .Lpdd_num:
                xorq %r13, %r13              # mantissa int64
                xorl %r10d, %r10d            # nInt
                xorl %r11d, %r11d            # nFrac
                xorl %ecx, %ecx              # nDig (r10+r11)
                xorl %edx, %edx              # flags: 1 neg, 2 dot
                movzbl 24(%rbx,%r14), %eax
                cmpl $45, %eax
                jne .Lpdd_nots
                orl $1, %edx
                incl %r14d
                jmp .Lpdd_p0
           .Lpdd_nots:
                cmpl $43, %eax               # '+'
                jne .Lpdd_p0
                incl %r14d
           .Lpdd_p0:
                # int part
           .Lpdd_iloop:
                cmpl %r15d, %r14d
                jae .Lpdd_dot
                movzbl 24(%rbx,%r14), %eax
                subl $48, %eax
                cmpl $9, %eax
                ja .Lpdd_dot
                cmpl $19, %ecx
                jae .Lpdd_throw
                imulq $10, %r13
                movslq %eax, %rax
                addq %rax, %r13
                incl %ecx
                incl %r10d
                incl %r14d
                jmp .Lpdd_iloop
           .Lpdd_dot:
                cmpl %r15d, %r14d
                jae .Lpdd_end
                movzbl 24(%rbx,%r14), %eax
                cmpb $46, %al
                jne .Lpdd_exp
                orl $2, %edx
                incl %r14d
           .Lpdd_floop:
                cmpl %r15d, %r14d
                jae .Lpdd_exp
                movzbl 24(%rbx,%r14), %eax
                subl $48, %eax
                cmpl $9, %eax
                ja .Lpdd_exp
                cmpl $19, %ecx
                jae .Lpdd_throw
                imulq $10, %r13
                movslq %eax, %rax
                addq %rax, %r13
                incl %ecx
                incl %r11d
                incl %r14d
                jmp .Lpdd_floop
           .Lpdd_exp:
                testl $2, %edx
                jz .Lpdd_exp_e
                testl %r11d, %r11d
                jnz .Lpdd_exp_e
                # "5." puro fim -> ok; "5.e3" -> expoente VALE (JDK: "5.e3"=5000.0,
                # medido no oraculo 10/09); sem NENHUM digito antes do ponto -> throw
                testl %ecx, %ecx
                jz .Lpdd_throw
                jmp .Lpdd_end
           .Lpdd_exp_e:
                testl %ecx, %ecx
                jnz .Lpdd_end
                # sem digitos ate aqui: ".5" e valido se o '.' veio antes? nao
                # (dot flag seria 2). Nada de digitos -> invalido
                jmp .Lpdd_throw
           .Lpdd_end:
                cmpl %r15d, %r14d
                jae .Lpdd_build
                movzbl 24(%rbx,%r14), %eax
                cmpb $101, %al
                je .Lpdd_expi
                cmpb $69, %al
                jne .Lpdd_throw
           .Lpdd_expi:
                incl %r14d
                xorl %r8d, %r8d              # exp neg flag
                cmpl %r15d, %r14d
                jae .Lpdd_throw
                movzbl 24(%rbx,%r14), %eax
                cmpb $45, %al
                jne .Lpdd_expp
                orl $1, %r8d
                incl %r14d
                jmp .Lpdd_expc
           .Lpdd_expp:
                cmpb $43, %al
                jne .Lpdd_expc
                incl %r14d
           .Lpdd_expc:
                xorq %r9, %r9
                movl $0, %esi                # tem digito
           .Lpdd_eloop:
                cmpl %r15d, %r14d
                jae .Lpdd_edone
                movzbl 24(%rbx,%r14), %eax
                subl $48, %eax
                cmpl $9, %eax
                ja .Lpdd_edone
                movl $1, %esi
                cmpq $1000000, %r9
                jae .Lpdd_ebig
                imulq $10, %r9
                movslq %eax, %rax
                addq %rax, %r9
                incl %r14d
                jmp .Lpdd_eloop
           .Lpdd_ebig:
                incl %r14d
                jmp .Lpdd_eloop
           .Lpdd_edone:
                testl %esi, %esi
                jz .Lpdd_throw               # "1e" sem digito
                cmpl %r15d, %r14d
                jne .Lpdd_throw              # lixo apos expoente
           .Lpdd_build:
                cmpq $320, %r9
                jae .Lpdd_hugeexp
                vcvtsi2sd %r13, %xmm0, %xmm0
                testl %r11d, %r11d
                jz .Lpdd_expapply
                divsd .Lpdd_p10(,%r11,8), %xmm0
           .Lpdd_expapply:
                testq %r9, %r9
                jz .Lpdd_sign
                movsd .Lpdd_ten(%rip), %xmm1
           .Lpdd_emul:
                testl $1, %r8d
                jnz .Lpdd_ediv
                mulsd %xmm1, %xmm0
                jmp .Lpdd_enext
           .Lpdd_ediv:
                divsd %xmm1, %xmm0
           .Lpdd_enext:
                decq %r9
                jnz .Lpdd_emul
                jmp .Lpdd_sign
           .Lpdd_sign:
                testl $1, %edx
                jz .Lpdd_ret
                movsd .Lpdd_mone(%rip), %xmm1
                mulsd %xmm1, %xmm0
           .Lpdd_ret:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
           .Lpdd_hugeexp:
                testl $1, %r8d
                jnz .Lpdd_hzero
                movsd .Lpdd_inf(%rip), %xmm0
                testl $1, %edx
                jz .Lpdd_ret
                movsd .Lpdd_ninf(%rip), %xmm0
                jmp .Lpdd_ret
           .Lpdd_hzero:
                xorpd %xmm0, %xmm0
                testl $1, %edx
                jz .Lpdd_ret
                movsd .Lpdd_nzero(%rip), %xmm0
                jmp .Lpdd_ret
           .Lpdd_throw:
                subq $8, %rsp
                leaq .Lpdd_msg(%rip), %rdi
                movl $14, %esi
                call kof_string_from_literal
                movq %rax, %rdi
                call kof_throw_string
            .section .data
            .Lpdd_msg: .asciz "Invalid number"
            .Lpdd_ten: .double 10.0
            .Lpdd_mone: .double -1.0
            .Lpdd_nan: .quad 0x7ff8000000000000
            .Lpdd_inf: .quad 0x7ff0000000000000
            .Lpdd_ninf: .quad 0xfff0000000000000
            .Lpdd_nzero: .quad 0x8000000000000000
            .Lpdd_p10:
                .double 1.0
                .double 10.0
                .double 100.0
                .double 1000.0
                .double 10000.0
                .double 100000.0
                .double 1000000.0
                .double 10000000.0
                .double 100000000.0
                .double 1000000000.0
                .double 10000000000.0
                .double 100000000000.0
                .double 1000000000000.0
                .double 10000000000000.0
                .double 100000000000000.0
                .double 1000000000000000.0
                .double 10000000000000000.0
                .double 100000000000000000.0
                .double 1000000000000000000.0
                .double 10000000000000000000.0
            .section .text
        """);
    }
    // (sem emitStringToFloat: kof_string_to_float e emitido no MESMO bloco do
    // double — .Lkfs_pd + cvtsd2ss — p/ nao duplicar a maquina de parse)

}

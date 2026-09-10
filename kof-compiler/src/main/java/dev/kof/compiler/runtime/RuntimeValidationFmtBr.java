package dev.kof.compiler.runtime;

/**
 * Formatador kof.validation BR (STDLIB S12b) no x86_64 — kof_validation_formatCnpj.
 * Arquivo NOVO (RuntimeValidationBr já em 455/500 — não coube o bloco de ~90
 * linhas). Usa kof_br_digits (emitido por RuntimeValidationBr, chamado antes).
 * NativeRuntime emite após RuntimeValidationBr.
 */
public final class RuntimeValidationFmtBr {

    private RuntimeValidationFmtBr() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_validation_formatCnpj(rdi=str) -> String (S12b)
            # 14 dígitos => NN.NNN.NNN/NNNN-NN (18 chars; canônico IBGE);
            # senão (incl. null) => original (no-op, nunca lança).
            # LAYOUT: tag=1@0 len@16 (Int32; 20=0!) bytes@24; kof_alloc preserva
            # callee-saved (rbx/r12 seguros sobre o call).
            .globl kof_validation_formatCnpj
            .type kof_validation_formatCnpj, @function
            kof_validation_formatCnpj:
                pushq %rbx
                pushq %r12
                subq $32, %rsp           # buf[16] em (%rsp); 2 pushes + 32 = 16-align
                movq %rdi, %rbx
                movq %rsp, %rsi
                call kof_br_digits       # eax = count (null => 0)
                cmpl $14, %eax
                jne .Lv_br_fcnpj_orig
                movl $48, %edi           # 18+25 = 43 -> 48 (16-aligned; kof_alloc cuida)
                call kof_alloc
                movq %rax, %r12
                movl $1, (%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movl $18, 16(%r12)
                movl $0, 20(%r12)
                # NN. (d0,d1,'.')
                movzbl 0(%rsp), %eax
                addl $48, %eax
                movb %al, 24(%r12)
                movzbl 1(%rsp), %eax
                addl $48, %eax
                movb %al, 25(%r12)
                movb $46, 26(%r12)       # '.'
                # NNN. (d2..d4)
                movzbl 2(%rsp), %eax
                addl $48, %eax
                movb %al, 27(%r12)
                movzbl 3(%rsp), %eax
                addl $48, %eax
                movb %al, 28(%r12)
                movzbl 4(%rsp), %eax
                addl $48, %eax
                movb %al, 29(%r12)
                movb $46, 30(%r12)       # '.'
                # NNN/ (d5..d7)
                movzbl 5(%rsp), %eax
                addl $48, %eax
                movb %al, 31(%r12)
                movzbl 6(%rsp), %eax
                addl $48, %eax
                movb %al, 32(%r12)
                movzbl 7(%rsp), %eax
                addl $48, %eax
                movb %al, 33(%r12)
                movb $47, 34(%r12)       # '/'
                # NNNN- (d8..d11)
                movzbl 8(%rsp), %eax
                addl $48, %eax
                movb %al, 35(%r12)
                movzbl 9(%rsp), %eax
                addl $48, %eax
                movb %al, 36(%r12)
                movzbl 10(%rsp), %eax
                addl $48, %eax
                movb %al, 37(%r12)
                movzbl 11(%rsp), %eax
                addl $48, %eax
                movb %al, 38(%r12)
                movb $45, 39(%r12)       # '-'
                # NN + NUL (d12,d13)
                movzbl 12(%rsp), %eax
                addl $48, %eax
                movb %al, 40(%r12)
                movzbl 13(%rsp), %eax
                addl $48, %eax
                movb %al, 41(%r12)
                movb $0, 42(%r12)
                movq %r12, %rax
                jmp .Lv_br_fcnpj_done
            .Lv_br_fcnpj_orig:
                movq %rbx, %rax
            .Lv_br_fcnpj_done:
                addq $32, %rsp
                popq %r12
                popq %rbx
                ret
        """);
    }
}

package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.validation documentos BR (STDLIB S5):
 * isCpf/isCnpj/isCep/isPis. Dígitos extraídos (não-dígitos ignorados),
 * dígito verificador módulo 11. Pesos como ARITMÉTICA (w = 9 - ((i+off)&7))
 * — sem tabela .rodata, para o port riscv/aarch ser direto (andi).
 * Paridade byte-a-byte com JVM/JS (vetores Python-derivados).
 */
public final class RuntimeValidationBr {

    private RuntimeValidationBr() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # helper: kof_br_digits(rdi=str, rsi=buf) -> eax = nº de dígitos
            # copia cada byte '0'-'9' como valor 0..9 em buf (buf >= 16B).
            .globl kof_br_digits
            .type kof_br_digits, @function
            kof_br_digits:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12          # buf
                xorl %r13d, %r13d        # count
                testq %rbx, %rbx
                jz .Lv_br_d_ret
                movl 16(%rbx), %ecx      # len
                xorl %edx, %edx          # i
            .Lv_br_d_loop:
                cmpl %ecx, %edx
                jge .Lv_br_d_ret
                movzbl 24(%rbx,%rdx), %eax
                incl %edx
                subl $48, %eax
                cmpl $9, %eax
                ja .Lv_br_d_loop
                movb %al, 0(%r12,%r13)
                incl %r13d
                jmp .Lv_br_d_loop
            .Lv_br_d_ret:
                movl %r13d, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isCpf(rdi=str) -> Bool (0/1)
            .globl kof_validation_isCpf
            .type kof_validation_isCpf, @function
            kof_validation_isCpf:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp           # buf[16] em (%rsp)
                movq %rdi, %rbx
                movq %rsp, %rsi
                call kof_br_digits       # eax = count
                cmpl $11, %eax
                jne .Lv_br_cpf_false
                # allSame?
                movzbl 0(%rsp), %r8d
                movl $1, %r9d
                movl $1, %ecx
            .Lv_br_cpf_same:
                cmpl $11, %ecx
                jge .Lv_br_cpf_samedone
                movzbl 0(%rsp,%rcx), %eax
                cmpl %r8d, %eax
                jne .Lv_br_cpf_notall
                incl %ecx
                jmp .Lv_br_cpf_same
            .Lv_br_cpf_notall:
                xorl %r9d, %r9d
            .Lv_br_cpf_samedone:
                testl %r9d, %r9d
                jnz .Lv_br_cpf_false
                # dv1 = sum d[i]*(10-i) i=0..8 mod 11
                xorl %r13d, %r13d        # acc
                xorl %r14d, %r14d        # i
            .Lv_br_cpf_dv1:
                cmpl $9, %r14d
                jge .Lv_br_cpf_dv1done
                movzbl 0(%rsp,%r14), %eax
                movl $10, %ecx
                subl %r14d, %ecx         # 10-i
                imull %ecx, %eax
                addl %eax, %r13d
                incl %r14d
                jmp .Lv_br_cpf_dv1
            .Lv_br_cpf_dv1done:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $11, %ecx
                divl %ecx                # edx = r
                movl %edx, %r13d
                cmpl $2, %r13d
                jb .Lv_br_cpf_dv1zero
                movl $11, %eax
                subl %r13d, %eax
                jmp .Lv_br_cpf_dv1cmp
            .Lv_br_cpf_dv1zero:
                xorl %eax, %eax
            .Lv_br_cpf_dv1cmp:
                movzbl 9(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lv_br_cpf_false
                # dv2 = sum d[i]*(11-i) i=0..9 mod 11
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lv_br_cpf_dv2:
                cmpl $10, %r14d
                jge .Lv_br_cpf_dv2done
                movzbl 0(%rsp,%r14), %eax
                movl $11, %ecx
                subl %r14d, %ecx
                imull %ecx, %eax
                addl %eax, %r13d
                incl %r14d
                jmp .Lv_br_cpf_dv2
            .Lv_br_cpf_dv2done:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $11, %ecx
                divl %ecx
                movl %edx, %r13d
                cmpl $2, %r13d
                jb .Lv_br_cpf_dv2zero
                movl $11, %eax
                subl %r13d, %eax
                jmp .Lv_br_cpf_dv2cmp
            .Lv_br_cpf_dv2zero:
                xorl %eax, %eax
            .Lv_br_cpf_dv2cmp:
                movzbl 10(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lv_br_cpf_false
                movl $1, %eax
                jmp .Lv_br_cpf_done
            .Lv_br_cpf_false:
                xorl %eax, %eax
            .Lv_br_cpf_done:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isCnpj(rdi=str) -> Bool
            # dv1 pesos w1[i]=9-((i+4)&7) i=0..11; dv2 w2[i]=9-((i+3)&7) i=0..12
            .globl kof_validation_isCnpj
            .type kof_validation_isCnpj, @function
            kof_validation_isCnpj:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movq %rsp, %rsi
                call kof_br_digits
                cmpl $14, %eax
                jne .Lv_br_cnpj_false
                # dv1
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lv_br_cnpj_dv1:
                cmpl $12, %r14d
                jge .Lv_br_cnpj_dv1done
                movzbl 0(%rsp,%r14), %eax
                movl %r14d, %ecx
                addl $4, %ecx
                andl $7, %ecx
                movl $9, %edx
                subl %ecx, %edx          # w1
                imull %edx, %eax
                addl %eax, %r13d
                incl %r14d
                jmp .Lv_br_cnpj_dv1
            .Lv_br_cnpj_dv1done:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $11, %ecx
                divl %ecx
                movl %edx, %r13d
                cmpl $2, %r13d
                jb .Lv_br_cnpj_dv1zero
                movl $11, %eax
                subl %r13d, %eax
                jmp .Lv_br_cnpj_dv1cmp
            .Lv_br_cnpj_dv1zero:
                xorl %eax, %eax
            .Lv_br_cnpj_dv1cmp:
                movzbl 12(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lv_br_cnpj_false
                # dv2
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lv_br_cnpj_dv2:
                cmpl $13, %r14d
                jge .Lv_br_cnpj_dv2done
                movzbl 0(%rsp,%r14), %eax
                movl %r14d, %ecx
                addl $3, %ecx
                andl $7, %ecx
                movl $9, %edx
                subl %ecx, %edx          # w2
                imull %edx, %eax
                addl %eax, %r13d
                incl %r14d
                jmp .Lv_br_cnpj_dv2
            .Lv_br_cnpj_dv2done:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $11, %ecx
                divl %ecx
                movl %edx, %r13d
                cmpl $2, %r13d
                jb .Lv_br_cnpj_dv2zero
                movl $11, %eax
                subl %r13d, %eax
                jmp .Lv_br_cnpj_dv2cmp
            .Lv_br_cnpj_dv2zero:
                xorl %eax, %eax
            .Lv_br_cnpj_dv2cmp:
                movzbl 13(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lv_br_cnpj_false
                movl $1, %eax
                jmp .Lv_br_cnpj_done
            .Lv_br_cnpj_false:
                xorl %eax, %eax
            .Lv_br_cnpj_done:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_validation_isCep(rdi=str) -> Bool (8 dígitos)
            .globl kof_validation_isCep
            .type kof_validation_isCep, @function
            kof_validation_isCep:
                pushq %rbx
                subq $32, %rsp
                movq %rdi, %rbx
                movq %rsp, %rsi
                call kof_br_digits
                cmpl $8, %eax
                je .Lv_br_cep_true
                xorl %eax, %eax
                jmp .Lv_br_cep_done
            .Lv_br_cep_true:
                movl $1, %eax
            .Lv_br_cep_done:
                addq $32, %rsp
                popq %rbx
                ret

            # kof_validation_isPis(rdi=str) -> Bool
            # pesos w[i]=9-((i+6)&7) i=0..9
            .globl kof_validation_isPis
            .type kof_validation_isPis, @function
            kof_validation_isPis:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $40, %rsp           # buf[16] em (%rsp); 4 pushes + 40 = 16-align
                movq %rdi, %rbx
                movq %rsp, %rsi
                call kof_br_digits
                cmpl $11, %eax
                jne .Lv_br_pis_false
                xorl %r13d, %r13d
                xorl %r14d, %r14d
            .Lv_br_pis_loop:
                cmpl $10, %r14d
                jge .Lv_br_pis_done
                movzbl 0(%rsp,%r14), %eax
                movl %r14d, %ecx
                addl $6, %ecx
                andl $7, %ecx
                movl $9, %edx
                subl %ecx, %edx          # w
                imull %edx, %eax
                addl %eax, %r13d
                incl %r14d
                jmp .Lv_br_pis_loop
            .Lv_br_pis_done:
                movl %r13d, %eax
                xorl %edx, %edx
                movl $11, %ecx
                divl %ecx
                movl %edx, %r13d
                cmpl $2, %r13d
                jb .Lv_br_pis_zero
                movl $11, %eax
                subl %r13d, %eax
                jmp .Lv_br_pis_cmp
            .Lv_br_pis_zero:
                xorl %eax, %eax
            .Lv_br_pis_cmp:
                movzbl 10(%rsp), %ecx
                cmpl %ecx, %eax
                jne .Lv_br_pis_false
                movl $1, %eax
                jmp .Lv_br_pis_end
            .Lv_br_pis_false:
                xorl %eax, %eax
            .Lv_br_pis_end:
                addq $40, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

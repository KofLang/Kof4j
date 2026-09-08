package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.encoding (STDLIB S4) hexEncode/hexDecode.
 * Transformação byte-a-byte UTF-8: paridade com JVM (getBytes UTF-8) e JS
 * (TextEncoder). null => retorna ponteiro original.
 * Sem TABELA de dados: dígito hex é aritmético (d<10 -> '0'+d, senao 'a'+d-10),
 * e decode é aritmético por faixa; isso evita seções .rodata e mantém o
 * tradutor riscv/aarch64 simples.
 */
public final class RuntimeEncoding {

    private RuntimeEncoding() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .section .text
            # kof_encoding_hexEncode(rdi=str) -> String (minúsculo)
            .globl kof_encoding_hexEncode
            .type kof_encoding_hexEncode, @function
            kof_encoding_hexEncode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # str
                testq %rbx, %rbx
                jz .Lv_enc_he_orig
                movl 16(%rbx), %r12d     # len (bytes UTF-8)
                leal 25(%r12,%r12), %edi # 2*len + 25
                call kof_alloc
                movq %rax, %r15          # novo
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                addl %r12d, %r12d        # outLen = 2*len
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                xorl %r13d, %r13d        # i
            .Lv_enc_he_loop:
                cmpl %r12d, %r13d
                jge .Lv_enc_he_term
                movl %r13d, %eax
                shrl $1, %eax
                movzbl 24(%rbx,%rax), %ecx   # byte b = s[i/2]
                testb $1, %r13b          # par? -> nibble ALTO
                jnz .Lv_enc_he_lo
                shrl $4, %ecx
                jmp .Lv_enc_he_put
            .Lv_enc_he_lo:
                andl $15, %ecx           # ímpar: nibble baixo
            .Lv_enc_he_put:
                # char = d<10 ? 48+d : 97+d-10  (sem tabela)
                cmpb $10, %cl
                jb .Lv_enc_he_d9
                leal 97(%ecx), %edx
                subl $10, %edx
                movb %dl, 24(%r15,%r13)
                jmp .Lv_enc_he_next
            .Lv_enc_he_d9:
                leal 48(%ecx), %edx
                movb %dl, 24(%r15,%r13)
            .Lv_enc_he_next:
                incl %r13d
                jmp .Lv_enc_he_loop
            .Lv_enc_he_term:
                movb $0, 24(%r15,%r12)
                movq %r15, %rax
                jmp .Lv_enc_he_done
            .Lv_enc_he_orig:
                movq %rbx, %rax
            .Lv_enc_he_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_encoding_hexDecode(rdi=str) -> String
            # cada 2 chars -> 1 byte; char fora de [0-9a-fA-F] => 0;
            # comprimento ímpar: último char é nibble alto (baixo=0).
            .globl kof_encoding_hexDecode
            .type kof_encoding_hexDecode, @function
            kof_encoding_hexDecode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_hd_null
                movl 16(%rbx), %r12d     # inLen
                movl %r12d, %r13d
                shrl $1, %r13d           # outLen = inLen/2
                movl %r12d, %eax
                andl $1, %eax
                addl %eax, %r13d         # +1 se ímpar
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r15          # novo
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r13d, 16(%r15)
                movl $0, 20(%r15)
                xorl %r14d, %r14d        # i (out index)
            .Lv_enc_hd_loop:
                cmpl %r13d, %r14d
                jge .Lv_enc_hd_term
                # hi = nib(s[2i])
                movl %r14d, %eax
                addl %eax, %eax          # 2i
                movzbl 24(%rbx,%rax), %edi
                call .Lv_enc_hd_nib
                shll $4, %eax
                movl %eax, %ecx          # hi<<4
                # lo: se existe s[2i+1], nib; senao 0
                movl %r14d, %eax
                addl %eax, %eax
                incl %eax                # 2i+1
                cmpl %r12d, %eax
                jge .Lv_enc_hd_merge
                movzbl 24(%rbx,%rax), %edi
                call .Lv_enc_hd_nib
                orl %eax, %ecx
            .Lv_enc_hd_merge:
                movb %cl, 24(%r15,%r14)
                incl %r14d
                jmp .Lv_enc_hd_loop
            .Lv_enc_hd_term:
                movb $0, 24(%r15,%r13)
                movq %r15, %rax
                jmp .Lv_enc_hd_done
            .Lv_enc_hd_null:
                xorl %eax, %eax
            .Lv_enc_hd_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            # sub-rotina: edi=char -> eax=nibble (0 se inválido).
            # Faixas ordenadas por threshold estrito (paridade com B10 riscv):
            # 48..57 num, 65..70 A-F, 97..102 a-f, resto 0.
            .Lv_enc_hd_nib:
                movl %edi, %eax
                cmpl $48, %eax
                jb .Lv_enc_hd_bad
                cmpl $58, %eax
                jb .Lv_enc_hd_num
                cmpl $65, %eax
                jb .Lv_enc_hd_bad
                cmpl $71, %eax
                jb .Lv_enc_hd_high
                cmpl $97, %eax
                jb .Lv_enc_hd_bad
                cmpl $103, %eax
                jb .Lv_enc_hd_low
                jmp .Lv_enc_hd_bad
            .Lv_enc_hd_num:
                subl $48, %eax
                ret
            .Lv_enc_hd_low:
                subl $87, %eax           # c-97+10
                ret
            .Lv_enc_hd_high:
                subl $55, %eax           # c-65+10
                ret
            .Lv_enc_hd_bad:
                xorl %eax, %eax
                ret
        """);
    }
}

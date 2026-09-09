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

            # ── S4.2 base64 (reusa kof_b64_*_internal do runtime crypto) ──
            # kof_encoding_base64Encode(rdi=str) -> String (com padding)
            .globl kof_encoding_base64Encode
            .type kof_encoding_base64Encode, @function
            kof_encoding_base64Encode:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_be_orig
                movl 16(%rbx), %r12d     # len
                # out = 4*((len+2)/3) + 25
                leal 2(%r12), %eax
                movl $3, %ecx
                cltd
                idivl %ecx
                shll $2, %eax
                addl $25, %eax
                movl %eax, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $0, 20(%r13)
                leaq 24(%r13), %rdi      # out buffer
                leaq 24(%rbx), %rsi      # bytes
                movl %r12d, %edx
                call kof_b64_encode_internal   # eax = bytes escritos
                movl %eax, 16(%r13)      # len
                movq %r13, %rax
                jmp .Lv_enc_be_done
            .Lv_enc_be_orig:
                movq %rbx, %rax
            .Lv_enc_be_done:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_encoding_base64Decode(rdi=str) -> String (tolerante)
            .globl kof_encoding_base64Decode
            .type kof_encoding_base64Decode, @function
            kof_encoding_base64Decode:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_bd_null
                movl 16(%rbx), %r12d     # inLen
                # out máx = inLen (nunca maior que a entrada) + 25
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $0, 20(%r13)
                leaq 24(%r13), %rdi
                leaq 24(%rbx), %rsi
                movl %r12d, %edx
                call kof_b64_decode_internal   # eax = nbytes
                movl %eax, 16(%r13)
                movb $0, 24(%r13,%rax)         # NUL (encode_internal já escreve o seu)
                movq %r13, %rax
                jmp .Lv_enc_bd_done
            .Lv_enc_bd_null:
                xorl %eax, %eax
            .Lv_enc_bd_done:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── S4.2b percent-encoding (RFC 3986, unreserved [A-Za-z0-9-_.~]) ──
            # Tudo inlined (sem sub-rotinas) para não arriscar clobber de caller-
            # saved entre call kof_alloc e o laço. hex MAIÚSCULO no encode.
            # kof_encoding_urlEncode(rdi=str) -> String
            .globl kof_encoding_urlEncode
            .type kof_encoding_urlEncode, @function
            kof_encoding_urlEncode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_ue_orig
                movl 16(%rbx), %r12d          # len
                leal 25(%r12,%r12,2), %edi    # 3*len + 25
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                xorl %r13d, %r13d             # i
                xorl %r14d, %r14d             # out pos
            .Lv_enc_ue_loop:
                cmpl %r12d, %r13d
                jge .Lv_enc_ue_term
                movzbl 24(%rbx,%r13), %eax    # c (eax é o único vivo até o store)
                # unreserved? usa ecx/rdx como temporários, NÃO toca eax
                movl %eax, %ecx
                subl $65, %ecx
                cmpl $25, %ecx
                jbe .Lv_enc_ue_keep
                movl %eax, %ecx
                subl $97, %ecx
                cmpl $25, %ecx
                jbe .Lv_enc_ue_keep
                movl %eax, %ecx
                subl $48, %ecx
                cmpl $9, %ecx
                jbe .Lv_enc_ue_keep
                cmpl $45, %eax
                je .Lv_enc_ue_keep
                cmpl $95, %eax
                je .Lv_enc_ue_keep
                cmpl $46, %eax
                je .Lv_enc_ue_keep
                cmpl $126, %eax
                je .Lv_enc_ue_keep
                # escape %XX (hi): d=eax>>4
                movb $37, 24(%r15,%r14)       # '%'
                incl %r14d
                movl %eax, %ecx
                shrl $4, %ecx
                andl $15, %ecx
                cmpl $10, %ecx
                jb .Lv_enc_ue_h9a
                leal 55(%ecx), %edx           # 'A'-10
                jmp .Lv_enc_ue_hputa
            .Lv_enc_ue_h9a:
                leal 48(%ecx), %edx
            .Lv_enc_ue_hputa:
                movb %dl, 24(%r15,%r14)
                incl %r14d
                # lo: d=eax&15 (re-read eax, ainda íntegro)
                movzbl 24(%rbx,%r13), %eax
                andl $15, %eax
                cmpl $10, %eax
                jb .Lv_enc_ue_h9b
                leal 55(%eax), %edx
                jmp .Lv_enc_ue_hputb
            .Lv_enc_ue_h9b:
                leal 48(%eax), %edx
            .Lv_enc_ue_hputb:
                movb %dl, 24(%r15,%r14)
                incl %r14d
                jmp .Lv_enc_ue_next
            .Lv_enc_ue_keep:
                movb %al, 24(%r15,%r14)
                incl %r14d
            .Lv_enc_ue_next:
                incl %r13d
                jmp .Lv_enc_ue_loop
            .Lv_enc_ue_term:
                movl %r14d, 16(%r15)
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                jmp .Lv_enc_ue_done
            .Lv_enc_ue_orig:
                movq %rbx, %rax
            .Lv_enc_ue_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_encoding_urlDecode(rdi=str) -> String (inlined, sem call interno)
            .globl kof_encoding_urlDecode
            .type kof_encoding_urlDecode, @function
            kof_encoding_urlDecode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_ud_null
                movl 16(%rbx), %r12d          # inLen
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                xorl %r13d, %r13d             # i
                xorl %r14d, %r14d             # out pos
            .Lv_enc_ud_loop:
                cmpl %r12d, %r13d
                jge .Lv_enc_ud_term
                movzbl 24(%rbx,%r13), %eax
                cmpl $37, %eax                # '%'?
                jne .Lv_enc_ud_lit
                # precisa de s[i+1], s[i+2]: i+2 < inLen  =>  i+3 <= inLen
                movl %r13d, %ecx
                addl $3, %ecx
                cmpl %r12d, %ecx
                jg .Lv_enc_ud_lit
                # hi nibble (charCode in rdx) — inline strict, inválido => jump lit
                movl %r13d, %ecx
                incl %ecx
                movzbl 24(%rbx,%rcx), %edx
                cmpl $48, %edx
                jb .Lv_enc_ud_lit
                cmpl $58, %edx
                jb .Lv_enc_ud_hnum
                cmpl $65, %edx
                jb .Lv_enc_ud_lit
                cmpl $71, %edx
                jb .Lv_enc_ud_hhigh
                cmpl $97, %edx
                jb .Lv_enc_ud_lit
                cmpl $103, %edx
                jae .Lv_enc_ud_lit
                subl $87, %edx
                jmp .Lv_enc_ud_hgot
            .Lv_enc_ud_hnum:
                subl $48, %edx
                jmp .Lv_enc_ud_hgot
            .Lv_enc_ud_hhigh:
                subl $55, %edx
            .Lv_enc_ud_hgot:
                movl %edx, %r8d               # hi (0..15)
                shll $4, %r8d
                # lo nibble s[i+2]
                movl %r13d, %ecx
                addl $2, %ecx
                movzbl 24(%rbx,%rcx), %edx
                cmpl $48, %edx
                jb .Lv_enc_ud_lit
                cmpl $58, %edx
                jb .Lv_enc_ud_lnum
                cmpl $65, %edx
                jb .Lv_enc_ud_lit
                cmpl $71, %edx
                jb .Lv_enc_ud_lhigh
                cmpl $97, %edx
                jb .Lv_enc_ud_lit
                cmpl $103, %edx
                jae .Lv_enc_ud_lit
                subl $87, %edx
                jmp .Lv_enc_ud_lgot
            .Lv_enc_ud_lnum:
                subl $48, %edx
                jmp .Lv_enc_ud_lgot
            .Lv_enc_ud_lhigh:
                subl $55, %edx
            .Lv_enc_ud_lgot:
                orl %edx, %r8d
                movb %r8b, 24(%r15,%r14)
                incl %r14d
                addl $3, %r13d
                jmp .Lv_enc_ud_loop
            .Lv_enc_ud_lit:
                movzbl 24(%rbx,%r13), %eax
                movb %al, 24(%r15,%r14)
                incl %r14d
                incl %r13d
                jmp .Lv_enc_ud_loop
            .Lv_enc_ud_term:
                movl %r14d, 16(%r15)
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                jmp .Lv_enc_ud_done
            .Lv_enc_ud_null:
                xorl %eax, %eax
            .Lv_enc_ud_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

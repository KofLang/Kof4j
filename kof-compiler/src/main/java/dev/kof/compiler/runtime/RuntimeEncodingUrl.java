package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.encoding urlEncode/urlDecode (STDLIB S4.2b).
 * Percent-encoding RFC 3986 byte-puro (unreserved [A-Za-z0-9-_.~]), hex
 * MAIÚSCULO, tudo inlined (sem sub-rotinas — lição: call clobbers
 * caller-saved). Extraído de RuntimeEncoding (gate ≤500); encadeado por
 * RuntimeEncoding.emit (ordem preservada; .s byte-idêntico).
 */
public final class RuntimeEncodingUrl {

    private RuntimeEncodingUrl() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
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

            # ── S4.2c base64url (reusa kof_b64url_*_internal do runtime JWT) ──
            # encode: SEM padding (o internal b64url não escreve '=');
            # decode: pré-substitui -_ => +/ num buffer e chama o decode b64
            # comum (tolerante, aceita os dois alfabetos + padding opcional —
            # mesma spec do JVM/JS).
            .globl kof_encoding_base64UrlEncode
            .type kof_encoding_base64UrlEncode, @function
            kof_encoding_base64UrlEncode:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_bue_orig
                movl 16(%rbx), %r12d
                # out máx = 4*((len+2)/3) + 25 (sem '=' mas reserva igual)
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
                leaq 24(%r13), %rdi
                leaq 24(%rbx), %rsi
                movl %r12d, %edx
                call kof_b64url_encode_internal   # eax = chars (sem padding)
                movl %eax, 16(%r13)
                movq %r13, %rax
                jmp .Lv_enc_bue_done
            .Lv_enc_bue_orig:
                movq %rbx, %rax
            .Lv_enc_bue_done:
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_encoding_base64UrlDecode
            .type kof_encoding_base64UrlDecode, @function
            kof_encoding_base64UrlDecode:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lv_enc_bud_null
                movl 16(%rbx), %r12d
                # buffer temporário pré-substituído: inLen bytes (allocação
                # temporária kof_alloc; nunca liberada — bump allocator, ok)
                movl %r12d, %edi
                call kof_alloc
                movq %rax, %r13          # tmp std-alphabet copy
                xorl %r14d, %r14d
            .Lv_enc_bud_pre:
                cmpl %r12d, %r14d
                jge .Lv_enc_bud_dec
                movzbl 24(%rbx,%r14), %eax
                cmpl $45, %eax           # '-' -> '+'
                jne .Lv_enc_bud_p1
                movb $43, %al
                jmp .Lv_enc_bud_put
            .Lv_enc_bud_p1:
                cmpl $95, %eax           # '_' -> '/'
                jne .Lv_enc_bud_put
                movb $47, %al
            .Lv_enc_bud_put:
                movb %al, (%r13,%r14)
                incl %r14d
                jmp .Lv_enc_bud_pre
            .Lv_enc_bud_dec:
                # decode b64 comum tolerante: out <= inLen
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r14          # novo (reusa r14)
                movl $1, (%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl $0, 20(%r14)
                leaq 24(%r14), %rdi
                movq %r13, %rsi          # tmp sem header — src cru
                movl %r12d, %edx
                call kof_b64_decode_internal
                movl %eax, 16(%r14)
                movb $0, 24(%r14,%rax)
                movq %r14, %rax
                jmp .Lv_enc_bud_done
            .Lv_enc_bud_null:
                xorl %eax, %eax
            .Lv_enc_bud_done:
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

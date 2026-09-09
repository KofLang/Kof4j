package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.encoding base64/base64url (STDLIB S4.2/S4.2c).
 * WRAPPERS finos sobre kof_b64_*_internal / kof_b64url_encode_internal
 * (runtime crypto/JWT já existentes — regra 2: não reimplementar; apenas
 * aloca a String de saída + header + NUL). Extraído de RuntimeEncoding
 * (gate ≤500); encadeado por RuntimeEncoding.emit (ordem preservada).
 */
public final class RuntimeEncodingB64 {

    private RuntimeEncodingB64() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
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
        """);
    }
}

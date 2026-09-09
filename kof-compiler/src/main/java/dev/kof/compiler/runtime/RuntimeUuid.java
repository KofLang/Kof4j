package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.uuid.v4 (STDLIB S3b). WRAPPER fino sobre
 * kof_sec_random_hex (getrandom, crypto lane) — não reimplementa entropia.
 * 16 bytes → força version=4 (nibble alto do byte 6) e variant=10 (nibble
 * alto do byte 8) — RFC 4122 — formata em 8-4-4-4-12 (36 chars + NUL).
 */
public final class RuntimeUuid {

    private RuntimeUuid() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_uuid_v4() -> String (8-4-4-4-12, version 4, variant 10xx)
            .globl kof_uuid_v4
            .type kof_uuid_v4, @function
            kof_uuid_v4:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl $16, %edi
                call kof_sec_random_hex        # rax = string de 32 hex chars
                movq %rax, %r12                # base da string hex
                movl $61, %edi                # 24 header + 36 + NUL
                call kof_alloc
                movq %rax, %r13                # novo obj
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $36, 16(%r13)
                movl $0, 20(%r13)
                xorl %ebx, %ebx                # i = byte 0..15
                xorl %r14d, %r14d              # posicao de saida
            .Lv_uuid_loop:
                cmpl $16, %ebx
                jge .Lv_uuid_ver
                # endereco dos 2 chars do byte i: 24 + 2*i
                leaq 24(%r12,%rbx,2), %r15
                movzbl 0(%r15), %eax
                movb %al, 24(%r13,%r14)
                incl %r14d
                movzbl 1(%r15), %eax
                movb %al, 24(%r13,%r14)
                incl %r14d
                # traco apos os bytes 3,5,7,9
                cmpl $3, %ebx
                je .Lv_uuid_dash
                cmpl $5, %ebx
                je .Lv_uuid_dash
                cmpl $7, %ebx
                je .Lv_uuid_dash
                cmpl $9, %ebx
                je .Lv_uuid_dash
                incl %ebx
                jmp .Lv_uuid_loop
            .Lv_uuid_dash:
                movb $45, 24(%r13,%r14)        # '-'
                incl %r14d
                incl %ebx
                jmp .Lv_uuid_loop
            .Lv_uuid_ver:
                # r14 = 36 (fim). Forca version (pos 14) e variant (pos 19).
                movb $52, 38(%r13)             # 24+14: '4' (version)
                movb $56, 43(%r13)             # 24+19: '8' (variant 10xx)
                movb $0, 60(%r13)              # 24+36: NUL
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
    }
}

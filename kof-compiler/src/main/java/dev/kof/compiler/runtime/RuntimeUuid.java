package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.uuid.v4 (STDLIB S3b). WRAPPER fino sobre
 * kof_sec_random_hex (getrandom, crypto lane) — não reimplementa entropia.
 * 16 bytes → força version=4 (nibble alto do byte 6) e VARIANT POR MÁSCARA
 * (b[8] = (b[8]&0x3f)|0x80 => char ∈ {8,9,a,b}) — RFC 4122. Paridade com
 * JVM/JS/riscv B25 (09/09): antes fixava '8', um subset do RFC — distribuição
 * divergia dos outros targets (regra 5).
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
                # r14 = 36 (fim). Forca version (pos 14); variant (pos 19)
                # por MASK (nibble alto 10xx), nao por forca de '8':
                # char = primeiro hex do byte 8 -> n em 0..15;
                # n' = (n&3)|8 (=> 8..11); re-codifica '0'+n' ou 'a'+n'-10.
                movb $52, 38(%r13)             # 24+14: '4' (version)
                movzbl 43(%r13), %eax          # char do nibble alto de b[8]
                cmpl $58, %eax                 # acima de '9'?
                jl .Lv_uuid_v8d
                subl $0x57, %eax               # 'a'..'f' -> 10..15
                jmp .Lv_uuid_v8n
            .Lv_uuid_v8d:
                subl $0x30, %eax               # '0'..'9' -> 0..9
            .Lv_uuid_v8n:
                andl $3, %eax
                orl $8, %eax                   # 10xx -> 8..11
                cmpl $10, %eax
                jl .Lv_uuid_v8c
                addl $0x57, %eax               # 10,11 -> 'a','b'
                jmp .Lv_uuid_v8s
            .Lv_uuid_v8c:
                addl $0x30, %eax               # 8,9 -> '8','9'
            .Lv_uuid_v8s:
                movb %al, 43(%r13)             # 24+19: variant 10xx
                movb $0, 60(%r13)              # 24+36: NUL
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_uuid_v7() -> String (8-4-4-4-12, RFC 9562, version 7, variant 10xx)
            .globl kof_uuid_v7
            .type kof_uuid_v7, @function
            kof_uuid_v7:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                call kof_now                   # rax = unix_ts_ms (64-bit int)
                movq %rax, %r14                # r14 = unix timestamp em ms
                movl $16, %edi                 # 16 bytes de entropia getrandom
                call kof_sec_random_hex        # rax = string de 32 hex chars
                movq %rax, %r12                # r12 = base da string hex aleatoria
                movl $61, %edi                 # 24 header + 36 chars + NUL
                call kof_alloc
                movq %rax, %r13                # r13 = novo objeto String
                movl $1, 0(%r13)               # tipo = 1 (string)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $36, 16(%r13)             # len = 36
                movl $0, 20(%r13)
                leaq .Lsec_hex_chars(%rip), %rbx # tabela de hex chars [0-9a-f]
                # Grava os primeiros 8 hex chars do timestamp (ms bits 47..16)
                movl $44, %ecx
                xorl %r15d, %r15d              # out_pos = 0..7
            .Lv7_ts_loop1:
                movq %r14, %rax
                shrq %cl, %rax
                andl $15, %eax
                movzbl (%rbx,%rax), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                subl $4, %ecx
                cmpl $16, %ecx
                jge .Lv7_ts_loop1
                # Hifen apos 8 chars (pos 8)
                movb $45, 24(%r13,%r15)
                incl %r15d                     # out_pos = 9
                # Grava os proximos 4 hex chars do timestamp (ms bits 15..0)
                movl $12, %ecx
            .Lv7_ts_loop2:
                movq %r14, %rax
                shrq %cl, %rax
                andl $15, %eax
                movzbl (%rbx,%rax), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                subl $4, %ecx
                jge .Lv7_ts_loop2
                # Hifen apos byte 5 (pos 13)
                movb $45, 24(%r13,%r15)
                incl %r15d                     # out_pos = 14
                # Versao 7 na pos 14 ('7')
                movb $55, 24(%r13,%r15)
                incl %r15d                     # out_pos = 15
                # 3 hex chars aleatorios de rand_a (chars 0..2 de r12) em pos 15..17
                movzbl 24(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                movzbl 25(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                movzbl 26(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d                     # out_pos = 18
                # Hifen apos byte 7 (pos 18)
                movb $45, 24(%r13,%r15)
                incl %r15d                     # out_pos = 19
                # Variante 10xx (RFC 4122 / RFC 9562) na pos 19: (n&3)|8 -> '8','9','a','b'
                movzbl 27(%r12), %eax          # char 3 da string aleatoria
                cmpl $58, %eax
                jl .Lv7_var_digit
                subl $0x57, %eax               # 'a'..'f' -> 10..15
                jmp .Lv7_var_mask
            .Lv7_var_digit:
                subl $0x30, %eax               # '0'..'9' -> 0..9
            .Lv7_var_mask:
                andl $3, %eax
                orl $8, %eax                   # 10xx -> 8..11
                cmpl $10, %eax
                jl .Lv7_var_char_digit
                addl $0x57, %eax               # 10,11 -> 'a','b'
                jmp .Lv7_var_store
            .Lv7_var_char_digit:
                addl $0x30, %eax               # 8,9 -> '8','9'
            .Lv7_var_store:
                movb %al, 24(%r13,%r15)
                incl %r15d                     # out_pos = 20
                # 3 hex chars aleatorios de rand_b (chars 4..6 de r12) em pos 20..22
                movzbl 28(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                movzbl 29(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                movzbl 30(%r12), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d                     # out_pos = 23
                # Hifen apos byte 9 (pos 23)
                movb $45, 24(%r13,%r15)
                incl %r15d                     # out_pos = 24
                # 12 hex chars aleatorios de rand_b (chars 7..18 de r12) em pos 24..35
                xorl %ecx, %ecx
            .Lv7_rand_b_loop:
                cmpl $12, %ecx
                jge .Lv7_done
                movzbl 31(%r12,%rcx), %eax
                movb %al, 24(%r13,%r15)
                incl %r15d
                incl %ecx
                jmp .Lv7_rand_b_loop
            .Lv7_done:
                movb $0, 60(%r13)              # 24+36: NUL
                movq %r13, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """);
        // kof_uuid_isUuid(rdi=str) -> 1/0 — predicado de forma (sem alocação):
        // len 36, traços em 8/13/18/23, demais hex (0-9 a-f A-F). Paridade
        // travada na matriz stduuidform (riscv/aarch = UUID001 até a fatia B).
        sb.append("""
            # kof_uuid_isUuid(rdi=str) -> 1/0 (forma 8-4-4-4-12, hex, traços
            # fixos; version/variant NAO verificadas — mesma regra JVM/JS/x86)
            .globl kof_uuid_isUuid
            .type kof_uuid_isUuid, @function
            kof_uuid_isUuid:
                testq %rdi, %rdi
                jz .Lv_uuid_f
                cmpl $36, 16(%rdi)
                jne .Lv_uuid_f
                leaq 24(%rdi), %r8
                xorq %rax, %rax
            .Lv_uuid_i_loop:
                cmpq $36, %rax
                jge .Lv_uuid_t
                # posicoes de traco fixas: 8/13/18/23 (testadas direto)
                cmpq $8, %rax
                je .Lv_uuid_i_dash
                cmpq $13, %rax
                je .Lv_uuid_i_dash
                cmpq $18, %rax
                je .Lv_uuid_i_dash
                cmpq $23, %rax
                je .Lv_uuid_i_dash
                movzbl (%r8,%rax), %edx
                # 48..57 | 65..70 | 97..102
                cmpl $48, %edx
                jl .Lv_uuid_f
                cmpl $57, %edx
                jle .Lv_uuid_next
                cmpl $65, %edx
                jl .Lv_uuid_f
                cmpl $70, %edx
                jle .Lv_uuid_next
                cmpl $97, %edx
                jl .Lv_uuid_f
                cmpl $102, %edx
                jg .Lv_uuid_f
            .Lv_uuid_next:
                incq %rax
                jmp .Lv_uuid_i_loop
            .Lv_uuid_i_dash:
                cmpb $45, (%r8,%rax)
                jne .Lv_uuid_f
                incq %rax
                jmp .Lv_uuid_i_loop
            .Lv_uuid_t:
                movl $1, %eax
                ret
            .Lv_uuid_f:
                xorl %eax, %eax
                ret
        """);
    }
}

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

            # ── kof_uuid_v7 (STDLIB S3b.2, RFC 9562) ───────────────────────
            # Espelha o v4: 32 hex aleatorios (kof_sec_random_hex) e SOBREPOE
            # os chars dos bytes 0..5 com ts-48 BE (kof_now epoch-ms); nibble
            # ALTO de b6 forcado '7' (version), variante em b8 por MASK
            # (paridade JVM/JS). ts>2^48 trunca p/ low-48 (formato).
            .globl kof_uuid_v7
            .type kof_uuid_v7, @function
            kof_uuid_v7:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movl $16, %edi
                call kof_sec_random_hex      # rax = 32 hex aleatorios
                movq %rax, %r12
                call kof_now                 # rax = epoch-ms
                movq %rax, %r15              # ts preservado (divl clobbers rax)
                xorl %ebx, %ebx              # j = 0..5
            .Lv7_ts:
                movl $5, %ecx
                subl %ebx, %ecx              # k = 5-j
                shll $3, %ecx                # shift = 8k (40..0)
                movq %r15, %rdx
                sarq %cl, %rdx
                movzbl %dl, %eax             # d = byte j do ts BE
                xorl %edx, %edx
                movl $16, %esi
                divl %esi                    # eax=hi, edx=lo
                leaq .Lsec_hex_chars(%rip), %r8
                movzbl (%r8,%rax), %eax
                movb %al, 24(%r12,%rbx,2)
                movzbl (%r8,%rdx), %eax
                movb %al, 25(%r12,%rbx,2)
                incl %ebx
                cmpl $6, %ebx
                jl .Lv7_ts
                movb $55, 36(%r12)           # 24+12: alto de b6 = '7'
                # variante b8 = hex[16] (40): n' = (n&3)|8 (idem v4)
                movzbl 40(%r12), %eax
                cmpl $58, %eax
                jl .Lv7_v8d
                subl $0x57, %eax
                jmp .Lv7_v8n
            .Lv7_v8d:
                subl $0x30, %eax
            .Lv7_v8n:
                andl $3, %eax
                orl $8, %eax
                cmpl $10, %eax
                jl .Lv7_v8c
                addl $0x57, %eax
                jmp .Lv7_v8s
            .Lv7_v8c:
                addl $0x30, %eax
            .Lv7_v8s:
                movb %al, 40(%r12)
                # --- monta String 36 com hífens (loop identico ao v4) ---
                movl $61, %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $36, 16(%r13)
                movl $0, 20(%r13)
                xorl %ebx, %ebx              # byte i
                xorl %r14d, %r14d            # pos saida
            .Lv7_loop:
                cmpl $16, %ebx
                jge .Lv7_term
                leaq 24(%r12,%rbx,2), %r15
                movzbl 0(%r15), %eax
                movb %al, 24(%r13,%r14)
                incl %r14d
                movzbl 1(%r15), %eax
                movb %al, 24(%r13,%r14)
                incl %r14d
                cmpl $3, %ebx
                je .Lv7_dash
                cmpl $5, %ebx
                je .Lv7_dash
                cmpl $7, %ebx
                je .Lv7_dash
                cmpl $9, %ebx
                je .Lv7_dash
                incl %ebx
                jmp .Lv7_loop
            .Lv7_dash:
                movb $45, 24(%r13,%r14)
                incl %r14d
                incl %ebx
                jmp .Lv7_loop
            .Lv7_term:
                movb $0, 60(%r13)
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

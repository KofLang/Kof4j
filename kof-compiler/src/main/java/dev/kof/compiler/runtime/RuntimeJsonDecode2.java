package dev.kof.compiler.runtime;

/**
 * Emissão do ASM do decode JSON (parte 2) do runtime nativo.
 */
public final class RuntimeJsonDecode2 {

    private RuntimeJsonDecode2() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_json_decode_string
            .type kof_json_decode_string, @function
            kof_json_decode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $16, %rsp
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movl 16(%rbx), %r14d
                xorq %r13, %r13
                jmp .Lkof_json_ds_skip

            .globl kof_json_decode_string_at
            .type kof_json_decode_string_at, @function
            kof_json_decode_string_at:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $16, %rsp
                movq %rdi, %rbx
                movq %rdx, %r13
                call kof_json_builder_new
                movq %rax, %r12
                movl 16(%rbx), %r14d
            .Lkof_json_ds_skip:
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_ds_skip_inc
                cmpb $10, %al
                je .Lkof_json_ds_skip_inc
                cmpb $13, %al
                je .Lkof_json_ds_skip_inc
                cmpb $9, %al
                je .Lkof_json_ds_skip_inc
                jmp .Lkof_json_ds_open
            .Lkof_json_ds_skip_inc:
                incq %r13
                jmp .Lkof_json_ds_skip
            .Lkof_json_ds_open:
                cmpb $34, %al
                # #1174 (19/11): valor NÃO-string devolve o TEXTO JSON CRU
                # aparado (contrato do oráculo JVM). Antes devolvia VAZIO.
                jne .Lkof_json_ds_raw
                incq %r13
            .Lkof_json_ds_loop:
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $34, %al
                je .Lkof_json_ds_close
                cmpb $92, %al
                jne .Lkof_json_ds_plain
                incq %r13
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                # §667: conjunto COMPLETO de escapes (n t r b f aspas barra unicode-hex)
                cmpb $110, %al
                je .Lkof_json_ds_newline
                cmpb $116, %al
                je .Lkof_json_ds_tab
                cmpb $114, %al
                je .Lkof_json_ds_cr
                cmpb $98, %al
                je .Lkof_json_ds_bs
                cmpb $102, %al
                je .Lkof_json_ds_ff
                cmpb $117, %al
                je .Lkof_json_ds_uesc
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_newline:
                movl $10, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_tab:
                movl $9, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_cr:
                movl $13, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_bs:
                movl $8, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_ff:
                movl $12, %eax
                jmp .Lkof_json_ds_plain
            # escape unicode -> lê os 4 dígitos hex, codifica em UTF-8 e emite os bytes.
            # Decodifica exatamente como o JsonParser do JVM (um code unit por
            # escape), então pares surrogate saem como CESU-8 nos dois lados.
            .Lkof_json_ds_uesc:
                movq %r13, %r10          # r10 = p = índice de 'u'
                leaq 1(%r10), %r13       # cursor do primeiro dígito hex
                xorl %r11d, %r11d        # r11 = code point
                movl $4, %r9d            # 4 dígitos hex
            .Lkof_json_ds_uhex_loop:
                cmpl %r14d, %r13d
                jge .Lkof_json_ds_uesc_end
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $48, %al
                jl .Lkof_json_ds_uesc_end
                cmpb $57, %al
                jle .Lkof_json_ds_uhex_digit
                cmpb $65, %al
                jl .Lkof_json_ds_uesc_end
                cmpb $70, %al
                jle .Lkof_json_ds_uhex_alpha
                cmpb $97, %al
                jl .Lkof_json_ds_uesc_end
                cmpb $102, %al
                jg .Lkof_json_ds_uesc_end
                subl $87, %eax           # a-f -> 10-15
                jmp .Lkof_json_ds_uhex_acc
            .Lkof_json_ds_uhex_alpha:
                subl $55, %eax           # A-F -> 10-15
                jmp .Lkof_json_ds_uhex_acc
            .Lkof_json_ds_uhex_digit:
                subl $48, %eax           # 0-9 -> 0-9
            .Lkof_json_ds_uhex_acc:
                shll $4, %r11d
                orl %eax, %r11d
                incq %r13
                decl %r9d
                jnz .Lkof_json_ds_uhex_loop
            .Lkof_json_ds_uesc_end:
                movq %r13, %r10
            # code point r11d -> 1-3 bytes UTF-8
                cmpl $128, %r11d
                jb .Lkof_json_ds_u1
                cmpl $2048, %r11d
                jb .Lkof_json_ds_u2
                cmpl $65536, %r11d
                jb .Lkof_json_ds_u3
                movl %r11d, %r9d
                shrl $18, %r9d
                orl $240, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                movl %r11d, %r9d
                shrl $12, %r9d
                andl $63, %r9d
                orl $128, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                movl %r11d, %r9d
                shrl $6, %r9d
                andl $63, %r9d
                orl $128, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                jmp .Lkof_json_ds_ucont
            .Lkof_json_ds_u3:
                movl %r11d, %r9d
                shrl $12, %r9d
                orl $224, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                movl %r11d, %r9d
                shrl $6, %r9d
                andl $63, %r9d
                orl $128, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                jmp .Lkof_json_ds_ucont
            .Lkof_json_ds_u2:
                movl %r11d, %r9d
                shrl $6, %r9d
                orl $192, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
            .Lkof_json_ds_ucont:
                movl %r11d, %r9d
                andl $63, %r9d
                orl $128, %r9d
                movq %r12, %rdi
                movl %r9d, %esi
                call kof_json_builder_char
                jmp .Lkof_json_ds_udone
            .Lkof_json_ds_u1:
                movq %r12, %rdi
                movl %r11d, %esi
                call kof_json_builder_char
            .Lkof_json_ds_udone:
                movq %r10, %r13          # retoma após os 4 dígitos hex
                jmp .Lkof_json_ds_loop
            .Lkof_json_ds_plain:
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r13
                jmp .Lkof_json_ds_loop
            .Lkof_json_ds_close:
                incq %r13
                jmp .Lkof_json_ds_done     # string FECHADA: nada de span cru
            .Lkof_json_ds_raw:
                movq %r14, (%rsp)        # fim (exclusivo) do documento (slot)
            .Lkof_json_ds_raw_trim:
                movq (%rsp), %r15
                cmpq %r13, %r15
                jle .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl -1(%rax,%r15), %eax
                cmpb $32, %al
                je .Lkof_json_ds_raw_dec
                cmpb $10, %al
                je .Lkof_json_ds_raw_dec
                cmpb $13, %al
                je .Lkof_json_ds_raw_dec
                cmpb $9, %al
                je .Lkof_json_ds_raw_dec
                jmp .Lkof_json_ds_raw_copy
            .Lkof_json_ds_raw_dec:
                decq %r15
                movq %r15, (%rsp)
                jmp .Lkof_json_ds_raw_trim
            .Lkof_json_ds_raw_copy:
                movq (%rsp), %r15
                cmpq %r15, %r13
                jge .Lkof_json_ds_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r13
                jmp .Lkof_json_ds_raw_copy
            .Lkof_json_ds_done:
                movq %r12, %rdi
                call kof_json_builder_result
                movq %r13, %rdx
                addq $16, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_int_list
            .type kof_json_decode_int_list, @function
            kof_json_decode_int_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_list_new
                movq %rax, %r12
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Lkof_json_dil_skip:
                cmpl %r15d, %r13d
                jge .Lkof_json_dil_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_dil_skip_inc
                cmpb $10, %al
                je .Lkof_json_dil_skip_inc
                cmpb $13, %al
                je .Lkof_json_dil_skip_inc
                cmpb $9, %al
                je .Lkof_json_dil_skip_inc
                jmp .Lkof_json_dil_open
            .Lkof_json_dil_skip_inc:
                incq %r13
                jmp .Lkof_json_dil_skip
            .Lkof_json_dil_open:
                cmpb $91, %al
                jne .Lkof_json_dil_done
                incq %r13
            .Lkof_json_dil_loop:
                cmpl %r15d, %r13d
                jge .Lkof_json_dil_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $93, %al
                je .Lkof_json_dil_done
                cmpb $44, %al
                je .Lkof_json_dil_comma
                cmpb $32, %al
                je .Lkof_json_dil_comma
                cmpb $10, %al
                je .Lkof_json_dil_comma
                cmpb $9, %al
                je .Lkof_json_dil_comma
                movq %rbx, %rdi
                movq %r13, %rdx
                call kof_json_decode_int_at
                movq %rdx, %r13
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Lkof_json_dil_loop
            .Lkof_json_dil_comma:
                incq %r13
                jmp .Lkof_json_dil_loop
            .Lkof_json_dil_done:
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_string_list
            .type kof_json_decode_string_list, @function
            kof_json_decode_string_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_list_new
                movq %rax, %r12
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Lkof_json_dsl_skip:
                cmpl %r15d, %r13d
                jge .Lkof_json_dsl_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $32, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $10, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $13, %al
                je .Lkof_json_dsl_skip_inc
                cmpb $9, %al
                je .Lkof_json_dsl_skip_inc
                jmp .Lkof_json_dsl_open
            .Lkof_json_dsl_skip_inc:
                incq %r13
                jmp .Lkof_json_dsl_skip
            .Lkof_json_dsl_open:
                cmpb $91, %al
                jne .Lkof_json_dsl_done
                incq %r13
            .Lkof_json_dsl_loop:
                cmpl %r15d, %r13d
                jge .Lkof_json_dsl_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r13), %eax
                cmpb $93, %al
                je .Lkof_json_dsl_done
                cmpb $44, %al
                je .Lkof_json_dsl_comma
                cmpb $32, %al
                je .Lkof_json_dsl_comma
                cmpb $10, %al
                je .Lkof_json_dsl_comma
                cmpb $9, %al
                je .Lkof_json_dsl_comma
                movq %rbx, %rdi
                movq %r13, %rdx
                call kof_json_decode_string_at
                movq %rdx, %r13
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Lkof_json_dsl_loop
            .Lkof_json_dsl_comma:
                incq %r13
                jmp .Lkof_json_dsl_loop
            .Lkof_json_dsl_done:
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
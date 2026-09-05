package dev.kof.compiler;

/**
 * Emissão do ASM do encode JSON (kof_json_encode_int/long/bool/double/float/string/
 * list/array) do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeJsonEncode {

    private RuntimeJsonEncode() {}

    static void emitJsonEncode(StringBuilder sb) {
        sb.append("""
            .globl kof_json_encode_int
            .type kof_json_encode_int, @function
            kof_json_encode_int:
                jmp kof_int_to_string

            .globl kof_json_encode_long
            .type kof_json_encode_long, @function
            kof_json_encode_long:
                jmp kof_long_to_string

            .globl kof_json_encode_bool
            .type kof_json_encode_bool, @function
            kof_json_encode_bool:
                jmp kof_bool_to_string

            .globl kof_json_encode_double
            .type kof_json_encode_double, @function
            kof_json_encode_double:
                # xmm0 = double -> KofString* com o texto (%g via snprintf)
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                subq $80, %rsp
                leaq -72(%rbp), %r12        # buffer (acima do rsp real, no frame)
                movq %r12, %rdi
                movq $64, %rsi
                leaq .Lfmt_double(%rip), %rdx
                movl $1, %eax
                movq %rsp, %rbx
                andq $-16, %rsp             # alinha para snprintf
                call snprintf
                movq %rbx, %rsp             # restaura rsp real
                xorl %edx, %edx
            .Lkof_je_dbl_len:
                cmpb $0, (%r12,%rdx)
                je .Lkof_je_dbl_gotlen
                incq %rdx
                jmp .Lkof_je_dbl_len
            .Lkof_je_dbl_gotlen:
                movl %edx, %esi
                movq %r12, %rdi
                call kof_string_from_literal
                addq $80, %rsp
                popq %r12
                popq %rbx
                popq %rbp
                ret

            .globl kof_json_encode_float
            .type kof_json_encode_float, @function
            kof_json_encode_float:
                cvtss2sd %xmm0, %xmm0
                jmp kof_json_encode_double

            .globl kof_json_encode_string
            .type kof_json_encode_string, @function
            kof_json_encode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_esc_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_esc_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r14), %eax
                cmpb $34, %al
                je .Lkof_json_esc_quote
                cmpb $92, %al
                je .Lkof_json_esc_backslash
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_quote:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_backslash:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_done:
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_list
            .type kof_json_encode_list, @function
            kof_json_encode_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r15d
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_el_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_el_done
                testq %r14, %r14
                jz .Lkof_json_el_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_el_no_comma:
                movq 24(%rbx), %rax
                movq (%rax,%r14,8), %rdi
                cmpl $1, %r15d
                je .Lkof_json_el_string
                cmpl $2, %r15d
                je .Lkof_json_el_bool
                call kof_json_encode_int
                jmp .Lkof_json_el_appended
            .Lkof_json_el_string:
                call kof_json_encode_string
                jmp .Lkof_json_el_appended
            .Lkof_json_el_bool:
                call kof_json_encode_bool
            .Lkof_json_el_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_el_loop
            .Lkof_json_el_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_array
            .type kof_json_encode_array, @function
            kof_json_encode_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                movl 20(%rbx), %r15d
                xorq %r14, %r14
            .Lkof_json_ea_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_ea_done
                testq %r14, %r14
                jz .Lkof_json_ea_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_ea_no_comma:
                leaq 24(%rbx), %rax
                cmpl $4, %r15d
                je .Lkof_json_ea_int
                movq (%rax,%r14,8), %rdi
                call kof_json_encode_string
                jmp .Lkof_json_ea_appended
            .Lkof_json_ea_int:
                movl (%rax,%r14,4), %edi
                call kof_json_encode_int
            .Lkof_json_ea_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_ea_loop
            .Lkof_json_ea_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }

}
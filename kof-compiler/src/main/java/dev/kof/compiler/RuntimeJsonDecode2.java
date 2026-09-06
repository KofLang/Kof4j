package dev.kof.compiler;

/**
 * Emissão do ASM do decode JSON (parte 2) do runtime nativo.
 */
final class RuntimeJsonDecode2 {

    private RuntimeJsonDecode2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_json_decode_string
            .type kof_json_decode_string, @function
            kof_json_decode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
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
                jne .Lkof_json_ds_done
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
                cmpb $110, %al
                je .Lkof_json_ds_newline
                cmpb $116, %al
                je .Lkof_json_ds_tab
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_newline:
                movl $10, %eax
                jmp .Lkof_json_ds_plain
            .Lkof_json_ds_tab:
                movl $9, %eax
            .Lkof_json_ds_plain:
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r13
                jmp .Lkof_json_ds_loop
            .Lkof_json_ds_close:
                incq %r13
            .Lkof_json_ds_done:
                movq %r12, %rdi
                call kof_json_builder_result
                movq %r13, %rdx
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
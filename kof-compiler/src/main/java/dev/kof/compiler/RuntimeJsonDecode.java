package dev.kof.compiler;

/**
Emissão do ASM do decode JSON (kof_json_decode_int/long/double/float/list/bool/
 * string/...) do runtime nativo. Domínio isolado do NativeRuntime — refactor preserva semântica.
 */
final class RuntimeJsonDecode {

    private RuntimeJsonDecode() {}

    static void emitJsonDecode(StringBuilder sb) {
        sb.append("""
            .globl kof_json_decode_int
            .type kof_json_decode_int, @function
            kof_json_decode_int:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
                xorq %rdx, %rdx
                jmp .Lkof_json_di_skip

            .globl kof_json_decode_int_at
            .type kof_json_decode_int_at, @function
            kof_json_decode_int_at:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
            .Lkof_json_di_skip:
                cmpl %ecx, %edx
                jge .Lkof_json_di_err
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $32, %al
                je .Lkof_json_di_skip_inc
                cmpb $10, %al
                je .Lkof_json_di_skip_inc
                cmpb $13, %al
                je .Lkof_json_di_skip_inc
                cmpb $9, %al
                je .Lkof_json_di_skip_inc
                jmp .Lkof_json_di_sign
            .Lkof_json_di_skip_inc:
                incq %rdx
                jmp .Lkof_json_di_skip
            .Lkof_json_di_sign:
                movq $1, %r12
                cmpb $45, %al
                jne .Lkof_json_di_digits
                movq $-1, %r12
                incq %rdx
            .Lkof_json_di_digits:
                xorq %r8, %r8
            .Lkof_json_di_loop:
                cmpl %ecx, %edx
                jge .Lkof_json_di_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $48, %al
                jl .Lkof_json_di_done
                cmpb $57, %al
                jg .Lkof_json_di_done
                imulq $10, %r8
                subl $48, %eax
                movslq %eax, %rax
                addq %rax, %r8
                incq %rdx
                jmp .Lkof_json_di_loop
            .Lkof_json_di_done:
                imulq %r12, %r8
                movq %r8, %rax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_di_err:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_long
            .type kof_json_decode_long, @function
            kof_json_decode_long:
                jmp kof_json_decode_int

            .globl kof_json_decode_double
            .type kof_json_decode_double, @function
            kof_json_decode_double:
                # rdi = KofString* json -> xmm0 = double do primeiro token
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $192, %rsp             # token em 0(%rsp), KofString em 128(%rsp)
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_jdd_zero
                movl 16(%rbx), %r15d        # len
                xorq %r12, %r12             # pos
            .Lkof_jdd_skip:
                cmpl %r15d, %r12d
                jge .Lkof_jdd_zero
                movzbl 24(%rbx,%r12), %eax
                cmpb $32, %al
                je .Lkof_jdd_skip_inc
                cmpb $9, %al
                je .Lkof_jdd_skip_inc
                cmpb $10, %al
                je .Lkof_jdd_skip_inc
                cmpb $13, %al
                je .Lkof_jdd_skip_inc
                jmp .Lkof_jdd_scan
            .Lkof_jdd_skip_inc:
                incq %r12
                jmp .Lkof_jdd_skip
            .Lkof_jdd_scan:
                xorq %r13, %r13             # len do token
            .Lkof_jdd_loop:
                cmpl %r15d, %r12d
                jge .Lkof_jdd_build
                movzbl 24(%rbx,%r12), %eax
                cmpb $43, %al               # '+'
                je .Lkof_jdd_take
                cmpb $45, %al               # '-'
                je .Lkof_jdd_take
                cmpb $46, %al               # '.'
                je .Lkof_jdd_take
                cmpb $101, %al              # 'e'
                je .Lkof_jdd_take
                cmpb $69, %al               # 'E'
                je .Lkof_jdd_take
                cmpb $48, %al
                jb .Lkof_jdd_build
                cmpb $57, %al
                ja .Lkof_jdd_build
            .Lkof_jdd_take:
                cmpq $127, %r13
                jge .Lkof_jdd_build
                leaq 24(%rbx), %rax
                movzbl (%rax,%r12), %eax
                movb %al, (%rsp,%r13)
                incq %r13
                incq %r12
                jmp .Lkof_jdd_loop
            .Lkof_jdd_build:
                testq %r13, %r13
                jz .Lkof_jdd_zero
                movb $0, (%rsp,%r13)        # NUL-terminate
                # KofString temporario em 128(%rsp): tag/len/data
                movq $1, 128(%rsp)          # tag string
                movq $0, 136(%rsp)
                movl %r13d, 144(%rsp)       # len
                xorq %r14, %r14
            .Lkof_jdd_copy:
                cmpq %r13, %r14
                jge .Lkof_jdd_call
                movzbl (%rsp,%r14), %eax
                movb %al, 152(%rsp,%r14)
                incq %r14
                jmp .Lkof_jdd_copy
            .Lkof_jdd_call:
                movb $0, 152(%rsp,%r13)
                leaq 128(%rsp), %rdi
                call kof_string_to_double
                addq $192, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_jdd_zero:
                xorpd %xmm0, %xmm0
                addq $192, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_float
            .type kof_json_decode_float, @function
            kof_json_decode_float:
                call kof_json_decode_double
                cvtsd2ss %xmm0, %xmm0
                ret

            .globl kof_json_decode_list
            .type kof_json_decode_list, @function
            kof_json_decode_list:
                jmp kof_json_decode_int_list

            .globl kof_json_decode_bool
            .type kof_json_decode_bool, @function
            kof_json_decode_bool:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl 16(%rbx), %ecx
                xorq %rdx, %rdx
            .Lkof_json_db_skip:
                cmpl %ecx, %edx
                jge .Lkof_json_db_false
                leaq 24(%rbx), %rax
                movzbl (%rax,%rdx), %eax
                cmpb $32, %al
                je .Lkof_json_db_skip_inc
                cmpb $10, %al
                je .Lkof_json_db_skip_inc
                cmpb $13, %al
                je .Lkof_json_db_skip_inc
                cmpb $9, %al
                je .Lkof_json_db_skip_inc
                jmp .Lkof_json_db_check
            .Lkof_json_db_skip_inc:
                incq %rdx
                jmp .Lkof_json_db_skip
            .Lkof_json_db_check:
                leaq .Lkof_json_true(%rip), %rsi
                movl $4, %r8d
                call kof_json_starts_with
                testl %eax, %eax
                jz .Lkof_json_db_false
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_db_false:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

            .globl kof_json_starts_with
            .type kof_json_starts_with, @function
            kof_json_starts_with:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movl %edx, %r12d
                movq %rsi, %rdi
                movslq %edx, %rdx
                movl 16(%rbx), %ecx
                cmpl %ecx, %edx
                jg .Lkof_json_sw_no
                xorq %rcx, %rcx
            .Lkof_json_sw_loop:
                cmpl %r12d, %ecx
                jge .Lkof_json_sw_yes
                leaq 24(%rbx), %rax
                movzbl (%rax,%rcx), %eax
                movzbl (%rdi,%rcx), %r8d
                cmpb %r8b, %al
                jne .Lkof_json_sw_no
                incq %rcx
                jmp .Lkof_json_sw_loop
            .Lkof_json_sw_yes:
                movl $1, %eax
                popq %r12
                popq %rbx
                ret
            .Lkof_json_sw_no:
                xorl %eax, %eax
                popq %r12
                popq %rbx
                ret

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
        sb.append(".Lkof_json_true: .asciz \"true\"\n");
    }

}
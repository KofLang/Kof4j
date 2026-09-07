package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM das operações base de String (kof_string_from_literal/
 * length/concat/equals + kof_print_string/println_string + kof_memcpy) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeStringBase {

    private RuntimeStringBase() {}

    public static void emitStringFromLiteral(StringBuilder sb) {
        sb.append("""
            .globl kof_string_from_literal
            .type kof_string_from_literal, @function
            kof_string_from_literal:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, 0(%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitMemcpy(StringBuilder sb) {
        sb.append("""
            .globl kof_memcpy
            .type kof_memcpy, @function
            kof_memcpy:
                xorq %rcx, %rcx
            .Lkof_memcpy_loop:
                cmpl %ecx, %edx
                jle .Lkof_memcpy_done
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Lkof_memcpy_loop
            .Lkof_memcpy_done:
                ret
            """);
    }
    public static void emitStringLength(StringBuilder sb) {
        sb.append("""
            .globl kof_string_length
            .type kof_string_length, @function
            kof_string_length:
                # conta code units UTF-16 (paridade JVM/JS), não bytes UTF-8
                # (bug 43): 1 byte → 1; 2/3 bytes → 1; astral (4 bytes) → 2.
                pushq %rbx
                pushq %r12
                movl 16(%rdi), %ecx        # byteLen (bytes UTF-8)
                leaq 24(%rdi), %rsi        # chars
                xorl %eax, %eax            # count = 0
                xorl %ebx, %ebx            # i = 0
            .Lkof_strlen_loop:
                cmpl %ecx, %ebx
                jge .Lkof_strlen_done
                movzbl (%rsi,%rbx), %edx   # byte
                cmpb $0x80, %dl
                jb .Lkof_strlen_inc1
                movb %dl, %r8b
                andb $0xE0, %r8b
                cmpb $0xC0, %r8b
                je .Lkof_strlen_inc2
                movb %dl, %r8b
                andb $0xF0, %r8b
                cmpb $0xE0, %r8b
                je .Lkof_strlen_inc3
                addl $2, %eax             # astral: surrogate pair = 2 code units
                addl $4, %ebx
                jmp .Lkof_strlen_loop
            .Lkof_strlen_inc1:
                addl $1, %eax
                addl $1, %ebx
                jmp .Lkof_strlen_loop
            .Lkof_strlen_inc2:
                addl $1, %eax
                addl $2, %ebx
                jmp .Lkof_strlen_loop
            .Lkof_strlen_inc3:
                addl $1, %eax
                addl $3, %ebx
                jmp .Lkof_strlen_loop
            .Lkof_strlen_done:
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitStringConcat(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lkof_null_str: .asciz "null"
            .section .text
            .globl kof_string_concat
            .type kof_string_concat, @function
            kof_string_concat:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                xorl %r13d, %r13d
                xorl %r15d, %r15d
                testq %rbx, %rbx
                jnz .Lkof_concat_rbx_len
                movl $4, %r13d
                movl $1, %r15d
                jmp .Lkof_concat_r12_len
            .Lkof_concat_rbx_len:
                movl 16(%rbx), %r13d
            .Lkof_concat_r12_len:
                testq %r12, %r12
                jnz .Lkof_concat_r12_len2
                addl $4, %r13d
                orl $2, %r15d
                jmp .Lkof_concat_alloc
            .Lkof_concat_r12_len2:
                addl 16(%r12), %r13d
            .Lkof_concat_alloc:
                leal 25(%r13), %edi
                call kof_alloc
                movq %rax, %r14
                movl $1, 0(%r14)
                movl $0, 4(%r14)
                movq $0, 8(%r14)
                movl %r13d, 16(%r14)
                movl $0, 20(%r14)
                movq %r14, %rdi
                addq $24, %rdi
                testl $1, %r15d
                jnz .Lkof_concat_copy_null_rbx
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %edx
                call kof_memcpy
                movl 16(%rbx), %eax
                jmp .Lkof_concat_after_rbx
            .Lkof_concat_copy_null_rbx:
                leaq .Lkof_null_str(%rip), %rsi
                movl $4, %edx
                call kof_memcpy
                movl $4, %eax
            .Lkof_concat_after_rbx:
                movq %r14, %rdi
                addq $24, %rdi
                addq %rax, %rdi
                testl $2, %r15d
                jnz .Lkof_concat_copy_null_r12
                testq %r12, %r12
                jz .Lkof_concat_done
                leaq 24(%r12), %rsi
                movl 16(%r12), %edx
                call kof_memcpy
                jmp .Lkof_concat_done
            .Lkof_concat_copy_null_r12:
                leaq .Lkof_null_str(%rip), %rsi
                movl $4, %edx
                call kof_memcpy
            .Lkof_concat_done:
                movb $0, 24(%r14,%r13)
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitPrintString(StringBuilder sb) {
        sb.append("""
            .globl kof_print_string
            .type kof_print_string, @function
            kof_print_string:
                testq %rdi, %rdi
                jnz .Lkof_print_string_ok
                leaq .Lkof_null_str(%rip), %rsi
                movq $4, %rdx
                movq $1, %rax
                movq $1, %rdi
                syscall
                ret
            .Lkof_print_string_ok:
                movq %rdi, %rsi
                addq $24, %rsi
                movl 16(%rdi), %edx
                movq $1, %rax
                movq $1, %rdi
                syscall
                ret
            """);
    }
    public static void emitPrintlnString(StringBuilder sb) {
        sb.append("""
            .globl kof_println_string
            .type kof_println_string, @function
            kof_println_string:
                pushq %rbx
                movq %rdi, %rbx
                movq %rbx, %rdi
                call kof_print_string
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }
    public static void emitStringEquals(StringBuilder sb) {
        sb.append("""
            .globl kof_string_equals
            .type kof_string_equals, @function
            kof_string_equals:
                # null-safe: comparar String com null compara ponteiros
                testq %rdi, %rdi
                jz .Lkof_streq_nulla
                testq %rsi, %rsi
                jnz .Lkof_streq_body
                xorl %eax, %eax          # a != null, b == null
                ret
            .Lkof_streq_nulla:
                testq %rsi, %rsi
                jnz .Lkof_streq_nullb
                movl $1, %eax            # ambas nulas
                ret
            .Lkof_streq_nullb:
                xorl %eax, %eax          # a == null, b != null
                ret
            .Lkof_streq_body:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                cmpl %r13d, 16(%r12)
                jne .Lkof_strequals_no
                xorq %rcx, %rcx
            .Lkof_strequals_loop:
                cmpl %r13d, %ecx
                jge .Lkof_strequals_yes
                movzbl 24(%rbx,%rcx), %eax
                cmpb %al, 24(%r12,%rcx)
                jne .Lkof_strequals_no
                incq %rcx
                jmp .Lkof_strequals_loop
            .Lkof_strequals_yes:
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strequals_no:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }}

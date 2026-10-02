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
                jmp .Lkof_concat_after_b
            .Lkof_concat_copy_null_r12:
                leaq .Lkof_null_str(%rip), %rsi
                movl $4, %edx
                call kof_memcpy
            .Lkof_concat_after_b:
            # §537: paridade com a JVM (UTF-16) — se `a` termina num high
            # surrogate e `b` comeca num low surrogate, os dois 3-byte WTF-8
            # viram UM 4-byte UTF-8. Sem isto `"" + (55357 as Char) + (56832
            # as Char)` nao bate com o literal `😀`.
            testq %rbx, %rbx
            jz .Lkof_concat_done
            testq %r12, %r12
            jz .Lkof_concat_done
            movl 16(%rbx), %eax        # aLen
            movl 16(%r12), %edx        # bLen
            cmpl $3, %eax
            jl .Lkof_concat_done
            cmpl $3, %edx
            jl .Lkof_concat_done
            leaq 24(%r14), %rsi        # p = destino
            leaq -3(%rsi,%rax), %rcx   # a3 = p + aLen - 3
            cmpb $0xED, 0(%rcx)
            jne .Lkof_concat_done
            movzbl 1(%rcx), %r8d
            andl $0xF0, %r8d
            cmpl $0xA0, %r8d
            jne .Lkof_concat_done
            movzbl 2(%rcx), %r9d
            andl $0xC0, %r9d
            cmpl $0x80, %r9d
            jne .Lkof_concat_done
            addq %rax, %rsi            # b0 = p + aLen
            cmpb $0xED, 0(%rsi)
            jne .Lkof_concat_done
            movzbl 1(%rsi), %r8d
            andl $0xF0, %r8d
            cmpl $0xB0, %r8d
            jne .Lkof_concat_done
            movzbl 2(%rsi), %r9d
            andl $0xC0, %r9d
            cmpl $0x80, %r9d
            jne .Lkof_concat_done
            # codepoint = 0x10000 + ((hi-0xD800)<<10) + (lo-0xDC00)
            movzbl 1(%rcx), %r8d
            andl $0x3F, %r8d
            shll $6, %r8d
            movzbl 2(%rcx), %r9d
            andl $0x3F, %r9d
            orl %r9d, %r8d
            orl $0xD800, %r8d          # hi
            movzbl 1(%rsi), %r9d
            andl $0x3F, %r9d
            shll $6, %r9d
            movzbl 2(%rsi), %r10d
            andl $0x3F, %r10d
            orl %r10d, %r9d
            orl $0xDC00, %r9d          # lo
            subl $0xD800, %r8d
            shll $10, %r8d
            subl $0xDC00, %r9d
            addl %r9d, %r8d
            addl $0x10000, %r8d        # cp
            # shift b[3..bLen) 2 bytes left (dest b+1)
            leaq 3(%rsi), %r10         # src
            leaq 1(%rsi), %r11         # dst
            subl $3, %edx              # count = bLen-3
            xorl %edi, %edi
        .Lkof_concat_shift_loop:
            cmpl %edx, %edi
            jge .Lkof_concat_shift_done
            movb (%r10,%rdi), %al
            movb %al, (%r11,%rdi)
            incl %edi
            jmp .Lkof_concat_shift_loop
        .Lkof_concat_shift_done:
            subl $2, %r13d
            movl %r13d, 16(%r14)
            # escreve os 4 bytes UTF-8 de cp em a3 (rcx)
            movl %r8d, %r9d
            shrl $18, %r9d
            orl $0xF0, %r9d
            movb %r9b, 0(%rcx)
            movl %r8d, %r9d
            shrl $12, %r9d
            andl $0x3F, %r9d
            orl $0x80, %r9d
            movb %r9b, 1(%rcx)
            movl %r8d, %r9d
            shrl $6, %r9d
            andl $0x3F, %r9d
            orl $0x80, %r9d
            movb %r9b, 2(%rcx)
            movl %r8d, %r9d
            andl $0x3F, %r9d
            orl $0x80, %r9d
            movb %r9b, 3(%rcx)
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
                movq $1, %rdi
                call kof_plat_write
                ret
            .Lkof_print_string_ok:
                movq %rdi, %rsi
                addq $24, %rsi
                movl 16(%rdi), %edx
                movq $1, %rdi
                call kof_plat_write
                ret
            """);
    }
    public static void emitPrintlnString(StringBuilder sb) {
        sb.append("""
            .globl kof_println_string
            .type kof_println_string, @function
            kof_println_string:
                # §396: null -> "null" + newline (paridade JVM; mesma lingua do
                # guard de kof_println; riscv ja fazia isso nativamente).
                testq %rdi, %rdi
                jne .Lkof_pls_nn
                leaq .Lkpls_null(%rip), %rdi
                call kof_print
                leaq .Lnewline(%rip), %rdi
                call kof_print
                ret
            .Lkof_pls_nn:
                pushq %rbx
                movq %rdi, %rbx
                movq %rbx, %rdi
                call kof_print_string
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            .Lkpls_null: .asciz "null"
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

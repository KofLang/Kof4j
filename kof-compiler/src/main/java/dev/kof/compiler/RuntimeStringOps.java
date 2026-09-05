package dev.kof.compiler;

/**
 * Emissão do ASM de operações em String (kof_string_charAt/substring/trim/
 * case/equalsIgnoreCase) do runtime nativo. Domínio isolado do NativeRuntime —
 * refactor preserva semântica.
 */
final class RuntimeStringOps {

    private RuntimeStringOps() {}

    static void emitStringCharAt(StringBuilder sb) {
        sb.append("""
            .globl kof_string_char_at
            .type kof_string_char_at, @function
            kof_string_char_at:
                movl 16(%rdi), %edx
                cmpl %edx, %esi
                jge .Lkof_strcharAt_bounds
                testl %esi, %esi
                jl .Lkof_strcharAt_bounds
                movzbl 24(%rdi,%rsi), %eax
                ret
            .Lkof_strcharAt_bounds:
                movl %esi, %edi
                movl 16(%rdi), %esi
                call kof_bounds_error
            """);
    }
    static void emitStringSubstring(StringBuilder sb) {
        sb.append("""
            .globl kof_string_substring
            .type kof_string_substring, @function
            kof_string_substring:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                movl 16(%rbx), %ecx
                cmpl $0, %r13d
                jne .Lkof_substr_end_ok
                movl %ecx, %r13d
            .Lkof_substr_end_ok:
                cmpl %ecx, %r13d
                jg .Lkof_substr_bounds
                testl %r12d, %r12d
                jl .Lkof_substr_bounds
                cmpl %r13d, %r12d
                jg .Lkof_substr_bounds
                movl %r13d, %edi
                subl %r12d, %edi
                movl %edi, %r14d
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                movq %r15, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                addq $24, %rsi
                movl %r12d, %eax
                addq %rax, %rsi
                movl %r14d, %edx
                call kof_memcpy
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_substr_bounds:
                movl %r12d, %edi
                movl %r13d, %esi
                call kof_bounds_error
            """);
    }
    static void emitStringTrim(StringBuilder sb) {
        sb.append("""
            .globl kof_string_trim
            .type kof_string_trim, @function
            kof_string_trim:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                xorl %r13d, %r13d
            .Lkof_trim_lead:
                cmpl %r12d, %r13d
                jge .Lkof_trim_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $32, %al
                je .Lkof_trim_skip
                cmpb $9, %al
                je .Lkof_trim_skip
                cmpb $10, %al
                je .Lkof_trim_skip
                cmpb $13, %al
                je .Lkof_trim_skip
                jmp .Lkof_trim_trail
            .Lkof_trim_skip:
                incl %r13d
                jmp .Lkof_trim_lead
            .Lkof_trim_trail:
                movl %r12d, %r14d
            .Lkof_trim_trail_loop:
                cmpl %r13d, %r14d
                jle .Lkof_trim_done
                decl %r14d
                movzbl 24(%rbx,%r14), %eax
                cmpb $32, %al
                je .Lkof_trim_trail_loop
                cmpb $9, %al
                je .Lkof_trim_trail_loop
                cmpb $10, %al
                je .Lkof_trim_trail_loop
                cmpb $13, %al
                je .Lkof_trim_trail_loop
                incl %r14d
            .Lkof_trim_done:
                movl %r14d, %eax
                subl %r13d, %eax
                movl %eax, %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                leaq 24(%r15), %rdi
                leaq 24(%rbx), %rsi
                addq %r13, %rsi
                movl %r12d, %edx
                call kof_memcpy
                movb $0, 24(%r15,%r12)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    static void emitStringCase(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_upper
            .type kof_string_to_upper, @function
            kof_string_to_upper:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                xorl %ecx, %ecx
            .Lkof_upper_loop:
                cmpl %r12d, %ecx
                jge .Lkof_upper_done
                movzbl 24(%rbx,%rcx), %eax
                cmpb $97, %al
                jb .Lkof_upper_store
                cmpb $122, %al
                ja .Lkof_upper_store
                subl $32, %eax
            .Lkof_upper_store:
                movb %al, 24(%r13,%rcx)
                incq %rcx
                jmp .Lkof_upper_loop
            .Lkof_upper_done:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .globl kof_string_to_lower
            .type kof_string_to_lower, @function
            kof_string_to_lower:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r12d, 16(%r13)
                movl $0, 20(%r13)
                xorl %ecx, %ecx
            .Lkof_lower_loop:
                cmpl %r12d, %ecx
                jge .Lkof_lower_done
                movzbl 24(%rbx,%rcx), %eax
                cmpb $65, %al
                jb .Lkof_lower_store
                cmpb $90, %al
                ja .Lkof_lower_store
                addl $32, %eax
            .Lkof_lower_store:
                movb %al, 24(%r13,%rcx)
                incq %rcx
                jmp .Lkof_lower_loop
            .Lkof_lower_done:
                movb $0, 24(%r13,%r12)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    static void emitStringEqualsIgnoreCase(StringBuilder sb) {
        sb.append("""
            .globl kof_string_equals_ignore_case
            .type kof_string_equals_ignore_case, @function
            kof_string_equals_ignore_case:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%rbx), %r13d
                movl 16(%r12), %r14d
                cmpl %r14d, %r13d
                jne .Lkof_eqic_no
                xorl %ecx, %ecx
            .Lkof_eqic_loop:
                cmpl %r13d, %ecx
                jge .Lkof_eqic_yes
                movzbl 24(%rbx,%rcx), %eax
                movzbl 24(%r12,%rcx), %edx
                cmpl %edx, %eax
                je .Lkof_eqic_next
                cmpb $65, %al
                jb .Lkof_eqic_no
                cmpb $90, %al
                ja .Lkof_eqic_try_up
                addl $32, %eax
                cmpl %edx, %eax
                je .Lkof_eqic_next
                jmp .Lkof_eqic_no
            .Lkof_eqic_try_up:
                cmpb $97, %al
                jb .Lkof_eqic_no
                cmpb $122, %al
                ja .Lkof_eqic_no
                subl $32, %eax
                cmpl %edx, %eax
                je .Lkof_eqic_next
                jmp .Lkof_eqic_no
            .Lkof_eqic_next:
                incq %rcx
                jmp .Lkof_eqic_loop
            .Lkof_eqic_yes:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_eqic_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }}

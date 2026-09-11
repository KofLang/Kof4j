package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de busca em String (kof_string_contains/startsWith/endsWith/
 * indexOf/lastIndexOf) do runtime nativo. Domínio isolado do NativeRuntime --
 * refactor preserva semântica.
 */
public final class RuntimeStringSearch {

    private RuntimeStringSearch() {}

    public static void emitStringContains(StringBuilder sb) {
        sb.append("""
            .globl kof_string_contains
            .type kof_string_contains, @function
            kof_string_contains:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                testl %r13d, %r13d
                jz .Lkof_strcontains_found
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strcontains_no
                xorq %rcx, %rcx
            .Lkof_strcontains_outer:
                cmpl %r14d, %ecx
                jge .Lkof_strcontains_no
                leaq 24(%rbx,%rcx), %rax
                xorq %rdx, %rdx
            .Lkof_strcontains_inner:
                cmpl %r13d, %edx
                jge .Lkof_strcontains_found
                movzbl (%rax,%rdx), %r8d
                movzbl 24(%r12,%rdx), %r9d
                cmpl %r9d, %r8d
                jne .Lkof_strcontains_next
                incq %rdx
                jmp .Lkof_strcontains_inner
            .Lkof_strcontains_next:
                incq %rcx
                jmp .Lkof_strcontains_outer
            .Lkof_strcontains_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strcontains_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    public static void emitStringStartsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_starts_with
            .type kof_string_starts_with, @function
            kof_string_starts_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %ecx
                cmpl %ecx, %r13d
                jg .Lkof_strstarts_no
                xorq %rcx, %rcx
            .Lkof_strstarts_loop:
                cmpl %r13d, %ecx
                jge .Lkof_strstarts_found
                movzbl 24(%rbx,%rcx), %eax
                cmpb %al, 24(%r12,%rcx)
                jne .Lkof_strstarts_no
                incq %rcx
                jmp .Lkof_strstarts_loop
            .Lkof_strstarts_found:
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strstarts_no:
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    public static void emitStringEndsWith(StringBuilder sb) {
        sb.append("""
            .globl kof_string_ends_with
            .type kof_string_ends_with, @function
            kof_string_ends_with:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl 16(%r12), %r13d
                movl 16(%rbx), %r14d
                cmpl %r14d, %r13d
                jg .Lkof_strends_no
                movl %r14d, %ecx
                subl %r13d, %ecx
            .Lkof_strends_loop:
                cmpl %r14d, %ecx
                jge .Lkof_strends_found
                movzbl 24(%rbx,%rcx), %eax
                movl %ecx, %edx
                addl %r13d, %edx
                subl %r14d, %edx
                movzbl 24(%r12,%rdx), %edx
                cmpl %edx, %eax
                jne .Lkof_strends_no
                incq %rcx
                jmp .Lkof_strends_loop
            .Lkof_strends_found:
                movl $1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strends_no:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }



    public static void emitStringIndexOf(StringBuilder sb) {
        // bug 43 (face indexOf, 10/09): devolve o índice em CODE UNITS UTF-16
        // (contrato JVM/JS), não bytes UTF-8. Reusa o walk de code units
        // (.Lkof_substr_walk, emitStringSubstring): alvo de corte de par
        // astral é pulado — needles well-formed (tudo que o Kof permite em
        // literal) não casam numa 2ª unit, então o skip é exato.
        sb.append("""
            .globl kof_string_index_of
            .type kof_string_index_of, @function
            kof_string_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rdi, %rbx
                movq %rsi, %r12
                # totais em code units (walk p/ target grande vai ao fim)
                movq %rbx, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r13d                  # totalH
                movq %r12, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r14d                  # totalN
                movl %eax, %r15d                  # lenBytes da needle
                testl %r14d, %r14d
                jz .Lkof_idx_found0               # needle vazio -> 0 (JVM)
                cmpl %r13d, %r14d
                jg .Lkof_idx_notfound             # needle maior que o alvo
                xorl %ebp, %ebp                   # i = 0 (unit)
            .Lkof_idx_scan:
                movl %r13d, %r8d
                subl %r14d, %r8d
                cmpl %r8d, %ebp
                jg .Lkof_idx_notfound
                movq %rbx, %rdi
                movl %ebp, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Lkof_idx_next                # corte de par: nao casa aqui
                xorl %r9d, %r9d                   # j (byte da needle)
            .Lkof_idx_cmp:
                cmpl %r15d, %r9d
                jge .Lkof_idx_found
                movl %eax, %r10d
                addl %r9d, %r10d
                movzbl 24(%rbx,%r10), %r11d
                movzbl 24(%r12,%r9), %edx
                cmpl %edx, %r11d
                jne .Lkof_idx_next
                incl %r9d
                jmp .Lkof_idx_cmp
            .Lkof_idx_next:
                incl %ebp
                jmp .Lkof_idx_scan
            .Lkof_idx_found0:
                xorl %ebp, %ebp
            .Lkof_idx_found:
                movl %ebp, %eax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_idx_notfound:
                movl $-1, %eax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
    public static void emitStringLastIndexOf(StringBuilder sb) {
        // bug 43 (face lastIndexOf, 10/09): índice em CODE UNITS UTF-16
        // (contrato JVM/JS); varre do fim. Corte de par astral pulado (idem
        // indexOf). Needle vazio -> total de units (JVM: lastIndexOf("")=len).
        sb.append("""
            .globl kof_string_last_index_of
            .type kof_string_last_index_of, @function
            kof_string_last_index_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rbx, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r13d                  # totalH
                movq %r12, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r14d                  # totalN
                movl %eax, %r15d                  # lenBytes da needle
                testl %r14d, %r14d
                jz .Lkof_lidx_found_end           # vazio -> totalH
                cmpl %r13d, %r14d
                jg .Lkof_lidx_notfound            # needle maior que o alvo
                movl %r13d, %ebp
                subl %r14d, %ebp                  # i = totalH - totalN
            .Lkof_lidx_scan:
                testl %ebp, %ebp
                js .Lkof_lidx_notfound
                movq %rbx, %rdi
                movl %ebp, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Lkof_lidx_next               # corte de par: nao casa aqui
                xorl %r9d, %r9d
            .Lkof_lidx_cmp:
                cmpl %r15d, %r9d
                jge .Lkof_lidx_found
                movl %eax, %r10d
                addl %r9d, %r10d
                movzbl 24(%rbx,%r10), %r11d
                movzbl 24(%r12,%r9), %edx
                cmpl %edx, %r11d
                jne .Lkof_lidx_next
                incl %r9d
                jmp .Lkof_lidx_cmp
            .Lkof_lidx_next:
                decl %ebp
                jmp .Lkof_lidx_scan
            .Lkof_lidx_found:
                movl %ebp, %eax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_lidx_found_end:
                movl %r13d, %eax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_lidx_notfound:
                movl $-1, %eax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }


}

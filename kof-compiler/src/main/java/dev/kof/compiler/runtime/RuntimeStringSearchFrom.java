package dev.kof.compiler.runtime;

/**
 * Versões com índice inicial (§102) da busca em String: indexOf/lastIndexOf/
 * startsWith com o 2º parâmetro `from` respeitado em CODE UNITS UTF-16 com as
 * clampagens do JDK (from<0, from>totalH, needle vazia, corte de par astral).
 * Separado de RuntimeStringSearch (regra ≤500) — mesma classe de bug (o
 * helper de aridade 1 ignorava o 2º arg que o roteador já empilhava).
 */
public final class RuntimeStringSearchFrom {

    private RuntimeStringSearchFrom() {}

    public static void emitStringIndexOf2(StringBuilder sb) {
        sb.append("""
            .globl kof_string_index_of2
            .type kof_string_index_of2, @function
            kof_string_index_of2:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %ebp                   # from (crú)
                testl %ebp, %ebp
                jge .Lki2_from
                xorl %ebp, %ebp                   # from < 0 → 0
            .Lki2_from:
                movq %rbx, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r13d                  # totalH
                cmpl %r13d, %ebp
                jle .Lki2_clamped
                movl %r13d, %ebp                  # start = min(from, totalH)
            .Lki2_clamped:
                movq %r12, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r14d                  # totalN
                movl %eax, %r15d                  # lenBytes da needle
                testl %r14d, %r14d
                jz .Lki2_found                    # vazia → start (já clampado)
                cmpl %r13d, %r14d
                jg .Lki2_notfound
            .Lki2_scan:
                movl %r13d, %r8d
                subl %r14d, %r8d
                cmpl %r8d, %ebp
                jg .Lki2_notfound
                movq %rbx, %rdi
                movl %ebp, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Lki2_next
                xorl %r9d, %r9d
            .Lki2_cmp:
                cmpl %r15d, %r9d
                jge .Lki2_found
                movl %eax, %r10d
                addl %r9d, %r10d
                movzbl 24(%rbx,%r10), %r11d
                movzbl 24(%r12,%r9), %edx
                cmpl %edx, %r11d
                jne .Lki2_next
                incl %r9d
                jmp .Lki2_cmp
            .Lki2_next:
                incl %ebp
                jmp .Lki2_scan
            .Lki2_found:
                movl %ebp, %eax
                jmp .Lki2_ret
            .Lki2_notfound:
                movl $-1, %eax
            .Lki2_ret:
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitStringLastIndexOf2(StringBuilder sb) {
        // §102 (paridade absoluta): lastIndexOf(needle, from) em CODE UNITS
        // UTF-16 com as clampagens do JDK: from<0 -> -1; from>totalH ->
        // totalH; needle vazia -> min(from,totalH) (>=0); match só comeÃ§a em
        // posiÃ§ao <= from; corte de par astral pulado (walk ecx=1).
        sb.append("""
            .globl kof_string_last_index_of2
            .type kof_string_last_index_of2, @function
            kof_string_last_index_of2:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %ebp                   # from (crú)
                testl %ebp, %ebp
                jns .Ll2_fromok
                movl $-1, %eax                    # from < 0 -> -1 (JDK)
                jmp .Ll2_ret
            .Ll2_fromok:
                movq %rbx, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r13d                  # totalH
                cmpl %r13d, %ebp
                jle .Ll2_fromclamp
                movl %r13d, %ebp                  # from = min(from, totalH)
            .Ll2_fromclamp:
                movq %r12, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r14d                  # totalN
                movl %eax, %r15d                  # lenBytes da needle
                testl %r14d, %r14d
                jnz .Ll2_nonempty
                movl %ebp, %eax                   # needle vazia -> from (<=totalH)
                jmp .Ll2_ret
            .Ll2_nonempty:
                cmpl %r13d, %r14d
                jg .Ll2_notfound                  # needle > haystack
                movl %r13d, %eax
                subl %r14d, %eax                  # inicio maximo = totalH - totalN
                cmpl %eax, %ebp
                jle .Ll2_start
                movl %eax, %ebp                   # i = min(from, totalH - totalN)
            .Ll2_start:
                testl %ebp, %ebp
                js .Ll2_notfound
                movq %rbx, %rdi
                movl %ebp, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Ll2_next                     # corte de par: não casa
                xorl %r9d, %r9d
            .Ll2_cmp:
                cmpl %r15d, %r9d
                jge .Ll2_found
                movl %eax, %r10d
                addl %r9d, %r10d
                movzbl 24(%rbx,%r10), %r11d
                movzbl 24(%r12,%r9), %edx
                cmpl %edx, %r11d
                jne .Ll2_next
                incl %r9d
                jmp .Ll2_cmp
            .Ll2_next:
                decl %ebp
                jmp .Ll2_start
            .Ll2_found:
                movl %ebp, %eax
                jmp .Ll2_ret
            .Ll2_notfound:
                movl $-1, %eax
            .Ll2_ret:
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitStringStartsWith2(StringBuilder sb) {
        // §102: startsWith(needle, from) em CODE UNITS UTF-16 com as clampagens
        // do JDK: from<0 -> false; from>totalH -> false; needle vazia -> true
        // (se from<=totalH); corte de par -> false (walk ecx=1); resto é a
        // comparação de BYTES da needle a partir do byteOffset devolvido pelo
        // walk (needle well-formed nunca casa cortando par — bug 43).
        sb.append("""
            .globl kof_string_starts_with2
            .type kof_string_starts_with2, @function
            kof_string_starts_with2:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d                  # from (crú)
                testl %r13d, %r13d
                jns .Ls2_fromok
                xorl %eax, %eax                   # from < 0 -> false
                jmp .Ls2_ret
            .Ls2_fromok:
                movq %rbx, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r14d                  # totalH
                cmpl %r14d, %r13d
                jg .Ls2_false                     # from > totalH
                movq %r12, %rdi
                movl $0x7FFFFFFF, %esi
                call .Lkof_substr_walk
                movl %edx, %r8d                   # totalN
                testl %r8d, %r8d
                jnz .Ls2_nonempty
                movl $1, %eax                     # needle vazia -> true
                jmp .Ls2_ret
            .Ls2_nonempty:
                movq %rbx, %rdi
                movl %r13d, %esi                  # alvo: byteOffset de 'from'
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Ls2_false                    # corte de par astral
                movl %eax, %r9d                   # byteOffset
                movl 16(%rbx), %ecx
                movl 16(%r12), %edx               # lenBytes da needle
                addl %r9d, %edx
                cmpl %ecx, %edx
                ja .Ls2_false                     # não cabe
                xorl %edx, %edx
            .Ls2_cmp:
                cmpl 16(%r12), %edx
                jge .Ls2_true
                movzbl 24(%rbx,%r9), %eax
                movzbl 24(%r12,%rdx), %esi
                cmpl %esi, %eax
                jne .Ls2_false
                incq %r9
                incl %edx
                jmp .Ls2_cmp
            .Ls2_true:
                movl $1, %eax
                jmp .Ls2_ret
            .Ls2_false:
                xorl %eax, %eax
            .Ls2_ret:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}

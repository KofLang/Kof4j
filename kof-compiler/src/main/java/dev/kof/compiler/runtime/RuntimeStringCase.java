package dev.kof.compiler.runtime;

/**
 * D-STR-UNICODE (27/09, row 11) — Native {@code String.toUpperCase}/
 * {@code toLowerCase} fold per UTF-16 CODE UNIT (Unicode SIMPLE case mapping,
 * {@code Character.toUpperCase}/{@code toLowerCase} per unit), not ASCII-only.
 *
 * <p>The runtime String store is UTF-8 ({@code length@16} = bytes). The old
 * {@code kof_string_to_upper}/{@code kof_string_to_lower} did {@code ±0x20}
 * byte-by-byte in {@code a-z}/{@code A-Z} and never folded accents
 * ({@code "café".toUpperCase() == "CAFé"} on x86 while JVM/JS give
 * {@code "CAFÉ"} — NAT-STR01). These functions now WALK the UTF-8 code points:
 * ASCII is folded inline; each BMP code point (0x80..0xFFFF) is remapped via a
 * compact embedded table (generated from the JDK's simple case mappings by
 * {@link #data}); astral code points (4-byte sequences) pass through unchanged
 * (per-code-unit semantics — surrogates are not folded). Output is re-encoded
 * to UTF-8 with a dynamic byte length.
 *
 * <p>Oracle: the JVM per-code-unit fold ({@code Character.toUpperCase} per
 * char) — the golden inputs avoid locale/full-case-mapping exceptions
 * (ß→SS, ﬁ, İ, Deseret) that are outside the ratified per-code-unit scope.
 */
public final class RuntimeStringCase {

    private RuntimeStringCase() {}

    /** Number of simple mappings for the BMP half {@code 0x80..0xFFFF}. */
    public static int count(boolean upper) {
        int n = 0;
        for (int c = 0x80; c <= 0xFFFF; c++) {
            if (map(c, upper) != c) n++;
        }
        return n;
    }

    private static int map(int c, boolean upper) {
        return upper ? Character.toUpperCase(c) : Character.toLowerCase(c);
    }

    /** Emits the {@code .hword from, to} table (sorted ascending by {@code from}). */
    public static String data(boolean upper, String label) {
        StringBuilder sb = new StringBuilder(label).append(":\n");
        for (int c = 0x80; c <= 0xFFFF; c++) {
            int m = map(c, upper);
            if (m != c) {
                sb.append("    .hword 0x").append(Integer.toHexString(c))
                  .append(", 0x").append(Integer.toHexString(m)).append('\n');
            }
        }
        return sb.toString();
    }

    public static void emit(StringBuilder sb) {
        String t = """
            # kof_string_to_upper(str@rdi) -> String (fold Unicode por CODE UNIT)
            # Caminha os code points UTF-8; ASCII inline; BMP 0x80..0xFFFF na
            # tabela .Lkof_cu_up_tab (busca binaria); astral (4 bytes) intacto.
            .globl kof_string_to_upper
            .type kof_string_to_upper, @function
            kof_string_to_upper:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_cu_up_null
                movl 16(%rbx), %r12d
                leal 25(%r12,%r12,2), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $0, 16(%r13)
                movl $0, 20(%r13)
                leaq .Lkof_cu_up_tab(%rip), %r10
                movl $@UPN@, %r11d
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lkof_cu_up_loop:
                cmpl %r12d, %r14d
                jge .Lkof_cu_up_done
                movzbl 24(%rbx,%r14), %eax
                cmpl $0x80, %eax
                jb .Lkof_cu_up_ascii
                cmpl $0xE0, %eax
                jb .Lkof_cu_up_two
                cmpl $0xF0, %eax
                jb .Lkof_cu_up_three
                movl 24(%rbx,%r14), %eax
                movl %eax, 24(%r13,%r15)
                addq $4, %r14
                addq $4, %r15
                jmp .Lkof_cu_up_loop
            .Lkof_cu_up_two:
                andl $0x1F, %eax
                shll $6, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movl $2, %r8d
                jmp .Lkof_cu_up_fold
            .Lkof_cu_up_three:
                andl $0x0F, %eax
                shll $12, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 26(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movl $3, %r8d
                jmp .Lkof_cu_up_fold
            .Lkof_cu_up_ascii:
                cmpl $97, %eax
                jb .Lkof_cu_up_emit
                cmpl $122, %eax
                ja .Lkof_cu_up_emit
                subl $32, %eax
            .Lkof_cu_up_emit:
                movb %al, 24(%r13,%r15)
                incq %r14
                incq %r15
                jmp .Lkof_cu_up_loop
            .Lkof_cu_up_fold:
                movl %eax, %r9d
                addq %r8, %r14
                xorl %esi, %esi
                movl %r11d, %edi
                decl %edi
            .Lkof_cu_up_bs:
                cmpl %edi, %esi
                jg .Lkof_cu_up_nf
                leal (%rsi,%rdi), %eax
                shrl $1, %eax
                movzwl (%r10,%rax,4), %edx
                cmpl %edx, %r9d
                je .Lkof_cu_up_found
                jl .Lkof_cu_up_bs_hi
                leal 1(%rax), %esi
                jmp .Lkof_cu_up_bs
            .Lkof_cu_up_bs_hi:
                leal -1(%rax), %edi
                jmp .Lkof_cu_up_bs
            .Lkof_cu_up_found:
                movzwl 2(%r10,%rax,4), %r9d
            .Lkof_cu_up_nf:
                cmpl $0x80, %r9d
                jb .Lkof_cu_up_e1
                cmpl $0x800, %r9d
                jb .Lkof_cu_up_e2
                movl %r9d, %eax
                shrl $12, %eax
                orl $0xE0, %eax
                movb %al, 24(%r13,%r15)
                movl %r9d, %eax
                shrl $6, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                movl %r9d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 26(%r13,%r15)
                addq $3, %r15
                jmp .Lkof_cu_up_loop
            .Lkof_cu_up_e2:
                movl %r9d, %eax
                shrl $6, %eax
                orl $0xC0, %eax
                movb %al, 24(%r13,%r15)
                movl %r9d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                addq $2, %r15
                jmp .Lkof_cu_up_loop
            .Lkof_cu_up_e1:
                movb %r9b, 24(%r13,%r15)
                incq %r15
                jmp .Lkof_cu_up_loop
            .Lkof_cu_up_done:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lkof_cu_up_epi
            .Lkof_cu_up_null:
                movq %rbx, %rax
            .Lkof_cu_up_epi:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_string_to_lower(str@rdi) -> String (fold Unicode por CODE UNIT)
            .globl kof_string_to_lower
            .type kof_string_to_lower, @function
            kof_string_to_lower:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_cu_lo_null
                movl 16(%rbx), %r12d
                leal 25(%r12,%r12,2), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $0, 16(%r13)
                movl $0, 20(%r13)
                leaq .Lkof_cu_lo_tab(%rip), %r10
                movl $@LON@, %r11d
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lkof_cu_lo_loop:
                cmpl %r12d, %r14d
                jge .Lkof_cu_lo_done
                movzbl 24(%rbx,%r14), %eax
                cmpl $0x80, %eax
                jb .Lkof_cu_lo_ascii
                cmpl $0xE0, %eax
                jb .Lkof_cu_lo_two
                cmpl $0xF0, %eax
                jb .Lkof_cu_lo_three
                movl 24(%rbx,%r14), %eax
                movl %eax, 24(%r13,%r15)
                addq $4, %r14
                addq $4, %r15
                jmp .Lkof_cu_lo_loop
            .Lkof_cu_lo_two:
                andl $0x1F, %eax
                shll $6, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movl $2, %r8d
                jmp .Lkof_cu_lo_fold
            .Lkof_cu_lo_three:
                andl $0x0F, %eax
                shll $12, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 26(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movl $3, %r8d
                jmp .Lkof_cu_lo_fold
            .Lkof_cu_lo_ascii:
                cmpl $65, %eax
                jb .Lkof_cu_lo_emit
                cmpl $90, %eax
                ja .Lkof_cu_lo_emit
                addl $32, %eax
            .Lkof_cu_lo_emit:
                movb %al, 24(%r13,%r15)
                incq %r14
                incq %r15
                jmp .Lkof_cu_lo_loop
            .Lkof_cu_lo_fold:
                movl %eax, %r9d
                addq %r8, %r14
                xorl %esi, %esi
                movl %r11d, %edi
                decl %edi
            .Lkof_cu_lo_bs:
                cmpl %edi, %esi
                jg .Lkof_cu_lo_nf
                leal (%rsi,%rdi), %eax
                shrl $1, %eax
                movzwl (%r10,%rax,4), %edx
                cmpl %edx, %r9d
                je .Lkof_cu_lo_found
                jl .Lkof_cu_lo_bs_hi
                leal 1(%rax), %esi
                jmp .Lkof_cu_lo_bs
            .Lkof_cu_lo_bs_hi:
                leal -1(%rax), %edi
                jmp .Lkof_cu_lo_bs
            .Lkof_cu_lo_found:
                movzwl 2(%r10,%rax,4), %r9d
            .Lkof_cu_lo_nf:
                cmpl $0x80, %r9d
                jb .Lkof_cu_lo_e1
                cmpl $0x800, %r9d
                jb .Lkof_cu_lo_e2
                movl %r9d, %eax
                shrl $12, %eax
                orl $0xE0, %eax
                movb %al, 24(%r13,%r15)
                movl %r9d, %eax
                shrl $6, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                movl %r9d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 26(%r13,%r15)
                addq $3, %r15
                jmp .Lkof_cu_lo_loop
            .Lkof_cu_lo_e2:
                movl %r9d, %eax
                shrl $6, %eax
                orl $0xC0, %eax
                movb %al, 24(%r13,%r15)
                movl %r9d, %eax
                andl $0x3F, %eax
                orl $0x80, %eax
                movb %al, 25(%r13,%r15)
                addq $2, %r15
                jmp .Lkof_cu_lo_loop
            .Lkof_cu_lo_e1:
                movb %r9b, 24(%r13,%r15)
                incq %r15
                jmp .Lkof_cu_lo_loop
            .Lkof_cu_lo_done:
                movl %r15d, 16(%r13)
                movb $0, 24(%r13,%r15)
                movq %r13, %rax
                jmp .Lkof_cu_lo_epi
            .Lkof_cu_lo_null:
                movq %rbx, %rax
            .Lkof_cu_lo_epi:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
        """;
        t = t.replace("@UPN@", Integer.toString(count(true)))
             .replace("@LON@", Integer.toString(count(false)));
        sb.append(t);
        sb.append(data(true, ".Lkof_cu_up_tab"));
        sb.append(data(false, ".Lkof_cu_lo_tab"));
    }
}

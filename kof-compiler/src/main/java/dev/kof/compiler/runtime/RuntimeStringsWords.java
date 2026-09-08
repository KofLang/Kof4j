package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Conversores de PALAVRA kof.strings (STDLIB S2b.4): joinWords (split+join
 * com boundary HTTPServer/XMLParser) + wrappers toCamelCase/toPascalCase/
 * toSnakeCase/toKebabCase/slugify por mode. Extraído de RuntimeStringsConv
 * (gate ≤500); emissão encadeada — .s byte-idêntico.
 */
public final class RuntimeStringsWords {

    private RuntimeStringsWords() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── S2b.4 split+join de palavras (ASCII) ───────────────────
            # Words = sequências de [0-9A-Za-z]; boundary em: primeiro alnum
            # após não-alnum, lower/dígit→Upper, Upper→Upper+lower
            # (HTTPServer = http|Server, XMLParser = xml|Parser). ≥128 é
            # delimitador (NAT-STR01 — mesma regra nos 4 targets).
            # mode: 0=camel 1=pascal 2=snake 3=kebab 4=slug(-).
            # Saída nunca excede 2*len (sep + char por byte). Uma passada.
            _kof_strings_joinWords:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # v
                movl %esi, %r12d         # mode
                testq %rbx, %rbx
                jz .Lv_str_jw_orig
                movl 16(%rbx), %r13d     # len
                leal 25(%r13,%r13), %edi # 2*len + 25
                call kof_alloc
                movq %rax, %r15          # novo
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                xorl %r14d, %r14d        # i
                xorl %eax, %eax          # pos
                xorl %ecx, %ecx          # wc
                movl $-1, %edx           # prev
            .Lv_str_jw_loop:
                cmpl %r13d, %r14d
                jge .Lv_str_jw_term
                movzbl 24(%rbx,%r14), %esi   # c
                movl %esi, %edi
                subl $65, %edi
                cmpl $25, %edi
                jbe .Lv_str_jw_alnum_up
                movl %esi, %edi
                subl $97, %edi
                cmpl $25, %edi
                jbe .Lv_str_jw_alnum_lo
                movl %esi, %edi
                subl $48, %edi
                cmpl $9, %edi
                ja .Lv_str_jw_notalnum
                xorl %edi, %edi          # dígito: não upper
                jmp .Lv_str_jw_alnum
            .Lv_str_jw_alnum_up:
                movl $1, %edi
                jmp .Lv_str_jw_alnum
            .Lv_str_jw_alnum_lo:
                xorl %edi, %edi
            .Lv_str_jw_alnum:
                # newWord? prev==-1 | (upper && (pl | pd | (pu && nl)))
                cmpl $-1, %edx
                je .Lv_str_jw_nw
                cmpl $0, %edi
                je .Lv_str_jw_emit_low
                movl %edx, %r8d
                subl $97, %r8d
                cmpl $25, %r8d
                jbe .Lv_str_jw_nw
                movl %edx, %r8d
                subl $48, %r8d
                cmpl $9, %r8d
                jbe .Lv_str_jw_nw
                movl %edx, %r8d
                subl $65, %r8d
                cmpl $25, %r8d
                ja .Lv_str_jw_emit_low
                movl %r14d, %r8d
                incl %r8d
                cmpl %r13d, %r8d
                jge .Lv_str_jw_emit_low
                movzbl 24(%rbx,%r8), %r8d
                subl $97, %r8d
                cmpl $25, %r8d
                ja .Lv_str_jw_emit_low
            .Lv_str_jw_nw:
                # sep: mode>=2 e já saiu char
                testl %eax, %eax
                jz .Lv_str_jw_cap
                cmpl $2, %r12d
                jl .Lv_str_jw_cap
                movl $45, %r8d
                cmpl $2, %r12d
                jne .Lv_str_jw_sep
                movl $95, %r8d
            .Lv_str_jw_sep:
                movb %r8b, 24(%r15,%rax)
                incl %eax
            .Lv_str_jw_cap:
                movl $1, %r8d            # cap?
                cmpl $1, %r12d
                je .Lv_str_jw_emit_c
                cmpl $1, %r12d
                jg .Lv_str_jw_c0
                testl %ecx, %ecx
                jz .Lv_str_jw_c0
                jmp .Lv_str_jw_emit_c
            .Lv_str_jw_c0:
                xorl %r8d, %r8d
            .Lv_str_jw_emit_c:
                # r8=1 → se lower, -32; r8=0 → se upper, +32
                movl %esi, %r9d
                cmpl $0, %r8d
                je .Lv_str_jw_low
                movl %r9d, %r10d
                subl $97, %r10d
                cmpl $25, %r10d
                ja .Lv_str_jw_put
                subl $32, %r9d
                jmp .Lv_str_jw_put
            .Lv_str_jw_low:
                movl %r9d, %r10d
                subl $65, %r10d
                cmpl $25, %r10d
                ja .Lv_str_jw_put
                addl $32, %r9d
            .Lv_str_jw_put:
                movb %r9b, 24(%r15,%rax)
                incl %eax
                incl %ecx
                movl %esi, %edx
                incl %r14d
                jmp .Lv_str_jw_loop
            .Lv_str_jw_emit_low:
                movl %esi, %r9d
                movl %r9d, %r10d
                subl $65, %r10d
                cmpl $25, %r10d
                ja .Lv_str_jw_put2
                addl $32, %r9d
            .Lv_str_jw_put2:
                movb %r9b, 24(%r15,%rax)
                incl %eax
                movl %esi, %edx
                incl %r14d
                jmp .Lv_str_jw_loop
            .Lv_str_jw_notalnum:
                movl $-1, %edx
                incl %r14d
                jmp .Lv_str_jw_loop
            .Lv_str_jw_term:
                movl %eax, 16(%r15)
                movb $0, 24(%r15,%rax)
                movq %r15, %rax
                jmp .Lv_str_jw_done
            .Lv_str_jw_orig:
                movq %rbx, %rax
            .Lv_str_jw_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_strings_toCamelCase
            kof_strings_toCamelCase:
                movl $0, %esi
                jmp _kof_strings_joinWords
            .globl kof_strings_toPascalCase
            kof_strings_toPascalCase:
                movl $1, %esi
                jmp _kof_strings_joinWords
            .globl kof_strings_toSnakeCase
            kof_strings_toSnakeCase:
                movl $2, %esi
                jmp _kof_strings_joinWords
            .globl kof_strings_toKebabCase
            kof_strings_toKebabCase:
                movl $3, %esi
                jmp _kof_strings_joinWords
            .globl kof_strings_slugify
            kof_strings_slugify:
                movl $4, %esi
                jmp _kof_strings_joinWords
        """);
    }
}

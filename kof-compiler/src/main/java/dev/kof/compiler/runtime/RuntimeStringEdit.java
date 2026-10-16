package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de edição de String (kof_string_replace/split) do runtime
 * nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeStringEdit {

    private RuntimeStringEdit() {}

    public static void emitStringReplace(StringBuilder sb) {
        sb.append("""
            .globl kof_string_replace_char
            .type kof_string_replace_char, @function
            kof_string_replace_char:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, %r13d
                movl 16(%rbx), %r14d
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                xorl %ecx, %ecx
            .Lkof_replace_char_loop:
                cmpl %r14d, %ecx
                jge .Lkof_replace_char_done
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_replace_char_store
                movl %r13d, %eax
            .Lkof_replace_char_store:
                movb %al, 24(%r15,%rcx)
                incq %rcx
                jmp .Lkof_replace_char_loop
            .Lkof_replace_char_done:
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_string_replace
            .type kof_string_replace, @function
            kof_string_replace:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx          # str
                movq %rsi, %r12          # from (substring, not a single char)
                movq %rdx, %r13          # to
                movl 16(%rbx), %r14d     # str_len
                movl 16(%r12), %r15d     # from_len
                testl %r15d, %r15d
                jnz .Lkof_replace_count
                # empty `from`: return a copy of str unchanged
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r14d, 16(%r15)
                movl $0, 20(%r15)
                xorl %ecx, %ecx
            .Lkof_replace_copy_empty:
                cmpl %r14d, %ecx
                jge .Lkof_replace_done_empty
                movzbl 24(%rbx,%rcx), %eax
                movb %al, 24(%r15,%rcx)
                incq %rcx
                jmp .Lkof_replace_copy_empty
            .Lkof_replace_done_empty:
                movb $0, 24(%r15,%r14)
                movq %r15, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            .Lkof_replace_count:
                movl 16(%r13), %eax
                movl %eax, %r8d         # to_len
                xorl %ebp, %ebp         # occurrence count (callee-saved)
                xorl %ecx, %ecx         # scan position i
            .Lkof_replace_scan:
                cmpl %r14d, %ecx
                jge .Lkof_replace_alloc
                xorl %r10d, %r10d       # j
            .Lkof_replace_cmp:
                cmpl %r15d, %r10d
                jge .Lkof_replace_match
                leaq 24(%rbx,%rcx), %rax
                movzbl (%rax,%r10), %eax
                movzbl 24(%r12,%r10), %edx
                cmpb %dl, %al
                jne .Lkof_replace_no_match
                incq %r10
                jmp .Lkof_replace_cmp
            .Lkof_replace_match:
                incl %ebp
                addl %r15d, %ecx
                jmp .Lkof_replace_scan
            .Lkof_replace_no_match:
                incq %rcx
                jmp .Lkof_replace_scan
            .Lkof_replace_alloc:
                # result_len = str_len + count * (to_len - from_len)
                movl %r8d, %eax
                subl %r15d, %eax
                imull %ebp, %eax
                addl %r14d, %eax
                leal 25(%rax), %edi
                call kof_alloc
                movq %rax, %r9          # out
                movl $1, (%r9)
                movl $0, 4(%r9)
                movq $0, 8(%r9)
                movl $0, 16(%r9)        # length fixed at the end
                movl $0, 20(%r9)
                movl 16(%r13), %r8d     # to_len (restored after alloc)
                xorl %ecx, %ecx         # i (scan pos)
                xorl %r11d, %r11d       # k (out pos)
            .Lkof_replace_build:
                cmpl %r14d, %ecx
                jge .Lkof_replace_done
                xorl %r10d, %r10d       # j
            .Lkof_replace_bcmp:
                cmpl %r15d, %r10d
                jge .Lkof_replace_bmatch
                leaq 24(%rbx,%rcx), %rax
                movzbl (%rax,%r10), %eax
                movzbl 24(%r12,%r10), %edx
                cmpb %dl, %al
                jne .Lkof_replace_bcopy
                incq %r10
                jmp .Lkof_replace_bcmp
            .Lkof_replace_bmatch:
                xorl %r10d, %r10d
            .Lkof_replace_bto:
                cmpl %r8d, %r10d
                jge .Lkof_replace_bskip
                movzbl 24(%r13,%r10), %eax
                movb %al, 24(%r9,%r11)
                incq %r10
                incq %r11
                jmp .Lkof_replace_bto
            .Lkof_replace_bskip:
                addl %r15d, %ecx
                jmp .Lkof_replace_build
            .Lkof_replace_bcopy:
                movzbl 24(%rbx,%rcx), %eax
                movb %al, 24(%r9,%r11)
                incq %rcx
                incq %r11
                jmp .Lkof_replace_build
            .Lkof_replace_done:
                movb $0, 24(%r9,%r11)
                movl %r11d, 16(%r9)
                movq %r9, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }
    public static void emitStringSplit(StringBuilder sb) {
        sb.append("""
            .globl kof_string_split
            .type kof_string_split, @function
            kof_string_split:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                movq %rdi, %rbx
                movl %esi, %r12d
                movl 16(%rbx), %r13d
                movl $1, %r14d
                xorl %ecx, %ecx
            .Lkof_split_count:
                cmpl %r13d, %ecx
                jge .Lkof_split_alloc
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_split_count_next
                incl %r14d
            .Lkof_split_count_next:
                incq %rcx
                jmp .Lkof_split_count
            .Lkof_split_alloc:
                movl %r14d, %edi
                movq $8, %rsi
                call kof_array_alloc
                movq %rax, %r15
                xorl %r8d, %r8d
                xorl %ecx, %ecx
                xorl %r9d, %r9d
            .Lkof_split_outer:
                cmpl %r13d, %ecx
                jge .Lkof_split_lastpiece
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_split_outer_next
            .Lkof_split_piece:
                movl %ecx, %eax
                subl %r9d, %eax
                movl %eax, %r10d
                movq %rcx, 0(%rsp)
                movq %r9, 8(%rsp)
                movq %r10, 16(%rsp)
                movq %r8, 24(%rsp)
                leal 25(%r10), %edi
                call kof_alloc
                movq %rax, %r11
                movq 24(%rsp), %r8
                movq 16(%rsp), %r10
                movq 8(%rsp), %r9
                movl $1, (%r11)
                movl $0, 4(%r11)
                movq $0, 8(%r11)
                movl %r10d, 16(%r11)
                movl $0, 20(%r11)
                leaq 24(%r11), %rdi
                leaq 24(%rbx), %rsi
                addq %r9, %rsi
                movl %r10d, %edx
                call kof_memcpy
                movb $0, 24(%r11,%r10)
                movq %r11, %rax
                movq %r8, %rcx
                shlq $3, %rcx
                movq %rax, 24(%r15,%rcx)
                movq 0(%rsp), %rcx
                incl %r8d
                cmpl %r13d, %ecx
                jge .Lkof_split_done
                incl %ecx
                movq %rcx, %r9
                jmp .Lkof_split_outer
            .Lkof_split_outer_next:
                incq %rcx
                jmp .Lkof_split_outer
            .Lkof_split_lastpiece:
                cmpl %r13d, %r9d
                jge .Lkof_split_done
                movl %r13d, %ecx
                jmp .Lkof_split_piece
            .Lkof_split_done:
                # §111: Java String.split(regex, 0) REMOVE os vazios TRAILING
                # (`"a,"`→["a"], `","`→[], `"a,b,"`→["a","b"]), EXCETO o caso
                # especial de input VAZIO (`""`→[""], tamanho 1). O loop acima
                # contava pieces SEM o trim → JVM/Script 0/1/2 vs Native 2/3/3.
                # Elementos em 24(%r15)+i*8; piece vazio ⟺ nBytes 16(%piece)==0;
                # length do array ⟹ 16(%r15). r8d = nº de pieces materializados.
                cmpl $0, %r13d
                jne .Lkof_split_trim_i
                # input vazio: nenhum piece foi gravado; força [""] (1 piece).
                movq $25, %rdi
                call kof_alloc
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 16(%rax)            # nBytes = 0
                movl $0, 20(%rax)
                movb $0, 24(%rax)
                movq %rax, 24(%r15)          # element[0] = ""
                movl $1, 16(%r15)            # length = 1
                movq %r15, %rax
                jmp .Lkof_split_ret
            .Lkof_split_trim_i:
                movl %r8d, %ecx              # i = count
            .Lkof_split_trim_dec:
                decl %ecx                    # i--
                js .Lkof_split_set0          # todos vazios → tamanho 0
                movslq %ecx, %rax
                movq 24(%r15,%rax,8), %rsi   # piece[i]
                cmpl $0, 16(%rsi)
                je .Lkof_split_trim_dec      # vazio → continua removendo
                incl %ecx                    # count = i+1 (mantém até i)
                jmp .Lkof_split_setcount_final
            .Lkof_split_set0:
                xorl %ecx, %ecx
            .Lkof_split_setcount_final:
                movl %ecx, 16(%r15)          # novo length do array
                movq %r15, %rax
            .Lkof_split_ret:
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    /**
     * §738 (face NATIVA x86): {@code String.split("")} — o emissor mandava
     * {@code sep_char = 0} (NUL) ao {@code kof_string_split}, que nunca casa
     * (um NUL não existe no texto) → 1 elemento; o JVM/Script/JS devolvem UM
     * elemento por CARACTERE ({@code "abc".split("")} = 3). Este helper é
     * ADITIVO (o caminho do separador não-vazio fica intacto) e percorre por
     * CODE POINT UTF-8 — a direção já decidida no D-STR-UNICODE.
     *
     * <p>{@code limit} é a sobrecarga {@code (String, Int)}: {@code <= 0} =
     * ilimitado; {@code > 0} = no máximo {@code limit} pieces e a ÚLTIMA é o
     * RESTO não dividido (o contrato JDK, sem a poda §111 — o split por
     * caractere nunca produz piece vazio à direita).
     *
     * <p>Fronteira honesta: um caractere ASTRAL vira 1 elemento (o JVM, que
     * corta por code unit UTF-16, daria 2 para um par surrogado).
     */
    public static void emitStringSplitChars(StringBuilder sb) {
        sb.append("""
            .globl kof_string_split_chars
            .type kof_string_split_chars, @function
            kof_string_split_chars:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                movq %rdi, %rbx              # str
                movl %esi, %r12d             # limit (<=0 = ilimitado)
                movl 16(%rbx), %r13d         # nBytes
                xorl %r14d, %r14d            # nº de code points
                xorl %ecx, %ecx
            .Lkof_spc_count:
                cmpl %r13d, %ecx
                jge .Lkof_spc_count_done
                movzbl 24(%rbx,%rcx), %eax
                andl $0xC0, %eax
                cmpl $0x80, %eax
                je .Lkof_spc_count_next      # byte de continuação → não é começo
                incl %r14d
            .Lkof_spc_count_next:
                incq %rcx
                jmp .Lkof_spc_count
            .Lkof_spc_count_done:
                cmpl $0, %r13d
                jne .Lkof_spc_limit
                movl $1, %r14d               # "" -> [""] (1 elemento), como o JVM
                jmp .Lkof_spc_alloc
            .Lkof_spc_limit:
                testl %r12d, %r12d
                jle .Lkof_spc_alloc
                cmpl %r12d, %r14d
                jle .Lkof_spc_alloc
                movl %r12d, %r14d            # teto do array = limit
            .Lkof_spc_alloc:
                movl %r14d, %edi
                movq $8, %rsi
                call kof_array_alloc
                movq %rax, %r15
                xorl %r8d, %r8d              # idx do elemento
                xorl %ecx, %ecx              # byte index
            .Lkof_spc_loop:
                cmpl %r13d, %ecx
                jge .Lkof_spc_tail
                movzbl 24(%rbx,%rcx), %edx
                movl $1, %r10d               # comprimento do char (lead byte)
                movl %edx, %eax
                andl $0xE0, %eax
                cmpl $0xC0, %eax
                jne .Lkof_spc_l3
                movl $2, %r10d
                jmp .Lkof_spc_len
            .Lkof_spc_l3:
                movl %edx, %eax
                andl $0xF0, %eax
                cmpl $0xE0, %eax
                jne .Lkof_spc_l4
                movl $3, %r10d
                jmp .Lkof_spc_len
            .Lkof_spc_l4:
                movl %edx, %eax
                andl $0xF8, %eax
                cmpl $0xF0, %eax
                jne .Lkof_spc_len
                movl $4, %r10d
            .Lkof_spc_len:
                testl %r12d, %r12d
                jle .Lkof_spc_cut
                leal 1(%r8d), %eax           # idx+1 == limit -> último = RESTO
                cmpl %r12d, %eax
                jne .Lkof_spc_cut
                movl %r13d, %r10d
                subl %ecx, %r10d
            .Lkof_spc_cut:
                movq %rcx, 0(%rsp)
                movq %r8, 8(%rsp)
                movl %r10d, 16(%rsp)
                leal 25(%r10), %edi
                call kof_alloc
                movq %rax, %r11
                movl 16(%rsp), %r10d
                movq 8(%rsp), %r8
                movq 0(%rsp), %rcx
                movl $1, (%r11)
                movl $0, 4(%r11)
                movq $0, 8(%r11)
                movl %r10d, 16(%r11)
                movl $0, 20(%r11)
                leaq 24(%r11), %rdi
                leaq 24(%rbx), %rsi
                addq %rcx, %rsi
                movl %r10d, %edx
                call kof_memcpy
                movb $0, 24(%r11,%r10)
                movq %r11, %rax
                movq %r8, %rcx
                shlq $3, %rcx
                movq %rax, 24(%r15,%rcx)
                movq 0(%rsp), %rcx
                movl 16(%rsp), %r10d
                addq %r10, %rcx              # avança o char INTEIRO
                incl %r8d
                testl %r12d, %r12d
                jle .Lkof_spc_loop
                cmpl %r12d, %r8d
                jl .Lkof_spc_loop
            .Lkof_spc_tail:
                cmpl $0, %r13d
                jne .Lkof_spc_setlen
                movq $25, %rdi               # "" -> elemento [0] = ""
                call kof_alloc
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 16(%rax)
                movl $0, 20(%rax)
                movb $0, 24(%rax)
                movq %rax, 24(%r15)
            .Lkof_spc_setlen:
                movl %r14d, 16(%r15)
                movq %r15, %rax
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    /**
     * §738 (face NATIVA x86): {@code String.replace("", to)} — a agulha VAZIA.
     * O {@code kof_string_replace} procura a SUBSTRING da agulha; uma agulha de
     * 0 bytes nunca casa, então o native devolvia a string INTACTA (no-op) onde
     * o JVM/Script inserem {@code to} em TODA posição, inclusive nas pontas:
     * {@code "aXbXc".replace("", "-")} = {@code -a-X-b-X-c-},
     * {@code "".replace("", "-")} = {@code -}. ADITIVO — o caminho da agulha
     * não-vazia fica intacto.
     *
     * <p>Percorre por CODE POINT UTF-8 (a direção do D-STR-UNICODE), como o
     * {@link #emitStringSplitChars}. Fronteira honesta: um caractere ASTRAL
     * conta 1 (o JVM, por code unit UTF-16, inseriria 2 vezes).
     */
    public static void emitStringReplaceEmpty(StringBuilder sb) {
        sb.append("""
            .globl kof_string_replace_empty
            .type kof_string_replace_empty, @function
            kof_string_replace_empty:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                movq %rdi, %rbx              # str
                movq %rsi, %r12              # to
                movl 16(%rbx), %r13d         # nBytes(str)
                movl 16(%r12), %r9d          # L = nBytes(to)
                xorl %r14d, %r14d            # nº de code points
                xorl %ecx, %ecx
            .Lkof_rpe_count:
                cmpl %r13d, %ecx
                jge .Lkof_rpe_count_done
                movzbl 24(%rbx,%rcx), %eax
                andl $0xC0, %eax
                cmpl $0x80, %eax
                je .Lkof_rpe_count_next
                incl %r14d
            .Lkof_rpe_count_next:
                incq %rcx
                jmp .Lkof_rpe_count
            .Lkof_rpe_count_done:
                movl %r14d, %eax             # total = (nCP+1)*L + nBytes(str)
                incl %eax
                imull %r9d, %eax
                addl %r13d, %eax
                movl %eax, %r10d
                movq %r10, 0(%rsp)           # total sobrevive ao kof_alloc
                leal 25(%r10), %edi
                call kof_alloc
                movq %rax, %r15
                movq 0(%rsp), %r10           # total de volta
                movl 16(%r12), %r9d          # L (recarregado pós-alloc)
                movl 16(%rbx), %r13d         # nBytes(str)
                movl $1, (%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r10d, 16(%r15)
                movl $0, 20(%r15)
                movl %r10d, %eax
                movb $0, 24(%r15,%rax)
                leaq 24(%r15), %r11          # cursor de destino
                xorl %ecx, %ecx              # byte index de origem
            .Lkof_rpe_loop:
                cmpl %r13d, %ecx
                jge .Lkof_rpe_last
                movzbl 24(%rbx,%rcx), %edx
                movl $1, %r10d               # comprimento do char (lead byte)
                movl %edx, %eax
                andl $0xE0, %eax
                cmpl $0xC0, %eax
                jne .Lkof_rpe_l3
                movl $2, %r10d
                jmp .Lkof_rpe_copy
            .Lkof_rpe_l3:
                movl %edx, %eax
                andl $0xF0, %eax
                cmpl $0xE0, %eax
                jne .Lkof_rpe_l4
                movl $3, %r10d
                jmp .Lkof_rpe_copy
            .Lkof_rpe_l4:
                movl %edx, %eax
                andl $0xF8, %eax
                cmpl $0xF0, %eax
                jne .Lkof_rpe_copy
                movl $4, %r10d
            .Lkof_rpe_copy:
                xorl %edi, %edi              # j = 0  (copia `to`)
            .Lkof_rpe_to:
                cmpl %r9d, %edi
                jge .Lkof_rpe_char
                movzbl 24(%r12,%rdi), %eax
                movb %al, (%r11,%rdi)
                incl %edi
                jmp .Lkof_rpe_to
            .Lkof_rpe_char:
                addq %rdi, %r11              # cursor += L
                xorl %edx, %edx              # k = 0  (copia o char)
            .Lkof_rpe_ch:
                cmpl %r10d, %edx
                jge .Lkof_rpe_adv
                movq %rcx, %rax
                addq %rdx, %rax
                movzbl 24(%rbx,%rax), %eax
                movb %al, (%r11,%rdx)
                incl %edx
                jmp .Lkof_rpe_ch
            .Lkof_rpe_adv:
                addq %rdx, %r11              # cursor += cpLen
                addq %r10, %rcx              # origem += cpLen
                jmp .Lkof_rpe_loop
            .Lkof_rpe_last:
                xorl %edi, %edi              # `to` FINAL (a ponta direita)
            .Lkof_rpe_fin:
                cmpl %r9d, %edi
                jge .Lkof_rpe_ret
                movzbl 24(%r12,%rdi), %eax
                movb %al, (%r11,%rdi)
                incl %edi
                jmp .Lkof_rpe_fin
            .Lkof_rpe_ret:
                movq %r15, %rax
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // §655: kof_string_split_limit(str@rdi, sep_char@esi, limit@edx) — o
    // contrato JDK da sobrecarga (String, Int): limit>0 = no máximo `limit`
    // pieces e a ÚLTIMA é o resto não dividido (sem a poda §111),
    // limit==0 = ilimitado COM a poda §111 (o mesmo que kof_string_split),
    // limit<0 = ilimitado SEM a poda. Input vazio → [""] para TODO limit.
    // Espelha kof_string_split peça a peça; o scratch próprio (subq $48)
    // mantém os spills do caminho de peça nos MESMOS offsets 0-31(%rsp).
    public static void emitStringSplitLimit(StringBuilder sb) {
        sb.append("""
            .globl kof_string_split_limit
            .type kof_string_split_limit, @function
            kof_string_split_limit:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                subq $48, %rsp
                movq %rdi, %rbx
                movl %esi, %r12d
                movl %edx, 40(%rsp)
                movl 16(%rbx), %r13d
                movl $1, %r14d
                xorl %ecx, %ecx
            .Lkof_splitl_count:
                cmpl %r13d, %ecx
                jge .Lkof_splitl_cap
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_splitl_count_next
                incl %r14d
            .Lkof_splitl_count_next:
                incq %rcx
                jmp .Lkof_splitl_count
            .Lkof_splitl_cap:
                movl 40(%rsp), %eax
                testl %eax, %eax
                jle .Lkof_splitl_alloc
                cmpl %eax, %r14d
                jle .Lkof_splitl_alloc
                movl %eax, %r14d
            .Lkof_splitl_alloc:
                movl %r14d, %edi
                movq $8, %rsi
                call kof_array_alloc
                movq %rax, %r15
                xorl %r8d, %r8d
                xorl %ecx, %ecx
                xorl %r9d, %r9d
            .Lkof_splitl_outer:
                cmpl %r13d, %ecx
                jge .Lkof_splitl_lastpiece
                movl 40(%rsp), %eax
                testl %eax, %eax
                jle .Lkof_splitl_norm
                leal -1(%rax), %eax
                cmpl %eax, %r8d
                jne .Lkof_splitl_norm
                movl %r13d, %ecx
                jmp .Lkof_splitl_piece
            .Lkof_splitl_norm:
                movzbl 24(%rbx,%rcx), %eax
                cmpl %r12d, %eax
                jne .Lkof_splitl_outer_next
            .Lkof_splitl_piece:
                movl %ecx, %eax
                subl %r9d, %eax
                movl %eax, %r10d
                movq %rcx, 0(%rsp)
                movq %r9, 8(%rsp)
                movq %r10, 16(%rsp)
                movq %r8, 24(%rsp)
                leal 25(%r10), %edi
                call kof_alloc
                movq %rax, %r11
                movq 24(%rsp), %r8
                movq 16(%rsp), %r10
                movq 8(%rsp), %r9
                movl $1, (%r11)
                movl $0, 4(%r11)
                movq $0, 8(%r11)
                movl %r10d, 16(%r11)
                movl $0, 20(%r11)
                leaq 24(%r11), %rdi
                leaq 24(%rbx), %rsi
                addq %r9, %rsi
                movl %r10d, %edx
                call kof_memcpy
                movb $0, 24(%r11,%r10)
                movq %r11, %rax
                movq %r8, %rcx
                shlq $3, %rcx
                movq %rax, 24(%r15,%rcx)
                movq 0(%rsp), %rcx
                incl %r8d
                cmpl %r13d, %ecx
                jge .Lkof_splitl_done
                incl %ecx
                movq %rcx, %r9
                jmp .Lkof_splitl_outer
            .Lkof_splitl_outer_next:
                incq %rcx
                jmp .Lkof_splitl_outer
            .Lkof_splitl_lastpiece:
                movl %r13d, %ecx
                jmp .Lkof_splitl_piece
            .Lkof_splitl_done:
                cmpl $0, %r13d
                jne .Lkof_splitl_may_trim
                movq $25, %rdi
                call kof_alloc
                movl $1, (%rax)
                movl $0, 4(%rax)
                movq $0, 8(%rax)
                movl $0, 16(%rax)
                movl $0, 20(%rax)
                movb $0, 24(%rax)
                movq %rax, 24(%r15)
                movl $1, 16(%r15)
                movq %r15, %rax
                jmp .Lkof_splitl_ret
            .Lkof_splitl_may_trim:
                movl %r8d, %ecx
                movl 40(%rsp), %eax
                testl %eax, %eax
                jne .Lkof_splitl_setcount
            .Lkof_splitl_trim_dec:
                decl %ecx
                js .Lkof_splitl_set0
                movslq %ecx, %rax
                movq 24(%r15,%rax,8), %rsi
                cmpl $0, 16(%rsi)
                je .Lkof_splitl_trim_dec
                incl %ecx
                jmp .Lkof_splitl_setcount
            .Lkof_splitl_set0:
                xorl %ecx, %ecx
            .Lkof_splitl_setcount:
                movl %ecx, 16(%r15)
                movq %r15, %rax
            .Lkof_splitl_ret:
                addq $48, %rsp
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }    /**
     * §740: {@code String.split} com separador de MAIS de um caractere. Os
     * emissores nativos passavam só o PRIMEIRO BYTE do separador aos helpers de
     * {@code sep_char} único, então {@code "aXXb".split("XX")} dividia no
     * {@code 'X'} (3 fatias em vez de 2). Este helper é ADITIVO e COMPÕE o
     * {@code kof_string_index_of2} (busca de substring em code units) com o
     * {@code kof_string_substring} (fatia em code units): conta os casamentos
     * NÃO-sobrepostos avançando pelo comprimento do separador em code units,
     * materializa as fatias e mantém a poda §111 dos vazios à direita quando
     * {@code limit <= 0} (com {@code limit > 0} a última fatia é o RESTO, sem
     * poda — o contrato do §655).
     */
    public static void emitStringSplitStr(StringBuilder sb) {
        sb.append("""
            .globl kof_string_split_str
            .type kof_string_split_str, @function
            kof_string_split_str:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                movq %rdi, %rbx
                movq %rsi, %r12
                movl %edx, %r13d
                xorl %r14d, %r14d
                movl 16(%r12), %r10d
                xorl %ecx, %ecx
            .Lsstr_u_loop:
                cmpl %r10d, %ecx
                jge .Lsstr_u_done
                movzbl 24(%r12,%rcx), %eax
                movl %eax, %edx
                andl $0xC0, %edx
                cmpl $0x80, %edx
                je .Lsstr_u_next
                movl %eax, %edx
                andl $0xF8, %edx
                cmpl $0xF0, %edx
                jne .Lsstr_u_one
                addl $2, %r14d
                jmp .Lsstr_u_next
            .Lsstr_u_one:
                incl %r14d
            .Lsstr_u_next:
                incq %rcx
                jmp .Lsstr_u_loop
            .Lsstr_u_done:
                movl $1, %r15d
                xorl %ebp, %ebp
            .Lsstr_c_loop:
                testl %r13d, %r13d
                jle .Lsstr_c_search
                cmpl %r13d, %r15d
                jge .Lsstr_c_done
            .Lsstr_c_search:
                movq %rbx, %rdi
                movq %r12, %rsi
                movl %ebp, %edx
                call kof_string_index_of2
                cmpl $-1, %eax
                je .Lsstr_c_done
                incl %r15d
                movl %eax, %ebp
                addl %r14d, %ebp
                jmp .Lsstr_c_loop
            .Lsstr_c_done:
                movq %r15, 24(%rsp)
                movl %r15d, %edi
                movq $8, %rsi
                call kof_array_alloc
                movq %rax, 0(%rsp)
                xorl %ebp, %ebp
                movq $0, 8(%rsp)
            .Lsstr_m_loop:
                movq 8(%rsp), %rax
                movq 24(%rsp), %rcx
                decq %rcx
                cmpq %rcx, %rax
                jge .Lsstr_m_last
                movq %rbx, %rdi
                movq %r12, %rsi
                movl %ebp, %edx
                call kof_string_index_of2
                movl %eax, 16(%rsp)
                movq %rbx, %rdi
                movl %ebp, %esi
                movl %eax, %edx
                call kof_string_substring
                movq 0(%rsp), %r10
                movq 8(%rsp), %rcx
                movq %rax, 24(%r10,%rcx,8)
                movl 16(%rsp), %ebp
                addl %r14d, %ebp
                incq 8(%rsp)
                jmp .Lsstr_m_loop
            .Lsstr_m_last:
                movq %rbx, %rdi
                movl %ebp, %esi
                movl $-1, %edx
                call kof_string_substring
                movq 0(%rsp), %r10
                movq 8(%rsp), %rcx
                movq %rax, 24(%r10,%rcx,8)
                movq 0(%rsp), %r10
                movq 24(%rsp), %rcx
                movl %ecx, 16(%r10)
                testl %r13d, %r13d
                jne .Lsstr_ret               # §740 (JDK): a poda §111 so vale com
                                             # limit == 0; limit < 0 mantem TODOS
                movl 16(%rbx), %eax
                testl %eax, %eax
                jz .Lsstr_ret
                movl %ecx, %edx
            .Lsstr_trim:
                decl %edx
                js .Lsstr_zero
                movslq %edx, %rax
                movq 24(%r10,%rax,8), %rsi
                cmpl $0, 16(%rsi)
                je .Lsstr_trim
                incl %edx
                movl %edx, 16(%r10)
                jmp .Lsstr_ret
            .Lsstr_zero:
                movl $0, 16(%r10)
            .Lsstr_ret:
                movq %r10, %rax
                popq %r11
                popq %r10
                popq %r9
                popq %r8
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

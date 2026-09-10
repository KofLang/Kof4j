package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de operações em String (kof_string_charAt/substring/trim/
 * case/equalsIgnoreCase) do runtime nativo. Domínio isolado do NativeRuntime --
 * refactor preserva semântica.
 */
public final class RuntimeStringOps {

    private RuntimeStringOps() {}

    public static void emitStringCharAt(StringBuilder sb) {
        // bug 43 (metade char_at): s.charAt(i) no JVM/JS devolve o CODE UNIT
        // UTF-16 na posição i — não o byte UTF-8 cru. O runtime guarda bytes
        // UTF-8; caminhamos do início: sequência 1/2/3 bytes consume 1 code
        // unit, astral (4 bytes) consume 2 (high+low surrogate). O índice
        // pedido conta CODE UNITS; ao alcançá-lo devolvemos o code unit da
        // posição (high half se o par astral começa aqui, low half senão).
        sb.append("""
            .globl kof_string_char_at
            .type kof_string_char_at, @function
            kof_string_char_at:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl 16(%rdi), %r10d      # byteLen (bytes UTF-8)
                leaq 24(%rdi), %r11       # chars base
                testl %esi, %esi
                jl .Lkof_strcharAt_bounds
                xorl %ebx, %ebx           # byteOff = 0
                xorl %r12d, %r12d         # units consumidos
            .Lkof_strcharAt_walk:
                cmpl %r10d, %ebx
                jge .Lkof_strcharAt_bounds
                cmpl %esi, %r12d
                jge .Lkof_strcharAt_at
                movzbl (%r11,%rbx), %eax  # lead byte
                movb %al, %r13b
                andb $0x80, %r13b
                jz .Lkof_strcharAt_adv1
                movb %al, %r13b
                andb $0xE0, %r13b
                cmpb $0xC0, %r13b
                je .Lkof_strcharAt_adv2
                movb %al, %r13b
                andb $0xF0, %r13b
                cmpb $0xE0, %r13b
                je .Lkof_strcharAt_adv3
                leal 1(%r12), %eax        # astral: 2 code units
                cmpl %esi, %eax
                je .Lkof_strcharAt_low
                addl $2, %r12d
                addl $4, %ebx
                jmp .Lkof_strcharAt_walk
            .Lkof_strcharAt_adv1:
                addl $1, %r12d
                addl $1, %ebx
                jmp .Lkof_strcharAt_walk
            .Lkof_strcharAt_adv2:
                addl $1, %r12d
                addl $2, %ebx
                jmp .Lkof_strcharAt_walk
            .Lkof_strcharAt_adv3:
                addl $1, %r12d
                addl $3, %ebx
                jmp .Lkof_strcharAt_walk
            .Lkof_strcharAt_at:
                movzbl (%r11,%rbx), %eax
                movb %al, %r13b
                andb $0x80, %r13b
                jz .Lkof_strcharAt_ret
                movb %al, %r13b
                andb $0xE0, %r13b
                cmpb $0xC0, %r13b
                je .Lkof_strcharAt_dec2
                movb %al, %r13b
                andb $0xF0, %r13b
                cmpb $0xE0, %r13b
                je .Lkof_strcharAt_dec3
                jmp .Lkof_strcharAt_high
            .Lkof_strcharAt_high:
                movzbl (%r11,%rbx), %eax
                andl $0x07, %eax
                shll $18, %eax
                movzbl 1(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                shll $12, %ecx
                orl %ecx, %eax
                movzbl 2(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 3(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                subl $0x10000, %eax
                shrl $10, %eax
                addl $0xD800, %eax
                jmp .Lkof_strcharAt_ret
            .Lkof_strcharAt_low:
                movzbl (%r11,%rbx), %eax
                andl $0x07, %eax
                shll $18, %eax
                movzbl 1(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                shll $12, %ecx
                orl %ecx, %eax
                movzbl 2(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 3(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                subl $0x10000, %eax
                andl $0x3FF, %eax
                addl $0xDC00, %eax
                jmp .Lkof_strcharAt_ret
            .Lkof_strcharAt_dec2:
                movzbl (%r11,%rbx), %eax
                andl $0x1F, %eax
                shll $6, %eax
                movzbl 1(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                jmp .Lkof_strcharAt_ret
            .Lkof_strcharAt_dec3:
                movzbl (%r11,%rbx), %eax
                andl $0x0F, %eax
                shll $12, %eax
                movzbl 1(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 2(%r11,%rbx), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                jmp .Lkof_strcharAt_ret
            .Lkof_strcharAt_ret:
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_strcharAt_bounds:
                popq %r13
                popq %r12
                popq %rbx
                movl %esi, %r13d
                call kof_string_length
                movl %eax, %esi
                movl %r13d, %edi
                call kof_bounds_error
            """);
    }
    public static void emitStringSubstring(StringBuilder sb) {
        // bug 43 (metade substring, 10/09): substring conta CODE UNITS UTF-16
        // (contrato JVM/JS), não bytes UTF-8. .Lkof_substr_walk converte
        // code unit → byte offset; a cópia é a fatia de bytes entre as duas
        // fronteiras (par astral sempre inteiro, senão cut → diagnóstico).
        sb.append(".Lstr_substr_astral: .asciz \"Runtime error: substring cannot split an astral code point (native UTF-16 face pending, known-bugs 43)\"\n");
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
                movl %esi, %r12d                  # start (code units UTF-16)
                movl %edx, %r13d                  # end (0 = até o fim — call site 1-arg)
                testl %r12d, %r12d
                jl .Lkof_substr_bounds
                movq %rbx, %rdi
                movl %r12d, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Lkof_substr_astral_panic
                cmpl %r12d, %edx
                jb .Lkof_substr_bounds            # start > total de units
                movl %eax, %r15d                  # startBytes
                testl %r13d, %r13d
                jz .Lkof_substr_toend
                movq %rbx, %rdi
                movl %r13d, %esi
                call .Lkof_substr_walk
                testl %ecx, %ecx
                jne .Lkof_substr_astral_panic
                cmpl %r13d, %edx
                jb .Lkof_substr_bounds            # end > total de units
                cmpl %r15d, %eax
                jb .Lkof_substr_bounds            # end < start (off monótono)
                movl %eax, %r13d                  # endBytes
                jmp .Lkof_substr_copy
            .Lkof_substr_toend:
                movl 16(%rbx), %r13d              # nBytes (fim da string)
            .Lkof_substr_copy:
                movl %r13d, %r14d
                subl %r15d, %r14d                 # lenBytes (callee-saved p/ atravessar calls)
                leal 25(%r14), %edi
                call kof_alloc
                movq %rax, %r13                   # novo KofStr*
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl %r14d, 16(%r13)
                movl $0, 20(%r13)
                movq %r13, %rdi
                addq $24, %rdi
                movq %rbx, %rsi
                addq $24, %rsi
                addq %r15, %rsi
                movl %r14d, %edx
                call kof_memcpy
                movb $0, 24(%r13,%r14)
                movq %r13, %rax
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
            .Lkof_substr_astral_panic:
                leaq .Lstr_substr_astral(%rip), %rdi
                call kof_panic
            # walk: rdi=str, esi=target → eax=byteOff do target,
            # edx=units consumidas (=min(target,total)), ecx=1 se o target
            # caiu na 2ª unit de um par astral (corte de code point).
            .Lkof_substr_walk:
                xorl %eax, %eax
                xorl %edx, %edx
                xorl %ecx, %ecx
                movl 16(%rdi), %r8d
                leaq 24(%rdi), %r9
            .Lksw_loop:
                cmpl %esi, %edx
                jae .Lksw_hit
                cmpl %r8d, %eax
                jae .Lksw_end
                movzbl (%r9,%rax), %r10d
                movl %r10d, %r11d
                andb $0x80, %r11b
                jz .Lksw_c1
                movl %r10d, %r11d
                andb $0xE0, %r11b
                cmpb $0xC0, %r11b
                je .Lksw_c2
                movl %r10d, %r11d
                andb $0xF0, %r11b
                cmpb $0xE0, %r11b
                je .Lksw_c3
                leal 1(%rdx), %r11d
                cmpl %esi, %r11d
                je .Lksw_cut                      # target = low do par
                addl $2, %edx
                addl $4, %eax
                jmp .Lksw_loop
            .Lksw_c1:
                addl $1, %edx
                addl $1, %eax
                jmp .Lksw_loop
            .Lksw_c2:
                addl $1, %edx
                addl $2, %eax
                jmp .Lksw_loop
            .Lksw_c3:
                addl $1, %edx
                addl $3, %eax
                jmp .Lksw_loop
            .Lksw_hit:
                ret
            .Lksw_cut:
                movl $1, %ecx
                ret
            .Lksw_end:
                movl %r8d, %eax
                ret
            """);
    }
    public static void emitStringTrim(StringBuilder sb) {
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
    public static void emitStringCase(StringBuilder sb) {
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
    public static void emitStringEqualsIgnoreCase(StringBuilder sb) {
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

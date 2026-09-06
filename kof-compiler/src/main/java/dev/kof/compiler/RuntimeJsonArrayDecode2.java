package dev.kof.compiler;

/**
Emissão do ASM de jsonarraydecode2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeJsonArrayDecode2 {

    private RuntimeJsonArrayDecode2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                je .Ljad_f64_take
                cmpb $46, %al
                je .Ljad_f64_take
                cmpb $101, %al
                je .Ljad_f64_take
                cmpb $69, %al
                je .Ljad_f64_take
                cmpb $48, %al
                jb .Ljad_f64_build
                cmpb $57, %al
                ja .Ljad_f64_build
            .Ljad_f64_take:
                cmpq $127, %rbx
                jge .Ljad_f64_build
                movzbl 24(%r10,%r13), %eax
                movb %al, (%rsp,%rbx)
                incq %rbx
                incq %r13
                jmp .Ljad_f64_loop
            .Ljad_f64_build:
                testq %rbx, %rbx
                jz .Ljad_f64_zero
                movq $1, 128(%rsp)          # tag string (header nao sobrescreve o token)
                movq $0, 136(%rsp)
                movl %ebx, 144(%rsp)        # len
                xorq %rcx, %rcx
            .Ljad_f64_copy:
                cmpq %rbx, %rcx
                jge .Ljad_f64_call
                movzbl (%rsp,%rcx), %eax
                movb %al, 152(%rsp,%rcx)
                incq %rcx
                jmp .Ljad_f64_copy
            .Ljad_f64_call:
                movb $0, 152(%rsp,%rbx)
                leaq 128(%rsp), %rdi
                call kof_string_to_double
                addq $192, %rsp
                popq %rbx
                ret
            .Ljad_f64_zero:
                xorpd %xmm0, %xmm0
                addq $192, %rsp
                popq %rbx
                ret

            .globl kof_json_decode_double_array
            .type kof_json_decode_double_array, @function
            kof_json_decode_double_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Ljda_skip0:
                cmpl %r15d, %r13d
                jge .Ljda_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al                # '['
                je .Ljda_open
                incq %r13
                jmp .Ljda_skip0
            .Ljda_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljda_count:
                cmpl %r15d, %r13d
                jge .Ljda_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljda_alloc
                cmpb $44, %al                # ','
                je .Ljda_cnext
                cmpb $32, %al
                je .Ljda_cws
                cmpb $10, %al
                je .Ljda_cws
                cmpb $9, %al
                je .Ljda_cws
                incq %r14                    # qualquer char de numero conta
            .Ljda_cvskip:
                cmpl %r15d, %r13d
                jge .Ljda_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $44, %al
                je .Ljda_cnext2
                cmpb $93, %al
                je .Ljda_alloc
                incq %r13
                jmp .Ljda_cvskip
            .Ljda_cnext2:
                incq %r13
                jmp .Ljda_count
            .Ljda_cws:
                incq %r13
                jmp .Ljda_count
            .Ljda_cnext:
                incq %r13
                jmp .Ljda_count
            .Ljda_alloc:
                movl %r14d, %edi
                movl $8, %esi
                call kof_array_alloc
                movq %rax, %r12
                xorq %r13, %r13
            .Ljda_rescan0:
                cmpl %r15d, %r13d
                jge .Ljda_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljda_rescan1
                incq %r13
                jmp .Ljda_rescan0
            .Ljda_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljda_fill:
                cmpl %r15d, %r13d
                jge .Ljda_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljda_done
                cmpb $44, %al
                je .Ljda_fnext
                cmpb $32, %al
                je .Ljda_fws
                cmpb $10, %al
                je .Ljda_fws
                cmpb $9, %al
                je .Ljda_fws
                call .Ljad_f64_at            # xmm0 = valor, r13 ja avancado
                movq %r14, %rcx
                shlq $3, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movsd %xmm0, (%rcx)
                incq %r14
                jmp .Ljda_fill
            .Ljda_fnext:
                incq %r13
                jmp .Ljda_fill
            .Ljda_fws:
                incq %r13
                jmp .Ljda_fill
            .Ljda_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljda_empty:
                xorl %edi, %edi
                movl $8, %esi
                call kof_array_alloc
                jmp .Ljda_done
            """);
    }
}
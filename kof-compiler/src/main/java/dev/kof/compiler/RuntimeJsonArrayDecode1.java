package dev.kof.compiler;

/**
Emissão do ASM de jsonarraydecode1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeJsonArrayDecode1 {

    private RuntimeJsonArrayDecode1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_json_decode_int_array
            .type kof_json_decode_int_array, @function
            kof_json_decode_int_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                call kof_json_decode_int_list
                movq %rax, %r12              # lista
                movq %r12, %rdi
                call kof_list_size
                movl %eax, %r13d             # n
                movl %r13d, %edi
                movl $4, %esi
                call kof_array_alloc         # (n, 4)
                movq %rax, %r14              # array
                xorq %r15, %r15              # i
            .Ljdia_fill:
                cmpq %r13, %r15
                jge .Ljdia_done
                movq %r12, %rdi
                movq %r15, %rsi
                call kof_list_get
                leaq (%r14,%r15,4), %rcx
                addq $24, %rcx
                movl %eax, (%rcx)
                incq %r15
                jmp .Ljdia_fill
            .Ljdia_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_decode_string_array
            .type kof_json_decode_string_array, @function
            kof_json_decode_string_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                call kof_json_decode_string_list
                movq %rax, %r12
                movq %r12, %rdi
                call kof_list_size
                movl %eax, %r13d
                movl %r13d, %edi
                movl $8, %esi
                call kof_array_alloc         # (n, 8) ponteiros
                movq %rax, %r14
                xorq %r15, %r15
            .Ljdsa_fill:
                cmpq %r13, %r15
                jge .Ljdsa_done
                movq %r12, %rdi
                movq %r15, %rsi
                call kof_list_get
                leaq (%r14,%r15,8), %rcx
                addq $24, %rcx
                movq %rax, (%rcx)
                incq %r15
                jmp .Ljdsa_fill
            .Ljdsa_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_parse_i64-like para JSON: rbx=json KofString*, edx=pos
            # -> rax=valor, rdx=nova pos (para apos o ultimo digito/sinal)
            .Ljad_i64_at:
                movl 16(%rbx), %ecx          # json len
            .Ljadi_ws:
                cmpl %ecx, %edx
                jge .Ljadi_bad
                movzbl 24(%rbx,%rdx), %eax
                cmpb $32, %al
                je .Ljadi_ws_inc
                cmpb $9, %al
                je .Ljadi_ws_inc
                cmpb $10, %al
                je .Ljadi_ws_inc
                cmpb $13, %al
                je .Ljadi_ws_inc
                jmp .Ljadi_sign
            .Ljadi_ws_inc:
                incq %rdx
                jmp .Ljadi_ws
            .Ljadi_sign:
                xorq %r11, %r11              # acc
                xorl %esi, %esi              # neg
                cmpl %ecx, %edx
                jge .Ljadi_bad
                movzbl 24(%rbx,%rdx), %eax
                cmpb $45, %al                # '-'
                je .Ljadi_neg
                cmpb $43, %al                # '+'
                je .Ljadi_pos
                jmp .Ljadi_dcheck
            .Ljadi_neg:
                movl $1, %esi
            .Ljadi_pos:
                incq %rdx
            .Ljadi_dcheck:
                cmpl %ecx, %edx
                jge .Ljadi_bad               # sinal sem digitos
                movzbl 24(%rbx,%rdx), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Ljadi_bad
            .Ljadi_digit:
                imulq $10, %r11
                movzbl %al, %eax
                addq %rax, %r11
                incq %rdx
                cmpl %ecx, %edx
                jge .Ljadi_end
                movzbl 24(%rbx,%rdx), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Ljadi_end                # nao-digito encerra o numero
                movzbl %al, %eax
                jmp .Ljadi_digit
            .Ljadi_end:
                movq %r11, %rax
                testl %esi, %esi
                jz .Ljadi_ok
                negq %rax
            .Ljadi_ok:
                ret
            .Ljadi_bad:
                xorl %edx, %edx              # pos invalida -> trata como fim
                xorl %eax, %eax
                ret

            .globl kof_json_decode_long_array
            .type kof_json_decode_long_array, @function
            kof_json_decode_long_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp               # [rsp]=len, [rsp+8]=count
                movq %rdi, %rbx              # json
                movl 16(%rbx), %r15d         # len
                xorq %r13, %r13              # i
            .Ljla_skip0:
                cmpl %r15d, %r13d
                jge .Ljla_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al                # '['
                je .Ljla_open
                incq %r13
                jmp .Ljla_skip0
            .Ljla_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljla_count:
                cmpl %r15d, %r13d
                jge .Ljla_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljla_alloc
                cmpb $44, %al                # ','
                je .Ljla_cnext
                cmpb $32, %al
                je .Ljla_cws
                cmpb $10, %al
                je .Ljla_cws
                cmpb $9, %al
                je .Ljla_cws
                cmpb $45, %al                # '-' (inicio de valor)
                je .Ljla_cvskip
                cmpb $43, %al
                je .Ljla_cvskip
                cmpb $48, %al
                jb .Ljla_cws
                cmpb $57, %al
                ja .Ljla_cws
                incq %r14                    # primeiro digito -> conta elemento
            .Ljla_cvskip:
                cmpl %r15d, %r13d
                jge .Ljla_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $44, %al
                je .Ljla_cnext2
                cmpb $93, %al
                je .Ljla_alloc
                incq %r13
                jmp .Ljla_cvskip
            .Ljla_cnext2:
                incq %r13
                jmp .Ljla_count
            .Ljla_cws:
                incq %r13
                jmp .Ljla_count
            .Ljla_cnext:
                incq %r13
                jmp .Ljla_count
            .Ljla_alloc:
                movl %r14d, %edi
                movl $8, %esi
                call kof_array_alloc
                movq %rax, %r12              # array
                # pass 2: preencher — reposiciona i apos '['
                xorq %r13, %r13
            .Ljla_rescan0:
                cmpl %r15d, %r13d
                jge .Ljla_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljla_rescan1
                incq %r13
                jmp .Ljla_rescan0
            .Ljla_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljla_fill:
                cmpl %r15d, %r13d
                jge .Ljla_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al                # ']'
                je .Ljla_done
                cmpb $44, %al                # ','
                je .Ljla_fnext
                cmpb $32, %al
                je .Ljla_fws
                cmpb $10, %al
                je .Ljla_fws
                cmpb $9, %al
                je .Ljla_fws
                # parse long em r13
                movq %r13, %rdx
                call .Ljad_i64_at            # rax=valor, rdx=nova pos
                # store 8 bytes em arr+24+idx*8
                movq %r14, %rcx
                shlq $3, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movq %rax, (%rcx)
                incq %r14
                movq %rdx, %r13              # nova pos
                jmp .Ljla_fill
            .Ljla_fnext:
                incq %r13
                jmp .Ljla_fill
            .Ljla_fws:
                incq %r13
                jmp .Ljla_fill
            .Ljla_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljla_empty:
                xorl %edi, %edi
                movl $8, %esi
                call kof_array_alloc
                jmp .Ljla_done

            .globl kof_json_decode_bool_array
            .type kof_json_decode_bool_array, @function
            kof_json_decode_bool_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movl 16(%rbx), %r15d
                xorq %r13, %r13
            .Ljba_skip0:
                cmpl %r15d, %r13d
                jge .Ljba_empty
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljba_open
                incq %r13
                jmp .Ljba_skip0
            .Ljba_open:
                incq %r13
                xorq %r14, %r14              # count
            .Ljba_count:
                cmpl %r15d, %r13d
                jge .Ljba_alloc
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljba_alloc
                cmpb $44, %al
                je .Ljba_cnext
                cmpb $32, %al
                je .Ljba_cws
                cmpb $10, %al
                je .Ljba_cws
                cmpb $9, %al
                je .Ljba_cws
                cmpb $116, %al               # 't' (true)
                je .Ljba_ct
                cmpb $102, %al               # 'f' (false)
                je .Ljba_cf
                jmp .Ljba_cws
            .Ljba_ct:
                incq %r14                    # true
                addq $4, %r13                # pula "true"
                jmp .Ljba_count
            .Ljba_cf:
                incq %r14                    # false
                addq $5, %r13                # pula "false"
                jmp .Ljba_count
            .Ljba_cnext:
                incq %r13
                jmp .Ljba_count
            .Ljba_cws:
                incq %r13
                jmp .Ljba_count
            .Ljba_alloc:
                movl %r14d, %edi
                movl $1, %esi
                call kof_array_alloc
                movq %rax, %r12
                xorq %r13, %r13
            .Ljba_rescan0:
                cmpl %r15d, %r13d
                jge .Ljba_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $91, %al
                je .Ljba_rescan1
                incq %r13
                jmp .Ljba_rescan0
            .Ljba_rescan1:
                incq %r13
                xorq %r14, %r14              # idx
            .Ljba_fill:
                cmpl %r15d, %r13d
                jge .Ljba_done
                movzbl 24(%rbx,%r13), %eax
                cmpb $93, %al
                je .Ljba_done
                cmpb $44, %al
                je .Ljba_fnext
                cmpb $32, %al
                je .Ljba_fws
                cmpb $10, %al
                je .Ljba_fws
                cmpb $9, %al
                je .Ljba_fws
                cmpb $116, %al               # 't'
                je .Ljba_ft
                cmpb $102, %al               # 'f'
                je .Ljba_ff
                incq %r13
                jmp .Ljba_fill
            .Ljba_ft:
                movq %r14, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movb $1, (%rcx)
                addq $4, %r13
                incq %r14
                jmp .Ljba_fill
            .Ljba_ff:
                movq %r14, %rcx
                addq $24, %rcx
                addq %r12, %rcx
                movb $0, (%rcx)
                addq $5, %r13
                incq %r14
                jmp .Ljba_fill
            .Ljba_fnext:
                incq %r13
                jmp .Ljba_fill
            .Ljba_fws:
                incq %r13
                jmp .Ljba_fill
            .Ljba_done:
                movq %r12, %rax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Ljba_empty:
                xorl %edi, %edi
                movl $1, %esi
                call kof_array_alloc
                jmp .Ljba_done

            # helper: parser FP minimalista sobre (rbx=json, r13=pos)
            # -> xmm0 = valor, r13 = pos apos o token. Aceita [-+0-9.eE].
            .Ljad_f64_at:
                pushq %rbx
                subq $192, %rsp             # token em 0(%rsp), KofString em 128(%rsp)
                movq %rbx, %r10             # salva rbx (json) em r10
                xorq %rbx, %rbx             # len do token
            .Ljad_f64_loop:
                cmpl 16(%r10), %r13d
                jge .Ljad_f64_build
                movl 16(%r10), %eax
                cmpl %eax, %r13d
                jge .Ljad_f64_build
                movzbl 24(%r10,%r13), %eax
                cmpb $43, %al
                je .Ljad_f64_take
                cmpb $45, %al
            """);
    }
}
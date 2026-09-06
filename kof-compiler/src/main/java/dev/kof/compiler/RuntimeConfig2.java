package dev.kof.compiler;

/**
Emissão do ASM de config2 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeConfig2 {

    private RuntimeConfig2() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                call kof_string_from_literal
                movq %rax, %r14              # r14 = "${"
                movq %r12, %rdi
                movq %r14, %rsi
                call kof_string_index_of
                cmpl $-1, %eax
                je .Lkci_done                # sem "${": pronto
                movl %eax, 0(%rsp)           # spill: start
                # tail = value[start+2 .. len]
                movq %r12, %rdi
                movl 0(%rsp), %esi
                addl $2, %esi
                movl 16(%r12), %edx
                call kof_string_substring
                testq %rax, %rax
                jz .Lkci_done
                movq %rax, %rbx              # rbx = tail
                # rel = index_of(tail, "}")
                leaq .Lkci_close(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %r14              # r14 = "}"
                movq %rbx, %rdi
                movq %r14, %rsi
                call kof_string_index_of
                cmpl $-1, %eax
                je .Lkci_done                # sem "}" -> literal
                leal 2(%rax), %r9d           # r9 = end (relativo ao start+2... ver: 2+rel == start+2+rel - start) 
                addl 0(%rsp), %r9d           # r9 = end absoluto
                movl %r9d, 4(%rsp)           # spill end
                # ref = value[start+2 .. end]
                movq %r12, %rdi
                movl 0(%rsp), %esi
                addl $2, %esi
                movl %r9d, %edx
                call kof_string_substring
                movq %rax, %r14              # r14 = ref KofString
                movq %r14, %rdi
                call kof_config_lookup       # resolve referência
                testq %rax, %rax
                jz .Lkci_done                # ref inexistente -> literal
                movq %rax, %r15              # r15 = resolved
                # prefixo = value[0..start]
                movq %r12, %rdi
                xorl %esi, %esi
                movl 0(%rsp), %edx
                call kof_string_substring
                movq %rax, %rbx              # rbx = prefixo
                # sufixo = value[end+1 .. len]
                movq %r12, %rdi
                movl 4(%rsp), %esi
                addl $1, %esi
                movl 16(%r12), %edx
                call kof_string_substring
                movq %rax, 8(%rsp)           # spill sufixo
                # tmp = resolved + sufixo
                movq %r15, %rdi
                movq 8(%rsp), %rsi
                call kof_string_concat
                movq %rax, %r15              # r15 = (resolved+sufixo)
                # result = prefixo + tmp
                movq %rbx, %rdi
                movq %r15, %rsi
                call kof_string_concat
                movq %rax, %r12              # novo valor corrente
                incl %r13d
                jmp .Lkci_loop
            .Lkci_done:
                movq %r12, %rax
            .Lkci_ret:
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkci_dollar: .asciz "${"
            .Lkci_close:  .asciz "}"

            # kof_config_required(rdi=key) -> KofString*|panic CONF002

            # kof_config_required(rdi=key) -> KofString*|panic CONF002
            kof_config_required:
                pushq %rbx
                subq $8, %rsp
                call kof_config_lookup
                addq $8, %rsp
                testq %rax, %rax
                jz .Lcfg_req_missing
                popq %rbx
                ret
            .Lcfg_req_missing:          # caminho sem chave: mensagem no rodata
                leaq .Lcfg_req_msg(%rip), %rdi
                call kof_panic
            .Lcfg_req_missing_key:
                movq %r12, %rsi
                movq %rbx, %rdi
                call kof_panic
            .Lcfg_req_msg: .asciz "Kof config: missing required key (CONF001)"

            kof_config_env:
                leaq 24(%rdi), %rdi          # nome KofString -> C-string (data NUL-terminada)
                jmp kof_env_getc

            kof_config_has:
                call kof_config_lookup
                testq %rax, %rax
                setne %al
                movzbq %al, %rax
                ret

            kof_config_str:
                pushq %rbx
                movq %rsi, %rbx              # default (lookup preserva rbx)
                call kof_config_lookup
                testq %rax, %rax
                cmovzq %rbx, %rax
                popq %rbx
                ret

            kof_config_int:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lci_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lci_def
                # range int32
                cmpq $2147483647, %rax
                jg .Lci_def
                cmpq $-2147483648, %rax
                jl .Lci_def
                popq %rbx
                ret
            .Lci_def:
                movl %ebx, %eax
                popq %rbx
                ret

            kof_config_long:
                pushq %rbx
                movq %rsi, %rbx              # default long
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcl_def
                movq %rax, %rdi
                call .Lcfg_parse_i64
                testl %edx, %edx
                jz .Lcl_def
                popq %rbx
                ret
            .Lcl_def:
                movq %rbx, %rax
                popq %rbx
                ret

            kof_config_bool:
                pushq %rbx
                movl %esi, %ebx              # default
                call kof_config_lookup
                testq %rax, %rax
                jz .Lcb_def
                movl 16(%rax), %ecx          # len
                leaq 24(%rax), %rdx          # data
                # trim rapido nas bordas
                xorq %r9, %r9
            .Lcb_tls:
                cmpq %rcx, %r9
                jge .Lcb_def
                movzbl (%rdx,%r9), %eax
                cmpb $32, %al
                je .Lcb_tls1
                cmpb $9, %al
                je .Lcb_tls1
                jmp .Lcb_tle
            .Lcb_tls1:
                incq %r9
                jmp .Lcb_tls
            .Lcb_tle:
                movq %rcx, %r10
            .Lcb_tle_loop:
                cmpq %r9, %r10
                jle .Lcb_def
                movzbl -1(%rdx,%r10), %eax
                cmpb $32, %al
                je .Lcb_tle1
                cmpb $9, %al
                je .Lcb_tle1
                jmp .Lcb_dispatch
            .Lcb_tle1:
                decq %r10
                jmp .Lcb_tle_loop
            .Lcb_dispatch:
                subq %r9, %r10               # len aparado
                leaq (%rdx), %rsi            # rsi = data do valor (contrato ci_match)
                addq %r9, %rsi               # + trim esquerdo
                # true / yes / 1 -> 1
                cmpq $4, %r10
                jne .Lcb_chk3
                leaq .Lcfg_w_true(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk3:
                cmpq $3, %r10
                jne .Lcb_chk1
                leaq .Lcfg_w_yes(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk1:
                cmpq $1, %r10
                jne .Lcb_chk5
                leaq .Lcfg_w_one(%rip), %rdi
                jmp .Lcb_cmp_true
            .Lcb_chk5:
                cmpq $5, %r10
                jne .Lcb_chk2
                leaq .Lcfg_w_false(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_chk2:
                cmpq $2, %r10
                jne .Lcb_def
                leaq .Lcfg_w_no(%rip), %rdi
                jmp .Lcb_cmp_false
            .Lcb_cmp_true:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                movl $1, %eax
                jmp .Lcb_ret1
            .Lcb_cmp_false:
                call .Lcb_ci_match
                testl %eax, %eax
                jz .Lcb_def
                xorl %eax, %eax
                jmp .Lcb_ret1
            .Lcb_def:
                movl %ebx, %eax
            .Lcb_ret1:
                popq %rbx
                ret

            # .Lcb_ci_match(rdi=candidato, rsi=data, r10=len aparado) -> eax=1 se igual
            .Lcb_ci_match:
                pushq %rbx
                movl %r10d, %ebx
                xorl %eax, %eax
                testl %ebx, %ebx
                jle .Lcbm_no
                xorq %rcx, %rcx
            .Lcbm_loop:
                movzbl (%rdi,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                orb $0x20, %r8b
                orb $0x20, %r9b
                cmpl %r9d, %r8d
                jne .Lcbm_no
                incq %rcx
                cmpl %ebx, %ecx
                jl .Lcbm_loop
                movl $1, %eax
            .Lcbm_no:
                popq %rbx
                ret

            # .Lcfg_parse_i64(rdi=KofString*) -> rax=valor, edx=1 ok | edx=0 invalido
            .Lcfg_parse_i64:
                movl 16(%rdi), %ecx          # len
                leaq 24(%rdi), %r8           # data
                xorq %r9, %r9
            .Lpi_tls:
                cmpq %rcx, %r9
                jge .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $32, %al
                je .Lpi_tls1
                cmpb $9, %al
                je .Lpi_tls1
                jmp .Lpi_tle
            .Lpi_tls1:
                incq %r9
                jmp .Lpi_tls
            .Lpi_tle:
                movq %rcx, %r10              # fim exclusivo
            .Lpi_tle_l:
                cmpq %r9, %r10
                jle .Lpi_bad                 # vazio apos trim
                movzbl -1(%r8,%r10), %eax
                cmpb $32, %al
                je .Lpi_tle1
                cmpb $9, %al
                je .Lpi_tle1
                jmp .Lpi_sign
            .Lpi_tle1:
                decq %r10
                jmp .Lpi_tle_l
            .Lpi_sign:
                xorq %r11, %r11              # acc
                xorl %esi, %esi              # neg
                cmpq %r9, %r10
                jle .Lpi_bad
                movzbl (%r8,%r9), %eax
                cmpb $45, %al                # '-'
                je .Lpi_negset
                cmpb $43, %al                # '+'
                je .Lpi_posskip
                jmp .Lpi_dcheck
            .Lpi_negset:
                movl $1, %esi
            .Lpi_posskip:
                incq %r9
            .Lpi_dcheck:
                cmpq %r9, %r10
                jle .Lpi_bad                 # sinal sem digitos
            .Lpi_digit:
                movzbl (%r8,%r9), %eax
                subb $48, %al
                cmpb $9, %al
                ja .Lpi_bad
                imulq $10, %r11
                movzbl %al, %eax
                addq %rax, %r11
                incq %r9
                cmpq %r9, %r10
                jg .Lpi_digit
                movq %r11, %rax
                testl %esi, %esi
                jz .Lpi_ok
                negq %rax
            .Lpi_ok:
                movl $1, %edx
                ret
            .Lpi_bad:
                xorl %edx, %edx
                xorl %eax, %eax
                ret
            """);
    }
}
package dev.kof.compiler;

/**
Emissão do ASM de config1 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeConfig1 {

    private RuntimeConfig1() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lcfg_s_kofconfig:  .asciz "KOF_CONFIG"
            .Lcfg_s_kofprofile: .asciz "KOF_PROFILE"
            .Lcfg_s_default:    .asciz "kof.config"
            .Lcfg_w_true:  .asciz "true"
            .Lcfg_w_yes:   .asciz "yes"
            .Lcfg_w_one:   .asciz "1"
            .Lcfg_w_false: .asciz "false"
            .Lcfg_w_no:    .asciz "no"
            .Lcfg_w_zero:  .asciz "0"
            .section .text

            # kof_env_getc(rdi=nome C-string) -> rax = KofString*|0
            # busca linear simples: acha "NAME=" como substring do environ;
            # valor = ate o NUL da entrada. Estado: r10=nlen, r13=len,
            # r14=i (posicao), r9=j (casamento), r8=ptr corrente.
            kof_env_getc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rdi, %rbx              # nome
                xorq %r10, %r10              # strlen(nome)
            .Lceg_nlen:
                cmpb $0, (%rbx,%r10)
                je .Lceg_nlen_done
                incq %r10
                jmp .Lceg_nlen
            .Lceg_nlen_done:
                leaq .Lsec_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open
                syscall
                testq %rax, %rax
                js .Lceg_fail
                movq %rax, %r12              # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rax, %r13              # bytes lidos
                movq %r12, %rdi
                movq $3, %rax                # close
                testq %r10, %r10             # nome vazio -> falha
                jle .Lceg_fail
                cmpq %r10, %r13
                jle .Lceg_fail               # buffer menor que o nome
                xorq %r14, %r14              # i = 0
            .Lceg_scan:
                cmpq %r13, %r14
                jge .Lceg_fail
                movq %rsp, %r8
                addq %r14, %r8               # r8 = buf + i
                xorq %r9, %r9                # j = 0
            .Lceg_pcmp:
                cmpq %r10, %r9
                je .Lceg_pmatched            # casou o nome inteiro
                movq %r14, %rax
                addq %r9, %rax
                cmpq %r13, %rax
                jge .Lceg_fail
                movzbl (%rbx,%r9), %eax      # name[j]
                movzbl (%r8,%r9), %ecx       # buf[i+j]
                cmpl %ecx, %eax
                jne .Lceg_advance
                incq %r9
                jmp .Lceg_pcmp
            .Lceg_pmatched:
                # exige '=' imediatamente apos o nome
                cmpb $61, (%r8,%r10)
                jne .Lceg_advance
                leaq 1(%r8,%r10), %rdi       # valor = buf + i + nlen + 1
                movq %r14, %rsi
                addq %r10, %rsi
                incq %rsi                    # inicio do valor (offset)
                movq %r13, %rdx
                subq %rsi, %rdx              # limite restante
                movq %rdx, %r15              # r15 = len maximo
                xorq %rcx, %rcx              # comprimento do valor ate NUL
            .Lceg_vscan:
                cmpq %r15, %rcx
                jge .Lceg_vdone
                cmpb $0, (%rdi,%rcx)
                je .Lceg_vdone
                incq %rcx
                jmp .Lceg_vscan
            .Lceg_vdone:
                movq %rcx, %rsi              # vallen
                call kof_string_from_literal
                jmp .Lceg_exit
            .Lceg_advance:
                # avanca ate passar do proximo NUL (fim da entrada)
                cmpq %r13, %r14
                jge .Lceg_fail
                cmpb $0, (%r8)
                je .Lceg_adv_null
                incq %r8
                incq %r14
                jmp .Lceg_advance
            .Lceg_adv_null:
                incq %r14                    # pula o proprio NUL
                jmp .Lceg_scan
            .Lceg_fail:
                xorl %eax, %eax
            .Lceg_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lcfg_file_find(rdi=path C-string, rsi=key KofString*) -> KofString*|0
            # Ponteiros ABSOLUTOS apenas: rbx=buffer base, r14=fim dos dados,
            # r13=key data, r15=key len, r12=cursor de linha, r10/r8/r11=temp.
            .Lcfg_file_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rsi, %r12              # key
                movq %rdi, %rbx              # path cstr
                movq %rbx, %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                movq $2, %rax                # SYS_open (ausente -> null)
                syscall
                testq %rax, %rax
                js .Lcff_fail
                movq %rax, %rcx              # fd
                movq %rcx, %rdi
                movq %rsp, %rsi
                movq $16384, %rdx
                xorq %rax, %rax              # SYS_read
                syscall
                movq %rsp, %rbx              # rbx = buffer base (fixo)
                movq %rax, %r14              # r14 = bytes lidos
                movq %rcx, %rdi
                movq $3, %rax                # close
                syscall
                testq %r14, %r14
                jle .Lcff_fail
                leaq 24(%r12), %r13          # r13 = endereco dos dados da chave
                movl 16(%r12), %r15d         # r15 = key len
                leaq (%rbx,%r14), %r9        # r9 = fim dos dados
                movq %rbx, %r12              # r12 = inicio da linha corrente
            .Lcff_line:
                cmpq %r9, %r12
                jae .Lcff_fail
                movq %r12, %r10              # acha o fim da linha (LF ou fim)
            .Lcff_findeol:
                cmpq %r9, %r10
                jae .Lcff_haveeol
                cmpb $10, (%r10)
                je .Lcff_haveeol
                incq %r10
                jmp .Lcff_findeol
            .Lcff_haveeol:
                movq %r12, %r11              # trim esquerdo -> r11
            .Lcff_tls:
                cmpq %r10, %r11
                jae .Lcff_blank
                movzbl (%r11), %eax
                cmpb $32, %al
                je .Lcff_tls1
                cmpb $9, %al
                jne .Lcff_tle
            .Lcff_tls1:
                incq %r11
                jmp .Lcff_tls
            .Lcff_tle:
                movq %r10, %r8               # trim direito -> r8 (exclusivo)
            .Lcff_tle_loop:
                cmpq %r11, %r8
                jbe .Lcff_blank
                movzbl -1(%r8), %eax
                cmpb $32, %al
                je .Lcff_tle1
                cmpb $9, %al
                je .Lcff_tle1
                cmpb $13, %al
                je .Lcff_tle1
                jmp .Lcff_hash
            .Lcff_tle1:
                decq %r8
                jmp .Lcff_tle_loop
            .Lcff_blank:
                leaq 1(%r10), %r12
                jmp .Lcff_line
            .Lcff_hash:
                cmpb $35, (%r11)             # '#'
                je .Lcff_blank
                movq %r11, %rcx              # procura '=' em [r11,r8)
            .Lcff_eqscan:
                cmpq %r8, %rcx
                jae .Lcff_blank
                cmpb $61, (%rcx)
                je .Lcff_eqfound
                incq %rcx
                jmp .Lcff_eqscan
            .Lcff_eqfound:
                movq %rcx, %r12              # salva o offset do '=' 
            .Lcff_keytrim:
                movq %rcx, %rdi              # chave direita-aparada: [r11, rdi)
            .Lcff_keyt:
                cmpq %r11, %rdi
                jbe .Lcff_blank
                movzbl -1(%rdi), %eax
                cmpb $32, %al
                je .Lcff_keyt1
                cmpb $9, %al
                jne .Lcff_keycmp
            .Lcff_keyt1:
                decq %rdi
                jmp .Lcff_keyt
            .Lcff_keycmp:
                movq %rdi, %rdx              # comprimento da chave na linha
                subq %r11, %rdx
                cmpq %r15, %rdx
                jne .Lcff_valskip
                xorq %rcx, %rcx              # indice i
            .Lcff_cmpline:
                cmpq %r15, %rcx
                jae .Lcff_matched
                movzbl (%r11,%rcx), %eax     # linha[i]
                movzbl (%r13,%rcx), %edx     # key[i]
                cmpb %dl, %al
                jne .Lcff_valskip
                incq %rcx
                jmp .Lcff_cmpline
            .Lcff_matched:
                leaq 1(%r12), %rsi           # vs = eq + 1 (r12 guarda o offset do '=')
            .Lcff_vtls:
                cmpq %r8, %rsi
                jae .Lcff_vmk
                movzbl (%rsi), %eax
                cmpb $32, %al
                je .Lcff_vtls1
                cmpb $9, %al
                jne .Lcff_vmk
            .Lcff_vtls1:
                incq %rsi
                jmp .Lcff_vtls
            .Lcff_vmk:
                movq %r8, %rax               # vallen = fim efetivo - inicio do valor
                subq %rsi, %rax
                movq %rsi, %rdi              # rdi = endereco do valor
                movl %eax, %esi              # ESI = vallen (contrato do from_literal!)
                call kof_string_from_literal
                jmp .Lcff_exit
            .Lcff_valskip:
                leaq 1(%r10), %r12
                jmp .Lcff_line
            .Lcff_fail:
                xorl %eax, %eax
            .Lcff_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            # .Lcfg_envname(rdi=key KofString*, rsi=dest) -> escreve "KOF_<KEY>" C-string
            .Lcfg_envname:
                movl 16(%rdi), %ecx          # keylen
                leaq 24(%rdi), %rdx          # data
                movb $75, 0(%rsi)            # 'K'
                movb $79, 1(%rsi)            # 'O'
                movb $70, 2(%rsi)            # 'F'
                movb $95, 3(%rsi)            # '_'
                movq $4, %rax
                xorq %r9, %r9
            .Lce_loop:
                cmpq %rcx, %r9
                jge .Lce_done
                movzbl (%rdx,%r9), %edi
                cmpb $46, %dil               # '.'
                je .Lce_us
                cmpb $45, %dil               # '-'
                je .Lce_us
                cmpb $97, %dil               # 'a'
                jb .Lce_store
                cmpb $122, %dil              # 'z'
                ja .Lce_store
                subb $32, %dil               # maiuscula
                jmp .Lce_store
            .Lce_us:
                movb $95, %dil               # '_'
            .Lce_store:
                movb %dil, (%rsi,%rax)
                incq %rax
                incq %r9
                jmp .Lce_loop
            .Lce_done:
                movb $0, (%rsi,%rax)
                ret

            # kof_config_lookup(rdi=key KofString*) -> KofString*|0
            # público: raw + interpolação ${key} (P2)
            kof_config_lookup:
                call kof_config_lookup_raw
                testq %rax, %rax
                jz .Lkcl_null
                movq %rax, %rdi
                call kof_config_interpolate
            .Lkcl_null:
                ret

            kof_config_lookup_raw:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $512, %rsp
                movq %rdi, %rbx              # key
                # 1) arquivo explicito via KOF_CONFIG
                leaq .Lcfg_s_kofconfig(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_envkey
                leaq 24(%rax), %rdi          # path cstr (data e NUL-terminada)
                movq %rbx, %rsi
                call .Lcfg_file_find
                testq %rax, %rax
                jnz .Lcl_exit
            .Lcl_envkey:
                # 2) env KOF_<CHAVE>
                movq %rbx, %rdi
                movq %rsp, %rsi
                call .Lcfg_envname
                movq %rsp, %rdi
                call kof_env_getc
                testq %rax, %rax
                jnz .Lcl_exit
                # 3) kof.<profile>.config ou kof.config
                leaq .Lcfg_s_kofprofile(%rip), %rdi
                call kof_env_getc
                testq %rax, %rax
                jz .Lcl_defaultfile
                # monta "kof.<profile>.config" no buffer do frame
                movq %rax, %r12              # profile KofString (antes de clobber rax)
                movq %rsp, %r8
                movl $778465131, %eax        # "kof." little-endian (6B 6F 66 2E)
                movl %eax, 0(%r8)
                movq $4, %rax
                movl 16(%r12), %ecx          # profile len
                leaq 24(%r12), %rdx          # profile data
                xorq %r9, %r9
            .Lcl_pcopy:
                cmpq %rcx, %r9
                jge .Lcl_pdone
                movzbl (%rdx,%r9), %edi
                movb %dil, (%r8,%rax)
                incq %rax
                incq %r9
                jmp .Lcl_pcopy
            .Lcl_pdone:
                movb $46, 0(%r8,%rax)        # ".config"
                movb $99, 1(%r8,%rax)
                movb $111, 2(%r8,%rax)
                movb $110, 3(%r8,%rax)
                movb $102, 4(%r8,%rax)
                movb $105, 5(%r8,%rax)
                movb $103, 6(%r8,%rax)
                addq $7, %rax
                movb $0, (%r8,%rax)
                leaq 0(%rsp), %rdi
                jmp .Lcl_ffcall
            .Lcl_defaultfile:
                leaq .Lcfg_s_default(%rip), %rdi
            .Lcl_ffcall:
                movq %rbx, %rsi
                call .Lcfg_file_find
            .Lcl_exit:
                addq $512, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ---- wrappers publicos ----

            kof_config_get:
                jmp kof_config_lookup

            # P2 (docs/stdlib-config.md §8.2): interpolação ${key}.
            # rdi = valor KofString* -> resolve referências a outras chaves.
            # Ref inexistente ou "${" sem "}" -> valor literal inalterado.
            # Profundidade máxima 16 (quebra ciclos).
            # callee-saved: r12=valor corrente, r13=depth, rbx/r14/r15=temps
            kof_config_interpolate:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp               # spills: [0]=start [8]=end [16]=sufixo
                                             # push x5 (40) + 32 = 72 -> rsp%16==8
                                             # na entrada de calls: OK (padrao SysV)
                testq %rdi, %rdi
                jz .Lkci_ret
                movq %rdi, %r12
                xorl %r13d, %r13d            # depth = 0
            .Lkci_loop:
                cmpl $16, %r13d
                jge .Lkci_done
                # achou "${" no valor corrente?
                leaq .Lkci_dollar(%rip), %rdi
                movl $2, %esi
            """);
    }
}
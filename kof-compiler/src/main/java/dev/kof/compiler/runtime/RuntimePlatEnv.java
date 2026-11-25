package dev.kof.compiler.runtime;

/**
 * M-6 (#1135): o corpo LINUX da família ENVIRON da costura — a busca de uma
 * variável no ambiente, lida do {@code /proc/self/environ}.
 *
 * <p><b>O CONTRATO (o mesmo da família):</b> {@code rdi} = o nome como C
 * string e {@code rax = KofString* | 0}. O Darwin tem o seu próprio corpo
 * (o {@code getenv} da libSystem — o procfs não existe lá), em
 * {@link RuntimePlatMacosEnv}.
 *
 * <p>A leitura do arquivo é feita pela própria costura
 * ({@code kof_plat_open}/{@code kof_plat_read}/{@code kof_plat_close}) — o
 * descritor vive num slot do frame, nunca num registrador que o syscall
 * destroi (o {@code %rcx}, medido).
 */
public final class RuntimePlatEnv {

    private RuntimePlatEnv() {}

    /** O corpo Linux da família ENVIRON. */
    public static void emitEnv(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_env_get
            .type kof_plat_env_get, @function
            # kof_plat_env_get(rdi=nome C-string) -> rax = KofString*|0
            # busca linear: acha "NAME=" como substring do environ; valor
            # ate o NUL da entrada. Estado: r10=nlen, r13=len, r14=i,
            # r9=j (casamento), r8=ptr corrente.
            kof_plat_env_get:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $16384, %rsp
                movq %rdi, %rbx              # nome
                xorq %r10, %r10              # strlen(nome)
            .Lpe_nlen:
                cmpb $0, (%rbx,%r10)
                je .Lpe_nlen_done
                incq %r10
                jmp .Lpe_nlen
            .Lpe_nlen_done:
                leaq .Lplat_environ_path(%rip), %rdi
                xorq %rsi, %rsi
                xorq %rdx, %rdx
                call kof_plat_open           # o open pela propria costura
                testq %rax, %rax
                js .Lpe_fail
                movq %rax, %r12              # fd
                movq %r12, %rdi
                leaq 0(%rsp), %rsi
                movq $16384, %rdx
                call kof_plat_read
                movq %rax, %r13              # bytes lidos
                movq %r12, %rdi
                call kof_plat_close
                testq %r10, %r10             # nome vazio -> falha
                jle .Lpe_fail
                cmpq %r10, %r13
                jle .Lpe_fail                # buffer menor que o nome
                xorq %r14, %r14              # i = 0
            .Lpe_scan:
                cmpq %r13, %r14
                jge .Lpe_fail
                movq %rsp, %r8
                addq %r14, %r8               # r8 = buf + i
                xorq %r9, %r9                # j = 0
            .Lpe_pcmp:
                cmpq %r10, %r9
                je .Lpe_pmatched             # casou o nome inteiro
                movq %r14, %rax
                addq %r9, %rax
                cmpq %r13, %rax
                jge .Lpe_fail
                movzbl (%rbx,%r9), %eax      # name[j]
                movzbl (%r8,%r9), %ecx       # buf[i+j]
                cmpl %ecx, %eax
                jne .Lpe_advance
                incq %r9
                jmp .Lpe_pcmp
            .Lpe_pmatched:
                # exige '=' imediatamente apos o nome
                cmpb $61, (%r8,%r10)
                jne .Lpe_advance
                leaq 1(%r8,%r10), %rdi       # valor = buf + i + nlen + 1
                movq %r14, %rsi
                addq %r10, %rsi
                incq %rsi                    # inicio do valor (offset)
                movq %r13, %rdx
                subq %rsi, %rdx              # limite restante
                movq %rdx, %r15              # r15 = len maximo
                xorq %rcx, %rcx              # comprimento do valor ate NUL
            .Lpe_vscan:
                cmpq %r15, %rcx
                jge .Lpe_vdone
                cmpb $0, (%rdi,%rcx)
                je .Lpe_vdone
                incq %rcx
                jmp .Lpe_vscan
            .Lpe_vdone:
                movq %rcx, %rsi              # vallen
                call kof_string_from_literal
                jmp .Lpe_exit
            .Lpe_advance:
                # avanca ate passar do proximo NUL (fim da entrada)
                cmpq %r13, %r14
                jge .Lpe_fail
                cmpb $0, (%r8)
                je .Lpe_adv_null
                incq %r8
                incq %r14
                jmp .Lpe_advance
            .Lpe_adv_null:
                incq %r14                    # pula o proprio NUL
                jmp .Lpe_scan
            .Lpe_fail:
                xorl %eax, %eax
            .Lpe_exit:
                addq $16384, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .data
            .Lplat_environ_path:
                .asciz "/proc/self/environ"
            .section .text
            """);
    }
}

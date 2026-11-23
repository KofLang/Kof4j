package dev.kof.compiler.runtime;

/**
 * M-4 (#1135, D-NATIVE-MACOS): a família de TLS da costura
 * ({@code kof_plat_tls_*}) sobre uma pthread key do Darwin — separada de
 * {@link RuntimePlatMacos} pelo gate de 500 linhas (responsabilidade
 * própria: a cadeia de exceções por thread).
 */
public final class RuntimePlatMacosTls {

    private RuntimePlatMacosTls() {}

    /**
     * A FAMÍLIA TLS (M-4): a cadeia de exceções ({@code kof_exc_chain}) por
     * thread.
     *
     * <p><b>POR QUE UMA PTHREAD KEY (e não o TLV do Mach-O):</b> o acesso TLV
     * do Mach-O foi medido no compilador real ({@code movq _x@TLVP(%rip),
     * %rdi; callq *(%rdi)} + o descritor {@code __DATA,__thread_vars}) — mas
     * ele obrigaria o tradutor Mach-O a reescrever cada acesso E a emitir o
     * descritor (o `.tbss`, o `$` nos nomes dos símbolos). A pthread key dá a
     * MESMA semântica (um ponteiro por thread, NULL no início) por uma API
     * PÚBLICA do SDK ({@code pthread_key_create}/{@code pthread_getspecific}/
     * {@code pthread_setspecific} — medidos no pthread.h), e a chave é criada
     * no STARTUP ({@code kof_plat_tls_init}, single-thread) para não haver
     * corrida na criação.
     *
     * <p><b>O CONTRATO É TRANSPARENTE:</b> o get devolve o valor em
     * {@code %rax} e preserva TODOS os outros registradores (o site antigo
     * lia o valor para um registrador); o set recebe o valor em {@code %rdi}
     * e preserva TUDO (inclusive {@code %rax} — o {@code setspecific} devolve
     * um int que o chamador não espera).
     */
    public static void emitTls(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_tls_key: .long -1        # -1 = a chave ainda nao foi criada

            .section .text
            .globl kof_plat_tls_init
            .type kof_plat_tls_init, @function
            kof_plat_tls_init:
                # pthread_key_create(&kof_tls_key, NULL) — no STARTUP (single-thread)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                leaq kof_tls_key(%rip), %rdi
                xorq %rsi, %rsi
                call pthread_key_create
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_tls_get
            .type kof_plat_tls_get, @function
            kof_plat_tls_get:
                # o contrato: so o %rax muda (o valor da thread)
                pushq %rcx
                pushq %rdx
                pushq %rsi
                pushq %rdi
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movl kof_tls_key(%rip), %edi
                call pthread_getspecific
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %rdi
                popq %rsi
                popq %rdx
                popq %rcx
                ret

            .globl kof_plat_tls_set
            .type kof_plat_tls_set, @function
            kof_plat_tls_set:
                # %rdi = o valor; preserva TUDO (inclusive o %rax)
                pushq %rax
                pushq %rbx
                movq %rdi, %rbx
                pushq %rcx
                pushq %rdx
                pushq %rsi
                pushq %rdi
                pushq %r8
                pushq %r9
                pushq %r10
                pushq %r11
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movl kof_tls_key(%rip), %edi
                movq %rbx, %rsi
                call pthread_setspecific
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %r11
                popq %r10
                popq %r9
                popq %r8
                popq %rdi
                popq %rsi
                popq %rdx
                popq %rcx
                popq %rbx
                popq %rax
                ret
            """);
    }

}


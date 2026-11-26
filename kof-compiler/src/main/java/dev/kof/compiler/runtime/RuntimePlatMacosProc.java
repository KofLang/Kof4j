package dev.kof.compiler.runtime;

/**
 * M-2 (#1135, D-NATIVE-MACOS): os corpos DARWIN da família PROCESS da costura
 * — a libSystem em vez do syscall cru.
 *
 * <p><b>O QUE MEDE DIFERENÇA:</b> o {@code pipe2} do Linux NÃO existe no
 * Darwin (não há versão com flags): o corpo faz {@code pipe} e depois
 * {@code fcntl(F_SETFD, FD_CLOEXEC)} nos dois descritores quando o sítio
 * pediu o {@code O_CLOEXEC}; e o {@code execveat} do Linux também não existe
 * — o corpo faz {@code execve} (com o {@code AT_FDCWD} o
 * {@code execveat} é o mesmo {@code execve}). Os demais são 1:1
 * ({@code fork}, {@code dup2}, {@code waitpid}, {@code kill}), com a falha
 * normalizada para {@code -errno} como o kernel devolve.
 */
public final class RuntimePlatMacosProc {

    private RuntimePlatMacosProc() {}

    /** Os corpos Darwin da família PROCESS. */
    public static void emitProc(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_pipe2
            .type kof_plat_pipe2, @function
            kof_plat_pipe2:
                # pipe2(fds=%rdi, flags=%rsi) — no Darwin nao existe pipe2:
                # pipe + FD_CLOEXEC via fcntl quando o sítio pediu O_CLOEXEC
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx           # fds
                movq %rsi, %r12           # flags
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call pipe
                testl %eax, %eax
                jns .Lpp2_ok
                call __error
                movl (%rax), %eax
                negl %eax
                jmp .Lpp2_ret
            .Lpp2_ok:
                xorl %eax, %eax
                testq %r12, %r12          # O_CLOEXEC (0x1000000) pedido?
                jz .Lpp2_ret
                xorq %r13, %r13
            .Lpp2_loop:
                cmpq $2, %r13
                jge .Lpp2_ret
                movl (%rbx,%r13,4), %edi
                movl $2, %esi             # F_SETFD
                movl $1, %edx             # FD_CLOEXEC
                call fcntl
                incq %r13
                jmp .Lpp2_loop
            .Lpp2_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_plat_fork
            .type kof_plat_fork, @function
            kof_plat_fork:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call fork
                testl %eax, %eax
                jns .Lpf_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpf_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_dup2
            .type kof_plat_dup2, @function
            kof_plat_dup2:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call dup2                 # dup2(old=%rdi, new=%rsi)
                testl %eax, %eax
                jns .Lpd_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpd_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_exec
            .type kof_plat_exec, @function
            kof_plat_exec:
                # execveat(dirfd, path=%rsi, argv=%rdx, envp=%r10, flags) — o
                # Darwin nao tem execveat: com o AT_FDCWD o execve e' o mesmo.
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rsi, %rdi           # path  (o dirfd/flags do execveat
                movq %rdx, %rsi           # argv   nao existem no Darwin)
                movq %r10, %rdx           # envp
                call execve
                call __error              # o execve so volta no ERRO
                movl (%rax), %eax
                negl %eax
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_wait
            .type kof_plat_wait, @function
            kof_plat_wait:
                # wait4(pid, status, options, rusage) — no Darwin e' wait4 dos
                # QUATRO argumentos (a mesma assinatura).
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call wait4
                testl %eax, %eax
                jns .Lpw_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpw_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_kill
            .type kof_plat_kill, @function
            kof_plat_kill:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call kill                 # kill(pid=%rdi, sig=%rsi)
                testl %eax, %eax
                jns .Lpk_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpk_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_cloexec
            .type kof_plat_cloexec, @function
            kof_plat_cloexec:
                # fcntl(fd=%rdi, F_SETFD=2, FD_CLOEXEC=1) — o fcntl e' variadico
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movl $2, %esi
                movl $1, %edx
                xorl %eax, %eax           # AL = nº de XMM usados
                call fcntl
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }
}

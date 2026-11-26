package dev.kof.compiler.runtime;

/**
 * M-2 (#1135, D-NATIVE-MACOS): os corpos LINUX da família PROCESS da costura
 * — os syscalls que o {@code kof_process_run}/{@code kof_spawn} faziam crus.
 *
 * <p><b>O CONTRATO é o do SYSCALL</b> (os sítios só trocam "movl $N, %eax;
 * syscall" por "call"): os argumentos já estão nos registradores do syscall e
 * o retorno é o do kernel ({@code -errno} nos erros). Os corpos do Darwin
 * estão em {@link RuntimePlatMacosProc}.
 */
public final class RuntimePlatProc {

    private RuntimePlatProc() {}

    /** Os corpos Linux da família PROCESS. */
    public static void emitProc(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_pipe2
            .type kof_plat_pipe2, @function
            kof_plat_pipe2:
                movq $293, %rax          # pipe2(fds, flags)
                syscall
                ret

            .globl kof_plat_fork
            .type kof_plat_fork, @function
            kof_plat_fork:
                movq $57, %rax           # fork()
                syscall
                ret

            .globl kof_plat_dup2
            .type kof_plat_dup2, @function
            kof_plat_dup2:
                movq $33, %rax           # dup2(old=%rdi, new=%rsi)
                syscall
                ret

            .globl kof_plat_exec
            .type kof_plat_exec, @function
            kof_plat_exec:
                # execveat(dirfd=%rdi, path=%rsi, argv=%rdx, envp=%r10, flags=%r8)
                movq $436, %rax
                syscall
                ret

            .globl kof_plat_wait
            .type kof_plat_wait, @function
            kof_plat_wait:
                movq $61, %rax           # wait4(pid, status, options, rusage)
                syscall
                ret

            .globl kof_plat_kill
            .type kof_plat_kill, @function
            kof_plat_kill:
                movq $62, %rax           # kill(pid, sig)
                syscall
                ret

            .globl kof_plat_cloexec
            .type kof_plat_cloexec, @function
            kof_plat_cloexec:
                # fcntl(fd=%rdi, F_SETFD=2, FD_CLOEXEC=1)  (medido: F_SETFD=2,
                # FD_CLOEXEC=1 nos DOIS alvos)
                movl $2, %esi
                movl $1, %edx
                movq $72, %rax
                syscall
                ret
            """);
    }
}

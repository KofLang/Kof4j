package dev.kof.compiler.runtime;

/**
 * M-6 (#1135, D-NATIVE-MACOS): as faces {@code *at} da costura — o
 * {@code openat}, o {@code mkdirat}, o {@code unlinkat}, o
 * {@code symlinkat}, o {@code readlinkat}, o {@code renameat} e o
 * {@code fchmod}.
 *
 * <p><b>O CONTRATO é o do SYSCALL Linux</b> (o sítio só troca "movq $N,
 * %rax; syscall" por "call"): os argumentos já estão nos registradores e o
 * retorno é o do kernel. O Darwin não tem essas formas — o corpo de lá
 * (em {@link RuntimePlatMacosIoAt}) traduz cada uma para a sua equivalente
 * da libSystem.
 */
public final class RuntimePlatIoAt {

    private RuntimePlatIoAt() {}

    /**
     * As faces {@code *at} da familia: o corpo Linux aqui, o do Darwin em
     * {@link RuntimePlatMacosIoAt}, e a recusa NOMEADA no UEFI/BIOS.
     */
    public static void emit(StringBuilder sb) {
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacosIoAt.emitIoAt(sb);
            return;
        }
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBios.refuse(sb, "kof_plat_openat, kof_plat_mkdirat, kof_plat_unlinkat, "
                    + "kof_plat_symlinkat, kof_plat_readlinkat, kof_plat_renameat, kof_plat_fchmod");
            return;
        }
        emitIoAt(sb);
    }

    /** As faces {@code *at} do Linux. */
    public static void emitIoAt(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_openat
            .type kof_plat_openat, @function
            kof_plat_openat:
                # openat(dirfd=%rdi, path=%rsi, flags=%rdx, mode=%r10) -> fd | -errno
                movq $257, %rax           # SYS_openat
                syscall
                ret

            .globl kof_plat_mkdirat
            .type kof_plat_mkdirat, @function
            kof_plat_mkdirat:
                # mkdirat(dirfd=%rdi, path=%rsi, mode=%rdx) -> 0 | -errno
                movq $258, %rax           # SYS_mkdirat
                syscall
                ret

            .globl kof_plat_unlinkat
            .type kof_plat_unlinkat, @function
            kof_plat_unlinkat:
                # unlinkat(dirfd=%rdi, path=%rsi, flags=%rdx) -> 0 | -errno
                movq $280, %rax           # SYS_unlinkat
                syscall
                ret

            .globl kof_plat_symlinkat
            .type kof_plat_symlinkat, @function
            kof_plat_symlinkat:
                # symlinkat(target=%rdi, newdirfd=%rsi, linkpath=%rdx) -> 0 | -errno
                movq $265, %rax           # SYS_symlinkat
                syscall
                ret

            .globl kof_plat_readlinkat
            .type kof_plat_readlinkat, @function
            kof_plat_readlinkat:
                # readlinkat(dirfd=%rdi, path=%rsi, buf=%rdx, size=%r10) -> n | -errno
                movq $267, %rax           # SYS_readlinkat
                syscall
                ret

            .globl kof_plat_fchmod
            .type kof_plat_fchmod, @function
            kof_plat_fchmod:
                # fchmod(fd=%rdi, mode=%rsi) -> 0 | -errno
                movq $91, %rax            # SYS_fchmod
                syscall
                ret

            .globl kof_plat_renameat
            .type kof_plat_renameat, @function
            kof_plat_renameat:
                # renameat(olddirfd=%rdi, oldpath=%rsi, newdirfd=%rdx, newpath=%r10)
                movq $264, %rax           # SYS_renameat
                syscall
                ret
            """);
    }
}

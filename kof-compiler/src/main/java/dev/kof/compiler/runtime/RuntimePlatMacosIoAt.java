package dev.kof.compiler.runtime;

/**
 * M-6 (#1135, D-NATIVE-MACOS): as faces {@code *at} da costura no DARWIN —
 * o corpo de cada uma traduz a forma Linux (que o Darwin não tem) para a
 * equivalente da libSystem.
 *
 * <p><b>O QUE MEDE DIFERENÇA:</b> o Darwin não tem {@code openat} nem
 * {@code unlinkat} — o corpo usa {@code open} (com o {@code AT_FDCWD}) e
 * escolhe entre {@code unlink} e {@code rmdir} pela flag
 * {@code AT_REMOVEDIR}; o {@code mkdirat}/{@code symlinkat}/
 * {@code readlinkat}/{@code renameat} são {@code mkdir}/{@code symlink}/
 * {@code readlink}/{@code rename} lá. A falha de cada uma é normalizada em
 * {@code -errno}, como o kernel devolve.
 */
public final class RuntimePlatMacosIoAt {

    private RuntimePlatMacosIoAt() {}

    /** As faces {@code *at} do Darwin. */
    public static void emitIoAt(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_openat
            .type kof_plat_openat, @function
            kof_plat_openat:
                # openat(dirfd, path=%rsi, flags=%rdx, mode=%r10) — o Darwin nao
                # tem openat: com o AT_FDCWD e' o open (medido: o SDK nao
                # declara openat; o AT_FDCWD do Darwin e' -2).
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rsi, %rdi           # path
                movq %rdx, %rsi           # flags
                movq %r10, %rdx           # mode
                xorl %eax, %eax
                call open
                testl %eax, %eax
                jns .Lpoa_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpoa_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_mkdirat
            .type kof_plat_mkdirat, @function
            kof_plat_mkdirat:
                # mkdirat(dirfd, path=%rsi, mode=%rdx) -> o Darwin: mkdir
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rsi, %rdi
                call mkdir
                testl %eax, %eax
                jns .Lpm_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpm_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_unlinkat
            .type kof_plat_unlinkat, @function
            kof_plat_unlinkat:
                # unlinkat(dirfd, path=%rsi, flags=%rdx) — o Darwin nao tem
                # unlinkat: o AT_REMOVEDIR (0x200) escolhe entre rmdir e unlink
                pushq %rbx
                pushq %r15
                movq %rsi, %rbx           # path
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rbx, %rdi
                movq $0x200, %rsi         # AT_REMOVEDIR
                testq %rdx, %rdx
                jz .Lpu_unlink
                call rmdir
                jmp .Lpu_check
            .Lpu_unlink:
                movq %rbx, %rdi
                call unlink
            .Lpu_check:
                testl %eax, %eax
                jns .Lpu_ret2
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpu_ret2:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                popq %rbx
                ret

            .globl kof_plat_symlinkat
            .type kof_plat_symlinkat, @function
            kof_plat_symlinkat:
                # symlinkat(target=%rdi, newdirfd, linkpath=%rdx) -> o Darwin: symlink
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rdi, %rsi           # target (o symlink do Darwin: o alvo
                movq %rdx, %rdi           # linkpath primeiro)
                call symlink
                testl %eax, %eax
                jns .Lps_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lps_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_readlinkat
            .type kof_plat_readlinkat, @function
            kof_plat_readlinkat:
                # readlinkat(dirfd, path=%rsi, buf=%rdx, size=%r10) -> o Darwin: readlink
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rsi, %rdi           # path
                movq %rdx, %rsi           # buf
                movq %r10, %rdx           # size
                call readlink
                testl %eax, %eax
                jns .Lprl_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lprl_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_fchmod
            .type kof_plat_fchmod, @function
            kof_plat_fchmod:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call fchmod               # fchmod(fd=%rdi, mode=%rsi) — a mesma ABI
                testl %eax, %eax
                jns .Lpfm_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lpfm_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_renameat
            .type kof_plat_renameat, @function
            kof_plat_renameat:
                # renameat(olddirfd, oldpath=%rsi, newdirfd, newpath=%r10)
                # — o Darwin: rename
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                movq %rsi, %rdi           # oldpath
                movq %r10, %rsi           # newpath
                call rename
                testl %eax, %eax
                jns .Lprn_ret
                call __error
                movl (%rax), %eax
                negl %eax
            .Lprn_ret:
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }
}

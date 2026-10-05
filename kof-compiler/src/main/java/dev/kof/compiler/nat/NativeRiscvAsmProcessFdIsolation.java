package dev.kof.compiler.nat;

/** riscv64/aarch64 port of the child descriptor-isolation helper (#762). */
public final class NativeRiscvAsmProcessFdIsolation {
    private NativeRiscvAsmProcessFdIsolation() {}

    static String RISCV_RUNTIME_ASM_PROCESS_FD_ISOLATION = """
            .section .text
            .globl kof_process_child_fd_isolation
            .type kof_process_child_fd_isolation, @function
            kof_process_child_fd_isolation:
                # Fast path: close_range(3, UINT_MAX, CLOSE_RANGE_CLOEXEC).
                li   a0, 3
                li   a1, -1
                li   a2, 4
                li   a7, 436
                ecall
                bgez a0, .Lkof_rpfd_ok

                # Old-kernel fallback: prlimit64 + fcntl(F_SETFD, FD_CLOEXEC).
                addi sp, sp, -16
                li   a0, 0
                li   a1, 7                  # RLIMIT_NOFILE
                li   a2, 0
                mv   a3, sp
                li   a7, 261                # prlimit64 (asm-generic)
                ecall
                bltz a0, .Lkof_rpfd_fail_stack
                ld   t0, 0(sp)              # rlim_cur
                addi sp, sp, 16
                li   t1, -1                 # RLIM_INFINITY => fail closed
                beq  t0, t1, .Lkof_rpfd_fail
                li   t1, 3
            .Lkof_rpfd_loop:
                bgeu t1, t0, .Lkof_rpfd_ok
                mv   a0, t1
                li   a1, 2                  # F_SETFD
                li   a2, 1                  # FD_CLOEXEC
                li   a7, 25                 # fcntl (asm-generic)
                ecall
                bgez a0, .Lkof_rpfd_next
                li   t2, -9                 # EBADF = sparse/closed slot
                bne  a0, t2, .Lkof_rpfd_fail
            .Lkof_rpfd_next:
                addi t1, t1, 1
                j    .Lkof_rpfd_loop
            .Lkof_rpfd_fail_stack:
                addi sp, sp, 16
            .Lkof_rpfd_fail:
                li   a0, -1
                ret
            .Lkof_rpfd_ok:
                li   a0, 0
                ret
            """;
}

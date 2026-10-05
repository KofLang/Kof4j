package dev.kof.compiler.runtime;

/** Child-side descriptor isolation shared by native process/shell exec paths. */
public final class RuntimeProcessFdIsolation {
    private RuntimeProcessFdIsolation() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # #762 / D-FULL-PARITY-050: match the JVM oracle — no ambient
            # parent descriptor > stderr may survive a successful exec.
            # Fast path: Linux >=5.11 marks the entire range CLOEXEC in one
            # syscall. Fallback: old kernels enumerate [3, RLIMIT_NOFILE)
            # with raw fcntl(F_SETFD, FD_CLOEXEC). Marking (not closing)
            # keeps the exec-failure pipe alive when execvp itself fails.
            .section .text
            .globl kof_process_child_fd_isolation
            .type kof_process_child_fd_isolation, @function
            kof_process_child_fd_isolation:
                movl $3, %edi                 # first fd
                movl $-1, %esi                # UINT_MAX
                movl $4, %edx                 # CLOSE_RANGE_CLOEXEC
                movl $436, %eax               # close_range
                syscall
                testq %rax, %rax
                jns .Lkof_proc_fd_ok

                # Compatibility fallback: prlimit64(0, RLIMIT_NOFILE, NULL, &lim)
                subq $16, %rsp
                xorl %edi, %edi
                movl $7, %esi                 # RLIMIT_NOFILE
                xorl %edx, %edx
                movq %rsp, %r10
                movl $302, %eax               # prlimit64 (x86-64)
                syscall
                testq %rax, %rax
                js .Lkof_proc_fd_fail_stack
                movq 0(%rsp), %r8             # rlim_cur
                addq $16, %rsp
                cmpq $-1, %r8                 # RLIM_INFINITY: cannot prove closure
                je .Lkof_proc_fd_fail
                movl $3, %r9d
            .Lkof_proc_fd_loop:
                cmpq %r8, %r9
                jae .Lkof_proc_fd_ok
                movl %r9d, %edi
                movl $2, %esi                 # F_SETFD
                movl $1, %edx                 # FD_CLOEXEC
                movl $72, %eax                # fcntl (x86-64)
                syscall
                testq %rax, %rax
                jns .Lkof_proc_fd_next
                cmpq $-9, %rax                # EBADF = sparse/closed slot
                jne .Lkof_proc_fd_fail
            .Lkof_proc_fd_next:
                incq %r9
                jmp .Lkof_proc_fd_loop
            .Lkof_proc_fd_fail_stack:
                addq $16, %rsp
            .Lkof_proc_fd_fail:
                movl $-1, %eax
                ret
            .Lkof_proc_fd_ok:
                xorl %eax, %eax
                ret
            """);
    }
}

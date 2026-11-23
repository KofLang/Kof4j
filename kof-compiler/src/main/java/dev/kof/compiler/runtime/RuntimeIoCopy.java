package dev.kof.compiler.runtime;

/**
 * `kof_io_file_copy_to` do runtime nativo x86-64 (D-FULL-PARITY-050 linha 13
 * fatia 16b). Contrato JVM (JvmRuntimeIo): Files.copy(src,dst,COPY_ATTRIBUTES)
 * -> 1; IOException -> 0; SEM REPLACE_EXISTING -> destino existente = 0.
 * Implementacao: stat src; openat(src,O_RDONLY); openat(dst,O_WRONLY|O_CREAT|
 * O_EXCL,0644) (O_EXCL garante o no-overwrite); loop read/write; fchmod (modo);
 * utimensat (atime/mtime); close. KofStr bytes@24; struct stat x86_64:
 * st_mode@24, st_atime@72, atime_nsec@80, st_mtime@88, mtime_nsec@96.
 */
public final class RuntimeIoCopy {

    private RuntimeIoCopy() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                .globl kof_io_file_copy_to
                .type kof_io_file_copy_to, @function
                kof_io_file_copy_to:
                    pushq %rbx
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    subq $8368, %rsp
                    movq %rdi, %rbx
                    movq %rsi, %r12
                    movq $-100, %rdi
                    leaq 24(%rbx), %rsi
                    movq %rsp, %rdx
                    xorq %r10, %r10
                    call kof_plat_stat        # M-6 (#1135): o stat pela costura (o macOS normaliza o layout)
                    testq %rax, %rax
                    js .Lkof_iocp_zero
                    movq 72(%rsp), %rax
                    movq %rax, 144(%rsp)
                    movq 80(%rsp), %rax
                    movq %rax, 152(%rsp)
                    movq 88(%rsp), %rax
                    movq %rax, 160(%rsp)
                    movq 96(%rsp), %rax
                    movq %rax, 168(%rsp)
                    movq $-100, %rdi
                    leaq 24(%rbx), %rsi
                    xorq %rdx, %rdx
                    xorq %r10, %r10
                    movq $257, %rax
                    syscall
                    testq %rax, %rax
                    js .Lkof_iocp_zero
                    movq %rax, %r13
                    movq $-100, %rdi
                    leaq 24(%r12), %rsi
                    movq $193, %rdx
                    movq $420, %r10
                    movq $257, %rax
                    syscall
                    testq %rax, %rax
                    js .Lkof_iocp_closesrc
                    movq %rax, %r14
                .Lkof_iocp_loop:
                    movq %r13, %rdi
                    leaq 176(%rsp), %rsi
                    movq $8192, %rdx
                    xorq %rax, %rax
                    syscall
                    testq %rax, %rax
                    jle .Lkof_iocp_attrs
                    movq %rax, %rdx
                    movq %r14, %rdi
                    leaq 176(%rsp), %rsi
                    movq $1, %rax
                    syscall
                    testq %rax, %rax
                    jle .Lkof_iocp_fail
                    jmp .Lkof_iocp_loop
                .Lkof_iocp_attrs:
                    movl 24(%rsp), %esi
                    andl $4095, %esi
                    movq %r14, %rdi
                    movq $91, %rax
                    syscall
                    movq $-100, %rdi
                    leaq 24(%r12), %rsi
                    leaq 144(%rsp), %rdx
                    xorq %r10, %r10
                    movq $280, %rax
                    syscall
                    movq %r14, %rdi
                    movq $3, %rax
                    syscall
                    movq %r13, %rdi
                    movq $3, %rax
                    syscall
                    movq $1, %rax
                    jmp .Lkof_iocp_ret
                .Lkof_iocp_fail:
                    movq %r14, %rdi
                    movq $3, %rax
                    syscall
                .Lkof_iocp_closesrc:
                    movq %r13, %rdi
                    movq $3, %rax
                    syscall
                .Lkof_iocp_zero:
                    xorl %eax, %eax
                .Lkof_iocp_ret:
                    addq $8368, %rsp
                    popq %r14
                    popq %r13
                    popq %r12
                    popq %rbx
                    ret
                """);
    }
}

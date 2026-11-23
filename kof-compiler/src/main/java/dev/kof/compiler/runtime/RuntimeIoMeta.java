package dev.kof.compiler.runtime;

/**
 * Metadados de arquivo do runtime nativo x86-64 (D-FULL-PARITY-050 linha 13
 * fatia 15): {@code kof_io_file_modified_time} e {@code kof_io_file_is_symlink}.
 * Contrato JVM (JvmRuntimeIo):
 *   modified_time -> millis; ausente LANCA string "file not found: " + path
 *   is_symlink    -> 1/0 (lstat: NAO segue o link), erro -> 0
 * struct stat x86_64: st_mode@24, st_mtime.tv_sec@88, st_mtime.tv_nsec@96.
 * newfstatat=262; AT_SYMLINK_NOFOLLOW=0x100; S_IFMT=0xF000, S_IFLNK=0xA000.
 */
public final class RuntimeIoMeta {

    private RuntimeIoMeta() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                .section .rodata
                .Lstr_io_mtime_prefix:
                    .ascii "file not found: "
                .section .text
                .globl kof_io_file_modified_time
                .type kof_io_file_modified_time, @function
                kof_io_file_modified_time:
                    pushq %rbx
                    subq $144, %rsp
                    movq %rdi, %rbx
                    movq $-100, %rdi
                    leaq 24(%rbx), %rsi
                    movq %rsp, %rdx
                    xorq %r10, %r10
                    call kof_plat_stat        # M-6 (#1135): o stat pela costura (o macOS normaliza o layout)
                    testq %rax, %rax
                    js .Lio_mt_err
                    movq 96(%rsp), %rax
                    xorl %edx, %edx
                    movq $1000000, %rcx
                    divq %rcx
                    movq 88(%rsp), %rcx
                    imulq $1000, %rcx, %rcx
                    addq %rcx, %rax
                    addq $144, %rsp
                    popq %rbx
                    ret
                .Lio_mt_err:
                    leaq .Lstr_io_mtime_prefix(%rip), %rdi
                    movl $16, %esi
                    call kof_string_from_literal
                    movq %rax, %rdi
                    movq %rbx, %rsi
                    call kof_string_concat
                    movq %rax, %rdi
                    call kof_throw_string

                .globl kof_io_file_is_symlink
                .type kof_io_file_is_symlink, @function
                kof_io_file_is_symlink:
                    pushq %rbx
                    subq $144, %rsp
                    movq %rdi, %rbx
                    movq $-100, %rdi
                    leaq 24(%rbx), %rsi
                    movq %rsp, %rdx
                    movq $256, %r10
                    call kof_plat_stat        # M-6 (#1135): o stat pela costura (o macOS normaliza o layout)
                    testq %rax, %rax
                    js .Lio_sl_no
                    movl 24(%rsp), %eax
                    andl $0xF000, %eax
                    cmpl $0xA000, %eax
                    jne .Lio_sl_no
                    movq $1, %rax
                    addq $144, %rsp
                    popq %rbx
                    ret
                .Lio_sl_no:
                    xorl %eax, %eax
                    addq $144, %rsp
                    popq %rbx
                    ret
                """);
    }
}

package dev.kof.compiler.runtime;

/**
 * #751 / D-MAINT-BATCH-0510 (IO1): {@code kof_io_path_real_path} no runtime
 * nativo x86-64. Contrato JVM (JvmRuntimeIo): caminho canônico com links e
 * junctions resolvidos; null quando o caminho não existe. Delega ao libc
 * {@code realpath} (mesma fonte que o JVM {@code toRealPath} usa do lado do
 * SO); o buffer de 4096 vive na pilha — sem malloc, nada vaza fora do GC.
 * KofStr: len@16, bytes@24. {@code kof_io_strlen}/{@code kof_io_make_string}
 * já existem no runtime x86-64 (RuntimeIo1).
 */
public final class RuntimeIoRealPath {

    private RuntimeIoRealPath() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                .section .text
                .globl kof_io_path_real_path
                .type kof_io_path_real_path, @function
                kof_io_path_real_path:
                    pushq %rbx
                    movq %rdi, %rbx
                    subq $4096, %rsp
                    leaq 24(%rbx), %rdi
                    movq %rsp, %rsi
                    call realpath
                    testq %rax, %rax
                    jz .Lio_real_none
                    movq %rsp, %rdi
                    call kof_io_strlen
                    movq %rax, %rsi
                    movq %rsp, %rdi
                    call kof_io_make_string
                    addq $4096, %rsp
                    popq %rbx
                    ret
                .Lio_real_none:
                    xorl %eax, %eax
                    addq $4096, %rsp
                    popq %rbx
                    ret
                """);
    }
}

package dev.kof.compiler.runtime;

/**
 * `kof_io_file_move_to` do runtime nativo x86-64 (D-FULL-PARITY-050 linha 13
 * fatia 16). Contrato JVM (JvmRuntimeIo): Files.move SEM REPLACE_EXISTING ->
 * destino existente = 0; sucesso = 1. Implementacao: newfstatat(262) no destino
 * (existe -> 0), senao rename(82). KofStr bytes@24.
 */
public final class RuntimeIoMove {

    private RuntimeIoMove() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
                .globl kof_io_file_move_to
                .type kof_io_file_move_to, @function
                kof_io_file_move_to:
                    pushq %rbx
                    pushq %r12
                    subq $144, %rsp
                    movq %rdi, %rbx
                    movq %rsi, %r12
                    movq $-100, %rdi
                    leaq 24(%r12), %rsi
                    movq %rsp, %rdx
                    xorq %r10, %r10
                    call kof_plat_stat        # M-6 (#1135): o stat pela costura (o macOS normaliza o layout)
                    testq %rax, %rax
                    jns .Lio_mv_no
                    leaq 24(%rbx), %rdi
                    leaq 24(%r12), %rsi
                    movq $82, %rax
                    syscall
                    testq %rax, %rax
                    jne .Lio_mv_no
                    movq $1, %rax
                    jmp .Lio_mv_done
                .Lio_mv_no:
                    xorl %eax, %eax
                .Lio_mv_done:
                    addq $144, %rsp
                    popq %r12
                    popq %rbx
                    ret
                """);
    }
}

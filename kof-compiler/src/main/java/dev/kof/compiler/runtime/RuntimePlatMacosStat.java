package dev.kof.compiler.runtime;

/**
 * M-6 (#1135, D-NATIVE-MACOS): a família STAT da costura {@code kof_plat_*}
 * sobre a libSystem.
 *
 * <p><b>O PROBLEMA MEDIDO:</b> o runtime lê os campos do stat em offsets do
 * Linux ({@code st_mode@24}, {@code st_size@48}, {@code st_mtim@88/96}), mas o
 * {@code struct stat} do Darwin tem OUTRO layout. Os offsets foram MEDIDOS
 * compilando os {@code offsetof} com o clang real (o toolchain provisionado):
 *
 * <pre>
 *   Darwin   mode@4  (u16)  nlink@6 (u16)  mtimespec@48  size@96  sizeof=144
 *   Linux    mode@24 (u32)  nlink@16 (u32) mtim@88      size@48   sizeof=144
 * </pre>
 *
 * <p>E as FLAGS são outras: {@code AT_SYMLINK_NOFOLLOW} = <b>0x20</b> no
 * Darwin (0x100 no Linux) e o {@code AT_FDCWD} = <b>-2</b> (-100 no Linux).
 *
 * <p><b>A ESTRATÉGIA (uma normalização, não leituras espalhadas):</b> o corpo
 * do macOS chama o {@code fstatat} num buffer de RASCUNHO (na pilha, 144
 * bytes) e copia os QUATRO campos que o runtime usa do layout do Darwin
 * para o do HOST — no buffer que o chamador já providenciou. NENHUM chamador
 * muda: eles continuam lendo {@code 24/48/88/96} do buffer deles.
 */
public final class RuntimePlatMacosStat {

    private RuntimePlatMacosStat() {}

    /**
     * O corpo: {@code fstatat(dirfd, path, scratch, flags)} + a normalização
     * para o layout do HOST.
     *
     * <p>O contrato da costura é {@code kof_plat_stat(dirfd, path, buf,
     * flags) -> 0 | erro≠0} — os sítios testam o sinal de {@code %rax}.
     */
    public static void emitStat(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_stat
            .type kof_plat_stat, @function
            kof_plat_stat:
                # rdi=dirfd, rsi=path, rdx=buf, r10=flags (o contrato da costura)
                pushq %rbx               # 2 pushes + subq 152 = 16-aligned
                pushq %r12
                subq $152, %rsp
                movq %rdx, %rbx          # buf (do chamador)
                movq %rsp, %rdx          # scratch (o buffer do DARWIN)
                # as FLAGS: 0x100 (AT_SYMLINK_NOFOLLOW do Linux) -> 0x20 (Darwin)
                testq %r10, %r10
                jz .Lkst_flags_ok
                movq $0x20, %r10         # AT_SYMLINK_NOFOLLOW no Darwin
            .Lkst_flags_ok:
                # o dirfd: -100 (AT_FDCWD do Linux) -> -2 (AT_FDCWD no Darwin)
                cmpq $-100, %rdi
                jne .Lkst_dirfd_ok
                movq $-2, %rdi
            .Lkst_dirfd_ok:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp          # a libSystem usa SSE
                call fstatat
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                testl %eax, %eax
                jne .Lkst_fail
                # a NORMALIZACAO Darwin -> HOST (scratch -> buf do chamador)
                movzwl 4(%rsp), %eax     # st_mode (u16 no Darwin)
                movl %eax, 24(%rbx)      #  -> mode@24 (u32 no host)
                movzwl 6(%rsp), %eax     # st_nlink (u16)
                movl %eax, 16(%rbx)      #  -> nlink@16 (u32)
                movq 48(%rsp), %rax      # st_mtimespec.tv_sec
                movq %rax, 88(%rbx)      #  -> mtim.tv_sec@88
                movq 56(%rsp), %rax      # st_mtimespec.tv_nsec
                movq %rax, 96(%rbx)      #  -> mtim.tv_nsec@96 (host)
                movq 96(%rsp), %rax      # st_size (Darwin@96 — lido DEPOIS do nsec)
                movq %rax, 48(%rbx)      #  -> size@48 (host)
                xorl %eax, %eax          # 0 = ok
                jmp .Lkst_ret
            .Lkst_fail:
                movq $-1, %rax           # o chamador testa o sinal
            .Lkst_ret:
                addq $152, %rsp
                popq %r12
                popq %rbx
                ret
            """);
    }
}

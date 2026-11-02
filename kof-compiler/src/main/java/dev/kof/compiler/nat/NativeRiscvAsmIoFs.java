package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): mutacoes de FS do
// kof.io no cross (riscv64 + aarch64; aarch64 herda pelo tradutor). Fatia 3:
// delete()/mkdir()/create(). Syscalls genericos do Linux (iguais nas duas
// archs): unlinkat=35, mkdirat=34. Port do x86 (RuntimeIo2.kof_io_delete,
// RuntimeIo3.kof_io_dir_create):
//   - kof_io_delete(path): stat -> 0 se ausente; dir -> unlinkat(AT_REMOVEDIR)
//     senao unlinkat(0); sucesso = 1. (Nao-recursivo; dir_delete recursivo e
//     fatia seguinte.)
//   - kof_io_dir_create(path): mkdirat(AT_FDCWD, path, 0755); sucesso = 1,
//     erro/EEXIST = 0 (igual ao x86).
public final class NativeRiscvAsmIoFs {

    private NativeRiscvAsmIoFs() {}

    static String RISCV_RUNTIME_ASM_IO_FS = """
            .section .text
            # kof_io_delete(path@a0) -> 1|0 (usa kof_io_stat_mode da IoStat)
            .globl kof_io_delete
            .type kof_io_delete, @function
            kof_io_delete:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                call kof_io_stat_mode
                bltz a0, .Lkof_io_del_fail
                li   t0, 61440              # S_IFMT
                and  a0, a0, t0
                li   t0, 16384              # S_IFDIR
                bne  a0, t0, .Lkof_io_del_unlink
                li   a2, 512                # AT_REMOVEDIR
                j    .Lkof_io_del_call
            .Lkof_io_del_unlink:
                li   a2, 0
            .Lkof_io_del_call:
                addi a1, s0, 24             # path bytes
                li   a0, -100               # AT_FDCWD
                li   a7, 35                 # unlinkat
                ecall
                bnez a0, .Lkof_io_del_fail
                li   a0, 1
                j    .Lkof_io_del_done
            .Lkof_io_del_fail:
                li   a0, 0
            .Lkof_io_del_done:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_io_dir_create(path@a0) -> 1|0 (mkdirat 0755)
            .globl kof_io_dir_create
            .type kof_io_dir_create, @function
            kof_io_dir_create:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                addi a1, s0, 24             # path bytes
                li   a0, -100               # AT_FDCWD
                li   a2, 493                # mode 0755
                li   a7, 34                 # mkdirat
                ecall
                bnez a0, .Lkof_io_mkdir_fail
                li   a0, 1
                j    .Lkof_io_mkdir_done
            .Lkof_io_mkdir_fail:
                li   a0, 0
            .Lkof_io_mkdir_done:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # #854: kof_io_file_create_new(path@a0) -> 1|0 (openat O_WRONLY|
            # O_CREAT|O_EXCL=193, close) — mesmo molde do mkdirat acima.
            # O_EXCL com O_CREAT falha para um link mesmo pendente (garantia
            # POSIX do ultimo componente), fechando a corrida TOCTOU de
            # exists()+write(). aarch64 herda pelo tradutor.
            .globl kof_io_file_create_new
            .type kof_io_file_create_new, @function
            kof_io_file_create_new:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                addi a1, s0, 24             # path bytes
                li   a0, -100               # AT_FDCWD
                li   a2, 193                # O_WRONLY|O_CREAT|O_EXCL
                li   a3, 420                # mode 0644
                li   a7, 56                 # openat
                ecall
                bltz a0, .Lkof_io_createnew_fail
                li   a7, 57                 # close
                ecall
                li   a0, 1
                j    .Lkof_io_createnew_done
            .Lkof_io_createnew_fail:
                li   a0, 0
            .Lkof_io_createnew_done:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            """;
}

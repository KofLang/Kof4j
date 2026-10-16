package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 8 — leitura com
// offset p/ arquivos grandes (kof.io readRange) no cross (riscv64 + aarch64).
// Port de RuntimeIo2.kof_io_read_range / kof_io_read_range_path (mesmo corpo):
//   openat(AT_FDCWD, path+24, O_RDONLY=0, 0); buf = kof_alloc(len);
//   pread64(=67)(fd, buf, len, offset) -> lidos; close; Int[] =
//   kof_array_alloc(lidos,4); spread byte->Int (lbu zero-extend). Em erro de
//   openat retorna 0 (null), como o JVM.
// pread64 no riscv64/aarch64: a0=fd, a1=buf, a2=count, a3=offset(64-bit).
public final class NativeRiscvAsmIoReadRange {

    private NativeRiscvAsmIoReadRange() {}

    static String RISCV_RUNTIME_ASM_IO_READRANGE = """
            .section .text
            # kof_io_read_range(path@a0, offset@a1, len@a2) -> Int[]|0
            .globl kof_io_read_range
            .type kof_io_read_range, @function
            .globl kof_io_read_range_path
            .type kof_io_read_range_path, @function
            kof_io_read_range:
            kof_io_read_range_path:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                mv   s0, a0                 # path
                mv   s4, a1                 # offset
                mv   s5, a2                 # len
                bltz s4, .Lkof_iorr_inval   # #848: offset/len negativos
                bltz s5, .Lkof_iorr_inval
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lkof_iorr_err
                mv   s1, a0                 # fd
                mv   a0, s5
                call kof_alloc
                mv   s2, a0                 # buf
                mv   a0, s1
                mv   a1, s2
                mv   a2, s5
                mv   a3, s4
                li   a7, 67                 # pread64
                ecall
                mv   s3, a0                 # lidos
                mv   a0, s1
                li   a7, 57
                ecall
                bltz s3, .Lkof_iorr_inval   # #848: pread negativo = erro
                mv   a0, s3
                li   a1, 4
                call kof_array_alloc
                mv   s1, a0                 # Int[]
                li   t0, 0
            .Lkof_iorr_spread:
                bgeu t0, s3, .Lkof_iorr_done
                add  t1, s2, t0
                lbu  t1, 0(t1)
                slli t2, t0, 2
                add  t2, s1, t2
                sw   t1, 24(t2)
                addi t0, t0, 1
                j    .Lkof_iorr_spread
            .Lkof_iorr_done:
                mv   a0, s1
                j    .Lkof_iorr_ret
            .Lkof_iorr_err:
                mv   a0, s0
                call kof_io_throw_not_found
            .Lkof_iorr_inval:
                mv   a0, s0
                call kof_io_throw_unreadable
            .Lkof_iorr_ret:
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            """;
}

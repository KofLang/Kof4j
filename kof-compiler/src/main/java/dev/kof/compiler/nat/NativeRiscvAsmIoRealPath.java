package dev.kof.compiler.nat;

// #751 / D-MAINT-BATCH-0510 (IO1): kof_io_path_real_path no cross
// (riscv64 + aarch64). Contrato JVM (JvmRuntimeIo): caminho canônico com
// links resolvidos; null quando não existe. Aqui delega ao libc realpath
// (mesma convenção do host que o x86-64 usa via call realpath); a libc
// resolve a cadeia inteira (equivalente a toRealPath).
//   realpath(path@a0, buf@a1) -> buf@a0 | NULL   (buf de 4096 na pilha,
//   sem malloc: nada vaza fora do GC)
//   NULL -> 0 (String? nulo); sucesso -> kof_io_make_string(buf, strlen(buf))
// KofStr: len@16, bytes@24. kof_io_strlen/kof_io_make_string já no cross.
public final class NativeRiscvAsmIoRealPath {

    private NativeRiscvAsmIoRealPath() {}

    static String RISCV_RUNTIME_ASM_IO_REALPATH = """
            .section .text
            # kof_io_path_real_path(path@a0) -> KofStr* | 0
            .globl kof_io_path_real_path
            .type kof_io_path_real_path, @function
            kof_io_path_real_path:
                li   t0, -4112
                add  sp, sp, t0
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                mv   s0, a0
                addi a0, s0, 24            # payload NUL-terminado
                addi a1, sp, 16            # resolved buffer (4096)
                call realpath
                beqz a0, .Lkof_ioreal_none
                addi a0, sp, 16
                call kof_io_strlen
                mv   a1, a0
                addi a0, sp, 16
                call kof_io_make_string
                j    .Lkof_ioreal_done
            .Lkof_ioreal_none:
                li   a0, 0
            .Lkof_ioreal_done:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                li   t0, 4112
                add  sp, sp, t0
                ret
            """;
}

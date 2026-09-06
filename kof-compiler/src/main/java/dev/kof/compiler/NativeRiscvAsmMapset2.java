package dev.kof.compiler;

// FASE 3 (REFACTOR-500): fatia 2 de RISCV_MAPSET_ASM — runtime assembly riscv64.
// Concatenada em ordem por NativeRiscvAsm; corpo verbatim (fechamento na
// coluna 12 preserva o valor byte-idêntico ao original).
final class NativeRiscvAsmMapset2 {

    private NativeRiscvAsmMapset2() {}

    static final String RISCV_MAPSET_ASM_2 = """
            .globl kof_scheduler_cancel
            kof_scheduler_cancel:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)          # id
                sd   s1, 24(sp)          # walk
                sd   s2, 16(sp)          # &lock
                mv   s0, a0
                la   s2, kof_sched_lock
            .Lsc_lk:
                li   t1, 1
                amoswap.w t0, t1, (s2)
                bnez t0, .Lsc_lk
                la   t0, kof_sched_head
                ld   s1, 0(t0)
            .Lsc_walk:
                beqz s1, .Lsc_unlock
                mv   a0, s0
                ld   a1, 24(s1)          # job->id
                call kof_string_equals   # a0=1 se igual (s0/s1/s2 preservados)
                bnez a0, .Lsc_found
                ld   s1, 0(s1)
                j    .Lsc_walk
            .Lsc_found:
                sw   zero, 20(s1)        # active = 0
            .Lsc_unlock:
                sw   zero, 0(s2)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}

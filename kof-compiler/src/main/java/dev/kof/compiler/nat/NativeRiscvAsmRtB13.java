package dev.kof.compiler.nat;

// FASE 3 (STDLIB S7-wedge): fatia 13 de RISCV_RUNTIME_ASM_B — kof.time
// calendário civil: isLeapYear(year) e daysInMonth(year,month).
// Paridade exata com JVM/JS/x86: year < 1 => false/0.
//
// Bissexto via r = y % 400 (y>=1 => positivo; rem do riscv = trunc = floor).
// Como 400 é múltiplo de 4 e 100: y%4==0 <=> r%4==0, y%100==0 <=> r%100==0,
// y%400==0 <=> r==0.  leap = r%4==0 && (r%100!=0 || r==0).
// (rem é usado em produção no runtime riscv B0 desde sempre — seguro p/ o
// tradutor aarch64: sdiv+msub; verificado com qemu exit 0.)
//
// ⚠️ `.section .text` NO TOPO (lição do bug .rodata herdado, fix 7be4fd0a).
public final class NativeRiscvAsmRtB13 {

    static final String RISCV_RUNTIME_ASM_B_13 = """

            .section .text
            # kof_time_isLeapYear(a0=year) -> 0/1
            .globl kof_time_isLeapYear
            kof_time_isLeapYear:
                li   t0, 1
                blt  a0, t0, .Lv_ly_false
                li   t1, 400
                rem  t2, a0, t1          # r = y % 400
                li   t0, 0
                beqz t2, .Lv_ly_true     # r == 0 => /400
                li   t3, 4
                rem  t4, t2, t3
                li   t0, 0
                bnez t4, .Lv_ly_done     # r%4 != 0
                li   t3, 100
                rem  t4, t2, t3
                li   t0, 1
                bnez t4, .Lv_ly_done     # r%100 != 0 => leap
            .Lv_ly_false:
                li   t0, 0
            .Lv_ly_done:
                mv   a0, t0
                ret
            .Lv_ly_true:
                li   a0, 1
                ret

            # kof_time_daysInMonth(a0=year, a1=month) -> dias (inválido => 0)
            .globl kof_time_daysInMonth
            kof_time_daysInMonth:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                li   t0, 1
                blt  a0, t0, .Lv_dim_zero
                li   t1, 1
                blt  a1, t1, .Lv_dim_zero
                li   t1, 12
                bgt  a1, t1, .Lv_dim_zero
                li   s0, 31
                li   t1, 4
                beq  a1, t1, .Lv_dim_30
                li   t1, 6
                beq  a1, t1, .Lv_dim_30
                li   t1, 9
                beq  a1, t1, .Lv_dim_30
                li   t1, 11
                beq  a1, t1, .Lv_dim_30
                li   t1, 2
                beq  a1, t1, .Lv_dim_feb
                mv   a0, s0
                j    .Lv_dim_done
            .Lv_dim_30:
                li   s0, 30
                mv   a0, s0
                j    .Lv_dim_done
            .Lv_dim_feb:
                call kof_time_isLeapYear  # a0 já é year — mas a1 foi perdido?
                li   s0, 28
                bnez a0, .Lv_dim_29
                mv   a0, s0
                j    .Lv_dim_done
            .Lv_dim_29:
                li   a0, 29
            .Lv_dim_done:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .Lv_dim_zero:
                li   a0, 0
                ret
            """;
}

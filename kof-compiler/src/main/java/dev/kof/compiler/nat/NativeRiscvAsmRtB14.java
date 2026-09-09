package dev.kof.compiler.nat;

// FASE 3 (STDLIB S7.2): fatia 14 de RISCV_RUNTIME_ASM_B — kof.time serial
// civil: dayOfWeek(y,m,d)->1..7 (ISO, 1=seg) e daysBetween(y1..d2)->Int.
// Paridade exata com JVM/JS/x86: data válida = 1<=ano<=9999, 1<=mês<=12,
// 1<=dia<=daysInMonth; inválida => 0 nos dois.
//
// Serial = epoch_day de Hinnant. Ano>=1 => todos dividendos não-negativos =>
// div/rem SIGNADOS = floor (tradutor aarch64: sdiv+msub, idêntico). mod-7 com
// bias +719470 (=719468+2; mínimo ano 1 = 308>0) => rem positivo puro.
//
// Registro-vivo-entre-calls: NENHUM (todas as 6 datas ficam em slots de
// pilha, recarregadas antes de cada helper). helpers: a0,a1,a2 in; a0 out;
// clobberam t0..t5 e (kdv_valid, por call p/ daysInMonth) s0.
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB14 {

    static final String RISCV_RUNTIME_ASM_B_14 = """

            .section .text
            # kdv_valid(a0=y,a1=m,a2=d) -> a0 0/1
            kdv_valid:
                li   t0, 1
                blt  a0, t0, .Lv_kv_f
                li   t0, 9999
                blt  t0, a0, .Lv_kv_f
                li   t0, 1
                blt  a1, t0, .Lv_kv_f
                li   t0, 12
                blt  t0, a1, .Lv_kv_f
                li   t0, 1
                blt  a2, t0, .Lv_kv_f
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a2, 16(sp)
                call kof_time_daysInMonth      # a0 = dim(year,mês); a1 vivo
                ld   t5, 16(sp)                # d
                ld   ra, 24(sp)
                addi sp, sp, 32
                blt  a0, t5, .Lv_kv_f          # d > dim => inválida
                li   a0, 1
                ret
            .Lv_kv_f:
                li   a0, 0
                ret

            # kdv_epoch(a0=y,a1=m,a2=d) -> a0 = dias-civil (pré-validado)
            kdv_epoch:
                li   t0, 2
                bgt  a1, t0, .Lv_ke_mp_off
                addi t6, a0, -1                # y2 = y-1  (m<=2)
                j    .Lv_ke_y2
            .Lv_ke_mp_off:
                mv   t6, a0
            .Lv_ke_y2:
                li   t1, 400
                div  t1, t6, t1                # era  (vivo só até o mul era*146097)
                li   t0, 400
                mul  t2, t1, t0
                sub  t2, t6, t2                # yoe = y2-era*400  (0..399)
                li   t0, 2
                bgt  a1, t0, .Lv_ke_mp_m3
                addi t3, a1, 9                 # mp = m+9
                j    .Lv_ke_mp
            .Lv_ke_mp_m3:
                addi t3, a1, -3                # mp = m-3
            .Lv_ke_mp:
                li   t0, 153
                mul  t3, t3, t0
                addi t3, t3, 2
                li   t0, 5
                div  t3, t3, t0                # (153mp+2)/5
                add  t3, t3, a2
                addi t3, t3, -1                # doy
                li   t0, 365
                mul  t4, t2, t0
                li   t0, 4
                div  t6, t2, t0                # yoe/4
                add  t4, t4, t6
                li   t0, 100
                div  t6, t2, t0                # yoe/100 (Hinnant: subtrai o QUOCIENTE)
                sub  t4, t4, t6                # yoe*365+yoe/4-yoe/100
                add  t4, t4, t3                # doe
                li   t0, 146097
                mul  t1, t1, t0                # era*146097
                add  t4, t4, t1
                li   t0, 719468
                sub  a0, t4, t0
                ret

            # kof_time_dayOfWeek(a0=y,a1=m,a2=d) -> 1..7 | 0
            .globl kof_time_dayOfWeek
            kof_time_dayOfWeek:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)                # y
                call kdv_valid
                beqz a0, .Lv_dw_f
                ld   a0, 16(sp)                # recarrega y (valid clobberou a0/t)
                call kdv_epoch                 # a0 = ed  (a1,a2 ainda válidos? não — reload)
                li   t0, 719470
                add  a0, a0, t0
                li   t1, 7
                rem  a0, a0, t1
                addi a0, a0, 1
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lv_dw_f:
                li   a0, 0
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_time_daysBetween(a0..a5 = y1,m1,d1,y2,m2,d2) -> Int | 0
            .globl kof_time_daysBetween
            kof_time_daysBetween:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   a0, 8(sp)                 # y1
                sd   a1, 16(sp)                # m1
                sd   a2, 24(sp)                # d1
                sd   a3, 32(sp)                # y2
                sd   a4, 40(sp)                # m2
                sd   a5, 48(sp)                # d2
                # valida 1
                call kdv_valid
                beqz a0, .Lv_db_f
                # ep1
                ld   a0, 8(sp)
                ld   a1, 16(sp)
                ld   a2, 24(sp)
                call kdv_epoch
                sd   a0, 0(sp)                 # ep1
                # valida 2
                ld   a0, 32(sp)
                ld   a1, 40(sp)
                ld   a2, 48(sp)
                call kdv_valid
                beqz a0, .Lv_db_f
                # ep2
                ld   a0, 32(sp)
                ld   a1, 40(sp)
                ld   a2, 48(sp)
                call kdv_epoch
                ld   t0, 0(sp)                 # ep1
                sub  a0, a0, t0                # ep2 - ep1
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lv_db_f:
                li   a0, 0
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}

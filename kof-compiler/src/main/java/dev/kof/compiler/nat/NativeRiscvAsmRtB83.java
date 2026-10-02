package dev.kof.compiler.nat;

// FASE (STDLIB S7a-ext3): kof_time startOf/endOf em data ISO (riscv64).
// Domínio isolado (regra <=500 linhas/classe). MESMO arquivo .s do cross-emit:
// .Lu8_parse2/.Lu8_put4/.Lu8_put2 (B33), kof_time_addDays (B33),
// kof_time_dayOfWeek (B14) e kof_time_daysInMonth já visíveis (label
// file-scope). COMPOSIÇÃO dos primitivos com paridade provada => byte-idêntico
// aos demais 4 alvos por construção. unit = day|week|month|year; semana =
// segunda..domingo; inválida/unit desconhecida/fora de 1..9999 => "".
// Convenção de regs vivos-entre-calls: NENHUM em caller (tudo em slots de
// pilha, recarregado antes de cada helper — lição B14). s0 é clobberado por
// kdv_valid/daysInMonth (não usado aqui).
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB83 {

    static  String RISCV_RUNTIME_ASM_B_83 = """

            .section .text

            # kof_time_startOf(a0=iso,a1=unit) / kof_time_endOf(a0=iso,a1=unit)
            # -> String (alloc; "" quando data inválida, unit desconhecida ou
            # resultado fora de 1..9999 — guardas nativas do addDays na rota
            # week). Flag 1=start/0=end vivE só no sw de entrada (t6 é
            # caller-saved; nenhum call antes do sw).
            .globl kof_time_startOf
            kof_time_startOf:
                li   t6, 1
                j    .Lsb_in
            .globl kof_time_endOf
            kof_time_endOf:
                li   t6, 0
            .Lsb_in:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s1, 48(sp)              # iso
                sd   s2, 40(sp)              # unit
                sd   s3, 32(sp)              # render len
                sd   s4, 24(sp)              # alloc result
                sw   t6, 12(sp)              # flag start/end
                li   t0, 0
                sw   t0, 16(sp)              # present = 0
                mv   s1, a0
                mv   s2, a1
                mv   a1, sp                  # slots 0/4/8 = y/m/d
                call .Lu8_parse2
                beqz a0, .Lsb_r
                lw   a0, 0(sp)
                lw   a1, 4(sp)
                lw   a2, 8(sp)
                call kdv_valid               # a0 = 0/1
                beqz a0, .Lsb_r
                lw   t0, 16(s2)              # len da unit
                lb   t1, 24(s2)              # primeiro byte
                # day=3 / week=4 / month=5 / year=4 -> len 4 DESAMBIGUA pelo
                # primeiro byte: 'w'=semana, 'y'=ano (MESMO despacho x86).
                li   t2, 3
                beq  t0, t2, .Lsb_day
                li   t2, 5
                beq  t0, t2, .Lsb_month
                li   t2, 4
                bne  t0, t2, .Lsb_r
                li   t2, 119                 # 'w'
                beq  t1, t2, .Lsb_week
                li   t2, 121                 # 'y'
                beq  t1, t2, .Lsb_year
                j    .Lsb_r
            .Lsb_day:
                li   t2, 100                 # 'd'
                bne  t1, t2, .Lsb_r
                j    .Lsb_p
            .Lsb_week:
                lw   a0, 0(sp)
                lw   a1, 4(sp)
                lw   a2, 8(sp)
                call kof_time_dayOfWeek      # a0 = dow 1..7
                mv   t3, a0
                lw   t4, 12(sp)
                beqz t4, .Lsb_wend
                li   a1, 1
                sub  a1, a1, t3              # -(dow-1)
                mv   a0, s1
                call kof_time_addDays
                j    .Lsb_ret
            .Lsb_wend:
                li   a1, 7
                sub  a1, a1, t3              # 7-dow
                mv   a0, s1
                call kof_time_addDays
                j    .Lsb_ret
            .Lsb_year:
                lw   t4, 12(sp)
                beqz t4, .Lsb_yend
                li   t0, 1
                sw   t0, 4(sp)               # m1 = 1
                sw   t0, 8(sp)               # d1 = 1
                j    .Lsb_p
            .Lsb_yend:
                li   t0, 12
                sw   t0, 4(sp)               # m1 = 12
                li   t0, 31
                sw   t0, 8(sp)               # d1 = 31
                j    .Lsb_p
            .Lsb_month:
                lw   t4, 12(sp)
                bnez t4, .Lsb_mstart
                lw   a0, 0(sp)
                lw   a1, 4(sp)
                call kof_time_daysInMonth    # a0 = dim(y,m)
                sw   a0, 8(sp)               # d1 = dim
                j    .Lsb_p
            .Lsb_mstart:
                li   t0, 1
                sw   t0, 8(sp)               # d1 = 1
            .Lsb_p:
                li   t0, 1
                sw   t0, 16(sp)              # present = 1
            .Lsb_r:
                lw   t0, 16(sp)
                beqz t0, .Lsb_r0
                li   s3, 10
                j    .Lsb_a
            .Lsb_r0:
                li   s3, 0
            .Lsb_a:
                addi a0, s3, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                li   t0, 0
                sw   t0, 4(s4)
                sd   t0, 8(s4)
                sw   s3, 16(s4)
                sw   t0, 20(s4)
                sb   t0, 24(s4)
                lw   t0, 16(sp)
                beqz t0, .Lsb_d
                addi a0, s4, 24
                lw   a1, 0(sp)
                call .Lu8_put4
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 4(sp)
                call .Lu8_put2
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 8(sp)
                call .Lu8_put2
                li   t0, 0
                sb   t0, 0(a0)
            .Lsb_d:
                mv   a0, s4
            .Lsb_ret:
                ld   s4, 24(sp)
                ld   s3, 32(sp)
                ld   s2, 40(sp)
                ld   s1, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            """;
}

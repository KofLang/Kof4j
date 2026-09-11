package dev.kof.compiler.nat;

// FASE (STDLIB S7c-1, TIME002): fatia 35 de RISCV_RUNTIME_ASM_B — kof.time
// addDays/diffDays em data ISO riscv64. Port fiel do x86 (runtime/
// RuntimeTimeIso.java, transcrição byte-a-byte das máquinas .Lka_parse2/
// .Lka_civil/.Lka_put4/.Lka_put2): parse estrito YYYY-MM-DD (dígitos + '-'
// em 4/7, validação kdv_valid da B14), epoch Hinnant (kdv_epoch da B14),
// inversa civil canônica, render %04d-%02d-%02d, inválida => "" (add) /
// 0 (diff), fora de [-719162, 2932896] => "". Layout String: typeId=1@0 /
// len@16 / bytes@24 / NUL; kof_alloc (len+25+15)&-16 (precedente B34).
//
// Convenções (mesmas B14/B33): args a0.., retorno a0; helpers locais
// kdv_valid/kdv_epoch (B14, definida antes na concatenação); estado vivo
// entre calls SEMPRE em s-regs salvos (t0..t6 caller-saved; kdv_valid
// clobbera t0..t5+s0 mas NUNCA s1..s11 — verificado B13/B14);
// divu/remu UNSIGNED = ex-equivalência com divl do x86 (dividendos não-
// negativos no domínio; tradutor: divu→udiv, remu→udiv+msub — cobertos).
// sext.w nos resultados Int de diffDays (lição B32/§103: wrap int32 do
// x86/JVM garantido nos dois alvos).
//
// ⚠ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB35 {

    private NativeRiscvAsmRtB35() {}

    static final String RISCV_RUNTIME_ASM_B_35 = """

            .section .text
            # kta_parse2(a0=str, a1=&out[3 ints]) -> a0 1 ok / 0 bad
            # (espelho .Lka_parse2: len==10, '-'@4/@7, byte em 47..58 ou '-',
            #  y=pos0..3 m=5..6 d=8..9, valida via kdv_valid)
            kta_parse2:
                beqz a0, .Lta_p_bad
                lw   t0, 16(a0)
                li   t1, 10
                bne  t0, t1, .Lta_p_bad
                addi t2, a0, 24            # &bytes (t2: kta_parse2 não pode
                lbu  t0, 4(t2)             #  tocar s-regs — não salva nenhum;
                                           #  sem call no loop digit->num)
                li   t1, 45
                bne  t0, t1, .Lta_p_bad
                lbu  t0, 7(t2)
                bne  t0, t1, .Lta_p_bad
                li   t3, 0                 # i
            .Lta_p_digit:
                li   t1, 10
                bgeu t3, t1, .Lta_p_num
                add  t0, t2, t3
                lbu  t0, 0(t0)
                li   t1, 45
                beq  t0, t1, .Lta_p_skipc
                li   t1, 47
                blt  t1, t0, .Lta_p_ck9
                j    .Lta_p_bad            # <= '/'
            .Lta_p_ck9:
                li   t1, 58
                blt  t0, t1, .Lta_p_skipc
                j    .Lta_p_bad            # >= ':'
            .Lta_p_skipc:
                addi t3, t3, 1
                j    .Lta_p_digit
            .Lta_p_num:
                li   t4, 0                 # acc
                li   t3, 0
            .Lta_p_yl:
                add  t0, t2, t3
                lbu  t0, 0(t0)
                addi t0, t0, -48
                li   t1, 10
                mul  t4, t4, t1
                add  t4, t4, t0
                addi t3, t3, 1
                li   t1, 4
                blt  t3, t1, .Lta_p_yl
                sw   t4, 0(a1)
                li   t4, 0
                li   t3, 5
            .Lta_p_ml:
                add  t0, t2, t3
                lbu  t0, 0(t0)
                addi t0, t0, -48
                li   t1, 10
                mul  t4, t4, t1
                add  t4, t4, t0
                addi t3, t3, 1
                li   t1, 7
                blt  t3, t1, .Lta_p_ml
                sw   t4, 4(a1)
                li   t4, 0
                li   t3, 8
            .Lta_p_dl:
                add  t0, t2, t3
                lbu  t0, 0(t0)
                addi t0, t0, -48
                li   t1, 10
                mul  t4, t4, t1
                add  t4, t4, t0
                addi t3, t3, 1
                li   t1, 10
                blt  t3, t1, .Lta_p_dl
                sw   t4, 8(a1)
                lw   a0, 0(a1)
                lw   a2, 8(a1)
                lw   a1, 4(a1)             # (a1 lido por último — era o bug)
                j    kdv_valid
            .Lta_p_bad:
                li   a0, 0
                ret

            # kta_civil(a0=days int32) -> a1=y, a2=m, a3=d
            # (espelho .Lka_civil Hinnant; era=(z)/146097, doe0=resto,
            #  yoe=doe0-doe0/1460+doe0/36524-doe0/146096, y=era*400+yoe/365,
            #  r11=yoe*365+yoe/4-yoe/100, doy=doe0-r11, mp=(5doy+2)/153,
            #  d=doy-(153mp+2)/5+1, m=mp+3 | mp-9 & y++)
            kta_civil:
                li   t0, 719468
                add  a0, a0, t0            # z = days + 719468 (>= 5306 no domínio)
                li   t0, 146097
                divu a4, a0, t0            # era
                remu a5, a0, t0            # doe (vivo até o final — a5 não
                li   t0, 1460              #  é tocado por mul/divu/remu)
                divu t2, a5, t0
                mv   t1, a5                # r8 = doe  (movl %r10d,%r8d x86)
                sub  t1, t1, t2            # r8 -= doe/1460
                li   t0, 36524
                divu t2, a5, t0
                add  t1, t1, t2            # r8 += doe/36524
                li   t0, 146096
                divu t2, a5, t0
                sub  t1, t1, t2            # r8 -= doe/146096
                li   t0, 365
                divu t1, t1, t0            # r8 = yoe (sobrescreve, como o x86
                li   t0, 400               #  movl %eax,%r8d)
                mul  a1, a4, t0
                add  a1, a1, t1            # y = era*400 + yoe
                li   t0, 365
                mul  t3, t1, t0            # yoe*365
                li   t0, 4
                divu t4, t1, t0
                add  t3, t3, t4            # + yoe/4
                li   t0, 100
                divu t4, t1, t0
                sub  t3, t3, t4            # r11 = yoe*365 + yoe/4 - yoe/100
                sub  t4, a5, t3            # doy = doe - r11
                li   t0, 5
                mul  t5, t4, t0
                addi t5, t5, 2
                li   t0, 153
                divu t5, t5, t0            # mp
                li   t0, 153
                mul  t6, t5, t0
                addi t6, t6, 2
                li   t0, 5
                divu t6, t6, t0
                sub  a3, t4, t6
                addi a3, a3, 1             # d -> a3 (convenção: a1=y a2=m a3=d)
                li   t0, 10
                bgeu t5, t0, .Lta_c_late   # mp >= 10 (unsigned, = x86 jae)
                addi a2, t5, 3             # m = mp+3 -> a2
                j    .Lta_c_ret
            .Lta_c_late:
                addi a2, t5, -9            # m = mp-9
                addi a1, a1, 1             # y++
            .Lta_c_ret:
                ret

            # kta_put4(a0=&buf, a1=v 0..9999) -> a0 avança 4
            kta_put4:
                li   t0, 1000
                divu t1, a1, t0
                remu a1, a1, t0
                addi t1, t1, 48
                sb   t1, 0(a0)
                addi a0, a0, 1
                li   t0, 100
                divu t1, a1, t0
                remu a1, a1, t0
                addi t1, t1, 48
                sb   t1, 0(a0)
                addi a0, a0, 1
                li   t0, 10
                divu t1, a1, t0
                remu a1, a1, t0
                addi t1, t1, 48
                sb   t1, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 48
                sb   a1, 0(a0)
                addi a0, a0, 1
                ret

            # kta_put2(a0=&buf, a1=v 0..99) -> a0 avança 2
            kta_put2:
                li   t0, 10
                divu t1, a1, t0
                remu a1, a1, t0
                addi t1, t1, 48
                sb   t1, 0(a0)
                addi a0, a0, 1
                addi a1, a1, 48
                sb   a1, 0(a0)
                addi a0, a0, 1
                ret

            # kof_time_addDays(a0=str, a1=n) -> KofStr* ("" se inválida/fora)
            .globl kof_time_addDays
            kof_time_addDays:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                # out[0..11] = sp+0..11 — saves a partir de 16 (sem colisão)
                sext.w s0, a1              # n (wrap int32 do x86 movslq)
                addi a1, sp, 0             # &out[0..11]
                call kta_parse2
                li   s1, 0                 # ok flag
                beqz a0, .Lta_ad_render
                lw   a0, 0(sp)
                lw   a1, 4(sp)
                lw   a2, 8(sp)
                call kdv_epoch             # a0 = epoch_day
                sext.w s2, a0
                add  s2, s2, s0            # days
                li   t0, -719162
                blt  s2, t0, .Lta_ad_render
                li   t0, 2932896
                bgt  s2, t0, .Lta_ad_render
                mv   a0, s2
                call kta_civil             # a1=y a2=m a3=d
                sw   a1, 0(sp)
                sw   a2, 4(sp)
                sw   a3, 8(sp)
                li   s1, 1
            .Lta_ad_render:
                li   s3, 0
                beqz s1, .Lta_ad_alloc
                li   s3, 10
            .Lta_ad_alloc:
                addi a0, s3, 40
                andi a0, a0, -16
                call kof_alloc
                mv   s4, a0
                li   t0, 1
                sw   t0, 0(s4)
                sw   zero, 4(s4)
                sd   zero, 8(s4)
                sw   s3, 16(s4)
                sw   zero, 20(s4)
                sb   zero, 24(s4)
                beqz s1, .Lta_ad_done
                addi a0, s4, 24
                lw   a1, 0(sp)
                call kta_put4
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 4(sp)
                call kta_put2
                li   t0, 45
                sb   t0, 0(a0)
                addi a0, a0, 1
                lw   a1, 8(sp)
                call kta_put2
                sb   zero, 0(a0)
            .Lta_ad_done:
                mv   a0, s4
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                addi sp, sp, 64
                ret

            # kof_time_diffDays(a0=str1, a1=str2) -> Int (0 se inválida)
            .globl kof_time_diffDays
            kof_time_diffDays:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a1                # str2
                addi a1, sp, 0
                call kta_parse2
                beqz a0, .Lta_dd0
                mv   a0, s0
                addi a1, sp, 12
                call kta_parse2
                beqz a0, .Lta_dd0
                lw   a0, 0(sp)
                lw   a1, 4(sp)
                lw   a2, 8(sp)
                call kdv_epoch
                sext.w a0, a0
                mv   s1, a0
                lw   a0, 12(sp)
                lw   a1, 16(sp)
                lw   a2, 20(sp)
                call kdv_epoch
                sext.w a0, a0
                sub  a0, a0, s1
                sext.w a0, a0
                j    .Lta_dd_done
            .Lta_dd0:
                li   a0, 0
            .Lta_dd_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                addi sp, sp, 48
                ret
            """;
}

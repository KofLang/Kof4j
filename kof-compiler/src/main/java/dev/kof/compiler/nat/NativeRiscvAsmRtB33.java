package dev.kof.compiler.nat;

// FASE (STDLIB S7c/TIME002): fatia 33 de RISCV_RUNTIME_ASM_B — kof.time
// addDays/diffDays em data ISO. Port do x86 RuntimeTimeIso (S7c): .Lka_parse2
// -> .Lu8_parse2 (valida formato + kdv_valid), .Lka_civil -> .Lu8_civil
// (inversa Hinnant dias-civil -> y/m/d), put4/put2 -> .Lu8_put4/.Lu8_put2.
// Reusa kdv_valid/kdv_epoch da B14 (mesmo arquivo .s; helpers clobberam
// t0..t6 e s0 — nenhum valor vivo em regs entre calls, tudo em slots de
// pilha, lição B14). Convenção: a0/a1 args, a0 retorno (String* p/ addDays,
// Int p/ diffDays) — generic path do cross-emit (pop a0/a1, push a0).
// Semântica travada (x86/JVM/JS): inválida => "" (add) / 0 (diff); epoch+n
// fora de [-719162, 2932896] (ano 1..9999) => "". aarch = tradutor.
// LIÇÕES aplicadas: frame -64 16-align p/ kof_alloc; .section .text no topo
// (7be4fd0a); sem t6 novo (usar t0..t5); blt/bge SIGNED no bounds (S7 wedge).
public final class NativeRiscvAsmRtB33 {

    static final String RISCV_RUNTIME_ASM_B_33 = """

            .section .text

            # .Lu8_parse2(a0=str, a1=slotptr[y@0,m@4,d@8]) -> a0 0/1
            # (formato YYYY-MM-DD + digitos + kdv_valid — igual .Lka_parse2;
            # label local: só chamada dentro deste .s, padrão kdv_valid/B14)
            .Lu8_parse2:
                beqz a0, .Lu8_p_bad
                lw t0, 16(a0)
                li t1, 10
                bne t0, t1, .Lu8_p_bad
                addi t2, a0, 24              # bytes
                mv a4, a1                    # slotptr
                li a5, 45                    # '-'
                lb t0, 4(t2)
                bne t0, a5, .Lu8_p_bad
                lb t0, 7(t2)
                bne t0, a5, .Lu8_p_bad
                li a0, 0                     # i (checa digito em todo byte)
            .Lu8_p_ck:
                li t1, 10
                bge a0, t1, .Lu8_p_yl0
                add t0, t2, a0
                lbu t0, 0(t0)
                beq t0, a5, .Lu8_p_cks
                li t1, 48
                blt t0, t1, .Lu8_p_bad
                li t1, 58
                bge t0, t1, .Lu8_p_bad
            .Lu8_p_cks:
                addi a0, a0, 1
                j .Lu8_p_ck
            .Lu8_p_yl0:
                li a1, 0                     # y
                li a2, 0
            .Lu8_p_yl:
                add t0, t2, a2
                lbu t0, 0(t0)
                addi t0, t0, -48
                li t1, 10
                mul a1, a1, t1
                add a1, a1, t0
                addi a2, a2, 1
                li t1, 4
                blt a2, t1, .Lu8_p_yl
                sw a1, 0(a4)
                li a1, 0
                li a2, 5
            .Lu8_p_ml:
                add t0, t2, a2
                lbu t0, 0(t0)
                addi t0, t0, -48
                li t1, 10
                mul a1, a1, t1
                add a1, a1, t0
                addi a2, a2, 1
                li t1, 7
                blt a2, t1, .Lu8_p_ml
                sw a1, 4(a4)
                li a1, 0
                li a2, 8
            .Lu8_p_dl:
                add t0, t2, a2
                lbu t0, 0(t0)
                addi t0, t0, -48
                li t1, 10
                mul a1, a1, t1
                add a1, a1, t0
                addi a2, a2, 1
                li t1, 10
                blt a2, t1, .Lu8_p_dl
                sw a1, 8(a4)
                lw a0, 0(a4)
                lw a1, 4(a4)
                lw a2, 8(a4)
                # TAIL-JMP (lição riscv — diferente do x86): `call kdv_valid`
                # + `ret` sobrescreveria o ra do caller do parse2 (jalr usa
                # ra, não pilha) = loop infinito no ret (pegado no trace qemu
                # 11/09). O ret do kdv_valid volta DIRETO ao caller do parse2.
                j kdv_valid
            .Lu8_p_bad:
                li a0, 0
                ret

            # .Lu8_civil(a0=days) -> a0=y, a1=m, a2=d (Hinnant civil_from_days;
            # z = days+719468 >= 0 garantido pelo bounds do caller -> divu ok).
            # Transcrição 1:1 do .Lka_civil x86 (RuntimeTimeIso):
            #   era=z/146097; doe=z%146097;
            #   yoe=doe-doe/1460+doe/36524-doe/146096; y=era*400+yoe;
            #   doy=doe-(yoe*365+yoe/4-yoe/100); mp=(5doy+2)/153;
            #   day=doy-(153mp+2)/5+1; month=mp<10?mp+3:mp-9 (+1 no ano se late)
            .Lu8_civil:
                li t0, 719468
                add t0, a0, t0               # z
                li t1, 146097
                divu t2, t0, t1              # era
                remu t0, t0, t1              # doe
                mv t3, t0                    # yoe acumulado
                li t1, 1460
                divu t4, t0, t1
                sub t3, t3, t4               # doe - doe/1460
                li t1, 36524
                divu t4, t0, t1
                add t3, t3, t4
                li t1, 146096
                divu t4, t0, t1
                sub t3, t3, t4
                li t1, 365
                divu t3, t3, t1              # yoe = r/365 (x86 divl $365 —
                                             # esquecido na 1a transcrição)
                li t1, 400
                mul t2, t2, t1
                add a0, t2, t3               # year = era*400 + yoe
                li t1, 365
                mul t4, t3, t1
                li t1, 4
                divu t6, t3, t1
                add t4, t4, t6
                li t1, 100
                divu t6, t3, t1
                sub t4, t4, t6
                sub t5, t0, t4               # doy
                li t1, 5
                mul t6, t5, t1
                addi t6, t6, 2
                li t1, 153
                divu t6, t6, t1              # mp
                li t1, 153
                mul t2, t6, t1
                addi t2, t2, 2
                li t1, 5
                divu t2, t2, t1
                sub t2, t5, t2
                addi a2, t2, 1               # day
                mv a1, t6                    # month prov. = mp
                li t0, 10
                bge t6, t0, .Lu8_c_late
                addi a1, a1, 3
                ret
            .Lu8_c_late:
                li t0, 9
                sub a1, a1, t0
                addi a0, a0, 1
                ret

            # .Lu8_put4(a0=dest, a1=val 0..9999) -> a0=dest+4 (só t-regs)
            .Lu8_put4:
                li t1, 1000
                divu t0, a1, t1
                addi t0, t0, 48
                sb t0, 0(a0)
                remu a1, a1, t1
                addi a0, a0, 1
                li t1, 100
                divu t0, a1, t1
                addi t0, t0, 48
                sb t0, 0(a0)
                remu a1, a1, t1
                addi a0, a0, 1
                li t1, 10
                divu t0, a1, t1
                addi t0, t0, 48
                sb t0, 0(a0)
                remu a1, a1, t1
                addi a0, a0, 1
                addi t0, a1, 48
                sb t0, 0(a0)
                addi a0, a0, 1
                ret

            # .Lu8_put2(a0=dest, a1=val 0..99) -> a0=dest+2
            .Lu8_put2:
                li t1, 10
                divu t0, a1, t1
                addi t0, t0, 48
                sb t0, 0(a0)
                remu a1, a1, t1
                addi a0, a0, 1
                addi t0, a1, 48
                sb t0, 0(a0)
                addi a0, a0, 1
                ret

            # kof_time_addDays(a0=str, a1=n) -> String (alloc; inválida ou
            # fora de ano 1..9999 => "" — paridade x86/JVM/JS).
            # LIÇÃO B14: kdv_valid clobbers s0 (call daysInMonth) — NADA vivo
            # em s0 entre calls; s1/s2/s3/s4 são seguros (helpers não tocam).
            .globl kof_time_addDays
            kof_time_addDays:
                addi sp, sp, -64
                sd ra, 56(sp)
                sd s1, 40(sp)
                sd s2, 32(sp)
                sd s3, 24(sp)
                sd s4, 16(sp)
                mv s1, a1                    # n
                li s3, 0                     # ok?
                mv a1, sp                    # slots 0/4/8
                call .Lu8_parse2
                beqz a0, .Lu8_ad_r
                lw a0, 0(sp)
                lw a1, 4(sp)
                lw a2, 8(sp)
                call kdv_epoch
                sext.w a0, a0
                sext.w t0, s1
                add a0, a0, t0               # epoch + n (64-bit, sem overflow)
                li t0, -719162
                blt a0, t0, .Lu8_ad_r
                li t0, 2932896
                bgt a0, t0, .Lu8_ad_r
                call .Lu8_civil              # a0=y a1=m a2=d
                sw a0, 0(sp)
                sw a1, 4(sp)
                sw a2, 8(sp)
                li s3, 1
            .Lu8_ad_r:
                li s2, 10
                beqz s3, .Lu8_ad_r0
                j .Lu8_ad_a
            .Lu8_ad_r0:
                li s2, 0
            .Lu8_ad_a:
                addi a0, s2, 25
                addi a0, a0, 15
                andi a0, a0, -16
                call kof_alloc
                mv s4, a0
                li t0, 1
                sw t0, 0(s4)
                li t0, 0
                sw t0, 4(s4)
                sd t0, 8(s4)
                sw s2, 16(s4)
                sw t0, 20(s4)
                sb t0, 24(s4)                # NUL em bytes[0] SEMPRE (x86 faz
                                             # movb $0,24 unconditional)
                beqz s3, .Lu8_ad_d
                addi a0, s4, 24
                lw a1, 0(sp)
                call .Lu8_put4
                li t0, 45
                sb t0, 0(a0)
                addi a0, a0, 1
                lw a1, 4(sp)
                call .Lu8_put2
                li t0, 45
                sb t0, 0(a0)
                addi a0, a0, 1
                lw a1, 8(sp)
                call .Lu8_put2
                li t0, 0
                sb t0, 0(a0)
            .Lu8_ad_d:
                mv a0, s4
                ld s4, 16(sp)
                ld s3, 24(sp)
                ld s2, 32(sp)
                ld s1, 40(sp)
                ld ra, 56(sp)
                addi sp, sp, 64
                ret

            # kof_time_diffDays(a0=str1, a1=str2) -> Int (epoch2-epoch1;
            # qualquer inválida => 0). LIÇÃO B14: nenhum s0 vivo entre calls
            # (parse2->kdv_valid->daysInMonth clobbers s0); str2 fica em SLOT
            # de pilha, epoch1 em s1 (safe).
            .globl kof_time_diffDays
            kof_time_diffDays:
                addi sp, sp, -48
                sd ra, 40(sp)
                sd s1, 32(sp)
                sd a1, 24(sp)                # str2 (slot — NÃO s0!)
                mv a1, sp
                call .Lu8_parse2
                beqz a0, .Lu8_dd0
                ld a0, 24(sp)
                addi a1, sp, 12
                call .Lu8_parse2
                beqz a0, .Lu8_dd0
                lw a0, 0(sp)
                lw a1, 4(sp)
                lw a2, 8(sp)
                call kdv_epoch
                sext.w a0, a0
                mv s1, a0
                lw a0, 12(sp)
                lw a1, 16(sp)
                lw a2, 20(sp)
                call kdv_epoch
                sext.w a0, a0
                sub a0, a0, s1
                j .Lu8_dd_d
            .Lu8_dd0:
                li a0, 0
            .Lu8_dd_d:
                ld s1, 32(sp)
                ld ra, 40(sp)
                addi sp, sp, 48
                ret
        """;
}

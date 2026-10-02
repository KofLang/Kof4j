package dev.kof.compiler.nat;

// FASE 3 (STDLIB S12c): fatia 84 de RISCV_RUNTIME_ASM_B — kof.validation
// creditCardBrand/last4. Espelha RuntimeValidationCard (x86) / JVM / JS.
// Brand: mesma extracao do isCreditCard (B17); >19 digitos => ""; exige Luhn
// valido e 13..19 digitos; prefixos Visa 4 / Mastercard 51..55 / Amex 34,37 /
// Discover 6011,65; invalido ou sem marca => "". Last4: janela rolante de 19
// com flag de estouro; ultimos 4 (>=4; >19 => "").
// LAYOUT string (idem B83): tag=1@0, 0@4, 0@8 (sd), len@16 (sw), 0@20,
// bytes@24. kof_alloc clobbers a0-a6/t* e PRESERVA s0..s4 (corpo em B42);
// sp do chamador nao se move atraves do call — a base do buf vive em t5 e o
// obj em callee-saved. IMEDIATOS de string: little-endian par-a-par (byte k
// = char k), montados com slli/or (faixa I; o tradutor aarch trata slli>=32).
// MAPA brand: s0=str s1=n s2=len entrada s3=len resultado s4=obj
//   t5=buf t6=i; luhn: t2=j t4=sum t3=paridade t0/t1=v/tmp.
// MAPA last4: s0=str s1=n s2=i s3=estouro-entao-obj s4=len entrada t5=buf.
// ⚠️ `.section .text` NO TOPO (lição 7be4fd0a).
public final class NativeRiscvAsmRtB84 {

    static String RISCV_RUNTIME_ASM_B_84 = """

            .section .text
            # kof_validation_creditCardBrand(a0=str) -> a0 String
            .globl kof_validation_creditCardBrand
            kof_validation_creditCardBrand:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                mv   s0, a0              # str
                mv   t5, sp              # buf[19]
                li   s1, 0               # n
                li   t6, 0               # i
                li   s3, 0               # len do resultado (0 = "")
                beqz s0, .Lb84_zero
                lw   s2, 16(s0)          # len da entrada
            .Lb84_collect:
                bge  t6, s2, .Lb84_nchk
                add  t0, s0, t6
                lbu  t0, 24(t0)          # c
                addi t6, t6, 1
                li   t1, 48
                blt  t0, t1, .Lb84_collect
                li   t1, 57
                bgt  t0, t1, .Lb84_collect
                li   t1, 19
                bge  s1, t1, .Lb84_zero  # >19 digitos => ""
                addi t0, t0, -48
                add  t1, t5, s1
                sb   t0, 0(t1)
                addi s1, s1, 1
                j    .Lb84_collect
            .Lb84_nchk:
                li   t1, 13
                blt  s1, t1, .Lb84_zero  # marca exige 13..19
                li   t2, 0               # j
                li   t4, 0               # sum
            .Lb84_sum:
                bge  t2, s1, .Lb84_luhn
                addi t3, s1, -1
                sub  t3, t3, t2          # n-1-j
                andi t3, t3, 1           # impar-contando-da-direita?
                add  t1, t5, t2
                lbu  t1, 0(t1)           # v
                beqz t3, .Lb84_add
                add  t1, t1, t1          # v*2
                li   t0, 9
                ble  t1, t0, .Lb84_add
                addi t1, t1, -9
            .Lb84_add:
                add  t4, t4, t1
                addi t2, t2, 1
                j    .Lb84_sum
            .Lb84_luhn:
                li   t1, 10
                rem  t0, t4, t1          # soma <= 19*9 = 171 > 0 — idem B17
                bnez t0, .Lb84_zero      # Luhn invalido => ""
                lbu  t0, 0(t5)           # d0
                lbu  t1, 1(t5)           # d1
                li   t2, 4
                beq  t0, t2, .Lb84_set4
                li   t2, 5
                beq  t0, t2, .Lb84_mc
                li   t2, 3
                beq  t0, t2, .Lb84_amex
                li   t2, 6
                beq  t0, t2, .Lb84_disc
                j    .Lb84_zero
            .Lb84_mc:
                li   t2, 1
                blt  t1, t2, .Lb84_zero
                li   t2, 5
                bgt  t1, t2, .Lb84_zero
                li   s3, 10
                j    .Lb84_render
            .Lb84_amex:
                li   t2, 4
                beq  t1, t2, .Lb84_set4
                li   t2, 7
                bne  t1, t2, .Lb84_zero
                j    .Lb84_set4
            .Lb84_disc:
                li   t2, 5
                beq  t1, t2, .Lb84_set8
                li   t2, 0
                bne  t1, t2, .Lb84_zero
                lbu  t2, 2(t5)           # d2
                li   t3, 1
                bne  t2, t3, .Lb84_zero
                lbu  t2, 3(t5)           # d3
                bne  t2, t3, .Lb84_zero
                j    .Lb84_set8
            .Lb84_set4:
                li   s3, 4
                j    .Lb84_render
            .Lb84_set8:
                li   s3, 8
                j    .Lb84_render
            .Lb84_zero:
                li   s3, 0
            .Lb84_render:                # s3 = len (0/4/8/10)
                addi a0, s3, 25
                call kof_alloc
                mv   s4, a0              # obj (padrao B83: callee-saved)
                li   t0, 1
                sw   t0, 0(s4)
                sw   x0, 4(s4)
                sd   x0, 8(s4)
                sw   s3, 16(s4)
                sw   x0, 20(s4)
                sb   x0, 24(s4)
                li   t1, 4
                beq  s3, t1, .Lb84_w4
                li   t1, 8
                beq  s3, t1, .Lb84_w8
                li   t1, 10
                beq  s3, t1, .Lb84_w10
                j    .Lb84_ret
            .Lb84_w4:                    # Visa(4)/Amex — d0 decide
                lbu  t0, 0(sp)           # buf via sp (t5 e clobbered pelo alloc)
                li   t1, 4
                beq  t0, t1, .Lb84_visa
                li   t0, 0x78656D41      # 'A','m','e','x'
                sw   t0, 24(s4)
                sb   x0, 28(s4)
                j    .Lb84_ret
            .Lb84_visa:
                li   t0, 0x61736956      # 'V','i','s','a'
                sw   t0, 24(s4)
                sb   x0, 28(s4)
                j    .Lb84_ret
            .Lb84_w8:                    # "Discover" — pares LE: Di|sc|ov|er
                li   t0, 0x6944          # 'D','i'  (bytes 0,1)
                li   t1, 0x6373          # 's','c'  (bytes 2,3)
                slli t1, t1, 16
                or   t0, t0, t1
                li   t1, 0x766F          # 'o','v'  (bytes 4,5)
                slli t1, t1, 32
                or   t0, t0, t1
                li   t1, 0x7265          # 'e','r'  (bytes 6,7)
                slli t1, t1, 48
                or   t0, t0, t1
                sd   t0, 24(s4)
                sb   x0, 32(s4)
                j    .Lb84_ret
            .Lb84_w10:                   # "Mastercard" — Ma|st|er|ca + rd
                li   t0, 0x614D          # 'M','a'  (bytes 0,1)
                li   t1, 0x7473          # 's','t'  (bytes 2,3)
                slli t1, t1, 16
                or   t0, t0, t1
                li   t1, 0x7265          # 'e','r'  (bytes 4,5)
                slli t1, t1, 32
                or   t0, t0, t1
                li   t1, 0x6163          # 'c','a'  (bytes 6,7)
                slli t1, t1, 48
                or   t0, t0, t1
                sd   t0, 24(s4)
                li   t0, 0x6472          # 'r','d'  (bytes 8,9)
                sh   t0, 32(s4)
                sb   x0, 34(s4)
                j    .Lb84_ret
            .Lb84_ret:
                mv   a0, s4
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret

            # kof_validation_last4(a0=str) -> a0 String
            .globl kof_validation_last4
            kof_validation_last4:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                mv   s0, a0              # str
                mv   t5, sp              # buf[19]
                li   s1, 0               # n (cap 19, janela rolante)
                li   s2, 0               # i
                li   s3, 0               # estouro
                beqz s0, .Lb84_l_empty
                lw   s4, 16(s0)          # len entrada
            .Lb84_l_collect:
                bge  s2, s4, .Lb84_l_nchk
                add  t0, s0, s2
                lbu  t0, 24(t0)
                addi s2, s2, 1
                li   t1, 48
                blt  t0, t1, .Lb84_l_collect
                li   t1, 57
                bgt  t0, t1, .Lb84_l_collect
                li   t1, 19
                blt  s1, t1, .Lb84_l_store
                li   s3, 1               # janela cheia: desloca 1, reusa +18
                li   t2, 0
            .Lb84_l_shift:
                li   t1, 18
                bge  t2, t1, .Lb84_l_shiftend
                add  t3, t5, t2
                lbu  t4, 1(t3)
                sb   t4, 0(t3)
                addi t2, t2, 1
                j    .Lb84_l_shift
            .Lb84_l_shiftend:
                addi t0, t0, -48         # t0 ainda = digito cru do collect
                sb   t0, 18(t5)
                j    .Lb84_l_collect
            .Lb84_l_store:
                addi t0, t0, -48
                add  t1, t5, s1
                sb   t0, 0(t1)
                addi s1, s1, 1
                j    .Lb84_l_collect
            .Lb84_l_nchk:
                bnez s3, .Lb84_l_empty
                li   t1, 4
                blt  s1, t1, .Lb84_l_empty
                li   a0, 29
                call kof_alloc
                mv   s3, a0              # obj em s3 (estouro nao e mais vivo)
                li   t0, 1
                sw   t0, 0(s3)
                sw   x0, 4(s3)
                sd   x0, 8(s3)
                li   t0, 4
                sw   t0, 16(s3)
                sw   x0, 20(s3)
                addi t1, s1, -4          # j0 = n-4
                add  t1, sp, t1          # ptr buf+j0 via sp (t5 morre no alloc)
                lbu  t2, 0(t1)
                addi t2, t2, 48
                sb   t2, 24(s3)
                lbu  t2, 1(t1)
                addi t2, t2, 48
                sb   t2, 25(s3)
                lbu  t2, 2(t1)
                addi t2, t2, 48
                sb   t2, 26(s3)
                lbu  t2, 3(t1)
                addi t2, t2, 48
                sb   t2, 27(s3)
                sb   x0, 28(s3)
                j    .Lb84_l_ret
            .Lb84_l_empty:
                li   a0, 25
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   x0, 4(s3)
                sd   x0, 8(s3)
                sw   x0, 16(s3)
                sw   x0, 20(s3)
                sb   x0, 24(s3)
            .Lb84_l_ret:
                mv   a0, s3
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;
}

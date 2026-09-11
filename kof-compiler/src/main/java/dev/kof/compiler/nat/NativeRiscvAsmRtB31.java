package dev.kof.compiler.nat;

// bug 82 (face cross): kof_string_to_double/float riscv64 no contrato do JDK
// (paridade x86 RuntimeStringParseFp). Algoritmo espelho do x86: trim (<=32
// nas pontas), +/-, digito-a-digito (mantissa int64 <=19), um '.', expoente
// e/E[+-]?digitos, literais NaN/Infinity (case-sensitive, idem JDK), falha =
// kof_string_from_literal + kof_throw_string (R6: nunca 0 silencioso).
// Divisao por 10^nfrac = UMA operacao (rounding unico; "0.3"==0.3; table
// .quad 20 entradas em hex — riscv asm nao suporta diretiva .double).
// Convencao de retorno cross: Double = bits raw em a0; Float = low32 bits
// (verificado no gerador de feq.d do backend riscv 10/09). Operacoes FP:
// fcvt.d.l/fmul.d/fdiv.d/fmv.d.x/fmv.x.d/fcvt.s.d/fmv.x.w — tradutor aarch
// tinha fcvt.d.l FALTANDO e fcvt.s.d/fcvt.d.s TROCADOS (dst/src invertidos
// — corrigido junto, 10/09, medido: todo F2D/D2F no aarch estava corrompido).
// LIMITE identico ao x86 (§82): >19 digitos LANCA; hex-float NAO parseia.
public final class NativeRiscvAsmRtB31 {

    private NativeRiscvAsmRtB31() {}

    static final String RISCV_RUNTIME_ASM_B_31 = """
            .section .text

            # kof_string_to_double(str) -> Double (raw bits em a0; contrato
            # Double.parseDouble(s.trim()); bug 82)
            .globl kof_string_to_double
            kof_string_to_double:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call .Lpd
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_string_to_float(str) -> Float (low32 bits em a0; cvt do double)
            .globl kof_string_to_float
            kof_string_to_float:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call .Lpd
                fmv.d.x f0, a0
                fcvt.s.d f0, f0
                fmv.x.w a0, f0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .Lpd:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s0, 96(sp)
                sd   s1, 88(sp)
                sd   s2, 80(sp)
                sd   s3, 72(sp)
                sd   s4, 64(sp)
                sd   s5, 56(sp)
                sd   s6, 48(sp)
                sd   s7, 40(sp)
                sd   s8, 32(sp)
                sd   s9, 24(sp)
                sd   s10, 16(sp)
                sd   s11, 8(sp)
                li   s11, 0
                beqz a0, .Lpd_throw
                mv   s0, a0
                lw   s1, 16(s0)
                mv   s3, s1
                li   s2, 0
            .Lpd_tl:
                bge  s2, s3, .Lpd_vazio
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 32
                bgt  t0, t1, .Lpd_th0
                addi s2, s2, 1
                j    .Lpd_tl
            .Lpd_th0:
                ble  s3, s2, .Lpd_vazio
                addi t0, s3, -1
                addi t0, t0, 24
                add  t0, s0, t0
                lbu  t0, 0(t0)
                li   t1, 32
                bgt  t0, t1, .Lpd_lit
                addi s3, s3, -1
                j    .Lpd_th0
            .Lpd_vazio:
                li   a0, 0
                j    .Lpd_ret
            .Lpd_lit:
                # NaN (len 3) / [+/-]Infinity (len 8/9/10), idem x86/JDK
                sub  t1, s3, s2
                li   t0, 3
                bne  t1, t0, .Lpd_lit_inf
                addi t2, s0, 24
                add  t2, t2, s2
                lbu  t1, 0(t2)
                li   t0, 78
                bne  t1, t0, .Lpd_num
                lbu  t1, 1(t2)
                li   t0, 97
                bne  t1, t0, .Lpd_num
                lbu  t1, 2(t2)
                li   t0, 78
                bne  t1, t0, .Lpd_num
                la   a0, .Lpdd_nan
                ld   a0, 0(a0)
                j    .Lpd_ret
            .Lpd_lit_inf:
                sub  t1, s3, s2
                li   t0, 8
                beq  t1, t0, .Lpd_inf_chk0
                li   t0, 9
                beq  t1, t0, .Lpd_inf_s1
                li   t0, 10
                bne  t1, t0, .Lpd_num
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 43
                bne  t0, t1, .Lpd_throw
                addi s2, s2, 1
                j    .Lpd_inf_chk0
            .Lpd_inf_s1:
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 45
                bne  t0, t1, .Lpd_throw
                li   s11, 1
                addi s2, s2, 1
            .Lpd_inf_chk0:
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t1, 0(t0)
                li   t2, 73
                bne  t1, t2, .Lpd_throw
                lbu  t1, 1(t0)
                li   t2, 110
                bne  t1, t2, .Lpd_throw
                lbu  t1, 2(t0)
                li   t2, 102
                bne  t1, t2, .Lpd_throw
                lbu  t1, 3(t0)
                li   t2, 105
                bne  t1, t2, .Lpd_throw
                lbu  t1, 4(t0)
                li   t2, 110
                bne  t1, t2, .Lpd_throw
                lbu  t1, 5(t0)
                li   t2, 105
                bne  t1, t2, .Lpd_throw
                lbu  t1, 6(t0)
                li   t2, 116
                bne  t1, t2, .Lpd_throw
                lbu  t1, 7(t0)
                li   t2, 121
                bne  t1, t2, .Lpd_throw
                bnez s11, .Lpd_ninf_ld
                la   a0, .Lpdd_inf
                ld   a0, 0(a0)
                j    .Lpd_ret
            .Lpd_ninf_ld:
                la   a0, .Lpdd_ninf
                ld   a0, 0(a0)
                j    .Lpd_ret
            .Lpd_num:
                li   s4, 0
                li   s6, 0
                li   s9, 0
                li   s7, 0
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 45
                bne  t0, t1, .Lpd_nots
                li   t1, 1
                or   s7, s7, t1
                addi s2, s2, 1
                j    .Lpd_p0
            .Lpd_nots:
                li   t1, 43
                bne  t0, t1, .Lpd_p0
                addi s2, s2, 1
            .Lpd_p0:
            .Lpd_iloop:
                bge  s2, s3, .Lpd_dot
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                addi t0, t0, -48
                bltz t0, .Lpd_dot
                li   t1, 9
                bgt  t0, t1, .Lpd_dot
                li   t1, 19
                bge  s9, t1, .Lpd_throw
                li   t1, 10
                mul  s4, s4, t1
                add  s4, s4, t0
                addi s9, s9, 1
                addi s2, s2, 1
                j    .Lpd_iloop
            .Lpd_dot:
                bge  s2, s3, .Lpd_end
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 46
                bne  t0, t1, .Lpd_exp
                li   t1, 2
                or   s7, s7, t1
                addi s2, s2, 1
            .Lpd_floop:
                bge  s2, s3, .Lpd_exp
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                addi t0, t0, -48
                bltz t0, .Lpd_exp
                li   t1, 9
                bgt  t0, t1, .Lpd_exp
                li   t1, 19
                bge  s9, t1, .Lpd_throw
                li   t1, 10
                mul  s4, s4, t1
                add  s4, s4, t0
                addi s9, s9, 1
                addi s6, s6, 1
                addi s2, s2, 1
                j    .Lpd_floop
            .Lpd_exp:
                andi t0, s7, 2
                beqz t0, .Lpd_exp_e
                bnez s6, .Lpd_exp_e
                # dot sem frac: "5." ok (pula exp); ".x" sem digitos -> throw;
                # "5.e3" tambem pula exp (idem x86/JDK)
                beqz s9, .Lpd_throw
                j    .Lpd_end
            .Lpd_exp_e:
                beqz s9, .Lpd_throw
                j    .Lpd_end
            .Lpd_end:
                bge  s2, s3, .Lpd_build
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 101
                beq  t0, t1, .Lpd_expi
                li   t1, 69
                bne  t0, t1, .Lpd_throw
            .Lpd_expi:
                addi s2, s2, 1
                li   s8, 0
                bge  s2, s3, .Lpd_throw
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                li   t1, 45
                bne  t0, t1, .Lpd_expp
                li   s8, 1
                addi s2, s2, 1
                j    .Lpd_expc
            .Lpd_expp:
                li   t1, 43
                bne  t0, t1, .Lpd_expc
                addi s2, s2, 1
            .Lpd_expc:
                li   s5, 0
                li   s10, 0
            .Lpd_eloop:
                bge  s2, s3, .Lpd_edone
                addi t0, s0, 24
                add  t0, t0, s2
                lbu  t0, 0(t0)
                addi t0, t0, -48
                bltz t0, .Lpd_edone
                li   t1, 9
                bgt  t0, t1, .Lpd_edone
                li   s10, 1
                li   t1, 1000000
                bge  s5, t1, .Lpd_ebig
                li   t1, 10
                mul  s5, s5, t1
                add  s5, s5, t0
                addi s2, s2, 1
                j    .Lpd_eloop
            .Lpd_ebig:
                addi s2, s2, 1
                j    .Lpd_eloop
            .Lpd_edone:
                beqz s10, .Lpd_throw
                bge  s2, s3, .Lpd_build
                j    .Lpd_throw
            .Lpd_build:
                li   t1, 320
                bge  s5, t1, .Lpd_hugeexp
                fcvt.d.l f0, s4
                beqz s6, .Lpd_expapply
                slli t0, s6, 3
                la   t1, .Lpdd_p10
                add  t1, t1, t0
                ld   t1, 0(t1)
                fmv.d.x f1, t1
                fdiv.d f0, f0, f1
            .Lpd_expapply:
                beqz s5, .Lpd_sign
                la   t1, .Lpdd_ten
                ld   t1, 0(t1)
                fmv.d.x f1, t1
            .Lpd_emul:
                beqz s8, .Lpd_mul
                fdiv.d f0, f0, f1
                j    .Lpd_enext
            .Lpd_mul:
                fmul.d f0, f0, f1
            .Lpd_enext:
                addi s5, s5, -1
                bnez s5, .Lpd_emul
                j    .Lpd_sign
            .Lpd_sign:
                andi t0, s7, 1
                beqz t0, .Lpd_pack
                la   t1, .Lpdd_mone
                ld   t1, 0(t1)
                fmv.d.x f1, t1
                fmul.d f0, f0, f1
            .Lpd_pack:
                fmv.x.d a0, f0
                j    .Lpd_ret
            .Lpd_hugeexp:
                bnez s8, .Lpd_hzero
                la   a0, .Lpdd_inf
                ld   a0, 0(a0)
                andi t0, s7, 1
                beqz t0, .Lpd_ret
                la   a0, .Lpdd_ninf
                ld   a0, 0(a0)
                j    .Lpd_ret
            .Lpd_hzero:
                li   a0, 0
                andi t0, s7, 1
                beqz t0, .Lpd_ret
                la   a0, .Lpdd_nzero
                ld   a0, 0(a0)
                j    .Lpd_ret
            .Lpd_ret:
                ld   s0, 96(sp)
                ld   s1, 88(sp)
                ld   s2, 80(sp)
                ld   s3, 72(sp)
                ld   s4, 64(sp)
                ld   s5, 56(sp)
                ld   s6, 48(sp)
                ld   s7, 40(sp)
                ld   s8, 32(sp)
                ld   s9, 24(sp)
                ld   s10, 16(sp)
                ld   s11, 8(sp)
                ld   ra, 104(sp)
                addi sp, sp, 112
                ret
            .Lpd_throw:
                la   a0, .Lpdd_msg
                li   a1, 14
                call kof_string_from_literal
                call kof_throw_string

            .section .data
            .Lpdd_msg:  .asciz "Invalid number"
            .Lpdd_ten:  .quad 0x4024000000000000
            .Lpdd_mone: .quad 0xbff0000000000000
            .Lpdd_nan:  .quad 0x7ff8000000000000
            .Lpdd_inf:  .quad 0x7ff0000000000000
            .Lpdd_ninf: .quad 0xfff0000000000000
            .Lpdd_nzero:.quad 0x8000000000000000
            .Lpdd_p10:
                .quad 0x3ff0000000000000
                .quad 0x4024000000000000
                .quad 0x4059000000000000
                .quad 0x408f400000000000
                .quad 0x40c3880000000000
                .quad 0x40f86a0000000000
                .quad 0x412e848000000000
                .quad 0x416312d000000000
                .quad 0x4197d78400000000
                .quad 0x41cdcd6500000000
                .quad 0x4202a05f20000000
                .quad 0x42374876e8000000
                .quad 0x426d1a94a2000000
                .quad 0x42a2309ce5400000
                .quad 0x42d6bcc41e900000
                .quad 0x430c6bf526340000
                .quad 0x4341c37937e08000
                .quad 0x4376345785d8a000
                .quad 0x43abc16d674ec800
                .quad 0x43e158e460913d00
            .section .text
            """;
}

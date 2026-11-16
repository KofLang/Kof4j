package dev.kof.compiler.nat;

// FASE (STDLIB S1b/MATH001): fatia 32 de RISCV_RUNTIME_ASM_B — kof.math Double.
// Port do x86 RuntimeMath (S1b sqrt + S1b.1 lerp/percentage/isInteger/
// isDecimal) p/ riscv64; aarch64 herda via translateRiscvToAarch64 (fsqrt.d
// -> fsqrtd adicionado ao tradutor neste commit).
//
// Convenção: args em a0/a1/a2 (bits crus IEEE 64-bit — MESMO caminho genérico
// do NativeRiscvCrossOps.emitCrossCallRiscv, que popula a0..aN e push(a0) o
// retorno); retorno = bits crus em a0 (o caller faz pushRiscv a0). FP scratch
// f0..f2; int scratch t0..t3 (caller-saved — safe, sem frame). Sem .rodata de
// texto (percentage reusa o padrão B27: .quad em .rodata + la/fld).
//
// Paridade byte-level com o x86 (RuntimeMath) e o JVM (JvmStringMathRuntime):
//   sqrt(v)        = IEEE sqrt (NaN p/ v<0 — Math.sqrt paridade)
//   lerp(a,b,t)    = a + (b-a)*t  (mesma ordem de sub/mul/add)
//   percentage(p,t)= p/t*100.0    (div-then-mul; 100.0 = .Lmth_pct100)
//   isInteger(v)   = exp==0x7ff(NaN/Inf)->false; exp>=0x433(|v|>=2^52 finito)
//                    ->true; senão feq(v, trunc32(v))
//   isDecimal(v)   = !isInteger(v)
public final class NativeRiscvAsmRtB32 {

    static  String RISCV_RUNTIME_ASM_B_32 = """

            .section .text

            # ── kof.math Double (STDLIB S1b + S1b.1 — MATH001) ─────────

            # kof_math_sqrt(a0=v) -> f0=bits, a0=bits
            .globl kof_math_sqrt
            kof_math_sqrt:
                fmv.d.x f0, a0
                fsqrt.d f0, f0
                fmv.x.d a0, f0
                ret

            # kof_math_lerp(a0=a, a1=b, a2=t) -> a0=bits de a+(b-a)*t
            .globl kof_math_lerp
            kof_math_lerp:
                fmv.d.x f0, a0             # a
                fmv.d.x f1, a1             # b
                fmv.d.x f2, a2             # t
                fsub.d f1, f1, f0          # b-a
                fmul.d f1, f1, f2          # (b-a)*t
                fadd.d f0, f0, f1          # a+...
                fmv.x.d a0, f0
                ret

            # kof_math_percentage(a0=part, a1=total) -> a0=bits de part/total*100
            .globl kof_math_percentage
            kof_math_percentage:
                fmv.d.x f0, a0             # part
                fmv.d.x f1, a1             # total
                fdiv.d f0, f0, f1          # part/total
                la   t0, .Lmth_pct100
                fld  f1, 0(t0)
                fmul.d f0, f0, f1          # *100.0
                fmv.x.d a0, f0
                ret

            # kof_math_isInteger(a0=v) -> a0=1/0
            # exp bits [63:52]: 0x7ff = NaN/Inf -> 0; >=0x433 (|v|>=2^52,
            # finito) -> 1; senão trunc p/ int64 e de volta a double, feq.
            # (FCVT.L.D 64-bit — igual o cvttsd2si %rdx do x86; FCVT.W.D
            # saturaria em 2^31 e quebraria inteiros como 1e10.)
            .globl kof_math_isInteger
            kof_math_isInteger:
                mv   t0, a0
                srli t1, t0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_ii_false
                li   t2, 0x433
                bgeu t1, t2, .Lmth_ii_true
                fmv.d.x f0, t0
                fcvt.l.d t0, f0, rtz
                fcvt.d.l f1, t0
                feq.d t0, f0, f1
                beqz t0, .Lmth_ii_false
            .Lmth_ii_true:
                li   a0, 1
                ret
            .Lmth_ii_false:
                li   a0, 0
                ret

            # kof_math_isDecimal(a0=v) -> a0=1/0 (negação do isInteger —
            # seqz p/ 0/1; xori nao existe no tradutor aarch).
            # LIÇÃO isWeekend/B14: wrapper que faz `call` DEVE salvar ra
            # (jalr do call sobrescreve ra — o ret do wrapper voltaria ao
            # próprio corpo = loop infinito; provado no trace qemu 11/09).
            .globl kof_math_isDecimal
            kof_math_isDecimal:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_math_isInteger
                ld   ra, 8(sp)
                addi sp, sp, 16
                seqz a0, a0
                ret

            # S1b.3 (DECISIONS §3): kof_math_roundTo(a0=v bits, a1=decimals)
            # -> a0=bits. Half-away-from-zero por escala decimal determinística
            # (sem libm). p = 10^m (m=|d|, satura 308) por fmul.d REPETIDO —
            # byte-idêntico ao x86/JVM/JS. d>=0: scaled=v*p, r/p. d<0:
            # scaled=v/p, r*p. Overflow de v*p -> devolve v. Inline (sem
            # wrapper/call → não precisa salvar ra).
            .globl kof_math_roundTo
            kof_math_roundTo:
                sext.w a1, a1                    # normaliza decimals p/ 64-bit
                # v NaN/Inf (exp==0x7ff) -> devolve v
                srli t0, a0, 52
                andi t0, t0, 0x7ff
                li   t1, 0x7ff
                beq  t0, t1, .Lmth_rt_ret
                # m = min(|d|, 308)
                mv   t2, a1
                bgez t2, .Lmth_rt_abs
                neg  t2, t2
            .Lmth_rt_abs:
                li   t1, 308
                ble  t2, t1, .Lmth_rt_m_ok
                li   t2, 308
            .Lmth_rt_m_ok:
                # p = 10^m
                la   t0, .Lmth_one
                fld  f1, 0(t0)
                la   t3, .Lmth_ten
                fld  f2, 0(t3)
                beqz t2, .Lmth_rt_p_done
            .Lmth_rt_p_loop:
                fmul.d f1, f1, f2
                addi t2, t2, -1
                bnez t2, .Lmth_rt_p_loop
            .Lmth_rt_p_done:
                fmv.d.x f0, a0
                bltz a1, .Lmth_rt_negd
                fmul.d f0, f0, f1                # scaled = v*p
                j    .Lmth_rt_scale
            .Lmth_rt_negd:
                fdiv.d f0, f0, f1                # scaled = v/p
            .Lmth_rt_scale:
                fmv.x.d t0, f0
                srli t1, t0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_rt_ret        # overflow -> v
                li   t2, 0x433
                bgeu t1, t2, .Lmth_rt_apply      # |scaled|>=2^52 -> r=scaled
                # r = half-away-from-zero(scaled)
                fcvt.l.d t3, f0, rtz             # t = trunc
                fcvt.d.l f3, t3                  # (double)t
                fsub.d f4, f0, f3                # frac = scaled - t
                la   t0, .Lmth_half
                fld  f5, 0(t0)
                flt.d t1, f4, f5                 # frac < 0.5 ?
                beqz t1, .Lmth_rt_up             # !(frac<0.5) -> frac>=0.5
                la   t0, .Lmth_nhalf
                fld  f5, 0(t0)
                flt.d t1, f5, f4                 # -0.5 < frac ?
                bnez t1, .Lmth_rt_r_done         # frac in (-0.5,0.5) -> r=t
                addi t3, t3, -1                  # frac <= -0.5 -> t-1
                j    .Lmth_rt_r_done
            .Lmth_rt_up:
                addi t3, t3, 1
            .Lmth_rt_r_done:
                fcvt.d.l f0, t3
            .Lmth_rt_apply:
                bltz a1, .Lmth_rt_mulback
                fdiv.d f0, f0, f1                # r/p
                j    .Lmth_rt_fin
            .Lmth_rt_mulback:
                fmul.d f0, f0, f1                # r*p
            .Lmth_rt_fin:
                fmv.x.d a0, f0
                ret
            .Lmth_rt_ret:
                ret

            .section .rodata
            .align 3
            .Lmth_pct100:
                .quad 0x4059000000000000
            .Lmth_one:
                .quad 0x3ff0000000000000
            .Lmth_ten:
                .quad 0x4024000000000000
            .Lmth_half:
                .quad 0x3fe0000000000000
            .Lmth_nhalf:
                .quad 0xbfe0000000000000
            # ── MATH-D (16/11, #1169): as sete double ────────────────────
            # Cadeias branchy EXATAS do JDK (Math.min/max fontes: NaN → a;
            # o caso ±0 por bits crus; o comparador final ≤/≥) — byte-idêntico.
            # .Lmth_dn1 = -1.0 (o floor do subnormal negativo); .Lmth_dzero =
            # -0.0 (o ceil do negativo não-inteiro).

            # kof_math_dabs(a0=bits) -> a0=|v| (limpa o bit de sinal; a máscara
            # carregada de .rodata — o and do tradutor é reg-reg)
            .globl kof_math_dabs
            kof_math_dabs:
                la   t0, .Lmth_dabsmask
                ld   t1, 0(t0)
                and  a0, a0, t1
                ret

            # kof_math_floor(a0=bits) -> a0=floor(v)
            # dispatch: exp=0x7ff → v (NaN/Inf); v=±0 → v; |v|>=2^52 → v (já
            # integral); -subnormal → -1; senão t=trunc(v); v<0 && v!=t → t-1
            .globl kof_math_floor
            kof_math_floor:
                srli t1, a0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_fl_id    # NaN/Inf → v
                li   t2, 0x433
                bgeu t1, t2, .Lmth_fl_id    # |v|>=2^52 → v (já integral)
                slli t2, a0, 1
                beqz t2, .Lmth_fl_id        # ±0 exato → v
                # FRACIONÁRIO (os subnormais inclusos — |v|<1 não-integral):
                fmv.d.x f0, a0
                fcvt.l.d t2, f0, rtz        # t2 = trunc(v)
                fcvt.d.l f1, t2
                feq.d t3, f0, f1            # v == trunc?
                beqz t3, .Lmth_fl_frac
                fmv.x.d a0, f1              # não deveria ocorrer aqui (o
                ret                         # dispatch já garante |v|<1... o
                                            # frac cai no braço abaixo)
            .Lmth_fl_frac:
                la   t0, .Lmth_dzero
                fld  f2, 0(t0)
                flt.d t3, f0, f2            # v < 0? (f0 = v INTACTO)
                beqz t3, .Lmth_fl_trunc     # v >= 0 → floor = trunc
                addi t2, t2, -1             # v < 0 fracionário → trunc - 1
                fcvt.d.l f1, t2
                fmv.x.d a0, f1
                ret
            .Lmth_fl_trunc:
                fmv.x.d a0, f1
                ret
            .Lmth_fl_id:
                ret

            # kof_math_ceil(a0=bits) -> a0=ceil(v) (o negativo fracionário
            # pequeno → -0.0; o mesmo dispatch do floor com t+1)
            .globl kof_math_ceil
            kof_math_ceil:
                srli t1, a0, 52
                andi t1, t1, 0x7ff
                li   t2, 0x7ff
                beq  t1, t2, .Lmth_ce_id    # NaN/Inf → v
                li   t2, 0x433
                bgeu t1, t2, .Lmth_ce_id    # |v|>=2^52 → v
                slli t2, a0, 1
                beqz t2, .Lmth_ce_id        # ±0 exato → v
                fmv.d.x f0, a0
                fcvt.l.d t2, f0, rtz        # t2 = trunc(v)
                fcvt.d.l f1, t2
                feq.d t3, f0, f1
                beqz t3, .Lmth_ce_frac
                fmv.x.d a0, f1              # integral (não deveria ocorrer)
                ret
            .Lmth_ce_frac:
                # v negativo não-integral: -1 < v < 0 → -0.0; v <= -1 → trunc
                # (ceil(-2.1) = -2.0 = trunc; ceil(-0.3) = -0.0)
                srli t3, a0, 63             # o sinal de v decide o braço
                beqz t3, .Lmth_ce_pos       # v >= 0 → trunc + 1
                li   t3, 0x3ff              # |v| < 1 ⟺ exp < 0x3ff
                bgeu t1, t3, .Lmth_ce_trunc
                la   t0, .Lmth_dzero
                fld  f2, 0(t0)
                fmv.x.d a0, f2              # -0.0 (|v|<1)
                ret
            .Lmth_ce_pos:
                j    .Lmth_ce_up
            .Lmth_ce_trunc:
                fmv.x.d a0, f1              # trunc(v)
                ret
            .Lmth_ce_up:
                addi t2, t2, 1              # trunc + 1 (INTEIRO)
                fcvt.d.l f1, t2
                fmv.x.d a0, f1
                ret
            .Lmth_ce_id:
                ret

            # kof_math_dmin(a0=a, a1=b) -> a0=Math.min(a,b) EXATO (JDK):
            # a NaN → a; a±0 && b±0 && b==-0 → a; a<=b ? a : b
            .globl kof_math_dmin
            kof_math_dmin:
                # a NaN? feq.d(v,v)==0
                fmv.d.x f0, a0
                feq.d t0, f0, f0
                beqz t0, .Lmth_dmin_a
                # b NaN? → a (o min devolve a? NÃO: JDK min devolve a quando
                # a NaN; b NaN segue para o comparador: a<=b → a ✓)
                fmv.d.x f1, a1
                feq.d t0, f1, f1
                beqz t0, .Lmth_dmin_cmp
                # ambos ±0?
                slli t1, a0, 1
                bnez t1, .Lmth_dmin_cmp
                slli t1, a1, 1
                bnez t1, .Lmth_dmin_cmp
                # b == -0? (o bit de sinal de a1)
                srli t1, a1, 63
                beqz t1, .Lmth_dmin_cmp
                mv   a0, a0                 # b -0 → devolve a (o JDK)
                ret
            .Lmth_dmin_a:
                mv   a0, a0
                ret
            .Lmth_dmin_cmp:
                fle.d t0, f0, f1            # a <= b → a
                bnez t0, .Lmth_dmin_a
                mv   a0, a1
                ret

            # kof_math_dmax(a0=a, a1=b) -> a0=Math.max(a,b) EXATO (JDK):
            # a NaN → a; a±0 && b±0 && a==-0 → b; a>=b ? a : b
            .globl kof_math_dmax
            kof_math_dmax:
                fmv.d.x f0, a0
                feq.d t0, f0, f0
                beqz t0, .Lmth_dmax_a
                fmv.d.x f1, a1
                feq.d t0, f1, f1
                beqz t0, .Lmth_dmax_cmp
                slli t1, a0, 1
                bnez t1, .Lmth_dmax_cmp
                slli t1, a1, 1
                bnez t1, .Lmth_dmax_cmp
                srli t1, a0, 63
                beqz t1, .Lmth_dmax_cmp
                mv   a0, a1                 # a -0 → devolve b (o JDK)
                ret
            .Lmth_dmax_a:
                mv   a0, a0
                ret
            .Lmth_dmax_cmp:
                fge.d t0, f0, f1            # a >= b → a
                bnez t0, .Lmth_dmax_a
                mv   a0, a1
                ret

            # kof_math_dclamp(a0=v, a1=lo, a2=hi) -> a0 = v<lo ? lo : (v>hi ? hi : v)
            # (NaN em v: as duas comparações false → v propaga; NaN em lo/hi:
            # a comparação unordered é false → a cadeia segue — o JDK igual)
            .globl kof_math_dclamp
            kof_math_dclamp:
                fmv.d.x f0, a0
                fmv.d.x f1, a1
                fmv.d.x f2, a2
                feq.d t0, f0, f0
                beqz t0, .Lmth_dc_v         # v NaN → v
                flt.d t0, f0, f1            # v < lo?
                bnez t0, .Lmth_dc_lo
                fgt.d t0, f0, f2            # v > hi?
                bnez t0, .Lmth_dc_hi
            .Lmth_dc_v:
                mv   a0, a0
                ret
            .Lmth_dc_lo:
                mv   a0, a1
                ret
            .Lmth_dc_hi:
                mv   a0, a2
                ret

            # kof_math_dsign(a0=bits) -> a0 = Int (NaN→0; v<0→-1; v>0→1; ±0→0)
            .globl kof_math_dsign
            kof_math_dsign:
                fmv.d.x f0, a0
                feq.d t0, f0, f0
                beqz t0, .Lmth_dsg_z        # NaN → 0
                la   t1, .Lmth_dzero
                fld  f1, 0(t1)
                flt.d t0, f1, f0            # 0 < v → 1
                bnez t0, .Lmth_dsg_p
                flt.d t0, f0, f1            # v < 0 → -1
                bnez t0, .Lmth_dsg_n
            .Lmth_dsg_z:
                li   a0, 0
                ret
            .Lmth_dsg_p:
                li   a0, 1
                ret
            .Lmth_dsg_n:
                li   a0, -1
                ret

            .section .rodata
            .Lmth_dabsmask:
                .quad 0x7fffffffffffffff
            .Lmth_dn1:
                .quad 0xbff0000000000000
            .Lmth_dzero:
                .quad 0x8000000000000000
            .Lmth_done1:
                .quad 0x3ff0000000000000
            .section .text
        """;
}

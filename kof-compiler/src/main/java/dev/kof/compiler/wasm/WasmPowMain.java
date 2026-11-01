package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-POW-1 (01/11, #776): o __ieee754_pow fdlibm (o {@code FdLibm.Pow}
 * do PRÓPRIO JDK — os literais hex verbatim do src.zip) como funcs WASM
 * mão-emitida — a ÚLTIMA fatia libm da família math (sin/tan/atan/asin/
 * acos/atan2 27-28/10). Este arquivo = o CAMINHO PRINCIPAL: o log2(x)
 * polinomial (o split BP/DP + a cadeia L1..L6 + o z_h/z_l), a CAUDA
 * comum (o split y·log2x + os gates de overflow/underflow OVT + a
 * cadeia P1..P5/LG2 + a construção 2^k por bits) e o scalb EXATO do
 * output subnormal. O driver/casos especiais fica em WasmPowKernel.
 *
 * <p>AS LEIS DA EMISSÃO (as mesmas do atan2): os params ocupam os
 * índices baixos (a decl dos locals = SÓ os não-param, os índices
 * continuam); os counts de shift I32 para o i32 e I64 para o i64 (o
 * wasm 3.0); o minuendo do SUB empilhado primeiro; os value-ifs
 * (0x7c) produzem o resultado; o NaN é x≠x (f64.ne); os bits de
 * sinal via reinterpret/shr_s/wrap (o −0.0 tem bit, não compare).
 * OS BITS DE JAVA INT = a semântica do wasm i32 (o wrap do count
 * mod 32, o wrap aritmético do add/sub) — o j−0x40900000 dos gates e
 * o n<<20 da construção 2^k dependem DELE e casam.
 *
 * <p>O ORÁCULO: o port fixa os bits do fdlibm == StrictMath.pow (o
 * diferencial da fundação); Math.pow DERIVA 1 ulp em ~3% das faces
 * transcedentais (medido 24/810 nesta fundação) — as faces E2E = o
 * subset MEDIDO-IGUAL (a MESMA classe da lei de drift do sin
 * LIBM-4-B; o veredito do cos NÃO se estende ao pow — o pow tem
 * casamento fdlibm==StrictMath pela própria construção).
 */
final class WasmPowMain {

    private WasmPowMain() {}

    // ---- as constantes verbatim do FdLibm.Pow (o src.zip do JDK) ----

    // o caminho |y| > 2^31 (o log perto de 1)
    private static final double INV_LN2   = 0x1.7154_7652_b82fep0;
    private static final double INV_LN2_H = 0x1.715476p0;
    private static final double INV_LN2_L = 0x1.4ae0_bf85_ddf44p-26;

    // o caminho principal (o split sqrt(3/2) / sqrt(3))
    private static final double CP   = 0x1.ec70_9dc3_a03fdp-1;
    private static final double CP_H = 0x1.ec709ep-1;
    private static final double CP_L = -0x1.e2fe_0145_b01f5p-28;
    private static final double DP_H_1 = 0x1.2b80_34p-1;
    private static final double DP_L_1 = 0x1.cfde_b43c_fd006p-27;
    private static final double L1 = 0x1.3333_3333_33303p-1;
    private static final double L2 = 0x1.b6db_6db6_fabffp-2;
    private static final double L3 = 0x1.5555_5518_f264dp-2;
    private static final double L4 = 0x1.1746_0a91_d4101p-2;
    private static final double L5 = 0x1.d864_a93c_9db65p-3;
    private static final double L6 = 0x1.a7e2_84a4_54eefp-3;

    // a cauda (o exp2 polinomial)
    private static final double P1 = 0x1.5555_5555_5553ep-3;
    private static final double P2 = -0x1.6c16_c16b_ebd93p-9;
    private static final double P3 = 0x1.1566_aaf2_5de2cp-14;
    private static final double P4 = -0x1.bbd4_1c5d_26bf1p-20;
    private static final double P5 = 0x1.6376_972b_ea4d0p-25;
    private static final double LG2   = 0x1.62e4_2fef_a39efp-1;
    private static final double LG2_H = 0x1.62e43p-1;
    private static final double LG2_L = -0x1.05c6_10ca_86c39p-29;
    // -(1024-log2(ovfl+.5ulp)) — o gate do overflow no z == 1024 exato
    private static final double OVT = 8.0085662595372944372e-17;

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    // ---- os 4 helpers de bits (o __HI/__LO do fdlibm) ----

    /** kof_pow_hi(x) -> i32: a palavra ALTA (o __HI(x) — o sinal
     *  incluído; o shr_s 32 + o wrap reproduzem o (int)(bits >> 32)). */
    static WasmFunc hiFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        return new WasmFunc("kof_pow_hi", List.of(0x7c), List.of(0x7f), List.of(), b);
    }

    /** kof_pow_lo(x) -> i32: a palavra BAIXA (o __LO(x) = o (int)bits). */
    static WasmFunc loFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        return new WasmFunc("kof_pow_lo", List.of(0x7c), List.of(0x7f), List.of(), b);
    }

    /** kof_pow_withhi(x, hi) -> f64: o __HI(x, hi) — a palavra alta
     *  SUBSTITUÍDA: o bits(x) E 0xFFFFFFFF preserva SÓ a palavra baixa
     *  (o OR sem a máscara deixa o expoente VELHO — o 0x3FF0|0x4000 =
     *  0x7FF0 = INFINITY, o bug dos 6 faces inteiros). */
    static WasmFunc withHiFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 0x00000000FFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_withhi", List.of(0x7c, 0x7f), List.of(0x7c), List.of(), b);
    }

    /** kof_pow_lo0(x) -> f64: o __LO(x, 0) — a palavra baixa ZERADA (a
     *  dança reinterpret/AND/reinterpret provada no acos). */
    static WasmFunc lo0Func() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 0xFFFFFFFF00000000L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_lo0", List.of(0x7c), List.of(0x7c), List.of(), b);
    }

    /** kof_pow_scalb(z, n) -> f64: o Math.scalb EXATO por bits para o
     *  output SUBNORMAL da cauda (o caller garante t = e+n ≤ 0 e z
     *  normal em [0.5, 2)): shift = 1-(e+n); o q = sig >> shift com o
     *  round-half-even; o carry do q = 2^52 É o bit-pattern do menor
     *  normal correto (o IEEE). O shift ≥ 54 → ±0 (o sinal preservado). */
    static WasmFunc scalbFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // params: 0=z (f64) 1=n (i64); locals: 2=bits 3=hi 5=frac 6=sig
        //         7=t (i64) 4=e 8=shift (i64 — os shifts de sig são i64)
        //         9=q 10=rem 11=half (i64)
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 2);
        get(b, 2);
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, 3);
        get(b, 3);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x7ff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 4);
        get(b, 2);
        b.add(new WasmInstr.Const(1, 0x000FFFFFFFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 5);
        get(b, 5);
        b.add(new WasmInstr.Const(1, 4503599627370496L));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        set(b, 6);
        // t = e + n
        get(b, 4);
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 7);
        // o caminho normal (t > 0): o campo de expoente substituído
        get(b, 7);
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(1, 0x800FFFFFFFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 7);
        b.add(new WasmInstr.Const(1, 52L));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // shift = 1 - t (≥ 1 no contrato, i64 — o q/rem o usam como shift;
        // o ORDEM é 1 − t: o t−1 dava shift NEGATIVO e o shr contava mod 64
        // (−52 mod 64 = 12 → q = 2^40 = o 2^−1034 dos probes))
        b.add(new WasmInstr.Const(1, 1L));
        get(b, 7);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 8);
        // shift ≥ 54 → ±0
        get(b, 8);
        b.add(new WasmInstr.Const(1, 54L));
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(1, Long.MIN_VALUE));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // q = sig >> shift (sig > 0); rem = sig - (q << shift)
        get(b, 6);
        get(b, 8);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        set(b, 9);
        get(b, 6);
        get(b, 9);
        get(b, 8);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 10);
        // half = 1 << (shift-1)
        b.add(new WasmInstr.Const(1, 1L));
        get(b, 8);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        set(b, 11);
        // round-half-even: rem > half || (rem == half && (q&1) == 1)
        get(b, 10);
        get(b, 11);
        b.add(new WasmInstr.Simple(0x5a, "i64.gt_s"));
        get(b, 10);
        get(b, 11);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        get(b, 9);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 9);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // out = sign | q (o carry do 2^52 = o menor normal correto)
        get(b, 2);
        b.add(new WasmInstr.Const(1, Long.MIN_VALUE));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 9);
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_scalb", List.of(0x7c, 0x7e), List.of(0x7c),
                List.of(0x7e, 0x7f, 0x7f, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e), b);
    }

    /** kof_pow_main(x, y, s) -> f64: o caminho principal — o log2(x)
     *  polinomial (t1/t2) e a CAUDA. Params: 0=x 1=y 2=s. Locals:
     *  3=x_abs 4=ix 5=n 6=j 7=k (i32) 8=bp 9=dp_h 10=dp_l 11=u 12=v
     *  13=ss 14=s_h 15=t_h 16=t_l 17=s_l 18=s2 19=r 20=p_h 21=p_l
     *  22=z_h 23=z_l 24=t 25=t1 26=t2 (f64). */
    static WasmFunc mainFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // x_abs = |x|; ix = HI(x_abs)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 3);
        get(b, 3);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        set(b, 4);
        // n = 0; o rescale do subnormal: x_abs *= 2^53; n -= 53; ix = HI
        b.add(new WasmInstr.Const(0, 0));
        set(b, 5);
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0x00100000));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(0x1.0p53));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 3);
        get(b, 5);
        b.add(new WasmInstr.Const(0, 53));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 5);
        get(b, 3);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        set(b, 4);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // n += (ix >> 20) - 0x3ff
        get(b, 5);
        get(b, 4);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x3ff));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 5);
        // j = ix & 0x000fffff; ix = j | 0x3ff00000
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0x000fffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 6);
        get(b, 6);
        b.add(new WasmInstr.Const(0, 0x3ff00000));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 4);
        // k: j <= 0x3988E → 0; j < 0xBB67A → 1; senão n += 1, ix -= 0x00100000
        b.add(new WasmInstr.Const(0, 0));
        set(b, 7);
        get(b, 6);
        b.add(new WasmInstr.Const(0, 0x3988E));
        b.add(new WasmInstr.Simple(0x4c, "i32.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 6);
        b.add(new WasmInstr.Const(0, 0xBB67A));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 1));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 5);
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0x00100000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 4);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // x_abs = __HI(x_abs, ix)
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Call("kof_pow_withhi"));
        set(b, 3);
        // bp/dp_h/dp_l pelo k (0/1)
        get(b, 7);
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(1.5));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        set(b, 8);
        get(b, 7);
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(DP_H_1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        set(b, 9);
        get(b, 7);
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(DP_L_1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        set(b, 10);
        // u = x_abs - bp; v = 1/(x_abs + bp); ss = u*v
        get(b, 3);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 11);
        b.add(new WasmInstr.Const(1.0));
        get(b, 3);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 12);
        get(b, 11);
        get(b, 12);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 13);
        // s_h = __LO(ss, 0)
        get(b, 13);
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 14);
        // t_h = __HI(0, ((ix>>1)|0x20000000) + 0x00080000 + (k<<18))
        b.add(new WasmInstr.Const(0.0));
        get(b, 4);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x20000000));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Const(0, 0x00080000));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 7);
        b.add(new WasmInstr.Const(0, 18));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Call("kof_pow_withhi"));
        set(b, 15);
        // t_l = x_abs - (t_h - bp)
        get(b, 3);
        get(b, 15);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 16);
        // s_l = v * ((u - s_h*t_h) - s_h*t_l)
        get(b, 12);
        get(b, 11);
        get(b, 14);
        get(b, 15);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 14);
        get(b, 16);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 17);
        // s2 = ss*ss; r = s2*s2*(L1 + s2*(L2 + s2*(L3 + s2*(L4 + s2*(L5 + s2*L6)))))
        get(b, 13);
        get(b, 13);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 18);
        get(b, 18);
        get(b, 18);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(L1));
        get(b, 18);
        b.add(new WasmInstr.Const(L2));
        get(b, 18);
        b.add(new WasmInstr.Const(L3));
        get(b, 18);
        b.add(new WasmInstr.Const(L4));
        get(b, 18);
        b.add(new WasmInstr.Const(L5));
        get(b, 18);
        b.add(new WasmInstr.Const(L6));
        // o fold de Horner: [s2², L1, s2, L2, …, s2, L6] → (mul, add) ×5
        // + o mul final do acumulador s2² — a alternância rígida (o
        // s2 pré-empilhado fica logo abaixo de cada add-parcial)
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2·L6
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // L5 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2·(L5+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // L4 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2·(L4+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // L3 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2·(L3+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // L2 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2·(L2+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // L1 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // s2²·(L1 + s2·(…)) = r
        set(b, 19);
        // r += s_l*(s_h + ss)
        get(b, 17);
        get(b, 14);
        get(b, 13);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 19);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 19);
        // s2 = s_h*s_h; t_h = __LO(3.0 + s2 + r, 0); t_l = r - ((t_h-3.0) - s2)
        get(b, 14);
        get(b, 14);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 18);
        b.add(new WasmInstr.Const(3.0));
        get(b, 18);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 19);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 15);
        get(b, 19);
        get(b, 15);
        b.add(new WasmInstr.Const(3.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 18);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 16);
        // u = s_h*t_h; v = s_l*t_h + t_l*ss
        get(b, 14);
        get(b, 15);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 11);
        get(b, 17);
        get(b, 15);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 16);
        get(b, 13);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 12);
        // p_h = __LO(u+v, 0); p_l = v - (p_h - u)
        get(b, 11);
        get(b, 12);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 20);
        get(b, 12);
        get(b, 20);
        get(b, 11);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 21);
        // z_h = CP_H*p_h; z_l = CP_L*p_h + p_l*CP + DP_L[k]
        get(b, 20);
        b.add(new WasmInstr.Const(CP_H));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 22);
        get(b, 20);
        b.add(new WasmInstr.Const(CP_L));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 21);
        b.add(new WasmInstr.Const(CP));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 10);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 23);
        // t = (double)n — o 0xb7 é o convert SIGNED (o n vai NEGATIVO no
        // subnormal: o 0xb8 é o convert_u e o −53 viraria 4294967243.0)
        get(b, 5);
        b.add(new WasmInstr.Simple(0xb7, "f64.convert_i32_s"));
        set(b, 24);
        get(b, 22);
        get(b, 23);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 24);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 25);
        // t2 = z_l - ((t1 - t) - DP_H[k] - z_h)
        get(b, 23);
        get(b, 25);
        get(b, 24);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 22);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 26);
        // a cauda comum
        get(b, 25);
        get(b, 26);
        get(b, 1);
        get(b, 2);
        b.add(new WasmInstr.Call("kof_pow_tail"));
        return new WasmFunc("kof_pow_main", List.of(0x7c, 0x7c, 0x7c), List.of(0x7c),
                List.of(0x7c, 0x7f, 0x7f, 0x7f, 0x7f,
                        0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c,
                        0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c), b);
    }

    /** kof_pow_tail(t1, t2, y, s) -> f64: a CAUDA comum (o caminho
     *  huge-y e o principal chegam aqui) — o split y·log2x, os gates
     *  OVT de overflow/underflow, a cadeia P/LG2 e a construção 2^k.
     *  Params: 0=t1 1=t2 2=y 3=s. Locals: 4=y1 5=p_l 6=p_h 7=z 8=t
     *  9=u 10=v 11=w 12=r (f64) 13=j 14=i 15=k 16=n (i32). */
    static WasmFunc tailFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // y1 = __LO(y, 0); p_l = (y-y1)*t1 + y*t2; p_h = y1*t1
        get(b, 2);
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 4);
        get(b, 2);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 2);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 5);
        get(b, 4);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 6);
        // z = p_l + p_h; j = HI(z); i = LO(z)
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 7);
        get(b, 7);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        set(b, 13);
        get(b, 7);
        b.add(new WasmInstr.Call("kof_pow_lo"));
        set(b, 14);
        // gate 1: z >= 1024 — o overflow (o OVT decide o z == 1024 exato)
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0x40900000));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0x40900000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        get(b, 14);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 5);
        b.add(new WasmInstr.Const(OVT));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 7);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // gate 2: z <= -1075 — o underflow (a MESMA janela de rounding)
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x4090cc00));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0xc090cc00));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        get(b, 14);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 5);
        get(b, 7);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // i = j & 0x7fffffff; k = (i>>20) - 0x3ff; n = 0
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 14);
        get(b, 14);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x3ff));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 15);
        b.add(new WasmInstr.Const(0, 0));
        set(b, 16);
        // if (i > 0x3fe00000): o [z + 0.5] do rounding
        get(b, 14);
        b.add(new WasmInstr.Const(0, 0x3fe00000));
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // n = j + (0x00100000 >> (k+1))
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0x00100000));
        get(b, 15);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 16);
        // k = ((n & 0x7fffffff) >> 20) - 0x3ff (o novo k do n)
        get(b, 16);
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x3ff));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 15);
        // t = __HI(0, n & ~(0x000fffff >> k))
        b.add(new WasmInstr.Const(0.0));
        get(b, 16);
        b.add(new WasmInstr.Const(0, 0x000fffff));
        get(b, 15);
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0xffffffff));
        b.add(new WasmInstr.Simple(0x73, "i32.xor"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Call("kof_pow_withhi"));
        set(b, 8);
        // n = ((n & 0x000fffff) | 0x00100000) >> (20 - k)
        get(b, 16);
        b.add(new WasmInstr.Const(0, 0x000fffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x00100000));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Const(0, 20));
        get(b, 15);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        set(b, 16);
        // if (j < 0) n = -n — o ORDEM é 0 − n (o sub inverte os operandos;
        // o n−0 era um no-op e TODA face com n negativo voltava 2^|n|)
        get(b, 13);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        get(b, 16);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 16);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // p_h -= t
        get(b, 6);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 6);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // t = __LO(p_l+p_h, 0); u = t*LG2_H; v = (p_l-(t-p_h))*LG2 + t*LG2_L
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 8);
        get(b, 8);
        b.add(new WasmInstr.Const(LG2_H));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 9);
        get(b, 5);
        get(b, 8);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(LG2));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 8);
        b.add(new WasmInstr.Const(LG2_L));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 10);
        // z = u+v; w = v-(z-u)
        get(b, 9);
        get(b, 10);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 7);
        get(b, 10);
        get(b, 7);
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 11);
        // t = z*z; t1 = z - t*(P1 + t*(P2 + t*(P3 + t*(P4 + t*P5))))
        get(b, 7);
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 8);
        get(b, 7);
        get(b, 8);
        b.add(new WasmInstr.Const(P1));
        get(b, 8);
        b.add(new WasmInstr.Const(P2));
        get(b, 8);
        b.add(new WasmInstr.Const(P3));
        get(b, 8);
        b.add(new WasmInstr.Const(P4));
        get(b, 8);
        b.add(new WasmInstr.Const(P5));
        // o fold de Horner: [z, P1, t, P2, t, P3, t, P4, t, P5] →
        // (mul, add) ×4 + o mul final — a MESMA lei da L-polynomial do
        // main (as 7 ops ingênuas deixam os valores NA pilha)
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // t·P5
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // P4 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // t·(P4+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // P3 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // t·(P3+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // P2 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // t·(P2+…)
        b.add(new WasmInstr.Simple(0xa0, "f64.add")); // P1 + …
        b.add(new WasmInstr.Simple(0xa2, "f64.mul")); // t·(P1+…) — o mul externo (o 4º par)
        b.add(new WasmInstr.Simple(0xa1, "f64.sub")); // z - t·(…)
        set(b, 0);
        // r = (z*t1)/(t1-2.0) - (w + z*w)
        get(b, 7);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 0);
        b.add(new WasmInstr.Const(2.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        get(b, 11);
        get(b, 7);
        get(b, 11);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 12);
        // z = 1.0 - (r - z)
        b.add(new WasmInstr.Const(1.0));
        get(b, 12);
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 7);
        // j = HI(z) + (n<<20); o subnormal → scalb; o normal → o campo +=(n<<20)
        get(b, 7);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        get(b, 16);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 13);
        get(b, 13);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x4c, "i32.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 7);
        get(b, 16);
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        b.add(new WasmInstr.Call("kof_pow_scalb"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 7);
        get(b, 7);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        get(b, 16);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Call("kof_pow_withhi"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        set(b, 7);
        // s * z
        get(b, 3);
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        return new WasmFunc("kof_pow_tail", List.of(0x7c, 0x7c, 0x7c, 0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c,
                        0x7f, 0x7f, 0x7f, 0x7f), b);
    }
}

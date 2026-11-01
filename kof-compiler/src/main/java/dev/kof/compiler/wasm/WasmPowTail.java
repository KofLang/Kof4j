package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-POW-1 (01/11, #776): o __ieee754_pow fdlibm (o {@code FdLibm.Pow}
 * do PRÓPRIO JDK — os literais hex verbatim do src.zip) como funcs WASM
 * mão-emitida — a ÚLTIMA fatia libm da família math. Este arquivo = a CAUDA COMUM (kof_pow_tail): o split y·(t1+t2) em p_h/p_l, os gates OVT de overflow/underflow (o 1024 exato e o z <= −1075), o bloco de arredondamento [z+0.5], a cadeia P1..P5/LG2 do exp2 e a construção 2^k por bits. O driver/casos especiais fica em WasmPowKernel, o caminho principal em WasmPowMain, os helpers de bits em WasmPowBits.
 *
 * <p>AS LEIS DA EMISSÃO (as mesmas do atan2): os params ocupam os
 * índices baixos (a decl dos locals = SÓ os não-param); os counts de
 * shift I32 para o i32 e I64 para o i64 (o wasm 3.0); o minuendo do
 * SUB empilhado primeiro (a classe inverted-sub); os value-ifs (0x7c)
 * produzem o resultado; os bits de JAVA INT = a semântica do wasm i32
 * (o wrap do count mod 32, o wrap aritmético do add/sub) — o j−0x40900000
 * dos gates e o n<<20 da construção 2^k dependem DELE e casam.
 */

final class WasmPowTail {

    private WasmPowTail() {}

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

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

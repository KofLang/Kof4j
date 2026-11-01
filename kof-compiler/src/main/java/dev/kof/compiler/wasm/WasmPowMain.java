package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-POW-1 (01/11, #776): o __ieee754_pow fdlibm (o {@code FdLibm.Pow}
 * do PRÓPRIO JDK — os literais hex verbatim do src.zip) como funcs WASM
 * mão-emitida — a ÚLTIMA fatia libm da família math. Este arquivo = o
 * CAMINHO PRINCIPAL: o log2(x) polinomial (o split BP/DP + a cadeia
 * L1..L6 + o z_h/z_l). A CAUDA comum fica em WasmPowTail, os helpers de
 * bits + o scalb em WasmPowBits, o driver/casos especiais + o hugey em
 * WasmPowKernel.
 */
final class WasmPowMain {

    private WasmPowMain() {}

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    // ---- as constantes verbatim do FdLibm.Pow (o src.zip do JDK) ----


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

}
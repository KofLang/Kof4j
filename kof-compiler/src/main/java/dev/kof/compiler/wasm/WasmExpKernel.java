package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * MATH-LOG-EXP fatia WASM (07/11, #776): o {@code __ieee754_exp} fdlibm (o
 * {@code FdLibm.Exp} do PRÓPRIO JDK — os P1..P5, ln2HI/LO, invln2 e os
 * thresholds verbatim do src.zip) como func WASM mão-emitida. Fecha o
 * primitivo no ÚLTIMO alvo.
 *
 * <p>Como o log, a aritmética de bits do fdlibm ({@code __HI}) é feita sobre
 * o i64 cru: o high word é {@code bits >> 32} truncado, e o ajuste de
 * expoente {@code __HI(y, __HI(y) + (k << 20))} vira
 * {@code (bits & 0xFFFFFFFF) | ((hi + (k << 20)) << 32)}.
 *
 * <p>Contrato IEEE/JDK: exp(0)=1, exp(1)=e, exp(-Inf)=0, exp(+Inf)=+Inf,
 * exp(NaN)=NaN, overflow → +Inf, underflow → 0.
 */
final class WasmExpKernel {

    private WasmExpKernel() {}

    // os coeficientes do FdLibm.Exp (JDK src.zip) — verbatim
    private static final double HUGE = 1.0e+300;
    private static final double TWOM1000 = 0x1.0p-1000;
    private static final double O_THRESHOLD = 0x1.62e42fefa39efp9;
    private static final double U_THRESHOLD = -0x1.74910d52d3051p9;
    private static final double LN2HI = 0x1.62e42feep-1;
    private static final double LN2LO = 0x1.a39ef35793c76p-33;
    private static final double INVLN2 = 0x1.71547652b82fep0;
    private static final double P1 = 0x1.555555555553ep-3;
    private static final double P2 = -0x1.6c16c16bebd93p-9;
    private static final double P3 = 0x1.1566aaf25de2cp-14;
    private static final double P4 = -0x1.bbd41c5d26bf1p-20;
    private static final double P5 = 0x1.6376972bea4d0p-25;

    // locals: 0=x(f64 param) 1=y 2=hi 3=lo 4=c 5=t (f64)
    //         6=k 7=xsb 8=hx 9=hiword (i32) 10=bits (i64)
    private static final int X = 0, Y = 1, HI = 2, LO = 3, C = 4, T = 5;
    private static final int K = 6, XSB = 7, HX = 8, HIWORD = 9, BITS = 10;

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    private static void cf(List<WasmInstr> b, double d) {
        b.add(new WasmInstr.Const(d));
    }

    private static void ci(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(0, v));
    }

    private static void cl(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(1, v));
    }

    private static void op(List<WasmInstr> b, int code, String name) {
        b.add(new WasmInstr.Simple(code, name));
    }

    private static void ifVoid(List<WasmInstr> b) {
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
    }

    private static void elseVoid(List<WasmInstr> b) {
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
    }

    private static void endVoid(List<WasmInstr> b) {
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
    }

    /** {@code y = __HI(y, __HI(y) + (k << 20))} — o ajuste de expoente. */
    private static void addExponent(List<WasmInstr> b, int kLocal) {
        get(b, Y);
        op(b, 0xbd, "i64.reinterpret_f64");
        set(b, BITS);
        get(b, BITS);
        cl(b, 32);
        op(b, 0x88, "i64.shr_u");
        op(b, 0xa7, "i32.wrap_i64");
        get(b, kLocal);
        ci(b, 20);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        set(b, HIWORD);
        get(b, BITS);
        cl(b, 0xFFFFFFFFL);
        op(b, 0x83, "i64.and");
        get(b, HIWORD);
        op(b, 0xad, "i64.extend_i32_u");
        cl(b, 32);
        op(b, 0x86, "i64.shl");
        op(b, 0x84, "i64.or");
        op(b, 0xbf, "f64.reinterpret_i64");
        set(b, Y);
    }

    static WasmFunc expFunc() {
        var b = new ArrayList<WasmInstr>();

        // hi = 0.0; lo = 0.0; k = 0
        cf(b, 0.0);
        set(b, HI);
        cf(b, 0.0);
        set(b, LO);
        ci(b, 0);
        set(b, K);

        // bits = reinterpret(x); hx = (int)(bits>>32); xsb = (hx>>31)&1; hx &= 0x7fffffff
        get(b, X);
        op(b, 0xbd, "i64.reinterpret_f64");
        set(b, BITS);
        get(b, BITS);
        cl(b, 32);
        op(b, 0x88, "i64.shr_u");
        op(b, 0xa7, "i32.wrap_i64");
        set(b, HX);
        get(b, HX);
        ci(b, 31);
        op(b, 0x75, "i32.shr_s");
        ci(b, 1);
        op(b, 0x71, "i32.and");
        set(b, XSB);
        get(b, HX);
        ci(b, 0x7fffffff);
        op(b, 0x71, "i32.and");
        set(b, HX);

        // if (hx >= 0x40862E42) { non-finite / overflow / underflow }
        get(b, HX);
        ci(b, 0x40862E42);
        op(b, 0x4e, "i32.ge_s");
        ifVoid(b);
        //   if (hx >= 0x7ff00000) { NaN -> x+x ; else exp(±inf) = {inf, 0} }
        get(b, HX);
        ci(b, 0x7ff00000);
        op(b, 0x4e, "i32.ge_s");
        ifVoid(b);
        //     if (((hx & 0xfffff) | __LO(x)) != 0) return x + x
        get(b, HX);
        ci(b, 0xfffff);
        op(b, 0x71, "i32.and");
        get(b, BITS);
        op(b, 0xa7, "i32.wrap_i64");
        op(b, 0x72, "i32.or");
        ci(b, 0);
        op(b, 0x47, "i32.ne");
        ifVoid(b);
        get(b, X);
        get(b, X);
        op(b, 0xa0, "f64.add");
        op(b, 0x0f, "return");
        endVoid(b);
        //     else return (xsb == 0) ? x : 0.0
        get(b, XSB);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        get(b, X);
        op(b, 0x0f, "return");
        elseVoid(b);
        cf(b, 0.0);
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);
        //   if (x > o_threshold) return huge*huge
        get(b, X);
        cf(b, O_THRESHOLD);
        op(b, 0x64, "f64.gt");
        ifVoid(b);
        cf(b, HUGE);
        cf(b, HUGE);
        op(b, 0xa2, "f64.mul");
        op(b, 0x0f, "return");
        endVoid(b);
        //   if (x < u_threshold) return twom1000*twom1000
        get(b, X);
        cf(b, U_THRESHOLD);
        op(b, 0x63, "f64.lt");
        ifVoid(b);
        cf(b, TWOM1000);
        cf(b, TWOM1000);
        op(b, 0xa2, "f64.mul");
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);

        // if (hx > 0x3fd62e42) { |x| > 0.5 ln2 }
        get(b, HX);
        ci(b, 0x3fd62e42);
        op(b, 0x4a, "i32.gt_s");
        ifVoid(b);
        //   if (hx < 0x3FF0A2B2) { |x| < 1.5 ln2 }
        get(b, HX);
        ci(b, 0x3FF0A2B2);
        op(b, 0x48, "i32.lt_s");
        ifVoid(b);
        //     hi = x - ln2HI[xsb]; lo = ln2LO[xsb]; k = 1 - xsb - xsb
        //     ln2HI[xsb] = xsb==0 ? +LN2HI : -LN2HI  ==  LN2HI * (1 - 2*xsb)
        get(b, X);
        cf(b, LN2HI);
        ci(b, 1);
        get(b, XSB);
        ci(b, 2);
        op(b, 0x6c, "i32.mul");
        op(b, 0x6b, "i32.sub");
        op(b, 0xb7, "f64.convert_i32_s");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        set(b, HI);
        cf(b, LN2LO);
        ci(b, 1);
        get(b, XSB);
        ci(b, 2);
        op(b, 0x6c, "i32.mul");
        op(b, 0x6b, "i32.sub");
        op(b, 0xb7, "f64.convert_i32_s");
        op(b, 0xa2, "f64.mul");
        set(b, LO);
        ci(b, 1);
        get(b, XSB);
        op(b, 0x6b, "i32.sub");
        get(b, XSB);
        op(b, 0x6b, "i32.sub");
        set(b, K);
        elseVoid(b);
        //     k = (int)(invln2*x + half[xsb]); t = k; hi = x - t*ln2HI; lo = t*ln2LO
        cf(b, INVLN2);
        get(b, X);
        op(b, 0xa2, "f64.mul");
        cf(b, 0.5);
        ci(b, 1);
        get(b, XSB);
        ci(b, 2);
        op(b, 0x6c, "i32.mul");
        op(b, 0x6b, "i32.sub");
        op(b, 0xb7, "f64.convert_i32_s");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xaa, "i32.trunc_f64_s");
        set(b, K);
        get(b, K);
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, T);
        get(b, X);
        get(b, T);
        cf(b, LN2HI);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        set(b, HI);
        get(b, T);
        cf(b, LN2LO);
        op(b, 0xa2, "f64.mul");
        set(b, LO);
        endVoid(b);
        //   x = hi - lo
        get(b, HI);
        get(b, LO);
        op(b, 0xa1, "f64.sub");
        set(b, X);
        elseVoid(b);
        // else if (hx < 0x3e300000) { |x| < 2^-28 }
        get(b, HX);
        ci(b, 0x3e300000);
        op(b, 0x48, "i32.lt_s");
        ifVoid(b);
        //   if (huge + x > 1.0) return 1.0 + x
        cf(b, HUGE);
        get(b, X);
        op(b, 0xa0, "f64.add");
        cf(b, 1.0);
        op(b, 0x64, "f64.gt");
        ifVoid(b);
        cf(b, 1.0);
        get(b, X);
        op(b, 0xa0, "f64.add");
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);
        endVoid(b);

        // t = x*x; c = x - t*(P1 + t*(P2 + t*(P3 + t*(P4 + t*P5))))
        get(b, X);
        get(b, X);
        op(b, 0xa2, "f64.mul");
        set(b, T);
        get(b, X);
        get(b, T);
        cf(b, P1);
        get(b, T);
        cf(b, P2);
        get(b, T);
        cf(b, P3);
        get(b, T);
        cf(b, P4);
        get(b, T);
        cf(b, P5);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        set(b, C);

        // if (k == 0) return 1.0 - ((x*c)/(c - 2.0) - x)
        get(b, K);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        cf(b, 1.0);
        get(b, X);
        get(b, C);
        op(b, 0xa2, "f64.mul");
        get(b, C);
        cf(b, 2.0);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa3, "f64.div");
        get(b, X);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        elseVoid(b);
        //   y = 1.0 - ((lo - (x*c)/(2.0 - c)) - hi)
        cf(b, 1.0);
        get(b, LO);
        get(b, X);
        get(b, C);
        op(b, 0xa2, "f64.mul");
        cf(b, 2.0);
        get(b, C);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa3, "f64.div");
        op(b, 0xa1, "f64.sub");
        get(b, HI);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        set(b, Y);
        endVoid(b);

        // if (k >= -1021) { y = __HI(y, __HI(y) + (k<<20)); return y }
        get(b, K);
        ci(b, -1021);
        op(b, 0x4e, "i32.ge_s");
        ifVoid(b);
        addExponent(b, K);
        get(b, Y);
        op(b, 0x0f, "return");
        elseVoid(b);
        //   y = __HI(y, __HI(y) + ((k+1000)<<20)); return y * twom1000
        get(b, K);
        ci(b, 1000);
        op(b, 0x6a, "i32.add");
        set(b, HIWORD);
        addExponent(b, HIWORD);
        get(b, Y);
        cf(b, TWOM1000);
        op(b, 0xa2, "f64.mul");
        op(b, 0x0f, "return");
        endVoid(b);

        // fallback (inalcançável)
        cf(b, 0.0);

        return new WasmFunc("kof_math_exp", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c,
                        0x7f, 0x7f, 0x7f, 0x7f, 0x7e),
                b);
    }
}

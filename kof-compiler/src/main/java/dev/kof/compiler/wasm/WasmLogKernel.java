package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * MATH-LOG-EXP fatia WASM (07/11, #776): o {@code __ieee754_log} fdlibm (o
 * {@code FdLibm.Log} do PRÓPRIO JDK — os coeficientes Lg1..Lg7 e o ln2_hi/lo
 * verbatim do src.zip) como func WASM mão-emitida. Fecha o primitivo no
 * ÚLTIMO alvo: JVM/Script/x86/cross já o têm (commit 6aa6c7727).
 *
 * <p>O fdlibm trabalha com as DUAS metades de 32 bits do double
 * ({@code __HI}/{@code __LO}); aqui a mesma aritmética é feita sobre o i64
 * cru ({@code i64.reinterpret_f64}) — o high word é {@code bits >> 32}
 * truncado a i32, o low é {@code bits} truncado. A normalização
 * {@code __HI(x, high)} vira {@code (bits & 0xFFFFFFFF) | (high << 32)}.
 *
 * <p>Contrato IEEE/JDK: log(1)=0, log(e)=1, log(0)=-Inf, log(-1)=NaN,
 * log(+Inf)=+Inf. A prova é o módulo VALIDADO por wasm-tools + o stdout
 * igual ao oráculo JVM (WasmWasiLogExpE2ETest).
 */
final class WasmLogKernel {

    private WasmLogKernel() {}

    // os coeficientes do FdLibm.Log (JDK src.zip) — verbatim
    private static final double LN2_HI = 0x1.62e42feep-1;
    private static final double LN2_LO = 0x1.a39ef35793c76p-33;
    private static final double LG1 = 0x1.5555555555593p-1;
    private static final double LG2 = 0x1.999999997fa04p-2;
    private static final double LG3 = 0x1.2492494229359p-2;
    private static final double LG4 = 0x1.c71c51d8e78afp-3;
    private static final double LG5 = 0x1.7466496cb03dep-3;
    private static final double LG6 = 0x1.39a09d078c69fp-3;
    private static final double LG7 = 0x1.2f112df3e5244p-3;
    private static final double TWO54 = 0x1.0p54;

    // locals: 0=x(f64 param) 1=f 2=s 3=z 4=R 5=w 6=t1 7=t2 8=dk 9=hfsq (f64)
    //         10=k 11=hx 12=i 13=j 14=lx (i32) 15=bits (i64)
    private static final int X = 0, F = 1, S = 2, Z = 3, R = 4, W = 5, T1 = 6, T2 = 7, DK = 8, HFSQ = 9;
    private static final int K = 10, HX = 11, I = 12, J = 13, LX = 14, BITS = 15;

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

    /** {@code hx = __HI(x)} — o high word como i32 (truncado). */
    private static void hiOfX(List<WasmInstr> b) {
        get(b, X);
        op(b, 0xbd, "i64.reinterpret_f64");
        cl(b, 32);
        op(b, 0x88, "i64.shr_u");
        op(b, 0xa7, "i32.wrap_i64");
    }

    /** {@code x = __HI(x, high)} — o high word do local HX, o low preservado. */
    private static void setHiOfX(List<WasmInstr> b) {
        get(b, X);
        op(b, 0xbd, "i64.reinterpret_f64");
        cl(b, 0xFFFFFFFFL);
        op(b, 0x83, "i64.and");
        get(b, HX);
        op(b, 0xad, "i64.extend_i32_u");
        cl(b, 32);
        op(b, 0x86, "i64.shl");
        op(b, 0x84, "i64.or");
        op(b, 0xbf, "f64.reinterpret_i64");
        set(b, X);
    }

    static WasmFunc logFunc() {
        var b = new ArrayList<WasmInstr>();

        // bits = reinterpret(x); hx = (int)(bits >> 32); lx = (int)bits
        get(b, X);
        op(b, 0xbd, "i64.reinterpret_f64");
        set(b, BITS);
        get(b, BITS);
        cl(b, 32);
        op(b, 0x88, "i64.shr_u");
        op(b, 0xa7, "i32.wrap_i64");
        set(b, HX);
        get(b, BITS);
        op(b, 0xa7, "i32.wrap_i64");
        set(b, LX);
        // k = 0
        ci(b, 0);
        set(b, K);

        // if (hx < 0x00100000) { ... subnormal / zero / negative ... }
        get(b, HX);
        ci(b, 0x00100000);
        op(b, 0x48, "i32.lt_s");
        ifVoid(b);
        //   if (((hx & 0x7fffffff) | lx) == 0) return -Inf
        get(b, HX);
        ci(b, 0x7fffffff);
        op(b, 0x71, "i32.and");
        get(b, LX);
        op(b, 0x72, "i32.or");
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        cf(b, Double.NEGATIVE_INFINITY);
        op(b, 0x0f, "return");
        endVoid(b);
        //   if (hx < 0) return (x - x)/0.0  (NaN)
        get(b, HX);
        ci(b, 0);
        op(b, 0x48, "i32.lt_s");
        ifVoid(b);
        get(b, X);
        get(b, X);
        op(b, 0xa1, "f64.sub");
        cf(b, 0.0);
        op(b, 0xa3, "f64.div");
        op(b, 0x0f, "return");
        endVoid(b);
        //   k -= 54; x *= TWO54; hx = __HI(x)
        get(b, K);
        ci(b, 54);
        op(b, 0x6b, "i32.sub");
        set(b, K);
        get(b, X);
        cf(b, TWO54);
        op(b, 0xa2, "f64.mul");
        set(b, X);
        hiOfX(b);
        set(b, HX);
        endVoid(b);

        // if (hx >= 0x7ff00000) return x + x  (Inf/NaN)
        get(b, HX);
        ci(b, 0x7ff00000);
        op(b, 0x4e, "i32.ge_s");
        ifVoid(b);
        get(b, X);
        get(b, X);
        op(b, 0xa0, "f64.add");
        op(b, 0x0f, "return");
        endVoid(b);

        // k += (hx >> 20) - 1023
        get(b, K);
        get(b, HX);
        ci(b, 20);
        op(b, 0x75, "i32.shr_s");
        ci(b, 1023);
        op(b, 0x6b, "i32.sub");
        op(b, 0x6a, "i32.add");
        set(b, K);
        // hx &= 0x000fffff
        get(b, HX);
        ci(b, 0x000fffff);
        op(b, 0x71, "i32.and");
        set(b, HX);
        // i = (hx + 0x95f64) & 0x100000
        get(b, HX);
        ci(b, 0x95f64);
        op(b, 0x6a, "i32.add");
        ci(b, 0x100000);
        op(b, 0x71, "i32.and");
        set(b, I);
        // hx = hx | (i ^ 0x3ff00000); x = __HI(x, hx)
        get(b, HX);
        get(b, I);
        ci(b, 0x3ff00000);
        op(b, 0x73, "i32.xor");
        op(b, 0x72, "i32.or");
        set(b, HX);
        setHiOfX(b);
        // k += (i >> 20)
        get(b, K);
        get(b, I);
        ci(b, 20);
        op(b, 0x75, "i32.shr_s");
        op(b, 0x6a, "i32.add");
        set(b, K);
        // f = x - 1.0
        get(b, X);
        cf(b, 1.0);
        op(b, 0xa1, "f64.sub");
        set(b, F);

        // if ((0x000fffff & (2 + hx)) < 3) { |f| < 2^-20 }
        get(b, HX);
        ci(b, 2);
        op(b, 0x6a, "i32.add");
        ci(b, 0x000fffff);
        op(b, 0x71, "i32.and");
        ci(b, 3);
        op(b, 0x48, "i32.lt_s");
        ifVoid(b);
        //   if (f == 0.0) { if (k == 0) return 0.0; else return dk*ln2_hi + dk*ln2_lo; }
        get(b, F);
        cf(b, 0.0);
        op(b, 0x61, "f64.eq");
        ifVoid(b);
        get(b, K);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        cf(b, 0.0);
        op(b, 0x0f, "return");
        elseVoid(b);
        get(b, K);
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, DK);
        get(b, DK);
        cf(b, LN2_HI);
        op(b, 0xa2, "f64.mul");
        get(b, DK);
        cf(b, LN2_LO);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);
        //   R = f*f*(0.5 - 0.33333333333333333*f)
        get(b, F);
        get(b, F);
        op(b, 0xa2, "f64.mul");
        cf(b, 0.5);
        cf(b, 0.33333333333333333);
        get(b, F);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        op(b, 0xa2, "f64.mul");
        set(b, R);
        //   if (k == 0) return f - R; else return dk*ln2_hi - ((R - dk*ln2_lo) - f)
        get(b, K);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        get(b, F);
        get(b, R);
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        elseVoid(b);
        get(b, K);
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, DK);
        get(b, DK);
        cf(b, LN2_HI);
        op(b, 0xa2, "f64.mul");
        get(b, R);
        get(b, DK);
        cf(b, LN2_LO);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        get(b, F);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);

        // s = f/(2.0 + f); dk = (double)k; z = s*s
        get(b, F);
        cf(b, 2.0);
        get(b, F);
        op(b, 0xa0, "f64.add");
        op(b, 0xa3, "f64.div");
        set(b, S);
        get(b, K);
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, DK);
        get(b, S);
        get(b, S);
        op(b, 0xa2, "f64.mul");
        set(b, Z);
        // i = hx - 0x6147a; w = z*z; j = 0x6b851 - hx
        get(b, HX);
        ci(b, 0x6147a);
        op(b, 0x6b, "i32.sub");
        set(b, I);
        get(b, Z);
        get(b, Z);
        op(b, 0xa2, "f64.mul");
        set(b, W);
        ci(b, 0x6b851);
        get(b, HX);
        op(b, 0x6b, "i32.sub");
        set(b, J);
        // t1 = w*(Lg2 + w*(Lg4 + w*Lg6))
        cf(b, LG2);
        get(b, W);
        cf(b, LG4);
        get(b, W);
        cf(b, LG6);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        get(b, W);
        op(b, 0xa2, "f64.mul");
        set(b, T1);
        // t2 = z*(Lg1 + w*(Lg3 + w*(Lg5 + w*Lg7)))
        cf(b, LG1);
        get(b, W);
        cf(b, LG3);
        get(b, W);
        cf(b, LG5);
        get(b, W);
        cf(b, LG7);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        get(b, Z);
        op(b, 0xa2, "f64.mul");
        set(b, T2);
        // i |= j; R = t2 + t1
        get(b, I);
        get(b, J);
        op(b, 0x72, "i32.or");
        set(b, I);
        get(b, T2);
        get(b, T1);
        op(b, 0xa0, "f64.add");
        set(b, R);

        // if (i > 0) { hfsq = 0.5*f*f; ... } else { ... }
        get(b, I);
        ci(b, 0);
        op(b, 0x4a, "i32.gt_s");
        ifVoid(b);
        cf(b, 0.5);
        get(b, F);
        op(b, 0xa2, "f64.mul");
        get(b, F);
        op(b, 0xa2, "f64.mul");
        set(b, HFSQ);
        get(b, K);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        //   return f-(hfsq - s*(hfsq + R))
        get(b, F);
        get(b, HFSQ);
        get(b, S);
        get(b, HFSQ);
        get(b, R);
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        elseVoid(b);
        //   return dk*ln2_hi - ((hfsq - (s*(hfsq + R) + dk*ln2_lo)) - f)
        get(b, DK);
        cf(b, LN2_HI);
        op(b, 0xa2, "f64.mul");
        get(b, HFSQ);
        get(b, S);
        get(b, HFSQ);
        get(b, R);
        op(b, 0xa0, "f64.add");
        op(b, 0xa2, "f64.mul");
        get(b, DK);
        cf(b, LN2_LO);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        op(b, 0xa1, "f64.sub");
        get(b, F);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        endVoid(b);
        elseVoid(b);
        get(b, K);
        ci(b, 0);
        op(b, 0x46, "i32.eq");
        ifVoid(b);
        //   return f - s*(f - R)
        get(b, F);
        get(b, S);
        get(b, F);
        get(b, R);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        elseVoid(b);
        //   return dk*ln2_hi - ((s*(f - R) - dk*ln2_lo) - f)
        get(b, DK);
        cf(b, LN2_HI);
        op(b, 0xa2, "f64.mul");
        get(b, S);
        get(b, F);
        get(b, R);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa2, "f64.mul");
        get(b, DK);
        cf(b, LN2_LO);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        get(b, F);
        op(b, 0xa1, "f64.sub");
        op(b, 0xa1, "f64.sub");
        op(b, 0x0f, "return");
        endVoid(b);
        endVoid(b);

        // fallback (inalcançável — todo caminho retorna; satisfaz o validador)
        cf(b, 0.0);

        return new WasmFunc("kof_math_log", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c,
                        0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7e),
                b);
    }
}

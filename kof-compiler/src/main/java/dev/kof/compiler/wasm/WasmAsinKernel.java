package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-ASIN-1 (28/10, #776): o __ieee754_asin fdlibm (o FdLibm.Asin do
 * PRÓPRIO JDK — pio2/pio4 + os pS0..pS5/qS1..qS4 verbatim do src.zip)
 * como func WASM mão-emitida. A FUNDAÇÃO MEDIDA: Math.asin ==
 * StrictMath.asin em 34/34 entradas (a rodada 30 mediu a família toda:
 * asin/acos/atan2 GO; o WRAPPER acos = π/2 − asin está MORTO — 8/18
 * divergem por 1 ulp, o acos terá a própria fatia). AS LEIS PRÉ-
 * APLICADAS (21-30): o Horner FUSIONADO; o minuendo do sub EMPILHADO
 * primeiro; o eq de i32 é 0x46 (o 0x61 = f64.eq); o if CONSUME a cond
 * da pilha (re-empilhar depois do set); o NaN do fdlibm ((x−x)/(x−x))
 * = 0/0 = o canônico; o __LO(w,0) = o zero do word baixo via o
 * reinterpret/AND/reinterpret i64 (o padrão provado do laço médio do
 * sin); o threshold 0x3FEF3333 = o double 0x1.F3333p-1 (o MENOR double
 * do hi-word — o compare double ⟺ o compare do fdlibm).
 */
final class WasmAsinKernel {

    private WasmAsinKernel() {}

    private static final double PIO2_HI = 0x1.921fb54442d18p0;
    private static final double PIO2_LO = 0x1.1a62633145c07p-54;
    private static final double PIO4_HI = 0x1.921fb54442d18p-1;
    private static final double PS_0 = 0x1.5555555555555p-3;
    private static final double PS_1 = -0x1.4d61203eb6f7dp-2;
    private static final double PS_2 = 0x1.9c1550e884455p-3;
    private static final double PS_3 = -0x1.48228b5688f3bp-5;
    private static final double PS_4 = 0x1.9efe07501b288p-11;
    private static final double PS_5 = 0x1.23de10dfdf709p-15;
    private static final double QS_1 = -0x1.33a271c8a2d4bp1;
    private static final double QS_2 = 0x1.02ae59c598ac8p1;
    private static final double QS_3 = -0x1.6066c1b8d0159p-1;
    private static final double QS_4 = 0x1.3b8c5b12e9282p-4;
    private static final double ASIN_THRESH = 0x1.F3333p-1;   // o hi 0x3FEF3333 do fdlibm (0.975)

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    // p = t·(pS0 + t·(pS1 + t·(pS2 + t·(pS3 + t·(pS4 + t·pS5))))) — o
    // Horner FUSIONADO (o t no local 2; o resultado no local 3)
    private static void emitPolyP(List<WasmInstr> b) {
        b.add(new WasmInstr.Const(PS_0));
        get(b, 2);
        b.add(new WasmInstr.Const(PS_1));
        get(b, 2);
        b.add(new WasmInstr.Const(PS_2));
        get(b, 2);
        b.add(new WasmInstr.Const(PS_3));
        get(b, 2);
        b.add(new WasmInstr.Const(PS_4));
        get(b, 2);
        b.add(new WasmInstr.Const(PS_5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 3);
    }

    // q = 1.0 + t·(qS1 + t·(qS2 + t·(qS3 + t·qS4))) → local 4
    private static void emitPolyQ(List<WasmInstr> b) {
        b.add(new WasmInstr.Const(1.0));
        get(b, 2);
        b.add(new WasmInstr.Const(QS_1));
        get(b, 2);
        b.add(new WasmInstr.Const(QS_2));
        get(b, 2);
        b.add(new WasmInstr.Const(QS_3));
        get(b, 2);
        b.add(new WasmInstr.Const(QS_4));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 4);
    }

    static WasmFunc asinFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // locals: 0=x(f64 param) 1=ax 2=t 3=p 4=q 5=s 6=w 7=c (f64)
        //         8=neg (i32)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 1);
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        set(b, 8);
        // |x| >= 1: o ±1.0 EXATO → x·pio2_hi + x·pio2_lo (o sinal no x);
        // |x| > 1 → (x−x)/(x−x) = o NaN do invalid (o MESMO bits do fdlibm)
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Const(PIO2_HI));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 0);
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // |x| < 0.5: o caminho pequeno — |x| < 2^-27 → return x (o ±0.0
        // bit-exato); senão t = x² (o x ASSINADO — o sinal carrega no final)
        get(b, 1);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(0x1p-27));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        emitPolyP(b);
        emitPolyQ(b);
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 6);                                               // w = p/q
        get(b, 0);
        get(b, 0);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x0f, "return"));             // x + x·w
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // 1 > |x| >= 0.5: w = 1 − |x|; t = w·0.5; os MESMOS polinômios; s = √t
        b.add(new WasmInstr.Const(1.0));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // o minuendo EMPILHADO primeiro
        set(b, 6);
        get(b, 6);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        emitPolyP(b);
        emitPolyQ(b);
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9f, "f64.sqrt"));
        set(b, 5);
        // |x| >= 0x1.F3333p-1 (o hi 0x3FEF3333 — |x| > 0.975): o caminho
        // t = pio2_hi − (2·(s + s·w) − pio2_lo); senão o DF do fdlibm
        get(b, 1);
        b.add(new WasmInstr.Const(ASIN_THRESH));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 6);                                               // w = p/q
        b.add(new WasmInstr.Const(PIO2_HI));
        get(b, 5);
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));            // s + s·w
        b.add(new WasmInstr.Const(2.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // 2·(s + s·w)
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // − pio2_lo
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // pio2_hi − (…)
        set(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // w = o s com o word baixo ZERADO (o __LO(w,0) do fdlibm — o
        // reinterpret/AND/reinterpret i64)
        get(b, 5);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, -4294967296L));             // 0xFFFFFFFF00000000
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        set(b, 6);
        // c = (t − w²)/(s + w)
        get(b, 2);
        get(b, 6);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // t − w²
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));            // s + w
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 7);
        // p ← r = p/q;  p = 2·s·r − (pio2_lo − 2·c);  q = pio4_hi − 2·w;
        // t = pio4_hi − (p − q)
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 3);
        b.add(new WasmInstr.Const(2.0));
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 3);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // 2·s·r
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Const(2.0));
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // pio2_lo − 2·c
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // 2·s·r − (…)
        set(b, 3);
        b.add(new WasmInstr.Const(PIO4_HI));
        b.add(new WasmInstr.Const(2.0));
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 4);                                               // q = pio4_hi − 2·w
        b.add(new WasmInstr.Const(PIO4_HI));
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // p − q
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // pio4_hi − (p − q)
        set(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o sinal: neg ? −t : t — o neg NO VALOR
        get(b, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_asin", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7f), b);
    }
}

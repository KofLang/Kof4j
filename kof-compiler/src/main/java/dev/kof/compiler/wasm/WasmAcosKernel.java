package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-ACOS-1 (28/10, #776): o __ieee754_acos fdlibm (o FdLibm.Acos do
 * PRÓPRIO JDK — pio2_hi/lo + os MESMOS pS0..pS5/qS1..qS4 do asin,
 * verbatim do src.zip) como func WASM mão-emitida. O WRAPPER π/2 − asin
 * está MORTO (8/18 por 1 ulp, medido na rodada 31) — as correções df/c
 * do próprio fdlibm são o contrato. TRÊS RAMOS (o sinal viaja NELES —
 * sem flag final): o pequeno |x| < 0.5 (o x ASSINADO em
 * pio2_hi − (x − (pio2_lo − x·r)); |x| <= 2^-57 → a CONSTANTE
 * pio2_hi+pio2_lo), o x < −0.5 (π − 2(s+w) com z = (1+x)/2) e o
 * x > 0.5 (2(df + w) com z = (1−x)/2 e o df = o s com o word baixo
 * ZERADO — a dança reinterpret/AND/reinterpret i64 provada). AS LEIS
 * PRÉ-APLICADAS (21-31): o Horner FUSIONADO; o minuendo do sub
 * EMPILHADO primeiro; a decl dos locals CASA com o mapa de slots (8
 * f64, NENHUM i32 — a lei da rodada 31); o NaN do fdlibm
 * ((x−x)/(x−x)); o f64.sqrt = o 0x9f nativo.
 */
final class WasmAcosKernel {

    private WasmAcosKernel() {}

    private static final double PIO2_HI = 0x1.921fb54442d18p0;
    private static final double PIO2_LO = 0x1.1a62633145c07p-54;
    private static final double PI = 0x1.921fb54442d18p1;
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

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    // p = z·(pS0 + z·(pS1 + z·(pS2 + z·(pS3 + z·(pS4 + z·pS5))))) — o
    // Horner FUSIONADO (o z no local 2; o resultado no local 3)
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

    // q = 1.0 + z·(qS1 + z·(qS2 + z·(qS3 + z·qS4))) → local 4
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

    static WasmFunc acosFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // locals: 0=x(f64 param) 1=ax 2=z 3=p 4=q 5=s 6=w 7=c 8=df — 8 f64,
        // NENHUM i32 (o sinal viaja nos ramos — a lei da contagem da decl)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 1);
        // |x| >= 1: o ±1.0 exato → 0.0 ou π + 2·pio2_lo; |x| > 1 → o NaN
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(PI));
        b.add(new WasmInstr.Const(2.0));
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
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
        // |x| < 0.5: |x| <= 2^-57 → a CONSTANTE pio2_hi + pio2_lo; senão o
        // x ASSINADO: pio2_hi − (x − (pio2_lo − x·r))
        get(b, 1);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(0x1p-57));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(PIO2_HI));
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        emitPolyP(b);
        emitPolyQ(b);
        b.add(new WasmInstr.Const(PIO2_HI));
        get(b, 0);
        b.add(new WasmInstr.Const(PIO2_LO));
        get(b, 0);
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));            // r = p/q
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // x·r
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // pio2_lo − x·r
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // x − (…)
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // pio2_hi − (…)
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // os DOIS ramos grandes como o value-if do sinal (o x < 0 decide)
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        // x < −0.5: z = (1+x)/2; s = √z; w = r·s − pio2_lo; π − 2(s+w)
        b.add(new WasmInstr.Const(1.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        emitPolyP(b);
        emitPolyQ(b);
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9f, "f64.sqrt"));
        set(b, 5);
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 6);                                               // r = p/q
        get(b, 6);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(PIO2_LO));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 6);                                               // w = r·s − pio2_lo
        b.add(new WasmInstr.Const(PI));
        b.add(new WasmInstr.Const(2.0));
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // π − 2(s+w)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // x > 0.5: z = (1−x)/2; s = √z; df = o s com o word baixo ZERADO;
        // c = (z − df²)/(s + df); w = r·s + c; 2(df + w)
        b.add(new WasmInstr.Const(1.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9f, "f64.sqrt"));
        set(b, 5);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, -4294967296L));             // 0xFFFFFFFF00000000
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        set(b, 8);                                               // df = __LO(s,0)
        get(b, 2);
        get(b, 8);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // z − df²
        get(b, 5);
        get(b, 8);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));            // s + df
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 7);                                               // c
        emitPolyP(b);
        emitPolyQ(b);
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 6);                                               // r = p/q
        get(b, 6);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 6);                                               // w = r·s + c
        b.add(new WasmInstr.Const(2.0));
        get(b, 8);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // 2(df + w)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_acos", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c), b);
    }
}

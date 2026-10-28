package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fase E (27/10, #776 — MATH-TAN-1, a re-emissão da rodada 27 com os DEFEITOS
 * PRÉ-CONSERTADOS): o {@code __kernel_tan} fdlibm da FAIXA PEQUENA
 * (|x| ≤ π/4), mapeado verbatim do FdLibm.Tan do PRÓPRIO JDK. A FUNDAÇÃO
 * MEDIDA: Math.tan == StrictMath.tan em 10/10 entradas da faixa. AS LEIS
 * APLICADAS ANTES DA EMISSÃO: o limiar = 0x1.59428p−1 EXATO; a cauda
 * refletida = o `1 − 2·(xw − (w²/(w+1) − r))` COMPLETO; o VALOR num local e
 * o sinal multiplica DEPOIS; o END de todo frame explícito.
 */
final class WasmTanKernel {

    private WasmTanKernel() {
    }

    private static List<WasmInstr> get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, "l" + i));
        return b;
    }

    private static List<WasmInstr> set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, "l" + i));
        return b;
    }

    // as constantes EXATAS do FdLibm.Tan (os literais hex verbatim)
    private static final double PIO4 = 0x1.921fb54442d18p-1;
    private static final double PIO4LO = 0x1.1a62633145c07p-55;
    private static final double TAN_T0 = 0x1.5555555555563p-2;
    private static final double TAN_T1 = 0x1.111111110fe7ap-3;
    private static final double TAN_T2 = 0x1.ba1ba1bb341fep-5;
    private static final double TAN_T3 = 0x1.664f48406d637p-6;
    private static final double TAN_T4 = 0x1.226e3e96e8493p-7;
    private static final double TAN_T5 = 0x1.d6d22c9560328p-9;
    private static final double TAN_T6 = 0x1.7dbc8fee08315p-10;
    private static final double TAN_T7 = 0x1.344d8f2f26501p-11;
    private static final double TAN_T8 = 0x1.026f71a8d1068p-12;
    private static final double TAN_T9 = 0x1.47e88a03792a6p-14;
    private static final double TAN_T10 = 0x1.2b80f32f0a7e9p-14;
    private static final double TAN_T11 = -0x1.375cbdb605373p-16;
    private static final double TAN_T12 = 0x1.b2a7074bf7ad4p-16;
    private static final double TAN_THRESH = 0x1.59428p-1;   // o hi 0x3FE59428 do fdlibm
    private static final double TAN_HI_LIMIT = 0x1.3p-3;     // 2⁻²⁸ (o atalho do tiny)

    /** A func {@code kof_math_tan} — o corpo inteiro num if de faixa
     *  (|x| ≤ π/4; além = o trap honesto). Locais: 1=xw, 2=z, 3=w, 4=r,
     *  5=valor, 6=s, 7=sg(i32), 8=tmp(f64). */
    static WasmFunc tanFunc() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, null, 0x7c));
        // o guard da faixa: |x| ≤ π/4, senão o trap honesto
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));            // LEI: o abs NO X
        b.add(new WasmInstr.Const(PIO4));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        // o sinal: sg = x < 0 ? −1 : 1 (VOID if — LEI)
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, -1));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 1));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // xw = |x|
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 1);
        // |xw| ≥ 0x1.59428p−1 → a reflexão: xw = (π/4 − xw) + π/4lo
        get(b, 1);
        b.add(new WasmInstr.Const(TAN_THRESH));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));             // fdlibm: ix >= 0x3FE59428 (o >= inclui a borda)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // LEI: VOID
        b.add(new WasmInstr.Const(PIO4));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // π/4 − xw (o π/4 EMPILHADO primeiro = a−b correto — a rodada 28 provou o contrário como o sinal invertido)
        b.add(new WasmInstr.Const(PIO4LO));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 1);
        b.add(new WasmInstr.Const(0, 1));                        // a FLAG do refletido (o ix ORIGINAL do fdlibm morre no local 1 — o flag sobrevive)
        set(b, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // z = xw²; w = z²
        get(b, 1);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        get(b, 2);
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 3);
        // o VALOR num local primeiro (a melhora da rodada 28) — a cadeia dos
        // dois trilhos do fdlibm: r = o par T[1,3,5,7,9,11]; v = z·o ímpar T[2,4,...,12]
        b.add(new WasmInstr.Const(TAN_T1));                      // r = T1
        get(b, 3);                                               // w
        b.add(new WasmInstr.Const(TAN_T3));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T5));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T7));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T9));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T11));
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
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "r"));
        b.add(new WasmInstr.Const(TAN_T2));                      // v = o mesmo Horner FUNDIDO do r: T2
        get(b, 3);                                               // w
        b.add(new WasmInstr.Const(TAN_T4));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T6));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T8));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T10));
        get(b, 3);
        b.add(new WasmInstr.Const(TAN_T12));
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
        get(b, 2);                                               // ·z
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "v"));
        // s = z·xw
        get(b, 2);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 6);
        // r = z·(s·(r + v))
        get(b, 6);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "r"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "v"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 4);
        // r += T0·s
        b.add(new WasmInstr.Const(TAN_T0));
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "r"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 4);
        // w = xw + r
        get(b, 1);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "r"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 3);
        // o sg_f64 NA BASE primeiro, depois o if do valor, depois o mul
        get(b, 7);
        b.add(new WasmInstr.Simple(0xb7, "f64.convert_i32_s"));  // sg_f64 (fica na base)
        // o valor: a FLAG do refletido → 1 − 2·(xw − (w²/(w+1) − r))  senão w
        get(b, 8);                                               // o flag = o ix ORIGINAL do fdlibm (o xw de agora já é pequeno DEMAIS para o teste)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(1.0));                         // o v = 1.0 do fdlibm (o iy=1) — LEI da rodada 27
        get(b, 1);                                               // xw (o REFLETIDO)
        get(b, 3);                                               // w
        get(b, 3);                                               // w
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // w²
        get(b, 3);                                               // w
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));            // w+1
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));            // w²/(w+1)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "r")); // r
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // w²/(w+1) − r
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // xw − (…)
        b.add(new WasmInstr.Const(2.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // 2·(…)
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // 1 − 2·(…)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 3);                                               // w
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));            // sg_f64 · valor
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));        // a fronteira honesta (|x| > π/4)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_tan", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7f, 0x7f), b);
    }
}

package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-ATAN-1 (28/10, #776): o __kernel_atan fdlibm (o FdLibm.Atan do
 * PRÓPRIO JDK — os atanhi/atanlo/aT verbatim do src.zip) como func WASM
 * mão-emitida. AUTOCONTIDO: o atan não usa rem_pio2 — a redução = a
 * árvore de 4 ramos (0.4375/0.6875/1.1875/2.4375 — TODAS as bordas com
 * hi-word de mantissa 0, o compare double ⟺ o compare hi-word, a lei da
 * rodada 28). A FUNDAÇÃO MEDIDA: Math.atan == StrictMath.atan em 34/34
 * entradas (o port fixa o oráculo byte a byte; asin/acos/atan2 também
 * GO — as fatias seguintes). AS LEIS PRÉ-APLICADAS (pagas na 21-28): o
 * Horner FUSIONADO numa única forma ([c0, w, c1, ..., cn] +
 * (mul,add)×n — a v-chain ao contrário pendurou o T12); o minuendo do
 * sub EMPILHADO primeiro; o neg NO VALOR (0x9a — o −z do fdlibm, não o
 * 0−z do minuendo); o return PRECOCE do |x| < 2^-29 preserva o ±0.0
 * bit-exato (a dança do inexact do fdlibm sempre devolve x aqui); o
 * id NUNCA indexa tabela — a árvore de ifs grava h/l em locais.
 */
final class WasmAtanKernel {

    private WasmAtanKernel() {}

    // o atanhi[] do FdLibm.Atan (JDK src.zip) — verbatim
    private static final double ATAN_HI_0 = 0x1.dac670561bb4fp-2;
    private static final double ATAN_HI_1 = 0x1.921fb54442d18p-1;
    private static final double ATAN_HI_2 = 0x1.f730bd281f69bp-1;
    private static final double ATAN_HI_3 = 0x1.921fb54442d18p0;
    // o atanlo[]
    private static final double ATAN_LO_0 = 0x1.a2b7f222f65e2p-56;
    private static final double ATAN_LO_1 = 0x1.1a62633145c07p-55;
    private static final double ATAN_LO_2 = 0x1.007887af0cbbdp-56;
    private static final double ATAN_LO_3 = 0x1.1a62633145c07p-54;
    // o aT[] (11 coeficientes do polinômio ímpar)
    private static final double AT_0 = 0x1.555555555550dp-2;
    private static final double AT_1 = -0x1.999999998ebc4p-3;
    private static final double AT_2 = 0x1.24924920083ffp-3;
    private static final double AT_3 = -0x1.c71c6fe231671p-4;
    private static final double AT_4 = 0x1.745cdc54c206ep-4;
    private static final double AT_5 = -0x1.3b0f2af749a6dp-4;
    private static final double AT_6 = 0x1.10d66a0d03d51p-4;
    private static final double AT_7 = -0x1.dde2d52defd9ap-5;
    private static final double AT_8 = 0x1.97b4b24760debp-5;
    private static final double AT_9 = -0x1.2b4442c6a6c2fp-5;
    private static final double AT_10 = 0x1.0ad3ae322da11p-6;

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    static WasmFunc atanFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // locals: 0=x(f64 param) 1=ax 2=z 3=w 4=s1 5=s2 6=h 7=l (f64)
        //         8=neg 9=small 10=id (i32)
        // NaN: x != x → return x+x (o fdlibm devolve x+x no NaN — o MESMO bit)
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0x62, "f64.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // neg = x < 0 (o hx < 0 do fdlibm)
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        set(b, 8);
        // ax = |x| (LEI: o abs NO X)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 1);
        // small = ax < 0.4375 (0x3fdc0000 — a borda exata)
        get(b, 1);
        b.add(new WasmInstr.Const(0x1.cp-2));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        set(b, 9);
        get(b, 9);                                               // o if CONSUME a cond da pilha — o set so grava o local
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // |x| < 2^-29 → return x PRECOCE (o ±0.0 bit-exato; a dança do
        // inexact do fdlibm devolve x sempre aqui)
        get(b, 1);
        b.add(new WasmInstr.Const(0x1p-29));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // o fdlibm ABS-iza o x SÓ no ramo grande (o pequeno usa o x ASSINADO)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 0);
        // a árvore da redução — id 0..3, o x reescrito no local 0
        get(b, 1);
        b.add(new WasmInstr.Const(0x1.3p0));                     // 1.1875
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(0x1.6p-1));                    // 0.6875
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // id=0: x = (2x − 1)/(2 + x)
        b.add(new WasmInstr.Const(0, 0));
        set(b, 10);
        b.add(new WasmInstr.Const(2.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // 2x − 1 (minuendo empilhado primeiro)
        b.add(new WasmInstr.Const(2.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));            // 2 + x
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // id=1: x = (x − 1)/(x + 1)
        b.add(new WasmInstr.Const(0, 1));
        set(b, 10);
        get(b, 0);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 0);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(0x1.38p1));                    // 2.4375
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // id=2: x = (x − 1.5)/(1 + 1.5x)
        b.add(new WasmInstr.Const(0, 2));
        set(b, 10);
        get(b, 0);
        b.add(new WasmInstr.Const(1.5));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Const(1.5));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // id=3: x = −1/x (o neg NO QUOCIENTE)
        b.add(new WasmInstr.Const(0, 3));
        set(b, 10);
        b.add(new WasmInstr.Const(1.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        set(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // z = x²; w = z²
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2);
        get(b, 2);
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 3);
        // s1 = z·(aT0 + w·(aT2 + w·(aT4 + w·(aT6 + w·(aT8 + w·aT10))))) —
        // o Horner FUSIONADO (a lei da rodada 28: UMA forma, nunca a
        // passo-a-passo ao contrário)
        b.add(new WasmInstr.Const(AT_0));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_2));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_4));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_6));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_8));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_10));
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
        set(b, 4);
        // s2 = w·(aT1 + w·(aT3 + w·(aT5 + w·(aT7 + w·aT9))))
        b.add(new WasmInstr.Const(AT_1));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_3));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_5));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_7));
        get(b, 3);
        b.add(new WasmInstr.Const(AT_9));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 3);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 5);
        // o final: small → x − x·(s1+s2); senão z = h − ((x·(s1+s2) − l) − x)
        // com o sinal do neg no fim
        get(b, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        get(b, 0);                                               // o x DUAS vezes: o minuendo E o fator (x − x·(s1+s2))
        get(b, 4);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // x − x·(s1+s2)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // h/l pela árvore do id (os ifs VOID gravam os locais — nunca tabela)
        get(b, 10);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(ATAN_HI_0));
        set(b, 6);
        b.add(new WasmInstr.Const(ATAN_LO_0));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 10);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(ATAN_HI_1));
        set(b, 6);
        b.add(new WasmInstr.Const(ATAN_LO_1));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 10);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(ATAN_HI_2));
        set(b, 6);
        b.add(new WasmInstr.Const(ATAN_LO_2));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(ATAN_HI_3));
        set(b, 6);
        b.add(new WasmInstr.Const(ATAN_LO_3));
        set(b, 7);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // z = h − ((x·(s1+s2) − l) − x) — o h PRIMEIRO (o minuendo externo)
        get(b, 6);
        get(b, 0);
        get(b, 4);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // x·(s1+s2) − l
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // (…) − x
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));            // h − (…)
        set(b, 2);
        // neg ? −z : z — o neg NO VALOR (0x9a, o −z literal do fdlibm)
        get(b, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));    // o END do if do neg
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));    // o END do if do small (o atan NÃO tem block externo — o 0x0b do serializer fecha a func)
        return new WasmFunc("kof_math_atan", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7f, 0x7f, 0x7f), b);
    }
}

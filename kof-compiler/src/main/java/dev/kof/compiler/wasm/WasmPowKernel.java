package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-POW-1 (01/11, #776): o __ieee754_pow fdlibm — o DRIVER. A ORDEM
 * DOS CASOS = a do {@code FdLibm.Pow} do JDK (o src.zip), verbatim:
 * y==0 → 1 (o ±0 do y é f64.eq — o Java trata ±0 y igual); o NaN (x+y);
 * y==2 → x*x; y==0.5 → sqrt(x+0.0) (o +0.0 converte o −0.0 — e o
 * x == −INF cai ATRAVÉS para o caminho principal, o comentário do C);
 * y==±1; |y|==INF (a tabela ±INF/±1); os bits hx/ix; o y_is_int (o
 * trunc para i64 é EXATO na faixa [1, 2^53) — sem trap); os especiais
 * de x (±0/±INF/±1 com o 1/z e o (−1)^não-int NaN); o gate NaN
 * (n|y_is_int)==0; o s do (−v)^ímpar; e o if/else final huge-y vs o
 * caminho principal (WasmPowMain: o log2 + a cauda + o scalb). O
 * s*0.0 = ±0.0 e o s*INF = ±INF saem das duas MULs (o f64.mul
 * propaga o sinal do zero — a semântica IEEE que o fdlibm usa).
 */
final class WasmPowKernel {

    private WasmPowKernel() {}

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    /** kof_math_pow(x, y) -> f64: o driver (os params 0=x 1=y; locals
     *  2=y_abs 3=x_abs 4=hx 5=ix 6=y_is_int 7=n (i32) 8=s (f64) 9=z
     *  (f64) 10=l (i64)). */
    static WasmFunc powFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // y == ±0 → 1.0 (o Java: pow(x, ±0) = 1)
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o NaN propaga: x + y (o MESMO bits do fdlibm)
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0x62, "f64.ne"));
        get(b, 1);
        get(b, 1);
        b.add(new WasmInstr.Simple(0x62, "f64.ne"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // y_abs/x_abs
        get(b, 1);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 2);
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 3);
        // y == 2 → x*x
        get(b, 1);
        b.add(new WasmInstr.Const(2.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // y == 0.5 && x >= -MAX → sqrt(x + 0.0) (o −INF cai através)
        get(b, 1);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        get(b, 0);
        b.add(new WasmInstr.Const(-Double.MAX_VALUE));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0x9f, "f64.sqrt"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // |y| == 1 → y==1 ? x : 1/x
        get(b, 2);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(1.0));
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // |y| == INF: ±1 → NaN; |x|>1 → (y≥0? y : 0); |x|<1 → (y<0? −y : 0)
        get(b, 2);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 1);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 3);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        // os value-ifs ALIMENTAM o resultado do if externo — o return
        // DENTRO do braço esvazia o frame do resultado (a lei roundTo:
        // o ÚNICO return vem DEPOIS do END do if externo)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // hx = HI(x); ix = hx & 0x7fffffff
        get(b, 0);
        b.add(new WasmInstr.Call("kof_pow_hi"));
        set(b, 4);
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 5);
        // y_is_int: hx<0 → (|y|≥2^53 → 2) senão (|y|≥1 → o trunc exato → 2-(l&1))
        b.add(new WasmInstr.Const(0, 0));
        set(b, 6);
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(0x1.0p53));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 2));
        set(b, 6);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xb0, "i64.trunc_f64_s"));
        set(b, 10);
        get(b, 10);
        b.add(new WasmInstr.Simple(0xb9, "f64.convert_i64_s"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 10);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, 7);
        b.add(new WasmInstr.Const(0, 2));
        get(b, 7);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 6);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40)); // fecha o hx<0 (o 4º frame)
        // os especiais de x: ±0/±INF/±1 — z = x_abs; y<0 → 1/z; hx<0 → as regras
        get(b, 3);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        get(b, 3);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        get(b, 3);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 3);
        set(b, 9);
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(1.0));
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 4);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        // ((ix - 0x3ff00000) | y_is_int) == 0 → (-1)^não-int é NaN
        get(b, 5);
        b.add(new WasmInstr.Const(0, 0x3ff00000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        get(b, 6);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 9);
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 9);
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // y_is_int == 1 (o ímpar) → z = -1.0 * z (o −0.0 sai da MUL)
        get(b, 6);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(-1.0));
        get(b, 9);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        get(b, 9);
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // n = (hx>>31) + 1 (o gate do sinal de x)
        get(b, 4);
        b.add(new WasmInstr.Const(0, 31));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 7);
        // (n | y_is_int) == 0 → (x-x)/(x-x) é NaN (x<0, y não-inteiro)
        get(b, 7);
        get(b, 6);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // s = 1.0; (n | (y_is_int-1)) == 0 → s = -1.0 (o (−v)^ímpar)
        b.add(new WasmInstr.Const(1.0));
        set(b, 8);
        get(b, 7);
        get(b, 6);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(-1.0));
        set(b, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o if/else final: |y| > ~2^31 → o huge-y; senão o caminho principal
        get(b, 2);
        b.add(new WasmInstr.Const(0x1.00000_ffff_ffffp31));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        get(b, 1);
        get(b, 8);
        b.add(new WasmInstr.Call("kof_pow_hugey"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 0);
        get(b, 1);
        get(b, 8);
        b.add(new WasmInstr.Call("kof_pow_main"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_pow", List.of(0x7c, 0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7f, 0x7f, 0x7f, 0x7f, 0x7c, 0x7c, 0x7e), b);
    }

    /** kof_pow_hugey(x, y, s) -> f64: o |y| > ~2^31 — os gates do
     *  over/underflow (o x longe de 1) e o log perto de 1 (t1/t2 → a
     *  cauda). Params: 0=x 1=y 2=s. Locals: 3=x_abs 4=t 5=w 6=u 7=v
     *  8=t1 9=t2 (f64). */
    static WasmFunc hugeyFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        set(b, 3);
        // |x| < ~0.99999952 → (y<0 ? s*INF : s*0)
        get(b, 3);
        b.add(new WasmInstr.Const(0x1.fffff_0000_0000p-1));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // |x| > ~1.0 → (y>0 ? s*INF : s*0)
        get(b, 3);
        b.add(new WasmInstr.Const(0x1.00000_ffff_ffffp0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // o log perto de 1: t = x_abs-1 (20 zeros); w/u/v; t1 = LO(u+v); t2
        get(b, 3);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 4);
        // w = (t*t)*(0.5 - t*(0.3333333333333333333333 - t*0.25))
        // A pilha: o sub emite BAIXO − TOPO — o minuend entra primeiro
        // (1/3 − t/4 e 0.5 − P; a ordem textual do fdlibm produzia w NEGADO
        // → v = tL + wI → a soma da série errava 2w·INV_LN2 — a quinta
        // instância da classe inverted-sub)
        get(b, 4);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Const(0.3333333333333333333333));
        get(b, 4);
        b.add(new WasmInstr.Const(0.25));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 5);
        // u = INV_LN2_H*t; v = t*INV_LN2_L - w*INV_LN2
        get(b, 4);
        b.add(new WasmInstr.Const(0x1.715476p0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 6);
        get(b, 4);
        b.add(new WasmInstr.Const(0x1.4ae0_bf85_ddf44p-26));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 5);
        b.add(new WasmInstr.Const(0x1.7154_7652_b82fep0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 7);
        // t1 = __LO(u+v, 0); t2 = v - (t1 - u) → a cauda
        get(b, 6);
        get(b, 7);
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Call("kof_pow_lo0"));
        set(b, 8);
        get(b, 7);
        get(b, 8);
        get(b, 6);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        set(b, 9);
        get(b, 8);
        get(b, 9);
        get(b, 1);
        get(b, 2);
        b.add(new WasmInstr.Call("kof_pow_tail"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_pow_hugey", List.of(0x7c, 0x7c, 0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c), b);
    }
}

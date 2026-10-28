package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-ATAN2-1 (28/10, #776): o __ieee754_atan2 fdlibm (o FdLibm.Atan2
 * do PRÓPRIO JDK — tiny/pi_o_4/pi_o_2/pi_lo verbatim do src.zip) como
 * func WASM mão-emitida. REUSA o func kof_math_atan (WasmAtanKernel, JÁ
 * registrado) via CALL — o atan(y) do x == 1.0 exato e o z =
 * atan(|y/x|) do caminho principal (a lei da emissão: o Call empilha o
 * args no site). A ORDEM DOS PARAMS = a do fdlibm compute(y, x) = a
 * ordem do call do IR (o math.atan2(y, x) do Kof) — o param 0 = Y, o
 * param 1 = X (a rodada 33 pagou o swap: o func nasceu (x, y) e TODA a
 * saída veio espelhada). A ORDEM DOS CASOS = a do fdlibm: o NaN (x+y),
 * o x == 1.0, o m, o y = ±0, o x = ±0, o x INF (a tabela dos
 * quadrantes ±INF), o y INF, o k = a diferença dos expoentes (os
 * hi-words via o reinterpret/shr_u/wrap provado) com as saídas
 * constantes do k extremo, e o switch final do m. O m vem dos BITS (o
 * shr_s 63 do i64), NUNCA do compare f64 — o −0.0 tem o bit de sinal e
 * NÃO tem o compare (a face atan2(±0, −x) = ±π). AS LEIS PRÉ-
 * APLICADAS (21-32): os counts de shift I64 para o i64 (o wasm 3.0),
 * os i32 counts para o i32; a decl dos locals = SÓ os NÃO-param (os
 * params ocupam os índices baixos — a lei da 31 refinada pela 33: o z
 * f64 + o by/bx i64 + o m/k/iys/ixs i32, os índices a partir do 2); o
 * minuendo do sub EMPILHADO primeiro; os value-ifs produzem o
 * resultado da func.
 */
final class WasmAtan2Kernel {

    private WasmAtan2Kernel() {}

    private static final double TINY = 1.0e-300;
    private static final double PI_O_4 = 0x1.921fb54442d18p-1;
    private static final double PI_O_2 = 0x1.921fb54442d18p0;
    private static final double PI_LO = 0x1.1a62633145c07p-53;
    private static final double PI = 0x1.921fb54442d18p1;

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    // o bit de sinal (0/1) de um double via os BITS (shr_s 63 → wrap → and 1)
    private static void emitSignBit(List<WasmInstr> b, int bitsLocal) {
        get(b, bitsLocal);
        b.add(new WasmInstr.Const(1, 63L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
    }

    // o < 0 de um double via o BIT puro (o wrap sem o and 1 — o −1 ou o 0)
    private static void emitIsNegative(List<WasmInstr> b, int bitsLocal) {
        get(b, bitsLocal);
        b.add(new WasmInstr.Const(1, 63L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
    }

    static WasmFunc atan2Func() {
        var b = new java.util.ArrayList<WasmInstr>();
        // locals: 0=y 1=x 2=z (f64)  3=by 4=bx (i64)  5=m 6=k 7=iys 8=ixs (i32)
        // o NaN: x ou y NaN → x + y (o MESMO bits do fdlibm; simétrico)
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
        // by (o param 0 = Y) / bx (o param 1 = X)
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 3);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 4);
        // x == 1.0 EXATO (+1.0 só) → atan(y) via o CALL (o arg = o param 0)
        get(b, 1);
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Call("kof_math_atan"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // m = sign(y) | sign(x)·2 — dos BITS (o −0.0 tem bit, não compare)
        emitSignBit(b, 3);
        emitSignBit(b, 4);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 5);
        // y = ±0 (o abs == 0.0): m 0/1 → y (o ±0); m 2 → π+tiny; m 3 → −π−tiny
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-PI));
        b.add(new WasmInstr.Const(-TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(PI));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // x = ±0 (o abs == 0.0): o sinal do y NOS BITS → ±π/2 ∓ tiny
        get(b, 1);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        emitIsNegative(b, 3);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-PI_O_2));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(PI_O_2));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // x INF (o NaN já saiu): y INF → a tabela dos quadrantes; senão ±0/±π
        get(b, 1);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-PI_O_4));
        b.add(new WasmInstr.Const(-TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(3.0));
        b.add(new WasmInstr.Const(PI_O_4));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-3.0));
        b.add(new WasmInstr.Const(PI_O_4));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(PI_O_4));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(PI));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-PI));
        b.add(new WasmInstr.Const(-TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // y INF (o x finito): o sinal do y nos BITS → ±π/2 ∓ tiny
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        emitIsNegative(b, 3);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(-PI_O_2));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Const(PI_O_2));
        b.add(new WasmInstr.Const(TINY));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o principal: o iys do by (o local 3), o ixs do bx (o local 4)
        get(b, 3);
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 7);
        get(b, 4);
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 0x7fffffff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 8);
        get(b, 7);
        get(b, 8);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        set(b, 6);
        // z: |y/x| > 2^60 → a constante; hx < 0 && k < −60 → 0.0; senão o CALL
        get(b, 6);
        b.add(new WasmInstr.Const(0, 60));
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(PI_O_2));
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Const(PI_LO));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        set(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        emitIsNegative(b, 4);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        get(b, 6);
        b.add(new WasmInstr.Const(0, -60));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0.0));
        set(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 0);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Call("kof_math_atan"));
        set(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o switch final do m: z / −z / π−(z−lo) / (z−lo)−π
        get(b, 5);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 5);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(PI));
        get(b, 2);
        b.add(new WasmInstr.Const(PI_LO));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        b.add(new WasmInstr.Const(PI_LO));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(PI));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_atan2", List.of(0x7c, 0x7c), List.of(0x7c),
                List.of(0x7c, 0x7e, 0x7e, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }
}

package dev.kof.compiler.wasm;

import java.util.List;

/**
 * MATH-POW-1 (01/11, #776): o __ieee754_pow fdlibm (o {@code FdLibm.Pow}
 * do PRÓPRIO JDK — os literais hex verbatim do src.zip) como funcs WASM
 * mão-emitida — a ÚLTIMA fatia libm da família math. Este arquivo = os HELPERS DE BITS: o __HI/__LO (kof_pow_hi/lo), o __HI(x, hi) (kof_pow_withhi), o __LO(x, 0) (kof_pow_lo0) e o Math.scalb EXATO por bits para o output subnormal (kof_pow_scalb). O driver/casos especiais fica em WasmPowKernel, o caminho principal em WasmPowMain, a cauda em WasmPowTail.
 *
 * <p>AS LEIS DA EMISSÃO (as mesmas do atan2): os params ocupam os
 * índices baixos (a decl dos locals = SÓ os não-param); os counts de
 * shift I32 para o i32 e I64 para o i64 (o wasm 3.0); o minuendo do
 * SUB empilhado primeiro (a classe inverted-sub); os value-ifs (0x7c)
 * produzem o resultado; os bits de JAVA INT = a semântica do wasm i32
 * (o wrap do count mod 32, o wrap aritmético do add/sub) — o j−0x40900000
 * dos gates e o n<<20 da construção 2^k dependem DELE e casam.
 */

final class WasmPowBits {

    private WasmPowBits() {}

    private static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, ""));
    }

    private static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, ""));
    }

    /** kof_pow_hi(x) -> i32: a palavra ALTA (o __HI(x) — o sinal
     *  incluído; o shr_s 32 + o wrap reproduzem o (int)(bits >> 32)). */
    static WasmFunc hiFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        return new WasmFunc("kof_pow_hi", List.of(0x7c), List.of(0x7f), List.of(), b);
    }

    /** kof_pow_lo(x) -> i32: a palavra BAIXA (o __LO(x) = o (int)bits). */
    static WasmFunc loFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        return new WasmFunc("kof_pow_lo", List.of(0x7c), List.of(0x7f), List.of(), b);
    }

    /** kof_pow_withhi(x, hi) -> f64: o __HI(x, hi) — a palavra alta
     *  SUBSTITUÍDA: o bits(x) E 0xFFFFFFFF preserva SÓ a palavra baixa
     *  (o OR sem a máscara deixa o expoente VELHO — o 0x3FF0|0x4000 =
     *  0x7FF0 = INFINITY, o bug dos 6 faces inteiros). */
    static WasmFunc withHiFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 0x00000000FFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_withhi", List.of(0x7c, 0x7f), List.of(0x7c), List.of(), b);
    }

    /** kof_pow_lo0(x) -> f64: o __LO(x, 0) — a palavra baixa ZERADA (a
     *  dança reinterpret/AND/reinterpret provada no acos). */
    static WasmFunc lo0Func() {
        var b = new java.util.ArrayList<WasmInstr>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 0xFFFFFFFF00000000L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_lo0", List.of(0x7c), List.of(0x7c), List.of(), b);
    }

    /** kof_pow_scalb(z, n) -> f64: o Math.scalb EXATO por bits para o
     *  output SUBNORMAL da cauda (o caller garante t = e+n ≤ 0 e z
     *  normal em [0.5, 2)): shift = 1-(e+n); o q = sig >> shift com o
     *  round-half-even; o carry do q = 2^52 É o bit-pattern do menor
     *  normal correto (o IEEE). O shift ≥ 54 → ±0 (o sinal preservado). */
    static WasmFunc scalbFunc() {
        var b = new java.util.ArrayList<WasmInstr>();
        // params: 0=z (f64) 1=n (i64); locals: 2=bits 3=hi 5=frac 6=sig
        //         7=t (i64) 4=e 8=shift (i64 — os shifts de sig são i64)
        //         9=q 10=rem 11=half (i64)
        get(b, 0);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 2);
        get(b, 2);
        b.add(new WasmInstr.Const(1, 32L));
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, 3);
        get(b, 3);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x75, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x7ff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 4);
        get(b, 2);
        b.add(new WasmInstr.Const(1, 0x000FFFFFFFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 5);
        get(b, 5);
        b.add(new WasmInstr.Const(1, 4503599627370496L));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        set(b, 6);
        // t = e + n
        get(b, 4);
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 7);
        // o caminho normal (t > 0): o campo de expoente substituído
        get(b, 7);
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(1, 0x800FFFFFFFFFFFFFL));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 7);
        b.add(new WasmInstr.Const(1, 52L));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // shift = 1 - t (≥ 1 no contrato, i64 — o q/rem o usam como shift;
        // o ORDEM é 1 − t: o t−1 dava shift NEGATIVO e o shr contava mod 64
        // (−52 mod 64 = 12 → q = 2^40 = o 2^−1034 dos probes))
        b.add(new WasmInstr.Const(1, 1L));
        get(b, 7);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 8);
        // shift ≥ 54 → ±0
        get(b, 8);
        b.add(new WasmInstr.Const(1, 54L));
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 2);
        b.add(new WasmInstr.Const(1, Long.MIN_VALUE));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Simple(0x0f, "return"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // q = sig >> shift (sig > 0); rem = sig - (q << shift)
        get(b, 6);
        get(b, 8);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        set(b, 9);
        get(b, 6);
        get(b, 9);
        get(b, 8);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 10);
        // half = 1 << (shift-1)
        b.add(new WasmInstr.Const(1, 1L));
        get(b, 8);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        set(b, 11);
        // round-half-even: rem > half || (rem == half && (q&1) == 1)
        get(b, 10);
        get(b, 11);
        b.add(new WasmInstr.Simple(0x5a, "i64.gt_s"));
        get(b, 10);
        get(b, 11);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        get(b, 9);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        get(b, 9);
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // out = sign | q (o carry do 2^52 = o menor normal correto)
        get(b, 2);
        b.add(new WasmInstr.Const(1, Long.MIN_VALUE));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        get(b, 9);
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        return new WasmFunc("kof_pow_scalb", List.of(0x7c, 0x7e), List.of(0x7c),
                List.of(0x7e, 0x7f, 0x7f, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e, 0x7e), b);
    }

}

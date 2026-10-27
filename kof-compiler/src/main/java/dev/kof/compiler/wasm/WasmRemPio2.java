package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fase E (27/10, #776 — split check_500): o laço MÉDIO do RemPio2 (|x| ≤
 * 2^19·π/2) + a variante iy=1 do kernel_sin, extraídos do
 * {@code WasmSinKernel} (625 ≥ 600 derrubava o gate CI). Mapeado do
 * FdLibm.RemPio2 do PRÓPRIO JDK.
 */
final class WasmRemPio2 {

    private WasmRemPio2() {
    }

    // as constantes do kernel_sin (a variante smalltail as usa) + o RemPio2
    private static final double SIN_S1 = -0x1.5555555555549p-3;
    private static final double SIN_S2 = 0x1.111111110f8a6p-7;
    private static final double SIN_S3 = -0x1.a01a019c161d5p-13;
    private static final double SIN_S4 = 0x1.71de357b1fe7dp-19;
    private static final double SIN_S5 = -0x1.ae5e68a2b9cebp-26;
    private static final double SIN_S6 = 0x1.5d93a5acfd57cp-33;
    private static final double INV_PIO2 = 0x1.45f306dc9c883p-1;
    private static final double PIO2_1 = 0x1.921fb544p0;
    private static final double PIO2_1T = 0x1.0b4611a626331p-34;
    private static final double PIO2_2 = 0x1.0b4611a6p-34;
    private static final double PIO2_2T = 0x1.3198a2e037073p-69;
    private static final double PIO2_3 = 0x1.3198a2ep-69;
    private static final double PIO2_3T = 0x1.b839a252049c1p-104;
    // a tabela npio2_hw do fdlibm (os hi-words de (k−1)·π/2)
    private static final int[] NPIO2_HW = {
            0x3FF921FB, 0x400921FB, 0x4012D97C, 0x401921FB, 0x401F6A7A, 0x4022D97C,
            0x4025FDBB, 0x402921FB, 0x402C463A, 0x402F6A7A, 0x4031475C, 0x4032D97C,
            0x40346B9C, 0x4035FDBB, 0x40378FDB, 0x403921FB, 0x403AB41B, 0x403C463A,
            0x403DD85A, 0x403F6A7A, 0x40407E4C, 0x4041475C, 0x4042106C, 0x4042D97C,
            0x4043A28C, 0x40446B9C, 0x404534AC, 0x4045FDBB, 0x4046C6CB, 0x40478FDB,
            0x404858EB, 0x404921FB,
    };

    private static List<WasmInstr> get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, "l" + i));
        return b;
    }

    /** O kernel_sin VARIANTE iy=1 (com a cauda y): x − ((z·(0.5y − v·r)
     *  − y) − v·S1), r = S2 + z·(S3 + z·(S4 + z·(S5 + z·S6))) — SEM o S1
     *  (ele entra separado no retorno). Locais: 2=z, 3=v, 4=r. */
    static WasmFunc smallTailFunc() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "z"));     // z = x²
        get(b, 2);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "v"));     // v = z·x
        // r = S2 + z·(S3 + z·(S4 + z·(S5 + z·S6)))
        b.add(new WasmInstr.Const(SIN_S6));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S5));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S4));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S3));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S2));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "r"));     // r
        // x − ((z·(0.5y − v·r) − y) − v·S1) — o x EMPILE-SE PRIMEIRO (a
        // rodada 22 perdeu este get: o sub final ficava sem o minuendo)
        get(b, 0);
        get(b, 2);
        get(b, 1);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 3);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "r"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // 0.5y − v·r
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));                // z·(0.5y − v·r)
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // (…) − y
        get(b, 3);
        b.add(new WasmInstr.Const(SIN_S1));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));                // v·S1
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // (…) − v·S1
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // x − (…)
        return new WasmFunc("kof.sin.smalltail", List.of(0x7c, 0x7c), List.of(0x7c), List.of(0x7c, 0x7c, 0x7c, 0x7c), b);
    }

    /** O laço MÉDIO (|x| ≤ 2^19·π/2): a redução de Cody-Waite com o caminho
     *  rápido (n < 32 e a tabela npio2_hw) e as iterações de bit-peek (a
     *  2ª/3ª rodada). O OCTANTE n&3 → 0:+small(y0,y1) / 1:cos / 2:−small /
     *  3:−cos (o small com a cauda — a variante iy=1). Locais:
     *  1=t, 2=fn, 3=r, 4=w, 5=y0, 6=y1, 7=n(i32), 8=ix(i32), 9=j(i32),
     *  10=i(i32), 11=scratch(i32). */
    static WasmFunc mediumFunc() {
        List<WasmInstr> b = new ArrayList<>();
        // t = |x|; ix = o hi-word de |x|
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "ix"));
        // n = (int)(t·invpio2 + 0.5)
        get(b, 1);
        b.add(new WasmInstr.Const(INV_PIO2));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Simple(0xaa, "i32.trunc_f64_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "n"));
        // fn = (double)n
        get(b, 7);
        b.add(new WasmInstr.Simple(0xb7, "f64.convert_i32_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "fn"));
        // r = t − fn·pio2_1
        get(b, 1);
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_1));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "r"));
        // w = fn·pio2_1t
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_1T));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "w"));
        // o caminho RÁPIDO: n < 32 && ix != NPIO2_HW[n−1] → y0 = r − w
        // a cadeia gerada: eq(n,1)&ne(ix,HW[0]) | eq(n,2)&ne(ix,HW[1]) …
        b.add(new WasmInstr.Const(0, 0));                            // o acumulador do quick (I32 0 — o Const(0) era o ctor DOUBLE!)
        for (int k = 1; k <= 32; k++) {
            get(b, 7);
            b.add(new WasmInstr.Const(0, k));
            b.add(new WasmInstr.Simple(0x46, "i32.eq"));             // n == k
            b.add(new WasmInstr.Const(0, NPIO2_HW[k - 1]));
            get(b, 8);
            b.add(new WasmInstr.Simple(0x46, "i32.eq"));
            b.add(new WasmInstr.Simple(0x45, "i32.eqz"));            // ix != HW[k−1]
            b.add(new WasmInstr.Simple(0x71, "i32.and"));            // ambos
            b.add(new WasmInstr.Simple(0x72, "i32.or"));             // acumula
        }
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // VOID: quick
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "y0"));    // y0 = r − w
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // as iterações de bit-peek: j = ix >> 20
        get(b, 8);
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 9, "j"));
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "y0"));    // y0 = r − w
        // i = j − ((HI(y0) >> 20) & 0x7ff)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "j"));
        get(b, 5);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x7ff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 10, "i"));    // i
        // i > 16 → a 2ª rodada
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "i"));
        b.add(new WasmInstr.Const(0, 16));
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // VOID
        get(b, 3);
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));     // t = r
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_2));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "w"));     // w = fn·pio2_2
        get(b, 1);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "r"));     // r = t − w
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_2T));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 1);
        get(b, 3);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "w"));     // w = fn·pio2_2t − ((t−r)−w)
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "y0"));    // y0 = r − w
        // i = j − ((HI(y0) >> 20) & 0x7ff)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "j"));
        get(b, 5);
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 20));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_s"));
        b.add(new WasmInstr.Const(0, 0x7ff));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 10, "i"));
        // i > 49 → a 3ª rodada
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "i"));
        b.add(new WasmInstr.Const(0, 49));
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // VOID
        get(b, 3);
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));     // t = r
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_3));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "w"));     // w = fn·pio2_3
        get(b, 1);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "r"));     // r = t − w
        get(b, 2);
        b.add(new WasmInstr.Const(PIO2_3T));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 1);
        get(b, 3);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "w"));     // w = fn·pio2_3t − ((t−r)−w)
        get(b, 3);
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "y0"));    // y0 = r − w
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));  // a 3ª
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));  // a 2ª
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));  // o quick
        // y1 = (r − y0) − w
        get(b, 3);
        get(b, 5);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 4);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "y1"));
        // hx < 0 → y0 = −y0; y1 = −y1 (e o n vira −n — o OCTANTE via n&3: −n&3 ≠ n&3?! o
        // Java: o switch usa n&3 com n NEGATIVO — o complemento-2: −1&3=3 ✓ os MESMOS bits
        // se eu mantiver o n negativo. A negação dos y's e o n SEM flip: o octante
        // usa (hx<0 ? −n : n) & 3 = (hx<0 ? (−n)&3 : n&3) — EMITO: o n efetivo = hx<0 ? −n : n
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // VOID: a negação
        get(b, 5);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "y0"));
        get(b, 6);
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "y1"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o n efetivo (p/ o octante): hx<0 ? −n : n
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));                            // 0
        get(b, 7);                                                   // n
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));                // 0 − n = −n (o idiom eqz ZERAVA o n!)
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "n"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o OCTANTE n&3: 0:+smalltail(y0,y1) / 1:cos(y0,y1) / 2:−smalltail / 3:−cos
        // LEI: o n&3 salvo no scratch 11 — o primeiro eq o CONSUME e os casos
        // seguintes o re-empilham (o round-22: o eq consumia e os casos
        // seguintes ficavam sem o cond — 'nothing on stack')
        get(b, 7);
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));                // n&3 (o complemento-2 = o Java)
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 11, "oct"));
        get(b, 11);
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));   // case 0
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Call("kof.sin.smalltail"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 11);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));   // case 1
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Call("kof.cos.kernel"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 11);
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));   // case 2
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Call("kof.sin.smalltail"));
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c)); // case 3
        get(b, 5);
        get(b, 6);
        b.add(new WasmInstr.Call("kof.cos.kernel"));
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof.sin.medium", List.of(0x7c), List.of(0x7c),
                List.of(0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

}

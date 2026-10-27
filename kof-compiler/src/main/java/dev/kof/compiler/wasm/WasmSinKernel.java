package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fase E (27/10, #776 — split check_500): o {@code __kernel_sin} fdlibm da
 * FAIXA PEQUENA (|x| ≤ π/4) + o caso ESPECIAL n=±1 (|x| < 3π/4) + o
 * {@code __kernel_cos}. A FUNDAÇÃO MEDIDA: Math.sin == StrictMath.sin em
 * 29/29 entradas (o port fixa o oráculo byte a byte) MAS Math.cos deriva
 * 5/29 por 1 ulp (0.1, ±1000, 1e15) — a face cos paridade está MORTA
 * (veredito registrado). As constantes são as do FdLibm.Sin/Cos/RemPio2 DO
 * PRÓPRIO JDK (os literais hex preservam os bits — a tabela decimal da
 * memória tinha os expoentes TODOS deslocados e o F5 mediu o erro).
 * AS LEIS DA RODADA 21 (aplicadas ANTES da emissão): todo Call empilha os
 * args no site; if que só grava local é VOID 0x40; o count dos shifts i64 é
 * I64 com o shr_u real 0x89 e o extend 0xad antes do shl 0x85.
 */
final class WasmSinKernel {

    private WasmSinKernel() {
    }

    private static final double PIO4 = 0x1.921fb54442d18p-1;
    private static final double THREEPIO4 = 0x1.2d97c7f3321d2p1;  // 3π/4
    private static final double PIO2_1 = 0x1.921fb544p0;
    private static final double PIO2_1T = 0x1.0b4611a626331p-34;
    private static final double PIO2_2 = 0x1.0b4611a6p-34;
    private static final double PIO2_2T = 0x1.3198a2e037073p-69;
    private static final double SIN_S1 = -0x1.5555555555549p-3;
    private static final double SIN_S2 = 0x1.111111110f8a6p-7;
    private static final double SIN_S3 = -0x1.a01a019c161d5p-13;
    private static final double SIN_S4 = 0x1.71de357b1fe7dp-19;
    private static final double SIN_S5 = -0x1.ae5e68a2b9cebp-26;
    private static final double SIN_S6 = 0x1.5d93a5acfd57cp-33;
    private static final double COS_C1 = 0x1.555555555554cp-5;
    private static final double COS_C2 = -0x1.6c16c16c15177p-10;
    private static final double COS_C3 = 0x1.a01a019cb159p-16;
    private static final double COS_C4 = -0x1.27e4f809c52adp-22;
    private static final double COS_C5 = 0x1.1ee9ebdb4b1c4p-29;
    private static final double COS_C6 = -0x1.8fae9be8838d4p-37;
    // MATH-LIBM-3 (27/10): as constantes do RemPio2 (o FdLibm do JDK)
    private static final double INV_PIO2 = 0x1.45f306dc9c883p-1;
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

    /** A func {@code kof_math_sin}    /** A func {@code kof_math_sin} — o DESPACHO das três faixas. */
    static WasmFunc sinFunc() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));                // LEI: o abs NO X (o const depois — o abs pegaria a const)
        b.add(new WasmInstr.Const(PIO4));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);                                               // LEI: os args no site
        b.add(new WasmInstr.Call("kof.sin.small"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));                // LEI: o abs NO X
        b.add(new WasmInstr.Const(THREEPIO4));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);                                               // LEI: os args no site
        b.add(new WasmInstr.Call("kof.sin.special"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // faixa 3: |x| ≤ 2^19·π/2 → o laço MÉDIO (MATH-LIBM-3)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));            // LEI: o abs NO X
        b.add(new WasmInstr.Const(0x1.921fb54442d18p19));        // 2^19·π/2 (o bound do fdlibm)
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);                                               // LEI: os args no site
        b.add(new WasmInstr.Call("kof.sin.medium"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));        // a fronteira honesta (o caminho GRANDE é a fatia seguinte)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));  // fecha o if do médio
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));  // fecha o if do especial
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_sin", List.of(0x7c), List.of(0x7c), List.of(), b);
    }

    /** O kernel PEQUENO: sin(x) = x + v·(S1 + z·r) — o grau 13. */
    static WasmFunc smallFunc() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "x2"));
        b.add(new WasmInstr.Const(SIN_S6));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S5));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S4));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S3));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S2));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S1));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));
        get(b, 0);
        get(b, 0);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        return new WasmFunc("kof.sin.small", List.of(0x7c), List.of(0x7c), List.of(0x7c, 0x7c), b);
    }

    /** O caso ESPECIAL n=±1 (|x| < 3π/4). O primeiro if é VOID 0x40 (só
     *  grava z/y0/y1); o octante é RESULT f64 com o neg SÓ no braço
     *  negativo. Locais: 1=z, 2=y0, 3=y1. */
    static WasmFunc specialFunc() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // LEI: VOID
        // ---- POSITIVO: n=+1 ----
        get(b, 0);
        b.add(new WasmInstr.Const(PIO2_1));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "z"));        // z = x − pio2_1
        get(b, 0);                                                       // LEI: os args [x, z]
        get(b, 1);
        b.add(new WasmInstr.Call("kof.sin.nearhalf"));                   // z −= pio2_2 se perto
        get(b, 1);
        b.add(new WasmInstr.Const(PIO2_1T));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "y0"));        // y0 = z − pio2_1t
        get(b, 1);
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(PIO2_1T));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "y1"));        // y1 = (z−y0) − pio2_1t
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        // ---- NEGATIVO: n=−1 ----
        get(b, 0);
        b.add(new WasmInstr.Const(PIO2_1));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "z"));        // z = x + pio2_1
        get(b, 0);                                                       // LEI: os args [x, z]
        get(b, 1);
        b.add(new WasmInstr.Call("kof.sin.nearhalf"));                   // z += pio2_2 se perto
        get(b, 1);
        b.add(new WasmInstr.Const(PIO2_1T));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "y0"));        // y0 = z + pio2_1t
        get(b, 1);
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Const(PIO2_1T));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "y1"));        // y1 = (z−y0) + pio2_1t
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // o octante: RESULT f64 — pos → cos(y0,y1); neg → −cos(y0,y1)
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2);
        get(b, 3);
        b.add(new WasmInstr.Call("kof.cos.kernel"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 2);
        get(b, 3);
        b.add(new WasmInstr.Call("kof.cos.kernel"));
        b.add(new WasmInstr.Simple(0x9a, "f64.neg"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof.sin.special", List.of(0x7c), List.of(0x7c), List.of(0x7c, 0x7c, 0x7c), b);
    }

    /** O branch perto-de-π/2: o hi-word de |x| == 0x3ff921fb → z ∓= pio2_2.
     *  Os params [x, z]; o count do shift I64 (wasm 3.0) e o shr_u real 0x89. */
    static WasmFunc nearHalfFunc() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));                           // LEI: o count I64
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));              // o shr_u REAL (0x88 = shr_s)
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 0x3ff921fb));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // LEI: VOID
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // LEI: VOID
        get(b, 1);
        b.add(new WasmInstr.Const(PIO2_2));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "z"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 1);
        b.add(new WasmInstr.Const(PIO2_2));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "z"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        return new WasmFunc("kof.sin.nearhalf", List.of(0x7c, 0x7c), List.of(), List.of(), b);
    }

    /** O __kernel_cos fdlibm: as DUAS formas de retorno (|x| < 0.3 direto;
     *  ≥ 0.3 o qx bit-hack com o extend 0xad e o shl 0x85). Locais:
     *  2=z, 3=r, 4=qx. */
    static WasmFunc cosFunc() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "z"));     // z = x²
        b.add(new WasmInstr.Const(COS_C6));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(COS_C5));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(COS_C4));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(COS_C3));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(COS_C2));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(COS_C1));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        get(b, 2);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "r"));     // r = z·poly
        // r = z·r − x·y (a cauda do y)
        get(b, 2);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "r"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        get(b, 0);
        get(b, 1);
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "r"));
        // |x| < 0.3 → 1 − (0.5z − r)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(0.3));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Const(1.0));
        get(b, 2);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "r"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // ≥ 0.3: o qx bit-hack (o if é VOID 0x40 — LEI)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(0.78125));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));   // LEI: VOID
        b.add(new WasmInstr.Const(0.28125));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "qx"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));                           // LEI: o count I64
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));              // o shr_u REAL
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 0x200000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));       // LEI: o valor volta a I64
        b.add(new WasmInstr.Const(1, 32));                           // LEI: o count I64
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));                // LEI: 0x85 (0x86 = rotl)
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "qx"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // a − (hz − r), hz = 0.5z − qx, a = 1 − qx
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "qx"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // a
        get(b, 2);
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "qx"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // hz
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "r"));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // hz − r
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));                // a − (hz − r)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof.cos.kernel", List.of(0x7c, 0x7c), List.of(0x7c), List.of(0x7c, 0x7c, 0x7c), b);
    }
}

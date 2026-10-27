package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fase E (27/10, #776 — split check_500): o {@code __kernel_sin} fdlibm da
 * FAIXA PEQUENA (|x| ≤ π/4), extraído do {@code WasmMathRuntime} (602 ≥ 600
 * derrubava o gate CI). A FUNDAÇÃO MEDIDA: Math.sin == StrictMath.sin em
 * 29/29 entradas (o port fixa o oráculo byte a byte) MAS Math.cos deriva
 * 5/29 por 1 ulp (0.1, ±1000, 1e15) — a face cos paridade está MORTA
 * (veredito registrado; o cos segue recusado no WASI). As constantes são as
 * do FdLibm.Sin DO PRÓPRIO JDK (os literais hex — a tabela decimal da
 * memória tinha os expoentes TODOS deslocados e o F5 mediu o erro).
 */
final class WasmSinKernel {

    private WasmSinKernel() {
    }

    // as constantes EXATAS do FdLibm.Sin do PRÓPRIO JDK (java/lang/
    // FdLibm.java — os literais hex preservam os bits)
    private static final double PIO4 = 0x1.921fb54442d18p-1;
    private static final double SIN_S1 = -0x1.5555555555549p-3;
    private static final double SIN_S2 = 0x1.111111110f8a6p-7;
    private static final double SIN_S3 = -0x1.a01a019c161d5p-13;
    private static final double SIN_S4 = 0x1.71de357b1fe7dp-19;
    private static final double SIN_S5 = -0x1.ae5e68a2b9cebp-26;
    private static final double SIN_S6 = 0x1.5d93a5acfd57cp-33;

    /** A func {@code kof_math_sin} — o kernel + o guard da faixa. */
    static WasmFunc sinFunc() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, null, 0x7c));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "x"));
        b.add(new WasmInstr.Const(PIO4));
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));                // |x|
        b.add(new WasmInstr.Simple(0x65, "f64.le"));                 // |x| <= π/4
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "x"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "x"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "x2"));    // x2 = x·x
        b.add(new WasmInstr.Const(SIN_S6));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = S6
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S5));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = s·x2 + S5
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S4));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = s·x2 + S4
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S3));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = s·x2 + S3
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S2));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = s·x2 + S2
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(SIN_S1));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "s"));     // s = s·x2 + S1
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "x"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "x"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "x2"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));                // [x, x·x2]  (v = x·x2)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "s"));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));                // [x, v·s]
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));                // x + v·s
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));            // a fronteira honesta (|x| > π/4)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_sin", List.of(0x7c), List.of(0x7c), List.of(0x7c, 0x7c), b);
    }
}

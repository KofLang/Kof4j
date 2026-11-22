package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * COS-1 (15/11 — lane issues/bugs/gaps): o DESPACHO do {@code FdLibm.Cos}
 * (o JDK src.zip verbatim): |x| ≤ π/4 → o {@code kof.cos.kernel(x, 0)}
 * já pousado (MATH-LIBM-1); Inf/NaN → {@code x−x}; |x| < 2^19·π/2 → o
 * laço MÉDIO {@code kof.cos.medium} (o gêmeo do sin — o mesmo Cody-Waite,
 * o OCTANTE do cos); além → o caminho GRANDE {@code kof.sin.large} (o
 * RemPio2 do scratch 928B — o kernel do rempi2 é COMUM: o despacho do
 * OCTANTE do cos é do DRIVER do sin grande, o {@code kof_math_cos} FINAL
 * re-despacha o n&3 retornado... NÃO — o fdlibm devolve o VALOR; o caminho
 * grande do cos = {@code kof.cos.large}, o GÊMEO do kof.sin.large com o
 * despacho FdLibm.Cos).
 *
 * <p>A LEI QUE SUPERA o veredito MORTO: MATH-POW-1 (01/11) — o port
 * fdlibm-EXATO é legal com as faces E2E = o subconjunto MEDIDO
 * Math==Strict + o diferencial Strict pinado (o cos deriva 9/52 — 0.1,
 * 2.3, ±1000, ±1193046, 2000000, ±1e15 medidos 15/11; essas faces ficam
 * FORA do oráculo Math e entram no diferencial pinado como o pow).
 */
final class WasmCosDriver {

    private WasmCosDriver() {
    }

    private static final double PIO4 = 0x1.921fb54442d18p-1;
    private static final double MEDIUM_BOUND = 0x1.921fb54442d18p19;  // 2^19·π/2

    private static List<WasmInstr> get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, "l" + i));
        return b;
    }

    /** {@code kof_math_cos(f64) -> f64} — a cadeia das 4 faixas. */
    static WasmFunc cosDriverFunc() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, null, 0x7c));
        // |x| ≤ π/4 → o kernel
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(PIO4));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Call("kof.cos.kernel"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // Inf/NaN → x−x (o ix >= 0x7ff00000 do fdlibm, via bits)
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 32));                           // LEI: o count I64
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, 0x7ff00000));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        get(b, 0);
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        // |x| ≤ 2^19·π/2 → o médio do cos; além → o grande do cos
        get(b, 0);
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(MEDIUM_BOUND));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Call("kof.cos.medium"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c));
        get(b, 0);
        b.add(new WasmInstr.Call("kof.cos.large"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));
        return new WasmFunc("kof_math_cos", List.of(0x7c), List.of(0x7c), List.of(), b);
    }
}

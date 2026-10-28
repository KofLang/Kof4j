package dev.kof.compiler.wasm;

import java.util.List;

import static dev.kof.compiler.wasm.WasmRemPio2Large.FQ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KFW;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KI;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KIH;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KK;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KN;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KQ0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.PIO2T;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Q;
import static dev.kof.compiler.wasm.WasmRemPio2Large.TWO24;
import static dev.kof.compiler.wasm.WasmRemPio2Large.TWON24;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Y0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Y1;
import static dev.kof.compiler.wasm.WasmRemPio2Large.blk;
import static dev.kof.compiler.wasm.WasmRemPio2Large.f64c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KBASE;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KE0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.get;
import static dev.kof.compiler.wasm.WasmRemPio2Large.i32c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.op;
import static dev.kof.compiler.wasm.WasmRemPio2Large.pow2a;
import static dev.kof.compiler.wasm.WasmRemPio2Large.pow2;
import static dev.kof.compiler.wasm.WasmRemPio2Large.set;

/**
 * MATH-LIBM-4 fatia B (28/10, #776): a CAUDA do
 * {@code __kernel_rem_pio2} — o chop dos termos zero (o laço descendente),
 * a reconversão {@code q[i] = fw·iq[i]} (o fw encolhendo por 2^-24), os
 * produtos {@code fq[jz−i] = Σ PIo2[k]·q[i+k]} e o empacotamento
 * prec=2 em {@code y[0]/y[1]} (o {@code ih} decide os sinais) + o retorno
 * {@code n &amp; 7}. Fase separada por responsabilidade (check_500).
 */
final class WasmRemPio2Tail {

    private WasmRemPio2Tail() {
    }

    static void emit(List<WasmInstr> b) {
        emitChop(b);
        emitQConvert(b);
        emitFq(b);
        emitY(b);
        // return n & 7
        get(b, KN);
        i32c(b, 7);
        op(b, 0x71, "i32.and");
    }

    /** if (z == 0) → jz−−/q0−−/descarta os termos zero; senão o split de
     *  2^24 do z (o jz cresce quando o chunk extra estoura). */
    private static void emitChop(List<WasmInstr> b) {
        get(b, KZ);
        f64c(b, 0.0);
        op(b, 0x61, "f64.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KJZ);
        get(b, KQ0);
        i32c(b, 24);
        op(b, 0x6b, "i32.sub");
        set(b, KQ0);
        blk(b, WasmInstr.Blocking.BLOCK, "ch1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "ch1l", 0x40);
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, WasmRemPio2Large.IQ));
        op(b, 0x45, "i32.eqz");
        op(b, 0x45, "i32.eqz");
        b.add(new WasmInstr.Branch("ch1x", 1, true));
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KJZ);
        get(b, KQ0);
        i32c(b, 24);
        op(b, 0x6b, "i32.sub");
        set(b, KQ0);
        b.add(new WasmInstr.Branch("ch1l", 0));
        blk(b, WasmInstr.Blocking.END, "ch1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "ch1x", 0x40);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        // z = z·2^(−q0)
        get(b, KZ);
        pow2(b, KQ0, 0);
        op(b, 0xa2, "f64.mul");
        set(b, KZ);
        get(b, KZ);
        f64c(b, TWO24);
        op(b, 0x66, "f64.ge");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        // fw = (int)(2^-24·z); iq[jz] = (int)(z − 2^24·fw); jz++; q0 += 24; iq[jz] = (int)fw
        get(b, KZ);
        f64c(b, TWON24);
        op(b, 0xa2, "f64.mul");
        op(b, 0xaa, "i32.trunc_f64_s");
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, KFW);
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KZ);
        f64c(b, TWO24);
        get(b, KFW);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        op(b, 0xaa, "i32.trunc_f64_s");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, WasmRemPio2Large.IQ));
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KJZ);
        get(b, KQ0);
        i32c(b, 24);
        op(b, 0x6a, "i32.add");
        set(b, KQ0);
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KFW);
        op(b, 0xaa, "i32.trunc_f64_s");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, WasmRemPio2Large.IQ));
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KZ);
        op(b, 0xaa, "i32.trunc_f64_s");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, WasmRemPio2Large.IQ));
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
    }

    /** fw = 2^q0; q[i] = fw·(double)iq[i]; fw *= 2^-24 — o laço descendente. */
    private static void emitQConvert(List<WasmInstr> b) {
        pow2a(b, KQ0);
        set(b, KFW);
        get(b, KJZ);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "cq1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "cq1l", 0x40);
        get(b, KI);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        b.add(new WasmInstr.Branch("cq1x", 1, true));
        get(b, KBASE);
        get(b, KI);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KFW);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, WasmRemPio2Large.IQ));
        op(b, 0xb7, "f64.convert_i32_s");
        op(b, 0xa2, "f64.mul");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, Q));
        get(b, KFW);
        f64c(b, TWON24);
        op(b, 0xa2, "f64.mul");
        set(b, KFW);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KI);
        b.add(new WasmInstr.Branch("cq1l", 0));
        blk(b, WasmInstr.Blocking.END, "cq1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "cq1x", 0x40);
    }

    /** fq[jz−i] = Σ_{k≤min(jp, jz−i)} PIo2[k]·q[i+k] (jp = 4). */
    private static void emitFq(List<WasmInstr> b) {
        get(b, KJZ);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "fp1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "fp1l", 0x40);
        get(b, KI);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        b.add(new WasmInstr.Branch("fp1x", 1, true));
        f64c(b, 0.0);
        set(b, KFW);
        i32c(b, 0);
        set(b, KK);
        blk(b, WasmInstr.Blocking.BLOCK, "fp2x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "fp2l", 0x40);
        get(b, KK);
        i32c(b, 4);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("fp2x", 1, true));
        get(b, KK);
        get(b, KJZ);
        get(b, KI);
        op(b, 0x6b, "i32.sub");
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("fp2x", 1, true));
        get(b, KFW);
        get(b, KBASE);
        get(b, KK);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, PIO2T));
        get(b, KBASE);
        get(b, KI);
        get(b, KK);
        op(b, 0x6a, "i32.add");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, Q));
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        set(b, KFW);
        get(b, KK);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KK);
        b.add(new WasmInstr.Branch("fp2l", 0));
        blk(b, WasmInstr.Blocking.END, "fp2l", 0x40);
        blk(b, WasmInstr.Blocking.END, "fp2x", 0x40);
        get(b, KBASE);
        get(b, KJZ);
        get(b, KI);
        op(b, 0x6b, "i32.sub");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KFW);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, FQ));
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KI);
        b.add(new WasmInstr.Branch("fp1l", 0));
        blk(b, WasmInstr.Blocking.END, "fp1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "fp1x", 0x40);
    }

    /** prec=2: y[0] = Σ fq (descendente); y[1] = (fq[0] − y0sel) + Σ_{i≥1} fq[i];
     *  os dois com o sinal do ih. */
    private static void emitY(List<WasmInstr> b) {
        f64c(b, 0.0);
        set(b, KFW);
        get(b, KJZ);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "sy1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "sy1l", 0x40);
        get(b, KI);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        b.add(new WasmInstr.Branch("sy1x", 1, true));
        get(b, KFW);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, FQ));
        op(b, 0xa0, "f64.add");
        set(b, KFW);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KI);
        b.add(new WasmInstr.Branch("sy1l", 0));
        blk(b, WasmInstr.Blocking.END, "sy1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "sy1x", 0x40);
        // o endereço do y0 fica SOB o if (o valor do braço entra por cima)
        get(b, KBASE);
        get(b, KIH);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x7c);
        get(b, KFW);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x7c);
        get(b, KFW);
        op(b, 0x9a, "f64.neg");
        blk(b, WasmInstr.Blocking.END, null, 0x7c);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, Y0));
        // fw = fq[0] − fw ; += fq[i] (i ≥ 1)
        get(b, KBASE);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, FQ));
        get(b, KFW);
        op(b, 0xa1, "f64.sub");
        set(b, KFW);
        i32c(b, 1);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "sy2x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "sy2l", 0x40);
        get(b, KI);
        get(b, KJZ);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("sy2x", 1, true));
        get(b, KFW);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, FQ));
        op(b, 0xa0, "f64.add");
        set(b, KFW);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        b.add(new WasmInstr.Branch("sy2l", 0));
        blk(b, WasmInstr.Blocking.END, "sy2l", 0x40);
        blk(b, WasmInstr.Blocking.END, "sy2x", 0x40);
        get(b, KBASE);
        get(b, KIH);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x7c);
        get(b, KFW);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x7c);
        get(b, KFW);
        op(b, 0x9a, "f64.neg");
        blk(b, WasmInstr.Blocking.END, null, 0x7c);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, Y1));
    }
}
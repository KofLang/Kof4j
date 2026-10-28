package dev.kof.compiler.wasm;

import java.util.List;

import static dev.kof.compiler.wasm.WasmRemPio2Large.F;
import static dev.kof.compiler.wasm.WasmRemPio2Large.IPIO2;
import static dev.kof.compiler.wasm.WasmRemPio2Large.IQ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KBASE;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KCARRY;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KFW;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KI;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KIH;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJX;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJV;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KK;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KN;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KQ0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Q;
import static dev.kof.compiler.wasm.WasmRemPio2Large.X0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.blk;
import static dev.kof.compiler.wasm.WasmRemPio2Large.f64c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.get;
import static dev.kof.compiler.wasm.WasmRemPio2Large.i32c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.op;
import static dev.kof.compiler.wasm.WasmRemPio2Large.pow2a;
import static dev.kof.compiler.wasm.WasmRemPio2Large.set;

/**
 * MATH-LIBM-4 fatia B (28/10, #776): a fase FINAL do laço do
 * {@code __kernel_rem_pio2} — o FLIP 1−q do carry quando ih &gt; 0 (o laço
 * do complemento, as máscaras raras do q0, o ih==2) e o CHECK DE
 * RECOMPUTAÇÃO (o or de iq[], o extend-q com o k termos, o
 * continue/break do laço principal). Os depths: dentro do if do j==0,
 * 3 = mainX e 2 = mainL (os dois IFs contam). Split check_500.
 */
final class WasmRemPio2Carry {

    private WasmRemPio2Carry() {
    }

    /** O flip 1−q do fdlibm quando ih > 0 (o carry, as máscaras do q0, o ih==2). */
    static void emitCarryFlip(List<WasmInstr> b) {
        get(b, KIH);
        i32c(b, 0);
        op(b, 0x4a, "i32.gt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KN);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KN);
        i32c(b, 0);
        set(b, KCARRY);
        i32c(b, 0);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "c1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "c1l", 0x40);
        get(b, KI);
        get(b, KJZ);
        op(b, 0x4e, "i32.ge_s");
        b.add(new WasmInstr.Branch("c1x", 1, true));
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        set(b, KJ);
        get(b, KCARRY);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KJ);
        i32c(b, 0);
        op(b, 0x47, "i32.ne");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        i32c(b, 1);
        set(b, KCARRY);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        i32c(b, 0x1000000);
        get(b, KJ);
        op(b, 0x6b, "i32.sub");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IQ));
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        i32c(b, 0xffffff);
        get(b, KJ);
        op(b, 0x6b, "i32.sub");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IQ));
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        b.add(new WasmInstr.Branch("c1l", 0));
        blk(b, WasmInstr.Blocking.END, "c1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "c1x", 0x40);
        emitQ0Masks(b);
        // if (ih == 2) { z = 1 − z; if (carry != 0) z -= 2^q0 }
        get(b, KIH);
        i32c(b, 2);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        f64c(b, 1.0);
        get(b, KZ);
        op(b, 0xa1, "f64.sub");
        set(b, KZ);
        get(b, KCARRY);
        i32c(b, 0);
        op(b, 0x47, "i32.ne");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KZ);
        pow2a(b, KQ0);
        op(b, 0xa1, "f64.sub");
        set(b, KZ);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
    }

    /** As máscaras do q0 do caso raro (1 em 12): &amp;= 0x7fffff / &amp;= 0x3fffff. */
    private static void emitQ0Masks(List<WasmInstr> b) {
        get(b, KQ0);
        i32c(b, 0);
        op(b, 0x4a, "i32.gt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KQ0);
        i32c(b, 1);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        maskTop(b, 0x7fffff);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        get(b, KQ0);
        i32c(b, 2);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        maskTop(b, 0x3fffff);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
    }

    /** iq[jz−1] &amp;= mask (o endereço empilhado ANTES do valor). */
    private static void maskTop(List<WasmInstr> b, int mask) {
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        i32c(b, mask);
        op(b, 0x71, "i32.and");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IQ));
    }

    /** O check de recomputação: z==0 → o or de iq[jz−1..jk]; j==0 → estende o
     *  q (k termos), jz += k e CONTINUA; senão SAI (os depths: 3 = mainX,
     *  2 = mainL — os dois IFs contam). */
    static void emitRecomputeCheck(List<WasmInstr> b) {
        get(b, KZ);
        f64c(b, 0.0);
        op(b, 0x61, "f64.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        i32c(b, 0);
        set(b, KJ);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "rr1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "rr1l", 0x40);
        get(b, KI);
        i32c(b, 4);
        op(b, 0x48, "i32.lt_s");
        b.add(new WasmInstr.Branch("rr1x", 1, true));
        get(b, KJ);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        op(b, 0x72, "i32.or");
        set(b, KJ);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KI);
        b.add(new WasmInstr.Branch("rr1l", 0));
        blk(b, WasmInstr.Blocking.END, "rr1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "rr1x", 0x40);
        get(b, KJ);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        emitExtendQ(b);
        get(b, KJZ);
        get(b, KK);
        op(b, 0x6a, "i32.add");
        set(b, KJZ);
        b.add(new WasmInstr.Branch("mainL", 2));
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        b.add(new WasmInstr.Branch("mainX", 3));
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        b.add(new WasmInstr.Branch("mainX", 2));
        blk(b, WasmInstr.Blocking.END, null, 0x40);
    }

    /** k = o número de termos necessários; estende f/q em [jz+1, jz+k]. */
    private static void emitExtendQ(List<WasmInstr> b) {
        i32c(b, 1);
        set(b, KK);
        blk(b, WasmInstr.Blocking.BLOCK, "kk1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "kk1l", 0x40);
        get(b, KBASE);
        i32c(b, 4);
        get(b, KK);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        op(b, 0x45, "i32.eqz");
        op(b, 0x45, "i32.eqz");
        b.add(new WasmInstr.Branch("kk1x", 1, true));
        get(b, KK);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KK);
        b.add(new WasmInstr.Branch("kk1l", 0));
        blk(b, WasmInstr.Blocking.END, "kk1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "kk1x", 0x40);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "ee1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "ee1l", 0x40);
        get(b, KI);
        get(b, KJZ);
        get(b, KK);
        op(b, 0x6a, "i32.add");
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("ee1x", 1, true));
        // f[jx+i] = (double) ipio2[jv+i]
        get(b, KBASE);
        get(b, KJX);
        get(b, KI);
        op(b, 0x6a, "i32.add");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KBASE);
        get(b, KJV);
        get(b, KI);
        op(b, 0x6a, "i32.add");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IPIO2));
        op(b, 0xb7, "f64.convert_i32_s");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, F));
        // q[i] = Σ x[j]·f[jx+i−j]
        f64c(b, 0.0);
        set(b, KFW);
        i32c(b, 0);
        set(b, KJ);
        blk(b, WasmInstr.Blocking.BLOCK, "ee2x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "ee2l", 0x40);
        get(b, KJ);
        get(b, KJX);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("ee2x", 1, true));
        get(b, KFW);
        get(b, KBASE);
        get(b, KJ);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, X0));
        get(b, KBASE);
        get(b, KJX);
        get(b, KI);
        op(b, 0x6a, "i32.add");
        get(b, KJ);
        op(b, 0x6b, "i32.sub");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, F));
        op(b, 0xa2, "f64.mul");
        op(b, 0xa0, "f64.add");
        set(b, KFW);
        get(b, KJ);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KJ);
        b.add(new WasmInstr.Branch("ee2l", 0));
        blk(b, WasmInstr.Blocking.END, "ee2l", 0x40);
        blk(b, WasmInstr.Blocking.END, "ee2x", 0x40);
        get(b, KBASE);
        get(b, KI);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KFW);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, Q));
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        b.add(new WasmInstr.Branch("ee1l", 0));
        blk(b, WasmInstr.Blocking.END, "ee1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "ee1x", 0x40);
    }
}
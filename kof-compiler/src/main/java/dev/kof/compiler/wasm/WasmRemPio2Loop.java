package dev.kof.compiler.wasm;

import java.util.List;

import static dev.kof.compiler.wasm.WasmRemPio2Large.IQ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KBASE;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KFW;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KI;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KIH;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KN;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KQ0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Q;
import static dev.kof.compiler.wasm.WasmRemPio2Large.TWO24;
import static dev.kof.compiler.wasm.WasmRemPio2Large.TWON24;
import static dev.kof.compiler.wasm.WasmRemPio2Large.blk;
import static dev.kof.compiler.wasm.WasmRemPio2Large.f64c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.get;
import static dev.kof.compiler.wasm.WasmRemPio2Large.i32c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.op;
import static dev.kof.compiler.wasm.WasmRemPio2Large.pow2a;
import static dev.kof.compiler.wasm.WasmRemPio2Large.set;

/**
 * MATH-LIBM-4 fatia B (28/10, #776): as duas fases INTERNAS do laço do
 * {@code __kernel_rem_pio2} — o DISTILL (os chunks de 24 bits de q[] para
 * iq[] no sentido reverso) e o CÁLCULO DO n/ih (o scalb exato, o trim de 8,
 * o trunc, e os três ramos do ih: q0 &gt; 0, q0 == 0 e o z ≥ 0.5). A fase
 * final do laço (o flip 1−q + o check de recomputação) mora no
 * {@link WasmRemPio2Carry}. Split check_500.
 */
final class WasmRemPio2Loop {

    private WasmRemPio2Loop() {
    }

    /** O distill: z = q[jz]; os chunks de 24 bits para iq[] (reverso). */
    static void emitDistill(List<WasmInstr> b) {
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, Q));
        set(b, KZ);
        i32c(b, 0);
        set(b, KI);
        get(b, KJZ);
        set(b, KJ);
        blk(b, WasmInstr.Blocking.BLOCK, "d1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "d1l", 0x40);
        get(b, KJ);
        i32c(b, 0);
        op(b, 0x4c, "i32.le_s");
        b.add(new WasmInstr.Branch("d1x", 1, true));
        // fw = (double)(int)(twon24·z)
        get(b, KZ);
        f64c(b, TWON24);
        op(b, 0xa2, "f64.mul");
        op(b, 0xaa, "i32.trunc_f64_s");
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, KFW);
        // iq[i] = (int)(z - 2^24·fw)
        get(b, KBASE);
        get(b, KI);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        get(b, KZ);
        f64c(b, TWO24);
        get(b, KFW);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        op(b, 0xaa, "i32.trunc_f64_s");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IQ));
        // z = q[j−1] + fw
        get(b, KBASE);
        get(b, KJ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, Q));
        get(b, KFW);
        op(b, 0xa0, "f64.add");
        set(b, KZ);
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        get(b, KJ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KJ);
        b.add(new WasmInstr.Branch("d1l", 0));
        blk(b, WasmInstr.Blocking.END, "d1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "d1x", 0x40);
    }

    /** z = scalb(z,q0); o trim de 8; n = (int)z; z -= n; o ih dos três ramos. */
    static void emitComputeN(List<WasmInstr> b) {
        get(b, KZ);
        pow2a(b, KQ0);
        op(b, 0xa2, "f64.mul");
        set(b, KZ);
        // z -= 8·floor(z·0.125)
        get(b, KZ);
        get(b, KZ);
        f64c(b, 0.125);
        op(b, 0xa2, "f64.mul");
        op(b, 0x9c, "f64.floor");
        f64c(b, 8.0);
        op(b, 0xa2, "f64.mul");
        op(b, 0xa1, "f64.sub");
        set(b, KZ);
        // n = (int) z ; z -= (double) n
        get(b, KZ);
        op(b, 0xaa, "i32.trunc_f64_s");
        set(b, KN);
        get(b, KZ);
        get(b, KN);
        op(b, 0xb7, "f64.convert_i32_s");
        op(b, 0xa1, "f64.sub");
        set(b, KZ);
        i32c(b, 0);
        set(b, KIH);
        // if (q0 > 0) {...} else if (q0 == 0) {...} else if (z >= 0.5) ih = 2
        get(b, KQ0);
        i32c(b, 0);
        op(b, 0x4a, "i32.gt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        // i = iq[jz−1] >> (24 − q0)
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        i32c(b, 24);
        get(b, KQ0);
        op(b, 0x6b, "i32.sub");
        op(b, 0x75, "i32.shr_s");
        set(b, KI);
        // n += i
        get(b, KN);
        get(b, KI);
        op(b, 0x6a, "i32.add");
        set(b, KN);
        // iq[jz−1] -= i << (24 − q0)
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
        get(b, KI);
        i32c(b, 24);
        get(b, KQ0);
        op(b, 0x6b, "i32.sub");
        op(b, 0x74, "i32.shl");
        op(b, 0x6b, "i32.sub");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IQ));
        // ih = iq[jz−1] >> (23 − q0)
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        i32c(b, 23);
        get(b, KQ0);
        op(b, 0x6b, "i32.sub");
        op(b, 0x75, "i32.shr_s");
        set(b, KIH);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        get(b, KQ0);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, KBASE);
        get(b, KJZ);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IQ));
        i32c(b, 23);
        op(b, 0x75, "i32.shr_s");
        set(b, KIH);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x40);
        get(b, KZ);
        f64c(b, 0.5);
        op(b, 0x66, "f64.ge");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        i32c(b, 2);
        set(b, KIH);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
    }
}
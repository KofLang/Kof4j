package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmRemPio2Large.F;
import static dev.kof.compiler.wasm.WasmRemPio2Large.IPIO2;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KBASE;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KE0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KFW;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KI;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJX;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KJZ;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KM;
import static dev.kof.compiler.wasm.WasmRemPio2Large.KQ0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.Q;
import static dev.kof.compiler.wasm.WasmRemPio2Large.X0;
import static dev.kof.compiler.wasm.WasmRemPio2Large.blk;
import static dev.kof.compiler.wasm.WasmRemPio2Large.f64c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.get;
import static dev.kof.compiler.wasm.WasmRemPio2Large.i32c;
import static dev.kof.compiler.wasm.WasmRemPio2Large.op;
import static dev.kof.compiler.wasm.WasmRemPio2Large.set;

/**
 * MATH-LIBM-4 fatia B (28/10, #776): o {@code __kernel_rem_pio2} do fdlibm
 * (prec=2, ipio2 = two_over_pi) — o ORQUESTRADOR: o setup (jx/jv/q0), os
 * dois inits (f[] e q[]) e o laço principal montado a partir das fases
 * ({@link WasmRemPio2Loop} = o distill + o n/ih; {@link WasmRemPio2Carry}
 * = o flip 1−q + o check de recomputação) + a cauda
 * ({@link WasmRemPio2Tail}). Os arrays vivem no SCRATCH DE MEMÓRIA (os
 * locais wasm não são indexáveis dinamicamente). Split check_500 (o
 * arquivo único de 702 linhas = a classe NOVA crítica).
 *
 * <p>Parâmetros: base (i32), e0 (i32), nx (i32) → n (i32). Os locais 3..15
 * (ver {@code WasmRemPio2Large}). As LEIS: os valores de q/f são inteiros
 * exatos; o scalb vira 2^n por bits; o {@code (int)} é trunc (a faixa é
 * garantida pelo fdlibm); o {@code /} trunca (div_s).
 */
final class WasmRemPio2Kernel {

    private WasmRemPio2Kernel() {
    }

    /** `kof.sin.rempi2(base i32, e0 i32, nx i32) -> i32` — o kernel completo. */
    static WasmFunc kernelFunc() {
        List<WasmInstr> b = new ArrayList<>();
        // jx = nx - 1
        get(b, 2);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, KJX);
        // jv = (e0 - 3)/24 (o Java trunca em direção a zero == div_s)
        get(b, KE0);
        i32c(b, 3);
        op(b, 0x6b, "i32.sub");
        i32c(b, 24);
        op(b, 0x6d, "i32.div_s");
        set(b, WasmRemPio2Large.KJV);
        // if (jv < 0) jv = 0
        get(b, WasmRemPio2Large.KJV);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        i32c(b, 0);
        set(b, WasmRemPio2Large.KJV);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        // q0 = e0 - 24*(jv + 1)
        get(b, KE0);
        get(b, WasmRemPio2Large.KJV);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        i32c(b, 24);
        op(b, 0x6c, "i32.mul");
        op(b, 0x6b, "i32.sub");
        set(b, KQ0);
        // j = jv - jx ; m = jx + jk (jk = 4)
        get(b, WasmRemPio2Large.KJV);
        get(b, KJX);
        op(b, 0x6b, "i32.sub");
        set(b, KJ);
        get(b, KJX);
        i32c(b, 4);
        op(b, 0x6a, "i32.add");
        set(b, KM);
        emitFInit(b);
        emitQInit(b);
        // jz = jk = 4
        i32c(b, 4);
        set(b, KJZ);
        emitMainLoop(b);
        WasmRemPio2Tail.emit(b);
        return new WasmFunc("kof.sin.rempi2", List.of(0x7f, 0x7f, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f,
                        0x7c, 0x7c), b);
    }

    /** f[i] = (j < 0) ? 0.0 : ipio2[j] para i ∈ [0, m], j andando de jv−jx. */
    private static void emitFInit(List<WasmInstr> b) {
        i32c(b, 0);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "f1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "f1l", 0x40);
        get(b, KI);
        get(b, KM);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("f1x", 1, true));
        // addr = base + i·8
        get(b, KBASE);
        get(b, KI);
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        // valor: j < 0 → 0.0 ; senão (double) ipio2[j]
        get(b, KJ);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x7c);
        f64c(b, 0.0);
        blk(b, WasmInstr.Blocking.ELSE, null, 0x7c);
        get(b, KBASE);
        get(b, KJ);
        i32c(b, 2);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, IPIO2));
        op(b, 0xb7, "f64.convert_i32_s");
        blk(b, WasmInstr.Blocking.END, null, 0x7c);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, F));
        get(b, KI);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KI);
        get(b, KJ);
        i32c(b, 1);
        op(b, 0x6a, "i32.add");
        set(b, KJ);
        b.add(new WasmInstr.Branch("f1l", 0));
        blk(b, WasmInstr.Blocking.END, "f1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "f1x", 0x40);
    }

    /** q[i] = Σ_{j=0..jx} x[j]·f[jx+i−j] para i ∈ [0, jk]. */
    private static void emitQInit(List<WasmInstr> b) {
        i32c(b, 0);
        set(b, KI);
        blk(b, WasmInstr.Blocking.BLOCK, "q1x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "q1l", 0x40);
        get(b, KI);
        i32c(b, 4);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("q1x", 1, true));
        f64c(b, 0.0);
        set(b, KFW);
        i32c(b, 0);
        set(b, KJ);
        blk(b, WasmInstr.Blocking.BLOCK, "q2x", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "q2l", 0x40);
        get(b, KJ);
        get(b, KJX);
        op(b, 0x4a, "i32.gt_s");
        b.add(new WasmInstr.Branch("q2x", 1, true));
        // fw += x[j]·f[jx+i−j]
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
        b.add(new WasmInstr.Branch("q2l", 0));
        blk(b, WasmInstr.Blocking.END, "q2l", 0x40);
        blk(b, WasmInstr.Blocking.END, "q2x", 0x40);
        // q[i] = fw
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
        b.add(new WasmInstr.Branch("q1l", 0));
        blk(b, WasmInstr.Blocking.END, "q1l", 0x40);
        blk(b, WasmInstr.Blocking.END, "q1x", 0x40);
    }

    /** O laço principal: distill → n/ih → o flip 1−q → o check de
     *  recomputação (o continue/break em três estados). */
    private static void emitMainLoop(List<WasmInstr> b) {
        blk(b, WasmInstr.Blocking.BLOCK, "mainX", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "mainL", 0x40);
        WasmRemPio2Loop.emitDistill(b);
        WasmRemPio2Loop.emitComputeN(b);
        WasmRemPio2Carry.emitCarryFlip(b);
        WasmRemPio2Carry.emitRecomputeCheck(b);
        blk(b, WasmInstr.Blocking.END, "mainL", 0x40);
        blk(b, WasmInstr.Blocking.END, "mainX", 0x40);
    }
}
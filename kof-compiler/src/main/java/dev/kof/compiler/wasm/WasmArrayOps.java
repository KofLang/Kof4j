package dev.kof.compiler.wasm;

import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.List;

import static dev.kof.compiler.wasm.WasmScalarOps.typeName;

/**
 * Fase E (27/10, #776 — split check_500): os OPS de ARRAY do backend WASI
 * (ARRAY-1), extraídos verbatim do {@code WasmLowering.emitPlain} (663 ≥ 600
 * derrubava o gate CI). Responsabilidade única: {@code new Int[n]} (o
 * ponteiro cru para [count i32][slots i32×N] no bump heap — o layout JÁ
 * contratado da 15.3c-sliceB pelo array args), o store com bounds-trap
 * (D-WASM-03), o load com sign-extend e o length. Comportamento idêntico
 * (freeze rule 3).
 */
final class WasmArrayOps {

    private WasmArrayOps() {
    }

    /** true = o op era de array e já emitiu. */
    static boolean emit(KofOperation op, WasmLowering.Ctx ctx, List<WasmInstr> out) {
        if (op instanceof KofNewArray na) {
            // ARRAY-1 (27/10, #776): `new Int[n]` — o tamanho vem na pilha
            // (i64); aloca 4 + n*4 no bump heap e grava o count. O layout e'
            // o da 15.3c-sliceB: [count i32][slots i32×N]. A disciplina de
            // scratch (sIdx/wIdx) e' a mesma dos loads — so em _start.
            if (!"int".equals(typeName(na.elementType()))) {
                throw new WasmUnsupportedException("new " + typeName(na.elementType())
                        + "[n] fora da fatia Int do array-heap (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("new Int[n] fora de '_start' (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));      // [n(i32)]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "n"));  // []
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "n"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));           // [n*4]
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));           // [n*4+4]
            out.add(new WasmInstr.Call("kof.heapAlloc"));
            out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.wIdx, "arr")); // [arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr")); // [arr, arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "n"));   // [arr, arr, n]
            out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // [arr] (count = n)
            ctx.lastPush = "array";
            ctx.lastPushWide = false;
        return true;
        }
        if (op instanceof KofArrayStore st) {
            // ARRAY-1 (27/10, #776): a[i] = v — bounds-trap (count <= idx →
            // unreachable, D-WASM-03: nunca lixo) e o Int na boca do slot de
            // 4 bytes (o wrap do i64 da pilha). [arr, idx(i64), v(i64)]
            if (!"int".equals(typeName(st.elementType()))) {
                throw new WasmUnsupportedException("store de " + typeName(st.elementType())
                        + " em array fora da fatia Int (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("store de array fora de '_start' (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.dupWideIdx, "v")); // [arr, idx]
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));      // [arr, idx(i32)]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "idx"));  // [arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.wIdx, "arr"));  // []
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));        // [count]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));  // [count, idx]
            out.add(new WasmInstr.Simple(0x4c, "i32.le_u"));          // count <= idx => fora
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
            out.add(new WasmInstr.Simple(0x00, "unreachable"));       // D-WASM-03
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));           // idx*4
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));           // addr
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.dupWideIdx, "v"));// [addr, v(i64)]
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));      // [addr, v(i32)]
            out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // []
            ctx.lastPush = null;
            ctx.lastPushWide = false;
        return true;
        }
        if (op instanceof KofArrayLength) {
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0)); // [arr] = count (15.3c-sliceB)
            out.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s")); // Int = i64 (D-WASM-02)
            ctx.lastPush = "int";
        return true;
        }
        if (op instanceof KofArrayLoad al) {
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("leitura de array fora de '_start' (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            // [arr, idx(i64)] -> bounds-check explícito (trap deterministico, nunca lixo)
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64")); // [arr, idx(i32)]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "idx"));  // [arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.wIdx, "arr"));  // []
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));   // [count]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));  // [count, idx]
            out.add(new WasmInstr.Simple(0x4c, "i32.le_u"));     // count <= idx => fora
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
            out.add(new WasmInstr.Simple(0x00, "unreachable"));  // D-WASM-03: trap honesto
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));      // [arr, idx*4]
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*4]
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*4 + 4]
            out.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            // ARRAY-1 (27/10): elem Int sai do slot de 4 bytes com SINAL
            // (o Int e' i64-wide na pilha — D-WASM-02); o caminho String
            // (o args) fica byte-intocado
            if ("int".equals(typeName(al.elementType()))) {
                out.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
                ctx.lastPushWide = true;
            }
            ctx.lastPush = typeName(al.elementType());
        return true;
        }

        return false;
    }
}

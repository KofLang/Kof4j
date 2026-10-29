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
            String ane = typeName(na.elementType());
            // ARRAY-2 (27/10): String entra — os slots zerados do bump SAO os
            // handles null (o boxToStr(0) renderiza "null", medido); long/
            // double truncariam no slot de 4 bytes
            // ARRAY-3 (27/10): Bool/Char entram (os slots zerados do bump
            // sao false / o char NUL — medido); long/double truncariam no
            // slot de 4 bytes
            // FATIA WIDE (28/10, #776): long/double entram — os slots de 8
            // bytes ([count i32][slots 8xN]) e o MESMO bounds-trap; os
            // zerados do bump = 0L/0.0 (o zero-init da JVM)
            boolean wideElem = "long".equals(ane) || "double".equals(ane);
            // CLASS-2j (29/10, #776): o ELEMENTO DE CLASSE = o MESMO slot de
            // 4 bytes do String (o handle i32 CRU — os slots zerados do bump
            // = os handles null)
            boolean classElem = ctx.classes() != null && ctx.classes().containsKey(ane);
            if (!"int".equals(ane) && !"string".equals(ane)
                    && !"bool".equals(ane) && !"char".equals(ane)
                    && !wideElem && !classElem) {
                throw new WasmUnsupportedException("new " + ane
                        + "[n] fora da fatia Int/String/Bool/Char/Classe do array-heap (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("new Int[n] fora de '_start' (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            int stride = wideElem ? 8 : 4;
            out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));      // [n(i32)]
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "n"));  // []
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "n"));
            out.add(new WasmInstr.Const(0, stride));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));           // [n*stride]
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));           // [n*stride+4]
            out.add(new WasmInstr.Call("kof.heapAlloc"));
            out.add(new WasmInstr.Local(WasmInstr.Local.TEE, ctx.wIdx, "arr")); // [arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr")); // [arr, arr]
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "n"));   // [arr, arr, n]
            out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // [arr] (count = n)
            // GC-1 (29/10, #776): os slots SAO zerados EXPLICITAMENTE — o
            // bloco pode vir da free-list (o dado reciclado nao e' zero) e
            // os slots zerados = os handles null/a paridade 0 do JVM
            out.add(new WasmInstr.Const(0, 0));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "j")); // j = 0 (reusa s)
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "zf", 0x40));
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "zl", 0x40));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "j"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "n"));
            out.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
            out.add(new WasmInstr.Branch("zf", 1, true));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "j"));
            out.add(new WasmInstr.Const(0, stride));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            if (wideElem) {
                out.add(new WasmInstr.Const(1, 0));
                out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0)); // o slot 8B = 0
            } else {
                out.add(new WasmInstr.Const(0, 0));
                out.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));   // o slot 4B = 0
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "j"));
            out.add(new WasmInstr.Const(0, 1));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, ctx.sIdx, "j"));
            out.add(new WasmInstr.Branch("zl", 0));
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "zl", 0x40));
            out.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "zf", 0x40));
            ctx.lastPush = "array";
            ctx.lastPushWide = false;
        return true;
        }
        if (op instanceof KofArrayStore st) {
            // ARRAY-1 (27/10, #776): a[i] = v — bounds-trap (count <= idx →
            // unreachable, D-WASM-03: nunca lixo) e o Int na boca do slot de
            // 4 bytes (o wrap do i64 da pilha). [arr, idx(i64), v(i64)]
            String ase = typeName(st.elementType());
            // ARRAY-2/3 (27/10): int/char chegam i64 (wrap); bool/string ja
            // sao i32 (CRU — o wrap do i32 leria o vizinho). O scratch
            // acompanha a LARGURA: os i64 usam o dupWideIdx, os i32 o dupIdx
            boolean wideStore = "long".equals(ase) || "double".equals(ase);
            // CLASS-2j (29/10, #776): o elemento de classe = o CRU (o handle
            // i32 no slot de 4 bytes — o MESMO do String)
            boolean classStore = ctx.classes() != null && ctx.classes().containsKey(ase);
            if (!"int".equals(ase) && !"string".equals(ase)
                    && !"bool".equals(ase) && !"char".equals(ase)
                    && !wideStore && !classStore) {
                throw new WasmUnsupportedException("store de " + ase
                        + " em array fora da fatia Int/String/Bool/Char/Classe (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            boolean wrapStore = "int".equals(ase) || "char".equals(ase);
            // FATIA WIDE: o long fica no scratch I64 (o dupWide), o double no
            // F64 (o vf64 — o scratch alocado SEMPRE pela fatia); o store =
            // 8 bytes no stride 8
            boolean dblStore = "double".equals(ase);
            int storeOp = dblStore ? WasmInstr.Mem.STORE_F64
                    : (wideStore ? WasmInstr.Mem.STORE64 : WasmInstr.Mem.STORE);
            // o scratch acompanha a LARGURA: o dupWideIdx e' I64 (o
            // validator pegou o handle i32 do String entrando nele) — o
            // string usa o dupIdx I32
            int vSlot = dblStore ? ctx.vf64Idx
                    : (wideStore ? ctx.dupWideIdx : (wrapStore ? ctx.dupWideIdx : ctx.dupIdx));
            if (ctx.wIdx < 0 || ctx.sIdx < 0) {
                throw new WasmUnsupportedException("store de array fora de '_start' (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            if (dblStore && ctx.vf64Idx < 0) {
                throw new WasmUnsupportedException("store de Double em array sem scratch f64 (WASM002) —"
                        + " docs/wasm-wasi-plan.md (#776)");
            }
            out.add(new WasmInstr.Local(WasmInstr.Local.SET, vSlot, "v")); // [arr, idx]
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
            int strideS = wideStore ? 8 : 4;
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));
            out.add(new WasmInstr.Const(0, strideS));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));           // idx*stride
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));           // addr
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, vSlot, "v"));// [addr, v]
            if (wrapStore) {
                out.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));  // [addr, v(i32)]
            }
            out.add(new WasmInstr.Mem(storeOp, 0));                   // []
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
            String ale = typeName(al.elementType());
            boolean wideLoad = "long".equals(ale) || "double".equals(ale);
            int strideL = wideLoad ? 8 : 4;
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.wIdx, "arr"));
            out.add(new WasmInstr.Local(WasmInstr.Local.GET, ctx.sIdx, "idx"));
            out.add(new WasmInstr.Const(0, strideL));
            out.add(new WasmInstr.Simple(0x6c, "i32.mul"));      // [arr, idx*stride]
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*stride]
            out.add(new WasmInstr.Const(0, 4));
            out.add(new WasmInstr.Simple(0x6a, "i32.add"));      // [arr + idx*stride + 4]
            out.add(new WasmInstr.Mem(wideLoad
                    ? ("double".equals(ale) ? WasmInstr.Mem.LOAD_F64 : WasmInstr.Mem.LOAD64)
                    : WasmInstr.Mem.LOAD, 0));
            // ARRAY-1 (27/10): elem Int sai do slot de 4 bytes com SINAL
            // (o Int e' i64-wide na pilha — D-WASM-02); o caminho String
            // (o args) fica byte-intocado. FATIA WIDE: o long sai i64 CRU
            // (o slot de 8 bytes), o double sai f64 (o rastro double)
            if ("int".equals(ale)) {
                out.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
                ctx.lastPushWide = true;
            } else if ("char".equals(ale)) {
                // ARRAY-3 (27/10): os code points sao nao-negativos — o
                // ZERO-extend devolve o char i64-wide (o writeChar/strChar
                // recebem i64)
                out.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
                ctx.lastPushWide = true;
            } else if ("bool".equals(ale)) {
                ctx.lastPushWide = false;
            } else if (wideLoad) {
                ctx.lastPushWide = "long".equals(ale);
            } else if (ctx.classes() != null && ctx.classes().containsKey(ale)) {
                // CLASS-2j (29/10, #776): o elemento de classe = o handle
                // i32 sem extensão; o rastro = o NOME da classe (o despacho
                // sobre o elemento anda na rota ARMADA)
                ctx.lastPushWide = false;
            }
            ctx.lastPush = ale;
        return true;
        }

        return false;
    }
}

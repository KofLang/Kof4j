package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (27/10, #776 — split check_500): a EXTRAÇÃO de iteração do MAP —
 * {@code m.keys()}/{@code m.values()} — extraída verbatim do
 * {@code WasmMapRuntime} (645 ≥ 600 derrubava o gate CI). Responsabilidade
 * única: construir a LISTA nova na forma 24B provada.
 */
final class WasmMapExtract {

    private WasmMapExtract() {
    }

    /** FATIA 3 (27/10): a EXTRAÇÃO comum de keys/values — copia os slots
     *  16B dos pares (offset 0 = chave, 8 = valor) para uma lista nova de
     *  8B por slot, na forma 24B PROVADA [dataPtr][len][cap=len]: SEM
     *  caminho de grow (o tamanho final é conhecido) e SEM chamar o
     *  listNew (zero dependência além do heapAlloc compartilhado — o mapa
     *  vazio dá heapAlloc(0), um bump válido). A lei dos dois blocos no
     *  laço (o br de saída mira o BLOCK, profundidade 1). A ORDEM não é o
     *  contrato (a semântica do HashMap não a expõe — MAP-1); a célula
     *  WASI é de inserção. */
    private static WasmFunc extract(String name, String lbl, int valueOffset) {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h(i32), 1=md(i32), 2=len(i64), 3=cell(i32), 4=ld(i32), 5=i(i64)
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 1, "md");
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 2, "len");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 4, "ld");
        i32c(b, 24);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 3, "cell");
        get(b, 3, "cell");
        get(b, 4, "ld");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [cell] = ld
        get(b, 3, "cell");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [cell+8] = len
        get(b, 3, "cell");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [cell+16] = cap=len
        i64c(b, 0);
        set(b, 5, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "x", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, 5, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch(lbl + "x", 1, true));
        get(b, 4, "ld");
        get(b, 5, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 1, "md");
        get(b, 5, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        if (valueOffset > 0) {
            i64c(b, valueOffset);
            b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        }
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 5, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 5, "i");
        b.add(new WasmInstr.Branch(lbl + "l", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "x", 0x40));
        get(b, 3, "cell");
        // slots 1..6 = md I32, len I64, cell I32, ld I32, i I64 (o
        // validator pegou a ORDEM da lista — len/cell trocados; a mesma
        // licao de mapa de slots das fatias anteriores)
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** `kof.mapKeys(h i32) -> i32` — as chaves como List<K> (o rastro
     *  "list" do despacho entrega o resultado à maquinaria da LISTA). */
    static WasmFunc mapKeys() {
        return extract("kof.mapKeys", "mk", 0);
    }

    /** `kof.mapValues(h i32) -> i32` — os valores como List<V>. */
    static WasmFunc mapValues() {
        return extract("kof.mapValues", "mv", 8);
    }

}

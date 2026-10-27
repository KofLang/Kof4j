package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (27/10, #776): LIST fatia 1 — `List<Int>` basico no WASI (o
 * `println(xs.size())`/`xs.get(i)` da unidade 15.3 que o subset escalar
 * recusava). O contrato OBSERVAVEL e o do `java.util.ArrayList` do JVM
 * (o desugar `kof_list_new` + N× `kof_list_add` + size/get): o handle do
 * chamador NUNCA invalida — por isso a celula e INDIRETA,
 *
 * <pre>celula 24B: [dataPtr i32][len i64][cap i64];  dados: cap × i64</pre>
 *
 * e o add que cresce COPIA para um bloco novo e reescreve a celula (o bump
 * heap nao libera o bloco velho — documentado; o JVM dobra a capacidade
 * interna do mesmo jeito). Fora do Int os elementos recusam pelo nome
 * (WASM002) — a fatia nao admite lista heterogenea silenciosa.
 */
final class WasmListRuntime {

    private WasmListRuntime() {
    }

    /** Os nomes de desugar atendidos (a whitelist do scan e o despacho). */
    static final Set<String> NAMES = Set.of(
            "kof_list_new", "kof_list_add", "kof_list_size", "kof_list_get");

    /** `kof.heapAlloc(size i32) -> i32` — o bump global 0 alinhado em 8. */
    private static WasmFunc heapAlloc() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        i32c(b, 7);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, -8);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 1, "base");                                       // alinhado
        // o NOVO topo = base + size (NAO g - base + size: a forma antiga
        // gravava o DELTA no global — o ponteiro colapsava, as alocacoes
        // se sobrepunham e a copia do grow borrava o bloco: 1,1,1,1 medido)
        get(b, 1, "base");
        get(b, 0, "size");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));    // base + size
        get(b, 1, "base");
        return new WasmFunc("kof.heapAlloc", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32), b);
    }

    /** `kof.listNew() -> i32` — a celula + o bloco inicial cap=4. */
    private static WasmFunc listNew() {
        List<WasmInstr> b = new ArrayList<>();
        i32c(b, 24);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 0, "cell");
        i32c(b, 32);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 1, "data");
        get(b, 0, "cell");                                       // [cell+0] = data
        get(b, 1, "data");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        get(b, 0, "cell");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));            // addr cell+8
        i64c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // len = 0
        get(b, 0, "cell");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));            // addr cell+16
        i64c(b, 4);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // cap = 4
        get(b, 0, "cell");
        return new WasmFunc("kof.listNew", List.of(), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.listAdd(h i32, v i64)` — cresce dobrando (copia, reescreve a celula). */
    private static WasmFunc listAdd() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=cap, 4=od, 5=nc, 6=nd, 7=data, 8=i (i64)
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 3, "cap");
        // if len == cap: dobrar
        get(b, 2, "len");
        get(b, 3, "cap");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "grow", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));         // od = [h]
        set(b, 4, "od");
        get(b, 3, "cap");
        get(b, 3, "cap");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 5, "nc");                                         // nc = cap+cap
        get(b, 5, "nc");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 6, "nd");
        // copia: for i in 0..len: [nd+i*8] = [od+i*8]
        i64c(b, 0);
        set(b, 8, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "cp", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "cpl", 0x40));
        get(b, 8, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));           // 0x59 = ge_s (nao 0x5d)
        b.add(new WasmInstr.Branch("cp", 1, true));
        get(b, 6, "nd");
        get(b, 8, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));            // nd + i*8
        get(b, 4, "od");
        get(b, 8, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));       // [od+i*8]
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // -> [nd+i*8]
        get(b, 8, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 8, "i");
        b.add(new WasmInstr.Branch("cpl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cpl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cp", 0x40));
        get(b, 0, "h");                                          // [h] = nd
        get(b, 6, "nd");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 5, "nc");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [h+16] = nc
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        // [data + len*8] = v; [h+8] = len+1 — o data RELOAD depois do grow
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 7, "data");
        get(b, 7, "data");
        get(b, 2, "len");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 1, "v");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        return new WasmFunc("kof.listAdd", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64), b);
    }

    /** `kof.listSize(h i32) -> i64` — [h+8]. */
    private static WasmFunc listSize() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.listSize", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64), List.of(), b);
    }

    /** `kof.listGet(h i32, i i64) -> i64` — fora da faixa TRAPA (nunca silencio). */
    private static WasmFunc listGet() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=i; 2=len, 3=data
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        // if i >=u len (negativo vira enorme no unsigned) -> unreachable
        get(b, 1, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));           // 0x5a = ge_u (0x5e e' f32!)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "oob", 0x40));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "oob", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        get(b, 3, "data");
        get(b, 1, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.listGet", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** Toda a familia (o chamador registra quando um kof_list_* aparece). */
    static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(heapAlloc());
        fs.add(listNew());
        fs.add(listAdd());
        fs.add(listSize());
        fs.add(listGet());
        return fs;
    }
}

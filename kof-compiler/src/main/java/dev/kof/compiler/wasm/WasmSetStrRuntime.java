package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (28/10, #776 — split check_500): a FAMÍLIA STR do Set —
 * `setAddStr`/`setContainsStr` (27/10) movidas verbatim de
 * {@code WasmSetRuntime} (569 ≥ 500 ia para o crítico com o novo) + o
 * `setRemoveStr` NOVO (a fatia SET-STR da rodada 53). A lei: o slot
 * guarda o HANDLE (i64 zero-extendido) e a igualdade é de CONTEÚDO (o
 * `kof.strEq` — o contrato do `java.util.HashSet` da JVM). O remove é
 * o mesmo shift do `setRemove` Int com o scan de conteúdo.
 */
final class WasmSetStrRuntime {

    private WasmSetStrRuntime() {
    }

    /**
     * O corpo COMUM das consultas/adições de Set<String>: o slot guarda o
     * HANDLE (i64 zero-extendido) e a igualdade e' de CONTEUDO (o
     * `kof.strEq` existente). Devolve o indice (ou -1) no local res.
     */
    static void scanBodyStr(List<WasmInstr> b, String lbl, int hLocal,
                            int sLocal, int resLocal, int tmpLocal) {
        // 2=len, 3=data, 4=i, tmp, res
        get(b, hLocal, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, hLocal, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, 0);
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "i", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, sLocal, "s");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, tmpLocal, "eq");
        get(b, tmpLocal, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, lbl + "m", 0x40));
        get(b, 4, "i");
        set(b, resLocal, "res");
        b.add(new WasmInstr.Branch(lbl, 3));                     // 0=if,1=loop,2=inner,3=outer
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "m", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch(lbl + "l", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "i", 0x40));
        i64c(b, -1);
        set(b, resLocal, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl, 0x40));
    }

    /** `kof.setAddStr(h i32, s i32)` — dedup por CONTEUDO antes do anexa.
     *  O scanBodyStr RE-CARREGA len/data nos slots 2/3 (os DELE) — o
     *  estado proprio deste metodo mora em 5+ (idx/cap/od/nc/nd/gi). */
    static WasmFunc setAddStr() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=s; 2=len(scan), 3=data(scan), 4=i(scan), 5=idx, 6=eq,
        // 7=cap(I64), 8=od(I32), 9=nc(I64), 10=nd(I32), 11=gi(I64), 12=livre
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 7, "cap");                                    // a cap ANTES do scan
        scanBodyStr(b, "ak", 0, 1, 5, 6);
        // ja existe (idx >= 0): o booleano 0 (dedup) — o if e' RESULT-TYPED
        // (o ELSE anexa e devolve 1; o resultado = o HashSet.add booleano —
        // a forma void da cadeia setOf dropa no despacho)
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "dup", 0x7f));
        i32c(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "dup", 0x7f));
        // o grow se cheio
        get(b, 2, "len");
        get(b, 7, "cap");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "grow", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 8, "od");
        get(b, 7, "cap");
        get(b, 7, "cap");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 9, "nc");
        get(b, 9, "nc");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 10, "nd");
        i64c(b, 0);
        set(b, 11, "gi");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sc", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "scl", 0x40));
        get(b, 11, "gi");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("sc", 1, true));
        get(b, 10, "nd");
        get(b, 11, "gi");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 8, "od");
        get(b, 11, "gi");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 11, "gi");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 11, "gi");
        b.add(new WasmInstr.Branch("scl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "scl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sc", 0x40));
        get(b, 0, "h");
        get(b, 10, "nd");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 9, "nc");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        // o anexa: re-carrega o data do handle (o scan sobrescreveu 3/4)
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 12, "data2");
        get(b, 12, "data2");
        get(b, 2, "len");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 1, "s");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        i32c(b, 1);                                              // anexou
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dup", 0x7f));
        // o mapa REAL: 2=len/3=data/4=i (os DELE do scan), 5=idx(I64),
        // 6=eq(I32), 7=cap(I64), 8=od(I32), 9=nc(I64), 10=nd(I32),
        // 11=gi(I64), 12=livre(I32)
        return new WasmFunc("kof.setAddStr", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setContainsStr(h i32, s i32) -> i32` — igualdade de conteudo. */
    static WasmFunc setContainsStr() {
        List<WasmInstr> b = new ArrayList<>();
        scanBodyStr(b, "ck", 0, 1, 6, 7);
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=livre(I64),
        // 6=idx/res(I64 — o INDICE!), 7=eq(I32), 8=found(I32) — o
        // comparador do idx e' i64 (o i32.const dava o mismatch)
        get(b, 6, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        set(b, 8, "found");
        get(b, 8, "found");
        return new WasmFunc("kof.setContainsStr", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setRemoveStr(h i32, s i32) -> i32` — remove por CONTEUDO (a
     *  fatia SET-STR da rodada 53): o scan do scanBodyStr acha o idx; o
     *  corpo é o shift do `setRemove` Int (o guard idx≥0 — a lei GE_S:
     *  o LT_S invertia e corrompia o vizinho, S12 mediu), a caminhada
     *  `data[j] = data[j+1]`, len−1 e o booleano do HashSet.remove. */
    static WasmFunc setRemoveStr() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=s; 2=len/3=data/4=i (os DELE do scan), 5=idx(I64),
        // 6=eq(I32), 7=j(I64), 8=rem(I32)
        scanBodyStr(b, "rk", 0, 1, 5, 6);
        // achou (idx >= 0): desloca [j] = [j+1] de idx ate len-2; len-1; rem=1
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        i32c(b, 1);
        set(b, 8, "rem");                                        // removeu
        get(b, 5, "idx");
        set(b, 7, "j");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rs", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rsl", 0x40));
        get(b, 7, "j");
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rs", 1, true));
        get(b, 3, "data");
        get(b, 7, "j");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 7, "j");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 7, "j");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 7, "j");
        b.add(new WasmInstr.Branch("rsl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rsl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rs", 0x40));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "has", 0x40));
        get(b, 8, "rem");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=idx(I64),
        // 6=eq(I32), 7=j(I64), 8=rem(I32 — o booleano do HashSet.remove,
        // que o KofPop do desugar da declaracao consome)
        return new WasmFunc("kof.setRemoveStr", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }
}
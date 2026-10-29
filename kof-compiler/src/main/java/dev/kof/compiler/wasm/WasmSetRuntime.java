package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (27/10, #776): SET fatia 1 — `Set<Int>` basico no WASI. O contrato
 * OBSERVAVEL e o do `java.util.HashSet` da JVM: add DEDUPLICA (o scan de
 * conteudo antes do anexa — `setOf(1, 2, 2)` tem size 2), contains/remove
 * scan por igualdade de conteudo (o bit-scan para Int). A CELULA e' a da
 * lista ([len][cap] + slots i64, crescimento dobrando, o heapAlloc
 * COMPARTILHADO); a busca e' LINEAR (a semantica do HashSet nao expõe
 * ordem nem complexidade).
 */
final class WasmSetRuntime {

    private WasmSetRuntime() {
    }

    /** Os nomes de desugar atendidos (a whitelist do scan e o despacho). */
    static final Set<String> NAMES = Set.of(
            "kof_set_new", "kof_set_add", "kof_set_contains", "kof_set_remove",
            "kof_set_size");  // fatia 1: Int; a fatia 2 roteia Set<String>


    /** `kof.setNew() -> i32` — a celula + o bloco inicial cap=8. */
    private static WasmFunc setNew() {
        List<WasmInstr> b = new ArrayList<>();
        // A LEI DA CELULA (o fix da rodada 53): a celula = 24 BYTES
        // ([0]=data i32, [8]=len i64, [16]=cap i64) — o heapAlloc(16)
        // original fazia o store da cap em cell+16 CAIR NO SLOT 0 do bloco
        // de dados (a proxima alocacao): a cap lida virava o valor do
        // elemento 0, o grow NUNCA disparava e os appends > 8 estouravam
        // no bloco vizinho do heap (os literais de String medidos: as
        // sondas S5/S7/S8 corromperam; a via Int escapou por sorte — as
        // escritas caian no espaco livre do bump). A lista ja alocava 24.
        i32c(b, 24);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 0, "cell");
        i32c(b, 64);                                             // 8 slots × 8B
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 1, "data");
        get(b, 0, "cell");
        get(b, 1, "data");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [cell] = data
        get(b, 0, "cell");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i64c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // len = 0
        get(b, 0, "cell");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i64c(b, 8);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // cap = 8
        get(b, 0, "cell");
        return new WasmFunc("kof.setNew", List.of(), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** O GROW comum: dobra o bloco, copia len slots, reescreve a celula.
     *  Os slots 5..8 (LIVRES — o 2=len, 3=cap, 4=data do chamador ficam
     *  vivos; o 3 do mapPut nao conflita porque la' o grow usa 3..6).
     *  SET-REC (29/10, #776): os ops por-record do {@link WasmSetRecRuntime}
     *  reusam o MESMO grow — package-private. */
    static void growBody(List<WasmInstr> b, int hLocal, int lenLocal, int capLocal) {
        int od = 5, nc = 6, nd = 7, i = 8;
        get(b, hLocal, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, od, "od");
        get(b, capLocal, "cap");
        get(b, capLocal, "cap");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, nc, "nc");
        get(b, nc, "nc");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, nd, "nd");
        i64c(b, 0);
        set(b, i, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sc", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "scl", 0x40));
        get(b, i, "i");
        get(b, lenLocal, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("sc", 1, true));
        get(b, nd, "nd");
        get(b, i, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, od, "od");
        get(b, i, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, i, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, i, "i");
        b.add(new WasmInstr.Branch("scl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "scl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sc", 0x40));
        get(b, hLocal, "h");
        get(b, nd, "nd");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [h] = nd
        get(b, hLocal, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, nc, "nc");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));      // [h+16] = nc
    }

    /**
     * `kof.setAdd(h i32, v i64)` — o scan de DEDUP (a lei dos dois blocos:
     * as saidas de fronteira bram no bloco interno e caem no anexa; o
     * ja-existe brama o bloco externo e sai SEM anexar).
     */
    private static WasmFunc setAdd() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=cap, 4=data, 5..8 grow, 9=i, 10=tmp
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
        get(b, 2, "len");
        get(b, 3, "cap");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "grow", 0x40));
        growBody(b, 0, 2, 3);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 4, "data");
        i64c(b, 0);
        set(b, 9, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ak", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "aki", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "akl", 0x40));
        get(b, 9, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("aki", 1, true));             // fronteira -> anexa
        get(b, 4, "data");
        get(b, 9, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        get(b, 1, "v");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "am", 0x40));
        // ja existe: res = 0 (dedup) — sai pelo bloco de fora SEM anexar
        i32c(b, 0);
        set(b, 11, "res");
        b.add(new WasmInstr.Branch("ak", 3));                    // 0=if,1=loop,2=inner,3=outer
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "am", 0x40));
        get(b, 9, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 9, "i");
        b.add(new WasmInstr.Branch("akl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "akl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "aki", 0x40));
        // anexa (so' as saidas de FRONTEIRA caem aqui): [data+len*8] = v; len+1
        get(b, 4, "data");
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
        i32c(b, 1);
        set(b, 11, "res");                                       // res = 1 (anexou)
        // fecha o bloco EXTERNO "ak" (faltava: control frames remain — o
        // mesmo END esquecido do mapPut)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ak", 0x40));
        get(b, 11, "res");                                       // o booleano (depois do END)
        // o mapa REAL: 2=len(I64), 3=cap(I64), 4=data(I32), 5=od(I32),
        // 6=nc(I64), 7=nd(I32), 8=i-do-grow(I64), 9=i-do-scan(I64),
        // 10=res(I32 — o booleano do HashSet.add) — a primeira declaracao
        // colidia o grow com len/cap/data
        return new WasmFunc("kof.setAdd", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.setContains(h i32, v i64) -> i32` — 1 se o conjunto contem. */
    private static WasmFunc setContains() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=data, 4=i, 5=found
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, 0);
        set(b, 4, "i");
        i32c(b, 0);
        set(b, 5, "found");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ck", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "ckl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("ck", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        get(b, 1, "v");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "cm", 0x40));
        i32c(b, 1);
        set(b, 5, "found");
        b.add(new WasmInstr.Branch("ck", 2));                    // 0=if,1=loop,2=block
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("ckl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ckl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ck", 0x40));
        get(b, 5, "found");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=found(I32 — a
        // emissao grava i32.const nele; I64 aqui = expected i64 found i32)
        return new WasmFunc("kof.setContains", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setRemove(h i32, v i64)` — remove por valor (desloca, len-1). */
    private static WasmFunc setRemove() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=data, 4=i, 5=idx(-1=ausente), 6=j, 7=tmp
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        i64c(b, -1);
        set(b, 5, "idx");
        i64c(b, 0);
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rkl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rk", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        get(b, 1, "v");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "rm", 0x40));
        get(b, 4, "i");
        set(b, 5, "idx");
        b.add(new WasmInstr.Branch("rk", 2));                    // 0=if,1=loop,2=block
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("rkl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rkl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rk", 0x40));
        // achou (idx >= 0): desloca [j] = [j+1] de idx ate len-2; len-1; rem=1
        // — a condicao e' GE_S (0x59): o LT_S invertia (o corpo rodava na
        // NAO-remocao e o shift corrompia o vizinho — S12 mediu)
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        i32c(b, 1);
        set(b, 8, "rem");                                        // removeu
        get(b, 5, "idx");
        set(b, 6, "j");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rs", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rsl", 0x40));
        get(b, 6, "j");
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rs", 1, true));
        get(b, 3, "data");
        get(b, 6, "j");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 6, "j");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 6, "j");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 6, "j");
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
        i32c(b, 1);
        set(b, 8, "rem");                                        // removeu
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "has", 0x40));
        get(b, 8, "rem");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=idx(I64),
        // 6=j(I64), 7=tmp(I64), 8=rem(I32 — o booleano do HashSet.remove,
        // que o KofPop do desugar da declaracao consome)
        return new WasmFunc("kof.setRemove", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setSize(h i32) -> i64` — [h+8]. */
    private static WasmFunc setSize() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.setSize", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64), List.of(), b);
    }


    /**
     * O corpo COMUM das consultas/adições de Set<String> — MOVIDO para
     * {@link WasmSetStrRuntime} (o split check_500 da fatia SET-STR).
     */

    /** Toda a familia (o chamador registra quando um kof_set_* aparece). */
    static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(setNew());
        fs.add(setAdd());
        fs.add(setContains());
        fs.add(setRemove());
        fs.add(setSize());
        // a familia STR (Set<String>): o anexa/contém pousados 27/10; o
        // remove por conteúdo = a fatia SET-STR (28/10, rodada 53)
        fs.add(WasmSetStrRuntime.setAddStr());
        fs.add(WasmSetStrRuntime.setContainsStr());
        fs.add(WasmSetStrRuntime.setRemoveStr());
        return fs;
    }
}

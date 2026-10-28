package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * FATIA MAP-KI-VAL (28/10, #776 — split check_500): os REMOVES das chaves
 * INT com os valores escalares — `kof.mapRemoveKi{Int,Bool,Dbl}`: o scan
 * RAW do {@link WasmMapKiValRuntime#scanKi} (a busca {@code i64.eq}, a lei
 * MAP-KI), a caixa do valor removido por largura (o boxInt / o boxBool do
 * qword wrapado / o boxDouble dos bits — as leis MAP-C/B/D), o shift dos
 * pares de 16B e o len−1. O Char remove = o mapRemoveKiInt (a MESMA caixa
 * boxInt — a lei MAP-C; só o rastro nullablechar do despacho difere).
 * Registrados pelo all() do {@link WasmMapRuntime}.
 */
final class WasmMapKiValRemove {

    private WasmMapKiValRemove() {
    }

    /** `kof.mapRemoveKi{Int,Bool,Dbl}(h i32, key i64) -> i32` — o hit =
     *  a caixa do valor removido + o shift dos pares; o miss = 0. */
    private static WasmFunc mapRemoveKiVal(String name, int kind) {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key(i64); 2=len, 3=data, 4=i, 5=idx, 6=res, 7=eq, 8=j
        WasmMapKiValRuntime.scanKi(b, "rk", 2, 3, 4, 5, 7);
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        // res = a caixa do valor removido (a lei da largura)
        WasmMapKiValRuntime.loadVal(b, 3, 5);
        WasmMapKiValRuntime.boxVal(b, kind);
        set(b, 6, "res");
        // o shift dos pares: [j] = [j+1] de idx ate len-2 (os DOIS qwords
        // do par 16B — a MESMA caminhada do mapRemoveDbl)
        get(b, 5, "idx");
        set(b, 8, "j");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "rs", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rsl", 0x40));
        get(b, 8, "j");
        get(b, 2, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("rs", 1, true));
        // [data+j·16] = [data+(j+1)·16]
        pairAddr(b, 8, 0);
        pairAddr(b, 8, 16);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        // [data+j·16+8] = [data+(j+1)·16+8]
        pairAddr(b, 8, 8);
        pairAddr(b, 8, 24);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 8, "j");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 8, "j");
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
        get(b, 6, "res");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=idx(I64),
        // 6=res(I32 — o HANDLE da caixa), 7=eq(I32), 8=j(I64)
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64), b);
    }

    /** O endereço [data + j·16 + off] empilhado (o shift dos pares). */
    private static void pairAddr(List<WasmInstr> b, int jLocal, int off) {
        get(b, 3, "data");
        get(b, jLocal, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        if (off > 0) {
            i32c(b, off);
            b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        }
    }

    /** `kof.mapRemoveKiInt(h i32, key i64) -> i32` — o Int (e o Char pela
     *  lei MAP-C: a MESMA caixa boxInt). */
    static WasmFunc mapRemoveKiInt() {
        return mapRemoveKiVal("kof.mapRemoveKiInt", 0);
    }

    /** `kof.mapRemoveKiBool(h i32, key i64) -> i32` — o boxBool do qword. */
    static WasmFunc mapRemoveKiBool() {
        return mapRemoveKiVal("kof.mapRemoveKiBool", 1);
    }

    /** `kof.mapRemoveKiDbl(h i32, key i64) -> i32` — o boxDouble dos bits. */
    static WasmFunc mapRemoveKiDbl() {
        return mapRemoveKiVal("kof.mapRemoveKiDbl", 2);
    }
}
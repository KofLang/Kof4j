package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * FATIA MAP-B (28/10, #776 — split check_500): os valores BOOL do map — o
 * get/remove DEVOLVEM A CAIXA §284 (o boxBool do qword zero-estendido; o
 * Bool? tem o null REAL: o 0 = false nao pode ser a sentinela, o MESMO
 * contrato do put). O put fica no CALL SITE (o extend_i32_u sobre o i32 do
 * bool + o mapPut CRU). Registrados pelo all() do {@link WasmMapRuntime}.
 */
final class WasmMapBoolRuntime {

    private WasmMapBoolRuntime() {
    }

static WasmFunc mapGetBool() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key; 2=len, 3=data, 4=i, 5=res, 6=tmp
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
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "gk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "gkl", 0x40));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch("gk", 1, true));
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 6, "eq");
        get(b, 6, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "gm", 0x40));
        // achou: res = boxBool(qword([data+i*16+8])); sai
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        // o boxBool pede o bool i32: o qword zero-estendido wrapa de volta
        // (o low 32 bits = o 0/1 do bool)
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.boxBool"));
        set(b, 5, "res");
        // DENTRO do if: 0=if, 1=loop, 2=bloco gk — o br 2 salta o 0-default
        b.add(new WasmInstr.Branch("gk", 2));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gm", 0x40));
        get(b, 4, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("gkl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gkl", 0x40));
        // nao achei: res = 0 (o NULL — boxToStr renderiza "null")
        i32c(b, 0);
        set(b, 5, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gk", 0x40));
        get(b, 5, "res");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=res(I32 — o
        // HANDLE da caixa!), 6=eq(I32) — res era I64 e o validador rejeitou
        // o set do handle
        return new WasmFunc("kof.mapGetBool", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }
static WasmFunc mapRemoveBool() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key; 2=len, 3=data, 4=i, 5=idx, 6=res, 7=eq, 8=j, 9=tmp
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
        i32c(b, 0);
        set(b, 6, "res");
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
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 1, "key");
        b.add(new WasmInstr.Call("kof.strEq"));
        set(b, 7, "eq");
        get(b, 7, "eq");
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
        // achou: res = boxBool(qword(valor)); desloca os pares; len-1
        // (o idx e' I64: o comparador tambem — o i32.const aqui dava
        // expected i64 found i32)
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        get(b, 3, "data");
        get(b, 5, "idx");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        // o boxBool pede o bool i32: o qword zero-estendido wrapa de volta
        // (o low 32 bits = o 0/1 do bool)
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.boxBool"));
        set(b, 6, "res");
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
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 3, "data");
        get(b, 8, "j");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
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
        // 6=res(I32), 7=eq(I32 — o resultado do strEq!), 8=j(I64),
        // 9=livre, 10=res2 — a ordem antiga punha eq em I64
        return new WasmFunc("kof.mapRemoveBool", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32), b);
    }
}

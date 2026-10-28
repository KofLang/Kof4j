package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * FATIA MAP-KI-VAL (28/10, #776 — split check_500): as chaves INT do map
 * com os valores ESCALARES (Int|Bool|Double|Char — o Char anda na fatia
 * Int pela lei MAP-C: o MESMO slot cru + a caixa boxInt, só o rastro
 * nullablechar difere). As leis compostas: a chave = RAW i64 com a busca
 * {@code i64.eq} (a lei MAP-KI — NUNCA o strEq); o Double = os BITS (o
 * reinterpret no call site, a caixa boxDouble — a lei MAP-D); o Bool = o
 * estendido no call site + o boxBool do qword wrapado (a lei MAP-B); o
 * put com o valor CRU. Os helpers (o {@code scanKi}, o {@code loadVal}, o
 * {@code storeVal}, o {@code boxVal}) são PARAMETRIZADOS por local — o
 * put tem o v no param 2, os demais layouts livres. O remove mora no
 * {@link WasmMapKiValRemove}. Registrados pelo all() do
 * {@link WasmMapRuntime}.
 */
final class WasmMapKiValRuntime {

    private WasmMapKiValRuntime() {
    }

    /** O scan da chave RAW: deixa len/data/i nos locais pedidos e o idx
     *  (ou -1) em idxLocal. A busca = o i64.eq (a lei MAP-KI: a chave INT
     *  = o i64 do slot — nunca o strEq). DOIS blocos (a lei do scanBody):
     *  a fronteira brama o bloco DE DENTRO (caindo no -1); o br do match
     *  mira o DE FORA (preserva o idx — 0=if, 1=loop, 2=externo). */
    static void scanKi(List<WasmInstr> b, String lbl, int lenLocal, int dataLocal,
                       int iLocal, int idxLocal, int eqLocal) {
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, lenLocal, "len");
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, dataLocal, "data");
        i64c(b, -1);
        set(b, idxLocal, "idx");
        i64c(b, 0);
        set(b, iLocal, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, iLocal, "i");
        get(b, lenLocal, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        get(b, dataLocal, "data");
        get(b, iLocal, "i");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        get(b, 1, "key");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        set(b, eqLocal, "eq");
        get(b, eqLocal, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, lbl + "m", 0x40));
        get(b, iLocal, "i");
        set(b, idxLocal, "idx");
        b.add(new WasmInstr.Branch(lbl, 2));                     // 0=if,1=loop,2=externo
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "m", 0x40));
        get(b, iLocal, "i");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, iLocal, "i");
        b.add(new WasmInstr.Branch(lbl + "l", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl, 0x40));
    }

    /** O qword do valor do idx: [data + idx·16 + 8] empilhado. */
    static void loadVal(List<WasmInstr> b, int dataLocal, int idxLocal) {
        get(b, dataLocal, "data");
        get(b, idxLocal, "idx");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
    }

    /** O endereço [data + idx·16 + 8] empilhado (para o STORE64 do valor). */
    static void valAddr(List<WasmInstr> b, int dataLocal, int idxLocal) {
        get(b, dataLocal, "data");
        get(b, idxLocal, "idx");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
    }

    /** A caixa do valor por largura: 0 = Int/Char (o boxInt do qword cru);
     *  1 = Bool (o wrap de volta ao i32 antes do boxBool — a lei MAP-B);
     *  2 = Double (o reinterpret dos bits antes do boxDouble — a lei
     *  MAP-D). Empilha o HANDLE da caixa. */
    static void boxVal(List<WasmInstr> b, int kind) {
        if (kind == 2) {
            b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
            b.add(new WasmInstr.Call("kof.boxDouble"));
        } else if (kind == 1) {
            b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
            b.add(new WasmInstr.Call("kof.boxBool"));
        } else {
            b.add(new WasmInstr.Call("kof.boxInt"));
        }
    }

    /** `kof.mapGetKi{Int,Bool,Dbl}(h i32, key i64) -> i32` — o hit = a
     *  caixa por largura, o miss = 0 (o NULL — o boxToStr renderiza
     *  "null"; o MESMO contrato do mapGet/mapGetDbl/mapGetBool). */
    private static WasmFunc mapGetKiVal(String name, int kind) {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key(i64); 2=len, 3=data, 4=i, 5=idx, 6=eq, 7=res
        scanKi(b, "gk", 2, 3, 4, 5, 6);
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "hit", 0x40));
        // o HIT = a caixa do valor (o idx >= 0); o MISS = 0 (o NULL)
        loadVal(b, 3, 5);
        boxVal(b, kind);
        set(b, 7, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "hit", 0x40));
        i32c(b, 0);
        set(b, 7, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "hit", 0x40));
        get(b, 7, "res");
        // o mapa REAL: 2=len(I64), 3=data(I32), 4=i(I64), 5=idx(I64),
        // 6=eq(I32), 7=res(I32 — o HANDLE da caixa)
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.mapPutKiRaw(h i32, key i64, v i64) -> i32` — o put da família
     *  Ki com o valor CRU (o Int/Char = o slot cru; o Double = os BITS
     *  reinterpretados no call site; o Bool = o estendido no call site —
     *  as leis MAP-D/B/C compostas com a MAP-KI). O res = o velho cru
     *  wrapado (nunca consume — o drop do KofPop). */
    static WasmFunc mapPutKiRaw() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=key(i64), 2=v(i64); 3=od, 4=nc, 5=nd, 6=gi (o grow — os
        // internos FIXOS do growBody); 7=len, 8=cap, 9=idx, 10=eq, 11=res
        // (o scan re-usa 3=data e 6=i DEPOIS do grow — os tipos casam)
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 7, "len");
        get(b, 0, "h");
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 8, "cap");
        // if len == cap: dobrar (o grow COMPARTILHADO — os internos 3..6)
        get(b, 7, "len");
        get(b, 8, "cap");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "grow", 0x40));
        WasmMapStrBodies.growBody(b, "gw", 0, 7, 8);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "grow", 0x40));
        scanKi(b, "pk", 7, 3, 6, 9, 10);
        get(b, 9, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "upd", 0x40));
        // achou: res = o velho cru wrapado; [data+idx·16+8] = v (CRU)
        loadVal(b, 3, 9);
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, 11, "res");
        valAddr(b, 3, 9);
        get(b, 2, "v");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "upd", 0x40));
        // anexa: [data+len·16] = key; [+8] = v (CRU — SEM extend); len+1
        get(b, 3, "data");
        get(b, 7, "len");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 1, "key");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 3, "data");
        get(b, 7, "len");
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 2, "v");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        get(b, 0, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 7, "len");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
        i32c(b, 0);
        set(b, 11, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "upd", 0x40));
        get(b, 11, "res");
        // o mapa REAL: 3=od/data(I32), 4=nc(I64), 5=nd(I32), 6=gi/i(I64),
        // 7=len(I64), 8=cap(I64), 9=idx(I64), 10=eq(I32), 11=res(I32)
        return new WasmFunc("kof.mapPutKiRaw", List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.mapGetKiInt(h i32, key i64) -> i32` — o Int (e o Char pela lei
     *  MAP-C: a MESMA caixa boxInt, só o rastro do despacho difere). */
    static WasmFunc mapGetKiInt() {
        return mapGetKiVal("kof.mapGetKiInt", 0);
    }

    /** `kof.mapGetKiBool(h i32, key i64) -> i32` — o boxBool do qword. */
    static WasmFunc mapGetKiBool() {
        return mapGetKiVal("kof.mapGetKiBool", 1);
    }

    /** `kof.mapGetKiDbl(h i32, key i64) -> i32` — o boxDouble dos bits. */
    static WasmFunc mapGetKiDbl() {
        return mapGetKiVal("kof.mapGetKiDbl", 2);
    }
}
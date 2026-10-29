package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * LIST-REC (29/10, #776): os ELEMENTOS de RECORD nas fatias de LISTA — as
 * BUSCAS por igualdade de CONTEÚDO (o contrato do `List.contains`/`indexOf`
 * + o equals de VALOR do record da JVM). O scan é o MESMO corpo das
 * variantes Str (`WasmListRuntime.scanBodyStr` — a busca linear pelos
 * slots, o primeiro/último índice, -1 ausente), com o compare = a chamada
 * do fold por-record `kof.recEq.<Rec>` COMPARTILHADO com o SET-REC (o
 * gerador package-visible em {@link WasmSetRecRuntime}; o dedupe por nome
 * antes da serialização cuida do dup quando o programa usa os dois). O
 * probe anda como HANDLE i32 CRU (sem extend — a MESMA disciplina das
 * variantes Str: o fold consome o par de handles), e o wrap do slot é o
 * MESMO da Str. Zero chamada indireta: o nome do fold é resolvido no
 * DESPACHO (o elemento do `List<Point>` escolhe `kof.recEq.Point` em
 * compile time).
 */
final class WasmListRecRuntime {

    private WasmListRecRuntime() {
    }

    /** A família por record: o fold compartilhado + as três buscas. */
    static List<WasmFunc> all(List<ClassLayout> recs, int recDepth,
            Map<String, ClassLayout> records) {
        List<WasmFunc> fs = new ArrayList<>();
        for (ClassLayout rl : recs) {
            fs.add(WasmSetRecRuntime.fold(rl, recDepth, records));
            fs.add(listContainsRec(rl));
            fs.add(listIndexOfRec(rl));
            fs.add(listLastIndexOfRec(rl));
        }
        return fs;
    }

    /** `kof.listContainsRec.<Rec>(h i32, r i32) -> i32` — 1 se contém (o
     *  MESMO corpo da containsStr com o compare = o fold). */
    private static WasmFunc listContainsRec(ClassLayout rl) {
        String name = "kof.listContainsRec." + rl.className();
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        scanBodyRec(b, "sc", 0, 1, 6, false, fold);
        get(b, 6, "res");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        set(b, 7, "found");
        get(b, 7, "found");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "f", 0x7f));
        i32c(b, 0);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "f", 0x7f));
        i32c(b, 1);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "f", 0x7f));
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.listIndexOfRec.<Rec>(h i32, r i32) -> i64` — o primeiro, -1. */
    private static WasmFunc listIndexOfRec(ClassLayout rl) {
        String name = "kof.listIndexOfRec." + rl.className();
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        scanBodyRec(b, "ix", 0, 1, 6, false, fold);
        get(b, 6, "res");
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64), b);
    }

    /** `kof.listLastIndexOfRec.<Rec>(h i32, r i32) -> i64` — o último, -1. */
    private static WasmFunc listLastIndexOfRec(ClassLayout rl) {
        String name = "kof.listLastIndexOfRec." + rl.className();
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        scanBodyRec(b, "lx", 0, 1, 6, true, fold);
        get(b, 6, "res");
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64), b);
    }

    /** O MESMO corpo de `WasmListRuntime.scanBodyStr` (a busca linear, o
     *  fantasma 0, o primeiro/último), com o compare = a chamada do fold:
     *  o slot Load64 → wrap (o handle) sobre o probe `r` (i32 CRU) — o fold
     *  consome o par e deixa o 1/0 i32. */
    private static void scanBodyRec(List<WasmInstr> b, String lbl,
            int hLocal, int rLocal, int resLocal, boolean fromEnd, String fold) {
        // 0=h, 1=r(handle i32); 2=len, 3=data, 4=i, 5=tmp, res
        get(b, hLocal, "h");
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        set(b, 2, "len");
        get(b, hLocal, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 3, "data");
        if (fromEnd) {
            get(b, 2, "len");
            i64c(b, 1);
            b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        } else {
            i64c(b, 0);
        }
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "i", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, 4, "i");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        get(b, 4, "i");
        get(b, 2, "len");
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        // if fold(wrap([data+i*8]), r): res = i; br externo
        get(b, 3, "data");
        get(b, 4, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));       // o handle do slot
        get(b, rLocal, "r");
        b.add(new WasmInstr.Call(fold));
        set(b, 5, "eq");                                         // i32 bool
        get(b, 5, "eq");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, lbl + "m", 0x40));
        get(b, 4, "i");
        set(b, resLocal, "res");
        b.add(new WasmInstr.Branch(lbl, 3));                     // 0=if,1=loop,2=inner,3=outer
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "m", 0x40));
        get(b, 4, "i");
        if (fromEnd) {
            i64c(b, 1);
            b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        } else {
            i64c(b, 1);
            b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        }
        set(b, 4, "i");
        b.add(new WasmInstr.Branch(lbl + "l", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "i", 0x40));
        i64c(b, -1);
        set(b, resLocal, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl, 0x40));
    }
}

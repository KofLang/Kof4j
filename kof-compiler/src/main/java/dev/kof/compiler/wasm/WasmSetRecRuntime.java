package dev.kof.compiler.wasm;

import dev.kof.compiler.ClassLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * SET-REC (29/10, #776): os ELEMENTOS de RECORD nas fatias de set — a
 * igualdade de CONTEÚDO (o contrato do `HashSet` + o equals de VALOR do
 * record da JVM). O fold de campo já existe como expansão de call site
 * (`WasmRecordCode.emitEquals`, 15.3d inc2 fatia C2) — aqui ele vira função
 * autônoma POR-RECORD `kof.recEq.<Rec>(L i32, R i32) -> i32` (o MESMO corpo,
 * os scratches eqAcc/eqEq/eqCmp/eqDbl/eqAnd + os pares de aninhamento como
 * LOCAIS da função), e o scan ganha os ops por-record
 * `kof.setAddRec.<Rec>` / `kof.setContainsRec.<Rec>` / `kof.setRemoveRec.<Rec>`
 * — a MESMA forma de scan dos ops de String (`WasmSetStrRuntime`), com o
 * compare = wrap+wrap+CALL do fold em vez do `kof.strEq`. O valor do slot é
 * o handle de record i32 ZERO-ESTENDIDO no slot i64 (a LEI DO SLOT DE VALOR
 * de classe/record — o extend no call site, o wrap de volta no runtime).
 * Nenhuma chamada indireta: o nome do fold é resolvido no DESPACHO (o
 * elemento do `Set<Point>` escolhe `kof.recEq.Point` em compile time).
 */
final class WasmSetRecRuntime {

    private WasmSetRecRuntime() {
    }

    /** A família por record: o fold + os três ops de scan (o chamador
     *  registra quando um `Set<X>` com X record aparece no programa). */
    static List<WasmFunc> all(List<ClassLayout> recs, int recDepth,
            Map<String, ClassLayout> records) {
        List<WasmFunc> fs = new ArrayList<>();
        for (ClassLayout rl : recs) {
            fs.add(fold(rl, recDepth, records));
            fs.add(setAddRec(rl));
            fs.add(setContainsRec(rl));
            fs.add(setRemoveRec(rl));
        }
        return fs;
    }

    /** `kof.recEq.<Rec>(L i32, R i32) -> i32` — o fold de campo do record
     *  como função autônoma: o corpo = o MESMO `emitEquals` do call site (a
     *  perna de null dupla = Objects.equals, o AND por accumulator, o strEq
     *  do String, os bits do Double = Double.equals, a recursão do
     *  aninhado); os scratches viram LOCAIS da função. LIST-REC (29/10,
     *  #776): package-visible — o scan da LISTA compartilha o MESMO fold
     *  (o dedupe por nome antes da serialização cuida do dup). */
    static WasmFunc fold(ClassLayout rl, int recDepth,
            Map<String, ClassLayout> records) {
        String name = "kof.recEq." + rl.className();
        // locals: 0=L, 1=R (params); 2=eqAcc, 3=eqEq, 4=eqCmp, 5=eqDbl(i64),
        // 6=eqAnd; 7+2*(d-1) = nestA[d], +1 = nestB[d] (d = 1..recDepth)
        List<Integer> locals = new ArrayList<>();
        locals.add(WasmFunc.TYPE_I32);
        locals.add(WasmFunc.TYPE_I32);
        locals.add(WasmFunc.TYPE_I32);
        locals.add(WasmFunc.TYPE_I64);
        locals.add(WasmFunc.TYPE_I32);
        int next = 7;
        int[] nestA = new int[recDepth + 1];
        int[] nestB = new int[recDepth + 1];
        for (int d = 1; d <= recDepth; d++) {
            nestA[d] = next;
            nestB[d] = next + 1;
            locals.add(WasmFunc.TYPE_I32);
            locals.add(WasmFunc.TYPE_I32);
            next += 2;
        }
        WasmLowering.Ctx c = new WasmLowering.Ctx(Map.of(), -1, Map.of(), name,
                new HashMap<>(), new ArrayList<>(), records,
                new WasmLowering.DataCursor(), new HashMap<>());
        c.eqAccIdx = 2;
        c.eqEqIdx = 3;
        c.eqCmpIdx = 4;
        c.eqDblIdx = 5;
        c.eqAndIdx = 6;
        c.nestA = nestA;
        c.nestB = nestB;
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "L");
        get(b, 1, "R");
        WasmRecordCode.emitEquals(rl, c, b, 0);
        return new WasmFunc(name, List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), locals, b);
    }

    /** `kof.setAddRec.<Rec>(h i32, v i64) -> i32` — o MESMO scan de DEDUP do
     *  `kof.setAdd` (a lei dos dois blocos), com o compare = o fold. */
    private static WasmFunc setAddRec(ClassLayout rl) {
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=cap, 4=data, 5..8 grow, 9=i, 10=tmp, 11=res,
        // 12=aL, 13=bL
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
        WasmSetRuntime.growBody(b, 0, 2, 3);
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
        foldCompare(b, fold, 4, 9, 12, 13, 1);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "am", 0x40));
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
        set(b, 11, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ak", 0x40));
        get(b, 11, "res");
        return new WasmFunc("kof.setAddRec." + rl.className(),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setContainsRec.<Rec>(h i32, v i64) -> i32` — o MESMO scan do
     *  `kof.setContains`, com o compare = o fold. */
    private static WasmFunc setContainsRec(ClassLayout rl) {
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=data, 4=i, 5=found, 6=aL, 7=bL
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
        foldCompare(b, fold, 3, 4, 6, 7, 1);
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
        return new WasmFunc("kof.setContainsRec." + rl.className(),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.setRemoveRec.<Rec>(h i32, v i64) -> i32` — o MESMO scan do
     *  `kof.setRemove` (o shift com a GE_S da lei do S12), com o compare =
     *  o fold. */
    private static WasmFunc setRemoveRec(ClassLayout rl) {
        String fold = "kof.recEq." + rl.className();
        List<WasmInstr> b = new ArrayList<>();
        // 0=h, 1=v; 2=len, 3=data, 4=i, 5=idx(-1=ausente), 6=j, 7=tmp,
        // 8=rem, 9=aL, 10=bL
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
        foldCompare(b, fold, 3, 4, 9, 10, 1);
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
        get(b, 5, "idx");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "has", 0x40));
        i32c(b, 1);
        set(b, 8, "rem");
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
        set(b, 8, "rem");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "has", 0x40));
        get(b, 8, "rem");
        return new WasmFunc("kof.setRemoveRec." + rl.className(),
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** O site de compare do scan: [data, i] no slot `data`/`i`; deixa o 1/0
     *  (i32) do fold no topo — o slot de record = o handle i32
     *  zero-estendido (o wrap de volta), a sonda = o param `v` (i64). */
    private static void foldCompare(List<WasmInstr> b, String fold, int dataIdx,
            int iIdx, int aLoc, int bLoc, int vLoc) {
        get(b, dataIdx, "data");
        get(b, iIdx, "i");
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, aLoc, "aL");
        get(b, vLoc, "v");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, bLoc, "bL");
        get(b, aLoc, "aL");
        get(b, bLoc, "bL");
        b.add(new WasmInstr.Call(fold));
    }
}

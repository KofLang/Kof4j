package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * GC-1 (29/10, #776): o coletor mark-sweep sobre a memória linear WASM — o
 * design NATIVO (G-0..G-4, `native-multiarch.md`, medido: `KofGcE2ETest`
 * 9/9) re-expressado, por `D-WASM-GO`/`D-WASM-04`/`D-WASM-05` ("objects =
 * handles + handle table; GC reuses the native design"). O contrato
 * OBSERVÁVEL = o do nativo G-6(a): o allocator, na EXAUSTÃO (o bump sem
 * espaço), COLETA e recicla — o programa só funciona, sem API de usuário.
 *
 * O LAYOUT: todo bloco `kof.heapAlloc`'d ganha o cabeçalho de 8 bytes ANTES
 * do dado — `[size31|mark1][gcnext]` — e o HANDLE CONTINUA SENDO O PONTEIRO
 * DO DADO (todos os runtimes existentes inalterados). Os blocos vivem na
 * gc-list (global 1); os livres, na free-list (global 2). O root buffer é a
 * região estática ANTES do primeiro bloco — os endereços rootBuf/heapFirst
 * são CONSTANTES assadas no código pelo backend. O mark = as raízes (os
 * spills do post-pass) + a caminhada CONSERVADORA do conteúdo por gray-list
 * intrusiva (a lição do §637/§639: ITERATIVA, stack O(1)); o over-mark = a
 * MESMA classe de comportamento do scan de stack conservador do nativo. O
 * sweep = a caminhada da gc-list: não marcado → a free-list; marcado → o
 * desmarca. O strAlloc anda por heapAlloc(n+4) preservando o layout
 * `[len]`-no-handle do String; o buf dos args fica um bump direto PINADO
 * (nunca na gc-list — o sweep nunca o vê).
 */
final class WasmGcRuntime {

    private WasmGcRuntime() {
    }

    /** Os globals do coletor: 0 = o bump (o layout inalterado), 1 = gcHead,
     *  2 = freeHead. O rootBuf/heapFirst = constantes assadas. */
    static final int GC_HEAD = 1;
    static final int FREE_HEAD = 2;
    /** O bit da marca no word do cabeçalho (o top bit do size31). */
    private static final int MARK_BIT = 0x80000000;
    private static final int SIZE_MASK = 0x7fffffff;

    /** `kof.heapAlloc(size i32) -> i32` — o CONTRATO continua "o ponteiro
     *  do dado": free-list first-fit → bump → collect → free-list → bump →
     *  memory.grow(1) → bump → trap (a OOM honesta). */
    public static WasmFunc heapAlloc() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=size(param), 1=h, 2=g
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryFree"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h1", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h1", 0x40));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryBump"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h2", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h2", 0x40));
        b.add(new WasmInstr.Call("kof.gcCollect"));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryFree"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h3", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h3", 0x40));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryBump"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h4", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h4", 0x40));
        i32c(b, 1);
        b.add(new WasmInstr.SimpleExt(0x40, 0, "memory.grow(1)"));
        set(b, 2, "g");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "g"));
        i32c(b, -1);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "oom", 0x40));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "oom", 0x40));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryBump"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        return new WasmFunc("kof.heapAlloc", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }


    /** `kof.heapAllocNG(size i32) -> i32` — a variante SEM COLETA (GC-2):
     *  free-list → bump → memory.grow → trap. Usada nos allocs de nível de
     *  programa com a pilha de expressão PENDENTE (depthAt > 0) — os
     *  intermediários da pilha são invisíveis ao spill (o native os cobre
     *  pelo scan de stack conservador; a WASM não pode), então NUNCA
     *  coletam: crescem. */
    public static WasmFunc heapAllocNG() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=size(param), 1=h, 2=g
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryFree"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h1", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h1", 0x40));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryBump"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "h2", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        b.add(WasmInstr.Branch.ret());
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "h2", 0x40));
        i32c(b, 1);
        b.add(new WasmInstr.SimpleExt(0x40, 0, "memory.grow(1)"));
        set(b, 2, "g");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "g"));
        i32c(b, -1);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "oom", 0x40));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "oom", 0x40));
        get(b, 0, "size");
        b.add(new WasmInstr.Call("kof.gcTryBump"));
        set(b, 1, "h");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "h"));
        return new WasmFunc("kof.heapAllocNG", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.gcTryFree(size i32) -> i32` — o first-fit da free-list (SEM
     *  split — o bloco inteiro serve; o desperdício = v1 honesto). O bloco
     *  sai da free-list e ENTRA na gc-list. -1 = nada serve. */
    static WasmFunc gcTryFree() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=size(param), 1=prev, 2=cur, 3=sz, 4=res, 5=nxt
        i32c(b, 0);
        set(b, 1, "prev");
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, FREE_HEAD));
        set(b, 2, "cur");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "fx", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "fn", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "fl", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Branch("fn", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        i32c(b, SIZE_MASK);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 3, "sz");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "sz"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "size"));
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "fit", 0x40));
        // serve: o unlink da free-list + o re-init do cabeçalho + a gc-list
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 5, "nxt");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "prev"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "nxt"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, FREE_HEAD));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "prev"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "nxt"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 4));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "sz"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // o mark = 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, GC_HEAD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // gcnext = gcHead
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, GC_HEAD));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // graynext = 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 12);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // pad = 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 4, "res");
        b.add(new WasmInstr.Branch("fx", 3));                   // 0=if,1=loop,2=inner,3=outer
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "fit", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        set(b, 1, "prev");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "cur"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 2, "cur");
        b.add(new WasmInstr.Branch("fl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "fl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "fn", 0x40));
        i32c(b, -1);
        set(b, 4, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "fx", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "res"));
        return new WasmFunc("kof.gcTryFree", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32), b);
    }

    /** `kof.gcTryBump(size i32) -> i32` — o bump se couber na memória
     *  linear atual (memory.size × 64Ki); senão -1. O bloco entra na
     *  gc-list. */
    static WasmFunc gcTryBump() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=size(param), 1=base, 2=end, 3=lim, 4=res
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        i32c(b, 7);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, -8);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 1, "base");
        b.add(new WasmInstr.SimpleExt(0x3f, 0, "memory.size"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));           // pages × 65536
        set(b, 3, "lim");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "size"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 2, "end");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "end"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "lim"));
        b.add(new WasmInstr.Simple(0x4d, "i32.le_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "room", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "size"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // hdr.size (o mark = 0)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, GC_HEAD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // gcnext = gcHead
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, GC_HEAD));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // graynext = 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        i32c(b, 12);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 0);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // pad = 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "end"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));   // o bump
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "base"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 4, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "room", 0x40));
        i32c(b, -1);
        set(b, 4, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "room", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "res"));
        return new WasmFunc("kof.gcTryBump", List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** `kof.gcCollect()` — o mark (as raízes do root buffer + a caminhada
     *  conservadora do conteúdo por gray-list intrusiva ITERATIVA — a lição
     *  do §637/§639) + o sweep (a gc-list → a free-list / o desmarca).
     *  Puro — não aloca. Os endereços rootBuf/heapFirst = CONSTANTES
     *  assadas pelo backend. */
    static WasmFunc gcCollect(int rootBuf, int rootSlots, int heapFirst) {
        List<WasmInstr> b = new ArrayList<>();
        // 0=gray, 1=hdr, 2=sz, 3=i, 4=w-entrada-do-tryMark, 5=cur, 6=nxt,
        // 7=prev, 8=tmp, 9=tHdr (o tryMark), 10=tW (o tryMark)
        i32c(b, 0);
        set(b, 0, "gray");
        // (A) as raízes: cada slot [0, rootSlots) → tryMark
        for (int s = 0; s < rootSlots; s++) {
            i32c(b, rootBuf + 4 * s);
            b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
            tryMark(b, heapFirst);
        }
        // (B) a caminhada da gray-list: o conteúdo conservador
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "gw", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "gl", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "gray"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Branch("gw", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "gray"));
        set(b, 1, "hdr");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "hdr"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));        // o próximo da gray ([hdr+8])
        set(b, 0, "gray");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "hdr"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        i32c(b, SIZE_MASK);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 2, "sz");
        i32c(b, 0);
        set(b, 3, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "cw", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "cl", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "sz"));
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Branch("cw", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "hdr"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));        // a palavra do conteúdo
        tryMark(b, heapFirst);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 3, "i");
        b.add(new WasmInstr.Branch("cl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cw", 0x40));
        b.add(new WasmInstr.Branch("gl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "gw", 0x40));
        // (C) o sweep: a gc-list → a free-list / o desmarca
        i32c(b, 0);
        set(b, 7, "prev");
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, GC_HEAD));
        set(b, 5, "cur");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sx", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sn", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "sl", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Branch("sn", 1, true));
        // nxt = [cur+4] (o link da gc-list) — GC-3 (29/10, #776): o set
        // direto do CUR era o spin infinito (o nxt = o próprio cur); o
        // G1..G4 passavam porque o rewrite NG impedia o coletor de
        // disparar nos churns de runtime — o G5 foi o primeiro a rodá-lo
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 6, "nxt");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 8, "tmp");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "tmp"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));          // o bit31 = negativo
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "mk", 0x40));
        // marcado: desmarca e segue
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "tmp"));
        i32c(b, SIZE_MASK);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        set(b, 7, "prev");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "nxt"));
        set(b, 5, "cur");
        b.add(new WasmInstr.Branch("sl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "mk", 0x40));
        // livre: o unlink da gc-list + a free-list
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "prev"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "nxt"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, GC_HEAD));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "prev"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "nxt"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 4));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ph", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, FREE_HEAD));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // next = freeHead
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "cur"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, FREE_HEAD));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "nxt"));
        set(b, 5, "cur");
        b.add(new WasmInstr.Branch("sl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "mk", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sn", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sx", 0x40));
        return new WasmFunc("kof.gcCollect", List.of(),
                List.of(), List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** O tryMark INLINE (o valor na pilha): se [heapFirst, heapTop), o
     *  TAMANHO é SANOS (tW + tSz ≤ heapTop — GC-3: o falso-positivo
     *  conservador — o escalar i64 grande aliado como ponteiro leria o
     *  "cabeçalho" lixo e o tamanho enorme andava OOB) e o bloco não
     *  marcado: marca e empurra na gray-list. Os scratches 9/10/11/12 =
     *  DEDICADOS (nunca colidem com os locals do chamador). */
    private static void tryMark(List<WasmInstr> b, int heapFirst) {
        // [v]; 9=tHdr, 10=tW, 11=tW0, 12=tSz
        set(b, 10, "tW");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "tW"));
        i32c(b, heapFirst);
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "tW"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "tm", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "tW"));
        i32c(b, 16);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 9, "tHdr");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "tHdr"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 11, "tW0");
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 11, "tW0"));
        i32c(b, SIZE_MASK);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        set(b, 12, "tSz");
        // a sanidade: tW + tSz ≤ heapTop (o tamanho cabe na memória) — o
        // falso-positivo com o tamanho lixo é rejeitado aqui (Q0)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 10, "tW"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 12, "tSz"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Simple(0x4e, "i32.le_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ok", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 11, "tW0"));
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "md", 0x40));
        // já marcado: nada
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "md", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "tHdr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 11, "tW0"));
        i32c(b, MARK_BIT);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // o mark
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "tHdr"));
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "gray"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));       // hdr.graynext = gray
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "tHdr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 0, "gray"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "md", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ok", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "tm", 0x40));
    }
}

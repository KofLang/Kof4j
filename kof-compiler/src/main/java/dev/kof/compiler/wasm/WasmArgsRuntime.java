package dev.kof.compiler.wasm;

import java.util.*;

import static dev.kof.compiler.wasm.WasmStdoutRuntime.*;

/**
 * Runtime WASI-preview1 de `args` (TIER 15 unidade 15.3 fatia B, issue #776):
 * `kof.readArgs` baixa argv (`args_sizes_get`/`args_get`) para handles
 * KofString `[len][bytes]\n` num array `[count][handle...]` no heap (global 0),
 * descartando argv[0] (paridade com `main(String[] args)` da JVM).
 */
public final class WasmArgsRuntime {

    private WasmArgsRuntime() {
    }

    /**
     * 15.3c-sliceB: `kof.readArgs` via WASI-preview1 `args_sizes_get`/`args_get`.
     * Devolve handle de array `[count i32][handle String x count]` no heap
     * (global 0); argv[0] (nome do programa) e descartado — paridade com o
     * `main(String[] args)` da JVM. Prova WAT isolada antes do bake.
     */
    public static WasmFunc kofReadArgs() {
        List<WasmInstr> b = new ArrayList<>();
        // 0 argc, 1 sz, 2 tbl, 3 buf, 4 arr, 5 count, 6 i, 7 p, 8 len, 9 h, 10 k
        b.add(new WasmInstr.Const(0, SCRATCH_ARGC));
        b.add(new WasmInstr.Const(0, SCRATCH_ARGSZ));
        b.add(new WasmInstr.Call("args_sizes_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Const(0, SCRATCH_ARGC));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 0, "argc"));
        b.add(new WasmInstr.Const(0, SCRATCH_ARGSZ));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "sz"));
        // count = argc > 1 ? argc - 1 : 0 (argv[0] = programa)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "argc"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "argc"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "count"));
        b.add(new WasmInstr.Branch(null, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "count"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        // tbl = heap; heap += argc*4 (alinhado 4)
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "tbl"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "argc"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        // buf = heap; heap += sz
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "buf"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "sz"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "tbl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "buf"));
        b.add(new WasmInstr.Call("args_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        // arr = heap; heap += 4 + count*4; [arr] = count
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "arr"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "count"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "arr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "count"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // i = 0; i < count; i++ — p = tbl[(i+1)*4]; len=strlen(p); h=strLit(p,len); arr[4+i*4]=h
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "argsall", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "argsall_c", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "count"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("argsall", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "tbl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "p"));
        // len = 0; while (load8u(p+len) != 0) len++
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "len"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sz15", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "sz15_c", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "p"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch("sz15", 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "len"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "len"));
        b.add(new WasmInstr.Branch("sz15_c", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sz15_c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sz15", 0x40));
        // h = heap; heap += len + 5; [h] = len; h+4 = copia de p[0..len); [h+4+len] = '\n'
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 9, "h"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, 5));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "len"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        copyInto(b, 7, 9, 8, 10, "argcp", 4);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        // [arr + 4 + i*4] = h
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "arr"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "i"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "i"));
        b.add(new WasmInstr.Branch("argsall_c", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "argsall_c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "argsall", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "arr"));
        return new WasmFunc("kof.readArgs", List.of(), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /** copia `n` bytes de `srcLocal` para `dstLocal+baseOff` (loop slot iIdx). */
    private static void copyInto(List<WasmInstr> b, int src, int dst, int n,
                                 int iIdx, String lbl, int baseOff) {
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, iIdx, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "_c", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, iIdx, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, n, "n"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, dst, "d"));
        b.add(new WasmInstr.Const(0, baseOff));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, iIdx, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, src, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, iIdx, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, iIdx, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, iIdx, "i"));
        b.add(new WasmInstr.Branch(lbl + "_c", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "_c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl, 0x40));
    }
}

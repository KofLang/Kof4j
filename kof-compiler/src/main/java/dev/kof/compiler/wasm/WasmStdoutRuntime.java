package dev.kof.compiler.wasm;

import dev.kof.compiler.*;

import java.util.*;

/**
 * Camada WASI-preview1 de stdout da fatia 1 (TIER 15 unidade 15.3, issue
 * #776): o layout da pagina linear do host e os helpers emitidos
 * `kof.writeInt`/`kof.writeBool`/`kof.writeChar`, que escrevem pelo import
 * `wasi_snapshot_preview1.fd_write`. Responsabilidade separada do backend:
 * binario/IR fica em `WasmBackend`, runtime de saida fica aqui (R6/Q7;
 * prova `WasmWasiE2ETest` — stdout == oracle JVM sob wasmtime).
 */
public final class WasmStdoutRuntime {

    /** layout da pagina linear do host 15.3 (nwritten / iovec / buffer). */
    public static final int SCRATCH_NWRITTEN = 8, SCRATCH_IOVEC = 16, SCRATCH_OUT = 256;

    /** base do pool de strings 15.3b (data segments; acima do scratch). */
    public static final int DATA_BASE = 1024;

    private WasmStdoutRuntime() {
    }

    public static WasmFunc kofWriteInt() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 20));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Const(1, -1));
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 0, "v"));
        b.add(new WasmInstr.Branch(null, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "itoa", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Const(1, 10));
        b.add(new WasmInstr.Simple(0x81, "i64.rem_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "d"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Const(1, 10));
        b.add(new WasmInstr.Simple(0x7f, "i64.div_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 0, "v"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "w"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.TEE, 2, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "d"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Const(0, '0'));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        b.add(new WasmInstr.Branch("itoa", 0, true));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "itoa", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "w"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.TEE, 2, "w"));
        b.add(new WasmInstr.Const(0, '-'));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "w"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 21));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "w"));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 20));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_NWRITTEN));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        return new WasmFunc("kof.writeInt", List.of(0x7e), List.of(),
                List.of(0x7e, 0x7f, 0x7f), b);
    }

    public static WasmFunc kofWriteBool() {
        List<WasmInstr> b = new ArrayList<>();
        // [addr,value] pares para 5 bytes; len 4 (true) ou 5? 'true'/'false' = 4/5
        emitStore8(b, 't', 256);
        emitStore8(b, 'r', 257);
        emitStore8(b, 'u', 258);
        emitStore8(b, 'e', 259);
        emitStore8(b, '\n', 260);
        emitStore8(b, 'f', 261);
        emitStore8(b, 'a', 262);
        emitStore8(b, 'l', 263);
        emitStore8(b, 's', 264);
        emitStore8(b, 'e', 265);
        emitStore8(b, '\n', 266);
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x40));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 256));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Const(0, 5));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Branch(null, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x40));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 261));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x40));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_NWRITTEN));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        return new WasmFunc("kof.writeBool", List.of(0x7f), List.of(), List.of(), b);
    }

    private static void emitStore8(List<WasmInstr> b, int ch, int addr) {
        b.add(new WasmInstr.Const(0, addr));
        b.add(new WasmInstr.Const(0, ch));
        b.add(new WasmInstr.Store8(0));
    }

    public static WasmFunc kofWriteChar() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        // buffer = char (i32, byte baixo — subset ASCII) + '\n'
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "c"));
        b.add(new WasmInstr.Store8(0));
        emitStore8(b, '\n', SCRATCH_OUT + 1);
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_NWRITTEN));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        return new WasmFunc("kof.writeChar", List.of(0x7f), List.of(), List.of(), b);
    }


    /** 15.3b: `kof.writeString(addr,len)` — iovec aponta o data segment e
     * um '\n' ocupa o byte reservado apos a string. */
    public static WasmFunc kofWriteString() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "addr"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "len"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "addr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_NWRITTEN));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        return new WasmFunc("kof.writeString", List.of(0x7f, 0x7f), List.of(), List.of(), b);
    }
}

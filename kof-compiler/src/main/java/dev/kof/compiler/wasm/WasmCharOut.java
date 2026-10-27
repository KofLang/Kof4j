package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmStdoutRuntime.SCRATCH_IOVEC;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.SCRATCH_NWRITTEN;
import static dev.kof.compiler.wasm.WasmStdoutRuntime.SCRATCH_OUT;

/**
 * `println(Char)` do runtime WASI (TIER 15, #776): codifica o code unit UTF-16
 * em UTF-8 (1-3 bytes) e escreve pelo import `fd_write`. Separado de
 * {@link WasmStdoutRuntime} pelo gate `check_500`.
 *
 * <p>Espelha o oraculo JVM MEDIDO: `"café".charAt(3)` sai como C3 A9 (2 bytes),
 * `"a€b".charAt(1)` como E2 82 AC (3), ASCII como 1 byte, e um code unit
 * surrogate ISOLADO sai como `?` (o encoder do JVM troca por replacement — um
 * par completo nao cabe num `Char` do Kof).
 */
final class WasmCharOut {

    private WasmCharOut() {
    }

    static WasmFunc kofWriteChar() {
        // Char chega como i64 (emitLiteral 15.2) -> wrap = code unit UTF-16.
        // Codifica em UTF-8 no SCRATCH_OUT (1..3 bytes) e escreve os bytes + '\n'.
        // Espelha o oraculo JVM medido: 'e' acentuado = 2 bytes, 'euro' = 3,
        // 'A' = 1, e um code unit surrogate ISOLADO -> '?' (replacement do JVM;
        // um par completo nao cabe num Char do Kof).
        // Locais: 0=c(i64 param); 1=cw(i32); 2=len(i32)
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "c"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "cw"));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "len"));
        // surrogate isolado (D800..DFFF)? -> '?'
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0xD800));
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0xDFFF));
        b.add(new WasmInstr.Simple(0x4d, "i32.le_u"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sur", 0x40));
        b.add(new WasmInstr.Const(0, '?'));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "cw"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sur", 0x40));
        // >= 0x80 ? senao ASCII (1 byte)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ne", 0x40));
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "len"));
        // 110xxxxx
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Store8(0));
        // >= 0x800 -> 3 bytes (sobrescreve o inicio, len=3)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0x800));
        b.add(new WasmInstr.Simple(0x4f, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "b3", 0x40));
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "len"));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 12));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Const(0, SCRATCH_OUT + 2));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "b3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ne", 0x40));
        // ASCII: 1 byte
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "cw"));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ne", 0x40));
        // '\n' logo apos os bytes do caractere
        b.add(new WasmInstr.Const(0, SCRATCH_OUT));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, '\n'));
        b.add(new WasmInstr.Store8(0));
        // iovec.len = len + 1
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC + 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "len"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_IOVEC));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Const(0, SCRATCH_NWRITTEN));
        b.add(new WasmInstr.Call("fd_write"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        return new WasmFunc("kof.writeChar", List.of(0x7e), List.of(), List.of(0x7f, 0x7f), b);
    }

    /**
     * `kof.writeDouble(v)` — `println(Double)` (unidade 15.3, Fase E 27/10).
     *
     * <p>A construcao do rendering mora em `kof.doubleToStr` — desde a Fase E
     * do Schubfach (27/10) o rendering e' o `Double.toString` COMPLETO
     * (shortest-round-trip byte-a-byte com o JDK). writeDouble = str + write,
     * UMA escrita (o writeStr acrescenta o \n).
     *
     * <p>Locais: 0 = v (f64)
     */
    static WasmFunc kofWriteDouble() {
        List<WasmInstr> b = new ArrayList<>();
        // Fase E (27/10): a construcao do handle mora em kof.doubleToStr
        // (os traps honestos tambem — o despacho da caixa kof.boxToStr reusa);
        // writeDouble = str + write, UMA escrita (o writeStr acrescenta o \n).
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v")); // o param NAO flui sozinho
        b.add(new WasmInstr.Call("kof.doubleToStr"));
        b.add(new WasmInstr.Call("kof.writeStr"));
        return new WasmFunc("kof.writeDouble", List.of(WasmFunc.TYPE_F64), List.of(),
                List.of(), b);
    }

    /**
     * `kof.doubleToStr(v) -> handle` — o `Double.toString` COMPLETO (Fase E,
     * 27/10, #776): delega ao driver Schubfach `kof.schubFull` (a re-emissao
     * WASM do asm nativo PROVADO `RuntimeDtoaSchubfachCore`/`Double`,
     * transcricao valida pelo interpretador op-a-op contra o `Double.toString`
     * do JVM em 28 casos — 3 estagios: to_decimal, format(f,e), composto).
     * Fecha o teto antigo (fracionario/expoenencial trapavam): agora fracao,
     * exponencial, subnormal, NaN/±Inf e ±0.0 saem byte-a-byte com o JVM.
     *
     * <p>O handle devolvido e' ESTATICO ([len][bytes] no scratch SCHUB_OUT) —
     * cada chamada SOBRESCREVE o anterior, valido porque todo consumidor
     * (writeStr/strConcat/boxToStr) copia os bytes sincronamente.
     */
    public static WasmFunc kofDoubleToStr() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v")); // o param NAO flui sozinho
        b.add(new WasmInstr.Call("kof.schubFull"));
        return new WasmFunc("kof.doubleToStr", List.of(WasmFunc.TYPE_F64), List.of(WasmFunc.TYPE_I32),
                List.of(), b);
    }
}

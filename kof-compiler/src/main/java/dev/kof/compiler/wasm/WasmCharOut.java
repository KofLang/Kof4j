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
     * `kof.writeDouble(v)` — `println(Double)` para o subconjunto DECIMAL
     * (unidade 15.3, primeiro incremento).
     *
     * <p>O `Double.toString` COMPLETO exige o Schubfach shortest-round-trip
     * (`RuntimeDtoaSchubfach`, 500+ linhas de asm) para bater o JDK byte-a-byte.
     * Este incremento cobre so' o que o rendering decimal simples reproduz:
     * valores INTEGRAIS imprimem `<digitos>.0` (reusa `kof.intToStr`, ja'
     * provado). Qualquer valor com parte fracionaria TRAPA em vez de imprimir
     * uma string silenciosamente diferente (regra 5 / Q7) — a face exponencial
     * (`1.0E-4`, `1.0E7`) fica para o port do Schubfach.
     *
     * <p>Locais: 0 = v (f64); 1 = truncado (i64); 2 = h (i32 handle)
     */
    static WasmFunc kofWriteDouble() {
        List<WasmInstr> b = new ArrayList<>();
        // trunca: i64.trunc_f64_s (0xb0)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Simple(0xb0, "i64.trunc_f64_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));
        // se v != (f64)t -> nao-integral: trap honesto (nunca string errada)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "t"));
        b.add(new WasmInstr.Simple(0xb9, "f64.convert_i64_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Simple(0x62, "f64.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "frac", 0x40));
        b.add(new WasmInstr.Simple(0x00, "unreachable"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "frac", 0x40));
        // Monta UM handle "<digitos>.0" e escreve UMA vez: kof.writeStr
        // acrescenta o '\n', entao duas escritas imprimiriam duas linhas.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "t"));
        b.add(new WasmInstr.Call("kof.intToStr"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        // ZERO NEGATIVO (regressao medida 22/10): trunc(-0.0) = 0, entao o sinal
        // se perde antes do intToStr e saia "0.0" onde o JVM imprime "-0.0".
        // O bit de sinal e' legivel SEM truncar: i64.reinterpret_f64 < 0.
        // So' precisa do prefixo quando t == 0 (para nao-zero o intToStr ja'
        // carrega o '-', e prefixar de novo daria "--1.0").
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "v"));
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));       // sinal ligado?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "t"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));         // t == 0?
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "nz", 0x40));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Call("kof.strAlloc"));
        b.add(new WasmInstr.Const(0, '-'));
        b.add(new WasmInstr.Call("kof.strAppendByte"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "nh"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "nh"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Call("kof.strAppend"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "nz", 0x40));
        // h = intToStr ; h = appendByte(h, '.') ; h = appendByte(h, '0')
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, '.'));
        b.add(new WasmInstr.Call("kof.strAppendByte"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Const(0, '0'));
        b.add(new WasmInstr.Call("kof.strAppendByte"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "h"));
        b.add(new WasmInstr.Call("kof.writeStr"));
        return new WasmFunc("kof.writeDouble", List.of(WasmFunc.TYPE_F64), List.of(),
                // 1=t (i64); 2=h, 3=nh (i32)
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }
}

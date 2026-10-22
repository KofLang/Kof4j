package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de INSPECAO de String do runtime WASI (TIER 15, #776): `length`,
 * `isEmpty`, `startsWith`/`endsWith`. Separados de {@link WasmStdoutRuntime}
 * (que fica com escrita/formatacao) porque aquele arquivo cruzou a linha de 600
 * do gate `check_500` — a responsabilidade aqui e' CONSULTAR um handle
 * `[len][bytes]`, nao produzir um.
 *
 * <p>A armadilha comum desta familia: o handle guarda BYTES UTF-8, enquanto a
 * semantica do Kof e' de CODE UNITS UTF-16. `length` conta os leaders; os
 * afixos comparam bytes (UTF-8 e' prefix-free, entao a equivalencia vale);
 * `isEmpty` usa o comprimento em bytes (zero bytes <=> zero code units).
 */
final class WasmStrInspect {

    private WasmStrInspect() {
    }

    /**
     * `kof.strLen(h) -> i64`: numero de CODE UNITS UTF-16 de um handle
     * `[len][bytes]` — a semantica do `String.length()` do Kof (JVM).
     *
     * <p>O handle guarda o comprimento em BYTES UTF-8 (`internLen`), enquanto
     * `length()` conta code units UTF-16: `"café"` tem 5 bytes e 4 units, e um
     * emoji (4 bytes) vale 2 units (par surrogate). Ler o cabecalho do handle
     * daria o valor errado em toda string nao-ASCII — divergencia SILENCIOSA,
     * por isso a contagem percorre os bytes.
     *
     * <p>Regra de contagem por byte lider (UTF-8 e auto-sincronizante, so os
     * bytes lideres precisam ser classificados): `0xxxxxxx` = 1 unit; `110xxxxx`
     * e `1110xxxx` = 1 unit; `11110xxx` = 2 units. Bytes de continuacao
     * (`10xxxxxx`) nao contam.
     */
    static WasmFunc kofStrLen() {
        // Locais: 0 = handle (i32 param); 1 = n bytes (i32); 2 = i (i32);
        // 3 = u code units (i64, tipo de retorno do Kof Int); 4 = c byte (i32).
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));             // i32: [h] = bytes
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "n"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "slen_x", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "slen", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "n"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Branch("slen_x", 1, true));              // i >= n -> sai
        // c = byte em [h+4+i]
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "c"));
        // NAO-continuacao (byte lider: ASCII, 2/3/4-byte) -> e' ele que conta.
        // (c & 0xC0) != 0x80 distingue lider de byte de continuacao; bytes de
        // continuacao nao entram no IF e portanto nao contam.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "cont", 0x40));
        // 4-byte leader (11110xxx): 2 units (par surrogate). Um lider de 3 bytes
        // (1110xxxx) casa 0xE0..0xEF em &0xF8, nunca 0xF0.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xF8));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "four", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "u"));
        b.add(new WasmInstr.Const(1, 2));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "four", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "u"));
        b.add(new WasmInstr.Const(1, 1));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "four", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cont", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Branch("slen", 0));                      // continue INCONDICIONAL
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "slen", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "slen_x", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "u"));
        return new WasmFunc("kof.strLen", List.of(0x7f), List.of(0x7e),
                List.of(0x7f, 0x7f, 0x7e, 0x7f), b);
    }

    /**
     * `kof.strIsEmpty(h) -> 1/0`: `String.isEmpty()` do Kof.
     *
     * <p>Exato sem varredura: um handle com ZERO bytes UTF-8 tem zero code units
     * UTF-16 (a reciproca tambem vale), entao o comprimento em bytes decide.
     */
    static WasmFunc kofStrIsEmpty() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        return new WasmFunc("kof.strIsEmpty", List.of(0x7f), List.of(0x7f),
                List.of(), b);
    }

    /**
     * `kof.strStarts(a,b)` / `kof.strEnds(a,b)` -> 1/0: `String.startsWith` /
     * `endsWith` do Kof, comparados como PREFIXO/SUFIXO DE BYTES.
     *
     * <p>E' exato comparar bytes: UTF-8 e' <b>prefix-free</b> (nenhuma sequencia
     * valida e' prefixo de outra), entao se os bytes de `b` sao prefixo dos de
     * `a`, os code units UTF-16 de `b` sao exatamente o prefixo dos de `a` — e
     * vice-versa para sufixo. Isso dispensa a varredura de code units que o
     * `charAt` exige.
     *
     * @param ends {@code false} = startsWith; {@code true} = endsWith
     */
    static WasmFunc kofStrAffix(boolean ends) {
        // Locais: 0 = a, 1 = b; 2 = r (resultado); 3 = la; 4 = i; 5 = lb; 6 = base
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "a"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "la"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "b"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "lb"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "r"));
        // if (la >= lb) — o afixo nao pode ser maior que a string
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "la"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "lb"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pre", 0x40));
        // base = ends ? la-lb : 0
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "base"));
        if (ends) {
            b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "la"));
            b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "lb"));
            b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "base"));
        }
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "i"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "scan", 0x40));
        // if (i >= lb) { r = 1; sai } — prefixo/sufixo inteiro casou
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "lb"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "done", 0x40));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "r"));
        b.add(new WasmInstr.Branch("pre", 2));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "done", 0x40));
        // a[base+i] != b[i] -> r fica 0, sai. O +4 salta o cabecalho [len:i32]
        // nos DOIS handles (mesma convencao do kof.strEq).
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "a"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "base"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "b"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "bad", 0x40));
        b.add(new WasmInstr.Branch("pre", 2));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "bad", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "i"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "i"));
        b.add(new WasmInstr.Branch("scan", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "scan", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pre", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "r"));
        return new WasmFunc(ends ? "kof.strEnds" : "kof.strStarts",
                List.of(0x7f, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

}

package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fatiamento de String do runtime WASI (TIER 15, #776): mapeamento de indice de
 * CODE UNIT UTF-16 para offset de BYTE UTF-8 (`strByteOff`) e os metodos que
 * dependem dele (`charAt`, `substring`). Separado de {@link WasmStrInspect}
 * pelo gate `check_500`.
 *
 * <p>O mapeamento e' necessario porque o handle guarda bytes UTF-8: um indice
 * de code unit nao e' um offset de byte em nenhuma string nao-ASCII.
 */
final class WasmStrSlice {

    private WasmStrSlice() {
    }

    /**
     * `kof.strByteOff(h, u) -> i32`: offset de BYTE do code unit UTF-16 `u`.
     *
     * <p>Mapeia indice de code unit para indice de byte andando pelos leaders
     * (mesma classificacao do `length`). Devolve o offset relativo ao INICIO DOS
     * BYTES do handle (nao inclui o cabecalho de 4). `u` fora do intervalo
     * devolve o comprimento em bytes (o chamador decide o trap).
     *
     * <p>Locais: 0 = h, 1 = u (i64); 2 = n (i32 bytes), 3 = i (i32 byte),
     * 4 = c (i32), 5 = k (i64, units ja' contados).
     */
    static WasmFunc kofStrByteOff() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "n"));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "i"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "k"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "bo_x", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "bo", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "k"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "u"));
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        b.add(new WasmInstr.Branch("bo_x", 1, true));      // ja' contou o suficiente
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "n"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("bo_x", 1, true));      // fim: devolve n
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "c"));
        // Avanca i pela LARGURA INTEIRA da sequencia (1/2/3/4 bytes) e k por
        // 1 (ou 2 num par surrogate). Avancar i byte-a-byte pararia no meio de
        // um caractere multibyte.
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "w"));   // w = 1
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "w2", 0x40));
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "w"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "w2", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "w3", 0x40));
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "w"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "w3", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xF8));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "w4", 0x40));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "w"));
        // par surrogate -> 2 code units
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "k"));
        b.add(new WasmInstr.Const(1, 2));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "k"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "w4", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "k"));
        b.add(new WasmInstr.Const(1, 1));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "k"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "w4", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "w"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "i"));
        b.add(new WasmInstr.Branch("bo", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "bo", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "bo_x", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "i"));
        return new WasmFunc("kof.strByteOff", List.of(0x7f, 0x7e), List.of(0x7f),
                // locais 2..6: n,i,c,w (i32), k (i64)
                List.of(0x7f, 0x7f, 0x7f, 0x7e, 0x7f), b);
    }

    /** `kof.strSub(h, i, j) -> handle`: `substring(i, j)` — recorta os BYTES
     *  `[byteOff(i), byteOff(j))` num handle novo (bump heap global 0). */
    static WasmFunc kofStrSub() {
        List<WasmInstr> b = new ArrayList<>();
        // Locais: 0 = h, 1 = i (i64), 2 = j (i64); 3 = s (i32), 4 = e (i32),
        // 5 = len (i32), 6 = p (i32), 7 = k (i32)
        // `substring(i)` de 1 arg chega com o sentinela j = -1 ("ate' o fim"),
        // a mesma convencao do Native — o 0 seria um indice legitimo.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "j"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "neg", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "j"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "neg", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "i"));
        b.add(new WasmInstr.Call("kof.strByteOff"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "j"));
        b.add(new WasmInstr.Call("kof.strByteOff"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "e"));
        // len = e - s (o frontend ja' garante 0 <= i <= j <= length)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "e"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "s"));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "len"));
        // p = global 0; global 0 += len + 5 (cabecalho 4 + newline)
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "p"));
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "len"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Const(0, 5));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "p"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "len"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "k"));
        // Copia `len` bytes de a+4+s para p+4. BLOCK externo = alvo da saida:
        // sem ele o `br_if` com o label do LOOP apenas repetiria a iteracao.
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "cp_x", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "cp", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "k"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "len"));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("cp_x", 1, true));      // k >= len -> sai do BLOCK
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "p"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "k"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "s"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "k"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "k"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "k"));
        b.add(new WasmInstr.Branch("cp", 0));              // continua
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cp", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cp_x", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "p"));
        return new WasmFunc("kof.strSub", List.of(0x7f, 0x7e, 0x7e), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /** `kof.strCharAt(h, u) -> i64`: `charAt(u)`. Usa `kof.strByteOff` para achar
     *  o leader do code unit e decodifica a sequencia UTF-8; um par surrogate
     *  devolve o code unit ALTO (o par inteiro nao cabe num `Char` do Kof, que e'
     *  uma unidade — mesma semantica do `String.charAt` do JVM). */
    static WasmFunc kofStrCharAt() {
        List<WasmInstr> b = new ArrayList<>();
        // Locais: 0 = h, 1 = u (i64); 2 = o (i32 offset), 3 = c (i32);
        // 4 = n (i32 bytes restantes)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "u"));
        b.add(new WasmInstr.Call("kof.strByteOff"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "o"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ca_x", 0x7e));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "c"));
        // ASCII?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0x80));
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a1", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Branch("ca_x", 1));            // retorna no fim
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a1", 0x40));
        // 2-byte?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a2", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0x1F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Branch("ca_x", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a2", 0x40));
        // 3-byte?
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a3", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0x0F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 12));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 6));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Branch("ca_x", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a3", 0x40));
        // 4-byte: par surrogate — devolve o code unit ALTO (0xD800 | ((c-0xF0)<<8 ...))
        // Formula do JVM: high = 0xD800 + ((cp - 0x10000) >> 10)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "c"));
        b.add(new WasmInstr.Const(0, 0x07));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 18));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 12));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 6));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "o"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 7));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        // cp >= 0x10000 aqui sempre (lider de 4 bytes) -> high = 0xD800 + ((cp-0x10000)>>10)
        b.add(new WasmInstr.Const(0, 0x10000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Const(0, 10));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        b.add(new WasmInstr.Const(0, 0xD800));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ca_x", 0x7e));
        return new WasmFunc("kof.strCharAt", List.of(0x7f, 0x7e), List.of(0x7e),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }
}

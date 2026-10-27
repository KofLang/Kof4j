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
        // Desenho FINAL (os 3 anteriores falharam por fluxo, nao por decode):
        // o laco SO' localiza o caractere e guarda o code point + o numero de
        // code units JA' consumidos ANTES dele (k0). TODA a classificacao de
        // metade acontece DEPOIS do laco, reta, sem branch aninhado:
        //     wu = (w == 4) ? 2 : 1
        //     r  = (wu == 2 && u == k0+1) ? low(cp) : (wu == 2 ? high(cp)
        //                                                  : cp)
        // Locais: 0=h, 1=u(i64), 2=i(i32), 3=k(i64), 4=c(i32), 5=w(i32),
        // 6=wu(i32), 7=r(i32), 8=cp(i32), 9=k0(i64)
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "k"));
        b.add(new WasmInstr.Const(1, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 9, "k0"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "w"));
        b.add(new WasmInstr.Const(0, '?'));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "cp"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "cx", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "ca", 0x40));
        // fim da string -> '?' (cp fica '?', w fica 1 => r = '?')
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("cx", 1, true));
        // k0 = k (units antes DESTE caractere)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "k"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 9, "k0"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 4, "c"));
        // w = 1 por omissao; cp = c
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "cp"));
        // Classificacao pelo BYTE LIDER (nao pelo valor ja decodificado — um
        // lider de 4 bytes decodificado so' com 2 bytes pode dar valor < 0x800,
        // e testar o valor escolheria a largura errada). Bits do lider:
        //   110xxxxx (0xC0) = 2 | 1110xxxx (0xE0) = 3 | 11110xxx (0xF0) = 4
        // (c & 0xE0) == 0xC0 — a mascara TEM de ser 0xE0: com 0xC0 um lider de
        // 4 bytes (0xF0) tambem casa 0xC0 e cai no ramo de 2 bytes.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xC0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "d2", 0x40));
        // ---- 2 bytes ----
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0x1F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "cp"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "d2", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xE0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "d3", 0x40));
        // ---- 3 bytes ----
        b.add(new WasmInstr.Const(0, 3));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0x0F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 12));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 6));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "cp"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "d3", 0x40));
        // 4 bytes (11110xxx)? O ELSE acima cobre TUDO que nao e' 2/3 bytes —
        // inclusive ASCII — entao o teste do lider de 4 e' obrigatorio aqui.
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0xF8));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xF0));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "d4", 0x40));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 5, "w"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 4, "c"));
        b.add(new WasmInstr.Const(0, 0x07));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 18));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 5));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 12));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 6));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 6));
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "h"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 7));
        b.add(new WasmInstr.Const(0, 0x3F));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 8, "cp"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "d4", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "d3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "d2", 0x40));
        // ---- fim da classificacao ----
        // wu = (w == 4) ? 2 : 1
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "wu"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "w"));
        b.add(new WasmInstr.Const(0, 4));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "f4", 0x40));
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 6, "wu"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "f4", 0x40));
        // contem u?  k0 <= u < k0 + wu
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "k0"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "u"));
        b.add(new WasmInstr.Simple(0x57, "i64.le_s"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "k0"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "wu"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Branch("cx", 1, true));          // achou: sai do laco
        // avanca: k += wu ; i += w
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 3, "k"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "wu"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "k"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "i"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 5, "w"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "i"));
        b.add(new WasmInstr.Branch("ca", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ca", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "cx", 0x40));
        // ---- DEPOIS do laco, reto: escolhe a metade ----
        // r = cp (default: ASCII/2/3-byte, ou o '?' do fim)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "cp"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "r"));
        // se wu == 2 -> par surrogate: high ou low por u == k0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 6, "wu"));
        b.add(new WasmInstr.Const(0, 2));
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "par", 0x40));
        // u == k0 ? high : low
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "u"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 9, "k0"));
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "hi", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "cp"));
        b.add(new WasmInstr.Const(0, 0x10000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Const(0, 10));
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        b.add(new WasmInstr.Const(0, 0xD800));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "r"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "hi", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 8, "cp"));
        b.add(new WasmInstr.Const(0, 0x10000));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Const(0, 0x3FF));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Const(0, 0xDC00));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 7, "r"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "hi", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "par", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 7, "r"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_u"));
        return new WasmFunc("kof.strCharAt", List.of(0x7f, 0x7e), List.of(0x7e),
                // locais 2..8: i,c,w,wu,r,cp (i32); 3=k e 9=k0 (i64)
                List.of(0x7f, 0x7e, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7e), b);
    }
}

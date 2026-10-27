package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * Case fold de String do runtime WASI (Fase E, 26/10, #776): {@code toUpper}/
 * {@code toLower} com a MESMA semantica do nativo — D-STR-UNICODE (27/09, row
 * 11): fold Unicode SIMPLES por CODE UNIT ({@code Character.toUpperCase}/
 * {@code toLowerCase} por code point), NAO ASCII-only. O oracle e o fold
 * por-code-unit da JVM (as entradas evitam as excecoes de locale/
 * full-case-mapping fora do escopo ratificado: ß→SS, İ-desde-0130 exceto
 * lowercase, ﬁ, Deseret).
 *
 * <p>A tabela embarcada e' gerada do proprio JDK: pares
 * {@code [from u16][to u16]} little-endian, ordenados por {@code from},
 * cobrindo os BMP 0x80..0xFFFF cujo mapeamento nao e' identidade (~1.4k
 * entradas, ~5.6KB por direcao). A busca e' binaria. ASCII dobra inline;
 * astral (4 bytes) passa intocado (por-code-unit). A saida e' re-codificada
 * em UTF-8 com comprimento dinamico (duas passadas: o total exato, depois o
 * preenchimento via {@code strAppendByte} — o mapeamento simples pode MUDAR
 * o comprimento em bytes, ex. U+0130 (2B) → i (1B)).
 */
final class WasmStrCase {

    private WasmStrCase() {
    }

    /** Os mapeamentos BMP nao-identidade, na ordem de codigo: [f0,t0,f1,t1,...]. */
    private static int[] mappings(boolean upper) {
        int n = 0;
        for (int c = 0x80; c <= 0xFFFF; c++) {
            if (map(c, upper) != c) n++;
        }
        int[] out = new int[n * 2];
        int i = 0;
        for (int c = 0x80; c <= 0xFFFF; c++) {
            int m = map(c, upper);
            if (m != c) {
                out[i++] = c;
                out[i++] = m;
            }
        }
        return out;
    }

    private static int map(int c, boolean upper) {
        return upper ? Character.toUpperCase(c) : Character.toLowerCase(c);
    }

    /** Tabela como bytes LE: por entrada, [from lo][from hi][to lo][to hi]. */
    static byte[] tableBytes(boolean upper) {
        int[] m = mappings(upper);
        byte[] out = new byte[m.length * 2];
        for (int i = 0; i < m.length; i++) {
            out[i * 2] = (byte) (m[i] & 0xff);
            out[i * 2 + 1] = (byte) ((m[i] >>> 8) & 0xff);
        }
        return out;
    }

    /** Numero de ENTRADAS da tabela (pares from/to). */
    static int tableEntries(boolean upper) {
        return mappings(upper).length / 2;
    }

    private static void i32c(List<WasmInstr> b, int v) {
        b.add(new WasmInstr.Const(0, v));
    }

    private static void get(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, n));
    }

    private static void set(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, n));
    }

    private static void load8(List<WasmInstr> b, int off) {
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, off));
    }

    /**
     * {@code kof.strCaseAt(h, p) -> i32}: o code point MAPEADO no offset de
     * byte {@code p}. ASCII dobra inline; BMP via busca binaria na tabela;
     * astral passa intocado.
     *
     * <p>Locais: 0=h, 1=p (params); 2=b0, 3=b1, 4=b2, 5=cp, 6=lo, 7=hi,
     * 8=mid, 9=f, 10=res (i32).
     */
    static WasmFunc kofStrCaseAt(String fnName, int tabAddr, boolean upper) {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        set(b, 2, "b0");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ret", 0x40));
        // ---- ASCII: dobra inline (select: em faixa ? dobrado : cru) ----
        get(b, 2, "b0");
        i32c(b, 0x80);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ascii", 0x40));
        // select do WASM: [val1, val2, cond] — a CONDICAO fica no TOPO
        if (upper) {
            get(b, 2, "b0");
            i32c(b, 0x20);
            b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            get(b, 2, "b0");
            get(b, 2, "b0");
            i32c(b, 0x61);
            b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            i32c(b, 0x1a);
            b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        } else {
            get(b, 2, "b0");
            i32c(b, 0x20);
            b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            get(b, 2, "b0");
            get(b, 2, "b0");
            i32c(b, 0x41);
            b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
            i32c(b, 0x1a);
            b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        }
        b.add(new WasmInstr.Simple(0x1b, "select"));
        set(b, 10, "res");
        b.add(new WasmInstr.Branch("ret", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ascii", 0x40));
        // ---- 2 bytes → senao 3 bytes → senao 4 bytes (if/else aninhado:
        // um br_if de saida precisaria de condicao na pilha vazia do frame) ----
        get(b, 2, "b0");
        i32c(b, 0xe0);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 0xc0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "b2", 0x40));
        get(b, 2, "b0");
        i32c(b, 0x1f);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 6);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 0x3f);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 5, "cp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "b2", 0x40));
        get(b, 2, "b0");
        i32c(b, 0xf0);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 0xe0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "b3", 0x40));
        get(b, 2, "b0");
        i32c(b, 0x0f);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 12);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 6);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 2);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 0x3f);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 5, "cp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "b3", 0x40));
        // 4 bytes (astral): cp cru, passa intocado
        get(b, 2, "b0");
        i32c(b, 0x07);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 18);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 12);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 2);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 6);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        get(b, 0, "h");
        get(b, 1, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 3);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        i32c(b, 0x3f);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 5, "cp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "b3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "b2", 0x40));
        // ---- astral: fora da tabela ----
        get(b, 5, "cp");
        i32c(b, 0x10000);
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ast", 0x40));
        get(b, 5, "cp");
        set(b, 10, "res");
        b.add(new WasmInstr.Branch("ret", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ast", 0x40));
        // ---- busca binaria na tabela BMP (entrada = 4 bytes) ----
        i32c(b, 0);
        set(b, 6, "lo");
        i32c(b, tableEntries(upper) - 1);
        set(b, 7, "hi");
        get(b, 5, "cp");
        set(b, 10, "res");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "bs", 0x40));
        get(b, 6, "lo");
        get(b, 7, "hi");
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Branch("ret", 1, true));
        get(b, 6, "lo");
        get(b, 7, "hi");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        set(b, 8, "mid");
        // f = load16_le(tab + mid*4)
        i32c(b, tabAddr);
        get(b, 8, "mid");
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 0);
        i32c(b, tabAddr);
        get(b, 8, "mid");
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 1);
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 9, "f");
        // if f == cp: res = load16_le(tab + mid*4 + 2); br ret (2)
        get(b, 9, "f");
        get(b, 5, "cp");
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "hit", 0x40));
        i32c(b, tabAddr);
        get(b, 8, "mid");
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 2);
        i32c(b, tabAddr);
        get(b, 8, "mid");
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6c, "i32.mul"));
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 3);
        i32c(b, 8);
        b.add(new WasmInstr.Simple(0x74, "i32.shl"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        set(b, 10, "res");
        b.add(new WasmInstr.Branch("ret", 2));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "hit", 0x40));
        // if f < cp: lo = mid+1  senao  hi = mid-1
        get(b, 9, "f");
        get(b, 5, "cp");
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "lt", 0x40));
        get(b, 8, "mid");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 6, "lo");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "lt", 0x40));
        get(b, 8, "mid");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 7, "hi");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "lt", 0x40));
        b.add(new WasmInstr.Branch("bs", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "bs", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ret", 0x40));
        get(b, 10, "res");
        return new WasmFunc(fnName, List.of(0x7f, 0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }

    /**
     * Emite: [byte em h+p] → sl (comprimento UTF-8 da fonte) no local
     * {@code slL}, via ifs aninhados.
     */
    private static void emitSrcLen(List<WasmInstr> b, int hL, int pL, int bL, int slL) {
        get(b, hL, "h");
        get(b, pL, "p");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        load8(b, 4);
        set(b, bL, "b");
        // b < 0x80 → 1
        get(b, bL, "b");
        i32c(b, 0x80);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "s1", 0x40));
        i32c(b, 1);
        set(b, slL, "sl");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "s1", 0x40));
        // (b & 0xE0) == 0xC0 → 2
        get(b, bL, "b");
        i32c(b, 0xe0);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 0xc0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "s2", 0x40));
        i32c(b, 2);
        set(b, slL, "sl");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "s2", 0x40));
        // (b & 0xF0) == 0xE0 → 3, senao 4
        get(b, bL, "b");
        i32c(b, 0xf0);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, 0xe0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "s3", 0x40));
        i32c(b, 3);
        set(b, slL, "sl");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "s3", 0x40));
        i32c(b, 4);
        set(b, slL, "sl");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "s3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "s2", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "s1", 0x40));
    }

    /** Emite: total += comprimento UTF-8 de [cp] no local {@code totL}. */
    private static void emitAddEncLen(List<WasmInstr> b, int cpL, int totL) {
        // cp < 0x80 → +1
        get(b, cpL, "cp");
        i32c(b, 0x80);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "c1", 0x40));
        get(b, totL, "total");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, totL, "total");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "c1", 0x40));
        // cp < 0x800 → +2
        get(b, cpL, "cp");
        i32c(b, 0x800);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "c2", 0x40));
        get(b, totL, "total");
        i32c(b, 2);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, totL, "total");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "c2", 0x40));
        // cp < 0x10000 → +3, senao +4
        get(b, cpL, "cp");
        i32c(b, 0x10000);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "c3", 0x40));
        get(b, totL, "total");
        i32c(b, 3);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, totL, "total");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "c3", 0x40));
        get(b, totL, "total");
        i32c(b, 4);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, totL, "total");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "c3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "c2", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "c1", 0x40));
    }

    /** Emite: anexa os bytes UTF-8 de [cp] em [out] — ifs aninhados, sem br para frente. */
    private static void emitAppendEncoded(List<WasmInstr> b, int outL, int cpL) {
        // 1 byte
        get(b, cpL, "cp");
        i32c(b, 0x80);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a1", 0x40));
        get(b, outL, "out");
        get(b, cpL, "cp");
        b.add(new WasmInstr.Call("kof.strAppendByte"));
        set(b, outL, "out");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "a1", 0x40));
        // 2 bytes
        get(b, cpL, "cp");
        i32c(b, 0x800);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a2", 0x40));
        append1(b, outL, cpL, 6, 0x1f, 0xc0);
        append1(b, outL, cpL, 0, 0x3f, 0x80);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "a2", 0x40));
        // 3 bytes
        get(b, cpL, "cp");
        i32c(b, 0x10000);
        b.add(new WasmInstr.Simple(0x49, "i32.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "a3", 0x40));
        append1(b, outL, cpL, 12, 0x0f, 0xe0);
        append1(b, outL, cpL, 6, 0x3f, 0x80);
        append1(b, outL, cpL, 0, 0x3f, 0x80);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "a3", 0x40));
        // 4 bytes
        append1(b, outL, cpL, 18, 0x07, 0xf0);
        append1(b, outL, cpL, 12, 0x3f, 0x80);
        append1(b, outL, cpL, 6, 0x3f, 0x80);
        append1(b, outL, cpL, 0, 0x3f, 0x80);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a2", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "a1", 0x40));
    }

    /** out = strAppendByte(out, (cp >> sh & mask) | or) — mascara e OR separados. */
    private static void append1(List<WasmInstr> b, int outL, int cpL, int sh, int mask, int orK) {
        get(b, outL, "out");
        get(b, cpL, "cp");
        i32c(b, sh);
        b.add(new WasmInstr.Simple(0x76, "i32.shr_u"));
        i32c(b, mask);
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        i32c(b, orK);
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Call("kof.strAppendByte"));
        set(b, outL, "out");
    }

    /**
     * {@code kof.strUpper(h) -> handle} (e {@code kof.strLower}): duas
     * passadas — o total exato de bytes, depois o preenchimento.
     *
     * <p>Locais: 0=h; 1=n, 2=p, 3=sl, 4=cp, 5=out, 6=total, 7=b (i32).
     */
    static WasmFunc kofStrFold(String name, String caseAtName) {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "h");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD, 0));
        set(b, 1, "n");
        // ---- passada 1: total ----
        i32c(b, 0);
        set(b, 2, "p");
        i32c(b, 0);
        set(b, 6, "total");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "w1", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "l1", 0x40));
        get(b, 2, "p");
        get(b, 1, "n");
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("w1", 1, true));
        emitSrcLen(b, 0, 2, 7, 3);
        get(b, 0, "h");
        get(b, 2, "p");
        b.add(new WasmInstr.Call(caseAtName));
        set(b, 4, "cp");
        emitAddEncLen(b, 4, 6);
        get(b, 2, "p");
        get(b, 3, "sl");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 2, "p");
        b.add(new WasmInstr.Branch("l1", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "l1", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "w1", 0x40));
        // ---- aloca pelo total exato e preenche ----
        get(b, 6, "total");
        b.add(new WasmInstr.Call("kof.strAlloc"));
        set(b, 5, "out");
        i32c(b, 0);
        set(b, 2, "p");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "w2", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "l2", 0x40));
        get(b, 2, "p");
        get(b, 1, "n");
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_u"));
        b.add(new WasmInstr.Branch("w2", 1, true));
        emitSrcLen(b, 0, 2, 7, 3);
        get(b, 0, "h");
        get(b, 2, "p");
        b.add(new WasmInstr.Call(caseAtName));
        set(b, 4, "cp");
        emitAppendEncoded(b, 5, 4);
        get(b, 2, "p");
        get(b, 3, "sl");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 2, "p");
        b.add(new WasmInstr.Branch("l2", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "l2", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "w2", 0x40));
        get(b, 5, "out");
        return new WasmFunc(name, List.of(0x7f), List.of(0x7f),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7f), b);
    }
}

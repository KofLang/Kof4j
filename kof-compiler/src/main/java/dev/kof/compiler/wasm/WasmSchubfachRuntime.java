package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fase E (27/10, #776): o PORT SCHUBFACH — o nucleo numerico de
 * {@code Double.toString} byte-a-byte do JDK, transcrito do asm nativo
 * PROVADO ({@code RuntimeDtoaSchubfachCore}). A renderizacao (format) e o
 * driver vivem em {@link WasmSchubfachFormat}; aqui ficam as primitivas.
 * RE-EMISSAO 27/10 (rodada 2) com as licoes da rodada 1 embutidas: os
 * shifts tomam operandos I64 nesta toolchain, os opcodes sao conferidos
 * contra a tabela canonica (rotulos mentirosos enganam), os stores vao
 * [addr, value], os if/else que produzem valor levam blocktype tipado, o
 * -k e `const 0; get k; sub`, e o q subnormal e a CONSTANTE -1074.
 */
public final class WasmSchubfachRuntime {

    private WasmSchubfachRuntime() {
    }

    /** Os nomes IR que o runtime WASI implementa (o scan whitelist usa). */
    static final Set<String> NAMES = Set.of(
            "kof_schub_flog10pow2", "kof_schub_flog10threequarters", "kof_schub_flog2pow10",
            "kof_schub_g1", "kof_schub_g0", "kof_schub_pow10", "kof_schub_rop",
            "kof_schub_to_decimal", "kof_schub_format", "kof_schub_full");

    static boolean usesAny(List<IRMethod> methods) {
        for (IRMethod m : methods) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && NAMES.contains(kc.methodName())) return true;
                }
            }
        }
        return false;
    }

    /** Endereco da tabela g no segmento de dados (fixado no registro). */
    private static int gTabAddr = -1;
    /** Endereco da tabela pow10. */
    private static int p10TabAddr = -1;

    public static void bindTables(int gTab, int p10Tab) {
        gTabAddr = gTab;
        p10TabAddr = p10Tab;
    }

    /** A tabela g INTERCALADA (g1,g0 por k) — as MESMAS formas fechadas do
     *  JDK {@code MathUtils} (geradas por {@code RuntimeDtoaSchubfach}). */
    public static byte[] gTable() {
        long[] t = dev.kof.compiler.runtime.RuntimeDtoaSchubfach.gTable();
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(t.length * 8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (long v : t) {
            bb.putLong(v);
        }
        return bb.array();
    }

    /** pow10(0..17) — a MESMA tabela do asm/JDK (MathUtils.pow10). */
    private static final long[] POW10 = {
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L,
            100_000_000L, 1_000_000_000L, 10_000_000_000L, 100_000_000_000L,
            1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L,
            1_000_000_000_000_000L, 10_000_000_000_000_000L, 100_000_000_000_000_000L,
    };

    public static byte[] pow10Table() {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(18 * 8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (long v : POW10) {
            bb.putLong(v);
        }
        return bb.array();
    }

    // ---- helpers de emissao ----

    static void get(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, n));
    }

    static void set(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, n));
    }

    static void i32c(List<WasmInstr> b, int v) {
        b.add(new WasmInstr.Const(0, v));
    }

    static void i64c(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(1, v));
    }

    /** ext(SIGNED) + i64.const(k) + mul + [- sub] + sar(s) + wrap:
     *  floor(e*k/2^s) com e SINALIZADO (o e pode ser negativo). */
    private static void flogBody(List<WasmInstr> b, long k, int shift, long sub) {
        get(b, 0, "e");
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        i64c(b, k);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        if (sub != 0) {
            i64c(b, sub);
            b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        }
        i64c(b, shift);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
    }

    // ---- funcoes ----

    /** mulhi64: os 64 bits altos de a*b unsigned via 4 muls 32x32. */
    public static WasmFunc kofMulHi64() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=a, 1=b; 2=a_h, 3=a_l, 4=b_h, 5=b_l, 6=ll, 7=t, 8=u
        get(b, 0, "a");
        i64c(b, 32);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        set(b, 2, "ah");
        get(b, 0, "a");
        i64c(b, 0xFFFFFFFFL);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 3, "al");
        get(b, 1, "b");
        i64c(b, 32);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        set(b, 4, "bh");
        get(b, 1, "b");
        i64c(b, 0xFFFFFFFFL);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 5, "bl");
        get(b, 3, "al");
        get(b, 5, "bl");
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        set(b, 6, "ll");
        get(b, 3, "al");
        get(b, 4, "bh");
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        get(b, 6, "ll");
        i64c(b, 32);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));              // t = al*bh + (ll>>>32)
        set(b, 7, "t");
        get(b, 2, "ah");
        get(b, 5, "bl");
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));              // hl
        get(b, 7, "t");
        i64c(b, 0xFFFFFFFFL);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));              // u = hl + (t & MAX32)
        set(b, 8, "u");
        get(b, 2, "ah");
        get(b, 4, "bh");
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));              // hh
        get(b, 7, "t");
        i64c(b, 32);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        get(b, 8, "u");
        i64c(b, 32);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        return new WasmFunc("kof.mulhi64", List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64), b);
    }

    public static WasmFunc kofFlog10Pow2() {
        List<WasmInstr> b = new ArrayList<>();
        flogBody(b, 0x9A209A84FBL, 41, 0);
        return new WasmFunc("kof.flog10pow2", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(), b);
    }

    public static WasmFunc kofFlog10ThreeQuarters() {
        List<WasmInstr> b = new ArrayList<>();
        flogBody(b, 0x9A209A84FBL, 41, 0x3FF7F85779L);
        return new WasmFunc("kof.flog10threequarters", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(), b);
    }

    public static WasmFunc kofFlog2Pow10() {
        List<WasmInstr> b = new ArrayList<>();
        flogBody(b, 0xD49A784BCDL, 38, 0);
        return new WasmFunc("kof.flog2pow10", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(), b);
    }

    /** g1(k): [G_TAB + (k+324)*16]. */
    public static WasmFunc kofSchubG1() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "k");
        i32c(b, 324);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        i64c(b, (long) gTabAddr);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.schubG1", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64),
                List.of(), b);
    }

    /** g0(k): [G_TAB + (k+324)*16 + 8]. */
    public static WasmFunc kofSchubG0() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "k");
        i32c(b, 324);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        i64c(b, 16);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        i64c(b, (long) gTabAddr + 8);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.schubG0", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64),
                List.of(), b);
    }

    /** pow10(e): [P10_TAB + e*8], e em 0..17. */
    public static WasmFunc kofSchubPow10() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "e");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        i64c(b, 8);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        i64c(b, (long) p10TabAddr);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        return new WasmFunc("kof.schubPow10", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I64),
                List.of(), b);
    }

    /** rop(g1, g0, cp): o asm literal — shr 1, carry do bit 63 e o teste
     *  x&MAX+MAX (que testa x != 0 nos 63 bits baixos). */
    public static WasmFunc kofSchubRop() {
        List<WasmInstr> b = new ArrayList<>();
        // 0=g1, 1=g0, 2=cp; 3=h0, 4=r9, 5=h1
        get(b, 1, "g0");
        get(b, 2, "cp");
        b.add(new WasmInstr.Call("kof.mulhi64"));
        set(b, 3, "h0");
        get(b, 0, "g1");
        get(b, 2, "cp");
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        set(b, 4, "r9");
        get(b, 0, "g1");
        get(b, 2, "cp");
        b.add(new WasmInstr.Call("kof.mulhi64"));
        set(b, 5, "h1");
        get(b, 4, "r9");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        get(b, 3, "h0");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 4, "r9");
        get(b, 4, "r9");
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));            // carry do bit 63
        get(b, 5, "h1");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));              // result
        get(b, 4, "r9");
        i64c(b, Long.MAX_VALUE);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        i64c(b, Long.MAX_VALUE);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        return new WasmFunc("kof.schubRop", List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64),
                List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64), b);
    }

    /** Escreve o topo da pilha (i32 e) em [SCHUB_E] — [addr, value]: o addr
     *  sai PRIMEIRO; o valor (i32) estende para i64 no temporario 17. */
    static void storeE(List<WasmInstr> b) {
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        set(b, 17, "eTmp");
        i32c(b, WasmSchubfachFormat.SCHUB_E);
        get(b, 17, "eTmp");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
    }

    /** rop(g1,g0, x << h) com x do local {@code xi} — a sequencia comum. */
    private static void ropCall(List<WasmInstr> b, int kLocal, int xLocal, int hLocal) {
        get(b, kLocal, "k");
        b.add(new WasmInstr.Call("kof.schubG1"));
        get(b, kLocal, "k");
        b.add(new WasmInstr.Call("kof.schubG0"));
        get(b, xLocal, "x");
        get(b, hLocal, "h");
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Call("kof.schubRop"));
    }

    /** O corpo cmp+final do asm (o MESMO nos dois caminhos): tp = t+1;
     *  oob/inl pelos limites; divergiram -> f=oob?t:tp, e=k+dk, br done;
     *  senao o final (diff, empate por paridade). {@code doneDepth} = a
     *  profundidade do br para o block `done` vista DE DENTRO do pick-if
     *  (o final usa doneDepth-1: esta um nivel fora do pick). */
    private static void cmpAndFinal(List<WasmInstr> b, int doneDepth) {
        // tp = t + 1
        get(b, 11, "t");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 12, "tp");
        get(b, 9, "rm1");
        get(b, 3, "odd");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        get(b, 11, "t");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x58, "i64.le_u"));
        set(b, 14, "oob");
        get(b, 12, "tp");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        get(b, 3, "odd");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        get(b, 10, "rp1");
        b.add(new WasmInstr.Simple(0x58, "i64.le_u"));
        set(b, 15, "inl");
        get(b, 14, "oob");
        get(b, 15, "inl");
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pick", 0x40));
        get(b, 14, "oob");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ob", 0x7e));
        get(b, 11, "t");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ob", 0x7e));
        get(b, 12, "tp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ob", 0x7e));
        set(b, 16, "f");
        get(b, 6, "k");
        get(b, 2, "dk");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        storeE(b);
        b.add(new WasmInstr.Branch("done", doneDepth));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pick", 0x40));
        // final: diff = rOut - 2*(t + tp); negativo -> t; != 0 -> tp;
        // empate -> t se t PAR senao tp
        get(b, 11, "t");
        get(b, 12, "tp");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        set(b, 13, "two");
        get(b, 8, "rout");
        get(b, 13, "two");
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 13, "diff");
        get(b, 13, "diff");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "neg", 0x40));
        get(b, 11, "t");
        set(b, 16, "f");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "neg", 0x40));
        get(b, 13, "diff");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "tie", 0x40));
        get(b, 11, "t");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0x50, "i64.eqz"));
        // o if produz o valor (os DOIS bracos empilham i64) — o blocktype tem
        // de ser 0x7e AQUI (o END nao codifica tipo: o 0x40 no if deixava o
        // i64 sobrando no fim do bloco — o validador rejeitou)
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "evn", 0x7e));
        get(b, 11, "t");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "evn", 0x7e));
        get(b, 12, "tp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "evn", 0x7e));
        set(b, 16, "f");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "tie", 0x40));
        get(b, 12, "tp");
        set(b, 16, "f");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "tie", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "neg", 0x40));
        get(b, 6, "k");
        get(b, 2, "dk");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        storeE(b);
        // o final esta um nivel FORA do if pick: a profundidade do done e -1
        b.add(new WasmInstr.Branch("done", doneDepth - 1));
    }

    /**
     * to_decimal(q, c, dk) -> f; o expoente `e` vai para [SCHUB_E].
     * Transcricao literal do asm to_decimal. Locais UNIFICADOS: 0=q, 1=c,
     * 2=dk (params); 3=odd, 4=OUT, 5=OUT_M1, 6=k, 7=h, 8=rOut, 9=rM1,
     * 10=rP1, 11=t, 12=tp, 13=two/diff, 14=oob, 15=inl, 16=f, 17=eTmp.
     */
    public static WasmFunc kofSchubToDecimal() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "done", 0x40));
        // odd = c & 1
        get(b, 1, "c");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 3, "odd");
        // OUT = c << 2
        get(b, 1, "c");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        set(b, 4, "out");
        // o minimal normal: c == 2^52 && q != -1074
        get(b, 1, "c");
        i64c(b, 0x10000000000000L);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        get(b, 0, "q");
        i32c(b, -1074);
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "min", 0x40));
        get(b, 4, "out");
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 5, "outm1");
        get(b, 0, "q");
        b.add(new WasmInstr.Call("kof.flog10threequarters"));
        set(b, 6, "k");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "min", 0x40));
        get(b, 4, "out");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        set(b, 5, "outm1");
        get(b, 0, "q");
        b.add(new WasmInstr.Call("kof.flog10pow2"));
        set(b, 6, "k");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "min", 0x40));
        // h = flog2pow10(-k) + q + 2 — o -k e `const 0; get k; sub`
        i32c(b, 0);
        get(b, 6, "k");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Call("kof.flog2pow10"));
        get(b, 0, "q");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 2);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Simple(0xac, "i64.extend_i32_s"));
        set(b, 7, "h");
        // rOut / rM1 / rP1
        ropCall(b, 6, 4, 7);
        set(b, 8, "rout");
        ropCall(b, 6, 5, 7);
        set(b, 9, "rm1");
        get(b, 4, "out");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 13, "two");                                       // OUT_P1 via scratch
        ropCall(b, 6, 13, 7);
        set(b, 10, "rp1");
        // t = rOut >> 2
        get(b, 8, "rout");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        set(b, 11, "t");
        // o caminho t >= 100
        get(b, 11, "t");
        i64c(b, 100);
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "big", 0x40));
        get(b, 11, "t");
        i64c(b, 0x19999999999999A0L);
        b.add(new WasmInstr.Call("kof.mulhi64"));
        i64c(b, 10);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        set(b, 12, "tp");
        get(b, 12, "tp");
        i64c(b, 10);
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        set(b, 13, "two");                                       // tp10
        get(b, 9, "rm1");
        get(b, 3, "odd");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        get(b, 12, "tp");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        b.add(new WasmInstr.Simple(0x58, "i64.le_u"));
        set(b, 14, "oob");
        get(b, 13, "two");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        get(b, 3, "odd");
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        get(b, 10, "rp1");
        b.add(new WasmInstr.Simple(0x58, "i64.le_u"));
        set(b, 15, "inl");
        get(b, 14, "oob");
        get(b, 15, "inl");
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pick", 0x40));
        get(b, 14, "oob");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ob", 0x7e));
        get(b, 12, "tp");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ob", 0x7e));
        get(b, 13, "two");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ob", 0x7e));
        set(b, 16, "f");
        get(b, 6, "k");
        storeE(b);
        b.add(new WasmInstr.Branch("done", 2));                  // pick,big,done
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pick", 0x40));
        cmpAndFinal(b, 2);                                       // o fall-through do asm
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "big", 0x40));
        // o caminho t < 100 (o MESMO cmp+final)
        cmpAndFinal(b, 1);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "done", 0x40));
        get(b, 16, "f");
        return new WasmFunc("kof.schubToDecimal",
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64),
                // o mapa REAL do corpo (prova byte-a-byte no validador):
                // 3=odd(i64 — os usos sao i64.add), 4=out, 5=outDn, 6=k(i32),
                // 7=h, 8..12=r9/rm/rp/t2/tp10, 13=tmp, 14=oob(i32),
                // 15=inl(i32), 16=f(i64), 17=e — set 3 grava i64.and, set 14/15
                // gravam i64.le_u, set 16 grava o if(result i64) do pick
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64), b);
    }

    /** Toda a familia (o chamador registra quando um double pode imprimir). */
    public static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(kofMulHi64());
        fs.add(kofFlog10Pow2());
        fs.add(kofFlog10ThreeQuarters());
        fs.add(kofFlog2Pow10());
        fs.add(kofSchubG1());
        fs.add(kofSchubG0());
        fs.add(kofSchubPow10());
        fs.add(kofSchubRop());
        fs.add(kofSchubToDecimal());
        fs.add(WasmSchubfachFormat.kofSchubFormat());
        fs.add(WasmSchubfachFormat.kofSchubFull());
        return fs;
    }
}

package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

/**
 * MATH-LIBM-4 fatia B (28/10, #776 — split check_500): o caminho GRANDE do
 * RemPio2 (|x| &gt; 2^19·π/2) — o DRIVER (a quebra tx[3] = scalbn(|x|,
 * ilogb−23), o Inf/NaN {@code x−x}, o despacho n&amp;3) + o layout do scratch
 * de memória compartilhado com o kernel ({@link WasmRemPio2Kernel}) e a
 * cauda ({@link WasmRemPio2Tail}). Mapeado do FdLibm.RemPio2/
 * KernelRemPio2 do PRÓPRIO JDK (as linhas 591-1028 do src.zip).
 *
 * <p>A FATIA PARCIAL (a opção (a) da rodada 51): o ramo n&amp;3=0/2
 * despacha ao {@code kof.sin.smalltail} (o __kernel_sin iy=1 já pousado);
 * n&amp;3=1/3 = o {@code unreachable} honesto — o território do veredicto
 * MORTO do cos (Math.cos == StrictMath.cos deriva 5/29 por 1 ulp, rodada 28).
 *
 * <p>AS LEIS DA EMISSÃO: os valores do distill q/f são INTEIROS EXATOS
 * (produtos de inteiros de 24 bits ≤ 2^48, somas ≤ 3·2^48 &lt; 2^53) — o
 * scalb vira a multiplicação EXATA por 2^n construído por bits (campo
 * 1023+n ∈ [996, 1026], normal); o {@code (int)} é i32.trunc_f64_s (sem
 * saturação: as entradas são todas na faixa); o {@code /} do Java trunca
 * == i32.div_s; o {@code >>} máscara-31 == o shift WASM mod-32.
 */
final class WasmRemPio2Large {

    private WasmRemPio2Large() {
    }

    /** O layout do scratch (base = o heapAlloc do driver). iq é i32; o
     *  resto f64. Os acessos usam o offset CONSTANTE do memarg sobre
     *  base + índice·escala. */
    static final int X0 = 0, X1 = 8, X2 = 16, Y0 = 24, Y1 = 32;
    static final int IQ = 40, F = 120, FQ = 280, Q = 440;
    static final int IPIO2 = 600, PIO2T = 864;
    static final int SCRATCH = 928;

    /** Os locais do KERNEL (3..15) — compartilhados com as duas fatias de
     *  emissão (WasmRemPio2Kernel + WasmRemPio2Tail). */
    static final int KJZ = 3, KJX = 4, KJV = 5, KQ0 = 6, KI = 7, KJ = 8,
            KM = 9, KK = 10, KCARRY = 11, KIH = 12, KN = 13, KZ = 14, KFW = 15;

    /** O local do BASE no kernel/cauda = o PRIMEIRO parâmetro (o driver usa
     *  o 1 — o bug medido da primeira sonda: no kernel o local 1 é o e0). */
    static final int KBASE = 0, KE0 = 1;

    static final double TWO24 = 16777216.0;
    static final double TWON24 = 0x1.0p-24;

    /** A tabela two_over_pi do fdlibm (66 palavras de 24 bits de 2/π). */
    static final int[] TWO_OVER_PI = {
            0xA2F983, 0x6E4E44, 0x1529FC, 0x2757D1, 0xF534DD, 0xC0DB62,
            0x95993C, 0x439041, 0xFE5163, 0xABDEBB, 0xC561B7, 0x246E3A,
            0x424DD2, 0xE00649, 0x2EEA09, 0xD1921C, 0xFE1DEB, 0x1CB129,
            0xA73EE8, 0x8235F5, 0x2EBB44, 0x84E99C, 0x7026B4, 0x5F7E41,
            0x3991D6, 0x398353, 0x39F49C, 0x845F8B, 0xBDF928, 0x3B1FF8,
            0x97FFDE, 0x05980F, 0xEF2F11, 0x8B5A0A, 0x6D1F6D, 0x367ECF,
            0x27CB09, 0xB74F46, 0x3F669E, 0x5FEA2D, 0x7527BA, 0xC7EBE5,
            0xF17B3D, 0x0739F7, 0x8A5292, 0xEA6BFB, 0x5FB11F, 0x8D5D08,
            0x560330, 0x46FC7B, 0x6BABF0, 0xCFBC20, 0x9AF436, 0x1DA9E3,
            0x91615E, 0xE61B08, 0x659985, 0x5F14A0, 0x68408D, 0xFFD880,
            0x4D7327, 0x310606, 0x1556CA, 0x73A8C9, 0x60E27B, 0xC08C6B,
    };

    /** As PIo2[8] do fdlibm (π/2 cortado em blocos de 24 bits). */
    static final double[] PIO2 = {
            0x1.921fb4p0, 0x1.4442dp-24, 0x1.846988p-48, 0x1.8cc516p-72,
            0x1.01b838p-96, 0x1.a25204p-120, 0x1.382228p-145, 0x1.9f31dp-169,
    };

    // helpers de emissão compartilhados com o kernel e a cauda
    static void get(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, "l" + i));
    }

    static void set(List<WasmInstr> b, int i) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, "l" + i));
    }

    static void i32c(List<WasmInstr> b, int v) {
        b.add(new WasmInstr.Const(0, v));
    }

    static void i64c(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(1, v));
    }

    static void f64c(List<WasmInstr> b, double v) {
        b.add(new WasmInstr.Const(v));
    }

    static void op(List<WasmInstr> b, int code, String n) {
        b.add(new WasmInstr.Simple(code, n));
    }

    static void blk(List<WasmInstr> b, int kind, String label, int type) {
        b.add(new WasmInstr.Blocking(kind, label, type));
    }

    /** 2^n por bits: (long) (1023+n) &lt;&lt; 52 reinterpretado. Só para n
     *  NORMAL (o kernel usa |n| ≤ 27). */
    static void pow2(List<WasmInstr> b, int nLocal, int delta) {
        i32c(b, 1023 + delta);
        get(b, nLocal);
        op(b, 0x6b, "i32.sub");
        op(b, 0xad, "i64.extend_i32_u");
        i64c(b, 52);
        op(b, 0x86, "i64.shl");
        op(b, 0xbf, "f64.reinterpret_i64");
    }

    /** 2^n por bits na forma ADITIVA: (1023 + n) — o expoente POSITIVO. */
    static void pow2a(List<WasmInstr> b, int nLocal) {
        i32c(b, 1023);
        get(b, nLocal);
        op(b, 0x6a, "i32.add");
        op(b, 0xad, "i64.extend_i32_u");
        i64c(b, 52);
        op(b, 0x86, "i64.shl");
        op(b, 0xbf, "f64.reinterpret_i64");
    }

    /** `kof.sin.large(x f64) -> f64` — o driver do caminho grande.
     *  Locais: 0=x, 1=base, 2=ix, 3=hx, 4=e0, 5=nx, 6=z, 7=t, 8=n,
     *  9=y0, 10=y1, 11=oct. */
    static WasmFunc largeFunc() {
        List<WasmInstr> b = new ArrayList<>();
        // base = heapAlloc(SCRATCH)
        i32c(b, SCRATCH);
        b.add(new WasmInstr.Call("kof.heapAlloc"));
        set(b, 1);
        // as duas tabelas são lidas por índice DINÂMICO no kernel: pré-copia
        for (int k = 0; k < TWO_OVER_PI.length; k++) {
            get(b, 1);
            i32c(b, TWO_OVER_PI[k]);
            b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, IPIO2 + 4 * k));
        }
        for (int k = 0; k < PIO2.length; k++) {
            get(b, 1);
            f64c(b, PIO2[k]);
            b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, PIO2T + 8 * k));
        }
        // hx = o hi-word COM sinal (a negação do fim); ix = hx & 0x7fffffff
        get(b, 0);
        op(b, 0xbd, "i64.reinterpret_f64");
        i64c(b, 32);
        op(b, 0x87, "i64.shr_s");
        op(b, 0xa7, "i32.wrap_i64");
        set(b, 3);
        get(b, 3);
        i32c(b, 0x7fffffff);
        op(b, 0x71, "i32.and");
        set(b, 2);
        // Inf/NaN (o fdlibm: ix >= EXP_BITS → x−x, antes da redução)
        get(b, 2);
        i32c(b, 0x7ff00000);
        op(b, 0x4e, "i32.ge_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, 0);
        get(b, 0);
        op(b, 0xa1, "f64.sub");
        b.add(WasmInstr.Branch.ret());
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        // e0 = (ix >> 20) - 1046
        get(b, 2);
        i32c(b, 20);
        op(b, 0x76, "i32.shr_u");
        i32c(b, 1046);
        op(b, 0x6b, "i32.sub");
        set(b, 4);
        // z = |x|·2^(−e0) — um shift de expoente EXATO (a mantissa intacta)
        get(b, 0);
        op(b, 0x99, "f64.abs");
        pow2(b, 4, 0);
        op(b, 0xa2, "f64.mul");
        set(b, 6);
        // tx[0], tx[1] (e o resto em tx[2]): tx = (int)z; z = (z−tx)·2^24
        tx(b, X0);
        tx(b, X1);
        get(b, 1);
        get(b, 6);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, X2));
        // nx = 3; while (tx[nx−1] == 0) nx−−
        i32c(b, 3);
        set(b, 5);
        blk(b, WasmInstr.Blocking.BLOCK, "nx", 0x40);
        blk(b, WasmInstr.Blocking.LOOP, "nxl", 0x40);
        get(b, 1);
        get(b, 5);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        i32c(b, 3);
        op(b, 0x74, "i32.shl");
        op(b, 0x6a, "i32.add");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, X0));
        f64c(b, 0.0);
        op(b, 0x61, "f64.eq");
        op(b, 0x45, "i32.eqz");
        b.add(new WasmInstr.Branch("nx", 1, true));
        get(b, 5);
        i32c(b, 1);
        op(b, 0x6b, "i32.sub");
        set(b, 5);
        b.add(new WasmInstr.Branch("nxl", 0));
        blk(b, WasmInstr.Blocking.END, "nxl", 0x40);
        blk(b, WasmInstr.Blocking.END, "nx", 0x40);
        // n = kernel(base, e0, nx) e os y do scratch
        get(b, 1);
        get(b, 4);
        get(b, 5);
        b.add(new WasmInstr.Call("kof.sin.rempi2"));
        set(b, 8);
        get(b, 1);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, Y0));
        set(b, 9);
        get(b, 1);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD_F64, Y1));
        set(b, 10);
        // o sinal (o driver do fdlibm nega y E n quando hx<0)
        get(b, 3);
        i32c(b, 0);
        op(b, 0x48, "i32.lt_s");
        blk(b, WasmInstr.Blocking.IF, null, 0x40);
        get(b, 9);
        op(b, 0x9a, "f64.neg");
        set(b, 9);
        get(b, 10);
        op(b, 0x9a, "f64.neg");
        set(b, 10);
        i32c(b, 0);
        get(b, 8);
        op(b, 0x6b, "i32.sub");
        set(b, 8);
        blk(b, WasmInstr.Blocking.END, null, 0x40);
        // oct = n&3: 0 → +smalltail; 2 → −smalltail; 1/3 → o trap honesto
        get(b, 8);
        i32c(b, 3);
        op(b, 0x71, "i32.and");
        set(b, 11);
        get(b, 11);
        i32c(b, 0);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x7c);
        get(b, 9);
        get(b, 10);
        b.add(new WasmInstr.Call("kof.sin.smalltail"));
        blk(b, WasmInstr.Blocking.ELSE, null, 0x7c);
        get(b, 11);
        i32c(b, 2);
        op(b, 0x46, "i32.eq");
        blk(b, WasmInstr.Blocking.IF, null, 0x7c);
        get(b, 9);
        get(b, 10);
        b.add(new WasmInstr.Call("kof.sin.smalltail"));
        op(b, 0x9a, "f64.neg");
        blk(b, WasmInstr.Blocking.ELSE, null, 0x7c);
        op(b, 0x00, "unreachable");
        blk(b, WasmInstr.Blocking.END, null, 0x7c);
        blk(b, WasmInstr.Blocking.END, null, 0x7c);
        return new WasmFunc("kof.sin.large", List.of(0x7c), List.of(0x7c),
                List.of(0x7f, 0x7f, 0x7f, 0x7f, 0x7f, 0x7c, 0x7c, 0x7f, 0x7c, 0x7c, 0x7f), b);
    }

    /** Um passo da quebra: tx = (int)z; z = (z−tx)·2^24; mem[base+off] = tx. */
    private static void tx(List<WasmInstr> b, int off) {
        get(b, 6);
        op(b, 0xaa, "i32.trunc_f64_s");
        op(b, 0xb7, "f64.convert_i32_s");
        set(b, 7);
        get(b, 6);
        get(b, 7);
        op(b, 0xa1, "f64.sub");
        f64c(b, TWO24);
        op(b, 0xa2, "f64.mul");
        set(b, 6);
        get(b, 1);
        get(b, 7);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE_F64, off));
    }
}
package dev.kof.compiler.wasm;

import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.wasm.WasmSchubfachRuntime.get;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i32c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.i64c;
import static dev.kof.compiler.wasm.WasmSchubfachRuntime.set;

/**
 * Fase E (27/10, #776): a RENDERIZACAO Schubfach (format, H=17) e o DRIVER
 * de decomposicao — transcritos do asm nativo PROVADO
 * ({@code RuntimeDtoaSchubfachCore} + {@code RuntimeDtoaSchubfachDouble}).
 * RE-EMISSAO 27/10 (rodada 2): os stores [addr, value], os if/else
 * result-typed onde os bracos produzem valor, os shifts i64, a constante
 * -1074 subnormal e o handle ESTATICO do format ([len][bytes] no scratch —
 * o writeStr le handle estatico, o MESMO padrao do
 * kof_string_from_literal do asm nativo).
 */
final class WasmSchubfachFormat {

    private WasmSchubfachFormat() {
    }

    /** Slot i64 do expoente devolvido por to_decimal (abaixo de DATA_BASE). */
    public static final int SCHUB_E = 64;
    /** 64 bytes: os 17 digitos de tras para frente (a cauda no +48/+56). */
    public static final int SCHUB_BUF = 96;
    /** 40 bytes: o handle estatico de saida [len][bytes]. */
    public static final int SCHUB_OUT = 160;

    /** Enderecos dos literais no segmento de dados (fixados no registro). */
    private static int litZero = -1, litNZero = -1, litNaN = -1, litInf = -1, litNInf = -1;

    public static void bindLiterals(int z, int nz, int nan, int inf, int ninf) {
        litZero = z;
        litNZero = nz;
        litNaN = nan;
        litInf = inf;
        litNInf = ninf;
    }

    /** Copia `cnt` (local) digitos de p (avancando p) para out+n (avancando). */
    private static void copyLoop(List<WasmInstr> b, String lbl, int cntLocal,
                                 int pLocal, int outLocal, int nLocal) {
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, cntLocal, "cnt");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        get(b, outLocal, "out");                                 // addr primeiro
        get(b, nLocal, "n");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, pLocal, "p");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        get(b, pLocal, "p");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, pLocal, "p");
        get(b, nLocal, "n");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, nLocal, "n");
        get(b, cntLocal, "cnt");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, cntLocal, "cnt");
        b.add(new WasmInstr.Branch(lbl, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "c", 0x40));
    }

    private static void putByte(List<WasmInstr> b, int outLocal, int nLocal, int ch) {
        get(b, outLocal, "out");
        get(b, nLocal, "n");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, ch);
        b.add(new WasmInstr.Store8(0));
        get(b, nLocal, "n");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, nLocal, "n");
    }

    /** 'E' + (e-1) no out (o asm: ev==0 -> "0"; negativo -> '-' + digitos). */
    private static void appendExp(List<WasmInstr> b, int outLocal, int nLocal) {
        putByte(b, outLocal, nLocal, 'E');
        get(b, 1, "e");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 12, "ev");
        get(b, 12, "ev");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ez", 0x40));
        putByte(b, outLocal, nLocal, '0');
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ez", 0x40));
        get(b, 12, "ev");
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "en", 0x40));
        putByte(b, outLocal, nLocal, '-');
        i32c(b, 0);
        get(b, 12, "ev");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 12, "ev");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "en", 0x40));
        // digitos reversos no SCHUB_BUF+56 e copia
        i32c(b, SCHUB_BUF + 56);
        set(b, 10, "edig");
        i32c(b, 0);
        set(b, 11, "m");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "ee", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "el", 0x40));
        get(b, 12, "ev");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch("ee", 1, true));
        get(b, 10, "edig");                                      // addr primeiro
        get(b, 11, "m");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 12, "ev");
        i32c(b, 10);
        b.add(new WasmInstr.Simple(0x6f, "i32.rem_u"));
        i32c(b, '0');
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Store8(0));
        get(b, 11, "m");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 11, "m");
        get(b, 12, "ev");
        i32c(b, 10);
        b.add(new WasmInstr.Simple(0x6e, "i32.div_u"));
        set(b, 12, "ev");
        b.add(new WasmInstr.Branch("el", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "el", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ee", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "er", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "erl", 0x40));
        get(b, 11, "m");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch("er", 1, true));
        get(b, 11, "m");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 11, "m");
        get(b, outLocal, "out");                                 // addr primeiro
        get(b, nLocal, "n");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        get(b, 10, "edig");
        get(b, 11, "m");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Store8(0));
        get(b, nLocal, "n");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, nLocal, "n");
        b.add(new WasmInstr.Branch("erl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "erl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "er", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ez", 0x40));
    }

    /** Escreve cnt (local) bytes '0' no out (o asm .Lschubf_z0 — NAO copia do buffer). */
    private static void zeroLoop(List<WasmInstr> b, String lbl, int cntLocal,
                                 int outLocal, int nLocal) {
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, lbl + "c", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, lbl + "l", 0x40));
        get(b, cntLocal, "cnt");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch(lbl, 1, true));
        putByte(b, outLocal, nLocal, '0');
        get(b, cntLocal, "cnt");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, cntLocal, "cnt");
        b.add(new WasmInstr.Branch(lbl, 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "l", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, lbl + "c", 0x40));
    }

    /**
     * format(f > 0, e, sign) -> o handle ESTATICO [SCHUB_OUT]. Locais
     * UNIFICADOS: 0=f(i64), 1=e, 2=sign (params); 3=len, 4=e10, 5=p,
     * 6=ndig, 7=q(i64), 8=out, 9=n, 10=edig, 11=m, 12=ev, 13=mode.
     */
    public static WasmFunc kofSchubFormat() {
        List<WasmInstr> b = new ArrayList<>();
        // len = 64 - clz(f) — [64; clz; sub]: a ORDEM e (a - b) com o a no
        // fundo; [clz; 64; sub] computava clz-64 (o len NEGATIVO matava o
        // e10 = -3 e a composicao inteira — o bug da rodada 1, achado pelo
        // interpretador op-a-op)
        i64c(b, 64);
        get(b, 0, "f");
        b.add(new WasmInstr.Simple(0x79, "i64.clz"));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        set(b, 3, "len");
        // e10 = flog10pow2(len); if f >= pow10(e10): e10++
        get(b, 3, "len");
        b.add(new WasmInstr.Call("kof.flog10pow2"));
        set(b, 4, "e10");
        get(b, 0, "f");
        get(b, 4, "e10");
        b.add(new WasmInstr.Call("kof.schubPow10"));
        b.add(new WasmInstr.Simple(0x5a, "i64.ge_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "inc", 0x40));
        get(b, 4, "e10");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 4, "e10");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "inc", 0x40));
        // f *= pow10(17 - e10); e += e10
        get(b, 0, "f");
        i32c(b, 17);
        get(b, 4, "e10");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Call("kof.schubPow10"));
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        set(b, 0, "f");
        get(b, 1, "e");
        get(b, 4, "e10");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 1, "e");
        // os 17 digitos de tras para frente: p termina em SCHUB_BUF+48
        i32c(b, SCHUB_BUF + 48);
        set(b, 5, "p");
        i32c(b, 17);
        set(b, 6, "ndig");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "dl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "dloop", 0x40));
        get(b, 6, "ndig");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch("dl", 1, true));
        get(b, 5, "p");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 5, "p");
        get(b, 5, "p");                                          // addr primeiro
        get(b, 0, "f");
        i64c(b, 10);
        b.add(new WasmInstr.Simple(0x80, "i64.div_u"));
        set(b, 7, "q");
        get(b, 0, "f");
        get(b, 7, "q");
        i64c(b, 10);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));            // rem
        i64c(b, '0');
        b.add(new WasmInstr.Simple(0x7c, "i64.add"));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));       // o store8 quer i32
        b.add(new WasmInstr.Store8(0));
        get(b, 7, "q");
        set(b, 0, "f");
        get(b, 6, "ndig");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 6, "ndig");
        b.add(new WasmInstr.Branch("dloop", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dloop", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dl", 0x40));
        // out/n + sinal — o OUT e um HANDLE estatico: [len][bytes]
        i32c(b, SCHUB_OUT + 4);
        set(b, 8, "out");
        i32c(b, 0);
        set(b, 9, "n");
        get(b, 2, "sign");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sg", 0x40));
        putByte(b, 8, 9, '-');
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sg", 0x40));
        // faixas do asm: e > 0 ? (e <= 7 ? plain : sci) : (e > -3 ? zeros : sci)
        // — o mode-local restaura os DOIS niveis do asm (o achatamento
        // "plain? : zeros?" mandava e > 7 para os zeros, cnt negativo)
        get(b, 1, "e");
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pos", 0x40));
        get(b, 1, "e");
        i32c(b, 7);
        b.add(new WasmInstr.Simple(0x4c, "i32.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "p7", 0x40));
        i32c(b, 0);
        set(b, 13, "mode");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "p7", 0x40));
        i32c(b, 2);
        set(b, 13, "mode");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "p7", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "pos", 0x40));
        get(b, 1, "e");
        i32c(b, -3);
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "z3", 0x40));
        i32c(b, 1);
        set(b, 13, "mode");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "z3", 0x40));
        i32c(b, 2);
        set(b, 13, "mode");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "z3", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pos", 0x40));
        // mode 0 (plain): e digitos, '.', 17-e digitos
        get(b, 13, "mode");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "plain", 0x40));
        get(b, 1, "e");
        set(b, 6, "ndig");
        copyLoop(b, "p1", 6, 5, 8, 9);
        putByte(b, 8, 9, '.');
        i32c(b, 17);
        get(b, 1, "e");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 6, "ndig");
        copyLoop(b, "p2", 6, 5, 8, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "plain", 0x40));
        // mode 1 (zeros): "0." + (-e) zeros LITERAIS + 17 digitos
        get(b, 13, "mode");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "zeros", 0x40));
        putByte(b, 8, 9, '0');
        putByte(b, 8, 9, '.');
        i32c(b, 0);
        get(b, 1, "e");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 6, "ndig");
        zeroLoop(b, "z0", 6, 8, 9);
        i32c(b, 17);
        set(b, 6, "ndig");
        copyLoop(b, "z1", 6, 5, 8, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "zeros", 0x40));
        // mode 2 (cientifica): 1 digito, '.', 16 digitos
        i32c(b, 1);
        set(b, 6, "ndig");
        copyLoop(b, "s0", 6, 5, 8, 9);
        putByte(b, 8, 9, '.');
        i32c(b, 16);
        set(b, 6, "ndig");
        copyLoop(b, "s1", 6, 5, 8, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "zeros", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "plain", 0x40));
        // strip: enquanto n > 0 e out[n-1]=='0' e (n <= 1 ou out[n-2] != '.')
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "sl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "sloop", 0x40));
        get(b, 9, "n");
        b.add(new WasmInstr.Simple(0x45, "i32.eqz"));
        b.add(new WasmInstr.Branch("sl", 1, true));
        get(b, 8, "out");
        get(b, 9, "n");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        i32c(b, '0');
        b.add(new WasmInstr.Simple(0x47, "i32.ne"));
        b.add(new WasmInstr.Branch("sl", 1, true));
        get(b, 9, "n");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x4c, "i32.le_s"));
        b.add(new WasmInstr.Branch("sl", 1, true));
        get(b, 8, "out");
        get(b, 9, "n");
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        i32c(b, 2);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        i32c(b, '.');
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Branch("sl", 1, true));
        get(b, 9, "n");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 9, "n");
        b.add(new WasmInstr.Branch("sloop", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sloop", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sl", 0x40));
        // 'E' + (e-1) so nas faixas e > 7 ou e <= -3 (a cientifica)
        get(b, 1, "e");
        i32c(b, 7);
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        get(b, 1, "e");
        i32c(b, -3);
        b.add(new WasmInstr.Simple(0x4c, "i32.le_s"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "exp", 0x40));
        appendExp(b, 8, 9);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "exp", 0x40));
        // o handle ESTATICO: [n] no header, os bytes ja estao no +4
        i32c(b, SCHUB_OUT);
        get(b, 9, "n");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));        // [SCHUB_OUT] = n
        i32c(b, SCHUB_OUT);
        return new WasmFunc("kof.schubFormat",
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32),
                // o mapa REAL do corpo: 3=len, 4=e10, 5=p, 6=ndig (i32),
                // 7=q (I64 — o set 7 grava i64.div_u; a lista antiga tinha o
                // I64 no slot 8 e o validador rejeitava o set 7), 8=out,
                // 9=n, 10=edig, 11=m, 12=ev, 13=mode (todos i32)
                List.of(WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I64, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32, WasmFunc.TYPE_I32,
                        WasmFunc.TYPE_I32, WasmFunc.TYPE_I32), b);
    }

    /** handle = strLit(addr, len) para o literal de dados — a ORDEM do
     *  kof.strLit do backend (local 0 = o endereco-fonte, local 1 = len). */
    private static void litHandle(List<WasmInstr> b, int len, int addr) {
        i32c(b, addr);
        i32c(b, len);
        b.add(new WasmInstr.Call("kof.strLit"));
    }

    /** e = 0 no slot [SCHUB_E] (o caminho do atalho) — [addr, value]. */
    private static void storeE0(List<WasmInstr> b) {
        i32c(b, SCHUB_E);
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));
    }

    /**
     * schubFull(v) -> handle de String: o driver do asm
     * kof_double_to_string. Locais UNIFICADOS: 0=v(f64); 1=bits(i64),
     * 2=sign(i64), 3=exp(i64), 4=mant(i64), 5=q(i32), 6=c(i64), 7=tmp(i64),
     * 8=handle(i32).
     */
    public static WasmFunc kofSchubFull() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "done", 0x40));
        get(b, 0, "v");
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 1, "bits");
        get(b, 1, "bits");
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        set(b, 2, "sign");
        get(b, 1, "bits");
        i64c(b, 52);
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        i64c(b, 0x7ff);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 3, "exp");
        // ---- NaN / +-Inf ----
        get(b, 3, "exp");
        i64c(b, 0x7ff);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ni", 0x40));
        get(b, 1, "bits");
        i64c(b, 12);
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        set(b, 7, "t");
        get(b, 7, "t");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "isn", 0x40));
        get(b, 2, "sign");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x52, "i64.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sgn", 0x7f));
        litHandle(b, 9, litNInf);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "sgn", 0x7f));
        litHandle(b, 8, litInf);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sgn", 0x7f));
        set(b, 8, "handle");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "isn", 0x40));
        litHandle(b, 3, litNaN);
        set(b, 8, "handle");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "isn", 0x40));
        b.add(new WasmInstr.Branch("done", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ni", 0x40));
        // ---- mantissa / zero / subnormal / normal ----
        get(b, 1, "bits");
        i64c(b, 0xFFFFFFFFFFFFFL);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        set(b, 4, "mant");
        // zero (com sinal)
        get(b, 3, "exp");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        get(b, 4, "mant");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "zr", 0x40));
        get(b, 2, "sign");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x52, "i64.ne"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sgz", 0x7f));
        litHandle(b, 4, litNZero);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "sgz", 0x7f));
        litHandle(b, 3, litZero);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sgz", 0x7f));
        set(b, 8, "handle");
        b.add(new WasmInstr.Branch("done", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "zr", 0x40));
        // q = 1075 - exp (i32); c = mant | 2^52
        i32c(b, 1075);
        get(b, 3, "exp");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 5, "q");
        get(b, 4, "mant");
        i64c(b, 0x10000000000000L);
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        set(b, 6, "c");
        // SUBNORMAL: exp == 0 && mant != 0 (o implicit bit NAO existe!)
        get(b, 3, "exp");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sub", 0x40));
        get(b, 4, "mant");
        i64c(b, 3);
        b.add(new WasmInstr.Simple(0x54, "i64.lt_u"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sm", 0x40));
        i32c(b, -1074);                                          // o asm: CONSTANTE
        get(b, 4, "mant");
        i64c(b, 10);
        b.add(new WasmInstr.Simple(0x7e, "i64.mul"));
        i32c(b, -1);
        b.add(new WasmInstr.Call("kof.schubToDecimal"));
        set(b, 7, "t");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "sm", 0x40));
        i32c(b, -1074);                                          // o asm: CONSTANTE
        get(b, 4, "mant");
        i32c(b, 0);
        b.add(new WasmInstr.Call("kof.schubToDecimal"));
        set(b, 7, "t");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sm", 0x40));
        get(b, 7, "t");                                          // f: o param do format
        i32c(b, WasmSchubfachFormat.SCHUB_E);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 2, "sign");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.schubFormat"));
        set(b, 8, "handle");
        b.add(new WasmInstr.Branch("done", 1));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sub", 0x40));
        // o atalho do literal removivel: 0 < q < 53 && (c>>q)<<q == c
        get(b, 5, "q");
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        get(b, 5, "q");
        i32c(b, 53);
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        get(b, 6, "c");
        get(b, 5, "q");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        get(b, 5, "q");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Simple(0x86, "i64.shl"));
        get(b, 6, "c");
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "short", 0x40));
        get(b, 6, "c");
        get(b, 5, "q");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
        set(b, 7, "t");
        storeE0(b);
        get(b, 7, "t");
        i32c(b, 0);                                              // e = 0 (o atalho)
        get(b, 2, "sign");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.schubFormat"));
        set(b, 8, "handle");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "short", 0x40));
        // caminho geral: to_decimal(-q, c, 0) -> format
        i32c(b, 0);
        get(b, 5, "q");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        get(b, 6, "c");
        i32c(b, 0);
        b.add(new WasmInstr.Call("kof.schubToDecimal"));
        set(b, 7, "t");
        get(b, 7, "t");                                          // f: o param do format
        i32c(b, SCHUB_E);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        get(b, 2, "sign");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.schubFormat"));
        set(b, 8, "handle");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "short", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "done", 0x40));
        get(b, 8, "handle");
        return new WasmFunc("kof.schubFull", List.of(WasmFunc.TYPE_F64), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I64, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64,
                        WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }
}

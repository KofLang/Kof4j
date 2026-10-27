package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fase E (27/10, #776): a CAIXA DE ERASURE do WASI — o MESMO contrato da
 * nativa §284 (RuntimeErasureBox), terceiro backend da mesma semântica: um
 * frontend nunca tem comportamentos diferentes entre backends.
 *
 * <p>Layout (24B, idêntico ao native): {@code [0]=MAGIC ímpar}
 * (0x4B4F46425F425801 — nenhum handle válido do bump heap casa, porque todo
 * handle é 8-alinhado e par), {@code [8]=tag} (mesma numeração do native:
 * 0=Int/Char, 2=Long, 3=Bool, 4=Double), {@code [16]=valor} (64 bits; o
 * Double via i64.reinterpret_f64). {@code kof.boxToStr} despacha pela tag
 * (null → "null"; não-caixa — String/record — passa cru), o MESMO despacho do
 * {@code kof_box_to_string} nativo.
 *
 * <p>Por que existe: o join HETEROGÊNEO do if-expression (`if (c) 1 else "x"`)
 * unifica em {@code java.lang.Object} com os ramos primitivos BOXEADOS —
 * medido no bytecode JVM ({@code Integer.valueOf} + {@code String.valueOf});
 * sem a caixa o valor cru não pode atravessar a fronteira de bloco da máquina
 * de estados WASI (o {@code refuseLiveStack} recusava toda a família).
 * Local do join = slot i32 ("object" no mapType), caixa e handle de String
 * são ambos i32 — indistinguíveis só pelo MAGIC, que a sonda lê ANTES de
 * tocar o handle.
 */
public final class WasmBoxRuntime {

    private WasmBoxRuntime() {
    }

    /** §284: MAGIC ímpar — nenhum handle 8-alinhado do heap casa. */
    static final long MAGIC = 0x4B4F46425F425801L;

    /** Os nomes IR que o runtime WASI implementa (o scan whitelist usa). */
    static final Set<String> NAMES = Set.of(
            "kof_box_int", "kof_box_long", "kof_box_bool", "kof_box_double",
            "kof_box_to_string");

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

    private static void get(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, i, n));
    }

    private static void set(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, n));
    }

    private static void i32c(List<WasmInstr> b, int v) {
        b.add(new WasmInstr.Const(0, v));
    }

    private static void i64c(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(1, v));
    }

    /**
     * Corpo comum dos box_*: bump de 24B no heap (global 0), [h]=MAGIC,
     * [h+8]=tag, [h+16]=valor. {@code valueIdx}/{@code hIdx} sao indices
     * UNIFICADOS (params primeiro): para boxInt/boxLong o valor É o param 0
     * e h = local 1; para boxBool/boxDouble o valor transformado (extend/
     * reinterpret) mora num LOCAL proprio — escrever no param 0 (slot i32)
     * com um i64 era o type mismatch que o validador rejeitou (medido).
     */
    private static List<WasmInstr> boxBody(int tag, int valueIdx, int hIdx) {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        set(b, hIdx, "h");                                           // h = global
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        i32c(b, 24);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));        // global += 24
        get(b, hIdx, "h");
        i64c(b, MAGIC);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 0));          // [h] = MAGIC
        get(b, hIdx, "h");
        i64c(b, tag);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 8));          // [h+8] = tag
        get(b, hIdx, "h");                                                 // [addr, valor]:
        get(b, valueIdx, "v");                                             //  o store consome
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE64, 16));         //  valor ENDE aço
        get(b, hIdx, "h");
        return b;
    }

    /** `kof.boxInt(v: i64) -> handle` — tag 0 (Int/Char; o Char boxa como Int
     *  no MESMO contrato do native: boxFn("char") = kof_box_int). */
    public static WasmFunc kofBoxInt() {
        return new WasmFunc("kof.boxInt", List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), boxBody(0, 0, 1));
    }

    /** `kof.boxLong(v: i64) -> handle` — tag 2. */
    public static WasmFunc kofBoxLong() {
        return new WasmFunc("kof.boxLong", List.of(WasmFunc.TYPE_I64), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I32), boxBody(2, 0, 1));
    }

    /** `kof.boxBool(b: i32) -> handle` — tag 3; o valor guarda ZERO-EXTENDIDO
     *  (o native guarda o qword; o dispatch faz wrap de volta). */
    public static WasmFunc kofBoxBool() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "b");
        b.add(new WasmInstr.Simple(0xad, "i64.extend_i32_u"));
        set(b, 1, "v");                                              // valor transformado no LOCAL 1
        b.addAll(boxBody(3, 1, 2));
        return new WasmFunc("kof.boxBool", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /** `kof.boxDouble(d: f64) -> handle` — tag 4; os BITS via i64.reinterpret. */
    public static WasmFunc kofBoxDouble() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "d");
        b.add(new WasmInstr.Simple(0xbd, "i64.reinterpret_f64"));
        set(b, 1, "v");                                              // bits no LOCAL 1
        b.addAll(boxBody(4, 1, 2));
        return new WasmFunc("kof.boxDouble", List.of(WasmFunc.TYPE_F64), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32), b);
    }

    /**
     * `kof.boxToStr(h) -> handle` — o despacho do valueOf(Object): null →
     * "null" (paridade JVM de String.valueOf(null)); MAGIC → pela tag (0/2 →
     * intToStr — o i64 já é a largura do Int/Long do WASI; 3 → strBool do
     * qword com wrap; 4 → doubleToStr dos bits); NÃO-caixa (String, record)
     * passa CRU. Fracionário na tag Double TRAPA dentro do doubleToStr —
     * nunca string diferente do JVM (o teto Schubfach, fatia registrada).
     * Locais: 0 = h, 1 = t (tag i64), 2 = s (resultado), 3 = tmp.
     */
    public static WasmFunc kofBoxToStr() {
        List<WasmInstr> b = new ArrayList<>();
        // s = h (o passthrough é o DEFAULT: String/record/desconhecido cru)
        get(b, 0, "l0");
        set(b, 2, "l2");
        // if h == 0 → s = "null"
        get(b, 0, "l0");
        i32c(b, 0);
        b.add(new WasmInstr.Simple(0x46, "i32.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "nul", 0x40));
        nullStr(b, 2);
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "nul", 0x40));
        // if [h] == MAGIC → despacho pela tag
        get(b, 0, "l0");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        i64c(b, MAGIC);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "mag", 0x40));
        get(b, 0, "l0");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 8));
        set(b, 1, "l1");                                                   // t = tag
        // t == 0 (Int/Char) | t == 2 (Long) → intToStr(valor) — MESMA largura
        get(b, 1, "l1");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        get(b, 1, "l1");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Simple(0x72, "i32.or"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "num", 0x40));
        get(b, 0, "l0");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 16));
        b.add(new WasmInstr.Call("kof.intToStr"));
        set(b, 2, "l2");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "num", 0x40));
        // t == 3 (Bool) → strBool(wrap(valor))
        get(b, 1, "l1");
        i64c(b, 3);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "boo", 0x40));
        get(b, 0, "l0");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 16));
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Call("kof.strBool"));
        set(b, 2, "l2");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "boo", 0x40));
        // t == 4 (Double) → doubleToStr(bits)
        get(b, 1, "l1");
        i64c(b, 4);
        b.add(new WasmInstr.Simple(0x51, "i64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "dbl", 0x40));
        get(b, 0, "l0");
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 16));
        b.add(new WasmInstr.Simple(0xbf, "f64.reinterpret_i64"));
        b.add(new WasmInstr.Call("kof.doubleToStr"));
        set(b, 2, "l2");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dbl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "boo", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "num", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "mag", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "nul", 0x40));
        get(b, 2, "l2");
        return new WasmFunc("kof.boxToStr", List.of(WasmFunc.TYPE_I32), List.of(WasmFunc.TYPE_I32),
                List.of(WasmFunc.TYPE_I64, WasmFunc.TYPE_I32, WasmFunc.TYPE_I64), b);
    }

    /**
     * Bump-aloca o handle "null" ([len=4]["null"]) SEM data segment — o
     * paridade do String.valueOf(null) da JVM. Alvo = local {@code dst}.
     */
    private static void nullStr(List<WasmInstr> b, int dst) {
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        set(b, dst, "dst");                                                 // dst = global
        b.add(new WasmInstr.Global(WasmInstr.Global.GET, 0));
        i32c(b, 9);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        b.add(new WasmInstr.Global(WasmInstr.Global.SET, 0));
        get(b, dst, "dst");
        i32c(b, 4);
        b.add(new WasmInstr.Mem(WasmInstr.Mem.STORE, 0));            // [dst] = 4
        byte[] nb = {'n', 'u', 'l', 'l'};
        for (int i = 0; i < nb.length; i++) {
            get(b, dst, "dst");
            i32c(b, 4 + i);
            b.add(new WasmInstr.Simple(0x6a, "i32.add"));
            i32c(b, nb[i]);
            b.add(new WasmInstr.Store8(0));                          // [dst+4+i]
        }
    }

    /** Todas as funções da família (o chamador registra quando usadas). */
    static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(kofBoxInt());
        fs.add(kofBoxLong());
        fs.add(kofBoxBool());
        fs.add(kofBoxDouble());
        fs.add(kofBoxToStr());
        return fs;
    }
}

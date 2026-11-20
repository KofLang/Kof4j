package dev.kof.compiler.wasm;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runtime WASI-preview1 das capabilities de host da fase 4 do plano (#776):
 * o CLOCK (`kof_time_now` — epoch-millis via `clock_time_get`) e a familia
 * `kof_random_*` (entropia via `random_get` — MESMA primitiva do SO do
 * nativo getrandom(2)/JVM SecureRandom, R11: entropia nunca caseira).
 *
 * <p>Contratos travados na matriz stdrandom (espelhados byte-a-byte do
 * {@code JvmStringRandomRuntime}/{@code RuntimeRandom}):
 * <ul>
 *   <li>{@code kof_time_now()} -> Long epoch-millis (REALTIME/1_000_000 —
 *       o mesmo {@code System.currentTimeMillis} da JVM).</li>
 *   <li>{@code kof_random_double()} -> Double em [0,1): 8 bytes -> >>> 11
 *       (53 bits de mantissa) / 2^53; nunca 1.0.</li>
 *   <li>{@code kof_random_bool}/{@code kof_random_boolean} -> Bool: bit do
 *       1o byte.</li>
 *   <li>{@code kof_random_int(bound)} -> Int em [0,bound) uniforme
 *       (rejection sampling com mascara potencia-de-2, como o
 *       {@code nextInt(bound)} da JVM); bound&lt;=0 =&gt; 0 (face leniente).</li>
 * </ul>
 *
 * <p>As faces que alocam String ({@code kof_random_hex}/{@code
 * kof_random_string}) ficam FORA desta fatia: o handle delas exige o
 * runtime de heap + append; um programa que as usa recusa honesto WASM002
 * no fecho (o {@code NAMES} nao as lista) — nunca fallback silencioso (R6).
 */
final class WasmWasiCaps {

    private WasmWasiCaps() {
    }

    /** os host-calls IMPLEMENTADOS — o fecho do emit os passa como intrinsics. */
    static final Set<String> NAMES = Set.of(
            "kof_time_now",
            "kof_random_double", "kof_random_boolean", "kof_random_bool",
            "kof_random_int");

    /** as faces que ALOCAM String (o hex/string) — o runtime proprio. */
    static final Set<String> STRING_NAMES = Set.of(
            "kof_random_hex", "kof_random_string");

    /** o programa usa o clock de parede ({@code time.now()})? */
    static boolean usesTime(List<IRMethod> methods) {
        return anyCall(methods, "kof_time_now");
    }

    /** o programa usa alguma face de random implementada? */
    static boolean usesRandom(List<IRMethod> methods) {
        for (IRMethod m : methods) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc
                            && kc.methodName().startsWith("kof_random_")
                            && (NAMES.contains(kc.methodName())
                                || STRING_NAMES.contains(kc.methodName()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** o programa usa uma face de random que ALOCA String? */
    static boolean usesRandomString(List<IRMethod> methods) {
        for (IRMethod m : methods) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc
                            && STRING_NAMES.contains(kc.methodName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * a familia random IMPLEMENTADA no programa: a maquinaria de
     * double/impressao nao depende desta fatia — so o consumidor direto.
     * `double` entra so com o println de double (o consumo escala).
     */
    static List<WasmFunc> randomFamily(List<IRMethod> methods) {
        List<WasmFunc> f = new ArrayList<>();
        if (usesRandom(methods)) f.add(kofRandomDouble());
        f.add(kofRandomBool("kof_random_boolean"));
        f.add(kofRandomBool("kof_random_bool"));
        f.add(kofRandomInt());
        return f;
    }

    private static boolean anyCall(List<IRMethod> methods, String name) {
        for (IRMethod m : methods) {
            for (IRBasicBlock bb : m.basicBlocks()) {
                for (KofOperation op : bb.operations()) {
                    if (op instanceof KofCall kc && name.equals(kc.methodName())) return true;
                }
            }
        }
        return false;
    }

    // ── kof_time_now ──────────────────────────────────────────────────────

    /** {@code kof_time_now() -> Long} — epoch-millis. clockid 0 = REALTIME. */
    static WasmFunc kofTimeNow() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, 0));                       // clockid REALTIME
        b.add(new WasmInstr.Const(1, 0));                       // precision (i64)
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_CLOCK));
        b.add(new WasmInstr.Call("clock_time_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));              // errno (o slot zera)
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_CLOCK));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));      // nanos
        b.add(new WasmInstr.Const(1, 1_000_000L));
        b.add(new WasmInstr.Simple(0x80, "i64.div_u"));         // -> millis
        return new WasmFunc("kof_time_now", List.of(), List.of(0x7e), List.of(), b);
    }

    // ── kof_random_* ──────────────────────────────────────────────────────

    /** {@code kof_random_double() -> Double} — [0,1): >>>11 / 2^53. */
    static WasmFunc kofRandomDouble() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Const(0, 8));
        b.add(new WasmInstr.Call("random_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Const(1, 11L));
        b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));         // 53 bits
        b.add(new WasmInstr.Simple(0xba, "f64.convert_i64_u"));
        b.add(new WasmInstr.Const(9007199254740992.0));         // 2^53
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        return new WasmFunc("kof_random_double", List.of(), List.of(0x7c), List.of(), b);
    }

    /** {@code kof_random_bool}/{@code kof_random_boolean} — bit do 1o byte. */
    static WasmFunc kofRandomBool(String name) {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Call("random_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, 0));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x71, "i32.and"));
        return new WasmFunc(name, List.of(), List.of(0x7f), List.of(), b);
    }

    /**
     * {@code kof_random_int(bound) -> Int} — [0,bound) uniforme: mascara
     * potencia-de-2 sobre 64 bits + rejection sampling (o mesmo esquema do
     * {@code Random.nextInt(bound)} da JVM: nunca o modulo enviesado).
     * bound&lt;=0 =&gt; 0 (face leniente).
     */
    static WasmFunc kofRandomInt() {
        List<WasmInstr> b = new ArrayList<>();
        // bound <= 0 -> 0
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "bound"));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Simple(0x57, "i64.le_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7e));
        b.add(new WasmInstr.Const(1, 0L));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7e));
        // mask = bound-1 espalhado (|>>1 |>>2 |>>4 |>>8 |>>16 |>>32)
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "bound"));
        b.add(new WasmInstr.Const(1, 1L));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "mask"));
        spread(b, 1);
        // loop de rejeicao: v = 8 bytes & mask; aceita quando v < bound
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "racc", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "rtry", 0x40));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Const(0, 8));
        b.add(new WasmInstr.Call("random_get"));
        b.add(new WasmInstr.Simple(0x1a, "drop"));
        b.add(new WasmInstr.Const(0, WasmStdoutRuntime.SCRATCH_RND));
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD64, 0));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 1, "mask"));
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "v"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "v"));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 0, "bound"));
        b.add(new WasmInstr.Simple(0x54, "i64.lt_u"));
        b.add(new WasmInstr.Branch("racc", 1, true));           // v < bound: sai
        b.add(new WasmInstr.Branch("rtry", 0));                 // retry
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "rtry", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "racc", 0x40));
        b.add(new WasmInstr.Local(WasmInstr.Local.GET, 2, "v"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7e));
        return new WasmFunc("kof_random_int", List.of(0x7e), List.of(0x7e),
                List.of(0x7e, 0x7e, 0x7e), b);
    }

    private static void spread(List<WasmInstr> b, int maskIdx) {
        for (long shift : new long[]{1, 2, 4, 8, 16, 32}) {
            b.add(new WasmInstr.Local(WasmInstr.Local.GET, maskIdx, "mask"));
            b.add(new WasmInstr.Local(WasmInstr.Local.GET, maskIdx, "mask"));
            b.add(new WasmInstr.Const(1, shift));
            b.add(new WasmInstr.Simple(0x88, "i64.shr_u"));
            b.add(new WasmInstr.Simple(0x84, "i64.or"));
            b.add(new WasmInstr.Local(WasmInstr.Local.SET, maskIdx, "mask"));
        }
    }
}

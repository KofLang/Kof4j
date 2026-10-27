package dev.kof.compiler.wasm;

import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A familia INT do {@code math} no runtime WASI (Fase E, 26/10, #776):
 * {@code abs/sign/min/max/clamp} + os predicados {@code isEven/isOdd/
 * isPositive/isNegative/isZero} — aritmetica i64 pura, SEM host imports
 * (servem para o WASI e para o WASM). A semantica e' a do JVM: abs de
 * MIN_VALUE continua MIN_VALUE (wrap, o mesmo contrato do
 * {@code Math.abs(int)}), os predicados comparam SIGNED.
 *
 * <p>O sqrt/pow/trig ficam fora (libm no nativo; no WASM exigiria host ou
 * polinomios — fatia propria, recusa honesta hoje).
 */
final class WasmMathRuntime {

    private WasmMathRuntime() {
    }

    /** Os nomes que o runtime WASI implementa (o scanwhitelist usa). */
    static final Set<String> NAMES = Set.of(
            "kof_math_abs", "kof_math_sign", "kof_math_min", "kof_math_max",
            "kof_math_clamp", "kof_math_isEven", "kof_math_isOdd",
            "kof_math_isPositive", "kof_math_isNegative", "kof_math_isZero",
            // Fase E (27/10, #776): a familia DOUBLE de aritmetica pura —
            // sqrt e' INSTRUCAO nativa do WASM (0x9f); toRadians/toDegrees
            // repl ica m a ordem do Math.toRadians/Degrees da JVM (div 180,
            // mul PI — a mesma sequencia, bit a bit); lerp/percentage sao
            // aritmetica pura com a ordem do runtime nativo. sin/cos/tan/
            // asin/acos/atan/atan2/pow continuam FORA (libm — fatia propria).
            "kof_math_sqrt", "kof_math_toRadians", "kof_math_toDegrees",
            "kof_math_lerp", "kof_math_percentage");

    static boolean usesAny(List<IRMethod> methods) {
        for (IRMethod m : methods) {
            for (dev.kof.compiler.IRBasicBlock bb : m.basicBlocks()) {
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

    private static void i64c(List<WasmInstr> b, long v) {
        b.add(new WasmInstr.Const(1, v));
    }

    private static List<WasmInstr> absBody() {
        // (x ^ (x >>s 63)) - (x >>s 63): MIN_VALUE wrapa como o JVM
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        get(b, 0, "x");
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0x85, "i64.xor"));
        get(b, 0, "x");
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        return b;
    }

    private static List<WasmInstr> maxBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "a");
        get(b, 1, "b");
        get(b, 0, "a");
        get(b, 1, "b");
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        b.add(new WasmInstr.Simple(0x1b, "select"));
        return b;
    }

    private static List<WasmInstr> minBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "a");
        get(b, 1, "b");
        get(b, 0, "a");
        get(b, 1, "b");
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Simple(0x1b, "select"));
        return b;
    }

    /** clamp(v, lo, hi) = v < lo ? lo : (v > hi ? hi : v). Locais: 3=t. */
    private static List<WasmInstr> clampBody() {
        List<WasmInstr> b = new ArrayList<>();
        // select: [val1, val2, cond] — o val1 e' o escolhido quando a
        // condicao (no topo) e' verdadeira
        get(b, 0, "v");
        get(b, 1, "lo");
        get(b, 1, "lo");
        get(b, 0, "v");
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        b.add(new WasmInstr.Simple(0x1b, "select"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "t"));
        get(b, 3, "t");
        get(b, 2, "hi");
        get(b, 2, "hi");
        get(b, 3, "t");
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        b.add(new WasmInstr.Simple(0x1b, "select"));
        return b;
    }

    /** sign = (x >>s 63) | ((0 - x) >>s 63 & 1): -1/0/1 sem ramos. */
    private static List<WasmInstr> signBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        i64c(b, 0);
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x7d, "i64.sub"));
        i64c(b, 63);
        b.add(new WasmInstr.Simple(0x87, "i64.shr_s"));
        i64c(b, 1);
        b.add(new WasmInstr.Simple(0x83, "i64.and"));
        b.add(new WasmInstr.Simple(0x84, "i64.or"));
        return b;
    }

    private static List<WasmInstr> isEvenBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x81, "i64.rem_s"));
        b.add(new WasmInstr.Simple(0x50, "i64.eqz"));
        return b;
    }

    private static List<WasmInstr> isOddBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        i64c(b, 2);
        b.add(new WasmInstr.Simple(0x81, "i64.rem_s"));
        b.add(new WasmInstr.Simple(0x50, "i64.eqz"));
        b.add(new WasmInstr.Const(0, 1));
        b.add(new WasmInstr.Simple(0x73, "i32.xor"));
        return b;
    }

    private static List<WasmInstr> isPositiveBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x55, "i64.gt_s"));
        return b;
    }

    private static List<WasmInstr> isNegativeBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x53, "i64.lt_s"));
        return b;
    }

    private static List<WasmInstr> isZeroBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x50, "i64.eqz"));
        return b;
    }

    // ---- familia DOUBLE (f64): sqrt = instrucao nativa; toRadians/
    // toDegrees com a MESMA ordem do Math.toRadians/ToDegrees da JVM
    // (v/180*PI e v*180/PI) para zero-drift bit a bit; lerp/percentage
    // com a ordem do runtime nativo. ----

    private static List<WasmInstr> sqrtBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x9f, "f64.sqrt"));
        return b;
    }

    private static List<WasmInstr> toRadiansBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "d");
        b.add(new WasmInstr.Const(180.0));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Const(Math.PI));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        return b;
    }

    private static List<WasmInstr> toDegreesBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "d");
        b.add(new WasmInstr.Const(180.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Const(Math.PI));
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        return b;
    }

    /** lerp(a,b,t) = a + (b-a)*t (a ordem do runtime nativo). Locais: 3=t? nao — sem locais. */
    private static List<WasmInstr> lerpBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "a");
        get(b, 1, "b");
        get(b, 0, "a");
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        get(b, 2, "t");
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        return b;
    }

    /** percentage(part,total) = part/total*100.0 (div-then-mul, ordem do nativo). */
    private static List<WasmInstr> percentageBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "part");
        get(b, 1, "total");
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Const(100.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        return b;
    }

    /** Todas as funcoes da familia (o chamador registra quando usadas). */
    static List<WasmFunc> all() {
        List<WasmFunc> fs = new ArrayList<>();
        fs.add(new WasmFunc("kof_math_abs", List.of(0x7e), List.of(0x7e), List.of(), absBody()));
        fs.add(new WasmFunc("kof_math_max", List.of(0x7e, 0x7e), List.of(0x7e), List.of(), maxBody()));
        fs.add(new WasmFunc("kof_math_min", List.of(0x7e, 0x7e), List.of(0x7e), List.of(), minBody()));
        fs.add(new WasmFunc("kof_math_clamp", List.of(0x7e, 0x7e, 0x7e), List.of(0x7e), List.of(0x7e), clampBody()));
        fs.add(new WasmFunc("kof_math_sign", List.of(0x7e), List.of(0x7e), List.of(), signBody()));
        fs.add(new WasmFunc("kof_math_isEven", List.of(0x7e), List.of(0x7f), List.of(), isEvenBody()));
        fs.add(new WasmFunc("kof_math_isOdd", List.of(0x7e), List.of(0x7f), List.of(), isOddBody()));
        fs.add(new WasmFunc("kof_math_isPositive", List.of(0x7e), List.of(0x7f), List.of(), isPositiveBody()));
        fs.add(new WasmFunc("kof_math_isNegative", List.of(0x7e), List.of(0x7f), List.of(), isNegativeBody()));
        fs.add(new WasmFunc("kof_math_isZero", List.of(0x7e), List.of(0x7f), List.of(), isZeroBody()));
        // DOUBLE
        fs.add(new WasmFunc("kof_math_sqrt", List.of(0x7c), List.of(0x7c), List.of(), sqrtBody()));
        fs.add(new WasmFunc("kof_math_toRadians", List.of(0x7c), List.of(0x7c), List.of(), toRadiansBody()));
        fs.add(new WasmFunc("kof_math_toDegrees", List.of(0x7c), List.of(0x7c), List.of(), toDegreesBody()));
        fs.add(new WasmFunc("kof_math_lerp", List.of(0x7c, 0x7c, 0x7c), List.of(0x7c), List.of(), lerpBody()));
        fs.add(new WasmFunc("kof_math_percentage", List.of(0x7c, 0x7c), List.of(0x7c), List.of(), percentageBody()));
        return fs;
    }
}

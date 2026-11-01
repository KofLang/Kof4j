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
            "kof_math_lerp", "kof_math_percentage",
            // S1b.3: roundTo(value, decimals) — half-away-from-zero por escala
            // repetida (a MESMA aritmetica do JVM/native: p = 10^m por
            // multiplicacao repetida, cada passo 1 op IEEE arredondada)
            "kof_math_roundTo", "kof_math_round_half",
            // MATH-D (27/10, #776): abs/min/max/floor/ceil DOUBLE — as
            // instruções IEEE nativas do WASM (abs 0x8b, min 0xA4, max 0xA5,
            // floor 0x9c, ceil 0x9b); min/max casam o contrato ±0/NaN da JVM
            "kof_math_dabs", "kof_math_floor", "kof_math_ceil",
            "kof_math_dmin", "kof_math_dmax",
            // MATH-D-2 (27/10): clamp/sign doubles — as cadeias branchy do
            // CONTRATO da família int (nunca min/max); o select só no sign
            "kof_math_dclamp", "kof_math_dsign",
            // MATH-LIBM-1 (27/10): o __kernel_sin fdlibm — WasmSinKernel
            // (split check_500). A fundação MEDIDA: Math.sin == StrictMath
            // em 29/29; cos DERIVA 5/29 por 1 ulp (a face cos paridade está
            // MORTA — veredito registrado)
            "kof_math_sin",
            // MATH-TAN-1 (27/10, redo): o kernel_tan fdlibm na faixa pequena
            "kof_math_tan",
            // MATH-ATAN-1 (28/10): o __kernel_atan fdlibm — AUTOCONTIDO (sem
            // rem_pio2); Math.atan == StrictMath 34/34 medido (asin/acos/
            // atan2 também GO — as fatias seguintes)
            "kof_math_atan",
            // MATH-ASIN-1 (28/10): o __ieee754_asin fdlibm — WasmAsinKernel
            // (o wrapper acos = π/2 − asin MORTO: 8/18 por 1 ulp, medido)
            "kof_math_asin",
            // MATH-ACOS-1 (28/10): o __ieee754_acos fdlibm — WasmAcosKernel
            // (as correções df/c do próprio fdlibm — nunca o wrapper morto)
            "kof_math_acos",
            // MATH-ATAN2-1 (28/10): o __ieee754_atan2 fdlibm — reusa o func
            // do atan via CALL (os quadrantes + os casos especiais)
            "kof_math_atan2",
            // MATH-POW-1 (01/11): o __ieee754_pow fdlibm — WasmPowKernel
            // (o driver) + WasmPowMain (o log2/a cauda/o scalb) — a ÚLTIMA
            // fatia libm da família
            "kof_math_pow");

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

    private static void i32c(List<WasmInstr> b, int v) {
        b.add(new WasmInstr.Const(0, v));
    }

    private static void set(List<WasmInstr> b, int i, String n) {
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, i, n));
    }

    private static void load8(List<WasmInstr> b, int off) {
        b.add(new WasmInstr.Mem(WasmInstr.Mem.LOAD8U, off));
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

    // ---- MATH-D (27/10): as cinco double — instruções IEEE nativas ----

    private static List<WasmInstr> mathDAbsBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        return b;
    }

    private static List<WasmInstr> mathDFloorBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x9c, "f64.floor"));
        return b;
    }

    private static List<WasmInstr> mathDCeilBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x9b, "f64.ceil"));
        return b;
    }

    private static List<WasmInstr> mathDMinBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "a");
        get(b, 1, "b");
        b.add(new WasmInstr.Simple(0xa4, "f64.min"));
        return b;
    }

    private static List<WasmInstr> mathDMaxBody() {
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "a");
        get(b, 1, "b");
        b.add(new WasmInstr.Simple(0xa5, "f64.max"));
        return b;
    }

    // ---- MATH-D-2 (27/10): clamp/sign doubles ----

    /** v<lo?lo:(v>hi?hi:v) — o if/else RESULT-TYPED (a máquina do
     *  roundTo): o CONTRATO branchy da família int VERBATIM (o ±0 pela
     *  igualdade IEEE — lt(-0.0,0.0) e' false como no JVM; o NaN propaga —
     *  toda comparação da cadeia da false e o v cru volta). */
    private static List<WasmInstr> mathDClampBody() {
        List<WasmInstr> b = new ArrayList<>();
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, null, 0x7c));
        get(b, 0, "v");
        get(b, 1, "lo");
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 1, "lo");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c)); // o ELSE SUBSTITUI o end do then
        get(b, 0, "v");
        get(b, 2, "hi");
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, null, 0x7c));
        get(b, 2, "hi");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, null, 0x7c)); // idem
        get(b, 0, "v");                                                     // ELSE: v
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));  // fecha o if interno
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));  // fecha o if externo
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, null, 0x7c));  // fecha o block
        return b;
    }

    /** x>0?1:(x<0?-1:0) — a cadeia do sign int (o NaN → 0: as duas
     *  comparações da false e o select devolve o 0 do meio). */
    private static List<WasmInstr> mathDSignBody() {
        List<WasmInstr> b = new ArrayList<>();
        // x<0 ? -1 : (x>0 ? 1 : 0) — o cond do select EXTERNO tem de ficar
        // NO TOPO: c1 salvo no scratch i32 (local 1), o select interno
        // empilha o r sobre o -1 e o c1 entra por último (o zero das
        // comparações é f64 — o Const(0,·) seria o i32 errado)
        get(b, 0, "x");
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x63, "f64.lt"));                 // x < 0
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "c1"));    // c1 salvo
        b.add(new WasmInstr.Const(1, -1));                           // v1 = -1
        b.add(new WasmInstr.Const(1, 1));                            // v2a = 1
        b.add(new WasmInstr.Const(1, 0));                            // v2b = 0
        get(b, 0, "x");
        b.add(new WasmInstr.Const(0.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));                 // o cond POR ÚLTIMO
        b.add(new WasmInstr.Simple(0x1b, "select"));                 // interno = x>0 ? 1 : 0
        get(b, 1, "c1");                                             // o cond do externo
        b.add(new WasmInstr.Simple(0x1b, "select"));                 // c1 ? -1 : interno
        return b;
    }

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

    // ---- roundTo(value, decimals): half-away-from-zero por escala decimal
    // repetida (S1b.3/DECISIONS §3 — a MESMA aritmetica do JVM e do nativo:
    // p = 10^|d| saturado em 308 por multiplicacao repetida, cada passo 1 op
    // IEEE; d>=0 escala p/ cima, d<0 p/ baixo; overflow devolve v) ----

    private static List<WasmInstr> roundHalfBody() {
        // x com |x| >= 2^52 ja e' inteiro → devolve x (NaN/Inf nunca chegam)
        List<WasmInstr> b = new ArrayList<>();
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(4503599627370496.0));
        b.add(new WasmInstr.Simple(0x64, "f64.gt"));
        // if/else com RESULTADO f64 (0x7c): cada braco deixa exatamente 1 f64
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "big", 0x7c));
        get(b, 0, "x");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "big", 0x7c));
        // t = trunc(x); f = x - t
        get(b, 0, "x");
        b.add(new WasmInstr.Simple(0x9d, "f64.trunc"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));
        get(b, 0, "x");
        get(b, 1, "t");
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 2, "f"));
        // f >= 0.5 → t+1; senao f <= -0.5 → t-1
        get(b, 2, "f");
        b.add(new WasmInstr.Const(0.5));
        b.add(new WasmInstr.Simple(0x66, "f64.ge"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "up", 0x40));
        get(b, 1, "t");
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa0, "f64.add"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "up", 0x40));
        get(b, 2, "f");
        b.add(new WasmInstr.Const(-0.5));
        b.add(new WasmInstr.Simple(0x65, "f64.le"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "dn", 0x40));
        get(b, 1, "t");
        b.add(new WasmInstr.Const(1.0));
        b.add(new WasmInstr.Simple(0xa1, "f64.sub"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 1, "t"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "dn", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "up", 0x40));
        get(b, 1, "t");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "big", 0x7c));
        return b;
    }

    /** roundTo(v, d): d>=0 escala v*p e r/p; d<0 escala v/p e r*p; Inf devolve v. */
    private static List<WasmInstr> roundToBody() {
        List<WasmInstr> b = new ArrayList<>();
        // m = |d| saturado em 308 (i32)
        get(b, 1, "d");
        b.add(new WasmInstr.Simple(0xa7, "i32.wrap_i64"));
        b.add(new WasmInstr.Local(WasmInstr.Local.SET, 3, "m"));
        get(b, 3, "m");
        b.add(new WasmInstr.Const(0, 0));
        b.add(new WasmInstr.Simple(0x48, "i32.lt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "neg", 0x40));
        i32c(b, 0);
        get(b, 3, "m");
        b.add(new WasmInstr.Simple(0x6b, "i32.sub"));
        set(b, 3, "m");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "neg", 0x40));
        get(b, 3, "m");
        i32c(b, 308);
        b.add(new WasmInstr.Simple(0x4a, "i32.gt_s"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "sat", 0x40));
        i32c(b, 308);
        set(b, 3, "m");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "sat", 0x40));
        // p = 10^m por multiplicacao repetida
        b.add(new WasmInstr.Const(1.0));
        set(b, 2, "p");
        i32c(b, 0);
        set(b, 4, "i");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.BLOCK, "pw", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.LOOP, "pl", 0x40));
        get(b, 4, "i");
        get(b, 3, "m");
        b.add(new WasmInstr.Simple(0x4e, "i32.ge_s"));
        b.add(new WasmInstr.Branch("pw", 1, true));
        get(b, 2, "p");
        b.add(new WasmInstr.Const(10.0));
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 2, "p");
        get(b, 4, "i");
        i32c(b, 1);
        b.add(new WasmInstr.Simple(0x6a, "i32.add"));
        set(b, 4, "i");
        b.add(new WasmInstr.Branch("pl", 0));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pl", 0x40));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pw", 0x40));
        // d >= 0: (roundHalf(v*p))/p ; d < 0: (roundHalf(v/p))*p
        get(b, 1, "d");
        i64c(b, 0);
        b.add(new WasmInstr.Simple(0x59, "i64.ge_s"));
        // if/else com RESULTADO f64 (0x7c): cada braco deixa exatamente 1 f64
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "pos", 0x7c));
        get(b, 0, "v");
        get(b, 2, "p");
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        set(b, 5, "scaled");
        scaledOverflowNoOp(b);
        get(b, 2, "p");
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "pos", 0x7c));
        get(b, 0, "v");
        get(b, 2, "p");
        b.add(new WasmInstr.Simple(0xa3, "f64.div"));
        set(b, 5, "scaled");
        scaledOverflowNoOp(b);
        get(b, 2, "p");
        b.add(new WasmInstr.Simple(0xa2, "f64.mul"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "pos", 0x7c));
        return b;
    }


    /** JVM kof_math_roundTo: Infinite(scaled) → devolve v (no-op de overflow).
     *  scaled entra em local 5; o if/else deixa exatamente 1 f64. */
    private static void scaledOverflowNoOp(List<WasmInstr> b) {
        get(b, 5, "scaled");
        b.add(new WasmInstr.Simple(0x99, "f64.abs"));
        b.add(new WasmInstr.Const(Double.POSITIVE_INFINITY));
        b.add(new WasmInstr.Simple(0x61, "f64.eq"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.IF, "ovf", 0x7c));
        get(b, 0, "v");
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.ELSE, "ovf", 0x7c));
        get(b, 5, "scaled");
        b.add(new WasmInstr.Call("kof_math_round_half"));
        b.add(new WasmInstr.Blocking(WasmInstr.Blocking.END, "ovf", 0x7c));
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
        // roundTo(value: f64, decimals: i64) -> f64 + o helper half-away
        fs.add(new WasmFunc("kof_math_round_half", List.of(0x7c), List.of(0x7c), List.of(0x7c, 0x7c), roundHalfBody()));
        fs.add(new WasmFunc("kof_math_roundTo", List.of(0x7c, 0x7e), List.of(0x7c), List.of(0x7c, 0x7f, 0x7f, 0x7c), roundToBody()));
        // MATH-D (27/10): as cinco double — instruções IEEE nativas
        fs.add(new WasmFunc("kof_math_dabs", List.of(0x7c), List.of(0x7c), List.of(), mathDAbsBody()));
        fs.add(new WasmFunc("kof_math_floor", List.of(0x7c), List.of(0x7c), List.of(), mathDFloorBody()));
        fs.add(new WasmFunc("kof_math_ceil", List.of(0x7c), List.of(0x7c), List.of(), mathDCeilBody()));
        fs.add(new WasmFunc("kof_math_dmin", List.of(0x7c, 0x7c), List.of(0x7c), List.of(), mathDMinBody()));
        fs.add(new WasmFunc("kof_math_dmax", List.of(0x7c, 0x7c), List.of(0x7c), List.of(), mathDMaxBody()));
        // MATH-D-2 (27/10): clamp (o if/else RESULT-TYPED 0x7c — a máquina
        // do roundTo) + sign (o select i64)
        fs.add(new WasmFunc("kof_math_dclamp", List.of(0x7c, 0x7c, 0x7c), List.of(0x7c), List.of(), mathDClampBody()));
        fs.add(new WasmFunc("kof_math_dsign", List.of(0x7c), List.of(0x7e), List.of(0x7f), mathDSignBody()));
        // MATH-LIBM-1/2 (27/10): o kernel_sin + o caso especial n=±1 + o
        // kernel_cos — WasmSinKernel (split check_500)
        fs.add(WasmSinKernel.sinFunc());
        fs.add(WasmSinKernel.smallFunc());
        fs.add(WasmSinKernel.specialFunc());
        fs.add(WasmSinKernel.nearHalfFunc());
        fs.add(WasmSinKernel.cosFunc());
        // MATH-TAN-1 (27/10, redo): o kernel_tan fdlibm — WasmTanKernel
        fs.add(WasmTanKernel.tanFunc());
        // MATH-ATAN-1 (28/10): o kernel_atan fdlibm — WasmAtanKernel
        fs.add(WasmAtanKernel.atanFunc());
        // MATH-ASIN-1 (28/10): o kernel_asin fdlibm — WasmAsinKernel
        fs.add(WasmAsinKernel.asinFunc());
        // MATH-ACOS-1 (28/10): o kernel_acos fdlibm — WasmAcosKernel
        fs.add(WasmAcosKernel.acosFunc());
        // MATH-ATAN2-1 (28/10): o kernel_atan2 fdlibm — reusa o atan
        fs.add(WasmAtan2Kernel.atan2Func());
        // MATH-POW-1 (01/11): o pow fdlibm — o driver + o hugey
        // (WasmPowKernel) e o main/tail/scalb + os 4 helpers de bits
        // (WasmPowMain); o dedupe por nome torna o add idempotente
        fs.add(WasmPowKernel.powFunc());
        fs.add(WasmPowKernel.hugeyFunc());
        fs.add(WasmPowMain.mainFunc());
        fs.add(WasmPowMain.tailFunc());
        fs.add(WasmPowMain.scalbFunc());
        fs.add(WasmPowMain.hiFunc());
        fs.add(WasmPowMain.loFunc());
        fs.add(WasmPowMain.withHiFunc());
        fs.add(WasmPowMain.lo0Func());
        fs.add(WasmRemPio2.smallTailFunc());
        fs.add(WasmRemPio2.mediumFunc());
        // MATH-LIBM-4 fatia B (28/10): o caminho GRANDE — o driver (a quebra
        // tx + o despacho n&3) + o kernel da memória-scratch. O heapAlloc
        // entra junto: o driver aloca o scratch de 928B (o dedupe por nome
        // torna o add redundante seguro quando já há coleções)
        fs.add(WasmRemPio2Large.largeFunc());
        fs.add(WasmRemPio2Kernel.kernelFunc());
        fs.add(WasmListRuntime.heapAlloc());
        return fs;
    }
}

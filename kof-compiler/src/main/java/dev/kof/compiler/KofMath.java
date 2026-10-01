package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.math} (STDLIB P0-a, plan-stdlib-expansion S1).
 *
 * Intention-first numeric helpers: {@code math.clamp(v, lo, hi)},
 * {@code math.abs(x)}, {@code math.isEven(x)}. Maps to {@code kof_math_*}
 * runtime functions on each backend. S1 is Int-only (clamp/abs/sign/min/max/
 * isEven/isOdd/isPositive/isNegative/isZero) — all available on JVM / Native /
 * JS / interpreter with byte-identical parity. Double variants (sqrt/lerp/
 * percentage/isInteger/isDecimal/pow/roundTo) are S1b (MATH001 closed 11/09 —
 * riscv slice B32; FLT001 caution: tests compare via Bool, never raw print).
 */
public final class KofMath {

    private KofMath() {}

    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type DOUBLE = Type.PrimitiveType.DOUBLE;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("math");

    static boolean isMathNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record MathCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo. */
    // 19/09 LSP-A fatia 3: a familia case "isEven","isOdd",... e o case
    // "isInteger","isDecimal" bindam os 5/2 — a lista so tinha o primeiro
    // literal de cada (drift do tipo db/process/net; lock novo segue virgulas).
    static List<String> functions() { return List.of("abs", "sign", "clamp", "min", "max",
            "isEven", "isOdd", "isPositive", "isNegative", "isZero", "sqrt", "lerp", "percentage", "isInteger", "isDecimal",
      "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
      "toRadians", "toDegrees", "pi", "e", "tau", "roundTo", "pow", "parseInt", "parseLong", "parseDouble", "parseIntOrDefault", "parseLongOrDefault", "parseDoubleOrDefault"); }
    static MathCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"math".equals(namespace)) return null;
        int argc = argTypes.size();
        return switch (name) {
            case "abs" -> argc == 1 && isInt(argTypes.get(0))
                    ? new MathCall("kof_math_abs", INT, List.of(INT)) : null;
            case "sign" -> argc == 1 && isInt(argTypes.get(0))
                    ? new MathCall("kof_math_sign", INT, List.of(INT)) : null;
            case "clamp" -> argc == 3
                    ? new MathCall("kof_math_clamp", INT, List.of(INT, INT, INT)) : null;
            case "min" -> argc == 2
                    ? new MathCall("kof_math_min", INT, List.of(INT, INT)) : null;
            case "max" -> argc == 2
                    ? new MathCall("kof_math_max", INT, List.of(INT, INT)) : null;
            case "isEven", "isOdd", "isPositive", "isNegative", "isZero" -> argc == 1
                    ? new MathCall("kof_math_" + name, BOOL, List.of(INT)) : null;
            // S1b wedge: sqrt = PRIMEIRO Double em kof.math (x86 sqrtsd — FLT
            // fechado 31/08 via XMM). NaN em <0 paridade JVM/JS (Math.sqrt).
            // riscv64/aarch64 = fatia B32 (fsqrt.d) — MATH001 fechado 11/09.
            case "sqrt" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_sqrt", DOUBLE, List.of(DOUBLE)) : null;
            // Trigonometric functions
            case "sin", "cos", "tan", "asin", "acos", "atan" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_" + name, DOUBLE, List.of(DOUBLE)) : null;
            case "atan2" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_atan2", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;
            case "toRadians", "toDegrees" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_" + name, DOUBLE, List.of(DOUBLE)) : null;
            // Mathematical constants (zero-arg functions)
            case "pi", "e", "tau" -> argc == 0
                    ? new MathCall("kof_math_" + name, DOUBLE, List.of()) : null;
            // S1b.1: escalares Double puros (SSE2 — sem libm, sem floor).
            // lerp/percentage: sub/mul/add/divsd. isInteger/isDecimal:
            // NaN→false, Inf→false, |x|>=2^52→true (finite big = integer),
            // senão x==trunc(x). Guard de tipo: args Double explícitos
            // (Int não alarga em silêncio — SEM025, R6).
            case "lerp" -> argc == 3 && isDouble(argTypes.get(0))
                    && isDouble(argTypes.get(1)) && isDouble(argTypes.get(2))
                    ? new MathCall("kof_math_lerp", DOUBLE, List.of(DOUBLE, DOUBLE, DOUBLE)) : null;
            case "percentage" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_percentage", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;
            case "isInteger", "isDecimal" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_" + name, BOOL, List.of(DOUBLE)) : null;
            // S1b.3 (DECISIONS §3): roundTo(value: Double, decimals: Int) —
            // half-away-from-zero (âncora C round()) por escala decimal
            // determinística (âncora Java BigDecimal.setScale). SEM libm: o
            // 10^decimals é potência por multiplicação/divisão REPETIDA (cada
            // passo é uma operação IEEE corretamente arredondada → byte-idêntico
            // nos 5 alvos). decimals pode ser negativo (arredonda p/ dezenas,
            // centenas...). Guard de tipo: decimals Int obrigatório (sem
            // widening silencioso, SEM025/R6).
            case "roundTo" -> argc == 2 && isDouble(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new MathCall("kof_math_roundTo", DOUBLE, List.of(DOUBLE, INT)) : null;
            // S1b.2 (decisão 7a da mantenedora 13/09): pow = PRIMEIRO caso que
            // exige libm no native (call pow@PLT + link -lm no x86, que já é
            // dinâmico). JVM/SCRIPT/JS = Math.pow / ** (exato). riscv/aarch =
            // MATH001 (link cross é estático sem libc — ligar libm mudaria o
            // modelo de runtime da lane nat; recusa honesta aqui, nunca link
            // quebrado silencioso).
            case "pow" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_pow", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;
            // S13a (plan-stdlib-expansion §2, P0): parse numérico como fachada
            // de namespace sobre as runtime fns EXISTENTES kof_string_to_*
            // (regra 2 — zero runtime novo nos 4 alvos). Contrato = JDK
            // Integer.parseInt/Long.parseLong/Double.parseDouble com trim
            // (idem `.toInt()`); inválido/overflow LANÇA (String runtime fn
            // kof_throw_string no native, exceção nos demais). Guard de tipo:
            // arg STR obrigatório (número não alarga de/para String em
            // silêncio — SEM025, R6). OrNull/OrDefault = S13b/S13c.
            case "parseInt" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_int", INT, List.of(STR)) : null;
            case "parseLong" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_long", LONG, List.of(STR)) : null;
            case "parseDouble" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_double", DOUBLE, List.of(STR)) : null;
            // S13b (plan-stdlib-expansion §2, P0): parse com default —
            // briefing §43 ("falha de parse = OrNull/OrDefault"). Mesmo
            // contrato do parse (JDK + trim); falha DEVOLVE o default
            // (nunca lança — paridade: JVM try/catch, JS wrapper, x86/riscv
            // handler local no exc_chain). Default tipado (Int/Long/Double
            // literal ou expressão — número não alarga p/ String, SEM025).
            case "parseIntOrDefault" -> argc == 2 && isStr(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new MathCall("kof_string_to_int_or_default", INT, List.of(STR, INT)) : null;
            case "parseLongOrDefault" -> argc == 2 && isStr(argTypes.get(0))
                    && (isLong(argTypes.get(1)) || isInt(argTypes.get(1)))
                ? new MathCall("kof_string_to_long_or_default", LONG, List.of(STR, LONG)) : null;
            case "parseDoubleOrDefault" -> argc == 2 && isStr(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_string_to_double_or_default", DOUBLE, List.of(STR, DOUBLE)) : null;
            default -> null;
        };
    }

    /** S1 (Int) + S1b/S1b.1 (Double) em TODOS os targets (MATH001 fechado
     * 11/09 — fatia riscv B32 + tradutor aarch fsqrt.d/fcvtzs; prova qemu).
     * S1b.2 pow (decisão 7a): x86 sim (pow@PLT + -lm); riscv/aarch NÃO —
     * o link cross é estático sem libc (invariante "asm puro" da lane nat;
     * ligar libm = decisão de arquitetura, regra 6). Recusa com código
     * MATH001 (R6), nunca undefined-reference silencioso. */
    static boolean supportedOn(@SuppressWarnings("unused") String function, @SuppressWarnings("unused") Target target) {
        // row 10 (27/09, D-DECISION-BATCH-2709B #3): pow roda tambem no cross
        // (shim kof_math_pow -> pow@PLT; -lm por uso). MATH001 fica reservado.
        return true;
    }

    static String gapCode(String function) {
        return "MATH001";
    }

    private static boolean isInt(Type t) {
        return t == INT || "int".equals(t.toString()) || "Int".equals(t.toString());
    }

    private static boolean isDouble(Type t) {
        return t == DOUBLE || "double".equals(t.toString()) || "Double".equals(t.toString());
    }

    private static boolean isStr(Type t) {
        return BuiltinTypes.STRING.equals(t) || "String".equals(t.toString());
    }

    private static boolean isLong(Type t) {
        return t == LONG || "long".equals(t.toString()) || "Long".equals(t.toString());
    }
}

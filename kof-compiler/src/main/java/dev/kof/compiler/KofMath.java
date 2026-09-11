package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.math} (STDLIB P0-a, plan-stdlib-expansion S1).
 *
 * Intention-first numeric helpers: {@code math.clamp(v, lo, hi)},
 * {@code math.abs(x)}, {@code math.isEven(x)}. Maps to {@code kof_math_*}
 * runtime functions on each backend. S1 is Int-only (clamp/abs/sign/min/max/
 * isEven/isOdd/isPositive/isNegative/isZero) — all available on JVM / Native /
 * JS / interpreter with byte-identical parity. Double variants (lerp/roundTo/
 * percentage/sqrt/pow) are S1b (need FP asm on riscv — FLT001 caution).
 */
public final class KofMath {

    private KofMath() {}

    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type DOUBLE = Type.PrimitiveType.DOUBLE;

    static final List<String> NAMESPACES = List.of("math");

    static boolean isMathNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record MathCall(String function, Type returnType, List<Type> parameterTypes) {}

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
            default -> null;
        };
    }

    /** S1 (Int) + S1b/S1b.1 (Double) em TODOS os targets (MATH001 fechado
     * 11/09 — fatia riscv B32 + tradutor aarch fsqrt.d/fcvtzs; prova qemu). */
    static boolean supportedOn(String function, Target target) {
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
}

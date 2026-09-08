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
            default -> null;
        };
    }

    /** All S1 math functions are Int-only and present on every target. */
    static boolean supportedOn(String function, Target target) {
        return true;
    }

    static String gapCode(String function) {
        return "MATH001";
    }

    private static boolean isInt(Type t) {
        return t == INT || "int".equals(t.toString()) || "Int".equals(t.toString());
    }
}

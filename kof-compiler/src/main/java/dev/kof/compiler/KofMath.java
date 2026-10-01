package dev.kof.compiler;

import java.util.List;

public final class KofMath {
    private KofMath() {}

    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type DOUBLE = Type.PrimitiveType.DOUBLE;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("math");
    static boolean isMathNamespace(String name) { return NAMESPACES.contains(name); }

    record MathCall(String function, Type returnType, List<Type> parameterTypes) {}

    static List<String> functions() {
        return List.of(
            "abs", "sign", "clamp", "min", "max",
            "isEven", "isOdd", "isPositive", "isNegative", "isZero",
            "sqrt", "cbrt", "hypot", "lerp", "percentage", "isInteger", "isDecimal",
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "toRadians", "toDegrees", "pi", "e", "tau",
            "ceil", "floor", "rint", "round", "signum",
            "exp", "expm1", "log", "log1p", "log10", "pow",
            "roundTo",
            "parseInt", "parseLong", "parseDouble",
            "parseIntOrDefault", "parseLongOrDefault", "parseDoubleOrDefault"
        );
    }

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

            case "sin" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_sin", DOUBLE, List.of(DOUBLE)) : null;
            case "cos" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_cos", DOUBLE, List.of(DOUBLE)) : null;
            case "tan" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_tan", DOUBLE, List.of(DOUBLE)) : null;
            case "asin" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_asin", DOUBLE, List.of(DOUBLE)) : null;
            case "acos" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_acos", DOUBLE, List.of(DOUBLE)) : null;
            case "atan" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_atan", DOUBLE, List.of(DOUBLE)) : null;
            case "atan2" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_atan2", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;
            case "toRadians" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_toRadians", DOUBLE, List.of(DOUBLE)) : null;
            case "toDegrees" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_toDegrees", DOUBLE, List.of(DOUBLE)) : null;
            case "pi" -> argc == 0
                    ? new MathCall("kof_math_pi", DOUBLE, List.of()) : null;
            case "e" -> argc == 0
                    ? new MathCall("kof_math_e", DOUBLE, List.of()) : null;
            case "tau" -> argc == 0
                    ? new MathCall("kof_math_tau", DOUBLE, List.of()) : null;

            case "sinh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_sinh", DOUBLE, List.of(DOUBLE)) : null;
            case "cosh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_cosh", DOUBLE, List.of(DOUBLE)) : null;
            case "tanh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_tanh", DOUBLE, List.of(DOUBLE)) : null;
            case "asinh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_asinh", DOUBLE, List.of(DOUBLE)) : null;
            case "acosh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_acosh", DOUBLE, List.of(DOUBLE)) : null;
            case "atanh" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_atanh", DOUBLE, List.of(DOUBLE)) : null;

            case "exp" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_exp", DOUBLE, List.of(DOUBLE)) : null;
            case "expm1" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_expm1", DOUBLE, List.of(DOUBLE)) : null;
            case "log" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_log", DOUBLE, List.of(DOUBLE)) : null;
            case "log1p" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_log1p", DOUBLE, List.of(DOUBLE)) : null;
            case "log10" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_log10", DOUBLE, List.of(DOUBLE)) : null;

            case "sqrt" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_sqrt", DOUBLE, List.of(DOUBLE)) : null;
            case "cbrt" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_cbrt", DOUBLE, List.of(DOUBLE)) : null;
            case "hypot" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_hypot", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;

            case "ceil" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_ceil", DOUBLE, List.of(DOUBLE)) : null;
            case "floor" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_floor", DOUBLE, List.of(DOUBLE)) : null;
            case "rint" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_rint", DOUBLE, List.of(DOUBLE)) : null;
            case "round" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_round", LONG, List.of(DOUBLE)) : null;
            case "signum" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_signum", DOUBLE, List.of(DOUBLE)) : null;

            case "lerp" -> argc == 3 && isDouble(argTypes.get(0))
                    && isDouble(argTypes.get(1)) && isDouble(argTypes.get(2))
                    ? new MathCall("kof_math_lerp", DOUBLE, List.of(DOUBLE, DOUBLE, DOUBLE)) : null;
            case "percentage" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_percentage", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;
            case "isInteger", "isDecimal" -> argc == 1 && isDouble(argTypes.get(0))
                    ? new MathCall("kof_math_" + name, BOOL, List.of(DOUBLE)) : null;
            case "roundTo" -> argc == 2 && isDouble(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new MathCall("kof_math_roundTo", DOUBLE, List.of(DOUBLE, INT)) : null;
            case "pow" -> argc == 2 && isDouble(argTypes.get(0)) && isDouble(argTypes.get(1))
                    ? new MathCall("kof_math_pow", DOUBLE, List.of(DOUBLE, DOUBLE)) : null;

            case "parseInt" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_int", INT, List.of(STR)) : null;
            case "parseLong" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_long", LONG, List.of(STR)) : null;
            case "parseDouble" -> argc == 1 && isStr(argTypes.get(0))
                    ? new MathCall("kof_string_to_double", DOUBLE, List.of(STR)) : null;
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

    static boolean supportedOn(@SuppressWarnings("unused") String function,
                                @SuppressWarnings("unused") Target target) {
        return true;
    }

    static String gapCode(String function) {
        return switch (function) {
            case "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
                 "exp", "expm1", "log", "log1p", "log10", "cbrt", "hypot",
                 "ceil", "floor", "rint", "round", "signum" -> "MATH002";
            default -> "MATH001";
        };
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

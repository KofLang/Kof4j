package dev.kof.compiler;

import java.util.List;

/**
 * Registro de assinaturas dos métodos de String e Object (resolução de
 * dispatch do CompilerDriver). Puro — sem estado de compilação.
 */
final class StringMethodRegistry {

    private StringMethodRegistry() {}

    record Sig(Type returnType, List<Type> parameterTypes) {}

    private static Sig sig(Type returnType, List<Type> parameterTypes) {
        return new Sig(returnType, parameterTypes);
    }

    static Sig objectMethodSignature(String name, int argCount) {
        Type INT = Type.PrimitiveType.INT;
        Type BOOL = Type.PrimitiveType.BOOL;
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        return switch (name) {
            case "hashCode" -> argCount == 0 ? sig(INT, List.of()) : null;
            case "toString" -> argCount == 0 ? sig(BuiltinTypes.STRING, List.of()) : null;
            case "equals" -> argCount == 1 ? sig(BOOL, List.of(object)) : null;
            case "getClass" -> argCount == 0 ? sig(
                    new Type.ClassType("java.lang", "Class", List.of()), List.of()) : null;
            default -> null;
        };
    }

    static Sig stringMethodSignature(String name, int argCount) {
        return stringMethodSignature(name, argCount, List.of());
    }

    static Sig stringMethodSignature(String name, int argCount, List<Type> argTypes) {
        Type str = BuiltinTypes.STRING;
        Type INT = Type.PrimitiveType.INT;
        Type BOOL = Type.PrimitiveType.BOOL;
        Type CHAR = Type.PrimitiveType.CHAR;
        Type charSeq = new Type.ClassType("java.lang", "CharSequence", List.of());
        Type object = new Type.ClassType("java.lang", "Object", List.of());
        Type strArray = new Type.ArrayType(BuiltinTypes.STRING);
        return switch (name) {
            case "length" -> argCount == 0 ? sig(INT, List.of()) : null;
            case "charAt" -> argCount == 1 ? sig(CHAR, List.of(INT)) : null;
            case "substring" -> argCount == 1 ? sig(str, List.of(INT))
                    : argCount == 2 ? sig(str, List.of(INT, INT)) : null;
            case "contains" -> argCount == 1 ? sig(BOOL, List.of(charSeq)) : null;
            case "startsWith" -> argCount == 1 ? sig(BOOL, List.of(str))
                    : argCount == 2 ? sig(BOOL, List.of(str, INT)) : null;
            case "endsWith" -> argCount == 1 ? sig(BOOL, List.of(str)) : null;
            case "equals" -> argCount == 1 ? sig(BOOL, List.of(object)) : null;
            case "equalsIgnoreCase" -> argCount == 1 ? sig(BOOL, List.of(str)) : null;
            case "indexOf" -> argCount == 1 ? sig(INT, List.of(str))
                    : argCount == 2 ? sig(INT, List.of(str, INT)) : null;
            case "lastIndexOf" -> argCount == 1 ? sig(INT, List.of(str))
                    : argCount == 2 ? sig(INT, List.of(str, INT)) : null;
            case "concat" -> argCount == 1 ? sig(str, List.of(str)) : null;
            case "trim" -> argCount == 0 ? sig(str, List.of()) : null;
            case "toInt" -> argCount == 0 ? sig(INT, List.of()) : null;
            case "toLong" -> argCount == 0 ? sig(Type.PrimitiveType.LONG, List.of()) : null;
            case "toDouble" -> argCount == 0 ? sig(Type.PrimitiveType.DOUBLE, List.of()) : null;
            case "toFloat" -> argCount == 0 ? sig(Type.PrimitiveType.FLOAT, List.of()) : null;
            case "toUpperCase", "toLowerCase" -> argCount == 0 ? sig(str, List.of()) : null;
            case "replace" -> argCount == 2 ? replaceSignature(argTypes, str, CHAR, charSeq) : null;
            case "split" -> argCount == 1 ? sig(strArray, List.of(str))
                    : argCount == 2 ? sig(strArray, List.of(str, INT)) : null;
            default -> null;
        };
    }

    /**
     * String.replace(a, b): with two String arguments the call must target
     * Java's replace(CharSequence, CharSequence); with two characters (Kof
     * Ints) it targets replace(char, char). The overload is resolved by the
     * argument types — a previous version always picked (char, char), which
     * pushed Strings onto a (C, C) descriptor (VerifyError on the JVM).
     */
    private static Sig replaceSignature(List<Type> argTypes, Type str, Type CHAR, Type charSeq) {
        boolean stringArgs = argTypes.size() == 2
                && BuiltinTypes.isString(argTypes.get(0)) && BuiltinTypes.isString(argTypes.get(1));
        return stringArgs
                ? sig(str, List.of(charSeq, charSeq))
                : sig(str, List.of(CHAR, CHAR));
    }

    /** Métodos do String implementados pelo runtime Kof (não existem no
     *  java.lang.String): as conversões numéricas. */
    static String stringRuntimeMethod(String name) {
        return switch (name) {
            case "toInt" -> "kof_string_to_int";
            case "toLong" -> "kof_string_to_long";
            case "toDouble" -> "kof_string_to_double";
            case "toFloat" -> "kof_string_to_float";
            default -> null;
        };
    }
}
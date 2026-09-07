package dev.kof.compiler;

import java.util.List;
import java.util.Map;

public sealed interface Type {
    record PrimitiveType(String name, int sort) implements Type {
        static final PrimitiveType BOOL = new PrimitiveType("bool", 1);
        static final PrimitiveType BYTE = new PrimitiveType("byte", 5);
        static final PrimitiveType SHORT = new PrimitiveType("short", 9);
        static final PrimitiveType INT = new PrimitiveType("int", 10);
        static final PrimitiveType LONG = new PrimitiveType("long", 11);
        static final PrimitiveType FLOAT = new PrimitiveType("float", 6);
        static final PrimitiveType DOUBLE = new PrimitiveType("double", 7);
        static final PrimitiveType CHAR = new PrimitiveType("char", 2);
        static final PrimitiveType VOID = new PrimitiveType("void", 0);
    }

    record ClassType(String packageName, String name, List<Type> typeArguments) implements Type {
        String internalName() {
            if (packageName.isEmpty()) return name;
            return packageName.replace('.', '/') + "/" + name;
        }
    }

    record TypeVariable(String name) implements Type {
    }

    record FunctionType(List<Type> parameterTypes, Type returnType, String className) implements Type {
        FunctionType(List<Type> parameterTypes, Type returnType) {
            this(parameterTypes, returnType, null);
        }
    }

    record ArrayType(Type componentType) implements Type {
    }

    record WildcardType(Type bound, boolean upper) implements Type {
    }

    record UnknownType() implements Type {
        static final UnknownType UNKNOWN = new UnknownType();
    }

    record NullableType(Type inner) implements Type {
    }

    static Type of(String name) {
        if (name == null) return UnknownType.UNKNOWN;
        // tipo de função: "(Int) -> Int" (bug 8)
        if (name.startsWith("(") && name.contains(" -> ")) {
            int rp = name.indexOf(')');
            int arrow = name.indexOf(" -> ");
            String paramsStr = rp > 1 ? name.substring(1, rp) : "";
            String retStr = name.substring(arrow + 4);
            List<Type> params = paramsStr.isEmpty() ? List.of()
                    : java.util.Arrays.stream(paramsStr.split(","))
                            .map(String::trim).map(Type::of).toList();
            return new FunctionType(params, Type.of(retStr));
        }
        if (name.endsWith("?")) {
            Type inner = of(name.substring(0, name.length() - 1));
            return new NullableType(inner);
        }
        if (name.endsWith("[]")) {
            Type component = of(name.substring(0, name.length() - 2));
            return new ArrayType(component);
        }
        if (name != null && name.contains("<")) {
            int lt = name.indexOf('<');
            String base = name.substring(0, lt);
            String argsStr = name.substring(lt + 1, name.lastIndexOf('>'));
            List<Type> args = java.util.Arrays.stream(argsStr.split(","))
                    .map(String::trim).map(Type::of).toList();
            if ("List".equals(base) || "ArrayList".equals(base)) return new ClassType("kof", "List", args);
            if ("Map".equals(base) || "HashMap".equals(base)) return new ClassType("kof", "Map", args);
            if ("Set".equals(base) || "HashSet".equals(base)) return new ClassType("kof", "Set", args);
            if ("Channel".equals(base)) return new ClassType("kof.concurrent", "Channel", args);
            return new ClassType("", base, args);
        }
        return switch (name) {
            case "bool", "boolean", "Bool", "Boolean" -> PrimitiveType.BOOL;
            case "byte", "Byte" -> PrimitiveType.BYTE;
            case "short", "Short" -> PrimitiveType.SHORT;
            case "int", "Int" -> PrimitiveType.INT;
            case "long", "Long" -> PrimitiveType.LONG;
            case "float", "Float" -> PrimitiveType.FLOAT;
            case "double", "Double" -> PrimitiveType.DOUBLE;
            case "char", "Char" -> PrimitiveType.CHAR;
            case "void", "Void" -> PrimitiveType.VOID;
            case "string", "String" -> BuiltinTypes.STRING;
            case "Object" -> new ClassType("java.lang", "Object", List.of());
            default -> new ClassType("", name, List.of());
        };
    }

    static boolean isPrimitive(Type type) {
        return type instanceof PrimitiveType && !(type instanceof PrimitiveType p && "void".equals(p.name()));
    }

    static boolean isVoid(Type type) {
        return type instanceof PrimitiveType p && "void".equals(p.name());
    }

    static boolean isUnknown(Type type) {
        return type instanceof UnknownType;
    }

    static boolean isString(Type type) {
        return BuiltinTypes.isString(type);
    }

    static boolean isArray(Type type) {
        return type instanceof ArrayType;
    }

    // Tipos cuja divisão/resto por zero lança ArithmeticException no JVM
    // (float/double produzem Infinity/NaN e não são inteiros aqui).
    static boolean isInteger(Type type) {
        if (!(type instanceof PrimitiveType pt)) return false;
        return switch (canonicalPrimitiveName(pt.name())) {
            case "int", "long", "byte", "short", "char" -> true;
            default -> false;
        };
    }

    static String canonicalPrimitiveName(String name) {
        return switch (name) {
            case "bool", "boolean", "Bool", "Boolean" -> "bool";
            case "byte", "Byte" -> "byte";
            case "short", "Short" -> "short";
            case "int", "Int" -> "int";
            case "long", "Long" -> "long";
            case "float", "Float" -> "float";
            case "double", "Double" -> "double";
            case "char", "Char" -> "char";
            case "void", "Void" -> "void";
            default -> name;
        };
    }

    static String canonicalName(String name) {
        String canonical = canonicalPrimitiveName(name);
        if (!canonical.equals(name)) return canonical;
        if ("string".equals(name)) return "String";
        if ("list".equals(name) || "arraylist".equals(name)) return "List";
        if ("map".equals(name) || "hashmap".equals(name)) return "Map";
        if ("set".equals(name) || "hashset".equals(name)) return "Set";
        return name;
    }

    static Type arrayElementType(Type type) {
        if (type instanceof ArrayType at) return at.componentType();
        return UnknownType.UNKNOWN;
    }
}

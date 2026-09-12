package dev.kof.compiler;

import java.util.List;


public final class BuiltinTypes {

    private BuiltinTypes() {}


    public static final Type STRING = new Type.ClassType("java.lang", "String", List.of());


    public static final Type STRING_ARRAY = new Type.ArrayType(STRING);


    public static boolean isString(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "java.lang".equals(ct.packageName()) && "String".equals(ct.name());
        }
        return false;
    }


    public static boolean isReferenceType(Type type) {
        return type instanceof Type.ClassType || type instanceof Type.ArrayType;
    }


    public static final Type LIST = new Type.ClassType("kof", "List", List.of());


    public static boolean isList(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "List".equals(ct.name());
        }
        return false;
    }


    public static final Type MAP = new Type.ClassType("kof", "Map", List.of());


    public static boolean isMap(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Map".equals(ct.name());
        }
        return false;
    }

    public static Type mapKey(Type type) {
        if (type instanceof Type.ClassType ct && "Map".equals(ct.name())
                && ct.typeArguments().size() == 2) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    /** §107: elemento de List/Set (typeArgs = [elem]); Unknown se ausente. */
    public static Type listElement(Type type) {
        if (type instanceof Type.ClassType ct && "List".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    public static Type setElement(Type type) {
        if (type instanceof Type.ClassType ct && "Set".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }

    public static Type mapValue(Type type) {
        if (type instanceof Type.ClassType ct && "Map".equals(ct.name())
                && ct.typeArguments().size() == 2) {
            return ct.typeArguments().get(1);
        }
        return Type.UnknownType.UNKNOWN;
    }


    public static final Type SET = new Type.ClassType("kof", "Set", List.of());


    /**
     * Canais tipados (concorrência): {@code channel<T>()}. O tipo carrega o
     * elemento T; em runtime o canal é opaco (JVM: Long do registry de filas;
     * Native: pointer p/ a struct; JS: objeto com array). Métodos: {@code send(v)},
     * {@code receive()} -> T.
     */
    public static final Type CHANNEL = new Type.ClassType("kof.concurrent", "Channel", List.of());

    public static boolean isChannel(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof.concurrent".equals(ct.packageName()) && "Channel".equals(ct.name());
        }
        return false;
    }

    public static Type channelElement(Type type) {
        if (type instanceof Type.ClassType ct && "Channel".equals(ct.name())
                && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }


    /**
     * Enums declarados na compilação corrente: em runtime o valor do enum É
     * o nome (String) — mapeado para java/lang/String em todos os backends.
     */
    private static final java.util.Set<String> ENUM_NAMES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void resetEnums() {
        ENUM_NAMES.clear();
    }

    public static void registerEnum(String name) {
        if (name != null) ENUM_NAMES.add(name);
    }

    public static boolean isEnumName(String name) {
        return name != null && ENUM_NAMES.contains(name);
    }

    public static boolean isEnumType(Type type) {
        return type instanceof Type.ClassType ct
                && ct.packageName().isEmpty() && isEnumName(ct.name());
    }


    public static boolean isSet(Type type) {
        if (type instanceof Type.ClassType ct) {
            return "kof".equals(ct.packageName()) && "Set".equals(ct.name());
        }
        return false;
    }
}

package dev.kof.compiler;

import java.util.List;

public final class KofCache {
    private KofCache() {}
    static final Type CACHE = new Type.ClassType("kof.cache", "Cache", List.of());
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isCacheNamespace(String name) { return "cache".equals(name); }
    static boolean isCacheMethod(String name) {
        return switch (name) {
            case "get", "set", "ttl", "delete", "clear" -> true;
            default -> false;
        };
    }
    record CacheCall(String function, Type returnType, List<Type> parameterTypes) {}
    static boolean supportedOn(Target target) { return true; }
    static CacheCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "get" -> argTypes.size() == 1 ? new CacheCall("kof_cache_get", STR, List.of(STR)) : null;
            case "set" -> {
                if (argTypes.size() == 2) yield new CacheCall("kof_cache_set", VOID, List.of(STR, STR));
                if (argTypes.size() == 3) yield new CacheCall("kof_cache_set_ttl", VOID, List.of(STR, STR, INT));
                yield null;
            }
            case "ttl" -> argTypes.size() == 1 ? new CacheCall("kof_cache_ttl", INT, List.of(STR)) : null;
            case "delete" -> argTypes.size() == 1 ? new CacheCall("kof_cache_delete", VOID, List.of(STR)) : null;
            case "clear" -> argTypes.isEmpty() ? new CacheCall("kof_cache_clear", VOID, List.of()) : null;
            default -> null;
        };
    }
}

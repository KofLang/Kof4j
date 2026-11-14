package dev.kof.compiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding slice 4 (issue #842): the RETURN TYPES of the host bindings.
 *
 * <p>The host registers, per session, the name of each binding and the Kof
 * type it answers with; the typer reads that table so `hostcall_x(...)` types
 * like any other call (the default is {@code String} — the honest default for
 * a host API that crosses the language boundary as text).
 *
 * <p>The table is per-compilation, not global: the session installs it before
 * the script is compiled and the compiler drops it afterwards, so one
 * embedding never leaks its signatures into another.
 */
public final class KofHostBindingTypes {

    private static final ThreadLocal<Map<String, Type>> CURRENT = new ThreadLocal<>();

    private KofHostBindingTypes() {
    }

    /** Installs the session's binding signatures for the current compilation. */
    public static void install(Map<String, Type> signatures) {
        CURRENT.set(signatures == null ? null : new ConcurrentHashMap<>(signatures));
    }

    /** Clears the installed signatures (the end of the compilation). */
    public static void clear() {
        CURRENT.remove();
    }

    /** The registered return type, or the default (String) when the host did
     *  not declare one. */
    static Type returnTypeOf(SemanticAnalyzer sa, String callName) {
        Map<String, Type> table = CURRENT.get();
        if (table != null) {
            Type t = table.get(callName.substring("hostcall_".length()));
            if (t != null) return t;
        }
        return new Type.ClassType("java.lang", "String", java.util.List.of());
    }
}

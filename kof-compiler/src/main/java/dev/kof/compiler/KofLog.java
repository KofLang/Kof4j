package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native logging module
 * ({@code kof.log}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * log.debug("detail")
 * log.info("request started")
 * log.warn("slow response")
 * log.error("failed: " + e.getMessage())
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_log_*} function of the
 * generated {@code dev.kof.runtime.KofRuntime} class (JVM target). The level
 * is controlled by the {@code KOF_LOG_LEVEL} environment variable
 * ({@code debug < info < warn < error < off}; default {@code info}).
 * Native and JS targets report {@code LOG001} at compile time.
 */
public final class KofLog {

    private KofLog() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isLogNamespace(String name) {
        return "log".equals(name);
    }

    static boolean isLogMethod(String name) {
        return switch (name) {
            case "debug", "info", "warn", "error" -> true;
            default -> false;
        };
    }

    record LogCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** kof.log: JVM + Native (asm próprio) + JS (console.* com nível). */
    static boolean supportedOn(Target target) {
        return target == Target.JVM || target.isNative() || target == Target.JS;
    }

    static String gapCode() {
        return "LOG001";
    }

    /** {@code log.<debug|info|warn|error>(message)} — one String argument. */
    static LogCall staticCall(String name, List<Type> argTypes) {
        if (!isLogMethod(name) || argTypes.size() != 1) return null;
        return new LogCall("kof_log_" + name, VOID, List.of(STR));
    }
}
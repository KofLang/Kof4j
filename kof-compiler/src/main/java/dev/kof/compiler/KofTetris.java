package dev.kof.compiler;

import java.util.List;

/**
 * KofTetris — hidden easter egg registry ({@code kof.tetris}).
 *
 * Calling {@code tetris.run()} starts a simplified terminal tetris on the
 * JVM target. It is intentionally undocumented: the entry point is the
 * surprise. On targets that cannot render an interactive terminal session
 * (Native, JS) the call produces a clear compile-time diagnostic (EGG001)
 * instead of silently diverging.
 */
public final class KofTetris {

    private KofTetris() {}

    static final List<String> NAMESPACES = List.of("tetris");

    static boolean isTetrisNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record TetrisCall(String function, Type returnType, List<Type> parameterTypes) {}

    /**
     * Resolves a call in the tetris namespace. Returns null when the call
     * is not part of the API (the analyzer reports an unknown method).
     */
    static TetrisCall staticMethod(String namespace, String name, int argCount) {
        if ("tetris".equals(namespace)) {
            return switch (name) {
                case "run" -> argCount == 0
                        ? new TetrisCall("kof_tetris_run", Type.PrimitiveType.VOID, List.of()) : null;
                default -> null;
            };
        }
        return null;
    }

    /** Only the JVM target can render an interactive terminal session. */
    static boolean supportedOn(Target target) {
        return target == Target.JVM;
    }

    /** Diagnostic code for target gaps (analogous to WEB001/CONF001). */
    static String gapCode() {
        return "EGG001";
    }
}
package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native time module ({@code kof.time}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * time.sleep(500)                  // pausa o thread atual (ms)
 * var now = time.now()             // epoch millis
 * var job = time.interval(1000, () -> { poll() })
 * time.cancel(job)
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_time_*} function of the
 * generated {@code dev.kof.runtime.KofRuntime} class (JVM target).
 * Native reuses the scheduler (SCHED001); JS runs a cooperative timer queue
 * pumped by {@code time.sleep} (GraalJS has no event loop — TIME001 closed).
 */
final class KofTime {

    private KofTime() {}

    static final Type TIME = new Type.ClassType("kof.time", "Time", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isTimeNamespace(String name) {
        return "time".equals(name);
    }

    static boolean isTimeMethod(String name) {
        return switch (name) {
            case "sleep", "now", "interval", "cancel" -> true;
            default -> false;
        };
    }

    record TimeCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** kof.time: now/sleep em todos targets; interval/cancel em JVM+Native
     *  (reaproveita o scheduler — SCHED001) + JS (fila cooperativa bombeada
     *  por time.sleep — GraalJS não expõe setInterval; TIME001 fechado). */
    static boolean supportedOn(Target target) {
        return true;
    }

    static boolean supportedOn(String method, Target target) {
        // TIME001 FEITO no cross (05/09): kof_time_interval/cancel são alias
        // de kof_scheduler_every/cancel no runtime riscv64/aarch64 (thread por
        // job via clone+nanosleep — mesmo mecanismo do spawn).
        return true;
    }

    static String gapCode() {
        return "TIME001";
    }

    /** {@code time.<method>(...)} — sleep/interval em ms; now em epoch millis. */
    static TimeCall staticCall(String name, List<Type> argTypes) {
        if (!isTimeMethod(name)) return null;
        return switch (name) {
            case "sleep" -> argTypes.size() == 1
                    ? new TimeCall("kof_time_sleep", VOID, List.of(INT))
                    : null;
            case "now" -> argTypes.size() == 0
                    ? new TimeCall("kof_time_now", LONG, List.of())
                    : null;
            case "interval" -> argTypes.size() == 2
                    ? new TimeCall("kof_time_interval", STR, List.of(INT, OBJ))
                    : null;
            case "cancel" -> argTypes.size() == 1
                    ? new TimeCall("kof_time_cancel", VOID, List.of(STR))
                    : null;
            default -> null;
        };
    }
}
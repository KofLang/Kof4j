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
public final class KofTime {

    private KofTime() {}

    static final Type TIME = new Type.ClassType("kof.time", "Time", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type LONG = Type.PrimitiveType.LONG;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isTimeNamespace(String name) {
        return "time".equals(name);
    }

    static boolean isTimeMethod(String name) {
        return switch (name) {
            case "sleep", "now", "interval", "cancel",
                    // STDLIB S7-wedge: calendário civil (escalares puros —
                    // dias entre datas e dia-da-semana chegam no próximo degrau)
                    "isLeapYear", "daysInMonth", "dayOfWeek", "daysBetween",
                    // STDLIB S7a: add/diff sobre data ISO (STR->STR/Int)
                    "addDays", "diffDays" -> true;
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
        // S7a TIME002 (10/09): addDays/diffDays = só JVM-family (JVM/SCRIPT/
        // ANDROID — interpretador herda o KofRuntime do JVM). JS/Native = gap
        // honesto (String-alocação no asm + parse data: escopo próprio, R6 —
        // nunca fallback silencioso).
        // S7b (10/09): JS FECHADO — kofTimeAddDays/kofTimeDiffDays no
        // JsRuntimeUiWeb (mesmo algoritmo civil do wedge, SEM Date =>
        // paridade byte-idêntica). Restam apenas os targets NATIVE.
        if (("addDays".equals(method) || "diffDays".equals(method))
                && target.isNative()) {
            return false;
        }
        return true;
    }

    static String gapCode(String method) {
        // TIME002 — data ISO add/diff: JVM/Script/JS FEITOS (S7a/S7b); resta
        // só Native (asm: parse String + alocação de String em runtime —
        // mesmo escopo do port nativo NET001).
        return ("addDays".equals(method) || "diffDays".equals(method))
                ? "TIME002" : "TIME001";
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
            // STDLIB S7-wedge — calendário civil (só mnemônicos aritméticos;
            // year >= 1 => nada negativo; paridade byte-a-byte JVM/JS/Native).
            case "isLeapYear" -> argTypes.size() == 1 && argTypes.get(0) == INT
                    ? new TimeCall("kof_time_isLeapYear", BOOL, List.of(INT)) : null;
            case "daysInMonth" -> argTypes.size() == 2
                    ? new TimeCall("kof_time_daysInMonth", INT, List.of(INT, INT)) : null;
            case "dayOfWeek" -> argTypes.size() == 3
                    ? new TimeCall("kof_time_dayOfWeek", INT, List.of(INT, INT, INT)) : null;
            case "daysBetween" -> argTypes.size() == 6
                    ? new TimeCall("kof_time_daysBetween", INT,
                            List.of(INT, INT, INT, INT, INT, INT)) : null;
            // STDLIB S7a — data ISO (String) add/diff. JVM/SCRIPT via
            // java.time; Native/JS = gap honesto TIME002 (parse+alocação de
            // String no asm é escopo próprio, R6). Inválido => ""/0 (paridade
            // com a política "invalid => 0" do calendário wedge).
            case "addDays" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == INT
                    ? new TimeCall("kof_time_addDays", STR, List.of(STR, INT)) : null;
            case "diffDays" -> argTypes.size() == 2 && argTypes.get(0) == STR
                    && argTypes.get(1) == STR
                    ? new TimeCall("kof_time_diffDays", INT, List.of(STR, STR)) : null;
            default -> null;
        };
    }
}
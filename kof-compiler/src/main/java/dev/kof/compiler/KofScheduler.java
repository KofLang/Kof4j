package dev.kof.compiler;

import java.util.List;

final class KofScheduler {
    private KofScheduler() {}
    static final Type SCHEDULER = new Type.ClassType("kof.scheduler", "Scheduler", List.of());
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isSchedulerNamespace(String name) { return "scheduler".equals(name) || "every".equals(name) || "at".equals(name); }
    static boolean isSchedulerMethod(String name) {
        return switch (name) {
            case "every", "at", "cancel" -> true;
            default -> false;
        };
    }
    record SchedulerCall(String function, Type returnType, List<Type> parameterTypes) {}
    static boolean supportedOn(Target target) {
        // SCHED001 FEITO no cross (05/09): kof_scheduler_every/cancel/at no
        // runtime riscv64/aarch64 (thread por job via clone 220 + nanosleep
        // 101 + spinlock amoswap.w — mesmo mecanismo do spawn).
        return target == Target.JVM || target == Target.ANDROID
                || target == Target.JS || target.isNative();
    }
    static SchedulerCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "every" -> argTypes.size() == 2 && argTypes.get(0) instanceof Type.PrimitiveType
                    ? new SchedulerCall("kof_scheduler_every", STR, List.of(INT, OBJ)) : null;
            case "at" -> argTypes.size() == 2
                    ? new SchedulerCall("kof_scheduler_at", STR, List.of(STR, OBJ)) : null;
            case "cancel" -> argTypes.size() == 1
                    ? new SchedulerCall("kof_scheduler_cancel", VOID, List.of(STR)) : null;
            default -> null;
        };
    }
}

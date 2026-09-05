package dev.kof.compiler;

import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native messaging module
 * ({@code kof.mq}).
 *
 * <p>The Kof surface is idiomatic:
 *
 * <pre>{@code
 * mq.subscribe("order.created", fn(msg) {
 *     println("pedido: " + msg)
 * })
 * mq.publish("order.created", "12345")
 *
 * var q = mq.queue()
 * mq.push(q, "job-1")
 * var job = mq.pop(q)     // null quando vazia
 * }</pre>
 *
 * <p>Internally every call maps to a static {@code kof_mq_*} function: the
 * JVM target resolves it against the generated {@code dev.kof.runtime.KofRuntime}
 * class (in-memory pub/sub — handlers are Kof lambdas invoked with the
 * message; queues are bounded {@code ArrayBlockingQueue}s keyed by a handle);
 * the Native target emits an asm implementation in
 * {@link NativeRuntime#emitMq(StringBuilder)} (in-process pub/sub + queues,
 * 01/09, MQ001 fechado); the JS target reuses the in-process runtime.
 */
final class KofMq {

    private KofMq() {}

    static final Type MQ = new Type.ClassType("kof.mq", "Mq", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static boolean isMqNamespace(String name) {
        return "mq".equals(name);
    }

    static boolean isMqMethod(String name) {
        return switch (name) {
            case "publish", "subscribe", "unsubscribe", "queue", "push", "pop", "queueSize" -> true;
            default -> false;
        };
    }

    record MqCall(String function, Type returnType, List<Type> parameterTypes) {}

    /** kof.mq: JVM + JS + Native (01/09, pub/sub + filas in-process). */
    static boolean supportedOn(Target target) {
        // MQ001: o runtime riscv64/aarch64 tem pub/sub/queue em asm mas com
        // bugs (queue com assinatura errada, pop não remove, queue_size
        // ausente, unsubscribe stub) e nenhum teste cross. Gate honesto até o
        // port completo (mesmo padrão de DB001) — nunca segfault silencioso.
        return target != Target.NATIVE_RISCV64 && target != Target.NATIVE_AARCH64;
    }

    static String gapCode() {
        return "MQ001";
    }

    /** {@code mq.<method>(...)} — topics são Strings; mensagens são Object. */
    static MqCall staticCall(String name, List<Type> argTypes) {
        if (!isMqMethod(name)) return null;
        return switch (name) {
            case "publish" -> argTypes.size() == 2
                    ? new MqCall("kof_mq_publish", VOID, List.of(STR, OBJ))
                    : null;
            case "subscribe", "unsubscribe" -> argTypes.size() == 2
                    ? new MqCall("kof_mq_" + name, VOID, List.of(STR, OBJ))
                    : null;
            case "queue" -> argTypes.size() == 0
                    ? new MqCall("kof_mq_queue", STR, List.of())
                    : null;
            case "push" -> argTypes.size() == 2
                    ? new MqCall("kof_mq_push", VOID, List.of(STR, OBJ))
                    : null;
            case "pop" -> argTypes.size() == 1
                    ? new MqCall("kof_mq_pop", OBJ, List.of(STR))
                    : null;
            case "queueSize" -> argTypes.size() == 1
                    ? new MqCall("kof_mq_queue_size", INT, List.of(STR))
                    : null;
            default -> null;
        };
    }
}
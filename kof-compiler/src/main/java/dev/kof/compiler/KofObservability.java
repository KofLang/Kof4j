package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.observability} (G5).
 *
 * <p>Intention-first: {@code observability.health()}, {@code observability.counter("req")},
 * {@code observability.requestId()} etc. Maps to {@code kof_observability_*} runtime
 * functions on each backend. All observability primitives are available on
 * JVM / Native / JS — no target gap (G5).
 *
 * <p>Health: {@code health()} → "UP", {@code readiness()}/{@code liveness()} → true.
 * Metrics: {@code counter(name)} increments by 1, {@code increment(name, delta)},
 * {@code gauge(name, value)} stores gauge, {@code histogram(name, value)} records
 * sum+count, {@code metrics()} renders all in Prometheus text format. Request
 * IDs: {@code requestId()} / {@code correlationId()} generate random hex IDs.
 */
public final class KofObservability {

    private KofObservability() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    static final List<String> NAMESPACES = List.of("observability");

    static boolean isObservabilityNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record ObservabilityCall(String function, Type returnType, List<Type> parameterTypes) {}

    static ObservabilityCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"observability".equals(namespace)) return null;
        int argc = argTypes.size();
        return switch (name) {
            case "health" -> argc == 0
                    ? new ObservabilityCall("kof_observability_health", STR, List.of()) : null;
            case "readiness" -> argc == 0
                    ? new ObservabilityCall("kof_observability_readiness", BOOL, List.of()) : null;
            case "liveness" -> argc == 0
                    ? new ObservabilityCall("kof_observability_liveness", BOOL, List.of()) : null;
            case "counter" -> argc == 1 && isString(argTypes.get(0))
                    ? new ObservabilityCall("kof_observability_counter", INT, List.of(STR)) : null;
            case "increment" -> argc == 2 && isString(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new ObservabilityCall("kof_observability_increment", INT, List.of(STR, INT)) : null;
            case "gauge" -> argc == 2 && isString(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new ObservabilityCall("kof_observability_gauge", VOID, List.of(STR, INT)) : null;
            case "histogram" -> argc == 2 && isString(argTypes.get(0)) && isInt(argTypes.get(1))
                    ? new ObservabilityCall("kof_observability_histogram", VOID, List.of(STR, INT)) : null;
            case "metrics" -> argc == 0
                    ? new ObservabilityCall("kof_observability_metrics", STR, List.of()) : null;
            case "requestId" -> argc == 0
                    ? new ObservabilityCall("kof_observability_request_id", STR, List.of()) : null;
            case "correlationId" -> argc == 0
                    ? new ObservabilityCall("kof_observability_correlation_id", STR, List.of()) : null;
            case "traceId" -> argc == 0
                    ? new ObservabilityCall("kof_observability_trace_id", STR, List.of()) : null;
            case "spanId" -> argc == 0
                    ? new ObservabilityCall("kof_observability_span_id", STR, List.of()) : null;
            case "spanStart" -> argc == 1 && isString(argTypes.get(0))
                    ? new ObservabilityCall("kof_observability_span_start", STR, List.of(STR)) : null;
            case "spanEnd" -> argc == 1 && isString(argTypes.get(0))
                    ? new ObservabilityCall("kof_observability_span_end", STR, List.of(STR)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(String function, Target target) {
        // OBS002 fechado: histogram/metrics (store + export Prometheus) agora
        // estão nos 3 targets (JVM/JS/Native).
        return true;
    }

    static String gapCode(String function) {
        return function.equals("kof_observability_histogram")
                || function.equals("kof_observability_metrics")
                ? "OBS002" : "OBS001";
    }

    private static boolean isString(Type t) {
        return t == STR || "String".equals(t.toString()) || t.toString().contains("String");
    }

    private static boolean isInt(Type t) {
        return t == INT || t == Type.PrimitiveType.INT || "int".equals(t.toString()) || "Int".equals(t.toString());
    }
}

package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.observability (G5: counters/gauges/histograms/spans/metrics) - parte 4/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmStringObsRuntime {

    private JvmStringObsRuntime() {}

    static String source() {
        return """
                // ── kof.observability (G5) ────────────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> KOF_OBS_COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Integer> KOF_OBS_GAUGES = new java.util.concurrent.ConcurrentHashMap<>();
                // histograma: name → [sum, count] (mutuamente sincronizado)
                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_OBS_HISTOGRAMS = new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_observability_health() {
                    return "UP";
                }

                public static boolean kof_observability_readiness() {
                    return true;
                }

                public static boolean kof_observability_liveness() {
                    return true;
                }

                public static int kof_observability_counter(String name) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
                }

                public static int kof_observability_increment(String name, int delta) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).addAndGet(delta);
                }

                public static void kof_observability_gauge(String name, int value) {
                    if (name == null) name = "";
                    KOF_OBS_GAUGES.put(name, value);
                }

                public static void kof_observability_histogram(String name, int value) {
                    if (name == null) name = "";
                    long[] entry = KOF_OBS_HISTOGRAMS.computeIfAbsent(name, k -> new long[2]);
                    synchronized (entry) {
                        entry[0] += value;      // sum
                        entry[1] += 1;          // count
                    }
                }

                // ── spans W3C com timing (tracing leve) ─────────────────
                // spanStart(name) → handle (id); spanEnd(handle) → JSON
                // {"traceId":..,"spanId":..,"parentSpanId":..,"name":..,
                //  "startMicros":..,"endMicros":..,"durationMicros":..}.
                // Propaga o traceId atual num ThreadLocal (requests web e
                // spawn herdam o trace).
                private static final java.lang.ThreadLocal<String> KOF_OBS_ACTIVE_TRACE = new java.lang.ThreadLocal<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_OBS_SPANS = new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_observability_span_start(String name) {
                    String id = kof_observability_trace_id() + kof_observability_span_id();
                    long[] span = { System.nanoTime() };
                    KOF_OBS_SPANS.put(id, span);
                    return id;
                }

                public static String kof_observability_span_end(String handle) {
                    long[] span = KOF_OBS_SPANS.remove(handle);
                    if (span == null) return "{}";
                    long endNanos = System.nanoTime();
                    long durUs = (endNanos - span[0]) / 1000;
                    String trace = KOF_OBS_ACTIVE_TRACE.get();
                    if (trace == null) trace = kof_observability_trace_id();
                    return "{\\"traceId\\":\\"" + trace + "\\",\\"spanId\\":\\"" + handle.substring(32)
                            + "\\",\\"parentSpanId\\":\\"\\",\\"name\\":\\"span\\",\\"startMicros\\":"
                            + span[0] / 1000 + ",\\"endMicros\\":" + endNanos / 1000
                            + ",\\"durationMicros\\":" + durUs + "}";
                }

                public static void kof_observability_set_trace(String traceId) {
                    if (traceId != null) KOF_OBS_ACTIVE_TRACE.set(traceId);
                }

                /** Exporta counters, gauges e histograms em formato Prometheus
                 *  (text exposition format). Histogramas sem buckets: expostos
                 *  como name_count (counter) + name_sum (gauge). */
                public static String kof_observability_metrics() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(sortedCounterLines(KOF_OBS_COUNTERS, "counter"));
                    sb.append(sortedIntLines(KOF_OBS_GAUGES, "gauge"));
                    // histogramas: sum/count por name
                    java.util.List<String> hnames = new java.util.ArrayList<>(KOF_OBS_HISTOGRAMS.keySet());
                    java.util.Collections.sort(hnames);
                    for (String base0 : hnames) {
                        String base = promName(base0, "");
                        long[] e = KOF_OBS_HISTOGRAMS.get(base0);
                        long sum, count;
                        synchronized (e) { sum = e[0]; count = e[1]; }
                        sb.append("# TYPE ").append(base).append("_count counter\\n");
                        sb.append(base).append("_count ").append(count).append('\\n');
                        sb.append("# TYPE ").append(base).append("_sum gauge\\n");
                        sb.append(base).append("_sum ").append(sum).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedCounterLines(
                        java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k).get()).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedIntLines(java.util.Map<String, Integer> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k)).append('\\n');
                    }
                    return sb.toString();
                }

                /** Nome Prometheus: sanitiza para [a-zA-Z0-9_:] e acrescenta sufixo. */
                private static String promName(String name, String suffix) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < name.length(); i++) {
                        char c = name.charAt(i);
                        if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
                        else sb.append('_');
                    }
                    if (sb.length() == 0) sb.append("k");
                    sb.append(suffix);
                    return sb.toString();
                }

                public static String kof_observability_request_id() {
                    return java.util.UUID.randomUUID().toString();
                }

                public static String kof_observability_correlation_id() {
                    return kof_observability_request_id();
                }

                public static String kof_observability_trace_id() {
                    return kof_sec_random_hex(16);
                }

                public static String kof_observability_span_id() {
                    return kof_sec_random_hex(8);
                }
""";
    }
}

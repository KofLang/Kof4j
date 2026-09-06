package dev.kof.compiler;

/**
 * kof.workflow (TIER 6) — runtime JVM. Jobs como closures nomeadas num
 * registro em memória; `run` executa; `pipeline` executa uma lista em
 * sequência. Zero YAML, zero framework externo.
 */
final class JvmWorkflowRuntime {

    private JvmWorkflowRuntime() {}

    static String source() {
        return """
                private static final java.util.Map<String, Object> KOF_WORKFLOW_JOBS =
                        new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_workflow_job(String name, Object fn) {
                    KOF_WORKFLOW_JOBS.put(name, fn);
                    return name;
                }

                public static String kof_workflow_run(String name) {
                    Object fn = KOF_WORKFLOW_JOBS.get(name);
                    if (fn == null) return "";
                    return invokeJob(fn);
                }

                public static String kof_workflow_pipeline(String name, Object names) {
                    if (names instanceof java.util.List<?> list) {
                        for (Object step : list) {
                            kof_workflow_run(String.valueOf(step));
                        }
                    }
                    return name;
                }

                private static String invokeJob(Object fn) {
                    if (fn == null) return "";
                    try {
                        java.lang.reflect.Method m = fn.getClass().getMethod("invoke");
                        Object r = m.invoke(fn);
                        return r == null ? "" : String.valueOf(r);
                    } catch (Exception e) {
                        return "";
                    }
                }

""";
    }
}